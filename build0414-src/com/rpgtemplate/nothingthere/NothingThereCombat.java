package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 一无所有的受伤与输出侧逻辑：适应（输出递增）、流血与回复（任何攻击）、
 * 死亡记忆（减免来自击杀者的伤害）、茧与 goodbye 的无敌和减伤（最后执行）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NothingThereCombat {
    static final String ADAPTATION = "rpgtemplate:adaptation";
    static final String DEATH_MEMORY = "rpgtemplate:death_memory";
    /** 形态索引：NtForms.NAMES 中 0 = 锤，1 = 手刀。 */
    static final int FORM_HAMMER = 0;
    static final int FORM_BLADE = 1;
    /** 锤形态下玩家攻击伤害的加成（待用户确认数值）。 */
    static final double HAMMER_ATTACK_BONUS = 0.20;

    private NothingThereCombat() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        Entity source = event.getSource().m_7639_();
        LivingEntity victim = event.getEntity();
        if (source instanceof ServerPlayer attacker && attacker != victim && !NtBleed.ticking) {
            applyAdaptation(event, attacker);
            NtBleed.onHit(event, victim, attacker);
        }
        if (victim instanceof ServerPlayer player) {
            if (source != null && source != victim) {
                reduceByDeathMemory(event, player, source);
            }
            guardVictim(event, player);
        }
    }

    /**
     * 茧：孵化前受到的伤害归零（虚空等强制死亡除外）。
     * goodbye：起手期间归零（无敌帧），起手结束后的窗口内减伤。
     */
    private static void guardVictim(LivingHurtEvent event, ServerPlayer player) {
        if (event.getAmount() <= 0.0f) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        String msgId = event.getSource().m_19385_();
        if ("outOfWorld".equals(msgId) || "genericKill".equals(msgId)) {
            return;
        }
        long now = player.m_284548_().m_46467_();
        if (NtForms.inCocoon(player, data) || data.value(NtForms.KEY_GB_STRIKE_AT, 0.0) > (double) now) {
            event.setAmount(0.0f);
            return;
        }
        if (data.value(NtForms.KEY_GB_GUARD_UNTIL, 0.0) > (double) now) {
            event.setAmount((float) (event.getAmount() * (1.0 - GoodbyeSkill.DAMAGE_REDUCTION)));
        }
    }

    /** 死亡记忆：对曾经击杀过自己的人，伤害按记录次数减免。 */
    private static void reduceByDeathMemory(LivingHurtEvent event, ServerPlayer player, Entity killer) {
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, DEATH_MEMORY);
        if (trait.isEmpty()) {
            return;
        }
        double stacks = data.value(NtForms.KEY_DM + killer.m_20148_(), 0.0);
        if (stacks < 1.0) {
            return;
        }
        double base = TraitLookup.param(trait.get(), "damage_reduction_vs_killer", 0.5);
        double step = TraitLookup.param(trait.get(), "stack_per_death", 0.5);
        double cap = TraitLookup.param(trait.get(), "max_reduction", 0.99);
        double reduction = Math.min(cap, base + (stacks - 1.0) * step);
        event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
    }

    /**
     * 形态输出（最后执行，早于原版护甲减伤）：
     * 锤（form 0）：玩家的攻击伤害 ×(1 + HAMMER_ATTACK_BONUS)。
     * 手刀（form 1）：玩家的攻击无视目标护甲。原版护甲减伤在 LivingHurtEvent 之后才生效，
     * 所以这里把伤害反解成"经过护甲减伤后恰好等于原始伤害"的数值。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurtFormModifiers(LivingHurtEvent event) {
        Entity source = event.getSource().m_7639_();
        LivingEntity victim = event.getEntity();
        if (!(source instanceof ServerPlayer attacker) || attacker == victim || NtBleed.ticking) {
            return;
        }
        if (event.getAmount() <= 0.0f) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data == null) {
            return;
        }
        int form = NtForms.current(data);
        if (form == FORM_HAMMER) {
            event.setAmount((float) (event.getAmount() * (1.0 + HAMMER_ATTACK_BONUS)));
        } else if (form == FORM_BLADE) {
            event.setAmount(ignoreArmor(victim, event.getAmount()));
        }
    }

    /** 反解原版护甲减伤：找到一个输入伤害，使 CombatRules 减伤后等于 raw。 */
    static float ignoreArmor(LivingEntity victim, float raw) {
        float armor = (float) victim.m_21230_();
        float toughness = (float) victim.m_21133_(Attributes.f_22285_);
        if (armor <= 0.0f && toughness <= 0.0f) {
            return raw;
        }
        float lo = raw;
        float hi = raw * 2.0f;
        while (CombatRules.m_19272_(hi, armor, toughness) < raw && hi < raw * 1000.0f) {
            hi *= 2.0f;
        }
        for (int i = 0; i < 30; i++) {
            float mid = (lo + hi) * 0.5f;
            if (CombatRules.m_19272_(mid, armor, toughness) < raw) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) * 0.5f;
    }

    /** 适应：每次命中 +1%（无上限），切换形态时清零。 */
    private static void applyAdaptation(LivingHurtEvent event, ServerPlayer attacker) {
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data == null) {
            return;
        }
        Optional<TraitEntry> trait = TraitLookup.activePassive(data, ADAPTATION);
        if (trait.isEmpty()) {
            return;
        }
        double perHit = TraitLookup.param(trait.get(), "per_hit", 0.01);
        double stacks = data.value(NtForms.KEY_ADAPT, 0.0);
        event.setAmount((float) (event.getAmount() * (1.0 + stacks * perHit)));
        data.setValue(NtForms.KEY_ADAPT, stacks + 1.0);
        SystemDataManager.markDirty(attacker);
    }
}
