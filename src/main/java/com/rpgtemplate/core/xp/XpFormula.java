package com.rpgtemplate.core.xp;

public final class XpFormula {
    private XpFormula() {
    }

    public static double kill(double var0, double var2, boolean var4, double var5, boolean var7, double var8) {
        double var10 = Math.max(0.0, var0) * var2;
        if (var4) {
            var10 *= var5;
        }

        if (var7) {
            var10 *= var8;
        }

        return var10;
    }

    public static double eat(int var0, float var1, double var2) {
        return ((double)Math.max(0, var0) * 2.0 + (double)Math.max(0.0F, var1)) * var2;
    }

    public static double mine(float var0, boolean var1, double var2, double var4) {
        if (var0 < 0.0F) {
            return 0.0;
        } else {
            double var6 = Math.max(0.05, (double)var0 * 0.5) * var2;
            if (var1) {
                var6 *= var4;
            }

            return var6;
        }
    }
}
