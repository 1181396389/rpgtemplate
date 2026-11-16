package com.rpgtemplate.forge.ext;

import com.rpgtemplate.core.terrain.PlaneRaster;
import com.rpgtemplate.core.terrain.VoxelLine;
import com.rpgtemplate.core.terrain.WedgeGeometry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.TerrainCutter;
import com.rpgtemplate.forge.combat.WorldSlashDamage;
import com.rpgtemplate.forge.entity.Effects;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.registry.ModSounds;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WorldRiftEntity extends Entity {
    private static final EntityDataAccessor<Float> FX = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FY = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FZ = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SX = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SY = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SZ = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RANGE = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BASE_HALF = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPREAD = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HALF = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> THICK = SynchedEntityData.defineId(WorldRiftEntity.class, EntityDataSerializers.FLOAT);
    public static final int GLOBAL_CHECKS = 65536;
    public static final int GLOBAL_BREAKS = 8192;
    public static final int SAFETY_TICKS = 1200;
    private static final Map<UUID, WorldRiftEntity> ACTIVE = new HashMap<>();
    public static final Set<WorldRiftEntity> CLIENT_SEEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static int budgetTick = Integer.MIN_VALUE;
    private static int checksLeft;
    private static int breaksLeft;
    private UUID owner;
    private float damage;
    public double kingRefund;
    private double playerScale = 0.5;
    private int pierce = 64;
    private WedgeGeometry geometry;
    private PlaneRaster raster;
    private boolean terrain;
    private int checksPerTick;
    private int blocksPerTick;
    private int maxBlocks;
    private int broken;
    private final Set<Integer> hit = new HashSet<>();
    private int projectilesCut;
    private boolean reported;

    public WorldRiftEntity(EntityType<? extends WorldRiftEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static WorldRiftEntity spawn(ServerPlayer var0, Vec3 var1, Vec3 var2, Vec3 var3, WorldRiftEntity.Spec var4, float var5, float var6) {
        WorldRiftEntity var7 = new WorldRiftEntity(SukunaTwentyRegistry.WORLD_RIFT, var0.level());
        var7.setPos(var1.x, var1.y, var1.z);
        var7.owner = var0.getUUID();
        var7.damage = var5;
        var7.playerScale = Math.max(0.0, var4.playerScale());
        var7.pierce = Math.max(1, var4.pierce());
        double[] var8 = new double[]{var1.x, var1.y, var1.z};
        double[] var9 = new double[]{var2.x, var2.y, var2.z};
        double[] var10 = new double[]{var3.x, var3.y, var3.z};
        var7.geometry = new WedgeGeometry(var8, var9, var10, var4.range(), var4.baseHalf(), var4.spread(), var4.maxHalf(), var4.thickness());
        WedgeGeometry var11 = new WedgeGeometry(var8, var9, var10, var4.range(), var4.baseHalf(), var4.spread(), var4.maxHalf(), var4.terrainThickness());
        var7.raster = new PlaneRaster(var11, 1.0, 0.5);
        var7.terrain = var4.terrain();
        var7.checksPerTick = Math.max(0, Math.min(65536, var4.checksPerTick()));
        var7.blocksPerTick = Math.max(0, Math.min(8192, var4.blocksPerTick()));
        var7.maxBlocks = Math.max(0, Math.min(1048576, var4.maxBlocks()));
        int var12 = (int)Math.ceil(var4.range() / Math.max(0.5, var4.speed()));
        var7.entityData.set(FX, (float)var7.geometry.fx);
        var7.entityData.set(FY, (float)var7.geometry.fy);
        var7.entityData.set(FZ, (float)var7.geometry.fz);
        var7.entityData.set(SX, (float)var7.geometry.sx);
        var7.entityData.set(SY, (float)var7.geometry.sy);
        var7.entityData.set(SZ, (float)var7.geometry.sz);
        var7.entityData.set(RANGE, (float)var4.range());
        var7.entityData.set(SPEED, (float)Math.max(0.5, var4.speed()));
        var7.entityData.set(BASE_HALF, (float)var4.baseHalf());
        var7.entityData.set(SPREAD, (float)var4.spread());
        var7.entityData.set(MAX_HALF, (float)Math.max(var4.baseHalf(), var4.maxHalf()));
        var7.entityData.set(POWER, var6);
        var7.entityData.set(THICK, (float)var4.thickness());
        var7.entityData.set(LIFE, var12 + Math.max(4, var4.lingerTicks()));
        WorldRiftEntity var13 = ACTIVE.put(var0.getUUID(), var7);
        if (var13 != null && var13.isAlive() && var13.terrain && !var13.raster.finished()) {
            var13.terrain = false;
            Msg.actionBar(var0, Msg.tr("skill.world_slash.replaced").withStyle(ChatFormatting.GRAY));
        }

        var0.level().addFreshEntity(var7);
        return var7;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FX, 0.0F);
        builder.define(FY, 0.0F);
        builder.define(FZ, 1.0F);
        builder.define(SX, 1.0F);
        builder.define(SY, 0.0F);
        builder.define(SZ, 0.0F);
        builder.define(RANGE, 128.0F);
        builder.define(SPEED, 8.0F);
        builder.define(BASE_HALF, 1.5F);
        builder.define(SPREAD, 0.45F);
        builder.define(MAX_HALF, 40.0F);
        builder.define(POWER, 1.0F);
        builder.define(THICK, 1.0F);
        builder.define(LIFE, 40);
    }

    public Vec3 forward() {
        return new Vec3(
            (double)((Float)this.entityData.get(FX)).floatValue(),
            (double)((Float)this.entityData.get(FY)).floatValue(),
            (double)((Float)this.entityData.get(FZ)).floatValue()
        );
    }

    public Vec3 side() {
        return new Vec3(
            (double)((Float)this.entityData.get(SX)).floatValue(),
            (double)((Float)this.entityData.get(SY)).floatValue(),
            (double)((Float)this.entityData.get(SZ)).floatValue()
        );
    }

    public float range() {
        return (Float)this.entityData.get(RANGE);
    }

    public float speed() {
        return (Float)this.entityData.get(SPEED);
    }

    public float power() {
        return (Float)this.entityData.get(POWER);
    }

    public int life() {
        return (Integer)this.entityData.get(LIFE);
    }

    public float thickness() {
        return (Float)this.entityData.get(THICK);
    }

    public float halfWidth(float var1) {
        float var2 = Math.max(0.0F, Math.min(this.range(), var1));
        return Math.min((Float)this.entityData.get(MAX_HALF), (Float)this.entityData.get(BASE_HALF) + var2 * (Float)this.entityData.get(SPREAD));
    }

    public float travelTicks() {
        return this.range() / Math.max(0.5F, this.speed());
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            CLIENT_SEEN.add(this);
        } else if (this.geometry != null && this.owner != null) {
            int var1 = this.tickCount;
            boolean var2 = this.terrain && !this.raster.finished();
            if ((var1 <= this.life() || var2) && var1 <= 6000) {
                ServerPlayer var13 = this.owner();
                boolean var4 = var13 != null && var13.isAlive() && var13.level() == this.level();
                if (!var4) {
                    this.terrain = false;
                } else {
                    double var5 = Math.max(0.5, (double)this.speed());
                    double var7 = this.geometry.range;
                    double var9 = Math.min(var7, (double)Math.max(0, var1 - 1) * var5);
                    double var11 = Math.min(var7, (double)var1 * var5);
                    if (var9 < var7) {
                        this.sweep(var13, var9, var11);
                        this.frontFx(var13, var11, var1);
                    } else if (!this.reported) {
                        this.reported = true;
                        Msg.actionBar(var13, Msg.tr("skill.world_slash.report", this.hit.size(), this.projectilesCut).withStyle(ChatFormatting.DARK_RED));
                    }

                    if (this.terrain) {
                        this.carve(var13, var11);
                    }
                }
            } else {
                if (var2) {
                    ServerPlayer var3 = this.owner();
                    if (var3 != null) {
                        Msg.actionBar(var3, Msg.tr("skill.world_slash.timeout").withStyle(ChatFormatting.GRAY));
                    }
                }

                this.discard();
            }
        } else {
            this.discard();
        }
    }

    private ServerPlayer owner() {
        MinecraftServer var1 = this.level().getServer();
        return var1 == null ? null : var1.getPlayerList().getPlayer(this.owner);
    }

    private void sweep(ServerPlayer var1, double var2, double var4) {
        double[] var6 = this.geometry.bandBounds(var2, var4, 3.0);
        AABB var7 = new AABB(var6[0], var6[1], var6[2], var6[3], var6[4], var6[5]);
        ServerLevel var8 = (ServerLevel)this.level();
        if (this.hit.size() < this.pierce) {
            for (Entity var10 : var8.getEntities(var1, var7, var1x -> TargetUtil.valid(var1, var1x))) {
                LivingEntity var11 = TargetUtil.living(var10);
                int var12 = var11 != null ? var11.getId() : var10.getId();
                if (!this.hit.contains(var12)) {
                    Vec3 var13 = TargetUtil.center(var10);
                    double var14 = (double)Math.max(var10.getBbWidth(), var10.getBbHeight()) * 0.5;
                    if (this.geometry.intersects(var13.x, var13.y, var13.z, var14, var2, var4)) {
                        this.hit.add(var12);
                        Object var16 = var11 != null ? var11 : var10;
                        float var17 = var16 instanceof Player ? (float)((double)this.damage * this.playerScale) : this.damage;
                        if (TargetUtil.hurt(var10, WorldSlashDamage.source(var1), var17) && this.kingRefund > 0.0) {
                            KingRules.refund(var1, this.kingRefund);
                            this.kingRefund = 0.0;
                        }

                        try {
                            Effects.cleave(var1, (Entity)var16, 18, var11 != null && !var11.isAlive());
                        } catch (Throwable var19) {
                        }

                        var8.sendParticles(ParticleTypes.SWEEP_ATTACK, var13.x, var13.y, var13.z, 3, 0.4, 0.4, 0.4, 0.0);
                        var8.sendParticles(ParticleTypes.DAMAGE_INDICATOR, var13.x, var13.y, var13.z, 6, 0.3, 0.5, 0.3, 0.2);
                        var8.playSound(null, var13.x, var13.y, var13.z, ModSounds.crush(), SoundSource.PLAYERS, 1.0F, 0.7F);
                        if (this.hit.size() >= this.pierce) {
                            break;
                        }
                    }
                }
            }
        }

        for (Entity var21 : var8.getEntities(
            var1, var7, var0 -> var0 instanceof AbstractHurtingProjectile || var0 instanceof ShulkerBullet || var0 instanceof EndCrystal
        )) {
            Vec3 var22 = TargetUtil.center(var21);
            if (this.geometry.intersects(var22.x, var22.y, var22.z, (double)Math.max(var21.getBbWidth(), var21.getBbHeight()) * 0.5, var2, var4)) {
                if (var21 instanceof EndCrystal) {
                    var21.hurt(WorldSlashDamage.source(var1), 1.0F);
                } else {
                    var8.sendParticles(ParticleTypes.SWEEP_ATTACK, var22.x, var22.y, var22.z, 1, 0.0, 0.0, 0.0, 0.0);
                    var8.sendParticles(ParticleTypes.SMOKE, var22.x, var22.y, var22.z, 6, 0.2, 0.2, 0.2, 0.02);
                    var21.discard();
                }

                this.projectilesCut++;
            }
        }
    }

    private void frontFx(ServerPlayer var1, double var2, int var4) {
        if ((var4 & 1) == 0) {
            ServerLevel var5 = (ServerLevel)this.level();
            WedgeGeometry var6 = this.geometry;
            double var7 = var6.halfWidth(var2);
            int var9 = (int)Math.max(6.0, Math.min(12.0, var7 / 3.0));

            for (int var10 = 0; var10 <= var9; var10++) {
                double var11 = -var7 + 2.0 * var7 * (double)var10 / (double)var9;
                double var13 = var6.ox + var6.fx * var2 + var6.sx * var11;
                double var15 = var6.oy + var6.fy * var2 + var6.sy * var11;
                double var17 = var6.oz + var6.fz * var2 + var6.sz * var11;
                var5.sendParticles(ParticleTypes.END_ROD, var13, var15, var17, 1, 0.05, 0.05, 0.05, 0.0);
                if ((var10 & 3) == 0) {
                    var5.sendParticles(ParticleTypes.CRIT, var13, var15, var17, 2, 0.2, 0.2, 0.2, 0.3);
                }
            }
        }
    }

    private static void refill(MinecraftServer var0) {
        int var1 = var0.getTickCount();
        if (var1 != budgetTick) {
            budgetTick = var1;
            checksLeft = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameChecks.get() / 4);
            breaksLeft = Math.max(1, (Integer)ModConfigs.SERVER.terrainFrameBlocks.get() / 4);
        }
    }

    private void carve(ServerPlayer var1, double var2) {
        MinecraftServer var4 = this.level().getServer();
        if (var4 != null && this.checksPerTick > 0 && this.blocksPerTick > 0 && this.maxBlocks > 0 && TerrainCutter.allowed(var1)) {
            int var5 = (Integer)ModConfigs.SERVER.terrainMaxBlocksPerSlash.get();
            if (var5 <= 0) {
                this.terrain = false;
            } else {
                refill(var4);
                int var6 = Math.min(Math.min(this.blocksPerTick, breaksLeft), this.maxBlocks - this.broken);
                int var7 = Math.min(this.checksPerTick, checksLeft);
                if (this.broken >= this.maxBlocks) {
                    Msg.actionBar(var1, Msg.tr("skill.world_slash.limit", this.maxBlocks).withStyle(ChatFormatting.GRAY));
                    this.terrain = false;
                } else if (var6 > 0 && var7 > 0) {
                    TerrainCutter var8 = TerrainCutter.forSlash(var1, this.damage, ((double)var6 + 0.1) / (double)var5);
                    if (var8 == null) {
                        this.terrain = false;
                    } else {
                        var8.quiet().sharedFrame().limit(var6);
                        int var9 = 0;

                        while (var9 < var7 && !var8.exhausted() && var8.canWorkThisTick()) {
                            VoxelLine.Cell var10 = this.raster.next(var2);
                            if (var10 == null) {
                                break;
                            }

                            var9++;
                            var8.cut(new BlockPos(var10.x(), var10.y(), var10.z()));
                        }

                        checksLeft -= var9;
                        breaksLeft = breaksLeft - var8.broken();
                        this.broken = this.broken + var8.broken();
                    }
                }
            }
        } else {
            this.terrain = false;
        }
    }

    public int blocksBroken() {
        return this.broken;
    }

    public int targetsHit() {
        return this.hit.size();
    }

    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (this.owner != null) {
            ACTIVE.remove(this.owner, this);
        }
    }

    public static void clearActive() {
        ACTIVE.clear();
        budgetTick = Integer.MIN_VALUE;
    }

    public AABB getBoundingBoxForCulling() {
        double var1 = (double)(this.range() + (Float)this.entityData.get(MAX_HALF)) + 4.0;
        return this.getBoundingBox().inflate(var1);
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        double var3 = (double)this.range() + 96.0;
        return var1 < var3 * var3;
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

    public static record Spec(
        double range,
        double speed,
        double baseHalf,
        double spread,
        double maxHalf,
        double thickness,
        double terrainThickness,
        int pierce,
        double playerScale,
        int lingerTicks,
        boolean terrain,
        int checksPerTick,
        int blocksPerTick,
        int maxBlocks
    ) {
    }
}
