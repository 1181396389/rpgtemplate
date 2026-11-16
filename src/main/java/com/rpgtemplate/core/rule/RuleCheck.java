package com.rpgtemplate.core.rule;

import java.util.function.DoubleSupplier;

public final class RuleCheck {
    private RuleCheck() {
    }

    public static RuleResult evaluate(int var0, int var1, int var2, DoubleSupplier var3) {
        int var4 = (int)Math.floor(var3.getAsDouble() * 7.0) - 3;
        return fromScore(var0 - var2 + var1 + var4);
    }

    public static RuleResult fromScore(int var0) {
        if (var0 >= 10) {
            return RuleResult.FULL;
        } else if (var0 >= 3) {
            return RuleResult.MAJOR;
        } else {
            return var0 >= -4 ? RuleResult.MINOR : RuleResult.FAIL;
        }
    }

    public static int ruleLevelFromMaxHealth(double var0) {
        return (int)Math.floor(Math.log(Math.max(1.0, var0)) / Math.log(2.0));
    }
}
