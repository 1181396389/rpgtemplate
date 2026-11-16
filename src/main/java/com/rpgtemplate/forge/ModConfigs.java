package com.rpgtemplate.forge;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.config.GrowthMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import org.apache.commons.lang3.tuple.Pair;

public final class ModConfigs {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigs.Server SERVER;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigs.Client CLIENT;

    private ModConfigs() {
    }

    static void onLoading(Loading var0) {
        if (var0.getConfig().getSpec() == SERVER_SPEC) {
            applyServer();
        }
    }

    static void onReloading(Reloading var0) {
        if (var0.getConfig().getSpec() == SERVER_SPEC) {
            applyServer();
        }
    }

    public static Map<String, Integer> bossRuleLevelMap() {
        HashMap var0 = new HashMap();

        for (String var2 : SERVER.bossRuleLevels.get()) {
            int var3 = var2.lastIndexOf(61);
            if (var3 > 0) {
                try {
                    var0.put(var2.substring(0, var3).trim(), Integer.parseInt(var2.substring(var3 + 1).trim()));
                } catch (NumberFormatException var5) {
                }
            }
        }

        return var0;
    }

    private static void applyServer() {
        CoreConfig.set(
            CoreConfig.builder()
                .growthMode((GrowthMode)SERVER.growthMode.get())
                .exponentialBase((Double)SERVER.exponentialBase.get())
                .linearXpBase((Double)SERVER.linearXpBase.get())
                .linearXpPower((Double)SERVER.linearXpPower.get())
                .expXpBase((Double)SERVER.expXpBase.get())
                .expXpGrowth((Double)SERVER.expXpGrowth.get())
                .templateXpBase((Double)SERVER.templateXpBase.get())
                .templateXpPower((Double)SERVER.templateXpPower.get())
                .templateLevelBonusPerLevel((Double)SERVER.templateLevelBonus.get())
                .collectionBonusRatio((Double)SERVER.collectionBonusRatio.get())
                .emptyPoolXpRatio((Double)SERVER.emptyPoolXpRatio.get())
                .build()
        );
        RpgTemplateMod.LOGGER.info("[RPG转生模板] 服务端配置已应用，成长模式 = {}", SERVER.growthMode.get());
    }

    static {
        Pair var0 = new Builder().configure(ModConfigs.Server::new);
        SERVER = (ModConfigs.Server)var0.getLeft();
        SERVER_SPEC = (ModConfigSpec)var0.getRight();
        Pair var1 = new Builder().configure(ModConfigs.Client::new);
        CLIENT = (ModConfigs.Client)var1.getLeft();
        CLIENT_SPEC = (ModConfigSpec)var1.getRight();
    }

    public static final class Client {
        public final DoubleValue hudFlicker;
        public final DoubleValue hudOpacity;
        public final DoubleValue hudScale;
        public final IntValue hudX;
        public final IntValue hudY;
        public final BooleanValue rainbowAura;
        public final BooleanValue chantSubtitles;
        public final BooleanValue xpPopups;
        public final BooleanValue hudGlitch;
        public final BooleanValue hudEnabled;
        public final BooleanValue skillPageAutoReturn;

        Client(Builder var1) {
            var1.comment("系统面板 HUD").push("hud");
            this.hudEnabled = var1.comment("显示左上角的系统面板").define("enabled", true);
            this.hudFlicker = var1.comment("面板高频闪烁强度（0 = 关闭，对闪烁敏感请设为 0）").defineInRange("flicker", 0.35, 0.0, 1.0);
            this.hudOpacity = var1.comment("面板背景不透明度").defineInRange("opacity", 0.55, 0.0, 1.0);
            this.hudScale = var1.comment("面板缩放").defineInRange("scale", 1.0, 0.5, 3.0);
            this.hudX = var1.defineInRange("x", 4, 0, 10000);
            this.hudY = var1.defineInRange("y", 4, 0, 10000);
            var1.pop();
            var1.push("effects");
            this.rainbowAura = var1.comment("获得新模板时的全光谱虹光（仅自己可见）").define("rainbow_aura", true);
            this.chantSubtitles = var1.comment("在屏幕中央显示咒词字幕").define("chant_subtitles", true);
            this.xpPopups = var1.comment("显示 +XP 浮动提示").define("xp_popups", true);
            this.hudGlitch = var1.comment("面板偶发的故障（错位）效果").define("hud_glitch", true);
            var1.pop();
            var1.comment("战斗输入 / Combat input").push("input");
            this.skillPageAutoReturn = var1.comment(
                    new String[]{"旧版兼容字段，已禁用自动返回。释放技能后保留当前页，需手动翻页。", "Legacy compatibility flag; ignored. Skill pages change only through manual page input."}
                )
                .define("skill_page_auto_return", false);
            var1.pop();
        }
    }

    public static final class Server {
        public final EnumValue<GrowthMode> growthMode;
        public final DoubleValue exponentialBase;
        public final DoubleValue linearXpBase;
        public final DoubleValue linearXpPower;
        public final DoubleValue expXpBase;
        public final DoubleValue expXpGrowth;
        public final DoubleValue templateXpBase;
        public final DoubleValue templateXpPower;
        public final DoubleValue templateLevelBonus;
        public final DoubleValue collectionBonusRatio;
        public final DoubleValue emptyPoolXpRatio;
        public final BooleanValue protectBoundTally;
        public final DoubleValue damageConversion;
        public final DoubleValue skillDamageConversion;
        public final DoubleValue skillHealConversion;
        public final BooleanValue terrainDestruction;
        public final BooleanValue terrainRespectMobGriefing;
        public final BooleanValue terrainDropBlocks;
        public final BooleanValue terrainBreakBlockEntities;
        public final BooleanValue terrainDirectRemoval;
        public final DoubleValue terrainBaseHardness;
        public final DoubleValue terrainMaxHardness;
        public final IntValue terrainMaxBlocksPerSlash;
        public final IntValue terrainFrameChecks;
        public final IntValue terrainFrameBlocks;
        public final DoubleValue terrainFrameMillis;
        public final ConfigValue<List<? extends String>> bossRuleLevels;
        public final DoubleValue hpRegen;
        public final DoubleValue mpRegen;
        public final DoubleValue spRegen;
        public final IntValue hpRegenDelay;
        public final IntValue spRegenDelay;
        public final DoubleValue sprintSpCost;
        public final DoubleValue exhaustionRecover;
        public final DoubleValue xpKill;
        public final DoubleValue xpBossMultiplier;
        public final DoubleValue xpSpawnerMultiplier;
        public final DoubleValue xpEat;
        public final DoubleValue xpEatStreakDecay;
        public final DoubleValue xpMine;
        public final DoubleValue xpOreMultiplier;
        public final DoubleValue xpCraft;
        public final DoubleValue xpSmelt;
        public final DoubleValue xpTrade;
        public final DoubleValue xpBrew;
        public final DoubleValue xpAdvancement;
        public final DoubleValue xpNewChunk;
        public final DoubleValue xpNewDimension;
        public final DoubleValue xpRepeatDecay;
        public final IntValue xpRepeatWindow;

        Server(Builder var1) {
            var1.comment("属性成长 / Stat growth").push("growth");
            this.growthMode = var1.comment(new String[]{"LINEAR = 线性：属性 = 基础值 × (1 + 等级)，适合低数值整合包", "EXPONENTIAL = 指数：属性 = 基础值 × 底数^等级，适合高数值整合包"})
                .defineEnum("mode", GrowthMode.LINEAR);
            this.exponentialBase = var1.comment("指数模式的底数（2.0 = 每级翻倍）").defineInRange("exponential_base", 2.0, 1.0001, 1000.0);
            var1.pop();
            var1.comment("升级经验曲线 / Level curve").push("leveling");
            this.linearXpBase = var1.comment("线性模式：升级需求 = base × (等级+1)^power").defineInRange("linear_xp_base", 100.0, 1.0, 1.0E12);
            this.linearXpPower = var1.defineInRange("linear_xp_power", 1.5, 0.0, 10.0);
            this.expXpBase = var1.comment("指数模式：升级需求 = base × growth^等级").defineInRange("exp_xp_base", 100.0, 1.0, 1.0E12);
            this.expXpGrowth = var1.defineInRange("exp_xp_growth", 1.6, 1.0001, 100.0);
            var1.pop();
            var1.comment("模板 / Templates").push("templates");
            this.templateXpBase = var1.comment("模板经验需求 = base × 模板等级^power × 品质系数").defineInRange("template_xp_base", 50.0, 1.0, 1.0E12);
            this.templateXpPower = var1.defineInRange("template_xp_power", 1.4, 0.0, 10.0);
            this.templateLevelBonus = var1.comment("模板每升 1 级，其属性加成提升的比例").defineInRange("template_level_bonus", 0.05, 0.0, 10.0);
            this.collectionBonusRatio = var1.comment("库存中未载入模板提供的收藏加成比例（品质越高越多），0 = 关闭").defineInRange("collection_bonus_ratio", 0.1, 0.0, 10.0);
            this.emptyPoolXpRatio = var1.comment("角色池抽空时，一次抽取转化为 当前升级需求 × 该比例 的经验").defineInRange("empty_pool_xp_ratio", 0.5, 0.0, 100.0);
            var1.pop();
            var1.comment("虎符 / Tally").push("tally");
            this.protectBoundTally = var1.comment("已认主的虎符掉落物不会消失，且免疫火焰与爆炸").define("protect_bound_tally", true);
            var1.pop();
            var1.comment("战斗 / Combat").push("combat");
            this.damageConversion = var1.comment("受到 1 点原版伤害 = 扣除多少系统 HP（系统 HP 先于原版生命吸收伤害）").defineInRange("damage_conversion", 1.0, 1.0E-4, 1.0E9);
            this.skillDamageConversion = var1.comment("技能威力 → 原版伤害的倍率（威力 = 系数 × 属性上限 × 0.1 × 模板等级修正）")
                .defineInRange("skill_damage_conversion", 4.0, 0.0, 1.0E9);
            this.skillHealConversion = var1.comment("治疗类技能威力 → 系统 HP 的倍率").defineInRange("skill_heal_conversion", 1.0, 0.0, 1.0E9);
            this.bossRuleLevels = var1.comment("机制判定中特定生物的规则等级（未列出的按 log2(最大生命) 计算）")
                .defineListAllowEmpty(
                    List.of("boss_rule_levels"), () -> List.of("minecraft:ender_dragon=30", "minecraft:wither=25", "minecraft:elder_guardian=20"), var0 -> {
                        if (var0 instanceof String var1x && var1x.contains("=")) {
                            return true;
                        }

                        return false;
                    }
                );
            var1.pop();
            var1.comment("地形破坏 / Terrain destruction（宿傩的斩击会切开方块）").push("terrain");
            this.terrainDestruction = var1.comment("总开关：允许技能破坏地形（玩家还可以在面板 → 设置里单独关闭自己的）").define("enabled", true);
            this.terrainRespectMobGriefing = var1.comment("遵守 mobGriefing 游戏规则（为 false 时不破坏）").define("respect_mob_griefing", false);
            this.terrainDropBlocks = var1.comment("被切开的方块是否掉落物品（大量掉落物可能造成卡顿）").define("drop_blocks", false);
            this.terrainBreakBlockEntities = var1.comment("是否切开带方块实体的方块（箱子、熔炉等，内容物会掉落）").define("break_block_entities", false);
            this.terrainDirectRemoval = var1.comment("直接移除方块：不逐块触发破坏事件、不掉落、不播放破坏粒子，只做客户端方块更新（大规模切割时显著减少卡顿；关闭则走完整破坏流程）。带方块实体的方块仍走完整流程以保留内容物")
                .define("direct_removal", true);
            this.terrainBaseHardness = var1.comment("可切开的基础硬度（石头 1.5、深板岩 3、铁块 5、黑曜石 50），实际 = 基础 × (1 + 技能伤害 / 20)")
                .defineInRange("base_hardness", 3.0, 0.0, 1000000.0);
            this.terrainMaxHardness = var1.comment("可切开硬度的上限（无论多强都切不开更硬的方块；基岩等不可破坏方块永远不会被切开）").defineInRange("max_hardness", 100.0, 0.0, 1000000.0);
            this.terrainMaxBlocksPerSlash = var1.comment("每道斩击最多切开的方块数").defineInRange("max_blocks_per_slash", 8192, 0, 1048576);
            this.terrainFrameChecks = var1.comment("所有斩击/领域共享的每 tick 方块检查上限").defineInRange("frame_checks", 16384, 256, 131072);
            this.terrainFrameBlocks = var1.comment("所有斩击/领域共享的每 tick 成功移除方块上限").defineInRange("frame_blocks", 1024, 16, 8192);
            this.terrainFrameMillis = var1.comment("共享切割操作软耗时预算（毫秒），不含后续光照和客户端重建").defineInRange("frame_millis", 4.0, 0.25, 20.0);
            var1.pop();
            var1.comment("系统属性恢复 / Regeneration（数值为每秒恢复上限的比例）").push("regen");
            this.hpRegen = var1.comment("系统 HP 每秒恢复比例（脱战后）").defineInRange("hp_per_second", 0.02, 0.0, 1.0);
            this.mpRegen = var1.comment("MP 每秒恢复比例").defineInRange("mp_per_second", 0.03, 0.0, 1.0);
            this.spRegen = var1.comment("SP 每秒恢复比例（停止疾跑后）").defineInRange("sp_per_second", 0.08, 0.0, 1.0);
            this.hpRegenDelay = var1.comment("受伤后多少 tick 开始恢复系统 HP").defineInRange("hp_regen_delay_ticks", 100, 0, 72000);
            this.spRegenDelay = var1.comment("停止消耗 SP 后多少 tick 开始恢复").defineInRange("sp_regen_delay_ticks", 20, 0, 72000);
            this.sprintSpCost = var1.comment("疾跑每秒消耗 SP 上限的比例（0 = 疾跑不消耗）").defineInRange("sprint_sp_cost_per_second", 0.01, 0.0, 1.0);
            this.exhaustionRecover = var1.comment("SP 耗尽后进入力竭（缓慢 II），恢复到该比例才解除").defineInRange("exhaustion_recover_ratio", 0.25, 0.0, 1.0);
            var1.pop();
            var1.comment("经验来源 / XP sources（全部为倍率，0 = 关闭该来源）").push("xp");
            this.xpKill = var1.comment("击杀：目标最大生命 × 该倍率").defineInRange("kill", 1.0, 0.0, 1.0E9);
            this.xpBossMultiplier = var1.comment("Boss（#c:bosses）额外倍率").defineInRange("boss_multiplier", 5.0, 0.0, 1.0E9);
            this.xpSpawnerMultiplier = var1.comment("刷怪笼生成的生物倍率（防刷）").defineInRange("spawner_multiplier", 0.2, 0.0, 1.0E9);
            this.xpEat = var1.comment("进食：(饥饿值×2 + 饱和度) × 该倍率").defineInRange("eat", 1.0, 0.0, 1.0E9);
            this.xpEatStreakDecay = var1.comment("连续吃同一种食物的递减系数").defineInRange("eat_streak_decay", 0.7, 0.0, 1.0);
            this.xpMine = var1.comment("挖掘：方块硬度 × 0.5 × 该倍率（自己放置的方块不给经验）").defineInRange("mine", 1.0, 0.0, 1.0E9);
            this.xpOreMultiplier = var1.comment("矿石（#c:ores）额外倍率").defineInRange("ore_multiplier", 5.0, 0.0, 1.0E9);
            this.xpCraft = var1.comment("合成：每件物品").defineInRange("craft", 1.0, 0.0, 1.0E9);
            this.xpSmelt = var1.comment("冶炼：每件物品").defineInRange("smelt", 0.5, 0.0, 1.0E9);
            this.xpTrade = var1.comment("交易：每次").defineInRange("trade", 3.0, 0.0, 1.0E9);
            this.xpBrew = var1.comment("酿造：每次取出").defineInRange("brew", 2.0, 0.0, 1.0E9);
            this.xpAdvancement = var1.comment("获得进度（只计算有显示的进度）").defineInRange("advancement", 20.0, 0.0, 1.0E9);
            this.xpNewChunk = var1.comment("探索：第一次进入一个区块").defineInRange("new_chunk", 1.0, 0.0, 1.0E9);
            this.xpNewDimension = var1.comment("探索：第一次进入一个维度").defineInRange("new_dimension", 50.0, 0.0, 1.0E9);
            this.xpRepeatDecay = var1.comment("合成 / 冶炼 / 交易 / 酿造重复同一物品的递减系数").defineInRange("repeat_decay", 0.85, 0.0, 1.0);
            this.xpRepeatWindow = var1.comment("递减的记忆窗口（tick），超过后重置").defineInRange("repeat_window_ticks", 1200, 1, 1728000);
            var1.pop();
        }
    }
}
