package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 假面骑士 Cronus 六大被动、光速/魂则弱点、Bugster 病毒负荷、4 秒回溯采样、三段跳滞空与旋转踢 1.5x 经验结算。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class CronusCombatAndPassives {
    public static final int AIR_JUMP = 0;
    public static final int AIR_HOVER_START = 1;
    public static final int AIR_HOVER_STOP = 2;

    private static final Map<UUID, Deque<RewindSnapshot>> REWIND_HISTORY = new HashMap<>();
    private static final Map<UUID, MuscleLaunch> MUSCLE_LAUNCHED = new HashMap<>();
    private static boolean resolvingMuscleSlam = false;
    private static boolean suppressRapidPopup = false;

    public static void setSuppressRapidPopup(boolean suppress) {
        suppressRapidPopup = suppress;
    }

    public static boolean isRapidPopupSuppressed() {
        return suppressRapidPopup;
    }

    /** 消耗【电锯必杀技二·绿能聚刃】充能并返回附加巨额伤害（无有效充能返回 0）。 */
    public static float consumeChainsawOvercharge(ServerPlayer attacker, PlayerSystemData data, long now) {
        if (attacker == null || data == null) {
            return 0.0f;
        }
        double until = data.value(CronusKeys.KEY_CHAINSAW_OVERCHARGE_UNTIL, 0.0);
        float bonus = (float) data.value(CronusKeys.KEY_CHAINSAW_OVERCHARGE_DMG, 0.0);
        if (until <= (double) now || bonus <= 0.0f) {
            return 0.0f;
        }
        data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_UNTIL, 0.0);
        data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_DMG, 0.0);
        SystemDataManager.markDirty((Player) attacker);
        if (attacker.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, attacker.m_20185_(), attacker.m_20186_(), attacker.m_20189_(),
                new CronusFxMsg(CronusFxMsg.KIND_CHAINSAW_CHARGE, attacker.m_19879_(),
                    attacker.m_20185_(), attacker.m_20186_(), attacker.m_20189_(), 0.0f, 0));
        }
        Msg.actionBar(attacker, Component.m_237115_("message.rpgtemplate.cronus.kime2_saw_detonate")
            .m_130940_(ChatFormatting.GREEN));
        return bonus;
    }

    /** 触发电锯必杀技二爆发瞬间的 3D 翡翠巨爆 + 天降绿雷 + CRITICAL SACRIFICE 原声斩击。 */
    public static void emitChainsawOverchargeBurst(ServerPlayer attacker, LivingEntity victim) {
        if (victim == null || !(victim.m_9236_() instanceof ServerLevel sl)) {
            return;
        }
        double vx = victim.m_20185_();
        double vy = victim.m_20186_() + victim.m_20206_() * 0.5;
        double vz = victim.m_20189_();
        CronusNet.sendNear(sl, vx, vy, vz,
            new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1, vx, vy, vz, 3.4f, 1));
        CronusNet.sendNear(sl, vx, victim.m_20186_(), vz,
            new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1, vx, victim.m_20186_(), vz,
                1.25f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));
        CronusSounds.playKimeCsSlash(sl, vx, vy, vz);
    }

    private record MuscleLaunch(UUID attackerId, float slamDamage, long expireTick) {
    }

    public static final class RewindSnapshot {
        public final long tick;
        public final ResourceLocation dimension;
        public final double x;
        public final double y;
        public final double z;
        public final float yRot;
        public final float xRot;
        public final double sysHp;
        public final float vanillaHp;

        RewindSnapshot(ServerPlayer p, PlayerSystemData data, long tick) {
            this.tick = tick;
            this.dimension = p.m_9236_().m_46472_().m_135782_();
            this.x = p.m_20185_();
            this.y = p.m_20186_();
            this.z = p.m_20189_();
            this.yRot = p.m_146908_();
            this.xRot = p.m_146909_();
            this.sysHp = data.hp();
            this.vanillaHp = p.m_21223_();
        }
    }

    private CronusCombatAndPassives() {
    }

    /** 供客户端 {@code TacticalSkillMsg} 同步调用的三段跳与 Shift 主动滞空入口。 */
    public static void handleAirAction(ServerPlayer player, int airAction) {
        PlayerSystemData data = CronusKeys.activeData(player);
        if (data == null) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        switch (airAction) {
            case AIR_JUMP -> {
                double jumpVel = (CronusKeys.isTransformed(data) ? 0.56 : 0.46)
                    + (data.value(CronusKeys.KEY_ITEM_JUMP_UNTIL, 0.0) > (double) now ? 0.16 : 0.0);
                Vec3 cur = player.m_20184_();
                player.m_20256_(new Vec3(cur.f_82479_ * 1.06, jumpVel, cur.f_82481_ * 1.06));
                player.f_19789_ = 0.0f;
                player.f_19864_ = true;
                if (player.m_9236_() instanceof ServerLevel sl) {
                    CronusNet.sendNear(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, player.m_19879_(),
                            player.m_20185_(), player.m_20186_() + 0.05, player.m_20189_(), 1.4f, 0));
                }
            }
            case AIR_HOVER_START -> {
                long hoverTicks = data.value(CronusKeys.KEY_ITEM_JUMP_UNTIL, 0.0) > (double) now ? 42L : 26L;
                data.setValue(CronusKeys.KEY_HOVER_UNTIL, (double) (now + hoverTicks));
                Vec3 cur = player.m_20184_();
                player.m_20256_(new Vec3(cur.f_82479_ * 0.85, Math.max(0.0, cur.f_82480_ * 0.1), cur.f_82481_ * 0.85));
                player.f_19789_ = 0.0f;
                player.f_19864_ = true;
            }
            case AIR_HOVER_STOP -> data.setValue(CronusKeys.KEY_HOVER_UNTIL, 0.0);
            default -> {
            }
        }
    }

    /** 读取约 4 秒前（最早不超过 85 ticks）的状态快照，供【Reset】使用。 */
    public static RewindSnapshot findRewindSnapshot(ServerPlayer player, long now) {
        Deque<RewindSnapshot> deque = REWIND_HISTORY.get(player.m_20148_());
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        ResourceLocation dim = player.m_9236_().m_46472_().m_135782_();
        RewindSnapshot chosen = null;
        for (RewindSnapshot snap : deque) {
            if (!dim.equals(snap.dimension)) {
                continue;
            }
            chosen = snap;
            if (now - snap.tick <= 82L) {
                break;
            }
        }
        return chosen;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 无论当前是否加载 Cronus，均检测模板解锁、物品界面状态同步以及变身武装/常驻增益回收
        CronusGearManager.tickPlayer(player);

        PlayerSystemData data = CronusKeys.activeData(player);
        if (data == null) {
            CronusPauseManager.forceClearFor(player.m_20148_());
            CronusLaserBeamManager.stopFor(player.m_20148_());
            CronusSkills.cancelPendingHenshin(player,
                SystemDataManager.available(player) ? SystemDataManager.get(player) : null);
            REWIND_HISTORY.remove(player.m_20148_());
            return;
        }
        long now = player.m_9236_().m_46467_();

        // 0. 推进变身音效 6 秒先导倒计时（满 6 秒后正式执行变身与演出）
        CronusSkills.tickPendingHenshin(player, data, now);

        // 1. 驱动【Pause】领域与光线枪必杀技一持续绿色激光
        CronusPauseManager.tickPlayerPause(player, data, now);
        CronusLaserBeamManager.tickPlayer(player, data, now);

        // 2. 主动【Shift 滞空】服务端防坠落同步
        if (data.value(CronusKeys.KEY_HOVER_UNTIL, 0.0) > (double) now && !player.m_20096_()) {
            Vec3 v = player.m_20184_();
            if (v.f_82480_ < 0.0) {
                player.m_20256_(new Vec3(v.f_82479_, 0.0, v.f_82481_));
            }
            player.f_19789_ = 0.0f;
        }

        // 3. 每 5 ticks 记录一帧 4 秒回溯快照（保留最近 18 帧 ≈ 4.5s）
        if (now % 5L == 0L && player.m_6084_()) {
            Deque<RewindSnapshot> q = REWIND_HISTORY.computeIfAbsent(player.m_20148_(), k -> new ArrayDeque<>(20));
            q.addLast(new RewindSnapshot(player, data, now));
            while (q.size() > 18) {
                q.removeFirst();
            }
        }

        // 4. 能量道具持续效果维持（高速化 / 跳跃强化）
        if (data.value(CronusKeys.KEY_ITEM_SPEED_UNTIL, 0.0) > (double) now) {
            player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 15, 2, true, false, true));
        }
        if (data.value(CronusKeys.KEY_ITEM_JUMP_UNTIL, 0.0) > (double) now) {
            player.m_7292_(new MobEffectInstance(MobEffects.f_19603_, 15, 2, true, false, true));
            player.f_19789_ = Math.max(0.0f, player.f_19789_ - 0.35f);
        }

        // 5. 每秒更新【时间防御系统】、变身常驻增益（长时夜视 / 水下呼吸 / 免疫中毒）与病毒负荷平复
        if (now % 20L == 0L) {
            boolean dirty = false;
            if (CronusKeys.isTransformed(data)) {
                // 变身态常驻微弱编年史机动性；长时夜视（5分钟，剩余低于2分钟才静默续满，绝不闪屏）+ 水下呼吸 + 免疫中毒
                player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 30, 0, true, false, false));

                MobEffectInstance nv = player.m_21124_(MobEffects.f_19611_);
                if (nv == null || nv.m_19557_() < 2400) {
                    player.m_7292_(new MobEffectInstance(MobEffects.f_19611_, CronusSkills.LONG_BUFF_DURATION, 0, true, false, true));
                }
                MobEffectInstance wb = player.m_21124_(MobEffects.f_19608_);
                if (wb == null || wb.m_19557_() < 2400) {
                    player.m_7292_(new MobEffectInstance(MobEffects.f_19608_, CronusSkills.LONG_BUFF_DURATION, 0, true, false, true));
                }
                if (player.m_20146_() < player.m_6062_()) {
                    player.m_20301_(player.m_6062_());
                }
                if (player.m_21023_(MobEffects.f_19614_)) {
                    player.m_21195_(MobEffects.f_19614_);
                }
                if (data.value(CronusKeys.KEY_BUFFS_APPLIED, 0.0) < 0.5) {
                    data.setValue(CronusKeys.KEY_BUFFS_APPLIED, 1.0);
                    dirty = true;
                }

                if (now % 100L == 0L) {
                    double cap = Math.min(0.45, 0.15 + data.level() * 0.005);
                    double cur = data.value(CronusKeys.KEY_TIME_DEF, 0.0);
                    if (cur < cap) {
                        data.setValue(CronusKeys.KEY_TIME_DEF, Math.min(cap, cur + 0.02));
                        dirty = true;
                    }
                }
            } else if (data.value(CronusKeys.KEY_TIME_DEF, 0.0) > 0.0) {
                data.setValue(CronusKeys.KEY_TIME_DEF, 0.0);
                dirty = true;
            }

            if (!CronusKeys.isPaused(data)) {
                double viral = data.value(CronusKeys.KEY_VIRAL, 0.0);
                if (viral > 0.0) {
                    double cool = CronusKeys.isTransformed(data) ? 1.5 : 3.2;
                    data.setValue(CronusKeys.KEY_VIRAL, Math.max(0.0, viral - cool));
                    dirty = true;
                }
            }

            // 清理过期拳套层数显示
            if (data.value(CronusKeys.KEY_GAUNTLET_STACKS, 0.0) > 0.0
                && data.value(CronusKeys.KEY_GAUNTLET_UNTIL, 0.0) <= (double) now) {
                data.setValue(CronusKeys.KEY_GAUNTLET_STACKS, 0.0);
                dirty = true;
            }
            if (dirty) {
                SystemDataManager.markDirty((Player) player);
            }
            if (now % 100L == 0L && !MUSCLE_LAUNCHED.isEmpty()) {
                MUSCLE_LAUNCHED.entrySet().removeIf(e -> now > e.getValue().expireTick());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // A. 攻击方为 Cronus：处理【伤害拳套强化】【交锋标记】【管理员权限增伤】与【时停悬停伤害池】
        if (source.m_7639_() instanceof ServerPlayer attacker) {
            PlayerSystemData atkData = CronusKeys.activeData(attacker);
            if (atkData != null && victim != attacker) {
                long now = attacker.m_9236_().m_46467_();
                float dmg = event.getAmount();

                // 叠加伤害拳套层数（每击 +10%，持续 7 秒，上限 3 ~ 7 层随等级锚定）
                int maxStacks = Math.min(7, 3 + atkData.level() / 15);
                int curStacks = atkData.value(CronusKeys.KEY_GAUNTLET_UNTIL, 0.0) > (double) now
                    ? (int) Math.round(atkData.value(CronusKeys.KEY_GAUNTLET_STACKS, 0.0)) : 0;
                int nextStacks = Math.min(maxStacks, curStacks + 1);
                atkData.setValue(CronusKeys.KEY_GAUNTLET_STACKS, (double) nextStacks);
                atkData.setValue(CronusKeys.KEY_GAUNTLET_UNTIL, (double) (now + 140L));
                atkData.setValue(CronusKeys.KEY_LAST_HIT_OUT, (double) now);
                SystemDataManager.markDirty((Player) attacker);

                // 应用缺陷护罩Ⅱ近战 1/3 技能攻击加成、电锯聚能必杀、拳套层数与肌肉强化道具乘区（仅非 Restart / 非肌肉落地二次结算阶段）
                boolean muscleActive = atkData.value(CronusKeys.KEY_ITEM_MUSCLE_UNTIL, 0.0) > (double) now;
                if (!CronusPauseManager.isResolvingRestart() && !resolvingMuscleSlam) {
                    boolean isMeleeHit = "player".equals(source.m_19385_());
                    boolean sawBurstTriggered = false;
                    if (isMeleeHit && CronusItems.isBugvisor(attacker.m_21205_())) {
                        dmg += CronusKeys.bugvisorAttackBonus(atkData);
                    }
                    // 电锯模式必杀技二：绿色能量聚集于电锯，下一次近战攻击引爆巨额伤害！
                    if (isMeleeHit && CronusKeys.weaponMode(atkData) == CronusKeys.MODE_CHAINSAW) {
                        float overcharge = consumeChainsawOvercharge(attacker, atkData, now);
                        if (overcharge > 0.0f) {
                            dmg += overcharge;
                            sawBurstTriggered = true;
                            emitChainsawOverchargeBurst(attacker, victim);
                        }
                    }
                    dmg *= (float) (1.0 + curStacks * 0.10);
                    if (muscleActive) {
                        dmg *= 1.50f;
                    }
                    if (CronusKeys.isPaused(atkData)) {
                        dmg *= 1.12f; // 编年史管理员权限：时停内追击 +12%
                    }
                    event.setAmount(dmg);

                    boolean armorPiercing = source.m_269533_(DamageTypeTags.f_268490_);
                    int popup = (sawBurstTriggered || armorPiercing || dmg >= 30.0f) ? CronusFxMsg.POPUP_CRITICAL
                        : (muscleActive || nextStacks >= 3 || dmg >= 15.0f ? CronusFxMsg.POPUP_GREAT : CronusFxMsg.POPUP_HIT);

                    // 若目标处于完全时停，转入悬停伤害蓄水池（保留无视护甲标签），留待 Restart 一并引爆
                    if (CronusPauseManager.interceptDeferredDamage(attacker, atkData, victim, dmg, armorPiercing)) {
                        victim.f_19802_ = 0;
                        event.setAmount(0.0f);
                        event.setCanceled(true);
                        return;
                    }

                    // 能量道具【肌肉化】：击飞目标并记录落地追加砸地伤害（参考效果：打击贴图升级为 GREAT!）
                    if (muscleActive && victim.m_6084_()) {
                        Vec3 away = victim.m_20182_().m_82546_(attacker.m_20182_());
                        double hLen = Math.max(1e-4, Math.hypot(away.f_82479_, away.f_82481_));
                        victim.m_20256_(new Vec3((away.f_82479_ / hLen) * 0.68, 0.48, (away.f_82481_ / hLen) * 0.68));
                        victim.f_19864_ = true;
                        MUSCLE_LAUNCHED.put(victim.m_20148_(),
                            new MuscleLaunch(attacker.m_20148_(), Math.max(3.0f, dmg * 0.25f), now + 55L));
                    }

                    // 常态命中弹出原剧街机风 HIT! / GREAT! / CRITICAL!（高频持续激光与回旋锯按节拍限流防刷屏）
                    if (!suppressRapidPopup || sawBurstTriggered) {
                        CronusNet.popupOn(victim, popup);
                    }
                }
            }
        }

        // B. 受击方为 Cronus：处理【中毒免疫】【光速/雷电/魔法弱点】【时间防御】【交锋减伤】与【小伤害免疫】
        if (victim instanceof ServerPlayer defender) {
            PlayerSystemData defData = CronusKeys.activeData(defender);
            if (defData == null) {
                return;
            }
            if (CronusKeys.isTransformed(defData)) {
                String srcId = source.m_19385_();
                if (srcId != null && srcId.toLowerCase().contains("poison")) {
                    event.setAmount(0.0f);
                    event.setCanceled(true);
                    return;
                }
            }
            long now = defender.m_9236_().m_46467_();
            float dmg = event.getAmount();
            boolean lightOrRuleWeakness = isLightOrSoulRuleDamage(source);

            // 弱点：超越时间的光速雷电、魔法与高维魂则冲击对其造成 +25% 伤害，并在时停中引发悖论耗蓝
            if (lightOrRuleWeakness) {
                dmg *= 1.25f;
                if (CronusKeys.isPaused(defData)) {
                    double maxMp = CronusKeys.maxStat(defData, StatType.MP);
                    defData.setMp(Math.max(0.0, defData.mp() - maxMp * 0.08));
                    CronusKeys.addViralStrain(defender, defData, 10.0, now);
                }
            }

            // 被动【时间防御系统】：变身态最高减免 15% ~ 45%（面对光速/魂则弱点时效能折半）
            if (CronusKeys.isTransformed(defData)) {
                double timeDef = defData.value(CronusKeys.KEY_TIME_DEF, 0.0);
                if (lightOrRuleWeakness) {
                    timeDef *= 0.5;
                }
                dmg *= (float) Math.max(0.35, 1.0 - timeDef);
            }

            // 被动【减伤系统（交锋拼刀）】：出击后 0.6s 内对拼承受伤害降低 25%
            if (now - (long) defData.value(CronusKeys.KEY_LAST_HIT_OUT, -100.0) <= 12L) {
                dmg *= 0.75f;
            }

            // 能量道具【钢铁化】：额外减免 35% 伤害
            if (defData.value(CronusKeys.KEY_ITEM_IRON_UNTIL, 0.0) > (double) now) {
                dmg *= 0.65f;
            }

            // 被动【小伤害免疫】：变身态下低于最大生命 2% ~ 6%（随等级成长）的擦伤完全无效化，弹出 GUARD!
            if (CronusKeys.isTransformed(defData) && !lightOrRuleWeakness) {
                double maxHp = Math.max(20.0, defender.m_21233_());
                double thresholdRatio = Math.min(0.06, 0.02 + defData.level() * 0.0007);
                if (dmg <= (float) (maxHp * thresholdRatio)) {
                    CronusNet.popupOn(defender, CronusFxMsg.POPUP_GUARD);
                    event.setAmount(0.0f);
                    event.setCanceled(true);
                    return;
                }
            }

            event.setAmount(dmg);
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (event.getEffectInstance() == null || event.getEffectInstance().m_19544_() != MobEffects.f_19614_) {
            return;
        }
        if (CronusKeys.isTransformed(CronusKeys.activeData(sp))) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.m_9236_().f_46443_) {
            return;
        }
        MuscleLaunch launch = MUSCLE_LAUNCHED.remove(victim.m_20148_());
        if (launch == null || !victim.m_6084_()) {
            return;
        }
        long now = victim.m_9236_().m_46467_();
        if (now > launch.expireTick() || event.getDistance() < 0.45f) {
            return;
        }
        if (victim.m_9236_() instanceof ServerLevel sl) {
            ServerPlayer attacker = sl.m_7654_().m_6846_().m_11259_(launch.attackerId());
            if (attacker != null && attacker.m_6084_()) {
                resolvingMuscleSlam = true;
                try {
                    victim.f_19802_ = 0;
                    TargetUtil.hurt(victim, ModDamageTypes.technique(attacker), launch.slamDamage());
                    CronusNet.popupOn(victim, CronusFxMsg.POPUP_GREAT);
                } finally {
                    resolvingMuscleSlam = false;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        MUSCLE_LAUNCHED.remove(event.getEntity().m_20148_());
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            CronusLaserBeamManager.stopFor(deadPlayer.m_20148_());
            CronusSkills.cancelPendingHenshin(deadPlayer,
                SystemDataManager.available(deadPlayer) ? SystemDataManager.get(deadPlayer) : null);
        }
        if (!(event.getSource().m_7639_() instanceof ServerPlayer killer)) {
            return;
        }
        PlayerSystemData data = CronusKeys.activeData(killer);
        if (data == null) {
            return;
        }
        long now = killer.m_9236_().m_46467_();
        // 旋转回旋踢（Critical Crews-Aid 长按）击杀：1.5x 系统经验奖励 + 平复病毒负荷
        if (data.value(CronusKeys.KEY_SPIN_XP_UNTIL, 0.0) >= (double) now) {
            double bonusSysXp = Math.max(8.0, event.getEntity().m_21233_() * 0.55);
            DrawAttunementUnlock.grantQuirkXp(killer, data, bonusSysXp, "「绝版哒！」社长裁决");
            CronusKeys.addViralStrain(killer, data, -10.0, now);
        }
    }

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Player attacking = event.getAttackingPlayer();
        if (!(attacking instanceof ServerPlayer killer)) {
            return;
        }
        PlayerSystemData data = CronusKeys.activeData(killer);
        if (data != null && data.value(CronusKeys.KEY_SPIN_XP_UNTIL, 0.0) >= (double) killer.m_9236_().m_46467_()) {
            // 原版掉落经验球同步提升至 1.5 倍
            event.setDroppedExperience((int) Math.ceil(event.getDroppedExperience() * 1.5));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            CronusPauseManager.forceClearFor(sp.m_20148_());
            CronusLaserBeamManager.stopFor(sp.m_20148_());
            CronusSkills.cancelPendingHenshin(sp,
                SystemDataManager.available(sp) ? SystemDataManager.get(sp) : null);
            REWIND_HISTORY.remove(sp.m_20148_());
        }
    }

    private static boolean isLightOrSoulRuleDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source.m_269533_(DamageTypeTags.f_268725_) // IS_LIGHTNING
            || source.m_269533_(DamageTypeTags.f_268738_)) { // BYPASSES_INVULNERABILITY
            return true;
        }
        String msgId = source.m_19385_();
        if (msgId == null) {
            return false;
        }
        String low = msgId.toLowerCase();
        return low.contains("lightning") || low.contains("magic") || low.contains("sonic")
            || low.contains("soul") || low.contains("world_slash");
    }
}
