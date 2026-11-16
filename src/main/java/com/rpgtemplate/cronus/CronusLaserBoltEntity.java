package com.rpgtemplate.cronus;
import net.minecraft.network.syncher.SynchedEntityData;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 光线枪模式普攻真实弹射物：高速双轨翡翠能量激光弹（射程 36 格，最多穿透 2 名敌人）。
 * 采用每 tick 4 次子步扫描确保高速下零穿模，命中时触发真实打击爆闪与 Cronus 战斗结算。
 */
public final class CronusLaserBoltEntity extends Projectile {
    private static final double SPEED = 2.85;
    private static final double MAX_RANGE = 36.0;
    private static final int MAX_AGE = 20;
    private static final int SUBSTEPS = 4;

    private float damage = 8.0f;
    private int maxPierce = 2;
    private int ageTicks = 0;
    private Vec3 originPos;
    private final Set<UUID> hitEntities = new HashSet<>();

    public CronusLaserBoltEntity(EntityType<? extends CronusLaserBoltEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true; // noPhysics：自行做精准方块射线与实体子步碰撞
    }

    public static CronusLaserBoltEntity launch(ServerPlayer shooter, InteractionHand hand, float damage) {
        if (CronusEntities.LASER_BOLT == null || !(shooter.level() instanceof ServerLevel level)) {
            return null;
        }
        CronusLaserBoltEntity bolt = new CronusLaserBoltEntity(CronusEntities.LASER_BOLT, level);
        bolt.setOwner(shooter);
        bolt.damage = damage;

        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        double side = (hand == InteractionHand.MAIN_HAND) ? 0.30 : -0.30;
        Vec3 muzzle = eye.add(right.scale(side)).add(0.0, -0.14, 0.0).add(look.scale(0.72));

        // 瞄准准星 32m 落点，保证从枪口射出依然精准命中准星中心
        Vec3 rawTarget = eye.add(look.scale(32.0));
        BlockHitResult bHit = level.clip(new ClipContext(eye, rawTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 aimPoint = bHit.getType() != HitResult.Type.MISS ? bHit.getLocation() : rawTarget;
        Vec3 dir = aimPoint.subtract(muzzle);
        dir = dir.lengthSqr() < 1e-4 ? look : dir.normalize();

        bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
        bolt.originPos = muzzle;
        bolt.setDirectionVelocity(dir, SPEED);
        level.addFreshEntity(bolt);
        return bolt;
    }

    private void setDirectionVelocity(Vec3 dir, double speed) {
        Vec3 vel = dir.scale(speed);
        this.setDeltaMovement(vel);
        double horiz = Math.hypot(dir.x, dir.z);
        float yRot = (float) (Mth.atan2(dir.x, dir.z) * (180.0 / Math.PI));
        float xRot = (float) (Mth.atan2(dir.y, horiz) * (180.0 / Math.PI));
        this.setYRot(yRot);
        this.setXRot(xRot);
        this.yRotO = yRot;
        this.xRotO = xRot;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        ageTicks++;
        Vec3 vel = this.getDeltaMovement();
        Vec3 start = this.position();
        Vec3 end = start.add(vel);

        if (!this.level().isClientSide) {
            Entity owner = this.getOwner();
            BlockHitResult blockHit = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
            Vec3 sweepEnd = hitBlock ? blockHit.getLocation() : end;

            // 分步扫描确保高速弹体精确命中并穿透至多 2 个目标
            for (int s = 1; s <= SUBSTEPS; s++) {
                double t0 = (double) (s - 1) / SUBSTEPS;
                double t1 = (double) s / SUBSTEPS;
                Vec3 p0 = start.lerp(sweepEnd, t0);
                Vec3 p1 = start.lerp(sweepEnd, t1);
                AABB box = new AABB(p0, p1).inflate(0.42);
                List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class, box);
                for (LivingEntity target : candidates) {
                    if (target == owner || !target.isAlive() || hitEntities.contains(target.getUUID())) {
                        continue;
                    }
                    hitEntities.add(target.getUUID());
                    if (owner != null) {
                        TargetUtil.hurt(target, ModDamageTypes.technique(owner), this.damage);
                    }
                    emitImpactFlash(target.getBoundingBox().getCenter());
                    if (hitEntities.size() >= maxPierce) {
                        this.discard();
                        return;
                    }
                }
            }

            if (hitBlock) {
                emitImpactFlash(blockHit.getLocation());
                this.discard();
                return;
            }
            if (ageTicks >= MAX_AGE || (originPos != null && start.distanceToSqr(originPos) >= MAX_RANGE * MAX_RANGE)) {
                this.discard();
                return;
            }
        }

        this.setPos(end.x, end.y, end.z);
    }

    private void emitImpactFlash(Vec3 pos) {
        if (this.level() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, pos.x, pos.y, pos.z,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                    pos.x, pos.y, pos.z, 0.65f, 0));
            sl.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35f, 1.85f);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 128.0 * 128.0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Dmg", damage);
        tag.putInt("Age", ageTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Dmg");
        ageTicks = tag.getInt("Age");
    }
}
