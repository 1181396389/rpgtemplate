package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Holle 的骨刺弹射物：沿视线高速飞行，命中第一个目标后造成伤害并消失；射程 32 格。
 * 模型见 client/HolleSpikeRenderer 与 client/HolleSpikeModel（Blockbench 模型 holle_spike 的几何，
 * 尖端位于实体位置，朝向即飞行方向）。
 */
public class HolleSpike extends Projectile {
    private static final Logger LOG = LogManager.getLogger("rpgtemplate/Holle");
    static final double SPEED = 3.5;      // 格/tick（约 9 tick 飞完 32 格）
    static final double RANGE = 32.0;
    static final int MAX_AGE = 40;        // 保险：超时即消失
    static final int SUBSTEPS = 4;       // 每 tick 的实体检测分段数（3.5 格/tick，每段约 0.9 格）
    static final double MUZZLE = 0.6;     // 出生点在眼睛前方的距离（避免一出生就压在施法者身上）

    private float damage;
    private Vec3 origin;
    private int age;
    private int dbgTicks;                 // 诊断：前几个 tick 记录位置与速度（两侧都记录）

    public HolleSpike(EntityType<? extends HolleSpike> type, Level level) {
        super(type, level);
        this.m_20242_(true);              // 无重力，直线飞行
    }

    /** 服务端发射：从玩家眼睛处沿视线生成一根骨刺。 */
    public static HolleSpike launch(ServerPlayer player, float damage) {
        return launch(player, damage, 0.0);
    }

    /**
     * 服务端发射：从玩家眼睛前方沿视线生成骨刺，方向绕世界 Y 轴偏转 yawOffsetDeg 度（用于扇形三连发）。
     */
    public static HolleSpike launch(ServerPlayer player, float damage, double yawOffsetDeg) {
        ServerLevel level = (ServerLevel) player.m_9236_();
        HolleSpike spike = new HolleSpike(NtEntities.HOLLE_SPIKE, level);
        spike.m_5602_(player);
        spike.damage = damage;
        Vec3 look = player.m_20154_();
        double a = Math.toRadians(yawOffsetDeg);
        double dx = look.f_82479_ * Math.cos(a) - look.f_82481_ * Math.sin(a);
        double dz = look.f_82479_ * Math.sin(a) + look.f_82481_ * Math.cos(a);
        Vec3 eye = player.m_146892_();
        Vec3 muzzle = new Vec3(eye.f_82479_ + dx * MUZZLE, eye.f_82480_ + look.f_82480_ * MUZZLE,
            eye.f_82481_ + dz * MUZZLE);
        spike.m_6034_(muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_);
        spike.origin = muzzle;
        spike.m_6686_(dx, look.f_82480_, dz, (float) SPEED, 0.0F);
        level.m_7967_(spike);
        Vec3 v = spike.m_20184_();
        LOG.info("[Holle] spike launched owner={} pos=({},{},{}) vel=({},{},{}) yawOffset={} damage={} added={}",
            player.m_36316_().getName(), muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_,
            v.f_82479_, v.f_82480_, v.f_82481_, yawOffsetDeg, damage, spike.m_6084_());
        return spike;
    }

    @Override
    protected void m_8097_() {
        // 无需同步数据：模型由渲染器按飞行方向绘制。
    }

    @Override
    public void m_8119_() {
        super.m_8119_();
        if (dbgTicks < 12) {
            dbgTicks++;
            Vec3 v = this.m_20184_();
            LOG.info("[Holle] tick {} side={} pos=({},{},{}) vel=({},{},{}) alive={}", dbgTicks,
                this.m_9236_().f_46443_ ? "client" : "server",
                this.m_20185_(), this.m_20186_(), this.m_20189_(), v.f_82479_, v.f_82480_, v.f_82481_, this.m_6084_());
        }
        if (!this.m_6084_()) {
            return;
        }
        // 自行移动：原版 Projectile 的移动在这个环境里没有生效（诊断日志显示位置不变），所以这里直接按速度推进。
        Vec3 v = this.m_20184_();
        double x0 = this.m_20185_(), y0 = this.m_20186_(), z0 = this.m_20189_();
        double x1 = x0 + v.f_82479_, y1 = y0 + v.f_82480_, z1 = z0 + v.f_82481_;
        if (!this.m_9236_().f_46443_) {
            // 方块碰撞（沿本 tick 的移动路径）
            BlockHitResult block = this.m_9236_().m_45547_(new ClipContext(
                new Vec3(x0, y0, z0), new Vec3(x1, y1, z1),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.m_6662_() == HitResult.Type.BLOCK) {
                this.m_6034_(block.m_82425_().m_123341_() + 0.5, block.m_82425_().m_123342_() + 0.5,
                    block.m_82425_().m_123343_() + 0.5);
                this.m_8060_(block);
                return;
            }
            // 实体碰撞（沿路径分段取样）
            for (int i = 1; i <= SUBSTEPS; i++) {
                double k = (double) i / SUBSTEPS;
                double px = x0 + (x1 - x0) * k, py = y0 + (y1 - y0) * k, pz = z0 + (z1 - z0) * k;
                AABB probe = new AABB(px - 0.25, py - 0.25, pz - 0.25, px + 0.25, py + 0.25, pz + 0.25);
                List<Entity> hits = this.m_9236_().m_6249_(this, probe, e -> this.m_5603_(e));
                if (!hits.isEmpty()) {
                    this.m_6034_(px, py, pz);
                    this.m_5790_(new EntityHitResult(hits.get(0)));
                    return;
                }
            }
        }
        this.m_6034_(x1, y1, z1);
        if (this.m_9236_().f_46443_) {
            return;
        }
        age++;
        if (origin != null) {
            double dx = this.m_20185_() - origin.f_82479_;
            double dy = this.m_20186_() - origin.f_82480_;
            double dz = this.m_20189_() - origin.f_82481_;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
                LOG.info("[Holle] spike expired: range");
                this.m_146870_();
                return;
            }
        }
        if (age > MAX_AGE) {
            LOG.info("[Holle] spike expired: age");
            this.m_146870_();
        }
    }

    /** 只能命中按技能规则有效的目标；施法者本人永远不是目标。 */
    @Override
    protected boolean m_5603_(Entity target) {
        Entity owner = this.m_19749_();
        if (target == owner) {
            return false;
        }
        if (!super.m_5603_(target)) {
            return false;
        }
        return !(owner instanceof ServerPlayer player) || TargetUtil.valid(player, target);
    }

    @Override
    protected void m_5790_(EntityHitResult result) {
        if (this.m_9236_().f_46443_) {
            return;
        }
        Entity target = result.m_82443_();
        Entity owner = this.m_19749_();
        Entity source = owner != null ? owner : this;
        LOG.info("[Holle] spike hit entity {} damage={}", target.m_6095_(), damage);
        TargetUtil.hurt(target, ModDamageTypes.technique(source), damage);
        if (this.m_9236_() instanceof ServerLevel hitLevel) {
            NtGore.hitBurst(hitLevel, target);
        }
        this.m_146870_();
    }

    @Override
    protected void m_8060_(BlockHitResult result) {
        super.m_8060_(result);
        if (!this.m_9236_().f_46443_) {
            LOG.info("[Holle] spike hit block at {}", result.m_82425_());
            this.m_146870_();
        }
    }
}
