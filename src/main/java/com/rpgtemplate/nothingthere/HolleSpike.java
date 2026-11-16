package com.rpgtemplate.nothingthere;
import net.minecraft.network.syncher.SynchedEntityData;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
        this.setNoGravity(true);              // 无重力，直线飞行
    }

    /**
     * 服务端自动发射：从玩家眼睛前方朝目标胸口方向生成一根骨刺（尖刺形态的自动反击用）。
     * 与 launch 走同一条发射路径，只是方向改为指向目标。
     */
    public static HolleSpike launchToward(ServerPlayer player, LivingEntity target, float damage) {
        ServerLevel level = (ServerLevel) player.level();
        HolleSpike spike = new HolleSpike(NtEntities.HOLLE_SPIKE, level);
        spike.setOwner(player);
        spike.damage = damage;
        Vec3 eye = player.getEyePosition();
        Vec3 aim = target.position();
        double ax = aim.x - eye.x;
        double ay = (aim.y + target.getBbHeight() * 0.5) - eye.y;
        double az = aim.z - eye.z;
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6) {
            return null;
        }
        double dx = ax / len;
        double dy = ay / len;
        double dz = az / len;
        Vec3 muzzle = new Vec3(eye.x + dx * MUZZLE, eye.y + dy * MUZZLE, eye.z + dz * MUZZLE);
        spike.setPos(muzzle.x, muzzle.y, muzzle.z);
        spike.origin = muzzle;
        spike.shoot(dx, dy, dz, (float) SPEED, 0.0F);
        level.addFreshEntity(spike);
        return spike;
    }

    /** 服务端发射：从玩家眼睛处沿视线生成一根骨刺。 */
    public static HolleSpike launch(ServerPlayer player, float damage) {
        return launch(player, damage, 0.0);
    }

    /**
     * 服务端发射：从玩家眼睛前方沿视线生成骨刺，方向绕世界 Y 轴偏转 yawOffsetDeg 度（用于扇形三连发）。
     */
    public static HolleSpike launch(ServerPlayer player, float damage, double yawOffsetDeg) {
        ServerLevel level = (ServerLevel) player.level();
        HolleSpike spike = new HolleSpike(NtEntities.HOLLE_SPIKE, level);
        spike.setOwner(player);
        spike.damage = damage;
        Vec3 look = player.getLookAngle();
        double a = Math.toRadians(yawOffsetDeg);
        double dx = look.x * Math.cos(a) - look.z * Math.sin(a);
        double dz = look.x * Math.sin(a) + look.z * Math.cos(a);
        Vec3 eye = player.getEyePosition();
        Vec3 muzzle = new Vec3(eye.x + dx * MUZZLE, eye.y + look.y * MUZZLE,
            eye.z + dz * MUZZLE);
        spike.setPos(muzzle.x, muzzle.y, muzzle.z);
        spike.origin = muzzle;
        spike.shoot(dx, look.y, dz, (float) SPEED, 0.0F);
        level.addFreshEntity(spike);
        Vec3 v = spike.getDeltaMovement();
        LOG.info("[Holle] spike launched owner={} pos=({},{},{}) vel=({},{},{}) yawOffset={} damage={} added={}",
            player.getGameProfile().getName(), muzzle.x, muzzle.y, muzzle.z,
            v.x, v.y, v.z, yawOffsetDeg, damage, spike.isAlive());
        return spike;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 无需同步数据：模型由渲染器按飞行方向绘制。
    }

    @Override
    public void tick() {
        super.tick();
        if (dbgTicks < 12) {
            dbgTicks++;
            Vec3 v = this.getDeltaMovement();
            LOG.info("[Holle] tick {} side={} pos=({},{},{}) vel=({},{},{}) alive={}", dbgTicks,
                this.level().isClientSide ? "client" : "server",
                this.getX(), this.getY(), this.getZ(), v.x, v.y, v.z, this.isAlive());
        }
        if (!this.isAlive()) {
            return;
        }
        // 自行移动：原版 Projectile 的移动在这个环境里没有生效（诊断日志显示位置不变），所以这里直接按速度推进。
        Vec3 v = this.getDeltaMovement();
        double x0 = this.getX(), y0 = this.getY(), z0 = this.getZ();
        double x1 = x0 + v.x, y1 = y0 + v.y, z1 = z0 + v.z;
        if (!this.level().isClientSide) {
            // 方块碰撞（沿本 tick 的移动路径）
            BlockHitResult block = this.level().clip(new ClipContext(
                new Vec3(x0, y0, z0), new Vec3(x1, y1, z1),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() == HitResult.Type.BLOCK) {
                this.setPos(block.getBlockPos().getX() + 0.5, block.getBlockPos().getY() + 0.5,
                    block.getBlockPos().getZ() + 0.5);
                this.onHitBlock(block);
                return;
            }
            // 实体碰撞（沿路径分段取样）
            for (int i = 1; i <= SUBSTEPS; i++) {
                double k = (double) i / SUBSTEPS;
                double px = x0 + (x1 - x0) * k, py = y0 + (y1 - y0) * k, pz = z0 + (z1 - z0) * k;
                AABB probe = new AABB(px - 0.25, py - 0.25, pz - 0.25, px + 0.25, py + 0.25, pz + 0.25);
                List<Entity> hits = this.level().getEntities(this, probe, e -> this.canHitEntity(e));
                if (!hits.isEmpty()) {
                    this.setPos(px, py, pz);
                    this.onHitEntity(new EntityHitResult(hits.get(0)));
                    return;
                }
            }
        }
        this.setPos(x1, y1, z1);
        if (this.level().isClientSide) {
            return;
        }
        age++;
        if (origin != null) {
            double dx = this.getX() - origin.x;
            double dy = this.getY() - origin.y;
            double dz = this.getZ() - origin.z;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
                LOG.info("[Holle] spike expired: range");
                this.discard();
                return;
            }
        }
        if (age > MAX_AGE) {
            LOG.info("[Holle] spike expired: age");
            this.discard();
        }
    }

    /** 只能命中按技能规则有效的目标；施法者本人永远不是目标。 */
    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        if (target == owner) {
            return false;
        }
        if (!super.canHitEntity(target)) {
            return false;
        }
        return !(owner instanceof ServerPlayer player) || TargetUtil.valid(player, target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        Entity source = owner != null ? owner : this;
        LOG.info("[Holle] spike hit entity {} damage={}", target.getType(), damage);
        TargetUtil.hurt(target, ModDamageTypes.technique(source), damage);
        if (this.level() instanceof ServerLevel hitLevel) {
            NtGore.hitBurst(hitLevel, target);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            LOG.info("[Holle] spike hit block at {}", result.getBlockPos());
            this.discard();
        }
    }
}
