package com.rpgtemplate.cronus;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 假面骑士 Cronus（{@code rpgtemplate:cronus_1}）的核心状态键与通用结算工具。
 */
public final class CronusKeys {
    public static final String TEMPLATE_ID = "rpgtemplate:cronus_1";

    /** 1.0 = 编年史装甲变身态；0.0 = 檀正宗人形态。 */
    public static final String KEY_TRANSFORMED = "cr.transformed";
    /** 变身开始或上次叠加时间防御的 tick。 */
    public static final String KEY_TRANSFORM_SINCE = "cr.transform_since";
    /** 故障驱动器 II 武装形态：0.0 = 链锯模式（Zwei Chainsaw），1.0 = 光束枪模式（Zwei Gun）。 */
    public static final String KEY_WEAPON_MODE = "cr.weapon_mode";

    /** 1.0 = 当前正在维持【Pause】时间暂停领域。 */
    public static final String KEY_PAUSED = "cr.paused";
    /** 当前【Pause】中累计待引爆的悬停总伤害（供左上角 HUD 实时显示）。 */
    public static final String KEY_PENDING_DMG = "cr.pending_dmg";
    /** 当前【Pause】预计剩余可维持秒数（供左上角 HUD 实时显示）。 */
    public static final String KEY_PAUSE_LEFT_SEC = "cr.pause_sec";

    /** Bugster 病毒抗体过载负荷（0 ~ 100）。 */
    public static final String KEY_VIRAL = "cr.viral";
    /** 驱动器过热锁死截止 tick（期间无法开启时停或变身）。 */
    public static final String KEY_OVERHEAT_UNTIL = "cr.overheat_until";

    /** 被动【伤害拳套强化】：当前层数与到期 tick。 */
    public static final String KEY_GAUNTLET_STACKS = "cr.gauntlet";
    public static final String KEY_GAUNTLET_UNTIL = "cr.gauntlet_until";

    /** 被动【时间防御系统】：变身后随时间递增的额外防御百分比（0.0 ~ 0.45）。 */
    public static final String KEY_TIME_DEF = "cr.time_def";

    /** 被动【减伤系统（交锋拼刀）】：最近一次命中敌人的 tick。 */
    public static final String KEY_LAST_HIT_OUT = "cr.last_hit_out";

    /** 主动【三段跳滞空】：服务端零重力悬停截止 tick。 */
    public static final String KEY_HOVER_UNTIL = "cr.hover_until";

    /** 旋转回旋踢（Critical Crews-Aid 长按）击败敌人 1.5x XP 倍率窗口截止 tick。 */
    public static final String KEY_SPIN_XP_UNTIL = "cr.spin_xp_until";

    /** 能量道具（Energy Item）增益截止 tick。 */
    public static final String KEY_ITEM_SPEED_UNTIL = "cr.item_speed";
    public static final String KEY_ITEM_MUSCLE_UNTIL = "cr.item_muscle";
    public static final String KEY_ITEM_IRON_UNTIL = "cr.item_iron";
    public static final String KEY_ITEM_JUMP_UNTIL = "cr.item_jump";

    public static final int MODE_CHAINSAW = 0;
    public static final int MODE_GUN = 1;

    private CronusKeys() {
    }

    /** 仅当玩家已激活系统且当前加载模板为 {@code rpgtemplate:cronus_1} 时返回数据。 */
    public static PlayerSystemData activeData(ServerPlayer player) {
        if (player == null || !SystemDataManager.available(player)) {
            return null;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        return data.isActivated() && TEMPLATE_ID.equals(data.loadedTemplateId()) ? data : null;
    }

    public static boolean isTransformed(PlayerSystemData data) {
        return data != null && data.value(KEY_TRANSFORMED, 0.0) > 0.5;
    }

    public static boolean isPaused(PlayerSystemData data) {
        return data != null && data.value(KEY_PAUSED, 0.0) > 0.5;
    }

    public static int weaponMode(PlayerSystemData data) {
        return data != null && data.value(KEY_WEAPON_MODE, 0.0) >= 0.5 ? MODE_GUN : MODE_CHAINSAW;
    }

    public static double maxStat(PlayerSystemData data, StatType stat) {
        return StatCalculator.maxStat(stat, data, TemplateRegistry.get(), CoreConfig.get());
    }

    /** 校验是否处于编年史变身态，未变身时在动作栏提示玩家先变身。 */
    public static boolean requireTransformed(ServerPlayer player, PlayerSystemData data) {
        if (isTransformed(data)) {
            return true;
        }
        Msg.actionBar(player, Component.m_237115_("message.rpgtemplate.cronus.need_henshin")
            .m_130940_(ChatFormatting.GREEN));
        return false;
    }

    /** 当前【伤害拳套强化】+【能量道具：肌肉强化】的总输出乘区。 */
    public static double attackAmplifier(PlayerSystemData data, long now) {
        if (data == null) {
            return 1.0;
        }
        double stacks = data.value(KEY_GAUNTLET_UNTIL, 0.0) > (double) now
            ? data.value(KEY_GAUNTLET_STACKS, 0.0) : 0.0;
        double mult = 1.0 + stacks * 0.10;
        if (data.value(KEY_ITEM_MUSCLE_UNTIL, 0.0) > (double) now) {
            mult *= 1.30;
        }
        return mult;
    }

    /** 增加或平复 Bugster 病毒抗体负荷；满 100% 时触发驱动器过热反噬并强制解除时停/变身。 */
    public static void addViralStrain(ServerPlayer player, PlayerSystemData data, double delta, long now) {
        if (player == null || data == null) {
            return;
        }
        double next = Math.max(0.0, Math.min(100.0, data.value(KEY_VIRAL, 0.0) + delta));
        data.setValue(KEY_VIRAL, next);
        if (next >= 100.0) {
            data.setValue(KEY_VIRAL, 55.0);
            data.setValue(KEY_OVERHEAT_UNTIL, (double) (now + 120L)); // 6 秒驱动器冷却锁
            if (isPaused(data)) {
                CronusPauseManager.restart(player, data, 1.0, false);
            }
            data.setValue(KEY_TRANSFORMED, 0.0);
            data.setValue(KEY_TIME_DEF, 0.0);
            double curHp = data.hp();
            if (curHp > 2.0) {
                data.setHp(Math.max(1.0, curHp * 0.92));
            }
            player.m_7292_(new MobEffectInstance(MobEffects.f_19597_, 60, 1, false, false, true));
            player.m_7292_(new MobEffectInstance(MobEffects.f_19604_, 60, 0, false, false, true));
            Msg.chat(player, Component.m_237115_("message.rpgtemplate.cronus.viral_overload")
                .m_130940_(ChatFormatting.RED));
        }
        SystemDataManager.markDirty((Player) player);
    }
}
