package com.rpgtemplate.cronus;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
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
    /** Gashacon 缺陷护罩Ⅱ 武装形态：0.0 = 电锯模式（Chainsaw），1.0 = 光线枪模式（Gun）。 */
    public static final String KEY_WEAPON_MODE = "cr.weapon_mode";
    /** 1.0 = 已由 Cronus 变身施加常驻夜视/水下呼吸增益（解除变身时自动回收）。 */
    public static final String KEY_BUFFS_APPLIED = "cr.buffs_applied";
    /** 1.0 = 通过腰间装备【故障驱动器Ⅱ】+ 右键卡带变身（兼容字段，解除仅限技能）。 */
    public static final String KEY_HENSHIN_VIA_DRIVER = "cr.henshin_via_driver";
    /** 变身动画结束、3D 假面骑士 Cronus 装甲显现的世界 tick（此前保持人形态 + 3D 腰带）。 */
    public static final String KEY_SUIT_REVEAL_TICK = "cr.suit_reveal_tick";
    /** 技能变身中 2D 飞入卡带精准插入 3D 腰带槽位并点亮实体卡带的世界 tick。 */
    public static final String KEY_BELT_GASHAT_TICK = "cr.belt_gashat_tick";
    /** 1.0 = 本次变身由右键消耗【假面骑士编年史转玩卡带】触发，解除变身时自动归还卡带。 */
    public static final String KEY_GASHAT_IN_HENSHIN = "cr.gashat_in_henshin";
    /** 本次右键变身所收纳卡带的唯一令牌（用于解除变身时原样归还且杜绝面板重复补发）。 */
    public static final String KEY_GASHAT_HELD_TOKEN = "cr.gashat_held_token";

    /** 变身音效先导时长：播放音效 6 秒（120 ticks）后再正式执行变身与演出。 */
    public static final int HENSHIN_AUDIO_LEAD_TICKS = 120;
    /** 技能 1 完整变身演出：卡带飞入锁定腰带（tFrame=126 ≈ 83 ticks）与落雷收束装甲显现（150 ticks）。 */
    public static final int HENSHIN_FULL_GASHAT_DOCK_TICKS = 83;
    public static final int HENSHIN_FULL_REVEAL_TICKS = 150;
    /** 右键卡带快捷变身演出：落雷收束装甲显现（39 ticks ≈ 1.95s）。 */
    public static final int HENSHIN_FAST_REVEAL_TICKS = 39;

    /** 面板「物品」界面专属装备收录与唯一令牌状态（获得 Cronus 模板后自动解锁）。 */
    public static final String KEY_GEAR_UNLOCKED = "cr.gear_unlocked";
    public static final String KEY_DRIVER_SEQ = "cr.driver_seq";
    public static final String KEY_DRIVER_TOKEN = "cr.driver_token";
    public static final String KEY_DRIVER_STATE = "cr.driver_state";
    public static final String KEY_GASHAT_SEQ = "cr.gashat_seq";
    public static final String KEY_GASHAT_TOKEN = "cr.gashat_token";
    public static final String KEY_GASHAT_STATE = "cr.gashat_state";

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

    /** 电锯模式必杀技二：将绿色能量聚集于电锯，下一次攻击造成毁灭性巨额伤害（截止 tick 与附伤值）。 */
    public static final String KEY_CHAINSAW_OVERCHARGE_UNTIL = "cr.saw_charge_until";
    public static final String KEY_CHAINSAW_OVERCHARGE_DMG = "cr.saw_charge_dmg";

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

    /** 玩家是否已激活系统并拥有假面骑士 Cronus 模板（无论当前是否加载）。 */
    public static boolean ownsCronusTemplate(PlayerSystemData data) {
        return data != null && data.isActivated() && data.findTemplate(TEMPLATE_ID).isPresent();
    }

    /**
     * 计算 Gashacon 缺陷护罩Ⅱ 的动态攻击加成：取 Cronus 技能基础攻击力（系数 3.2，等同 Critical Crews-Aid 基础技能倍率）的精确三分之一。
     * 会随玩家等级、模板等级与技能伤害换算配置实时成长。
     */
    public static float bugvisorAttackBonus(PlayerSystemData data) {
        if (data == null || !data.isActivated()) {
            return 0.0f;
        }
        int tplLevel = data.findTemplate(TEMPLATE_ID).map(TemplateInstance::level).orElse(1);
        double mpMax = maxStat(data, StatType.MP);
        double techMult = 1.0 + TraitLookup.techniqueDamageBonus(data);
        double conv = ModConfigs.SERVER.skillDamageConversion.get();
        double skillDmg = SkillFormula.attackPower(3.2, mpMax, Math.max(1, tplLevel), techMult, conv);
        return (float) Math.max(2.0, skillDmg / 3.0);
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
            mult *= 1.50;
        }
        return mult;
    }

    /**
     * 玩家解除变身时：清理常驻增益、腰带卡带指示灯、3D 装甲同步、召唤武装，
     * 并在本次变身为右键【假面骑士编年史转玩卡带】触发时原样归还卡带至背包。
     *
     * @return 是否归还了之前因右键变身收纳的编年史转玩卡带
     */
    public static boolean clearTransformedState(ServerPlayer player, PlayerSystemData data) {
        boolean shouldReturnGashat = data != null && data.value(KEY_GASHAT_IN_HENSHIN, 0.0) > 0.5;
        long heldToken = data != null ? (long) data.value(KEY_GASHAT_HELD_TOKEN, 0.0) : 0L;

        if (data != null) {
            data.setValue(KEY_TRANSFORMED, 0.0);
            data.setValue(KEY_TIME_DEF, 0.0);
            data.setValue(KEY_HENSHIN_VIA_DRIVER, 0.0);
            data.setValue(KEY_SUIT_REVEAL_TICK, 0.0);
            data.setValue(KEY_BELT_GASHAT_TICK, 0.0);
            data.setValue(KEY_GASHAT_IN_HENSHIN, 0.0);
            data.setValue(KEY_GASHAT_HELD_TOKEN, 0.0);
        }
        if (player != null && data != null && data.value(KEY_BUFFS_APPLIED, 0.0) > 0.5) {
            player.m_21195_(MobEffects.f_19611_); // NIGHT_VISION
            player.m_21195_(MobEffects.f_19608_); // WATER_BREATHING
            data.setValue(KEY_BUFFS_APPLIED, 0.0);
        }
        boolean returnedGashat = false;
        if (player != null) {
            CronusGearManager.clearAllDriverLoadedFlags(player);
            if (shouldReturnGashat) {
                CronusGearManager.returnConsumedGashat(player, data, heldToken);
                returnedGashat = true;
            }
            CronusGearManager.retractSummonedBugvisor(player);
            CronusNet.syncSuitState(player, false, 0, 0);
        }
        return returnedGashat;
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
            clearTransformedState(player, data);
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
