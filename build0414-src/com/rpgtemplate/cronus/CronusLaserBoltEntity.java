package com.rpgtemplate.cronus;

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
        this.m_20242_(true);
        this.f_19794_ = true; // noPhysics：自行做精准方块射线与实体子步碰撞
    }

    public static CronusLaserBoltEntity launch(ServerPlayer shooter, InteractionHand hand, float damage) {
        if (CronusEntities.LASER_BOLT == null || !(shooter.m_9236_() instanceof ServerLevel level)) {
            return null;
        }
        CronusLaserBoltEntity bolt = new CronusLaserBoltEntity(CronusEntities.LASER_BOLT, level);
        bolt.m_5602_(shooter);
        bolt.damage = damage;

        Vec3 eye = shooter.m_146892_();
        Vec3 look = shooter.m_20154_().m_82541_();
        Vec3 right = look.m_82537_(new Vec3(0.0, 1.0, 0.0));
        right = right.m_82556_() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.m_82541_();
        double side = (hand == InteractionHand.MAIN_HAND) ? 0.30 : -0.30;
        Vec3 muzzle = eye.m_82549_(right.m_82490_(side)).m_82520_(0.0, -0.14, 0.0).m_82549_(look.m_82490_(0.72));

        // 瞄准准星 32m 落点，保证从枪口射出依然精准命中准星中心
        Vec3 rawTarget = eye.m_82549_(look.m_82490_(32.0));
        BlockHitResult bHit = level.m_45547_(new ClipContext(eye, rawTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 aimPoint = bHit.m_6662_() != HitResult.Type.MISS ? bHit.m_82450_() : rawTarget;
        Vec3 dir = aimPoint.m_82546_(muzzle);
        dir = dir.m_82556_() < 1e-4 ? look : dir.m_82541_();

        bolt.m_6034_(muzzle.f_82479_, muzzle.f_82480_, muzzle.f_82481_);
        bolt.originPos = muzzle;
        bolt.setDirectionVelocity(dir, SPEED);
        level.m_7967_(bolt);
        return bolt;
    }

    private void setDirectionVelocity(Vec3 dir, double speed) {
        Vec3 vel = dir.m_82490_(speed);
        this.m_20256_(vel);
        double horiz = Math.hypot(dir.f_82479_, dir.f_82481_);
        float yRot = (float) (Mth.m_14136_(dir.f_82479_, dir.f_82481_) * (180.0 / Math.PI));
        float xRot = (float) (Mth.m_14136_(dir.f_82480_, horiz) * (180.0 / Math.PI));
        this.m_146922_(yRot);
        this.m_146926_(xRot);
        this.f_19859_ = yRot;
        this.f_19860_ = xRot;
    }

    @Override
    protected void m_8097_() {
    }

    @Override
    public void m_8119_() {
        super.m_8119_();
        if (!this.m_6084_()) {
            return;
        }
        ageTicks++;
        Vec3 vel = this.m_20184_();
        Vec3 start = this.m_20182_();
        Vec3 end = start.m_82549_(vel);

        if (!this.m_9236_().f_46443_) {
            Entity owner = this.m_19749_();
            BlockHitResult blockHit = this.m_9236_().m_45547_(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean hitBlock = blockHit.m_6662_() != HitResult.Type.MISS;
            Vec3 sweepEnd = hitBlock ? blockHit.m_82450_() : end;

            // 分步扫描确保高速弹体精确命中并穿透至多 2 个目标
            for (int s = 1; s <= SUBSTEPS; s++) {
                double t0 = (double) (s - 1) / SUBSTEPS;
                double t1 = (double) s / SUBSTEPS;
                Vec3 p0 = start.m_165921_(sweepEnd, t0);
                Vec3 p1 = start.m_165921_(sweepEnd, t1);
                AABB box = new AABB(p0, p1).m_82400_(0.42);
                List<LivingEntity> candidates = this.m_9236_().m_45976_(LivingEntity.class, box);
                for (LivingEntity target : candidates) {
                    if (target == owner || !target.m_6084_() || hitEntities.contains(target.m_20148_())) {
                        continue;
                    }
                    hitEntities.add(target.m_20148_());
                    if (owner != null) {
                        TargetUtil.hurt(target, ModDamageTypes.technique(owner), this.damage);
                    }
                    emitImpactFlash(target.m_20191_().m_82399_());
                    if (hitEntities.size() >= maxPierce) {
                        this.m_146870_();
                        return;
                    }
                }
            }

            if (hitBlock) {
                emitImpactFlash(blockHit.m_82450_());
                this.m_146870_();
                return;
            }
            if (ageTicks >= MAX_AGE || (originPos != null && start.m_82557_(originPos) >= MAX_RANGE * MAX_RANGE)) {
                this.m_146870_();
                return;
            }
        }

        this.m_6034_(end.f_82479_, end.f_82480_, end.f_82481_);
    }

    private void emitImpactFlash(Vec3 pos) {
        if (this.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, pos.f_82479_, pos.f_82480_, pos.f_82481_,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                    pos.f_82479_, pos.f_82480_, pos.f_82481_, 0.65f, 0));
            sl.m_6263_(null, pos.f_82479_, pos.f_82480_, pos.f_82481_,
                SoundEvents.f_11871_, SoundSource.PLAYERS, 0.35f, 1.85f);
        }
    }

    @Override
    public boolean m_6783_(double distSq) {
        return distSq < 128.0 * 128.0;
    }

    @Override
    protected void m_7380_(CompoundTag tag) {
        super.m_7380_(tag);
        tag.m_128350_("Dmg", damage);
        tag.m_128405_("Age", ageTicks);
    }

    @Override
    protected void m_7378_(CompoundTag tag) {
        super.m_7378_(tag);
        damage = tag.m_128457_("Dmg");
        ageTicks = tag.m_128451_("Age");
    }
}
