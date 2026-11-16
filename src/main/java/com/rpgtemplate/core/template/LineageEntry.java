package com.rpgtemplate.core.template;

import java.util.List;

public record LineageEntry(double chance, int pity, double weight, int playerLevel, double carryLevel, List<LineageReward> consolation) {
    public double effectiveChance(double var1) {
        return Math.min(1.0, Math.max(0.0, this.chance) * (1.0 + Math.max(0.0, var1)));
    }

    public int mergedLevel(int var1, int var2) {
        int var3 = (int)Math.round((double)Math.max(0, var1) * Math.max(0.0, this.carryLevel));
        var3 = Math.max(1, var3);
        return var2 > 0 ? Math.min(var3, var2) : var3;
    }

    public boolean pityReached(int var1) {
        return this.pity > 0 && var1 >= this.pity;
    }

    public double safeWeight() {
        return this.weight > 0.0 ? this.weight : 1.0;
    }
}
