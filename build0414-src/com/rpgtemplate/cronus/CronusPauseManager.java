package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 假面骑士 Cronus 核心机制：【Pause（时间暂停）】与【Restart（时间重开）】领域管理器。
 * <ul>
 *   <li>严格执行四档克制判定（{@link RuleJudge}）：完全生效冻结实体与投射物、显著干扰重度迟缓、微弱牵制轻度减速、判定失败触发悖论反噬。</li>
 *   <li>时停期间对冻结目标造成的伤害转入「时停悬停伤害池」，在【Restart】瞬间统一以 1.20x（回旋踢引爆为 1.35x）倍率结算爆发。</li>
 *   <li>支持时停专属悬停陷阱（断罪十字刻四刃、全方位死角包围网 12 光弹）在【Restart】时同步收束引爆。</li>
 * </ul>
 */
public final class CronusPauseManager {
    private static final Map<UUID, PauseDomain> DOMAINS = new HashMap<>();
    /** 全局防递归标记：正在执行 Restart 结算伤害时不再二次拦截进悬停池。 */
    private static boolean resolvingRestart = false;

    public static final class FrozenMobState {
        final double x;
        final double y;
        final double z;
        final float yRot;
        final float xRot;
        final boolean origNoAi;
        final boolean origNoGravity;
        final long frozenSince;

        FrozenMobState(LivingEntity e, boolean origNoAi, boolean origNoGravity, long now) {
            this.x = e.m_20185_();
            this.y = e.m_20186_();
            this.z = e.m_20189_();
            this.yRot = e.m_146908_();
            this.xRot = e.m_146909_();
            this.origNoAi = origNoAi;
            this.origNoGravity = origNoGravity;
            this.frozenSince = now;
        }
    }

    public static final class FrozenProjectileState {
        final Vec3 velocity;
        final boolean origNoGravity;

        FrozenProjectileState(Vec3 velocity, boolean origNoGravity) {
            this.velocity = velocity;
            this.origNoGravity = origNoGravity;
        }
    }

    public static final class SuspendedTrap {
        final LivingEntity target;
        final float damage;
        final int kind; // 0 = 断罪十字刻, 1 = 全方位包围网

        SuspendedTrap(LivingEntity target, float damage, int kind) {
            this.target = target;
            this.damage = damage;
            this.kind = kind;
        }
    }

    public static final class PauseDomain {
        final UUID ownerId;
        final ResourceKey<Level> dimension;
        double cx;
        double cy;
        double cz;
        final double radius;
        final long startedAt;
        long lastJudgeTick;

        final Map<LivingEntity, FrozenMobState> frozenEntities = new WeakHashMap<>();
        final Map<LivingEntity, RuleJudge.Tier> judgedTiers = new WeakHashMap<>();
        final Map<Projectile, FrozenProjectileState> frozenProjectiles = new WeakHashMap<>();
        final Map<LivingEntity, Float> deferredDamage = new WeakHashMap<>();
        final List<SuspendedTrap> traps = new ArrayList<>();

        PauseDomain(ServerPlayer owner, double radius, long now) {
            this.ownerId = owner.m_20148_();
            this.dimension = owner.m_9236_().m_46472_();
            this.cx = owner.m_20185_();
            this.cy = owner.m_20186_();
            this.cz = owner.m_20189_();
            this.radius = radius;
            this.startedAt = now;
            this.lastJudgeTick = now - 20L;
        }

        double totalDeferredDamage() {
            double sum = 0.0;
            for (Float v : deferredDamage.values()) {
                if (v != null && v > 0f) {
                    sum += v;
                }
            }
            for (SuspendedTrap trap : traps) {
                if (trap.target != null && trap.target.m_6084_()) {
                    sum += trap.damage;
                }
            }
            return sum;
        }
    }

    private CronusPauseManager() {
    }

    public static boolean isResolvingRestart() {
        return resolvingRestart;
    }

    public static PauseDomain getDomain(ServerPlayer player) {
        return player == null ? null : DOMAINS.get(player.m_20148_());
    }

    /** 判断指定目标当前是否正被该 Cronus 的【Pause】完全冻结（Tier == FULL）。 */
    public static boolean isTargetFullFrozenBy(ServerPlayer cronus, LivingEntity target) {
        PauseDomain d = getDomain(cronus);
        if (d == null || target == null) {
            return false;
        }
        return d.judgedTiers.get(target) == RuleJudge.Tier.FULL;
    }

    /** 开启【Pause】时钟领域。 */
    public static boolean startPause(ServerPlayer player, PlayerSystemData data, long now) {
        if (DOMAINS.containsKey(player.m_20148_())) {
            return false;
        }
        double radius = 12.0 + Math.min(8.0, data.level() * 0.14);
        PauseDomain domain = new PauseDomain(player, radius, now);
        DOMAINS.put(player.m_20148_(), domain);

        data.setValue(CronusKeys.KEY_PAUSED, 1.0);
        data.setValue(CronusKeys.KEY_PENDING_DMG, 0.0);
        updateRemainingSecHud(data, drainPerTick(data, now) * 20.0);
        SystemDataManager.markDirty((Player) player);

        CronusSounds.playPause(player);
        if (player.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, domain.cx, domain.cy, domain.cz,
                new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_PAUSE, player.m_19879_(),
                    domain.cx, domain.cy + 0.05, domain.cz, (float) radius, 200));
        }
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.pause_on")
            .m_130940_(ChatFormatting.GREEN));
        tickDomain(player, data, domain, now);
        return true;
    }

    /** 向当前时停领域注册悬停必杀构造物（Restart 时一并收束引爆）。 */
    public static void addSuspendedTrap(ServerPlayer player, PlayerSystemData data,
                                        LivingEntity target, float damage, int trapKind) {
        PauseDomain d = getDomain(player);
        if (d == null || target == null) {
            return;
        }
        d.traps.add(new SuspendedTrap(target, damage, trapKind));
        data.setValue(CronusKeys.KEY_PENDING_DMG, d.totalDeferredDamage());
        SystemDataManager.markDirty((Player) player);
        if (player.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.5, target.m_20189_(),
                new CronusFxMsg(CronusFxMsg.KIND_PAUSE_TRAP, target.m_19879_(),
                    target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.5, target.m_20189_(),
                    1.0f, trapKind));
        }
    }

    /** 若攻击者处于时停且目标被完全冻结，将本次伤害压入时停悬停伤害池。返回 true 表示已拦截悬停。 */
    public static boolean interceptDeferredDamage(ServerPlayer attacker, PlayerSystemData data,
                                                  LivingEntity target, float amount) {
        if (resolvingRestart || amount <= 0f) {
            return false;
        }
        PauseDomain d = getDomain(attacker);
        if (d == null || d.judgedTiers.get(target) != RuleJudge.Tier.FULL) {
            return false;
        }
        d.deferredDamage.merge(target, amount, Float::sum);
        data.setValue(CronusKeys.KEY_PENDING_DMG, d.totalDeferredDamage());
        SystemDataManager.markDirty((Player) attacker);
        CronusNet.popupOn(target, amount >= 18.0f ? CronusFxMsg.POPUP_GREAT : CronusFxMsg.POPUP_HIT);
        return true;
    }

    /** 触发【Restart（时间重开）】：解冻所有实体与投射物，并以指定倍率一次性引爆全部悬停伤害。 */
    public static void restart(ServerPlayer player, PlayerSystemData data,
                               double burstMultiplier, boolean notifyActionbar) {
        PauseDomain d = player == null ? null : DOMAINS.remove(player.m_20148_());
        if (data != null) {
            data.setValue(CronusKeys.KEY_PAUSED, 0.0);
            data.setValue(CronusKeys.KEY_PENDING_DMG, 0.0);
            data.setValue(CronusKeys.KEY_PAUSE_LEFT_SEC, 0.0);
            if (player != null) {
                SystemDataManager.markDirty((Player) player);
            }
        }
        if (d == null) {
            return;
        }

        unfreezeAll(d);

        if (player != null && player.m_6084_()) {
            CronusSounds.playRestart(player);
            if (player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, d.cx, d.cy, d.cz,
                    new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_RESTART, player.m_19879_(),
                        d.cx, d.cy + 0.05, d.cz, (float) d.radius, 0));
            }
            resolvingRestart = true;
            try {
                for (SuspendedTrap trap : d.traps) {
                    if (trap.target != null && trap.target.m_6084_()) {
                        d.deferredDamage.merge(trap.target, trap.damage, Float::sum);
                    }
                }
                for (Map.Entry<LivingEntity, Float> entry : d.deferredDamage.entrySet()) {
                    LivingEntity victim = entry.getKey();
                    Float raw = entry.getValue();
                    if (victim != null && victim.m_6084_() && raw != null && raw > 0.0f) {
                        float finalBurst = (float) (raw * burstMultiplier);
                        TargetUtil.hurt(victim, ModDamageTypes.technique(player), finalBurst);
                        CronusNet.popupOn(victim, CronusFxMsg.POPUP_CRITICAL);
                    }
                }
            } finally {
                resolvingRestart = false;
            }
            if (notifyActionbar) {
                Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.restart")
                    .m_130940_(ChatFormatting.GOLD));
            }
        }
    }

    /** 每 tick 驱动所有在线 Cronus 的【Pause】领域。 */
    public static void tickPlayerPause(ServerPlayer player, PlayerSystemData data, long now) {
        PauseDomain d = DOMAINS.get(player.m_20148_());
        if (d == null) {
            if (CronusKeys.isPaused(data)) {
                data.setValue(CronusKeys.KEY_PAUSED, 0.0);
                SystemDataManager.markDirty((Player) player);
            }
            return;
        }
        if (!player.m_6084_() || !CronusKeys.isTransformed(data)
            || !player.m_9236_().m_46472_().equals(d.dimension)) {
            restart(player, data, 1.0, false);
            return;
        }

        // 每 tick 消耗能量并累积微量 Bugster 负荷；能量耗尽时自动触发 Restart
        double mpDrain = drainPerTick(data, now);
        if (data.mp() < mpDrain) {
            restart(player, data, 1.20, true);
            return;
        }
        data.setMp(Math.max(0.0, data.mp() - mpDrain));
        updateRemainingSecHud(data, mpDrain * 20.0);
        if (now % 20L == 0L) {
            CronusKeys.addViralStrain(player, data, 2.2, now);
            if (!DOMAINS.containsKey(player.m_20148_())) {
                return; // 过载满 100% 时已在 addViralStrain 内解除时停
            }
            // 每秒刷新一次客户端时钟领域特效锚点，令时钟跟随管理员推进
            d.cx = player.m_20185_();
            d.cy = player.m_20186_();
            d.cz = player.m_20189_();
            if (player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, d.cx, d.cy, d.cz,
                    new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_PAUSE, player.m_19879_(),
                        d.cx, d.cy + 0.05, d.cz, (float) d.radius, 30));
            }
        }

        // 被动【编年史管理员权限】：自身在 Pause 领域内免疫迟缓并获得迅捷步伐
        player.m_21195_(MobEffects.f_19597_);
        player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 15, 1, true, false, false));

        tickDomain(player, data, d, now);
    }

    private static double drainPerTick(PlayerSystemData data, long now) {
        double maxMp = CronusKeys.maxStat(data, StatType.MP);
        // 基础每秒约消耗 3.8% 最大能量 + 固定 1.4 点（低级玩家难以长时间维持，高级玩家可从容控场）
        double perSec = Math.max(2.6, maxMp * 0.038 + 1.4);
        return perSec / 20.0;
    }

    private static void updateRemainingSecHud(PlayerSystemData data, double drainPerSec) {
        double sec = drainPerSec > 1e-4 ? data.mp() / drainPerSec : 0.0;
        data.setValue(CronusKeys.KEY_PAUSE_LEFT_SEC, Math.max(0.0, sec));
    }

    private static void tickDomain(ServerPlayer cronus, PlayerSystemData data, PauseDomain d, long now) {
        AABB box = new AABB(
            d.cx - d.radius, d.cy - d.radius * 0.75, d.cz - d.radius,
            d.cx + d.radius, d.cy + d.radius * 0.95, d.cz + d.radius);
        double rSq = d.radius * d.radius;

        boolean rejudge = (now - d.lastJudgeTick) >= 15L;
        if (rejudge) {
            d.lastJudgeTick = now;
        }

        // 1. 判定并冻结领域内生物
        List<LivingEntity> livings = cronus.m_9236_().m_45976_(LivingEntity.class, box);
        for (LivingEntity target : livings) {
            if (target == cronus || !target.m_6084_() || target.m_20275_(d.cx, target.m_20186_(), d.cz) > rSq) {
                continue;
            }
            RuleJudge.Tier tier = d.judgedTiers.get(target);
            if (tier == null || rejudge) {
                RuleJudge.Result res = RuleJudge.evaluate(cronus, data, target, StatType.MP);
                if (tier == null) {
                    RuleJudge.notify(cronus, target, "Pause·时间暂停", res);
                }
                tier = res.tier();
                d.judgedTiers.put(target, tier);
                if (tier == RuleJudge.Tier.FAIL && rejudge) {
                    // 判定失败：目标挣脱时间法则，管理员承受悖论反噬
                    CronusKeys.addViralStrain(cronus, data, 6.0, now);
                }
            }
            applyTierFreeze(target, tier, d, now);
        }

        // 释放已经离开领域或失效的生物
        Iterator<Map.Entry<LivingEntity, FrozenMobState>> it = d.frozenEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, FrozenMobState> e = it.next();
            LivingEntity ent = e.getKey();
            if (ent == null || !ent.m_6084_()
                || ent.m_20275_(d.cx, ent.m_20186_(), d.cz) > (d.radius + 2.5) * (d.radius + 2.5)
                || d.judgedTiers.get(ent) != RuleJudge.Tier.FULL) {
                restoreEntity(ent, e.getValue());
                it.remove();
            }
        }

        // 2. 冻结领域内所有非管理员自身的飞行投射物（箭矢、三叉戟、火球等定格半空）
        List<Projectile> projectiles = cronus.m_9236_().m_45976_(Projectile.class, box);
        for (Projectile proj : projectiles) {
            if (!proj.m_6084_() || proj.m_19749_() == cronus) {
                continue;
            }
            if (proj.m_20275_(d.cx, proj.m_20186_(), d.cz) > rSq) {
                continue;
            }
            d.frozenProjectiles.computeIfAbsent(proj, p -> new FrozenProjectileState(p.m_20184_(), p.m_20068_()));
            proj.m_20242_(true);
            proj.m_20256_(Vec3.f_82478_);
            proj.f_19864_ = true;
        }
    }

    private static void applyTierFreeze(LivingEntity target, RuleJudge.Tier tier, PauseDomain d, long now) {
        switch (tier) {
            case FULL -> {
                FrozenMobState st = d.frozenEntities.computeIfAbsent(target, t -> {
                    boolean noAi = (t instanceof Mob mob) && mob.m_21525_();
                    return new FrozenMobState(t, noAi, t.m_20068_(), now);
                });
                if (target instanceof Player) {
                    // PvP 保护：前 3.5 秒强定身锚定，随后降级为重度迟缓以保留博弈空间
                    if (now - st.frozenSince <= 70L) {
                        target.m_6021_(st.x, st.y, st.z);
                        target.m_20256_(Vec3.f_82478_);
                        target.f_19864_ = true;
                        target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 15, 6, false, false, true));
                    } else {
                        target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 15, 3, false, false, true));
                    }
                } else {
                    if (target instanceof Mob mob) {
                        mob.m_21557_(true);
                    }
                    target.m_20242_(true);
                    target.m_20256_(Vec3.f_82478_);
                    target.m_7678_(st.x, st.y, st.z, st.yRot, st.xRot);
                }
            }
            case MAJOR -> {
                Vec3 v = target.m_20184_();
                target.m_20256_(new Vec3(v.f_82479_ * 0.35, Math.min(0.02, v.f_82480_ * 0.5), v.f_82481_ * 0.35));
                target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 20, 3, false, false, true));
                target.m_7292_(new MobEffectInstance(MobEffects.f_19613_, 20, 0, false, false, true));
            }
            case MINOR -> target.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 20, 1, false, false, true));
            case FAIL -> {
                // 完全免疫
            }
        }
    }

    private static void unfreezeAll(PauseDomain d) {
        for (Map.Entry<LivingEntity, FrozenMobState> e : d.frozenEntities.entrySet()) {
            restoreEntity(e.getKey(), e.getValue());
        }
        d.frozenEntities.clear();
        for (Map.Entry<Projectile, FrozenProjectileState> e : d.frozenProjectiles.entrySet()) {
            Projectile p = e.getKey();
            FrozenProjectileState st = e.getValue();
            if (p != null && p.m_6084_() && st != null) {
                p.m_20242_(st.origNoGravity);
                p.m_20256_(st.velocity);
                p.f_19864_ = true;
            }
        }
        d.frozenProjectiles.clear();
    }

    private static void restoreEntity(LivingEntity ent, FrozenMobState st) {
        if (ent == null || st == null) {
            return;
        }
        if (ent instanceof Mob mob) {
            mob.m_21557_(st.origNoAi);
        }
        ent.m_20242_(st.origNoGravity);
    }

    public static void forceClearFor(UUID playerId) {
        PauseDomain d = DOMAINS.remove(playerId);
        if (d != null) {
            unfreezeAll(d);
        }
    }
}
