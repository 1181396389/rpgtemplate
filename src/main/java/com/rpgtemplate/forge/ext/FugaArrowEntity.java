package com.rpgtemplate.forge.ext;

import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class FugaArrowEntity extends Entity {
    public static final byte HELD = 0;
    public static final byte FLYING = 1;
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(FugaArrowEntity.class, EntityDataSerializers.BOOLEAN);
    public static final Map<Integer, FugaArrowEntity> CLIENT_HELD = new ConcurrentHashMap<>();
    public static volatile FugaBlastEntity lastBlast;
    private ServerPlayer owner;
    private FugaImpact.Spec spec;
    private double range = 64.0;
    private double travelled;
    private boolean exploded;

    public FugaArrowEntity(EntityType<? extends FugaArrowEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static FugaArrowEntity held(ServerPlayer var0, boolean var1) {
        FugaArrowEntity var2 = new FugaArrowEntity(ExtRegistry.FUGA_ARROW, var0.level());
        var2.owner = var0;
        var2.entityData.set(OWNER, var0.getId());
        var2.entityData.set(OPEN, var1);
        var2.setPos(handPos(var0, 1.0F));
        var2.setYRot(var0.getYRot());
        var2.setXRot(var0.getXRot());
        return var2;
    }

    public static Vec3 handPos(Entity var0, float var1) {
        Vec3 var2 = var0.getEyePosition(var1);
        Vec3 var3 = var0.getViewVector(var1);
        Vec3 var4 = var3.cross(new Vec3(0.0, 1.0, 0.0));
        var4 = var4.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : var4.normalize();
        return var2.add(var3.scale(0.8)).add(var4.scale(0.1)).add(0.0, -0.3, 0.0);
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(STATE, (byte)0);
        builder.define(CHARGE, 0.0F);
        builder.define(OPEN, false);
    }

    public int ownerId() {
        return (Integer)this.entityData.get(OWNER);
    }

    public byte state() {
        return (Byte)this.entityData.get(STATE);
    }

    public float charge() {
        return (Float)this.entityData.get(CHARGE);
    }

    public boolean open() {
        return (Boolean)this.entityData.get(OPEN);
    }

    public void setCharge(float var1) {
        this.entityData.set(CHARGE, Math.max(0.0F, Math.min(1.0F, var1)));
    }

    public void setOpen(boolean var1) {
        this.entityData.set(OPEN, var1);
    }

    public ServerPlayer owner() {
        return this.owner;
    }

    public double travelled() {
        return this.travelled;
    }

    public boolean exploded() {
        return this.exploded;
    }

    public void launch(Vec3 var1, double var2, double var4, FugaImpact.Spec var6) {
        this.spec = var6;
        this.range = var4;
        this.entityData.set(STATE, (byte)1);
        this.entityData.set(CHARGE, 1.0F);
        this.entityData.set(OPEN, var6.open);
        Vec3 var7 = var1.normalize().scale(var2);
        this.setDeltaMovement(var7);
        this.faceVelocity(var7);
        this.hasImpulse = true;
    }

    private void faceVelocity(Vec3 var1) {
        double var2 = Math.sqrt(var1.x * var1.x + var1.z * var1.z);
        this.setYRot((float)(Math.atan2(-var1.x, var1.z) * (180.0 / Math.PI)));
        this.setXRot((float)(-Math.atan2(var1.y, var2) * (180.0 / Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientTick();
        } else if (this.owner == null || !this.owner.isAlive() || this.owner.isRemoved() || this.owner.level() != this.level()) {
            this.discard();
        } else if (this.state() == 0) {
            HeldState var1 = SkillManager.heldState(this.owner);
            if (var1 != null && "rpgtemplate:kamino".equals(var1.skillId) && this.tickCount <= 1200) {
                this.setPos(handPos(this.owner, 1.0F));
                this.setYRot(this.owner.getYRot());
                this.setXRot(this.owner.getXRot());
            } else {
                this.discard();
            }
        } else {
            this.flyStep();
        }
    }

    private void clientTick() {
        int var1 = this.ownerId();
        if (this.state() == 0) {
            CLIENT_HELD.put(var1, this);
            Entity var2 = this.level().getEntity(var1);
            if (var2 != null) {
                this.setPos(handPos(var2, 1.0F));
            }

            if (this.random.nextFloat() < 0.35F + 0.5F * this.charge()) {
                this.level()
                    .addParticle(
                        this.open() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.4,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                        0.0,
                        0.03,
                        0.0
                    );
            }
        } else {
            CLIENT_HELD.remove(var1, this);
            Vec3 var6 = this.getDeltaMovement();

            for (int var3 = 0; var3 < 3; var3++) {
                double var4 = this.random.nextDouble();
                this.level()
                    .addParticle(
                        ParticleTypes.FLAME,
                        this.getX() - var6.x * var4,
                        this.getY() - var6.y * var4,
                        this.getZ() - var6.z * var4,
                        (this.random.nextDouble() - 0.5) * 0.1,
                        (this.random.nextDouble() - 0.5) * 0.1,
                        (this.random.nextDouble() - 0.5) * 0.1
                    );
            }
        }
    }

    private void flyStep() {
        if (!this.exploded) {
            Vec3 var1 = this.position();
            Vec3 var2 = this.getDeltaMovement();
            if (var2.lengthSqr() < 1.0E-6) {
                this.detonate(var1, null);
            } else {
                Vec3 var3 = var1.add(var2);
                BlockHitResult var4 = this.level().clip(new ClipContext(var1, var3, Block.COLLIDER, Fluid.NONE, this));
                Vec3 var5 = var4.getType() == Type.MISS ? var3 : var4.getLocation();
                Entity var6 = null;
                Vec3 var7 = null;
                double var8 = Double.MAX_VALUE;
                AABB var10 = new AABB(var1, var5).inflate(1.5);

                for (Entity var12 : this.level().getEntities(this, var10, var1x -> var1x != this.owner && TargetUtil.valid(this.owner, var1x))) {
                    AABB var13 = var12.getBoundingBox().inflate(0.45);
                    Optional<Vec3> var14 = var13.clip(var1, var5);
                    Vec3 var15 = var14.orElse(var13.contains(var1) ? var1 : null);
                    if (var15 != null) {
                        double var16 = var1.distanceToSqr(var15);
                        if (var16 < var8) {
                            var8 = var16;
                            var6 = var12;
                            var7 = var15;
                        }
                    }
                }

                if (var6 != null) {
                    this.setPos(var7);
                    this.detonate(var7, var6);
                } else if (var4.getType() != Type.MISS) {
                    this.setPos(var5);
                    this.detonate(var5, null);
                } else {
                    this.setPos(var3);
                    this.travelled = this.travelled + var2.length();
                    ServerLevel var18 = (ServerLevel)this.level();
                    var18.sendParticles(ParticleTypes.FLAME, var3.x, var3.y, var3.z, 6, 0.25, 0.25, 0.25, 0.02);
                    if (this.travelled >= this.range) {
                        this.detonate(var3, null);
                    }
                }
            }
        }
    }

    private void detonate(Vec3 var1, Entity var2) {
        if (!this.exploded) {
            this.exploded = true;
            FugaImpact.Spec var3 = this.spec != null ? this.spec : new FugaImpact.Spec();
            lastBlast = FugaImpact.explode(this.owner, (ServerLevel)this.level(), var1, var2, var3);
            this.discard();
        }
    }

    public void onClientRemoval() {
        CLIENT_HELD.remove(this.ownerId(), this);
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        return var1 < 65536.0;
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    protected void readAdditionalSaveData(CompoundTag var1) {
    }

    protected void addAdditionalSaveData(CompoundTag var1) {
    }
}
