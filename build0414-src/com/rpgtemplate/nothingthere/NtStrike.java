package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.combat.WorldSlashDamage;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
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
@Mod.EventBusSubscriber(modid = "rpgtemplate")
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
                Math.min(from.f_82479_, to.f_82479_) - radius,
                Math.min(from.f_82480_, to.f_82480_) - radius,
                Math.min(from.f_82481_, to.f_82481_) - radius,
                Math.max(from.f_82479_, to.f_82479_) + radius,
                Math.max(from.f_82480_, to.f_82480_) + radius,
                Math.max(from.f_82481_, to.f_82481_) + radius);
        Set<LivingEntity> out = new LinkedHashSet<>();
        for (Entity entity : player.m_9236_().m_6249_(player, search, e -> TargetUtil.valid(player, e))) {
            LivingEntity living = TargetUtil.living(entity);
            if (living == null || living == player) {
                continue;
            }
            if (capsuleTouches(from, to, radius, living.m_20191_())) {
                out.add(living);
            }
        }
        return out;
    }

    private static boolean capsuleTouches(Vec3 a, Vec3 b, double r, AABB box) {
        for (int i = 0; i <= SAMPLES; i++) {
            double t = i / (double) SAMPLES;
            double x = a.f_82479_ + (b.f_82479_ - a.f_82479_) * t;
            double y = a.f_82480_ + (b.f_82480_ - a.f_82480_) * t;
            double z = a.f_82481_ + (b.f_82481_ - a.f_82481_) * t;
            if (sphereTouches(x, y, z, r, box)) {
                return true;
            }
        }
        return false;
    }

    /** 球体与 AABB 相交：球心到包围盒的最近点距离不超过半径。 */
    private static boolean sphereTouches(double x, double y, double z, double r, AABB box) {
        double qx = Math.max(box.f_82288_, Math.min(x, box.f_82291_));
        double qy = Math.max(box.f_82289_, Math.min(y, box.f_82292_));
        double qz = Math.max(box.f_82290_, Math.min(z, box.f_82293_));
        double dx = qx - x;
        double dy = qy - y;
        double dz = qz - z;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /**
     * 强制命中：不走 hurt / attack 事件，因此无视无敌帧、抗性效果（伤害归零）以及其他模组对攻击事件的取消。
     * 玩家目标先经过系统生命（按其防御减免）再扣原版生命；致死时走 kill()。
     */
    public static void forceHit(ServerPlayer attacker, LivingEntity target, float damage) {
        float remaining = damage;
        if (target instanceof ServerPlayer sp) {
            var targetData = NtForms.activeData(sp);
            if (targetData != null) {
                remaining = com.rpgtemplate.forge.combat.DamageHandler.absorb(sp, targetData, damage, false);
                com.rpgtemplate.forge.system.SystemDataManager.markDirty(sp);
            }
        }
        if (remaining > 0.0f && target.m_6084_()) {
            float hp = target.m_21223_();
            if (hp - remaining > 0.0f) {
                target.m_21153_(hp - remaining);
            } else {
                kill(attacker, target);
            }
        }
        NtBleed.afterHit(target, attacker, damage);
        if (target.m_9236_() instanceof net.minecraft.server.level.ServerLevel hitLevel) {
            NtGore.hitBurst(hitLevel, target);
        }
    }

    /** 致死：生命归零后走正式死亡流程；非玩家目标若死亡被取消，则直接移除，不留假死。 */
    private static void kill(ServerPlayer attacker, LivingEntity target) {
        if (!(target instanceof ServerPlayer)) {
            target.m_21153_(0.0f);
        }
        DamageSource source = WorldSlashDamage.source(attacker);
        dying = target;
        deathCancelled = false;
        try {
            target.m_6667_(source);
        } finally {
            dying = null;
        }
        if (deathCancelled) {
            if (target instanceof net.minecraft.server.level.ServerPlayer) {
                LOG.info("[Goodbye] player death cancelled (cocoon or other handler), keeping player state");
            } else {
                LOG.warn("[Goodbye] death of {} was cancelled by another handler; removing it to avoid a fake death",
                        target.m_6095_());
                target.m_146870_();
            }
        }
    }
}
