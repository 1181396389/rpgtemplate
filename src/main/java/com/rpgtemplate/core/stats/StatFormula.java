package com.rpgtemplate.core.stats;

import com.rpgtemplate.core.config.CoreConfig;

public final class StatFormula {
    private StatFormula() {
    }

    public static double levelStat(StatType var0, int var1, CoreConfig var2) {
        return clamp(switch (var2.growthMode) {
            case EXPONENTIAL -> var0.base() * Math.pow(var2.exponentialBase, (double)var1);
            default -> var0.base() * (1.0 + (double)var1);
        }, var2.statCap);
    }

    public static double xpToNextLevel(int var0, CoreConfig var1) {
        return clamp(Math.max(1.0, switch (var1.growthMode) {
            case EXPONENTIAL -> var1.expXpBase * Math.pow(var1.expXpGrowth, (double)var0);
            default -> var1.linearXpBase * Math.pow((double)var0 + 1.0, var1.linearXpPower);
        }), var1.statCap);
    }

    public static double clamp(double var0, double var2) {
        if (Double.isNaN(var0)) {
            return 0.0;
        } else {
            return !Double.isInfinite(var0) && !(var0 > var2) ? var0 : var2;
        }
    }
}
