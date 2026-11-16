package com.rpgtemplate.core.stats;

public final class CombatBalance {
    private CombatBalance() {
    }

    public static double mitigate(double var0, double var2) {
        if (Double.isFinite(var0) && !(var0 <= 0.0)) {
            double var4 = Double.isFinite(var2) ? Math.max(0.0, var2) : 0.0;
            return Math.max(Math.min(1.0, var0), var0 - var4);
        } else {
            return 0.0;
        }
    }
}
