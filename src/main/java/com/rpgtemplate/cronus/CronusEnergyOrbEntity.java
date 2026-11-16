package com.rpgtemplate.cronus;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * 光线枪模式必杀技二：巨大绿色追踪爆炸弹丸（每次齐射 4 发）。
 * <ul>
 *   <li><b>非时停常态（{@code fastMode = false}）</b>：发射 4 枚巨大绿色能量球，以从容优雅的低速（0.42 格/tick）平滑弧线追踪敌人，命中爆发 4.5 格巨型翠绿爆炸与高额伤害！</li>
 *   <li><b>时停（{@code fastMode = true}）</b>：发射 4 枚超频翠金能量球，短暂展开 4 ticks 后以极速强追踪（1.55 格/tick）直扑目标引爆巨型爆炸与高额伤害！</li>
 * </ul>
 */
public final class CronusEnergyOrbEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_FAST_MODE =
        SynchedEntityData.defineId(CronusEnergyOrbEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double SLOW_SPEED = 0.42;
    private static final double FAST_SPEED = 1.55;
    private static final double HOMING_RANGE = 32.0;
    private static final double BLAST_RADIUS = 4.5;

    private float damage = 14.0f;
    private int ageTicks = 0;
    private int maxAge = 125;
    private int lockedTargetId = -1;
    private boolean exploded = false;

    public CronusEnergyOrbEntity(EntityType<? extends CronusEnergyOrbEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public boolean isFastMode() {
        return this.entityData.get(DATA_FAST_MODE);
    }

    public void setFastMode(boolean fast) {
        this.entityData.set(DATA_FAST_MODE, fast);
    }

    /** 发射 4 发巨大绿色追踪爆炸弹丸齐射（自动分散锁定前方与周围敌人）。 */
    public static void launchVolley(ServerPlayer shooter, float damagePerOrb, boolean fastMode) {
        if (CronusEntities.ENERGY_ORB == null || !(shooter.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look).normalize();

        // 预先筛选附近可锁定目标，让 4 发巨弹合理分摊或集火强敌
        List<LivingEntity> candidates = findCandidateTargets(level, shooter, eye, look);

        // 4 发对称扇形展开角（左外 / 左内 / 右内 / 右外）与高低错落
        double[] yawOffsets = {-28.0, -10.0, 10.0, 28.0};
        double[] upOffsets = {0.22, 0.42, 0.42, 0.22};
        double[] sideSpawn = {-0.85, -0.35, 0.35, 0.85};

        for (int i = 0; i < 4; i++) {
            CronusEnergyOrbEntity orb = new CronusEnergyOrbEntity(CronusEntities.ENERGY_ORB, level);
            orb.setOwner(shooter);
            orb.damage = damagePerOrb;
            orb.setFastMode(fastMode);
            orb.maxAge = fastMode ? 72 : 125;

            if (!candidates.isEmpty()) {
                LivingEntity preferred = candidates.get(i % candidates.size());
                orb.lockedTargetId = preferred.getId();
            }

            double rad = Math.toRadians(yawOffsets[i]);
            Vec3 spreadDir = look.scale(Math.cos(rad))
                .add(right.scale(Math.sin(rad)))
                .add(up.scale(upOffsets[i]))
                .normalize();

            Vec3 spawnPos = eye
                .add(look.scale(1.15))
                .add(right.scale(sideSpawn[i]))
                .add(up.scale(upOffsets[i] * 0.65));
            orb.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            double initSpeed = fastMode ? 0.85 : SLOW_SPEED;
            orb.applyVelocity(spreadDir.scale(initSpeed));
            level.addFreshEntity(orb);
        }
    }

    private static List<LivingEntity> findCandidateTargets(ServerLevel level, ServerPlayer shooter, Vec3 eye, Vec3 look) {
        AABB box = shooter.getBoundingBox().inflate(HOMING_RANGE, HOMING_RANGE * 0.7, HOMING_RANGE);
        List<LivingEntity> list = new ArrayList<>();
        double rSq = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == shooter || !e.isAlive() || e.distanceToSqr(shooter) > rSq) {
                continue;
            }
            list.add(e);
        }
        // 优先锁定准星视野内且更近的目标
        list.sort(Comparator.comparingDouble(e -> {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = Math.max(0.5, to.length());
            double dot = to.scale(1.0 / dist).dot(look);
            return dist - dot * 14.0;
        }));
        return list;
    }

    private void applyVelocity(Vec3 vel) {
        this.setDeltaMovement(vel);
        double horiz = Math.hypot(vel.x, vel.z);
        if (horiz > 1e-4 || Math.abs(vel.y) > 1e-4) {
            float yRot = (float) (Mth.atan2(vel.x, vel.z) * (180.0 / Math.PI));
            float xRot = (float) (Mth.atan2(vel.y, horiz) * (180.0 / Math.PI));
            this.setYRot(yRot);
            this.setXRot(xRot);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FAST_MODE, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        ageTicks++;
        boolean fast = isFastMode();
        Vec3 pos = this.position();
        Vec3 vel = this.getDeltaMovement();

        if (!this.level().isClientSide) {
            Entity owner = this.getOwner();

            // 追踪制导：常态为低速优雅大弧线慢追踪；时停为 4 ticks 展开后极速强锁敌突刺！
            LivingEntity target = resolveTarget(owner);
            int homingStartTick = fast ? 4 : 5;
            if (target != null && ageTicks >= homingStartTick) {
                Vec3 aim = target.getBoundingBox().getCenter().subtract(pos);
                double dist = aim.length();
                if (dist > 1e-4) {
                    Vec3 desiredDir = aim.scale(1.0 / dist);
                    Vec3 curDir = vel.lengthSqr() > 1e-5 ? vel.normalize() : desiredDir;
                    double turnAlpha = fast ? 0.44 : 0.13;
                    Vec3 newDir = curDir.scale(1.0 - turnAlpha).add(desiredDir.scale(turnAlpha));
                    newDir = newDir.lengthSqr() > 1e-5 ? newDir.normalize() : desiredDir;
                    double speed = fast
                        ? Mth.lerp(Math.min(1.0, (ageTicks - 3) / 6.0), 0.95, FAST_SPEED)
                        : SLOW_SPEED;
                    vel = newDir.scale(speed);
                    applyVelocity(vel);
                    this.hurtMarked = true;
                }
            } else if (fast && ageTicks >= 4 && vel.length() < FAST_SPEED) {
                vel = vel.normalize().scale(FAST_SPEED);
                applyVelocity(vel);
            }

            Vec3 nextPos = pos.add(vel);

            // 碰撞检测（实体命中优先 -> 方块命中 -> 超时引爆）
            int steps = fast ? 3 : 2;
            for (int s = 1; s <= steps; s++) {
                Vec3 p0 = pos.lerp(nextPos, (double) (s - 1) / steps);
                Vec3 p1 = pos.lerp(nextPos, (double) s / steps);
                AABB hitBox = new AABB(p0, p1).inflate(0.85);
                for (LivingEntity cand : this.level().getEntitiesOfClass(LivingEntity.class, hitBox)) {
                    if (cand == owner || !cand.isAlive()) {
                        continue;
                    }
                    detonate(cand.getBoundingBox().getCenter(), owner);
                    return;
                }
            }

            BlockHitResult bHit = this.level().clip(new ClipContext(
                pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (bHit.getType() != HitResult.Type.MISS) {
                detonate(bHit.getLocation(), owner);
                return;
            }
            if (ageTicks >= maxAge) {
                detonate(nextPos, owner);
                return;
            }
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
        } else {
            Vec3 nextPos = pos.add(vel);
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
        }
    }

    private LivingEntity resolveTarget(Entity owner) {
        if (lockedTargetId >= 0) {
            Entity e = this.level().getEntity(lockedTargetId);
            if (e instanceof LivingEntity liv && liv.isAlive() && liv != owner
                && liv.distanceToSqr(this) <= HOMING_RANGE * HOMING_RANGE * 1.4) {
                return liv;
            }
            lockedTargetId = -1;
        }
        AABB box = this.getBoundingBox().inflate(HOMING_RANGE, HOMING_RANGE * 0.7, HOMING_RANGE);
        LivingEntity nearest = null;
        double bestSq = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity cand : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (cand == owner || !cand.isAlive()) {
                continue;
            }
            double dSq = cand.distanceToSqr(this);
            if (dSq < bestSq) {
                bestSq = dSq;
                nearest = cand;
            }
        }
        if (nearest != null) {
            lockedTargetId = nearest.getId();
        }
        return nearest;
    }

    private void detonate(Vec3 center, Entity owner) {
        if (exploded) {
            return;
        }
        exploded = true;
        if (this.level() instanceof ServerLevel sl) {
            boolean fast = isFastMode();
            double radius = fast ? BLAST_RADIUS + 0.6 : BLAST_RADIUS;
            double rSq = radius * radius;
            AABB blastBox = new AABB(center, center).inflate(radius);

            for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, blastBox)) {
                if (victim == owner || !victim.isAlive()) {
                    continue;
                }
                double distSq = victim.getBoundingBox().getCenter().distanceToSqr(center);
                if (distSq > rSq) {
                    continue;
                }
                double dist = Math.sqrt(distSq);
                float falloff = (float) Mth.clamp(1.0 - (dist / radius) * 0.35, 0.65, 1.0);
                // 清零无敌帧，保证 4 发巨型弹丸连续爆炸全部全额结算不吞伤！
                victim.invulnerableTime = 0;
                if (owner != null) {
                    TargetUtil.hurt(victim, ModDamageTypes.technique(owner), this.damage * falloff);
                }
                // 非时停目标施加强烈爆炸冲击击退
                if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, victim))) {
                    Vec3 push = victim.getBoundingBox().getCenter().subtract(center);
                    double len = Math.max(0.25, push.length());
                    victim.setDeltaMovement(victim.getDeltaMovement().add(push.scale(0.55 / len)).add(0.0, 0.28, 0.0));
                    victim.hurtMarked = true;
                }
            }

            // 巨型 3D 翠绿能量爆炸球 + 双冲击波环 + 翠绿落雷（零方块破坏防炸图）
            CronusNet.sendNear(sl, center.x, center.y, center.z,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                    center.x, center.y, center.z,
                    (float) radius, fast ? 2 : 1));
            CronusNet.sendNear(sl, center.x, center.y, center.z,
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                    center.x, center.y - 0.8, center.z,
                    fast ? 1.15f : 1.0f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));

            CronusSounds.playOrbExplode(sl, center.x, center.y, center.z, fast);
        }
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 144.0 * 144.0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Dmg", damage);
        tag.putInt("Age", ageTicks);
        tag.putBoolean("Fast", isFastMode());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Dmg");
        ageTicks = tag.getInt("Age");
        setFastMode(tag.getBoolean("Fast"));
    }
}
