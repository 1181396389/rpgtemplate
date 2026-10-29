package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 形态效果（锤与利刃的基础伤害加成见 NothingThereCombat.onHurtFormModifiers）：
 * <ul>
 *   <li>骨刺连射（2）：受到伤害时反弹 25% 为技巧伤害，并以 15 tick 冷却自动向攻击者发射骨刺。</li>
 *   <li>贪食之口（3）：每次命中削减目标最大生命，见 NtBite。</li>
 *   <li>脊骨鞭（4）：攻击无视无敌帧（见 onAttack）、攻击距离 +2、攻速大幅提升；无攻击冷却见 OhManSkill。</li>
 *   <li>全收（5）：全属性提升（最大生命、攻击、护甲、韧性、移速、击退抗性、幸运）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NtFormEffects {
    static final int FORM_SPIKE = 2;
    static final int FORM_BITE = 3;
    static final int FORM_WHIP = 4;
    static final int FORM_MUSCLE = 5;

    static final double SPIKE_REFLECT = 0.25;   // 反弹伤害占受到伤害的比例
    static final double SPIKE_SHOT = 0.5;       // 自动发射骨刺的伤害占受到伤害的比例
    static final long SPIKE_COOLDOWN = 15L;     // 自动发射冷却（tick）

    /** 反伤造成的伤害正在结算时为 true。此时不再触发形态效果，避免递归。 */
    static boolean reflecting;

    private static final Map<UUID, Integer> LAST_FORM = new HashMap<>();
    private static final Map<UUID, Long> LAST_SPIKE = new HashMap<>();
    /** 利刃命中时被暂存的防御（吸收护盾、伤害抗性效果），在本 tick 结束时还原。 */
    private static final Map<LivingEntity, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        float absorb = -1.0F;
        MobEffectInstance resist;
    }

    private record Mod(Supplier<Attribute> attr, String id, double amount, AttributeModifier.Operation op) {
    }

    private static final List<Mod> WHIP_MODS = List.of(
        new Mod(() -> ForgeMod.ENTITY_REACH.get(), "nt_whip_reach", 2.0, AttributeModifier.Operation.ADDITION),
        new Mod(() -> Attributes.f_22283_, "nt_whip_attack_speed", 1000.0, AttributeModifier.Operation.ADDITION));

    private static final List<Mod> MUSCLE_MODS = List.of(
        new Mod(() -> Attributes.f_22276_, "nt_muscle_max_health", 0.1, AttributeModifier.Operation.MULTIPLY_BASE),
        new Mod(() -> Attributes.f_22281_, "nt_muscle_attack_damage", 0.1, AttributeModifier.Operation.MULTIPLY_BASE),
        new Mod(() -> Attributes.f_22283_, "nt_muscle_attack_speed", 0.1, AttributeModifier.Operation.MULTIPLY_BASE),
        new Mod(() -> Attributes.f_22279_, "nt_muscle_movement_speed", 0.1, AttributeModifier.Operation.MULTIPLY_BASE),
        new Mod(() -> Attributes.f_22284_, "nt_muscle_armor", 2.0, AttributeModifier.Operation.ADDITION),
        new Mod(() -> Attributes.f_22285_, "nt_muscle_armor_toughness", 1.0, AttributeModifier.Operation.ADDITION),
        new Mod(() -> Attributes.f_22278_, "nt_muscle_knockback_resistance", 0.1, AttributeModifier.Operation.ADDITION),
        new Mod(() -> Attributes.f_22286_, "nt_muscle_luck", 1.0, AttributeModifier.Operation.ADDITION));

    private NtFormEffects() {
    }

    static UUID uuid(String id) {
        return UUID.nameUUIDFromBytes(("rpgtemplate:" + id).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 按当前形态同步属性修饰符。只在形态变化时修改，避免每次都推送属性更新包。
     * 在 LAST_FORM 中找不到该玩家（刚登录或服务器刚启动）时，会先清掉所有相关修饰符再重新应用。
     */
    static void syncAttributes(ServerPlayer player, int form) {
        UUID owner = player.m_20148_();
        Integer last = LAST_FORM.get(owner);
        if (last != null && last == form) {
            return;
        }
        LAST_FORM.put(owner, form);
        apply(player, WHIP_MODS, form == FORM_WHIP);
        apply(player, MUSCLE_MODS, form == FORM_MUSCLE);
    }

    private static void apply(ServerPlayer player, List<Mod> mods, boolean on) {
        for (Mod mod : mods) {
            AttributeInstance inst = player.m_21051_(mod.attr().get());
            if (inst == null) {
                continue;
            }
            UUID id = uuid(mod.id());
            inst.m_22120_(id);
            if (on) {
                inst.m_22118_(new AttributeModifier(id, mod.id(), mod.amount(), mod.op()));
            }
        }
    }

    /** 脊骨鞭：攻击无视被攻击者的无敌帧。在 LivingAttackEvent 中清零 invulnerableTime，之后的伤害才能生效。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getSource().m_7639_() instanceof ServerPlayer attacker) || attacker == event.getEntity()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.f_19802_ <= 0) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data != null && NtForms.current(data) == FORM_WHIP) {
            victim.f_19802_ = 0;
        }
    }

    /** 攻击者命中目标后的效果（由 NothingThereCombat.onHurt 调用，只处理玩家造成的伤害）。 */
    static void onPlayerHit(ServerPlayer attacker, LivingEntity victim, float amount) {
        if (reflecting || amount <= 0.0F || !victim.m_6084_()) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(attacker);
        if (data != null && NtForms.current(data) == FORM_BITE) {
            NtBite.hit(victim);
        }
    }

    /** 玩家受到伤害后的效果（骨刺连射）。在守护结算之后调用，伤害已被守护清零时不反弹。 */
    static void onPlayerHurt(ServerPlayer player, LivingEntity attacker, float amount) {
        if (reflecting || amount <= 0.0F || attacker == player || !attacker.m_6084_()) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null || NtForms.current(data) != FORM_SPIKE) {
            return;
        }
        reflecting = true;
        try {
            TargetUtil.hurt(attacker, ModDamageTypes.technique(player), (float) (amount * SPIKE_REFLECT));
        } finally {
            reflecting = false;
        }
        long now = player.m_9236_().m_46467_();
        UUID owner = player.m_20148_();
        long last = LAST_SPIKE.getOrDefault(owner, Long.MIN_VALUE / 2);
        if (now - last >= SPIKE_COOLDOWN) {
            LAST_SPIKE.put(owner, now);
            HolleSpike.launchToward(player, attacker, (float) (amount * SPIKE_SHOT));
        }
    }

    /**
     * 利刃：本次命中不受目标的吸收护盾与伤害抗性（药水效果，I–V 级全部）影响。
     * 命中前把护盾清零、把抗性效果移除并暂存；本 tick 结束时还原。
     * 所以这次伤害不会消耗护盾，抗性效果也不会因此丢失。
     */
    static void suspendDefenses(LivingEntity victim) {
        float absorb = victim.m_6103_();
        MobEffectInstance resist = victim.m_21124_(MobEffects.f_19606_);
        if (absorb <= 0.0F && resist == null) {
            return;
        }
        Pending pending = PENDING.computeIfAbsent(victim, k -> new Pending());
        if (absorb > 0.0F && pending.absorb < 0.0F) {
            pending.absorb = absorb;
            victim.m_7911_(0.0F);
        }
        if (resist != null && pending.resist == null) {
            pending.resist = new MobEffectInstance(resist);
            victim.m_21195_(MobEffects.f_19606_);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Pending> entry = it.next();
            LivingEntity victim = entry.getKey();
            Pending pending = entry.getValue();
            if (victim.m_6084_()) {
                if (pending.absorb >= 0.0F) {
                    victim.m_7911_(pending.absorb);
                }
                if (pending.resist != null && victim.m_21124_(MobEffects.f_19606_) == null) {
                    victim.m_7292_(pending.resist);
                }
            }
            it.remove();
        }
    }
}
