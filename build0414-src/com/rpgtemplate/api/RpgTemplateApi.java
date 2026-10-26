package com.rpgtemplate.api;

import com.rpgtemplate.core.production.BuildCatalog;
import com.rpgtemplate.core.production.BuildMotion;
import com.rpgtemplate.core.production.BuildRules;
import com.rpgtemplate.core.production.CanonicalCatalog;
import com.rpgtemplate.core.production.CaptureRules;
import com.rpgtemplate.core.production.Catalog;
import com.rpgtemplate.core.production.CraftMath;
import com.rpgtemplate.core.production.PersonalRules;
import com.rpgtemplate.core.template.Quality;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * rpgtemplate 公开 Java API（门面类）。
 *
 * <p>设计约定：
 * <ul>
 *   <li>只暴露纯逻辑：查询、规则计算、动作曲线采样。不依赖 Minecraft 运行时状态，也不修改任何存档数据。</li>
 *   <li>返回的集合均为不可变副本，调用方修改不会影响模组内部状态。</li>
 *   <li>输入非法时：查询类方法返回 {@link Optional#empty()}；计算类方法沿用内部规则的边界钳制，不抛异常。</li>
 * </ul>
 *
 * <p>本类不包含 {@code CraftMath.enhanceStat}，因为它依赖玩家存档数据（PlayerSystemData），
 * 不属于纯逻辑范围。
 */
public final class RpgTemplateApi {

    /** API 版本号。接口发生不兼容变更时递增。 */
    public static final int API_VERSION = 1;

    private RpgTemplateApi() {
    }

    // ------------------------------------------------------------------
    // 品质（Quality）
    // ------------------------------------------------------------------

    /** 所有品质，按定义顺序（BASE → MR）。 */
    public static List<Quality> qualities() {
        return List.of(Quality.values());
    }

    /**
     * 按名称解析品质，接受大小写不敏感的名称以及 "EX+" 写法。
     *
     * @return 解析失败（包括 null、空串、未知名称）时返回 empty
     */
    public static Optional<Quality> findQuality(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Quality.parse(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // 制作配方（Catalog）
    // ------------------------------------------------------------------

    /** 全部制作配方。 */
    public static List<Catalog.Recipe> recipes() {
        return Catalog.ALL;
    }

    /** 按配方 ID 查询。 */
    public static Optional<Catalog.Recipe> recipe(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(Catalog.BY_ID.get(id));
    }

    /** 按角色（build / smith / 其他药理角色）筛选配方。 */
    public static List<Catalog.Recipe> recipesByRole(String role) {
        if (role == null) {
            return List.of();
        }
        return Catalog.ALL.stream().filter(r -> role.equals(r.role())).toList();
    }

    /** 按流派（tech / magic / odd / organic / inorganic / medical / enhancer / fabricator / bio / matter）筛选配方。 */
    public static List<Catalog.Recipe> recipesBySchool(String school) {
        if (school == null) {
            return List.of();
        }
        return Catalog.ALL.stream().filter(r -> school.equals(r.school())).toList();
    }

    /** 角色 ID 对应的显示名称。 */
    public static String roleName(String role) {
        return Catalog.roleName(role);
    }

    /** 流派 ID 对应的显示名称。 */
    public static String schoolName(String school) {
        return Catalog.schoolName(school);
    }

    // ------------------------------------------------------------------
    // 标准瓶罐（CanonicalCatalog，固定 60 个，30 对）
    // ------------------------------------------------------------------

    /** 标准瓶罐全部 60 个，顺序与旧版 20 槽库存保持一致。 */
    public static List<CanonicalCatalog.Bottle> canonicalBottles() {
        return CanonicalCatalog.ALL;
    }

    /** 按 ID 查询标准瓶罐。 */
    public static Optional<CanonicalCatalog.Bottle> canonicalBottle(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(CanonicalCatalog.BY_ID.get(id));
    }

    /** 两个标准瓶罐是否为同一对的一阴一阳（有机 / 无机）组合。 */
    public static boolean isBestCanonicalPair(String a, String b) {
        return CanonicalCatalog.best(a, b);
    }

    // ------------------------------------------------------------------
    // 满装瓶罐（BuildCatalog，包含历史遗留条目）
    // ------------------------------------------------------------------

    /** 全部满装瓶罐（当前为标准瓶罐集合）。 */
    public static List<BuildCatalog.Bottle> buildBottles() {
        return BuildCatalog.ALL;
    }

    /** 包含历史遗留条目的完整列表。 */
    public static List<BuildCatalog.Bottle> legacyBuildBottles() {
        return BuildCatalog.LEGACY;
    }

    /** 按 ID 查询满装瓶罐。历史遗留（非标准）条目不会出现在结果中。 */
    public static Optional<BuildCatalog.Bottle> buildBottle(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(BuildCatalog.BY_ID.get(id));
    }

    /** 该瓶罐是否属于标准瓶罐。 */
    public static boolean isCanonicalBuildBottle(BuildCatalog.Bottle bottle) {
        return BuildCatalog.canonical(bottle);
    }

    /** 两个满装瓶罐是否构成最佳配对。 */
    public static boolean isBestBuildPair(BuildCatalog.Bottle a, BuildCatalog.Bottle b) {
        return BuildCatalog.best(a, b);
    }

    // ------------------------------------------------------------------
    // 制作判定（CraftMath，纯计算部分）
    // ------------------------------------------------------------------

    /** 制作品阶（1–9），输入先钳制到 [1, 9] 再取整。 */
    public static int craftRank(double n) {
        return CraftMath.rank(n);
    }

    /** 升到下一品阶所需的累计量；9 阶为 0。 */
    public static int craftNeeded(int rank) {
        return CraftMath.needed(rank);
    }

    /** 基础成功率（百分比，钳制在 [5, 100]）。 */
    public static double craftBaseChance(int bodyLevel, int recipeLevel, int equippedCount) {
        return CraftMath.base(bodyLevel, recipeLevel, equippedCount);
    }

    /** 最终成功率 = 基础值 + 分数 × 25，钳制在 [5, 100]。 */
    public static double craftChance(double baseChance, double score) {
        return CraftMath.chance(baseChance, score);
    }

    /** 节拍得分：0–1，越接近目标时间越高。 */
    public static double craftTiming(int elapsedTicks, int targetTicks, int widthTicks) {
        return CraftMath.timing(elapsedTicks, targetTicks, widthTicks);
    }

    // ------------------------------------------------------------------
    // 研究与培养（BuildRules）
    // ------------------------------------------------------------------

    /** 危险等级钳制到 [0, 10]。 */
    public static int buildDanger(int level) {
        return BuildRules.danger(level);
    }

    /** 当前等级升到下一级所需的进度；满级为 0。 */
    public static int buildNeed(int level) {
        return BuildRules.need(level);
    }

    /** 等级对应的缩放系数。 */
    public static double buildScale(int level) {
        return BuildRules.scale(level);
    }

    /** 是否满足进阶条件（研究、制作、配对、医疗四项均达标）。 */
    public static boolean buildCanAdvance(int grade, int research, int crafted, int pairs, int medical) {
        return BuildRules.advance(grade, research, crafted, pairs, medical);
    }

    /** 分数是否达到成功门槛（手术 0.5，普通 0.25）。 */
    public static boolean buildSufficient(boolean surgery, double score) {
        return BuildRules.sufficient(surgery, score);
    }

    /** 满装操作的成功率（百分比，钳制在 [0, 100]）。 */
    public static double buildChance(boolean surgery, int grade, double score) {
        return BuildRules.chance(surgery, grade, score);
    }

    /** 一次培养结算，返回新的等级、进度、待吸收量与负载。 */
    public static BuildRules.Cultivation buildCultivate(int level, double progress, double pending, double load) {
        return BuildRules.second(level, progress, pending, load);
    }

    // ------------------------------------------------------------------
    // 捕获（CaptureRules）
    // ------------------------------------------------------------------

    /** 目标是否满足采样条件（血量比例不超过 5%）。 */
    public static boolean captureSampleable(double hp, double maxHp) {
        return CaptureRules.sampleable(hp, maxHp);
    }

    /** 危险等级对应的捕获缩放系数。 */
    public static double captureScale(int hazard) {
        return CaptureRules.scale(hazard);
    }

    /** 捕获后属性值：value × 缩放，钳制在 [min, cap] 之间。 */
    public static double captureStat(double value, int hazard, double min, double cap) {
        return CaptureRules.stat(value, hazard, min, cap);
    }

    /** 血量比例，钳制在 [0, 1]。 */
    public static double captureRatio(double hp, double maxHp) {
        return CaptureRules.ratio(hp, maxHp);
    }

    /** 是否为特殊手指面板（sukuna_20_fingers）。 */
    public static boolean isHandPanel(String itemId) {
        return CaptureRules.handPanel(itemId);
    }

    /** 有机 / 无机两个瓶罐 ID 能否组成有效捕获配对。 */
    public static boolean isValidCapturePair(String organicId, String inorganicId) {
        return CaptureRules.pairValid(organicId, inorganicId);
    }

    // ------------------------------------------------------------------
    // 个人配对（PersonalRules）
    // ------------------------------------------------------------------

    /**
     * 生成一个无固定点的随机配对（错排）。
     *
     * @param rng  随机源
     * @param size 池大小，范围 [2, 256]
     * @throws IllegalArgumentException 池大小越界
     */
    public static int[] randomPairing(Random rng, int size) {
        return PersonalRules.generate(rng, size);
    }

    /** 检查一个数组是否为有效配对（无固定点、双射）。 */
    public static boolean isValidPairing(int[] pairing) {
        return PersonalRules.valid(pairing);
    }

    /** 按权重随机选择一个下标。 */
    public static int weightedPick(List<Integer> weights, Random rng) {
        return PersonalRules.weighted(weights, rng);
    }

    // ------------------------------------------------------------------
    // 动作曲线（BuildMotion）
    // ------------------------------------------------------------------

    /** 每帧姿态通道数（22）。 */
    public static int motionChannels() {
        return BuildMotion.CHANNELS;
    }

    /** 已加载的动作曲线 ID 集合。 */
    public static Set<Integer> motionClipIds() {
        return BuildMotion.CLIPS.keySet();
    }

    /** 全部动作曲线的只读视图（ID → 曲线）。 */
    public static Map<Integer, BuildMotion.Clip> motionClips() {
        return BuildMotion.CLIPS;
    }

    /**
     * 在动作曲线的某个时刻采样姿态（线性插值 + smoothstep 缓动）。
     *
     * @param clipId 曲线 ID，不存在时返回全零数组
     * @param age    动画进行到的 tick，不在 (0, poseDuration) 内时返回全零数组
     * @return 长度为 {@link #motionChannels()} 的新数组
     */
    public static float[] motionPose(int clipId, float age) {
        return BuildMotion.sample(clipId, age);
    }

    /** 缓动函数 smoothstep，输入钳制到 [0, 1]。 */
    public static float motionEase(float t) {
        return BuildMotion.ease(t);
    }

    /** 检查动作参数是否合法：曲线存在、时长匹配、所有坐标为有限且绝对值不超过 3000 万。 */
    public static boolean isValidMotion(int clipId, int duration, double... coordinates) {
        return BuildMotion.valid(clipId, duration, coordinates);
    }
}
