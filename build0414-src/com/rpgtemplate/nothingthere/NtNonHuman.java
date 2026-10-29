package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.TraitLookup;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 非人体质（原“迅捷”）：随等级增长。
 * <ul>
 *   <li>移动速度：MOVEMENT_SPEED 修饰符，MULTIPLY_BASE，= 等级 × speed_per_level × 总倍率，并受上限限制。</li>
 *   <li>跳跃：跳跃提升效果，每 jump_levels_per_amp 级提升一级，最高 max_jump_amplifier（0 = 跳跃提升 I）。</li>
 * </ul>
 * 总倍率与上限在服务端设置中（NtConfig）。
 */
final class NtNonHuman {
    static final UUID SPEED_ID = UUID.nameUUIDFromBytes("rpgtemplate:nt_non_human_speed".getBytes(StandardCharsets.UTF_8));
    /** 上一次写入的移速加成，玩家 UUID → 数值。数值不变时不写属性，避免每次都推送更新。 */
    private static final Map<UUID, Double> LAST_SPEED = new HashMap<>();

    private NtNonHuman() {
    }

    /** 移速加成（小数）。未激活时为 0。 */
    static double speedBonus(PlayerSystemData data) {
        if (data == null) {
            return 0.0;
        }
        Optional<TraitEntry> trait = NtPassives.active(data, NtPassives.NON_HUMAN);
        if (trait.isEmpty()) {
            return 0.0;
        }
        double perLevel = TraitLookup.param(trait.get(), "speed_per_level", 0.01);
        double raw = data.level() * perLevel * NtConfig.speedScale();
        return Math.max(0.0, Math.min(NtConfig.speedCap(), raw));
    }

    /** 跳跃提升的等级（效果的 amplifier）。被动激活时至少为 0（跳跃提升 I）。 */
    static int jumpAmplifier(PlayerSystemData data, TraitEntry trait) {
        double levelsPerAmp = TraitLookup.param(trait, "jump_levels_per_amp", 15.0);
        int max = (int) TraitLookup.param(trait, "max_jump_amplifier", 2.0);
        int amp = levelsPerAmp <= 0.0 ? 0 : (int) (data.level() / levelsPerAmp);
        return Math.max(0, Math.min(max, amp));
    }

    static void syncSpeed(ServerPlayer player, PlayerSystemData data) {
        double target = speedBonus(data);
        UUID owner = player.m_20148_();
        Double last = LAST_SPEED.get(owner);
        if (last != null && last == target) {
            return;
        }
        LAST_SPEED.put(owner, target);
        AttributeInstance inst = player.m_21051_(Attributes.f_22279_);
        if (inst == null) {
            return;
        }
        inst.m_22120_(SPEED_ID);
        if (target > 0.0) {
            inst.m_22118_(new AttributeModifier(SPEED_ID, "rpgtemplate:nt_non_human_speed", target,
                AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }
}
