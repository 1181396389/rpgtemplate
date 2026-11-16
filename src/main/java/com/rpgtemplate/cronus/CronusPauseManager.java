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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 假面骑士 Cronus 核心机制：【Pause（时间暂停）】与【Restart（时间重开）】领域管理器。
 * <ul>
 *   <li>严格执行四档克制判定（{@link RuleJudge}）：完全生效冻结实体全身姿态、投射物、TNT 引信、
 *       下落方块、掉落物、经验球与滞留药水云；显著干扰重度迟缓；微弱牵制轻度减速；判定失败触发悖论反噬。</li>
 *   <li>时停期间对冻结目标造成的伤害、连续命中连击加成与击退动量转入「时停悬停池」，并在客户端锚定发光时空斩痕；
 *       在【Restart】瞬间穿透无敌帧统一引爆并同步释放累积击退冲击。</li>
 *   <li>实时向周边客户端同步被完全冻结的实体 ID 快照，驱动客户端真·定格渲染（肢体/头部/呼吸/弹道零抖动）。</li>
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
        final float yHeadRot;
        final float yBodyRot;
        final boolean origNoAi;
        final boolean origNoGravity;
        final int origFireTicks;
        final float origFallDist;
        final long frozenSince;

        FrozenMobState(LivingEntity e, boolean origNoAi, boolean origNoGravity, long now) {
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
            this.yRot = e.getYRot();
            this.xRot = e.getXRot();
            this.yHeadRot = e.getYHeadRot();
            this.yBodyRot = e.yBodyRot;
            this.origNoAi = origNoAi;
            this.origNoGravity = origNoGravity;
            this.origFireTicks = e.getRemainingFireTicks();
            this.origFallDist = e.fallDistance;
            this.frozenSince = now;
        }
    }

    public static final class FrozenProjectileState {
        final double x;
        final double y;
        final double z;
        final Vec3 velocity;
        final boolean origNoGravity;

        FrozenProjectileState(Projectile p) {
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
            this.velocity = p.getDeltaMovement();
            this.origNoGravity = p.isNoGravity();
        }
    }

    /** 冻结的非生物世界物体状态（TNT 引信、下落方块、掉落物、经验球、滞留药水云）。 */
    public static final class FrozenWorldObjState {
        final double x;
        final double y;
        final double z;
        final Vec3 velocity;
        final boolean origNoGravity;
        final int savedInt; // PrimedTnt.fuse / FallingBlockEntity.time / AreaEffectCloud.duration

        FrozenWorldObjState(Entity e, int savedInt) {
            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
            this.velocity = e.getDeltaMovement();
            this.origNoGravity = e.isNoGravity();
            this.savedInt = savedInt;
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
        final int ownerEntityId;
        final ResourceKey<Level> dimension;
        double cx;
        double cy;
        double cz;
        final double radius;
        final long startedAt;
        long lastJudgeTick;
        long lastFrozenSyncTick;

        final Map<LivingEntity, FrozenMobState> frozenEntities = new WeakHashMap<>();
        final Map<LivingEntity, RuleJudge.Tier> judgedTiers = new WeakHashMap<>();
        final Map<Projectile, FrozenProjectileState> frozenProjectiles = new WeakHashMap<>();
        final Map<Entity, FrozenWorldObjState> frozenWorldObjs = new WeakHashMap<>();
        final Map<LivingEntity, Float> deferredDamage = new WeakHashMap<>();
        /** 时停中无视护甲（如光线枪必杀技一）所积蓄的悬停伤害，Restart 时以 sonic_boom 无视护甲源爆发结算。 */
        final Map<LivingEntity, Float> deferredArmorPiercingDamage = new WeakHashMap<>();
        /** 时停中累积的延迟击退动量向量，Restart 爆发瞬间一次性释放送飞目标。 */
        final Map<LivingEntity, Vec3> deferredKnockback = new WeakHashMap<>();
        /** 时停中对各目标连续击打次数（最高提供 +25% 协同收束爆发）。 */
        final Map<LivingEntity, Integer> frozenHitCounts = new WeakHashMap<>();
        final List<SuspendedTrap> traps = new ArrayList<>();

        PauseDomain(ServerPlayer owner, double radius, long now) {
            this.ownerId = owner.getUUID();
            this.ownerEntityId = owner.getId();
            this.dimension = owner.level().dimension();
            this.cx = owner.getX();
            this.cy = owner.getY();
            this.cz = owner.getZ();
            this.radius = radius;
            this.startedAt = now;
            this.lastJudgeTick = now - 20L;
            this.lastFrozenSyncTick = now - 20L;
        }

        double totalDeferredDamage() {
            double sum = 0.0;
            for (Float v : deferredDamage.values()) {
                if (v != null && v > 0f) {
                    sum += v;
                }
            }
            for (Float v : deferredArmorPiercingDamage.values()) {
                if (v != null && v > 0f) {
                    sum += v;
                }
            }
            for (SuspendedTrap trap : traps) {
                if (trap.target != null && trap.target.isAlive()) {
                    sum += trap.damage;
                }
            }
            return sum;
        }

        int[] collectFrozenEntityIds() {
            Set<Integer> ids = new LinkedHashSet<>();
            for (LivingEntity le : frozenEntities.keySet()) {
                if (le != null && le.isAlive() && !(le instanceof Player)) {
                    ids.add(le.getId());
                }
            }
            for (Projectile p : frozenProjectiles.keySet()) {
                if (p != null && p.isAlive()) {
                    ids.add(p.getId());
                }
            }
            for (Entity obj : frozenWorldObjs.keySet()) {
                if (obj != null && obj.isAlive()) {
                    ids.add(obj.getId());
                }
            }
            int limit = Math.min(ids.size(), 128);
            int[] out = new int[limit];
            int idx = 0;
            for (Integer id : ids) {
                if (idx >= limit) {
                    break;
                }
                out[idx++] = id;
            }
            return out;
        }
    }

    private CronusPauseManager() {
    }

    public static boolean isResolvingRestart() {
        return resolvingRestart;
    }

    public static PauseDomain getDomain(ServerPlayer player) {
        return player == null ? null : DOMAINS.get(player.getUUID());
    }

    /** 判断指定目标当前是否正被该 Cronus 的【Pause】完全冻结（Tier == FULL）。 */
    public static boolean isTargetFullFrozenBy(ServerPlayer cronus, LivingEntity target) {
        PauseDomain d = getDomain(cronus);
        if (d == null || target == null) {
            return false;
        }
        return d.judgedTiers.get(target) == RuleJudge.Tier.FULL;
    }

    /** 判断指定生物当前是否被任意活跃的【Pause】领域完全冻结（用于拦截自然火焰/中毒/溺水等时间渗漏伤害）。 */
    public static boolean isEntityFullFrozenByAny(LivingEntity target) {
        if (target == null || DOMAINS.isEmpty()) {
            return false;
        }
        for (PauseDomain d : DOMAINS.values()) {
            if (d.judgedTiers.get(target) == RuleJudge.Tier.FULL && d.frozenEntities.containsKey(target)) {
                return true;
            }
        }
        return false;
    }

    /** 开启【Pause】时钟领域。 */
    public static boolean startPause(ServerPlayer player, PlayerSystemData data, long now) {
        if (DOMAINS.containsKey(player.getUUID())) {
            return false;
        }
        double radius = 13.0 + Math.min(9.0, data.level() * 0.15);
        PauseDomain domain = new PauseDomain(player, radius, now);
        DOMAINS.put(player.getUUID(), domain);

        data.setValue(CronusKeys.KEY_PAUSED, 1.0);
        data.setValue(CronusKeys.KEY_PENDING_DMG, 0.0);
        updateRemainingSecHud(data, drainPerTick(data, now) * 20.0);
        SystemDataManager.markDirty((Player) player);

        CronusSounds.playPause(player);
        if (player.level() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, domain.cx, domain.cy, domain.cz,
                new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_PAUSE, player.getId(),
                    domain.cx, domain.cy + 0.05, domain.cz, (float) radius, 220));
            CronusNet.sendNear(sl, domain.cx, domain.cy, domain.cz,
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, player.getId(),
                    domain.cx, domain.cy, domain.cz, 1.20f, CronusFxMsg.SPRITE_CLOCK_ASSEMBLE));
            CronusNet.sendNear(sl, domain.cx, domain.cy, domain.cz,
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, player.getId(),
                    domain.cx, domain.cy, domain.cz, 1.30f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
        }
        Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.pause_on")
            .withStyle(ChatFormatting.GREEN));
        tickDomain(player, data, domain, now);
        syncFrozenToClients(player, domain, false);
        domain.lastFrozenSyncTick = now;
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
        if (player.level() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                new CronusFxMsg(CronusFxMsg.KIND_PAUSE_TRAP, target.getId(),
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    1.0f, trapKind));
        }
    }

    /** 若攻击者处于时停且目标被完全冻结，将本次伤害压入时停悬停伤害池。返回 true 表示已拦截悬停。 */
    public static boolean interceptDeferredDamage(ServerPlayer attacker, PlayerSystemData data,
                                                  LivingEntity target, float amount) {
        return interceptDeferredDamage(attacker, data, target, amount, false);
    }

    /** 若攻击者处于时停且目标被完全冻结，将本次伤害（保留是否无视护甲属性）与击退动量压入时停悬停池，并锚定时空斩痕。 */
    public static boolean interceptDeferredDamage(ServerPlayer attacker, PlayerSystemData data,
                                                  LivingEntity target, float amount, boolean armorPiercing) {
        if (resolvingRestart || amount <= 0f) {
            return false;
        }
        PauseDomain d = getDomain(attacker);
        if (d == null || d.judgedTiers.get(target) != RuleJudge.Tier.FULL) {
            return false;
        }
        if (armorPiercing) {
            d.deferredArmorPiercingDamage.merge(target, amount, Float::sum);
        } else {
            d.deferredDamage.merge(target, amount, Float::sum);
        }
        int hits = d.frozenHitCounts.merge(target, 1, Integer::sum);

        // 累积延迟击退向量（沿攻击者指向目标方向叠加并设安全上限，Restart 瞬间统一爆飞）
        Vec3 dir = target.position().subtract(attacker.position());
        double hLen = Math.hypot(dir.x, dir.z);
        if (hLen < 1e-4) {
            dir = attacker.getLookAngle();
            hLen = Math.max(1e-4, Math.hypot(dir.x, dir.z));
        }
        double impulse = Math.min(0.45, 0.14 + amount * 0.008);
        Vec3 addKb = new Vec3((dir.x / hLen) * impulse, Math.min(0.16, 0.05 + amount * 0.003),
            (dir.z / hLen) * impulse);
        Vec3 prevKb = d.deferredKnockback.getOrDefault(target, Vec3.ZERO);
        Vec3 nextKb = prevKb.add(addKb);
        double nextHoriz = Math.hypot(nextKb.x, nextKb.z);
        if (nextHoriz > 1.65) {
            double sc = 1.65 / nextHoriz;
            nextKb = new Vec3(nextKb.x * sc, Math.min(0.62, nextKb.y), nextKb.z * sc);
        } else {
            nextKb = new Vec3(nextKb.x, Math.min(0.62, nextKb.y), nextKb.z);
        }
        d.deferredKnockback.put(target, nextKb);

        data.setValue(CronusKeys.KEY_PENDING_DMG, d.totalDeferredDamage());
        if (hits <= 2 || (hits % 2 == 0)) {
            SystemDataManager.markDirty((Player) attacker);
        }

        // 在冻结目标身上锚定悬停发光时空斩痕（封顶单目标频次防止极速激光发包过多）
        if (hits <= 24 && attacker.level() instanceof ServerLevel sl) {
            double hx = target.getX();
            double hy = target.getY() + target.getBbHeight() * (0.28 + ((hits * 37) % 100) * 0.0046);
            double hz = target.getZ();
            CronusNet.sendNear(sl, hx, hy, hz,
                new CronusFxMsg(CronusFxMsg.KIND_PAUSE_HIT_SCAR, target.getId(),
                    hx, hy, hz, Math.min(2.0f, 0.75f + amount * 0.025f), armorPiercing ? 1 : 0));
        }

        if (!CronusCombatAndPassives.isRapidPopupSuppressed()) {
            CronusNet.popupOn(target, (armorPiercing || amount >= 18.0f) ? CronusFxMsg.POPUP_GREAT : CronusFxMsg.POPUP_HIT);
        }
        return true;
    }

    /** 触发【Restart（时间重开）】：解冻所有实体、投射物与世界物体，并以指定倍率 + 连击协同一次性引爆悬停伤害与击退。 */
    public static void restart(ServerPlayer player, PlayerSystemData data,
                               double burstMultiplier, boolean notifyActionbar) {
        PauseDomain d = player == null ? null : DOMAINS.remove(player.getUUID());
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
        syncFrozenToClients(player, d, true);

        if (player != null && player.level() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, d.cx, d.cy, d.cz,
                new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_RESTART, player.getId(),
                    d.cx, d.cy + 0.05, d.cz, (float) d.radius, 0));
            CronusNet.sendNear(sl, player.getX(), player.getY(), player.getZ(),
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, player.getId(),
                    player.getX(), player.getY(), player.getZ(),
                    1.25f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
            CronusNet.sendNear(sl, player.getX(), player.getY(), player.getZ(),
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                    player.getX(), player.getY(), player.getZ(),
                    1.20f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));
        }

        if (player != null && player.isAlive()) {
            CronusSounds.playRestart(player);
            resolvingRestart = true;
            try {
                for (SuspendedTrap trap : d.traps) {
                    if (trap.target != null && trap.target.isAlive()) {
                        d.deferredDamage.merge(trap.target, trap.damage, Float::sum);
                    }
                }
                int fxCount = 0;
                for (Map.Entry<LivingEntity, Float> entry : d.deferredDamage.entrySet()) {
                    LivingEntity victim = entry.getKey();
                    Float raw = entry.getValue();
                    if (victim != null && victim.isAlive() && raw != null && raw > 0.0f) {
                        int hits = d.frozenHitCounts.getOrDefault(victim, 1);
                        double comboMult = 1.0 + Math.min(0.25, Math.max(0, hits - 1) * 0.03);
                        float finalBurst = (float) (raw * burstMultiplier * comboMult);
                        victim.invulnerableTime = 0;
                        TargetUtil.hurt(victim, ModDamageTypes.technique(player), finalBurst);
                        applyDeferredKnockback(victim, d.deferredKnockback.remove(victim));
                        CronusNet.popupOn(victim, CronusFxMsg.POPUP_CRITICAL);
                        if (fxCount++ < 8 && player.level() instanceof ServerLevel sl) {
                            CronusNet.sendNear(sl, victim.getX(), victim.getY(), victim.getZ(),
                                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1,
                                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                    Math.min(3.2f, 1.4f + hits * 0.16f), 2));
                            if (fxCount <= 6) {
                                CronusNet.sendNear(sl, victim.getX(), victim.getY(), victim.getZ(),
                                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                                        victim.getX(), victim.getY(), victim.getZ(),
                                        1.0f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));
                            }
                        }
                    }
                }
                // 结算时停期间积蓄的【无视护甲】悬停伤害（如时停光线枪必杀技一），以 sonic_boom 源穿透护甲与盾牌
                for (Map.Entry<LivingEntity, Float> entry : d.deferredArmorPiercingDamage.entrySet()) {
                    LivingEntity victim = entry.getKey();
                    Float raw = entry.getValue();
                    if (victim != null && victim.isAlive() && raw != null && raw > 0.0f) {
                        int hits = d.frozenHitCounts.getOrDefault(victim, 1);
                        double comboMult = 1.0 + Math.min(0.25, Math.max(0, hits - 1) * 0.03);
                        float finalBurst = (float) (raw * burstMultiplier * comboMult);
                        victim.invulnerableTime = 0;
                        TargetUtil.hurt(victim, player.damageSources().sonicBoom(player), finalBurst);
                        applyDeferredKnockback(victim, d.deferredKnockback.remove(victim));
                        CronusNet.popupOn(victim, CronusFxMsg.POPUP_CRITICAL);
                        if (fxCount++ < 10 && player.level() instanceof ServerLevel sl) {
                            CronusNet.sendNear(sl, victim.getX(), victim.getY(), victim.getZ(),
                                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1,
                                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                                    2.8f, 2));
                        }
                    }
                }
            } finally {
                resolvingRestart = false;
            }
            if (notifyActionbar) {
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.cronus.restart")
                    .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static void applyDeferredKnockback(LivingEntity victim, Vec3 kb) {
        if (victim == null || kb == null || !victim.isAlive()) {
            return;
        }
        victim.setDeltaMovement(victim.getDeltaMovement().add(kb));
        victim.hurtMarked = true;
    }

    /** 每 tick 驱动所有在线 Cronus 的【Pause】领域。 */
    public static void tickPlayerPause(ServerPlayer player, PlayerSystemData data, long now) {
        PauseDomain d = DOMAINS.get(player.getUUID());
        if (d == null) {
            if (CronusKeys.isPaused(data)) {
                data.setValue(CronusKeys.KEY_PAUSED, 0.0);
                SystemDataManager.markDirty((Player) player);
            }
            return;
        }
        if (!player.isAlive() || !CronusKeys.isTransformed(data)
            || !player.level().dimension().equals(d.dimension)) {
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
        if (now % 5L == 0L) {
            SystemDataManager.markDirty((Player) player);
        }
        if (now % 20L == 0L) {
            CronusKeys.addViralStrain(player, data, 2.2, now);
            if (!DOMAINS.containsKey(player.getUUID())) {
                return; // 过载满 100% 时已在 addViralStrain 内解除时停
            }
        }
        // 每 10 ticks 顺滑刷新客户端时钟领域锚点（跟进管理员推进，同时保持低带宽）
        if (now % 10L == 0L) {
            d.cx = player.getX();
            d.cy = player.getY();
            d.cz = player.getZ();
            if (player.level() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, d.cx, d.cy, d.cz,
                    new CronusFxMsg(CronusFxMsg.KIND_DOMAIN_PAUSE, player.getId(),
                        d.cx, d.cy + 0.05, d.cz, (float) d.radius, 36));
            }
        }

        // 被动【编年史管理员权限】：自身在 Pause 领域内免疫迟缓并获得迅捷步伐
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 1, true, false, false));

        tickDomain(player, data, d, now);

        if (now - d.lastFrozenSyncTick >= 8L) {
            d.lastFrozenSyncTick = now;
            syncFrozenToClients(player, d, false);
        }
    }

    private static void syncFrozenToClients(ServerPlayer player, PauseDomain d, boolean clear) {
        if (player == null || !(player.level() instanceof ServerLevel sl)) {
            return;
        }
        int[] ids = clear ? new int[0] : d.collectFrozenEntityIds();
        CronusNet.sendNear(sl, d.cx, d.cy, d.cz,
            CronusFxMsg.frozenSync(d.ownerEntityId, d.cx, d.cy, d.cz, clear, ids));
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
            d.cx - d.radius, d.cy - d.radius * 0.80, d.cz - d.radius,
            d.cx + d.radius, d.cy + d.radius * 0.98, d.cz + d.radius);
        double rSq = d.radius * d.radius;
        double releaseSq = (d.radius + 2.5) * (d.radius + 2.5);

        boolean rejudge = (now - d.lastJudgeTick) >= 15L;
        if (rejudge) {
            d.lastJudgeTick = now;
        }

        // 单次范围扫描覆盖全部实体（生物、投射物、TNT、下落方块、掉落物、经验球、药水云），大幅减少每 tick 多次区块查询开销
        List<Entity> entities = cronus.level().getEntities(cronus, box);
        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToSqr(d.cx, entity.getY(), d.cz) > rSq) {
                continue;
            }
            if (entity instanceof LivingEntity target) {
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
            } else if (entity instanceof Projectile proj) {
                if (proj.getOwner() == cronus) {
                    continue;
                }
                FrozenProjectileState st = d.frozenProjectiles.computeIfAbsent(proj, FrozenProjectileState::new);
                proj.setNoGravity(true);
                proj.setDeltaMovement(Vec3.ZERO);
                proj.setPos(st.x, st.y, st.z);
                proj.hurtMarked = true;
            } else if (entity instanceof PrimedTnt tnt) {
                FrozenWorldObjState st = d.frozenWorldObjs.computeIfAbsent(tnt,
                    e -> new FrozenWorldObjState(e, ((PrimedTnt) e).getFuse()));
                tnt.setNoGravity(true);
                tnt.setDeltaMovement(Vec3.ZERO);
                tnt.setPos(st.x, st.y, st.z);
                tnt.setFuse(Math.max(2, st.savedInt));
                tnt.hurtMarked = true;
            } else if (entity instanceof FallingBlockEntity fb) {
                FrozenWorldObjState st = d.frozenWorldObjs.computeIfAbsent(fb,
                    e -> new FrozenWorldObjState(e, ((FallingBlockEntity) e).time));
                fb.setNoGravity(true);
                fb.setDeltaMovement(Vec3.ZERO);
                fb.setPos(st.x, st.y, st.z);
                fb.time = st.savedInt;
                fb.hurtMarked = true;
            } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                FrozenWorldObjState st = d.frozenWorldObjs.computeIfAbsent(entity,
                    e -> new FrozenWorldObjState(e, 0));
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
                entity.setPos(st.x, st.y, st.z);
                entity.hurtMarked = true;
            } else if (entity instanceof AreaEffectCloud cloud) {
                FrozenWorldObjState st = d.frozenWorldObjs.computeIfAbsent(cloud,
                    e -> new FrozenWorldObjState(e, ((AreaEffectCloud) e).getDuration()));
                cloud.setDuration(st.savedInt + 1);
            }
        }

        // 释放已经离开领域或失效的生物
        Iterator<Map.Entry<LivingEntity, FrozenMobState>> it = d.frozenEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, FrozenMobState> e = it.next();
            LivingEntity ent = e.getKey();
            if (ent == null || !ent.isAlive()
                || ent.distanceToSqr(d.cx, ent.getY(), d.cz) > releaseSq
                || d.judgedTiers.get(ent) != RuleJudge.Tier.FULL) {
                restoreEntity(ent, e.getValue());
                it.remove();
            }
        }

        // 释放已经离开领域的投射物
        Iterator<Map.Entry<Projectile, FrozenProjectileState>> pit = d.frozenProjectiles.entrySet().iterator();
        while (pit.hasNext()) {
            Map.Entry<Projectile, FrozenProjectileState> e = pit.next();
            Projectile p = e.getKey();
            if (p == null || !p.isAlive() || p.distanceToSqr(d.cx, p.getY(), d.cz) > releaseSq) {
                restoreProjectile(p, e.getValue());
                pit.remove();
            }
        }

        // 释放已经离开领域的世界物体
        Iterator<Map.Entry<Entity, FrozenWorldObjState>> wit = d.frozenWorldObjs.entrySet().iterator();
        while (wit.hasNext()) {
            Map.Entry<Entity, FrozenWorldObjState> e = wit.next();
            Entity obj = e.getKey();
            if (obj == null || !obj.isAlive() || obj.distanceToSqr(d.cx, obj.getY(), d.cz) > releaseSq) {
                restoreWorldObj(obj, e.getValue());
                wit.remove();
            }
        }
    }

    private static void applyTierFreeze(LivingEntity target, RuleJudge.Tier tier, PauseDomain d, long now) {
        switch (tier) {
            case FULL -> {
                FrozenMobState st = d.frozenEntities.computeIfAbsent(target, t -> {
                    boolean noAi = (t instanceof Mob mob) && mob.isNoAi();
                    return new FrozenMobState(t, noAi, t.isNoGravity(), now);
                });
                if (target instanceof Player) {
                    // PvP 保护：前 3.5 秒强定身锚定，随后降级为重度迟缓以保留博弈空间
                    if (now - st.frozenSince <= 70L) {
                        target.teleportTo(st.x, st.y, st.z);
                        target.setDeltaMovement(Vec3.ZERO);
                        target.hurtMarked = true;
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 6, false, false, true));
                    } else {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 3, false, false, true));
                    }
                } else {
                    if (target instanceof Mob mob) {
                        mob.setNoAi(true);
                    }
                    target.setNoGravity(true);
                    target.setDeltaMovement(Vec3.ZERO);
                    target.moveTo(st.x, st.y, st.z, st.yRot, st.xRot);
                    target.setYHeadRot(st.yHeadRot);
                    target.setYBodyRot(st.yBodyRot);
                    target.yBodyRotO = st.yBodyRot;
                    target.yHeadRotO = st.yHeadRot;
                    target.yRotO = st.yRot;
                    target.xRotO = st.xRot;
                    target.fallDistance = st.origFallDist;
                    target.setRemainingFireTicks(st.origFireTicks);
                }
            }
            case MAJOR -> {
                Vec3 v = target.getDeltaMovement();
                target.setDeltaMovement(new Vec3(v.x * 0.35, Math.min(0.02, v.y * 0.5), v.z * 0.35));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 0, false, false, true));
            }
            case MINOR -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
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
            restoreProjectile(e.getKey(), e.getValue());
        }
        d.frozenProjectiles.clear();
        for (Map.Entry<Entity, FrozenWorldObjState> e : d.frozenWorldObjs.entrySet()) {
            restoreWorldObj(e.getKey(), e.getValue());
        }
        d.frozenWorldObjs.clear();
    }

    private static void restoreEntity(LivingEntity ent, FrozenMobState st) {
        if (ent == null || st == null) {
            return;
        }
        if (ent instanceof Mob mob) {
            mob.setNoAi(st.origNoAi);
        }
        ent.setNoGravity(st.origNoGravity);
    }

    private static void restoreProjectile(Projectile p, FrozenProjectileState st) {
        if (p != null && p.isAlive() && st != null) {
            p.setNoGravity(st.origNoGravity);
            p.setDeltaMovement(st.velocity);
            p.hurtMarked = true;
        }
    }

    private static void restoreWorldObj(Entity obj, FrozenWorldObjState st) {
        if (obj == null || !obj.isAlive() || st == null) {
            return;
        }
        if (!(obj instanceof AreaEffectCloud)) {
            obj.setNoGravity(st.origNoGravity);
            obj.setDeltaMovement(st.velocity);
            obj.hurtMarked = true;
        }
    }

    public static void forceClearFor(UUID playerId) {
        PauseDomain d = DOMAINS.remove(playerId);
        if (d != null) {
            unfreezeAll(d);
        }
    }
}
