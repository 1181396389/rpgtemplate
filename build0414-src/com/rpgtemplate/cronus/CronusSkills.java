package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.templates.TemplateHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 假面骑士 Cronus（{@code rpgtemplate:cronus_1}）全部 8 个主动技能实现。
 */
public final class CronusSkills {
    /** 变身后赋予的长时夜视/水下呼吸基准时长（5 分钟，低于 2 分钟时静默续满，彻底杜绝原版夜视低时长闪屏）。 */
    public static final int LONG_BUFF_DURATION = 6000;

    private record PendingHenshin(long executeAtTick, boolean fast) {
    }

    /** 音效先导队列：先播变身音效，满 6 秒（120 ticks）后再正式执行变身。 */
    private static final Map<UUID, PendingHenshin> PENDING_HENSHINS = new HashMap<>();

    public static boolean isHenshinPending(ServerPlayer player) {
        return player != null && PENDING_HENSHINS.containsKey(player.m_20148_());
    }

    /** 服务端每 tick 推进变身音效 6 秒倒计时；抵达 6 秒瞬间正式执行变身演出与状态切换。 */
    public static void tickPendingHenshin(ServerPlayer player, PlayerSystemData data, long now) {
        if (player == null) {
            return;
        }
        PendingHenshin pending = PENDING_HENSHINS.get(player.m_20148_());
        if (pending == null) {
            return;
        }
        if (!player.m_6084_() || data == null || CronusKeys.isTransformed(data)) {
            cancelPendingHenshin(player, data);
            return;
        }
        long remaining = pending.executeAtTick() - now;
        if (remaining > 0L) {
            if (remaining % 20L == 0L) {
                int sec = (int) (remaining / 20L);
                Msg.actionBar(player, Component.m_237110_("message.rpgtemplate.cronus.henshin_audio_countdown", sec)
                    .m_130940_(ChatFormatting.GREEN));
            }
            return;
        }
        PENDING_HENSHINS.remove(player.m_20148_());
        executeHenshinTransformation(player, data, now, pending.fast());
    }

    /** 取消尚未执行变身的 6 秒音效先导（若为右键卡带快捷变身，安全归还已收纳进腰带的卡带）。 */
    public static void cancelPendingHenshin(ServerPlayer player, PlayerSystemData data) {
        if (player == null) {
            return;
        }
        PendingHenshin removed = PENDING_HENSHINS.remove(player.m_20148_());
        if (removed == null) {
            return;
        }
        if (data != null && !CronusKeys.isTransformed(data)
            && data.value(CronusKeys.KEY_GASHAT_IN_HENSHIN, 0.0) > 0.5) {
            long heldToken = (long) data.value(CronusKeys.KEY_GASHAT_HELD_TOKEN, 0.0);
            data.setValue(CronusKeys.KEY_GASHAT_IN_HENSHIN, 0.0);
            data.setValue(CronusKeys.KEY_GASHAT_HELD_TOKEN, 0.0);
            CronusGearManager.clearAllDriverLoadedFlags(player);
            CronusGearManager.returnConsumedGashat(player, data, heldToken);
            SystemDataManager.markDirty((Player) player);
            SystemDataManager.sync(player);
        }
    }

    private CronusSkills() {
    }

    private static boolean checkDriverReady(ServerPlayer player, PlayerSystemData data, long now) {
        if (data.value(CronusKeys.KEY_OVERHEAT_UNTIL, 0.0) > (double) now) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.overheated")
                .m_130940_(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /**
     * 触发变身先导流程：立即播放对应编年史变身音效，并在音效播放满 6 秒（120 ticks）后再正式执行变身与演出。
     * ({@code fast=false} 为完整音效 + 完整 7.8s 演出；{@code fast=true} 为 9 秒后音效 + 快捷版 2.2s 快速变身)
     */
    public static void performHenshinOn(ServerPlayer player, PlayerSystemData data, long now, boolean fast) {
        if (fast) {
            // 快捷版快速变身：立即播放原视频 9 秒后的编年史变身音效
            CronusSounds.playHenshinFast(player);
        } else {
            // 技能 1 完整变身：立即播放原视频完整编年史变身音效
            CronusSounds.playHenshin(player);
        }
        PENDING_HENSHINS.put(player.m_20148_(),
            new PendingHenshin(now + CronusKeys.HENSHIN_AUDIO_LEAD_TICKS, fast));
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.henshin_audio_warmup")
            .m_130940_(ChatFormatting.GREEN));
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    /** 音效播放满 6 秒后正式执行变身状态切换、常驻增益与 3D 变身演出。 */
    private static void executeHenshinTransformation(ServerPlayer player, PlayerSystemData data, long now, boolean fast) {
        int revealDelay = fast ? CronusKeys.HENSHIN_FAST_REVEAL_TICKS : CronusKeys.HENSHIN_FULL_REVEAL_TICKS;
        int beltGashatDelay = fast ? 0 : CronusKeys.HENSHIN_FULL_GASHAT_DOCK_TICKS;

        data.setValue(CronusKeys.KEY_TRANSFORMED, 1.0);
        data.setValue(CronusKeys.KEY_TRANSFORM_SINCE, (double) now);
        data.setValue(CronusKeys.KEY_TIME_DEF, 0.05);
        data.setValue(CronusKeys.KEY_HENSHIN_VIA_DRIVER, fast ? 1.0 : 0.0);
        data.setValue(CronusKeys.KEY_SUIT_REVEAL_TICK, (double) (now + revealDelay));
        data.setValue(CronusKeys.KEY_BELT_GASHAT_TICK, (double) (now + beltGashatDelay));

        // 变身即赋予长时夜视（5分钟，不闪烁）、长时水下呼吸并立即净化既有中毒
        player.m_21195_(MobEffects.f_19614_); // POISON
        player.m_7292_(new MobEffectInstance(MobEffects.f_19611_, LONG_BUFF_DURATION, 0, true, false, true));
        player.m_7292_(new MobEffectInstance(MobEffects.f_19608_, LONG_BUFF_DURATION, 0, true, false, true));
        player.m_20301_(player.m_6062_());
        data.setValue(CronusKeys.KEY_BUFFS_APPLIED, 1.0);

        // 先广播带动画延时的 3D 装甲同步包（在变身动画结束后再显现盔甲，此前保持人形态与 3D 故障驱动器Ⅱ）
        CronusNet.syncSuitState(player, true, revealDelay, beltGashatDelay);

        if (player.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(),
                new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, player.m_19879_(),
                    player.m_20185_(), player.m_20186_() + 0.05, player.m_20189_(), 3.6f, 1));
            CronusNet.sendNear(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(),
                new CronusFxMsg(fast ? CronusFxMsg.KIND_HENSHIN_FAST : CronusFxMsg.KIND_HENSHIN_SEQ,
                    player.m_19879_(),
                    player.m_20185_(), player.m_20186_(), player.m_20189_(), 1.0f, 0));
        }
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.henshin_on")
            .m_130940_(ChatFormatting.GREEN));
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    /** 兼容入口：默认完整变身演出。 */
    public static void performHenshinOn(ServerPlayer player, PlayerSystemData data, long now) {
        performHenshinOn(player, data, now, false);
    }

    /** 统一解除变身执行（仅通过技能触发：终止时停、停止持续激光、清除电锯聚能、收回缺陷护罩Ⅱ、归还卡带、同步还原外观）。 */
    public static void performHenshinOff(ServerPlayer player, PlayerSystemData data) {
        cancelPendingHenshin(player, data);
        CronusLaserBeamManager.stopFor(player.m_20148_());
        data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_UNTIL, 0.0);
        data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_DMG, 0.0);
        if (player.m_9236_() instanceof ServerLevel sl) {
            CronusNet.sendNear(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(),
                new CronusFxMsg(CronusFxMsg.KIND_CHAINSAW_CHARGE, player.m_19879_(),
                    player.m_20185_(), player.m_20186_(), player.m_20189_(), 0.0f, 0));
        }
        if (CronusKeys.isPaused(data)) {
            CronusPauseManager.restart(player, data, 1.20, false);
        }
        boolean returnedGashat = CronusKeys.clearTransformedState(player, data);
        Msg.actionBar(player, Component.m_237115_(returnedGashat
                ? "message.rpgtemplate.cronus.henshin_off_gashat_returned"
                : "message.rpgtemplate.cronus.henshin_off")
            .m_130940_(returnedGashat ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        SystemDataManager.markDirty((Player) player);
        SystemDataManager.sync(player);
    }

    /**
     * 腰间装备【故障驱动器Ⅱ】后右键【假面骑士编年史转玩卡带】触发快捷版变身：
     * <ul>
     *   <li>若已变身则不可再用卡带解除（只能使用【编年史变身】技能解除并归还卡带）。</li>
     *   <li>变身成功后直接消耗背包中的编年史转玩卡带（插入驱动器），解除变身时自动原样归还。</li>
     * </ul>
     */
    public static boolean tryHenshinWithGashat(ServerPlayer player, PlayerSystemData data,
                                               net.minecraft.world.item.ItemStack gashat, long now) {
        if (!checkDriverReady(player, data, now)) {
            return false;
        }
        if (CronusKeys.isTransformed(data)) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.gashat_skill_only_off")
                .m_130940_(ChatFormatting.YELLOW));
            return false;
        }
        if (isHenshinPending(player)) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.henshin_pending")
                .m_130940_(ChatFormatting.YELLOW));
            return false;
        }
        double cost = 4.0;
        if (data.mp() < cost) {
            Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.driver_low_mp")
                .m_130940_(ChatFormatting.RED));
            return false;
        }
        data.setMp(Math.max(0.0, data.mp() - cost));

        // 记下卡带原属主与唯一令牌，消耗手中卡带并插入腰间故障驱动器Ⅱ
        java.util.UUID gOwner = CronusItems.ownerOf(gashat);
        if (gOwner == null) {
            gOwner = player.m_20148_();
        }
        long gToken = CronusItems.tokenOf(gashat);
        gashat.m_41774_(1);

        var wornDriver = CronusItems.getWornDriver(player);
        if (!wornDriver.m_41619_()) {
            CronusItems.setDriverLoaded(wornDriver, gOwner, gToken);
        }
        data.setValue(CronusKeys.KEY_GASHAT_IN_HENSHIN, 1.0);
        data.setValue(CronusKeys.KEY_GASHAT_HELD_TOKEN, (double) gToken);
        data.setValue(CronusKeys.KEY_GASHAT_STATE, (double) CronusGearManager.STATE_IN_DRIVER);

        performHenshinOn(player, data, now, true);
        return true;
    }

    /** 技能 1：编年史变身（檀正宗人形态 ⇄ 假面骑士 Cronus 完整演出切换；与腰带卡带快捷变身完全互通）。 */
    public static final class HenshinSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (CronusKeys.isTransformed(ctx.data)) {
                ctx.cooldown(30);
                performHenshinOff(ctx.player, ctx.data);
                return;
            }
            if (isHenshinPending(ctx.player)) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.henshin_pending")
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
            if (!ctx.consume(ctx.cost(4.0))) {
                return;
            }
            ctx.cooldown(160);
            performHenshinOn(ctx.player, ctx.data, ctx.now, false);
        }
    }

    /** 技能 2：Pause / Restart（时间暂停与时间重开领域切换）。 */
    public static final class PauseSkill implements Skill {
        @Override
        public boolean bypassCooldown(SkillContext ctx) {
            return CronusKeys.isPaused(ctx.data);
        }

        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (CronusKeys.isPaused(ctx.data)) {
                CronusPauseManager.restart(ctx.player, ctx.data, 1.20, true);
                ctx.cooldown(70);
                return;
            }
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (!ctx.consume(ctx.cost(6.0))) {
                return;
            }
            CronusKeys.addViralStrain(ctx.player, ctx.data, 8.0, ctx.now);
            if (!CronusKeys.isTransformed(ctx.data)) {
                return;
            }
            CronusPauseManager.startPause(ctx.player, ctx.data, ctx.now);
        }
    }

    /**
     * 技能 3：时序瞬移（Chrono Blink，取代原模式切换槽位；武装模式已交由 Gashacon 缺陷护罩Ⅱ 蹲下右键瞬切）。
     * <ul>
     *   <li>常态点按瞄准敌对目标（≤18m）：瞬间绕后闪现至目标背后 2m 并自动转身锁定其后背，便于无缝衔接链锯/飞踢连段。</li>
     *   <li>常态点按指向空地/高台（≤16m）：自由时空间跃迁并重置坠落高度。</li>
     *   <li>潜行 + 点按：向后瞬移 8m 拉开身位进行时序闪避。</li>
     *   <li>【Pause】时停期间：冷却减半、能耗降低 40%，还原剧场级全向鬼魅瞬移。</li>
     * </ul>
     */
    public static final class ChronoBlinkSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            boolean paused = CronusKeys.isPaused(ctx.data);
            double cost = ctx.cost(paused ? 1.8 : 3.0);
            if (!ctx.consume(cost)) {
                return;
            }
            ctx.cooldown(paused ? 14 : 28);

            ServerPlayer sp = ctx.player;
            Level level = sp.m_9236_();
            Vec3 fromPos = sp.m_20182_();
            Vec3 fromMid = fromPos.m_82520_(0.0, sp.m_20206_() * 0.55, 0.0);
            Vec3 destPos;
            float newYaw = sp.m_146908_();
            float newPitch = sp.m_146909_();
            boolean backstab = false;

            if (sp.m_6144_()) {
                // 潜行点按：向后 8m 时序回避瞬移
                Vec3 lookH = new Vec3(-Math.sin(Math.toRadians(sp.m_146908_())), 0.0, Math.cos(Math.toRadians(sp.m_146908_())));
                destPos = findSafeBlinkPos(level, sp, fromPos, lookH.m_82490_(-1.0), 8.0);
            } else {
                // 优先检测准星前方 18m 内的锁定目标 → 闪现至目标身后！
                LivingEntity target = findBlinkTarget(sp, 18.0);
                if (target != null) {
                    Vec3 tPos = target.m_20182_();
                    double tYawRad = Math.toRadians(target.m_146908_());
                    Vec3 tFwd = new Vec3(-Math.sin(tYawRad), 0.0, Math.cos(tYawRad));
                    // 优先落点：目标背后 2.0 格；若被墙阻挡则尝试侧后或前方安全位
                    Vec3 behind = tPos.m_82546_(tFwd.m_82490_(2.0));
                    Vec3 candidate = resolveStandable(level, behind);
                    if (candidate == null) {
                        Vec3 toSelf = fromPos.m_82546_(tPos);
                        double hLen = Math.max(1e-4, Math.hypot(toSelf.f_82479_, toSelf.f_82481_));
                        candidate = resolveStandable(level, tPos.m_82520_(toSelf.f_82479_ / hLen * 1.8, 0.0, toSelf.f_82481_ / hLen * 1.8));
                    }
                    if (candidate != null) {
                        destPos = candidate;
                        backstab = true;
                        Vec3 faceDir = target.m_146892_().m_82546_(destPos.m_82520_(0.0, sp.m_20192_(), 0.0));
                        double flat = Math.max(1e-4, Math.hypot(faceDir.f_82479_, faceDir.f_82481_));
                        newYaw = (float) (Mth.m_14136_(faceDir.f_82481_, faceDir.f_82479_) * (180.0 / Math.PI)) - 90.0f;
                        newPitch = (float) (-(Mth.m_14136_(faceDir.f_82480_, flat) * (180.0 / Math.PI)));
                    } else {
                        destPos = findSafeBlinkPos(level, sp, fromPos, sp.m_20154_().m_82541_(), 16.0);
                    }
                } else {
                    destPos = findSafeBlinkPos(level, sp, fromPos, sp.m_20154_().m_82541_(), 16.0);
                }
            }

            // 执行瞬移并同步朝向（锁敌背刺强制转向目标后背，自由跃迁保持玩家实时视角防抖动），清零坠落伤害
            if (backstab && sp.f_8906_ != null) {
                sp.f_8906_.m_9774_(destPos.f_82479_, destPos.f_82480_, destPos.f_82481_, newYaw, newPitch);
            } else {
                sp.m_6021_(destPos.f_82479_, destPos.f_82480_, destPos.f_82481_);
            }
            sp.f_19789_ = 0.0f;
            sp.m_20256_(Vec3.f_82478_);
            sp.f_19864_ = true;

            Vec3 toMid = destPos.m_82520_(0.0, sp.m_20206_() * 0.55, 0.0);
            if (level instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, fromPos.f_82479_, fromPos.f_82480_, fromPos.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, sp.m_19879_(),
                        fromPos.f_82479_, fromPos.f_82480_ + 0.06, fromPos.f_82481_, 1.7f, 0));
                CronusNet.sendNear(sl, destPos.f_82479_, destPos.f_82480_, destPos.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, sp.m_19879_(),
                        destPos.f_82479_, destPos.f_82480_ + 0.06, destPos.f_82481_, 2.0f, 1));
                CronusNet.sendNear(sl, destPos.f_82479_, destPos.f_82480_, destPos.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_BEAM_LINE, sp.m_19879_(),
                        fromMid.f_82479_, fromMid.f_82480_, fromMid.f_82481_,
                        toMid.f_82479_, toMid.f_82480_, toMid.f_82481_,
                        1.0f, CronusFxMsg.BEAM_BLINK));
            }
            CronusSounds.playBlink(sp);
            if (backstab) {
                Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.blink_backstab")
                    .m_130940_(ChatFormatting.GREEN));
            }
        }

        private static LivingEntity findBlinkTarget(ServerPlayer sp, double range) {
            Vec3 eye = sp.m_146892_();
            Vec3 look = sp.m_20154_().m_82541_();
            AABB box = sp.m_20191_().m_82377_(range, range * 0.75, range);
            LivingEntity best = null;
            double bestScore = 0.88; // 约 28° 精准锁定锥
            double rSq = range * range;
            for (LivingEntity e : sp.m_9236_().m_45976_(LivingEntity.class, box)) {
                if (e == sp || !e.m_6084_() || e.m_20280_(sp) > rSq) {
                    continue;
                }
                Vec3 to = e.m_20191_().m_82399_().m_82546_(eye);
                double dist = Math.max(0.5, to.m_82553_());
                double dot = to.m_82490_(1.0 / dist).m_82526_(look);
                if (dot > bestScore) {
                    bestScore = dot;
                    best = e;
                }
            }
            return best;
        }

        private static Vec3 findSafeBlinkPos(Level level, ServerPlayer sp, Vec3 origin, Vec3 dir, double maxDist) {
            Vec3 eye = sp.m_146892_();
            Vec3 rawEnd = eye.m_82549_(dir.m_82490_(maxDist));
            BlockHitResult hit = level.m_45547_(new ClipContext(
                eye, rawEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));

            Vec3 probe = hit.m_6662_() == HitResult.Type.MISS
                ? origin.m_82549_(dir.m_82490_(maxDist))
                : hit.m_82450_().m_82546_(dir.m_82490_(0.65));

            // 沿命中点或终点向上/向回逐格检索可站立安全空间（绝不卡墙窒息）
            Vec3 direct = resolveStandable(level, probe);
            if (direct != null) {
                return direct;
            }
            if (hit.m_6662_() != HitResult.Type.MISS) {
                Vec3 atop = resolveStandable(level, Vec3.m_82539_(hit.m_82425_().m_7494_()));
                if (atop != null) {
                    return atop;
                }
            }
            double totalDist = origin.m_82554_(probe);
            for (double d = totalDist - 0.8; d >= 1.0; d -= 0.8) {
                Vec3 step = origin.m_82549_(dir.m_82490_(d));
                Vec3 ok = resolveStandable(level, step);
                if (ok != null) {
                    return ok;
                }
            }
            return origin;
        }

        private static Vec3 resolveStandable(Level level, Vec3 pos) {
            for (int dy = 0; dy <= 2; dy++) {
                double y = pos.f_82480_ + dy;
                BlockPos feet = BlockPos.m_274561_(pos.f_82479_, y, pos.f_82481_);
                BlockPos head = feet.m_7494_();
                if (!level.m_8055_(feet).m_280555_() && !level.m_8055_(head).m_280555_()) {
                    return new Vec3(pos.f_82479_, y, pos.f_82481_);
                }
            }
            return null;
        }
    }

    /** 技能 4：能量道具抽取（仅变身态可用；智能随机获取高速化/肌肉化/钢铁化/跳跃强化/回复道具并弹出 ITEM GET!）。 */
    public static final class EnergyItemSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            if (!ctx.consume(ctx.cost(4.5))) {
                return;
            }
            ctx.cooldown(140);
            double maxHp = CronusKeys.maxStat(ctx.data, StatType.HP);
            int roll = (ctx.data.hp() < maxHp * 0.45)
                ? 4
                : ctx.player.m_217043_().m_188503_(5);

            String msgKey;
            int iconTicks;
            switch (roll) {
                case 0 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_SPEED_UNTIL, (double) (ctx.now + 250L));
                    msgKey = "message.rpgtemplate.cronus.item_speed";
                    iconTicks = 250;
                }
                case 1 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_MUSCLE_UNTIL, (double) (ctx.now + 250L));
                    msgKey = "message.rpgtemplate.cronus.item_muscle";
                    iconTicks = 250;
                }
                case 2 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_IRON_UNTIL, (double) (ctx.now + 250L));
                    msgKey = "message.rpgtemplate.cronus.item_iron";
                    iconTicks = 250;
                }
                case 3 -> {
                    ctx.data.setValue(CronusKeys.KEY_ITEM_JUMP_UNTIL, (double) (ctx.now + 250L));
                    msgKey = "message.rpgtemplate.cronus.item_jump";
                    iconTicks = 250;
                }
                default -> {
                    TemplateHelper.healRatio(ctx.player, ctx.data, 0.22);
                    CronusKeys.addViralStrain(ctx.player, ctx.data, -12.0, ctx.now);
                    msgKey = "message.rpgtemplate.cronus.item_recovery";
                    iconTicks = 55;
                }
            }
            CronusSounds.playItemGet(ctx.player);
            CronusNet.popupOn(ctx.player, CronusFxMsg.POPUP_ITEM_GET);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_ICON, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        (float) iconTicks, roll));
            }
            Msg.actionBar(ctx.player, Component.m_237115_(msgKey).m_130940_(ChatFormatting.YELLOW));
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    /**
     * 技能 5：Critical Crews-Aid（时计飞踢 / 蓄力旋转回旋踢）。
     * <ul>
     *   <li>点按（&lt;10 ticks）：迅捷突进时计踢。</li>
     *   <li>长按（≥10 ticks）：展开脚下旋转时钟指针发动 360° 旋转回旋踢，赋予 1.5x 击败经验加成；
     *   若在【Pause】期间命中生命低于 15% 且被完全冻结（FULL）的目标，直接触发「绝版哒！」处决并引爆 1.35x Restart！</li>
     * </ul>
     */
    public static final class CrewsAidSkill implements Skill {
        private static final String KEY_CHARGE_START = "cr.kick_charge";

        @Override
        public boolean holdable() {
            return true;
        }

        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)) {
                return;
            }
            ctx.data.setValue(KEY_CHARGE_START, (double) ctx.now);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_() + 0.05, ctx.player.m_20189_(), 2.2f, 0));
            }
        }

        @Override
        public void onHoldTick(SkillContext ctx, HeldState held) {
            double start = ctx.data.value(KEY_CHARGE_START, -1.0);
            if (start < 0.0) {
                return;
            }
            long heldTicks = ctx.now - (long) start;
            if (heldTicks == 10L) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.spin_ready")
                    .m_130940_(ChatFormatting.GREEN));
                ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    SoundEvents.f_11983_, SoundSource.PLAYERS, 0.8f, 1.8f);
            }
            if (heldTicks >= 26L) {
                ctx.data.setValue(KEY_CHARGE_START, -1.0);
                executeKick(ctx, true);
            }
        }

        @Override
        public void onRelease(SkillContext ctx, HeldState held) {
            double start = ctx.data.value(KEY_CHARGE_START, -1.0);
            if (start < 0.0) {
                return;
            }
            ctx.data.setValue(KEY_CHARGE_START, -1.0);
            long heldTicks = ctx.now - (long) start;
            executeKick(ctx, heldTicks >= 10L);
        }

        private void executeKick(SkillContext ctx, boolean spinKick) {
            double cost = ctx.cost(spinKick ? 8.5 : 5.5);
            if (!ctx.consume(cost)) {
                return;
            }
            ctx.cooldown(spinKick ? 130 : 75);
            CronusSounds.playCrewsAid(ctx.player);

            if (!spinKick) {
                TemplateHelper.dashForward(ctx.player, 1.15, 0.18);
                ctx.player.f_19789_ = 0.0f;
                float dmg = ctx.damage(1.0);
                for (LivingEntity target : coneTargets(ctx.player, 5.0, 0.25)) {
                    TargetUtil.hurt(target, ModDamageTypes.technique(ctx.player), dmg);
                }
                if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_FINISHER_FX, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            1.0f, CronusFxMsg.FINISHER_CRITICAL_CREWS_AID));
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_RIDER_KICK, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_() + 0.1, ctx.player.m_20189_(), 3.2f, 0));
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            0.95f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
                }
                return;
            }

            // 蓄力旋转回旋踢：开启 5 秒 1.5x 经验倍率窗口 + 时停中对 <15% HP（OUT）完全冻结目标触发「绝版」处决！
            ctx.data.setValue(CronusKeys.KEY_SPIN_XP_UNTIL, (double) (ctx.now + 100L));
            TemplateHelper.dashForward(ctx.player, 0.90, 0.26);
            ctx.player.f_19789_ = 0.0f;

            boolean wasPaused = CronusKeys.isPaused(ctx.data);
            float dmg = ctx.damage(1.45);
            AABB sweep = ctx.player.m_20191_().m_82377_(4.8, 2.2, 4.8);
            List<LivingEntity> victims = ctx.player.m_9236_().m_45976_(LivingEntity.class, sweep);

            List<LivingEntity> outExecutions = new ArrayList<>();
            for (LivingEntity v : victims) {
                if (v == ctx.player || !v.m_6084_()) {
                    continue;
                }
                if (wasPaused
                    && v.m_21223_() <= v.m_21233_() * 0.15f
                    && CronusPauseManager.isTargetFullFrozenBy(ctx.player, v)) {
                    outExecutions.add(v);
                } else {
                    TargetUtil.hurt(v, ModDamageTypes.technique(ctx.player), dmg);
                }
            }

            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                if (outExecutions.isEmpty()) {
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_FINISHER_FX, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            1.18f, CronusFxMsg.FINISHER_CRITICAL_CREWS_AID));
                }
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_RIDER_KICK, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_() + 0.1, ctx.player.m_20189_(), 4.8f, 1));
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        1.15f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
            }

            // 若在时停中释放旋转踢，强制触发 1.35x 倍率【Restart】爆发，并对 OUT 目标降下「绝版！」处决
            if (wasPaused) {
                CronusPauseManager.restart(ctx.player, ctx.data, 1.35, false);
                if (!outExecutions.isEmpty()) {
                    CronusSounds.playOutExecute(ctx.player);
                    if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                        CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            new CronusFxMsg(CronusFxMsg.KIND_FINISHER_FX, ctx.player.m_19879_(),
                                ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                                1.35f, CronusFxMsg.FINISHER_SHUT_DOWN));
                        CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            new CronusFxMsg(CronusFxMsg.KIND_OUT_BANNER, ctx.player.m_19879_(),
                                ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(), 1.0f, 0));
                    }
                    for (LivingEntity exec : outExecutions) {
                        if (exec.m_6084_()) {
                            float lethal = Math.max(dmg * 3.0f, exec.m_21233_() * 1.25f);
                            exec.f_19802_ = 0; // 清零 Restart 刚赋予的无敌帧，保证绝版处决全额生效
                            TargetUtil.hurt(exec, ModDamageTypes.technique(ctx.player), lethal);
                            CronusNet.popupOn(exec, CronusFxMsg.POPUP_CRITICAL);
                            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                                CronusNet.sendNear(sl, exec.m_20185_(), exec.m_20186_(), exec.m_20189_(),
                                    new CronusFxMsg(CronusFxMsg.KIND_ENERGY_BLAST, -1,
                                        exec.m_20185_(), exec.m_20186_() + exec.m_20206_() * 0.5, exec.m_20189_(),
                                        3.8f, 2));
                                CronusNet.sendNear(sl, exec.m_20185_(), exec.m_20186_(), exec.m_20189_(),
                                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                                        exec.m_20185_(), exec.m_20186_(), exec.m_20189_(),
                                        1.35f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));
                            }
                        }
                    }
                    DrawAttunementUnlock.grantQuirkXp(ctx.player, ctx.data,
                        14.0 * outExecutions.size(), "「绝版哒！」社长裁决");
                    Msg.chat(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.out_execute")
                        .m_130940_(ChatFormatting.GREEN));
                }
            }
            CronusKeys.addViralStrain(ctx.player, ctx.data, 10.0, ctx.now);
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    /**
     * 技能 6：Kimewaza Ⅰ · 编年史武装必杀Ⅰ（根据「光线枪/电锯」×「非时停/时停」派生全新实体必杀技）：
     * <ul>
     *   <li>光线枪 + 非时停：持续 3 秒递增绿色激光射线（起手低伤，无视无敌帧，随照射时间伤害越来越高）。</li>
     *   <li>光线枪 + 时停：一次性射出巨型贯穿绿色激光炮，造成大量伤害且完全无视护甲与盾牌。</li>
     *   <li>电锯（常态 & 时停）：斩出巨大绿色回旋刃形能量波，未命中持续飞出，命中后压制敌人高频旋转持续绞杀。</li>
     * </ul>
     */
    public static final class KimewazaSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (!ctx.consume(ctx.cost(10.5))) {
                return;
            }
            ctx.cooldown(200);
            int mode = CronusKeys.weaponMode(ctx.data);
            boolean paused = CronusKeys.isPaused(ctx.data);
            float baseDmg = ctx.damage(1.0);
            int finisherStyle = (mode == CronusKeys.MODE_GUN)
                ? (paused ? CronusFxMsg.FINISHER_CRITICAL_JUDGMENT_PAUSE : CronusFxMsg.FINISHER_CRITICAL_JUDGMENT)
                : CronusFxMsg.FINISHER_CRITICAL_SACRIFICE;

            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_FINISHER_FX, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        paused ? 1.25f : 1.05f, finisherStyle));
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        1.15f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
            }

            if (mode == CronusKeys.MODE_GUN && !paused) {
                // 光线枪非时停必杀技一：持续递增无视无敌帧绿色激光
                CronusSounds.playKimeCjBeam(ctx.player);
                CronusLaserBeamManager.startContinuousLaser(ctx.player, baseDmg);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime1_gun_beam")
                    .m_130940_(ChatFormatting.GREEN));
            } else if (mode == CronusKeys.MODE_GUN) {
                // 时停光线枪必杀技一：一次性巨额无视护甲绿色激光炮
                CronusSounds.playKimeCjPause(ctx.player);
                CronusLaserBeamManager.firePauseArmorPiercingLaser(ctx.player, baseDmg);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime1_gun_pause")
                    .m_130940_(ChatFormatting.GOLD));
            } else {
                // 电锯模式必杀技一：巨大绿色回旋刃能量波（未命中持续飞出，命中后压制高频旋转绞杀）
                CronusSounds.playKimeCsWave(ctx.player);
                ctx.player.m_6674_(InteractionHand.MAIN_HAND);
                CronusSawWaveEntity.launch(ctx.player, baseDmg, paused);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime1_saw_wave")
                    .m_130940_(ChatFormatting.GREEN));
            }
            CronusKeys.addViralStrain(ctx.player, ctx.data, 12.0, ctx.now);
        }
    }

    /**
     * 技能 7：Kimewaza Ⅱ · 编年史武装必杀Ⅱ（根据「光线枪/电锯」×「非时停/时停」派生强力实体与聚能终结技）：
     * <ul>
     *   <li>光线枪 + 非时停：发射 4 发巨大绿色能量弹丸，缓慢弧线追踪敌人并在命中时引发巨型爆炸与高额伤害。</li>
     *   <li>光线枪 + 时停：发射 4 发超频巨大绿色能量弹丸，高速强追踪敌人并引爆巨型爆炸与高额伤害。</li>
     *   <li>电锯（常态 & 时停）：将大量绿色高能聚集缠绕于电锯上，下一次攻击造成毁灭性巨额爆发伤害。</li>
     * </ul>
     */
    public static final class Kimewaza2Skill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            if (!ctx.consume(ctx.cost(11.5))) {
                return;
            }
            ctx.cooldown(220);
            int mode = CronusKeys.weaponMode(ctx.data);
            boolean paused = CronusKeys.isPaused(ctx.data);
            float baseDmg = ctx.damage(1.0);
            int finisherStyle = (mode == CronusKeys.MODE_GUN)
                ? (paused ? CronusFxMsg.FINISHER_CRITICAL_JUDGMENT_PAUSE : CronusFxMsg.FINISHER_CRITICAL_JUDGMENT)
                : CronusFxMsg.FINISHER_CRITICAL_SACRIFICE;

            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_FINISHER_FX, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        paused ? 1.25f : 1.10f, finisherStyle));
                CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, ctx.player.m_19879_(),
                        ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        1.20f, CronusFxMsg.SPRITE_LIGHTNING_ARC));
            }

            if (mode == CronusKeys.MODE_GUN && !paused) {
                // 光线枪非时停必杀技二：4 发巨大绿色缓慢追踪爆炸弹丸
                CronusSounds.playKimeCjOrbs(ctx.player);
                CronusEnergyOrbEntity.launchVolley(ctx.player, baseDmg * 0.72f, false);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime2_gun_orbs")
                    .m_130940_(ChatFormatting.GREEN));
            } else if (mode == CronusKeys.MODE_GUN) {
                // 时停光线枪必杀技二：4 发巨大绿色极速追踪爆炸弹丸
                CronusSounds.playKimeCjOrbs(ctx.player);
                CronusEnergyOrbEntity.launchVolley(ctx.player, baseDmg * 0.88f, true);
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime2_gun_pause_orbs")
                    .m_130940_(ChatFormatting.GOLD));
            } else {
                // 电锯模式必杀技二：将炽烈绿色高能聚集缠绕于电锯，下一次近战或弧斩引爆巨额伤害！
                CronusSounds.playKimeCsCharge(ctx.player);
                float bonusDmg = baseDmg * (paused ? 3.0f : 2.45f);
                int durationTicks = 240; // 12 秒充能窗口
                ctx.data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_UNTIL, (double) (ctx.now + durationTicks));
                ctx.data.setValue(CronusKeys.KEY_CHAINSAW_OVERCHARGE_DMG, (double) bonusDmg);
                SystemDataManager.markDirty((Player) ctx.player);
                if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_CHAINSAW_CHARGE, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                            (float) durationTicks, paused ? 1 : 0));
                    CronusNet.sendNear(sl, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
                        new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                            ctx.player.m_20185_(), ctx.player.m_20186_() + 0.08, ctx.player.m_20189_(),
                            3.2f, 0));
                }
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.kime2_saw_charge")
                    .m_130940_(ChatFormatting.GREEN));
            }
            CronusKeys.addViralStrain(ctx.player, ctx.data, 13.0, ctx.now);
        }
    }

    /** 技能 7：Reset · 时间回溯（将自身生命值、空间坐标与负面异常回溯至 4 秒前，累积较高病毒过载）。 */
    public static final class ResetSkill implements Skill {
        @Override
        public void onPress(SkillContext ctx, HeldState held) {
            if (!CronusKeys.requireTransformed(ctx.player, ctx.data)
                || !checkDriverReady(ctx.player, ctx.data, ctx.now)) {
                return;
            }
            CronusCombatAndPassives.RewindSnapshot snap = CronusCombatAndPassives.findRewindSnapshot(ctx.player, ctx.now);
            if (snap == null) {
                Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.reset_empty")
                    .m_130940_(ChatFormatting.YELLOW));
                return;
            }
            if (!ctx.consume(ctx.cost(12.0))) {
                return;
            }
            ctx.cooldown(360);

            // 还原空间坐标与朝向，生命值取「当前值」与「4秒前快照」中的较高者，净化负面状态
            ctx.player.m_146922_(snap.yRot);
            ctx.player.m_146926_(snap.xRot);
            ctx.player.m_6021_(snap.x, snap.y, snap.z);
            ctx.player.m_20256_(Vec3.f_82478_);
            ctx.player.f_19789_ = 0.0f;
            ctx.player.f_19864_ = true;
            ctx.player.m_20095_(); // clearFire

            double maxSysHp = CronusKeys.maxStat(ctx.data, StatType.HP);
            ctx.data.setHp(Math.min(maxSysHp, Math.max(ctx.data.hp(), snap.sysHp)));
            ctx.player.m_21153_(Math.min(ctx.player.m_21233_(), Math.max(ctx.player.m_21223_(), snap.vanillaHp)));

            ctx.player.m_21195_(MobEffects.f_19614_); // POISON
            ctx.player.m_21195_(MobEffects.f_19615_); // WITHER
            ctx.player.m_21195_(MobEffects.f_19597_); // SLOWNESS
            ctx.player.m_21195_(MobEffects.f_19613_); // WEAKNESS
            ctx.player.m_21195_(MobEffects.f_19610_); // BLINDNESS
            ctx.player.m_21195_(MobEffects.f_216964_); // DARKNESS

            CronusSounds.playReset(ctx.player);
            if (ctx.player.m_9236_() instanceof ServerLevel sl) {
                CronusNet.sendNear(sl, snap.x, snap.y, snap.z,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, ctx.player.m_19879_(),
                        snap.x, snap.y + 0.05, snap.z, 4.5f, 2));
                CronusNet.sendNear(sl, snap.x, snap.y, snap.z,
                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, ctx.player.m_19879_(),
                        snap.x, snap.y, snap.z, 1.15f, CronusFxMsg.SPRITE_CLOCK_REWIND));
                CronusNet.sendNear(sl, snap.x, snap.y, snap.z,
                    new CronusFxMsg(CronusFxMsg.KIND_SPRITE_FX, -1,
                        snap.x, snap.y, snap.z, 1.1f, CronusFxMsg.SPRITE_LIGHTNING_BOLT));
            }
            Msg.actionBar(ctx.player, Component.m_237115_("message.rpgtemplate.cronus.reset_done")
                .m_130940_(ChatFormatting.GOLD));
            CronusKeys.addViralStrain(ctx.player, ctx.data, 24.0, ctx.now);
            SystemDataManager.markDirty((Player) ctx.player);
        }
    }

    private static List<LivingEntity> coneTargets(ServerPlayer player, double range, double minDot) {
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20154_().m_82541_();
        AABB box = player.m_20191_().m_82377_(range, range * 0.6, range);
        List<LivingEntity> out = new ArrayList<>();
        double rSq = range * range;
        for (LivingEntity e : player.m_9236_().m_45976_(LivingEntity.class, box)) {
            if (e == player || !e.m_6084_() || e.m_20280_(player) > rSq) {
                continue;
            }
            Vec3 dir = e.m_20191_().m_82399_().m_82546_(eye).m_82541_();
            if (dir.m_82526_(look) >= minDot) {
                out.add(e);
            }
        }
        return out;
    }
}
