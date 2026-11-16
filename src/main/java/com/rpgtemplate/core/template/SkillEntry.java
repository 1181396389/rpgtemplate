package com.rpgtemplate.core.template;

import com.google.gson.JsonObject;

public record SkillEntry(
    String id,
    String name,
    CostType costType,
    double baseCost,
    double coefficient,
    int cooldownTicks,
    String description,
    JsonObject params,
    int unlockLevel,
    boolean remove
) {
    public SkillEntry(String var1, String var2, CostType var3, double var4, double var6, int var8, String var9, JsonObject var10) {
        this(var1, var2, var3, var4, var6, var8, var9, var10, 0, false);
    }

    public boolean unlockedAt(int var1) {
        return var1 >= this.unlockLevel;
    }
}
