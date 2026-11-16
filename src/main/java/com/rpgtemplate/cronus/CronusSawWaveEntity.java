package com.rpgtemplate.cronus;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * 电锯模式必杀技一：巨大绿色回旋刃形能量波（直径 3.1 格实体旋转锯盘）。
 * <ul>
 *   <li><b>未命中阶段</b>：高速向前持续飞出（最远 38 格），沿途劈开空气。</li>
 *   <li><b>命中压制阶段</b>：命中敌人瞬间锁死于目标身上，将敌人与周边目标强力压制在回旋刃下，
 *       回旋刃进入 4 倍高频狂转，每 3 ticks 无视无敌帧持续绞杀（共 12 段 + 终结重斩）！</li>
 * </ul>
 */
public final class CronusSawWaveEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_PINNING =
        SynchedEntityData.defineId(CronusSawWaveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_TILT =
        SynchedEntityData.defineId(CronusSawWaveEntity.class, EntityDataSerializers.FLOAT);

    private static final double FLY_SPEED = 1.05;
    private static final int MAX_FLY_TICKS = 42;
    private static final int PIN_DURATION_TICKS = 36; // 1.8 秒高频压制绞杀（每 3 ticks = 12 段）
    private static final int SHRED_INTERVAL = 3;

    private float damagePerTick = 5.0f;
    private int ageTicks = 0;
    private int pinTicks = 0;
    private int pinnedTargetId = -1;
    private Vec3 pinAnchor = null;

    public CronusSawWaveEntity(EntityType<? extends CronusSawWaveEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public boolean isPinning() {
        return this.entityData.get(DATA_PINNING);
    }

    public void setPinning(boolean pinning) {
        this.entityData.set(DATA_PINNING, pinning);
    }

    public float tiltRad() {
        return this.entityData.get(DATA_TILT);
    }

    public static CronusSawWaveEntity launch(ServerPlayer shooter, float totalSkillBaseDamage, boolean paused) {
        if (CronusEntities.SAW_WAVE == null || !(shooter.level() instanceof ServerLevel level)) {
            return null;
        }
        CronusSawWaveEntity wave = new CronusSawWaveEntity(CronusEntities.SAW_WAVE, level);
        wave.setOwner(shooter);
        // 12 段高频绞杀总伤害极具压制力（单段 0.24x，终结段 0.65x；时停下进一步强化 20%）
        wave.damagePerTick = totalSkillBaseDamage * (paused ? 0.28f : 0.24f);
        wave.entityData.set(DATA_TILT, (float) Math.toRadians(14.0));

        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle().normalize();
        Vec3 spawn = eye.add(0.0, -0.22, 0.0).add(look.scale(1.45));
        wave.setPos(spawn.x, spawn.y, spawn.z);

        Vec3 vel = look.scale(FLY_SPEED);
        wave.setDeltaMovement(vel);
        double horiz = Math.hypot(look.x, look.z);
        float yRot = (float) (Mth.atan2(look.x, look.z) * (180.0 / Math.PI));
        float xRot = (float) (Mth.atan2(look.y, horiz) * (180.0 / Math.PI));
        wave.setYRot(yRot);
        wave.setXRot(xRot);
        wave.yRotO = yRot;
        wave.xRotO = xRot;

        level.addFreshEntity(wave);
        return wave;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PINNING, false);
        builder.define(DATA_TILT, 0.24f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        ageTicks++;

        if (this.level().isClientSide) {
            if (!isPinning()) {
                Vec3 next = this.position().add(this.getDeltaMovement());
                this.setPos(next.x, next.y, next.z);
            }
            return;
        }

        Entity owner = this.getOwner();

        // ==================== 阶段二：命中压制 + 高频狂转持续绞杀 ====================
        if (isPinning()) {
            pinTicks++;
            LivingEntity locked = resolvePinnedTarget(owner);
            if (locked != null && locked.isAlive()) {
                Vec3 center = locked.getBoundingBox().getCenter();
                pinAnchor = center;
                this.setPos(center.x, center.y, center.z);
                this.setDeltaMovement(Vec3.ZERO);
                this.hurtMarked = true;

                // 强力压制目标：取消水平位移与起跳，令其被回旋刃死死按在攻击之下！
                if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, locked))) {
                    Vec3 curVel = locked.getDeltaMovement();
                    locked.setDeltaMovement(new Vec3(curVel.x * 0.08, Math.min(0.0, curVel.y) - 0.08, curVel.z * 0.08));
                    locked.hurtMarked = true;
                    locked.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4, false, false, false));
                }
            } else {
                // 原锁定目标已倒下：尝试顺势压制 2.6 格内下一个敌人，否则提前爆裂收尾
                LivingEntity nextVictim = findNearbyVictim(owner, 2.6);
                if (nextVictim != null) {
                    pinnedTargetId = nextVictim.getId();
                } else {
                    finishSawBurst(owner);
                    return;
                }
            }

            // 每 3 ticks 高频旋转绞杀（无视无敌帧！）并牵引周围敌人入刃
            if (pinTicks == 1 || pinTicks % SHRED_INTERVAL == 0) {
                boolean isFinalHit = (pinTicks >= PIN_DURATION_TICKS);
                float hitDmg = isFinalHit ? (this.damagePerTick * 2.5f) : this.damagePerTick;
                Vec3 c = this.position();
                AABB grindBox = new AABB(c, c).inflate(2.15);
                List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class, grindBox);

                // 每隔一轮允许弹出街机打击字，避免刷屏同时保持强烈连击爽感
                boolean suppressPopup = !isFinalHit && (pinTicks % (SHRED_INTERVAL * 2) != 0);
                CronusCombatAndPassives.setSuppressRapidPopup(suppressPopup);
                try {
                    for (LivingEntity v : victims) {
                        if (v == owner || !v.isAlive()) {
                            continue;
                        }
                        v.invulnerableTime = 0; // 无视无敌帧高频绞杀
                        if (owner != null) {
                            TargetUtil.hurt(v, ModDamageTypes.technique(owner), hitDmg);
                        }
                        if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, v))) {
                            Vec3 pull = c.subtract(v.getBoundingBox().getCenter());
                            v.setDeltaMovement(pull.scale(0.22).add(0.0, -0.06, 0.0));
                            v.hurtMarked = true;
                        }
                    }
                } finally {
                    CronusCombatAndPassives.setSuppressRapidPopup(false);
                }

                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, c.x, c.y, c.z,
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.72f, 1.35f + (pinTicks / (float) PIN_DURATION_TICKS) * 0.55f);
                    if (pinTicks % 6 == 0) {
                        CronusNet.sendNear(sl, c.x, c.y, c.z,
                            new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                                c.x, c.y, c.z, 1.25f, 0));
                    }
                }
            }

            if (pinTicks >= PIN_DURATION_TICKS) {
                finishSawBurst(owner);
            }
            return;
        }

        // ==================== 阶段一：高速飞出（未命中敌人持续飞出） ====================
        Vec3 start = this.position();
        Vec3 vel = this.getDeltaMovement();
        Vec3 end = start.add(vel);

        // 先检测大半径回旋刃是否劈中敌人
        AABB sweepBox = new AABB(start, end).inflate(1.65);
        LivingEntity firstHit = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity cand : this.level().getEntitiesOfClass(LivingEntity.class, sweepBox)) {
            if (cand == owner || !cand.isAlive()) {
                continue;
            }
            double dSq = cand.distanceToSqr(this);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                firstHit = cand;
            }
        }
        if (firstHit != null) {
            this.pinnedTargetId = firstHit.getId();
            this.setPinning(true);
            this.pinTicks = 0;
            Vec3 c = firstHit.getBoundingBox().getCenter();
            this.pinAnchor = c;
            this.setPos(c.x, c.y, c.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.hurtMarked = true;
            if (this.level() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, c.x, c.y, c.z,
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                        c.x, c.y, c.z, 1.85f, 1));
                sl.playSound(null, c.x, c.y, c.z,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.78f);
            }
            return;
        }

        // 未命中敌人则持续飞出，撞实心方块或超程才消散
        BlockHitResult bHit = this.level().clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (bHit.getType() != HitResult.Type.MISS) {
            if (this.level() instanceof ServerLevel sl) {
                Vec3 p = bHit.getLocation();
                CronusNet.sendNear(sl, p.x, p.y, p.z,
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                        p.x, p.y, p.z, 1.5f, 0));
            }
            this.discard();
            return;
        }
        if (ageTicks >= MAX_FLY_TICKS) {
            this.discard();
            return;
        }
        this.setPos(end.x, end.y, end.z);
    }

    private LivingEntity resolvePinnedTarget(Entity owner) {
        if (pinnedTargetId < 0) {
            return null;
        }
        Entity e = this.level().getEntity(pinnedTargetId);
        return (e instanceof LivingEntity liv && liv != owner && liv.isAlive()) ? liv : null;
    }

    private LivingEntity findNearbyVictim(Entity owner, double radius) {
        Vec3 c = pinAnchor != null ? pinAnchor : this.position();
        AABB box = new AABB(c, c).inflate(radius);
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity cand : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (cand == owner || !cand.isAlive()) {
                continue;
            }
            double dSq = cand.getBoundingBox().getCenter().distanceToSqr(c);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = cand;
            }
        }
        return best;
    }

    private void finishSawBurst(Entity owner) {
        if (this.level() instanceof ServerLevel sl) {
            Vec3 c = this.position();
            CronusNet.sendNear(sl, c.x, c.y, c.z,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.getId(),
                    c.x, c.y, c.z, 2.4f, 1));
            sl.playSound(null, c.x, c.y, c.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.75f, 1.35f);
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
        tag.putFloat("Dmg", damagePerTick);
        tag.putInt("Age", ageTicks);
        tag.putInt("Pin", pinTicks);
        tag.putBoolean("Pinning", isPinning());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damagePerTick = tag.getFloat("Dmg");
        ageTicks = tag.getInt("Age");
        pinTicks = tag.getInt("Pin");
        setPinning(tag.getBoolean("Pinning"));
    }
}
