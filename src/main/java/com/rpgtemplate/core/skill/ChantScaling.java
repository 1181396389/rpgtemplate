package com.rpgtemplate.core.skill;

import java.util.Set;

public final class ChantScaling {
    public static final Set<String> CHANT_KEYS = Set.of("charge.ticks_per_stage", "charge_ticks", "open.charge_ticks", "cast_ticks");
    public static final int MIN_CHANT_TICKS = 4;

    private ChantScaling() {
    }

    public static boolean isChantKey(String var0) {
        return CHANT_KEYS.contains(var0);
    }

    public static boolean isSlownessKey(String var0) {
        return var0.equals("slowness") || var0.endsWith(".slowness");
    }

    public static double chantTicks(double var0, double var2) {
        if (Double.isFinite(var0) && !(var0 <= 0.0)) {
            double var4 = Double.isFinite(var2) && var2 > 1.0 ? var2 : 1.0;
            return Math.max(Math.min(var0, 4.0), var0 / var4);
        } else {
            return var0;
        }
    }

    public static double slowness(double var0, double var2) {
        if (Double.isFinite(var0) && !(var0 < 0.0)) {
            double var4 = Double.isFinite(var2) ? Math.max(0.0, var2) : 0.0;
            double var6 = var0 - var4;
            return var6 < 0.0 ? -1.0 : var6;
        } else {
            return var0;
        }
    }

    public static double skipPower(int var0, int var1, double var2, double var4) {
        if (var1 > 0 && var0 < var1) {
            int var6 = var1 - Math.max(0, var0);
            double var7 = Math.max(0.0, Math.min(1.0, var4));
            return Math.max(var7, 1.0 - (double)var6 * Math.max(0.0, var2));
        } else {
            return 1.0;
        }
    }
}
