package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.WorldSlashDamage;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * goodbye 的伤害与区域判定（高级版）。
 *
 * 伤害：致死时先把生命设为 0，再走一次正式的 die()。如果死亡事件被其他监听器取消，
 * 非玩家目标会被直接移除，避免出现“血量 0 但仍在行动”的假死。玩家目标保留原有的茧机制。
 *
 * 区域：从玩家身前拉出一条胶囊形区域（线段 + 半径），用球体与目标包围盒的最近点判定相交。
 * 与旧版的轴对齐方块相比，不会误伤角落，也不会漏掉贴边的目标。
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class NtStrike {
    private static final Logger LOG = LogManager.getLogger("rpgtemplate/NtStrike");
    /** 胶囊区域采样点数（线段上均匀取点，每个点做一次球体相交测试）。 */
    private static final int SAMPLES = 8;

    /** 当前正在执行 die() 的目标，仅用于监听它的死亡事件是否被取消。服务端单线程，不需要同步。 */
    private static LivingEntity dying;
    private static boolean deathCancelled;

    private NtStrike() {
    }

    /** 记录 die() 期间的死亡事件是否被取消（LOWEST 优先级，此时其他监听器已经处理完）。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeathResolved(LivingDeathEvent event) {
        if (dying != null && event.getEntity() == dying && event.isCanceled()) {
            deathCancelled = true;
        }
    }

    /**
     * 区域判定：沿 from -> to 的胶囊形区域（半径 radius），返回与之相交的存活生物（不含施法者本人）。
     */
    public static Set<LivingEntity> sweep(ServerPlayer player, Vec3 from, Vec3 to, double radius) {
        AABB search = new AABB(
                Math.min(from.x, to.x) - radius,
                Math.min(from.y, to.y) - radius,
                Math.min(from.z, to.z) - radius,
                Math.max(from.x, to.x) + radius,
                Math.max(from.y, to.y) + radius,
                Math.max(from.z, to.z) + radius);
        Set<LivingEntity> out = new LinkedHashSet<>();
        for (Entity entity : player.level().getEntities(player, search, e -> TargetUtil.valid(player, e))) {
            LivingEntity living = TargetUtil.living(entity);
            if (living == null || living == player) {
                continue;
            }
            if (capsuleTouches(from, to, radius, living.getBoundingBox())) {
                out.add(living);
            }
        }
        return out;
    }

    private static boolean capsuleTouches(Vec3 a, Vec3 b, double r, AABB box) {
        for (int i = 0; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            if (sphereTouches(x, y, z, r, box)) {
                return true;
            }
        }
        return false;
    }

    /** 球体与 AABB 相交：球心到包围盒的最近点距离不超过半径。 */
    private static boolean sphereTouches(double x, double y, double z, double r, AABB box) {
        double qx = Math.max(box.minX, Math.min(x, box.maxX));
        double qy = Math.max(box.minY, Math.min(y, box.maxY));
        double qz = Math.max(box.minZ, Math.min(z, box.maxZ));
        double dx = qx - x;
        double dy = qy - y;
        double dz = qz - z;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /**
     * 按四档机制判定执行 goodbye 斩击：
     * ① 完全生效：无视无敌帧强制命中与致死处决；
     * ② 显著干扰：常规技巧重创（75% 威力）+ 2 秒减速；
     * ③ 微弱牵制：常规技巧擦伤（35% 威力）；
     * ④ 判定失败：仅造成 10% 余波冲击，施放者受反震短暂减速 1 秒。
     */
    public static void strikeWithRule(ServerPlayer attacker, PlayerSystemData attackerData,
                                      LivingEntity target, float damage) {
        RuleJudge.Result check = RuleJudge.evaluate(attacker, attackerData, target, StatType.SP);
        RuleJudge.notify(attacker, target, "goodbye", check);
        switch (check.tier()) {
            case FULL -> forceHit(attacker, target, damage);
            case MAJOR -> {
                if (TargetUtil.hurt(target, ModDamageTypes.technique(attacker), damage * 0.75f)) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
                    if (target.level() instanceof net.minecraft.server.level.ServerLevel hitLevel) {
                        NtGore.hitBurst(hitLevel, target);
                    }
                }
            }
            case MINOR -> TargetUtil.hurt(target, ModDamageTypes.technique(attacker), damage * 0.35f);
            case FAIL -> {
                TargetUtil.hurt(target, ModDamageTypes.technique(attacker), damage * 0.10f);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, true));
            }
        }
    }

    /**
     * 强制命中：不走 hurt / attack 事件，因此无视无敌帧、抗性效果（伤害归零）以及其他模组对攻击事件的取消。
     * 玩家目标先经过系统生命（按其防御减免）再扣原版生命；致死时走 kill()。
     */
    public static void forceHit(ServerPlayer attacker, LivingEntity target, float damage) {
        float remaining = damage;
        if (target instanceof ServerPlayer sp && SystemDataManager.available(sp)) {
            PlayerSystemData targetData = SystemDataManager.get(sp);
            if (targetData.isActivated()) {
                remaining = com.rpgtemplate.forge.combat.DamageHandler.absorb(sp, targetData, damage, false);
                SystemDataManager.markDirty(sp);
            }
        }
        if (remaining > 0.0f && target.isAlive()) {
            float hp = target.getHealth();
            if (hp - remaining > 0.0f) {
                target.setHealth(hp - remaining);
            } else {
                kill(attacker, target);
            }
        }
        NtBleed.afterHit(target, attacker, damage);
        if (target.level() instanceof net.minecraft.server.level.ServerLevel hitLevel) {
            NtGore.hitBurst(hitLevel, target);
        }
    }

    /** 致死：生命归零后走正式死亡流程；非玩家目标若死亡被取消，则直接移除，不留假死。 */
    private static void kill(ServerPlayer attacker, LivingEntity target) {
        if (!(target instanceof ServerPlayer)) {
            target.setHealth(0.0f);
        }
        DamageSource source = WorldSlashDamage.source(attacker);
        dying = target;
        deathCancelled = false;
        try {
            target.die(source);
        } finally {
            dying = null;
        }
        if (deathCancelled) {
            if (target instanceof net.minecraft.server.level.ServerPlayer) {
                LOG.info("[Goodbye] player death cancelled (cocoon or other handler), keeping player state");
            } else {
                LOG.warn("[Goodbye] death of {} was cancelled by another handler; removing it to avoid a fake death",
                        target.getType());
                target.discard();
            }
        }
    }
}
