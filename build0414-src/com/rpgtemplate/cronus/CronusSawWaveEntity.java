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
        SynchedEntityData.m_135353_(CronusSawWaveEntity.class, EntityDataSerializers.f_135035_);
    private static final EntityDataAccessor<Float> DATA_TILT =
        SynchedEntityData.m_135353_(CronusSawWaveEntity.class, EntityDataSerializers.f_135029_);

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
        this.m_20242_(true);
        this.f_19794_ = true;
    }

    public boolean isPinning() {
        return this.f_19804_.m_135370_(DATA_PINNING);
    }

    public void setPinning(boolean pinning) {
        this.f_19804_.m_135381_(DATA_PINNING, pinning);
    }

    public float tiltRad() {
        return this.f_19804_.m_135370_(DATA_TILT);
    }

    public static CronusSawWaveEntity launch(ServerPlayer shooter, float totalSkillBaseDamage, boolean paused) {
        if (CronusEntities.SAW_WAVE == null || !(shooter.m_9236_() instanceof ServerLevel level)) {
            return null;
        }
        CronusSawWaveEntity wave = new CronusSawWaveEntity(CronusEntities.SAW_WAVE, level);
        wave.m_5602_(shooter);
        // 12 段高频绞杀总伤害极具压制力（单段 0.24x，终结段 0.65x；时停下进一步强化 20%）
        wave.damagePerTick = totalSkillBaseDamage * (paused ? 0.28f : 0.24f);
        wave.f_19804_.m_135381_(DATA_TILT, (float) Math.toRadians(14.0));

        Vec3 eye = shooter.m_146892_();
        Vec3 look = shooter.m_20154_().m_82541_();
        Vec3 spawn = eye.m_82520_(0.0, -0.22, 0.0).m_82549_(look.m_82490_(1.45));
        wave.m_6034_(spawn.f_82479_, spawn.f_82480_, spawn.f_82481_);

        Vec3 vel = look.m_82490_(FLY_SPEED);
        wave.m_20256_(vel);
        double horiz = Math.hypot(look.f_82479_, look.f_82481_);
        float yRot = (float) (Mth.m_14136_(look.f_82479_, look.f_82481_) * (180.0 / Math.PI));
        float xRot = (float) (Mth.m_14136_(look.f_82480_, horiz) * (180.0 / Math.PI));
        wave.m_146922_(yRot);
        wave.m_146926_(xRot);
        wave.f_19859_ = yRot;
        wave.f_19860_ = xRot;

        level.m_7967_(wave);
        level.m_6263_(null, spawn.f_82479_, spawn.f_82480_, spawn.f_82481_,
            SoundEvents.f_12317_, SoundSource.PLAYERS, 1.15f, 0.72f);
        level.m_6263_(null, spawn.f_82479_, spawn.f_82480_, spawn.f_82481_,
            SoundEvents.f_11983_, SoundSource.PLAYERS, 0.85f, 1.35f);
        return wave;
    }

    @Override
    protected void m_8097_() {
        this.f_19804_.m_135372_(DATA_PINNING, false);
        this.f_19804_.m_135372_(DATA_TILT, 0.24f);
    }

    @Override
    public void m_8119_() {
        super.m_8119_();
        if (!this.m_6084_()) {
            return;
        }
        ageTicks++;

        if (this.m_9236_().f_46443_) {
            if (!isPinning()) {
                Vec3 next = this.m_20182_().m_82549_(this.m_20184_());
                this.m_6034_(next.f_82479_, next.f_82480_, next.f_82481_);
            }
            return;
        }

        Entity owner = this.m_19749_();

        // ==================== 阶段二：命中压制 + 高频狂转持续绞杀 ====================
        if (isPinning()) {
            pinTicks++;
            LivingEntity locked = resolvePinnedTarget(owner);
            if (locked != null && locked.m_6084_()) {
                Vec3 center = locked.m_20191_().m_82399_();
                pinAnchor = center;
                this.m_6034_(center.f_82479_, center.f_82480_, center.f_82481_);
                this.m_20256_(Vec3.f_82478_);
                this.f_19864_ = true;

                // 强力压制目标：取消水平位移与起跳，令其被回旋刃死死按在攻击之下！
                if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, locked))) {
                    Vec3 curVel = locked.m_20184_();
                    locked.m_20256_(new Vec3(curVel.f_82479_ * 0.08, Math.min(0.0, curVel.f_82480_) - 0.08, curVel.f_82481_ * 0.08));
                    locked.f_19864_ = true;
                    locked.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 10, 4, false, false, false));
                }
            } else {
                // 原锁定目标已倒下：尝试顺势压制 2.6 格内下一个敌人，否则提前爆裂收尾
                LivingEntity nextVictim = findNearbyVictim(owner, 2.6);
                if (nextVictim != null) {
                    pinnedTargetId = nextVictim.m_19879_();
                } else {
                    finishSawBurst(owner);
                    return;
                }
            }

            // 每 3 ticks 高频旋转绞杀（无视无敌帧！）并牵引周围敌人入刃
            if (pinTicks == 1 || pinTicks % SHRED_INTERVAL == 0) {
                boolean isFinalHit = (pinTicks >= PIN_DURATION_TICKS);
                float hitDmg = isFinalHit ? (this.damagePerTick * 2.5f) : this.damagePerTick;
                Vec3 c = this.m_20182_();
                AABB grindBox = new AABB(c, c).m_82400_(2.15);
                List<LivingEntity> victims = this.m_9236_().m_45976_(LivingEntity.class, grindBox);

                // 每隔一轮允许弹出街机打击字，避免刷屏同时保持强烈连击爽感
                boolean suppressPopup = !isFinalHit && (pinTicks % (SHRED_INTERVAL * 2) != 0);
                CronusCombatAndPassives.setSuppressRapidPopup(suppressPopup);
                try {
                    for (LivingEntity v : victims) {
                        if (v == owner || !v.m_6084_()) {
                            continue;
                        }
                        v.f_19802_ = 0; // 无视无敌帧高频绞杀
                        if (owner != null) {
                            TargetUtil.hurt(v, ModDamageTypes.technique(owner), hitDmg);
                        }
                        if (!(owner instanceof ServerPlayer sp && CronusPauseManager.isTargetFullFrozenBy(sp, v))) {
                            Vec3 pull = c.m_82546_(v.m_20191_().m_82399_());
                            v.m_20256_(pull.m_82490_(0.22).m_82520_(0.0, -0.06, 0.0));
                            v.f_19864_ = true;
                        }
                    }
                } finally {
                    CronusCombatAndPassives.setSuppressRapidPopup(false);
                }

                if (this.m_9236_() instanceof ServerLevel sl) {
                    sl.m_6263_(null, c.f_82479_, c.f_82480_, c.f_82481_,
                        SoundEvents.f_12317_, SoundSource.PLAYERS, 0.72f, 1.35f + (pinTicks / (float) PIN_DURATION_TICKS) * 0.55f);
                    if (pinTicks % 6 == 0) {
                        CronusNet.sendNear(sl, c.f_82479_, c.f_82480_, c.f_82481_,
                            new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                                c.f_82479_, c.f_82480_, c.f_82481_, 1.25f, 0));
                    }
                }
            }

            if (pinTicks >= PIN_DURATION_TICKS) {
                finishSawBurst(owner);
            }
            return;
        }

        // ==================== 阶段一：高速飞出（未命中敌人持续飞出） ====================
        Vec3 start = this.m_20182_();
        Vec3 vel = this.m_20184_();
        Vec3 end = start.m_82549_(vel);

        // 先检测大半径回旋刃是否劈中敌人
        AABB sweepBox = new AABB(start, end).m_82400_(1.65);
        LivingEntity firstHit = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity cand : this.m_9236_().m_45976_(LivingEntity.class, sweepBox)) {
            if (cand == owner || !cand.m_6084_()) {
                continue;
            }
            double dSq = cand.m_20280_(this);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                firstHit = cand;
            }
        }
        if (firstHit != null) {
            this.pinnedTargetId = firstHit.m_19879_();
            this.setPinning(true);
            this.pinTicks = 0;
            Vec3 c = firstHit.m_20191_().m_82399_();
            this.pinAnchor = c;
            this.m_6034_(c.f_82479_, c.f_82480_, c.f_82481_);
            this.m_20256_(Vec3.f_82478_);
            this.f_19864_ = true;
            if (this.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, c.f_82479_, c.f_82480_, c.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                        c.f_82479_, c.f_82480_, c.f_82481_, 1.85f, 1));
                sl.m_6263_(null, c.f_82479_, c.f_82480_, c.f_82481_,
                    SoundEvents.f_11983_, SoundSource.PLAYERS, 1.0f, 0.78f);
            }
            return;
        }

        // 未命中敌人则持续飞出，撞实心方块或超程才消散
        BlockHitResult bHit = this.m_9236_().m_45547_(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (bHit.m_6662_() != HitResult.Type.MISS) {
            if (this.m_9236_() instanceof ServerLevel sl) {
                Vec3 p = bHit.m_82450_();
                CronusNet.sendNear(sl, p.f_82479_, p.f_82480_, p.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                        p.f_82479_, p.f_82480_, p.f_82481_, 1.5f, 0));
            }
            this.m_146870_();
            return;
        }
        if (ageTicks >= MAX_FLY_TICKS) {
            this.m_146870_();
            return;
        }
        this.m_6034_(end.f_82479_, end.f_82480_, end.f_82481_);
    }

    private LivingEntity resolvePinnedTarget(Entity owner) {
        if (pinnedTargetId < 0) {
            return null;
        }
        Entity e = this.m_9236_().m_6815_(pinnedTargetId);
        return (e instanceof LivingEntity liv && liv != owner && liv.m_6084_()) ? liv : null;
    }

    private LivingEntity findNearbyVictim(Entity owner, double radius) {
        Vec3 c = pinAnchor != null ? pinAnchor : this.m_20182_();
        AABB box = new AABB(c, c).m_82400_(radius);
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity cand : this.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (cand == owner || !cand.m_6084_()) {
                continue;
            }
            double dSq = cand.m_20191_().m_82399_().m_82557_(c);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = cand;
            }
        }
        return best;
    }

    private void finishSawBurst(Entity owner) {
        if (this.m_9236_() instanceof ServerLevel sl) {
            Vec3 c = this.m_20182_();
            CronusNet.sendNear(sl, c.f_82479_, c.f_82480_, c.f_82481_,
                new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, this.m_19879_(),
                    c.f_82479_, c.f_82480_, c.f_82481_, 2.4f, 1));
            sl.m_6263_(null, c.f_82479_, c.f_82480_, c.f_82481_,
                SoundEvents.f_11913_, SoundSource.PLAYERS, 0.75f, 1.35f);
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
        tag.m_128350_("Dmg", damagePerTick);
        tag.m_128405_("Age", ageTicks);
        tag.m_128405_("Pin", pinTicks);
        tag.m_128379_("Pinning", isPinning());
    }

    @Override
    protected void m_7378_(CompoundTag tag) {
        super.m_7378_(tag);
        damagePerTick = tag.m_128457_("Dmg");
        ageTicks = tag.m_128451_("Age");
        pinTicks = tag.m_128451_("Pin");
        setPinning(tag.m_128471_("Pinning"));
    }
}
