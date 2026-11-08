package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
                data.setValue(CronusKeys.KEY_HOVER_UNTIL, (double) (now + 26L));
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
        PlayerSystemData data = CronusKeys.activeData(player);
        if (data == null) {
            CronusPauseManager.forceClearFor(player.m_20148_());
            REWIND_HISTORY.remove(player.m_20148_());
            return;
        }
        long now = player.m_9236_().m_46467_();

        // 1. 驱动【Pause】领域
        CronusPauseManager.tickPlayerPause(player, data, now);

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

        // 5. 每秒更新【时间防御系统】与平复非时停态的【Bugster 病毒负荷】
        if (now % 20L == 0L) {
            boolean dirty = false;
            if (CronusKeys.isTransformed(data)) {
                // 变身态常驻微弱编年史机动性，每 5 秒叠加 2% 时间防御，上限随等级 15% ~ 45%
                player.m_7292_(new MobEffectInstance(MobEffects.f_19596_, 30, 0, true, false, false));
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

                // 应用拳套层数与肌肉强化道具乘区（仅非 Restart 结算阶段）
                if (!CronusPauseManager.isResolvingRestart()) {
                    dmg *= (float) (1.0 + curStacks * 0.10);
                    if (atkData.value(CronusKeys.KEY_ITEM_MUSCLE_UNTIL, 0.0) > (double) now) {
                        dmg *= 1.30f;
                    }
                    if (CronusKeys.isPaused(atkData)) {
                        dmg *= 1.12f; // 编年史管理员权限：时停内追击 +12%
                    }
                    event.setAmount(dmg);

                    // 若目标处于完全时停，转入悬停伤害蓄水池，留待 Restart 一并引爆
                    if (CronusPauseManager.interceptDeferredDamage(attacker, atkData, victim, dmg)) {
                        event.setAmount(0.0f);
                        event.setCanceled(true);
                        return;
                    }
                }

                // 常态命中弹出原剧街机风 HIT! / GREAT! / CRITICAL!
                if (!CronusPauseManager.isResolvingRestart()) {
                    int popup = dmg >= 30.0f ? CronusFxMsg.POPUP_CRITICAL
                        : (nextStacks >= 3 || dmg >= 15.0f ? CronusFxMsg.POPUP_GREAT : CronusFxMsg.POPUP_HIT);
                    CronusNet.popupOn(victim, popup);
                }
            }
        }

        // B. 受击方为 Cronus：处理【光速/雷电/魔法弱点】【时间防御】【交锋减伤】与【小伤害免疫】
        if (victim instanceof ServerPlayer defender) {
            PlayerSystemData defData = CronusKeys.activeData(defender);
            if (defData == null) {
                return;
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
    public static void onLivingDeath(LivingDeathEvent event) {
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
            REWIND_HISTORY.remove(sp.m_20148_());
        }
    }

    private static boolean isLightOrSoulRuleDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source.m_269533_(DamageTypeTags.f_268738_) // IS_LIGHTNING
            || source.m_269533_(DamageTypeTags.f_268415_)) { // BYPASSES_INVULNERABILITY
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
