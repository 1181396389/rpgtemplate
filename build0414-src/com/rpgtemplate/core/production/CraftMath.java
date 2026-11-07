package com.rpgtemplate.core.production;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatType;

/**
 * 职业/build 系统已移除。本类只保留一个直通存根，
 * 因为未改动的 {@code StatCalculator} 仍然调用 {@code enhanceStat}。
 * 直通：返回输入的基础值，不做任何加成。
 */
public final class CraftMath {

    private CraftMath() {
    }

    public static double enhanceStat(double baseValue, StatType type, PlayerSystemData data, CoreConfig config) {
        return baseValue;
    }
}
