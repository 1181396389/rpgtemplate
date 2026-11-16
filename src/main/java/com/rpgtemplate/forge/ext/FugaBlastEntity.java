package com.rpgtemplate.forge.ext;

import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.skill.TargetUtil;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class FugaBlastEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(FugaBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(FugaBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(FugaBlastEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(FugaBlastEntity.class, EntityDataSerializers.INT);
    private ServerPlayer owner;
    private FugaImpact.Spec spec;
    private TerrainCutter cutter;
    private int[] crater;
    private int craterIdx;
    private int craterCount;
    private boolean firesPlaced;
    private BlockPos origin;
    private double craterRadius;

    public FugaBlastEntity(EntityType<? extends FugaBlastEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static FugaBlastEntity create(
        ServerPlayer var0, ServerLevel var1, Vec3 var2, double var3, double var5, FugaImpact.Spec var7, TerrainCutter var8, double var9
    ) {
        FugaBlastEntity var11 = new FugaBlastEntity(ExtRegistry.FUGA_BLAST, var1);
        var11.owner = var0;
        var11.spec = var7;
        var11.cutter = var8;
        var11.setPos(var2.x, var2.y, var2.z);
        var11.origin = BlockPos.containing(var2);
        var11.craterRadius = var9;
        var11.entityData.set(RADIUS, (float)var3);
        var11.entityData.set(HEIGHT, (float)var5);
        var11.entityData.set(OPEN, var7.open);
        var11.entityData.set(LIFE, var7.lifetime);
        return var11;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 6.0F);
        builder.define(HEIGHT, 18.0F);
        builder.define(OPEN, false);
        builder.define(LIFE, 80);
    }

    public float radius() {
        return (Float)this.entityData.get(RADIUS);
    }

    public float height() {
        return (Float)this.entityData.get(HEIGHT);
    }

    public boolean open() {
        return (Boolean)this.entityData.get(OPEN);
    }

    public int life() {
        return (Integer)this.entityData.get(LIFE);
    }

    public int blocksBroken() {
        return this.cutter == null ? 0 : this.cutter.broken();
    }

    public boolean craterDone() {
        return this.cutter == null || this.crater != null && (this.craterIdx >= this.craterCount || this.cutter.exhausted());
    }

    public boolean firesPlaced() {
        return this.firesPlaced;
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientTick();
        } else if (this.owner != null && this.spec != null && this.tickCount <= this.life()) {
            ServerLevel var1 = (ServerLevel)this.level();
            this.carve();
            if (!this.firesPlaced && this.craterDone()) {
                this.firesPlaced = true;
                this.placeFires(var1);
            }

            float var2 = this.radius();
            float var3 = this.height();
            double var4 = (double)var2 * this.spec.pillarRadiusRatio;
            int var6 = this.life();
            if ((double)this.tickCount < (double)var6 * 0.8 && this.tickCount % 10 == 0) {
                AABB var7 = new AABB(
                    this.getX() - var4, this.getY() - 2.0, this.getZ() - var4, this.getX() + var4, this.getY() + (double)var3, this.getZ() + var4
                );
                float var8 = (float)((double)this.spec.damage * this.spec.pillarDamageScale);

                for (Entity var10 : var1.getEntities(this.owner, var7, var1x -> TargetUtil.valid(this.owner, var1x))) {
                    double var11 = var10.getX() - this.getX();
                    double var13 = var10.getZ() - this.getZ();
                    if (!(var11 * var11 + var13 * var13 > var4 * var4)) {
                        var10.igniteForSeconds(this.spec.fireSeconds);
                        TargetUtil.hurt(var10, ModDamageTypes.technique(this.owner), var8);
                        var10.push(0.0, 0.25, 0.0);
                        var10.hurtMarked = true;
                    }
                }
            }

            if ((double)this.tickCount < (double)var6 * 0.7 && this.tickCount % 3 == 0) {
                for (int var15 = 0; var15 < 4; var15++) {
                    double var16 = this.getY() + this.random.nextDouble() * (double)var3;
                    var1.sendParticles(ParticleTypes.FLAME, this.getX(), var16, this.getZ(), 6, var4 * 0.5, 1.5, var4 * 0.5, 0.05);
                }

                var1.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 1.0, this.getZ(), 3, var4, 0.5, var4, 0.0);
            }

            if (this.tickCount == 20 || this.tickCount == 45) {
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 6.0F, 0.5F);
                var1.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 4.0F, 0.4F);
            }
        } else {
            this.discard();
        }
    }

    private void carve() {
        if (this.cutter != null && !(this.craterRadius < 1.0)) {
            if (this.crater == null) {
                this.buildCrater();
            }

            for (int var1 = 0; this.craterIdx < this.craterCount && var1 < this.spec.blocksPerTick && !this.cutter.exhausted(); var1++) {
                int var2 = this.crater[this.craterIdx++];
                int var3 = (var2 >> 20 & 1023) - 512;
                int var4 = (var2 >> 10 & 1023) - 512;
                int var5 = (var2 & 1023) - 512;
                this.cutter.cut(this.origin.offset(var3, var4, var5));
            }
        }
    }

    private void buildCrater() {
        double var1 = Math.min(400.0, this.craterRadius);
        int var3 = (int)Math.ceil(var1);
        double var4 = var1 * var1;
        int var6 = 2 * var3 + 1;
        long[] var7 = new long[Math.min(var6 * var6 * var6, 8000000)];
        int var8 = 0;

        for (int var9 = -var3; var9 <= var3; var9++) {
            double var10 = var9 < 0 ? (double)var9 / 0.65 : (double)var9 * 0.9;

            for (int var12 = -var3; var12 <= var3; var12++) {
                for (int var13 = -var3; var13 <= var3; var13++) {
                    double var14 = (double)(var12 * var12) + var10 * var10 + (double)(var13 * var13);
                    if (!(var14 > var4) && var8 < var7.length) {
                        int var16 = var12 + 512 << 20 | var9 + 512 << 10 | var13 + 512;
                        var7[var8++] = (long)(var14 * 64.0) << 32 | (long)var16 & 4294967295L;
                    }
                }
            }
        }

        Arrays.sort(var7, 0, var8);
        this.crater = new int[var8];

        for (int var17 = 0; var17 < var8; var17++) {
            this.crater[var17] = (int)var7[var17];
        }

        this.craterCount = var8;
    }

    private void placeFires(ServerLevel var1) {
        if (this.cutter != null && this.spec.fireBlocks > 0) {
            double var2 = Math.max(2.0, (double)this.radius());
            int var4 = 0;

            for (int var5 = 0; var5 < this.spec.fireBlocks * 4 && var4 < this.spec.fireBlocks; var5++) {
                double var6 = this.random.nextDouble() * Math.PI * 2.0;
                double var8 = Math.sqrt(this.random.nextDouble()) * var2;
                int var10 = (int)Math.floor(this.getX() + Math.cos(var6) * var8);
                int var11 = (int)Math.floor(this.getZ() + Math.sin(var6) * var8);
                int var12 = (int)Math.floor(this.getY() + var2 * 0.6);
                int var13 = (int)Math.floor(this.getY() - var2);
                int var14 = var12;

                while (var14 >= var13) {
                    BlockPos var15 = BlockPos.containing((double)var10, (double)var14, (double)var11);
                    if (var1.hasChunkAt(var15)) {
                        BlockState var16 = var1.getBlockState(var15);
                        if (var16.isAir()) {
                            var14--;
                        } else {
                            BlockPos var17 = var15.above();
                            if (var1.getBlockState(var17).isAir() && BaseFireBlock.canBePlacedAt(var1, var17, Direction.UP)) {
                                var1.setBlockAndUpdate(var17, BaseFireBlock.getState(var1, var17));
                                var4++;
                            }
                        }
                    }
                }
            }
        }
    }

    private void clientTick() {
        float var1 = this.radius();
        float var2 = this.height();
        int var3 = this.life();
        if (!((double)this.tickCount > (double)var3 * 0.75)) {
            int var4 = this.open() ? 6 : 3;

            for (int var5 = 0; var5 < var4; var5++) {
                double var6 = this.random.nextDouble() * Math.PI * 2.0;
                double var8 = this.random.nextDouble() * (double)var1 * 0.5;
                double var10 = this.getY() + this.random.nextDouble() * (double)var2 * 0.6;
                this.level()
                    .addParticle(
                        ParticleTypes.FLAME,
                        this.getX() + Math.cos(var6) * var8,
                        var10,
                        this.getZ() + Math.sin(var6) * var8,
                        0.0,
                        0.25 + this.random.nextDouble() * 0.4,
                        0.0
                    );
                if (this.open() && var5 % 2 == 0) {
                    this.level()
                        .addParticle(
                            ParticleTypes.END_ROD, this.getX() + Math.cos(var6) * var8 * 0.4, var10, this.getZ() + Math.sin(var6) * var8 * 0.4, 0.0, 0.5, 0.0
                        );
                }
            }
        }
    }

    public AABB getBoundingBoxForCulling() {
        float var1 = this.radius();
        return new AABB(
            this.getX() - (double)var1,
            this.getY() - (double)var1,
            this.getZ() - (double)var1,
            this.getX() + (double)var1,
            this.getY() + (double)this.height(),
            this.getZ() + (double)var1
        );
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        return var1 < 262144.0;
    }

    public boolean isPickable() {
        return false;
    }

    protected void readAdditionalSaveData(CompoundTag var1) {
    }

    protected void addAdditionalSaveData(CompoundTag var1) {
    }
}
