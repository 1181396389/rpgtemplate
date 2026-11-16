package com.rpgtemplate.core.template;

import com.google.gson.JsonObject;

public record LineageReward(String type, double weight, JsonObject json) {
    public static final String XP = "xp";
    public static final String DRAW = "draw";
    public static final String SHARD = "shard";
    public static final String STAT_BONUS = "stat_bonus";
    public static final String TRAIT = "trait";
    public static final String ITEM = "item";

    public String str(String var1, String var2) {
        return this.json != null && this.json.has(var1) && !this.json.get(var1).isJsonNull() ? this.json.get(var1).getAsString() : var2;
    }

    public double num(String var1, double var2) {
        return this.json != null && this.json.has(var1) && this.json.get(var1).isJsonPrimitive() ? this.json.get(var1).getAsDouble() : var2;
    }

    public int integer(String var1, int var2) {
        return (int)this.num(var1, (double)var2);
    }

    public TraitEntry trait() {
        if ("trait".equals(this.type) && this.json != null && this.json.has("trait")) {
            JsonObject var1 = this.json.getAsJsonObject("trait");
            if (!var1.has("type")) {
                return null;
            } else {
                JsonObject var2 = var1.has("params") ? var1.getAsJsonObject("params") : new JsonObject();
                String var3 = var1.get("type").getAsString();
                String var4 = var1.has("name") ? var1.get("name").getAsString() : var3;
                String var5 = var1.has("description") ? var1.get("description").getAsString() : "";
                return new TraitEntry(var3, var4, var5, var2, false);
            }
        } else {
            return null;
        }
    }

    public boolean global() {
        return "global".equalsIgnoreCase(this.str("scope", "lineage"));
    }

    public String displayName() {
        TraitEntry var1 = this.trait();
        return var1 != null ? var1.name() : this.str("name", this.str("id", this.type));
    }
}
