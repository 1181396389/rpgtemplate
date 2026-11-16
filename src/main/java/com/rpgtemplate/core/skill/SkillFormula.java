package com.rpgtemplate.core.skill;

import com.rpgtemplate.core.template.Quality;

public final class SkillFormula {
    public static final double STAT_TO_POWER = 0.1;
    public static final double ATTACK_STAT_TO_POWER = 0.04;
    public static final double PER_TEMPLATE_LEVEL = 0.05;

    private SkillFormula() {
    }

    public static double power(double var0, double var2, int var4, double var5, double var7) {
        double var9 = 1.0 + (double)Math.max(0, var4 - 1) * 0.05;
        double var11 = var0 * var2 * 0.1 * var9 * var5 * var7;
        return !Double.isNaN(var11) && !(var11 < 0.0) ? Math.min(var11, 8.5070587E37F) : 0.0;
    }

    public static double attackPower(double var0, double var2, int var4, double var5, double var7) {
        double var9 = 1.0 + (double)Math.max(0, var4 - 1) * 0.01;
        double var11 = var0 * var2 * 0.04 * var9 * var5 * var7;
        return Double.isFinite(var11) ? Math.max(0.0, Math.min(var11, 8.5070587E37F)) : 0.0;
    }

    public static double cost(double var0, Quality var2) {
        return Math.max(0.0, var0) * (var2 == null ? 1.0 : var2.costMultiplier());
    }

    public static double toVanilla(double var0, double var2) {
        return var2 <= 0.0 ? var0 : var0 / var2;
    }
}
