package com.rpgtemplate.core.stats;

import java.util.Locale;

public final class NumberFormatter {
    private static final String[] SUFFIX = new String[]{"", "K", "M", "B", "T"};

    private NumberFormatter() {
    }

    public static String format(double var0) {
        if (Double.isNaN(var0)) {
            return "NaN";
        } else if (Double.isInfinite(var0)) {
            return var0 > 0.0 ? "∞" : "-∞";
        } else {
            double var2 = Math.abs(var0);
            if (var2 < 1000.0) {
                return var2 == Math.floor(var2) ? String.format(Locale.ROOT, "%.0f", var0) : String.format(Locale.ROOT, "%.1f", var0);
            } else {
                int var4 = (int)Math.floor(Math.log10(var2) / 3.0);
                if (var4 < SUFFIX.length) {
                    double var5 = var0 / Math.pow(1000.0, (double)var4);
                    return trim(var5) + SUFFIX[var4];
                } else {
                    return String.format(Locale.ROOT, "%.2e", var0).replace("e+", "e");
                }
            }
        }
    }

    private static String trim(double var0) {
        double var2 = Math.abs(var0);
        if (var2 >= 100.0) {
            return String.format(Locale.ROOT, "%.0f", var0);
        } else {
            return var2 >= 10.0 ? String.format(Locale.ROOT, "%.1f", var0) : String.format(Locale.ROOT, "%.2f", var0);
        }
    }
}
