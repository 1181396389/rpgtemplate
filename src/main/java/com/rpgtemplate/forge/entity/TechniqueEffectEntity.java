package com.rpgtemplate.forge.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TechniqueEffectEntity extends Entity {
    public static final int STYLE_SLASH = 0;
    public static final int STYLE_CLEAVE = 1;
    public static final int STYLE_RCT_RING = 2;
    public static final int STYLE_CHARGE_AURA = 3;
    public static final int STYLE_BEAM = 4;
    public static final int STYLE_RAINBOW = 5;
    public static final int STYLE_RCT_AURA = 6;
    public static final int STYLE_GRID = 7;
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PARAM = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FOLLOW = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VX = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VY = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VZ = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TRAVEL = SynchedEntityData.defineId(TechniqueEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> COMPANION = SynchedEntityData.defineId(
        TechniqueEffectEntity.class, EntityDataSerializers.OPTIONAL_UUID
    );

    public TechniqueEffectEntity(EntityType<? extends TechniqueEffectEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, 0);
        builder.define(LIFETIME, 10);
        builder.define(LENGTH, 0.0F);
        builder.define(WIDTH, 0.0F);
        builder.define(ROLL, 0.0F);
        builder.define(COLOR, 16777215);
        builder.define(PARAM, 0);
        builder.define(FOLLOW, -1);
        builder.define(SEED, 0);
        builder.define(OWNER, Optional.empty());
        builder.define(COMPANION, Optional.empty());
        builder.define(YAW, 0.0F);
        builder.define(PITCH, 0.0F);
        builder.define(VX, 0.0F);
        builder.define(VY, 0.0F);
        builder.define(VZ, 0.0F);
        builder.define(TRAVEL, 0);
    }

    public int style() {
        return (Integer)this.entityData.get(STYLE);
    }

    public int lifetime() {
        return Math.max(1, (Integer)this.entityData.get(LIFETIME));
    }

    public float length() {
        return (Float)this.entityData.get(LENGTH);
    }

    public float width() {
        return (Float)this.entityData.get(WIDTH);
    }

    public float roll() {
        return (Float)this.entityData.get(ROLL);
    }

    public int color() {
        return (Integer)this.entityData.get(COLOR);
    }

    public int param() {
        return (Integer)this.entityData.get(PARAM);
    }

    public int followId() {
        return (Integer)this.entityData.get(FOLLOW);
    }

    public int seed() {
        return (Integer)this.entityData.get(SEED);
    }

    public Optional<UUID> owner() {
        return (Optional<UUID>)this.entityData.get(OWNER);
    }

    public float effectYaw() {
        return (Float)this.entityData.get(YAW);
    }

    public float effectPitch() {
        return (Float)this.entityData.get(PITCH);
    }

    public int travelTicks() {
        return (Integer)this.entityData.get(TRAVEL);
    }

    public Vec3 flightOffset(float var1) {
        int var2 = this.travelTicks();
        if (var2 <= 0) {
            return Vec3.ZERO;
        } else {
            float var3 = Math.min(var1, (float)var2);
            return new Vec3(
                (double)((Float)this.entityData.get(VX) * var3),
                (double)((Float)this.entityData.get(VY) * var3),
                (double)((Float)this.entityData.get(VZ) * var3)
            );
        }
    }

    public TechniqueEffectEntity fly(Vec3 var1, int var2) {
        this.entityData.set(VX, (float)var1.x);
        this.entityData.set(VY, (float)var1.y);
        this.entityData.set(VZ, (float)var1.z);
        this.entityData.set(TRAVEL, Math.max(0, var2));
        return this;
    }

    public AABB getBoundingBoxForCulling() {
        AABB var1 = super.getBoundingBoxForCulling().inflate(Math.max(2.0, (double)this.length()));
        int var2 = this.travelTicks();
        if (var2 > 0) {
            var1 = var1.expandTowards(this.flightOffset((float)var2));
        }

        return var1;
    }

    public TechniqueEffectEntity setup(int var1, int var2, float var3, float var4, float var5, int var6, int var7) {
        this.entityData.set(STYLE, var1);
        this.entityData.set(LIFETIME, var2);
        this.entityData.set(LENGTH, var3);
        this.entityData.set(WIDTH, var4);
        this.entityData.set(ROLL, var5);
        this.entityData.set(COLOR, var6);
        this.entityData.set(PARAM, var7);
        this.entityData.set(SEED, this.random.nextInt());
        return this;
    }

    public TechniqueEffectEntity orient(float var1, float var2) {
        this.entityData.set(YAW, var1);
        this.entityData.set(PITCH, var2);
        this.setYRot(var1);
        this.setXRot(var2);
        return this;
    }

    public TechniqueEffectEntity owner(UUID var1) {
        this.entityData.set(OWNER, Optional.ofNullable(var1));
        return this;
    }

    public TechniqueEffectEntity follow(Entity var1) {
        this.entityData.set(FOLLOW, var1 == null ? -1 : var1.getId());
        if (var1 != null) {
            this.setPos(var1.getX(), var1.getY(), var1.getZ());
        }

        return this;
    }

    public void tick() {
        super.tick();
        int var1 = this.followId();
        Entity var2;
        if (var1 >= 0 && (var2 = this.level().getEntity(var1)) != null && !var2.isRemoved()) {
            this.setPos(var2.getX(), var2.getY(), var2.getZ());
        }

        if (!this.level().isClientSide && this.tickCount >= this.lifetime() + 2) {
            this.discard();
        }
    }

    public TechniqueEffectEntity companion(UUID var1) {
        this.entityData.set(COMPANION, Optional.ofNullable(var1));
        return this;
    }

    public boolean broadcastToPlayer(ServerPlayer var1) {
        return this.visibleTo(var1);
    }

    public boolean visibleTo(Player var1) {
        if (var1 == null) {
            return false;
        } else if (this.owner().isEmpty()) {
            return true;
        } else {
            return this.owner().get().equals(var1.getUUID()) ? true : ((Optional)this.entityData.get(COMPANION)).filter(var1.getUUID()::equals).isPresent();
        }
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isAttackable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    public boolean shouldBeSaved() {
        return false;
    }

    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        return var1 < (this.style() != 7 && this.style() != 8 ? 16384.0 : 147456.0);
    }

    protected void readAdditionalSaveData(CompoundTag var1) {
    }

    protected void addAdditionalSaveData(CompoundTag var1) {
    }
}
