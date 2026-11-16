package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 光线枪模式直接激光射线控制器（必杀技一双形态）：
 * <ul>
 *   <li><b>常态（非时停）· 持续递增翡翠激光</b>：持续 3.0s（60 ticks），实时跟随玩家准星扫描 34m，
 *       每 2 ticks 结算一次并<b>无视无敌帧</b>，伤害从低起手（0.14x）平滑飙升至炽热高爆（0.75x/2ticks），
 *       激光光柱随伤害飙升同步变粗变亮！</li>
 *   <li><b>时停（Pause）· 瞬发无视护甲贯穿巨炮激光</b>：一次性射出 42m 超粗翡翠金绿激光射线，
 *       贯穿全部路径目标造成 2.15x 巨额伤害，且使用 {@code sonic_boom} 源完全无视护甲与盾牌！</li>
 * </ul>
 */
public final class CronusLaserBeamManager {
    private static final int CHANNEL_DURATION_TICKS = 60; // 3.0 秒持续绿色激光
    private static final double CHANNEL_RANGE = 34.0;
    private static final double PAUSE_LASER_RANGE = 42.0;

    private static final class ActiveChannel {
        final float baseSkillDamage;
        int ticksElapsed;
        final Map<UUID, Integer> targetFocusTicks = new HashMap<>();

        ActiveChannel(float baseSkillDamage) {
            this.baseSkillDamage = baseSkillDamage;
        }
    }

    private static final Map<UUID, ActiveChannel> CHANNELS = new HashMap<>();

    private CronusLaserBeamManager() {
    }

    /** 开启光线枪模式（非时停）必杀技一：持续 3 秒递增无视无敌帧绿色激光。 */
    public static void startContinuousLaser(ServerPlayer player, float baseSkillDamage) {
        if (player == null) {
            return;
        }
        CHANNELS.put(player.m_20148_(), new ActiveChannel(baseSkillDamage));
    }

    public static void stopFor(UUID playerId) {
        CHANNELS.remove(playerId);
    }

    /** 服务端每 tick 驱动持续绿色激光射线：实时更新光束终点、递增伤害并无视无敌帧！ */
    public static void tickPlayer(ServerPlayer player, PlayerSystemData data, long now) {
        ActiveChannel ch = CHANNELS.get(player.m_20148_());
        if (ch == null) {
            return;
        }
        if (!player.m_6084_()
            || data == null
            || !CronusKeys.isTransformed(data)
            || CronusKeys.weaponMode(data) != CronusKeys.MODE_GUN
            || CronusKeys.isPaused(data)) {
            CHANNELS.remove(player.m_20148_());
            return;
        }

        ch.ticksElapsed++;
        if (ch.ticksElapsed > CHANNEL_DURATION_TICKS) {
            CHANNELS.remove(player.m_20148_());
            return;
        }

        float progress = Math.min(1.0f, ch.ticksElapsed / (float) CHANNEL_DURATION_TICKS);
        Vec3 muzzle = computeGunMuzzle(player);
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20154_().m_82541_();
        Vec3 maxEnd = eye.m_82549_(look.m_82490_(CHANNEL_RANGE));

        BlockHitResult bHit = player.m_9236_().m_45547_(new ClipContext(
            eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 beamEnd = (bHit.m_6662_() != HitResult.Type.MISS) ? bHit.m_82450_() : maxEnd;
        double limitDist = Math.max(2.0, eye.m_82554_(beamEnd));

        // 每 2 ticks 结算一次无视无敌帧的递增激光伤害（起步低 -> 随持续时间 & 持续灼烧同一目标越来越高！）
        if (ch.ticksElapsed % 2 == 1) {
            double beamHalfWidth = 0.55 + progress * 0.35;
            List<TargetUtil.Hit> hits = TargetUtil.ray(player, limitDist, 6, beamHalfWidth);

            // 起手低（0.14x），随持续时间二次曲线飙升至 0.62x，对同一目标持续照射再叠加最高 +35% 熔毁倍率！
            float baseMult = 0.14f + 0.48f * (progress * progress);
            boolean suppressPopup = (ch.ticksElapsed % 6 != 1);

            CronusCombatAndPassives.setSuppressRapidPopup(suppressPopup);
            try {
                for (TargetUtil.Hit h : hits) {
                    LivingEntity victim = TargetUtil.living(h.entity());
                    if (victim == null || !victim.m_6084_()) {
                        continue;
                    }
                    int focus = ch.targetFocusTicks.merge(victim.m_20148_(), 1, Integer::sum);
                    float focusMult = 1.0f + Math.min(0.35f, (focus - 1) * 0.03f);
                    float pulseDmg = ch.baseSkillDamage * baseMult * focusMult;

                    // 无视原版无敌帧！每次激光脉冲全额结算！
                    victim.f_19802_ = 0;
                    TargetUtil.hurt(victim, ModDamageTypes.technique(player), pulseDmg);
                }
            } finally {
                CronusCombatAndPassives.setSuppressRapidPopup(false);
            }
        }

        // 每 tick 同步连续激光射线坐标与当前炽热强度（0..1），客户端呈现丝滑 60fps 持续绿激光！
        if (player.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_,
                new CronusFxMsg(CronusFxMsg.KIND_CONTINUOUS_BEAM, player.m_19879_(),
                    muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_,
                    beamEnd.f_82479_, beamEnd.f_82480_, beamEnd.f_82481_,
                    progress, 0));
        }
    }

    /**
     * 时停下光线枪必杀技一：将巨型绿色激光一次性射出，贯穿全路径造成大量伤害且<b>完全无视护甲</b>！
     */
    public static void firePauseArmorPiercingLaser(ServerPlayer player, float baseSkillDamage) {
        if (player == null || !(player.m_9236_() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 muzzle = computeGunMuzzle(player);
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20154_().m_82541_();
        Vec3 maxEnd = eye.m_82549_(look.m_82490_(PAUSE_LASER_RANGE));

        BlockHitResult bHit = sl.m_45547_(new ClipContext(
            eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 beamEnd = (bHit.m_6662_() != HitResult.Type.MISS) ? bHit.m_82450_() : maxEnd;
        double limitDist = Math.max(3.0, eye.m_82554_(beamEnd));

        // 一次性巨额无视护甲伤害（2.15x 技能基础伤，sonic_boom 穿透护甲与盾牌）
        float massiveDmg = baseSkillDamage * 2.15f;
        DamageSource armorPiercingSrc = player.m_269291_().m_269285_(player); // sonic_boom (bypasses_armor & shield)

        List<TargetUtil.Hit> hits = TargetUtil.ray(player, limitDist, 12, 1.05);
        int boltFx = 0;
        for (TargetUtil.Hit h : hits) {
            LivingEntity victim = TargetUtil.living(h.entity());
            if (victim == null || !victim.m_6084_()) {
                continue;
            }
            victim.f_19802_ = 0;
            TargetUtil.hurt(victim, armorPiercingSrc, massiveDmg);
            if (boltFx++ < 4) {
                CronusNet.sendNear(sl, victim.m_20185_(), victim.m_20186_(), victim.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1,
                        victim.m_20185_(), victim.m_20186_() + victim.m_20206_() * 0.5, victim.m_20189_(),
                        2.3f, 2));
            }
        }

        // 发射超粗巨型翡翠金绿贯穿主炮射线（22 ticks 滞留 + 6 道扩张时钟环）
        CronusNet.sendNear(sl, muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_,
            new CronusFxMsg(CronusFxMsg.KIND_BEAM_LINE, player.m_19879_(),
                muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_,
                beamEnd.f_82479_, beamEnd.f_82480_, beamEnd.f_82481_,
                1.0f, CronusFxMsg.BEAM_PAUSE_CANNON));
        CronusNet.sendNear(sl, beamEnd.f_82479_, beamEnd.f_82480_, beamEnd.f_82481_,
            new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1,
                beamEnd.f_82479_, beamEnd.f_82480_, beamEnd.f_82481_,
                3.2f, 2));
    }

    public static Vec3 computeGunMuzzle(ServerPlayer player) {
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20154_().m_82541_();
        Vec3 right = look.m_82537_(new Vec3(0.0, 1.0, 0.0));
        right = right.m_82556_() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.m_82541_();
        return eye.m_82549_(right.m_82490_(0.32)).m_82520_(0.0, -0.16, 0.0).m_82549_(look.m_82490_(0.78));
    }
}
