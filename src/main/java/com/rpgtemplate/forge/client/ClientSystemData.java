package com.rpgtemplate.forge.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.client.gui.SystemPanelScreen;
import com.rpgtemplate.forge.client.hud.SystemHud;

public final class ClientSystemData {
    private static volatile PlayerSystemData data = new PlayerSystemData();
    private static volatile double maxHp;
    private static volatile double maxMp;
    private static volatile double maxSp;
    private static volatile double xpToNext;
    private static volatile double templateXpToNext;
    private static volatile int templateMaxLevel;
    private static volatile String growthMode = "LINEAR";
    private static volatile JsonObject derived = new JsonObject();

    private ClientSystemData() {
    }

    public static void update(String var0) {
        JsonObject var1 = JsonParser.parseString(var0).getAsJsonObject();
        PlayerSystemData var2 = data;
        double var3 = xpToNext;
        data = PlayerSystemData.fromJson(var1);
        if (var2.isActivated() && data.isActivated()) {
            double var5;
            if (data.level() == var2.level()) {
                var5 = data.xp() - var2.xp();
            } else if (data.level() > var2.level()) {
                var5 = var3 - var2.xp() + data.xp();
            } else {
                var5 = 0.0;
            }

            if (var5 > 1.0E-6) {
                SystemHud.onXpGained(var5, data.level() > var2.level());
            }
        }

        JsonObject var7 = var1.has("derived") ? var1.getAsJsonObject("derived") : new JsonObject();
        maxHp = num(var7, "maxHp");
        maxMp = num(var7, "maxMp");
        maxSp = num(var7, "maxSp");
        xpToNext = num(var7, "xpToNext");
        templateXpToNext = num(var7, "templateXpToNext");
        templateMaxLevel = var7.has("templateMaxLevel") ? var7.get("templateMaxLevel").getAsInt() : 0;
        growthMode = var7.has("growthMode") ? var7.get("growthMode").getAsString() : "LINEAR";
        derived = var7;
        if (var2.isActivated() && data.isActivated() && data.templates().size() > var2.templates().size()) {
            SystemPanelScreen.onNewTemplate(data.newestTemplateId());
        }
    }

    public static double derived(String var0, double var1) {
        JsonObject var3 = derived;
        return var3.has(var0) && var3.get(var0).isJsonPrimitive() ? var3.get(var0).getAsDouble() : var1;
    }

    public static boolean derivedBool(String var0, boolean var1) {
        JsonObject var2 = derived;
        return var2.has(var0) && var2.get(var0).isJsonPrimitive() ? var2.get(var0).getAsBoolean() : var1;
    }

    public static double templateXpToNext(String var0) {
        JsonObject var1 = derived;
        if (!var1.has("templateXp")) {
            return 0.0;
        } else {
            JsonObject var2 = var1.getAsJsonObject("templateXp");
            return var2.has(var0) ? var2.get(var0).getAsDouble() : 0.0;
        }
    }

    public static void clear() {
        data = new PlayerSystemData();
        templateXpToNext = 0.0;
        xpToNext = 0.0;
        maxSp = 0.0;
        maxMp = 0.0;
        maxHp = 0.0;
        templateMaxLevel = 0;
        derived = new JsonObject();
    }

    public static PlayerSystemData get() {
        return data;
    }

    public static double maxHp() {
        return maxHp;
    }

    public static double maxMp() {
        return maxMp;
    }

    public static double maxSp() {
        return maxSp;
    }

    public static double xpToNext() {
        return xpToNext;
    }

    public static double templateXpToNext() {
        return templateXpToNext;
    }

    public static int templateMaxLevel() {
        return templateMaxLevel;
    }

    public static String growthMode() {
        return growthMode;
    }

    private static double num(JsonObject var0, String var1) {
        return var0.has(var1) ? var0.get(var1).getAsDouble() : 0.0;
    }
}
