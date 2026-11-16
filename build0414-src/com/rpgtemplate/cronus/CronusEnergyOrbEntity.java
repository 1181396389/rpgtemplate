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
        SynchedEntityData.m_135353_(CronusEnergyOrbEntity.class, EntityDataSerializers.f_135035_);

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
        this.m_20242_(true);
        this.f_19794_ = true;
    }

    public boolean isFastMode() {
        return this.f_19804_.m_135370_(DATA_FAST_MODE);
    }

    public void setFastMode(boolean fast) {
        this.f_19804_.m_135381_(DATA_FAST_MODE, fast);
    }

    /** 发射 4 发巨大绿色追踪爆炸弹丸齐射（自动分散锁定前方与周围敌人）。 */
    public static void launchVolley(ServerPlayer shooter, float damagePerOrb, boolean fastMode) {
        if (CronusEntities.ENERGY_ORB == null || !(shooter.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        Vec3 eye = shooter.m_146892_();
        Vec3 look = shooter.m_20154_().m_82541_();
        Vec3 right = look.m_82537_(new Vec3(0.0, 1.0, 0.0));
        right = right.m_82556_() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.m_82541_();
        Vec3 up = right.m_82537_(look).m_82541_();

        // 预先筛选附近可锁定目标，让 4 发巨弹合理分摊或集火强敌
        List<LivingEntity> candidates = findCandidateTargets(level, shooter, eye, look);

        // 4 发对称扇形展开角（左外 / 左内 / 右内 / 右外）与高低错落
        double[] yawOffsets = {-28.0, -10.0, 10.0, 28.0};
        double[] upOffsets = {0.22, 0.42, 0.42, 0.22};
        double[] sideSpawn = {-0.85, -0.35, 0.35, 0.85};

        for (int i = 0; i < 4; i++) {
            CronusEnergyOrbEntity orb = new CronusEnergyOrbEntity(CronusEntities.ENERGY_ORB, level);
            orb.m_5602_(shooter);
            orb.damage = damagePerOrb;
            orb.setFastMode(fastMode);
            orb.maxAge = fastMode ? 72 : 125;

            if (!candidates.isEmpty()) {
                LivingEntity preferred = candidates.get(i % candidates.size());
                orb.lockedTargetId = preferred.m_19879_();
            }

            double rad = Math.toRadians(yawOffsets[i]);
            Vec3 spreadDir = look.m_82490_(Math.cos(rad))
                .m_82549_(right.m_82490_(Math.sin(rad)))
                .m_82549_(up.m_82490_(upOffsets[i]))
                .m_82541_();

            Vec3 spawnPos = eye
                .m_82549_(look.m_82490_(1.15))
                .m_82549_(right.m_82490_(sideSpawn[i]))
                .m_82549_(up.m_82490_(upOffsets[i] * 0.65));
            orb.m_6034_(spawnPos.f_82479_, spawnPos.f_82480_, spawnPos.f_82481_);
            double initSpeed = fastMode ? 0.85 : SLOW_SPEED;
            orb.applyVelocity(spreadDir.m_82490_(initSpeed));
            level.m_7967_(orb);
        }
    }

    private static List<LivingEntity> findCandidateTargets(ServerLevel level, ServerPlayer shooter, Vec3 eye, Vec3 look) {
        AABB box = shooter.m_20191_().m_82377_(HOMING_RANGE, HOMING_RANGE * 0.7, HOMING_RANGE);
        List<LivingEntity> list = new ArrayList<>();
        double rSq = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity e : level.m_45976_(LivingEntity.class, box)) {
            if (e == shooter || !e.m_6084_() || e.m_20280_(shooter) > rSq) {
                continue;
            }
            list.add(e);
        }
        // 优先锁定准星视野内且更近的目标
        list.sort(Comparator.comparingDouble(e -> {
            Vec3 to = e.m_20191_().m_82399_().m_82546_(eye);
            double dist = Math.max(0.5, to.m_82553_());
            double dot = to.m_82490_(1.0 / dist).m_82526_(look);
            return dist - dot * 14.0;
        }));
        return list;
    }

    private void applyVelocity(Vec3 vel) {
        this.m_20256_(vel);
        double horiz = Math.hypot(vel.f_82479_, vel.f_82481_);
        if (horiz > 1e-4 || Math.abs(vel.f_82480_) > 1e-4) {
            float yRot = (float) (Mth.m_14136_(vel.f_82479_, vel.f_82481_) * (180.0 / Math.PI));
            float xRot = (float) (Mth.m_14136_(vel.f_82480_, horiz) * (180.0 / Math.PI));
            this.m_146922_(yRot);
            this.m_146926_(xRot);
        }
    }

    @Override
    protected void m_8097_() {
        this.f_19804_.m_135372_(DATA_FAST_MODE, false);
    }

    @Override
    public void m_8119_() {
        super.m_8119_();
        if (!this.m_6084_()) {
            return;
        }
        ageTicks++;
        boolean fast = isFastMode();
        Vec3 pos = this.m_20182_();
        Vec3 vel = this.m_20184_();

        if (!this.m_9236_().f_46443_) {
            Entity owner = this.m_19749_();

            // 追踪制导：常态为低速优雅大弧线慢追踪；时停为 4 ticks 展开后极速强锁敌突刺！
            LivingEntity target = resolveTarget(owner);
            int homingStartTick = fast ? 4 : 5;
            if (target != null && ageTicks >= homingStartTick) {
                Vec3 aim = target.m_20191_().m_82399_().m_82546_(pos);
                double dist = aim.m_82553_();
                if (dist > 1e-4) {
                    Vec3 desiredDir = aim.m_82490_(1.0 / dist);
                    Vec3 curDir = vel.m_82556_() > 1e-5 ? vel.m_82541_() : desiredDir;
                    double turnAlpha = fast ? 0.44 : 0.13;
                    Vec3 newDir = curDir.m_82490_(1.0 - turnAlpha).m_82549_(desiredDir.m_82490_(turnAlpha));
                    newDir = newDir.m_82556_() > 1e-5 ? newDir.m_82541_() : desiredDir;
                    double speed = fast
                        ? Mth.m_14139_(Math.min(1.0, (ageTicks - 3) / 6.0), 0.95, FAST_SPEED)
                        : SLOW_SPEED;
                    vel = newDir.m_82490_(speed);
                    applyVelocity(vel);
                    this.f_19864_ = true;
                }
            } else if (fast && ageTicks >= 4 && vel.m_82553_() < FAST_SPEED) {
                vel = vel.m_82541_().m_82490_(FAST_SPEED);
                applyVelocity(vel);
            }

            Vec3 nextPos = pos.m_82549_(vel);

            // 碰撞检测（实体命中优先 -> 方块命中 -> 超时引爆）
            int steps = fast ? 3 : 2;
            for (int s = 1; s <= steps; s++) {
                Vec3 p0 = pos.m_165921_(nextPos, (double) (s - 1) / steps);
                Vec3 p1 = pos.m_165921_(nextPos, (double) s / steps);
                AABB hitBox = new AABB(p0, p1).m_82400_(0.85);
                for (LivingEntity cand : this.m_9236_().m_45976_(LivingEntity.class, hitBox)) {
                    if (cand == owner || !cand.m_6084_()) {
                        continue;
                    }
                    detonate(cand.m_20191_().m_82399_(), owner);
                    return;
                }
            }

            BlockHitResult bHit = this.m_9236_().m_45547_(new ClipContext(
                pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (bHit.m_6662_() != HitResult.Type.MISS) {
                detonate(bHit.m_82450_(), owner);
                return;
            }
            if (ageTicks >= maxAge) {
                detonate(nextPos, owner);
                return;
            }
            this.m_6034_(nextPos.f_82479_, nextPos.f_82480_, nextPos.f_82481_);
        } else {
            Vec3 nextPos = pos.m_82549_(vel);
            this.m_6034_(nextPos.f_82479_, nextPos.f_82480_, nextPos.f_82481_);
        }
    }

    private LivingEntity resolveTarget(Entity owner) {
        if (lockedTargetId >= 0) {
            Entity e = this.m_9236_().m_6815_(lockedTargetId);
            if (e instanceof LivingEntity liv && liv.m_6084_() && liv != owner
                && liv.m_20280_(this) <= HOMING_RANGE * HOMING_RANGE * 1.4) {
                return liv;
            }
            lockedTargetId = -1;
        }
        AABB box = this.m_20191_().m_82377_(HOMING_RANGE, HOMING_RANGE * 0.7, HOMING_RANGE);
        LivingEntity nearest = null;
        double bestSq = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity cand : this.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (cand == owner || !cand.m_6084_()) {
                continue;
            }
            double dSq = cand.m_20280_(this);
            if (dSq < bestSq) {
                bestSq = dSq;
                nearest = cand;
            }
        }
        if (nearest != null) {
            lockedTargetId = nearest.m_19879_();
        }
        return nearest;
    }

    private void detonate(Vec3 center, Entity owner) {
        if (exploded) {
            return;
        }
        exploded = true;
        if (this.m_9236_() instanceof ServerLevel sl) {
            boolean fast = isFastMode();
            double radius = fast ? BLAST_RADIUS + 0.6 : BLAST_RADIUS;
            double rSq = radius * radius;
            AABB blastBox = new AABB(center, center).m_82400_(radius);

            for (LivingEntity victim : sl.m_45976_(LivingEntity.class, blastBox)) {
                if (victim == owner || !victim.m_6084_()) {
                    continue;
                }
                double distSq = victim.m_20191_().m_82399_().m_82557_(center);
                if (distSq > rSq) {
                    continue;
                }
                double dist = Math.sqrt(distSq);
                float falloff = (float) Mth.m_14008_(1.0 - (dist / radius) * 0.35, 0.65, 1.0);
                // 清零无敌帧，保证 4 发巨型弹丸连续爆炸全部全额结算不吞伤！
                victim.f_19802_ = 0;
                if (owner != null) {
                    TargetUtil.hurt(victim, ModDamageTypes.technique(owner), this.damage * falloff);
                }
                // 非时停目标施加强烈爆炸冲击击退
                if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, victim))) {
                    Vec3 push = victim.m_20191_().m_82399_().m_82546_(center);
                    double len = Math.max(0.25, push.m_82553_());
                    victim.m_20256_(victim.m_20184_().m_82549_(push.m_82490_(0.55 / len)).m_82520_(0.0, 0.28, 0.0));
                    victim.f_19864_ = true;
                }
            }

            // 巨型 3D 翠绿能量爆炸球 + 双冲击波环 + 翠绿落雷（零方块破坏防炸图）
            CronusNet.sendNear(sl, center.f_82479_, center.f_82480_, center.f_82481_,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                    center.f_82479_, center.f_82480_, center.f_82481_,
                    (float) radius, fast ? 2 : 1));
            CronusNet.sendNear(sl, center.f_82479_, center.f_82480_, center.f_82481_,
                new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                    center.f_82479_, center.f_82480_ - 0.8, center.f_82481_,
                    fast ? 1.15f : 1.0f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));

            CronusSounds.playOrbExplode(sl, center.f_82479_, center.f_82480_, center.f_82481_, fast);
        }
        this.m_146870_();
    }

    @Override
    public boolean m_6783_(double distSq) {
        return distSq < 144.0 * 144.0;
    }

    @Override
    protected void m_7380_(CompoundTag tag) {
        super.m_7380_(tag);
        tag.m_128350_("Dmg", damage);
        tag.m_128405_("Age", ageTicks);
        tag.m_128379_("Fast", isFastMode());
    }

    @Override
    protected void m_7378_(CompoundTag tag) {
        super.m_7378_(tag);
        damage = tag.m_128457_("Dmg");
        ageTicks = tag.m_128451_("Age");
        setFastMode(tag.m_128471_("Fast"));
    }
}
