package com.rpgtemplate.forge.ext;

import com.rpgtemplate.forge.possession.SoulRealm;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.UUID;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class ShrineEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ShrineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(ShrineEntity.class, EntityDataSerializers.FLOAT);
    private UUID ownerUuid;
    private boolean realm;

    public ShrineEntity(EntityType<? extends ShrineEntity> var1, Level var2) {
        super(var1, var2);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static ShrineEntity create(ServerPlayer var0, double var1, double var3, double var5, float var7, float var8) {
        ShrineEntity var9 = new ShrineEntity(ExtRegistry.SHRINE, var0.level());
        var9.ownerUuid = var0.getUUID();
        var9.entityData.set(OWNER, var0.getId());
        var9.entityData.set(SCALE, var8);
        var9.moveTo(var1, var3, var5, var7, 0.0F);
        var9.setYHeadRot(var7);
        return var9;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(SCALE, 1.0F);
    }

    public int ownerId() {
        return (Integer)this.entityData.get(OWNER);
    }

    public float scale() {
        return (Float)this.entityData.get(SCALE);
    }

    public UUID ownerUuid() {
        return this.ownerUuid;
    }

    public void markRealm() {
        this.realm = true;
    }

    public boolean isRealm() {
        return this.realm;
    }

    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            ServerLevel var1 = (ServerLevel)this.level();
            if (this.ownerUuid == null) {
                this.discard();
            } else {
                if (this.realm) {
                    if (this.tickCount % 20 == 0 && !SoulRealm.keepShrine(this)) {
                        this.discard();
                        return;
                    }
                } else if (this.tickCount % 10 == 0) {
                    ServerPlayer var2 = var1.getServer().getPlayerList().getPlayer(this.ownerUuid);
                    if (var2 == null
                        || var2.level() != this.level()
                        || !SystemDataManager.available(var2)
                        || !DomainExpansionSkill.active(SystemDataManager.get(var2), var1.getGameTime())) {
                        this.discard();
                        return;
                    }
                }

                if (this.tickCount % 4 == 0) {
                    float var9 = this.scale();
                    double var3 = Math.toRadians((double)this.getYRot());
                    double var5 = -Math.sin(var3);
                    double var7 = Math.cos(var3);
                    var1.sendParticles(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        this.getX() + var5 * 2.6 * (double)var9,
                        this.getY() + 6.2 * (double)var9,
                        this.getZ() + var7 * 2.6 * (double)var9,
                        2,
                        1.2 * (double)var9,
                        0.4 * (double)var9,
                        1.2 * (double)var9,
                        0.01
                    );
                    var1.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        this.getX(),
                        this.getY() + 15.0 * (double)var9,
                        this.getZ(),
                        2,
                        2.0 * (double)var9,
                        0.5,
                        2.0 * (double)var9,
                        0.01
                    );
                }
            }
        }
    }

    public AABB getBoundingBoxForCulling() {
        float var1 = this.scale();
        return new AABB(
            this.getX() - (double)(12.0F * var1),
            this.getY() - 1.0,
            this.getZ() - (double)(12.0F * var1),
            this.getX() + (double)(12.0F * var1),
            this.getY() + (double)(22.0F * var1),
            this.getZ() + (double)(12.0F * var1)
        );
    }

    public boolean shouldRenderAtSqrDistance(double var1) {
        return var1 < 147456.0;
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
