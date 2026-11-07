package com.rpgtemplate.api;

import com.rpgtemplate.core.template.Quality;

import java.util.List;
import java.util.Optional;

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
 * <p>职业/build 相关的 API（制作、瓶罐、研究、捕获、配对、动作曲线）已随职业系统移除。
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
}
