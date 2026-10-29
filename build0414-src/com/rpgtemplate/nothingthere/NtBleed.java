package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 一无所有的任何攻击：命中后使目标流血，并回复自身少量最大生命。
 * 草案数值：流血 5 秒，每秒造成“命中伤害 × 20%”，刷新而不叠加；命中回复 1% 最大生命。
 */
final class NtBleed {
    static final int DURATION_TICKS = 100;      // 5 秒
    static final double PER_SECOND_RATIO = 0.20;
    static final double HEAL_RATIO = 0.01;

    /** True while bleed damage is being applied; such hits do not re-bleed or heal. */
    static boolean ticking;

    private static final Map<LivingEntity, Bleed> BLEEDS = new WeakHashMap<>();

    private NtBleed() {
    }

    private static final class Bleed {
        final ServerPlayer source;
        final float perSecond;
        int ticksLeft;

        Bleed(ServerPlayer source, float perSecond, int ticksLeft) {
            this.source = source;
            this.perSecond = perSecond;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Called from the hurt handler for a player's hit (not for bleed ticks). */
    static void onHit(LivingHurtEvent event, LivingEntity victim, ServerPlayer attacker) {
        afterHit(victim, attacker, event.getAmount());
    }

    /** Bleed + self-heal after any landed hit. Also used by forced hits that skip the hurt event. */
    static void afterHit(LivingEntity victim, ServerPlayer attacker, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data == null) {
            return;
        }
        if (victim.m_6084_()) {
            BLEEDS.put(victim, new Bleed(attacker, (float) (amount * PER_SECOND_RATIO), DURATION_TICKS));
        }
        NtForms.healRatio(attacker, data, HEAL_RATIO);
    }

    /** Server tick: ticks down every bleed and damages once per second (at 80, 60, 40, 20, 0 left). */
    static void tick() {
        if (BLEEDS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Bleed>> it = BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Bleed> entry = it.next();
            LivingEntity victim = entry.getKey();
            Bleed bleed = entry.getValue();
            if (!victim.m_6084_()) {
                it.remove();
                continue;
            }
            bleed.ticksLeft--;
            if (bleed.ticksLeft % 20 == 0) {
                ticking = true;
                try {
                    TargetUtil.hurt(victim, ModDamageTypes.technique(bleed.source), bleed.perSecond);
                } finally {
                    ticking = false;
                }
            }
            if (bleed.ticksLeft <= 0) {
                it.remove();
            }
        }
    }
}
