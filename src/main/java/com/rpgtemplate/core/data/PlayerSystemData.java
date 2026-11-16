package com.rpgtemplate.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;

public final class PlayerSystemData {
    private boolean activated;
    private int level;
    private double xp;
    private double hp = -1.0;
    private double mp = -1.0;
    private double sp = -1.0;
    private final List<TemplateInstance> templates = new ArrayList<>();
    private String loadedTemplateId;
    private String newestTemplateId;
    private int pendingDraws;
    private boolean drawUnlocked;
    private long totalDraws;
    private final Map<String, Double> values = new HashMap<>();

    public void activate() {
        if (!this.activated) {
            this.activated = true;
            this.level = 0;
            this.xp = 0.0;
            this.templates.clear();
            this.templates.add(new TemplateInstance("rpgtemplate:traveler", 0, 0.0));
            this.loadedTemplateId = "rpgtemplate:traveler";
            this.newestTemplateId = "rpgtemplate:traveler";
            this.drawUnlocked = true;
            this.pendingDraws = 0;
            this.hp = this.mp = this.sp = -1.0;
        }
    }

    public void reset() {
        this.activated = false;
        this.level = 0;
        this.xp = 0.0;
        this.hp = this.mp = this.sp = -1.0;
        this.templates.clear();
        this.loadedTemplateId = null;
        this.newestTemplateId = null;
        this.pendingDraws = 0;
        this.drawUnlocked = false;
        this.totalDraws = 0L;
        this.values.clear();
    }

    public boolean isActivated() {
        return this.activated;
    }

    public int level() {
        return this.level;
    }

    public double xp() {
        return this.xp;
    }

    public double hp() {
        return this.hp;
    }

    public double mp() {
        return this.mp;
    }

    public double sp() {
        return this.sp;
    }

    public List<TemplateInstance> templates() {
        return Collections.unmodifiableList(this.templates);
    }

    public String loadedTemplateId() {
        return this.loadedTemplateId;
    }

    public String newestTemplateId() {
        return this.newestTemplateId;
    }

    public int pendingDraws() {
        return this.pendingDraws;
    }

    public boolean isDrawUnlocked() {
        return this.drawUnlocked;
    }

    public long totalDraws() {
        return this.totalDraws;
    }

    public void setLevel(int var1) {
        this.level = Math.max(0, var1);
    }

    public void setXp(double var1) {
        this.xp = Math.max(0.0, var1);
    }

    public void setHp(double var1) {
        this.hp = var1;
    }

    public void setMp(double var1) {
        this.mp = var1;
    }

    public void setSp(double var1) {
        this.sp = var1;
    }

    public void setLoadedTemplateId(String var1) {
        this.loadedTemplateId = var1;
    }

    public void setNewestTemplateId(String var1) {
        this.newestTemplateId = var1;
    }

    public void setPendingDraws(int var1) {
        this.pendingDraws = Math.max(0, var1);
    }

    public void setDrawUnlocked(boolean var1) {
        this.drawUnlocked = var1;
    }

    public void incrementTotalDraws() {
        this.totalDraws++;
    }

    public Optional<TemplateInstance> findTemplate(String var1) {
        if (var1 == null) {
            return Optional.empty();
        } else {
            for (TemplateInstance var3 : this.templates) {
                if (var3.templateId().equals(var1)) {
                    return Optional.of(var3);
                }
            }

            return Optional.empty();
        }
    }

    public Optional<TemplateInstance> loadedTemplate() {
        return this.findTemplate(this.loadedTemplateId);
    }

    public void addTemplate(TemplateInstance var1) {
        this.templates.add(var1);
    }

    public double value(String var1, double var2) {
        Double var4 = this.values.get(var1);
        return var4 == null ? var2 : var4;
    }

    public void setValue(String var1, double var2) {
        this.values.put(var1, var2);
    }

    public void removeValue(String var1) {
        this.values.remove(var1);
    }

    public JsonObject toJson() {
        JsonObject var1 = new JsonObject();
        var1.addProperty("v", 1);
        var1.addProperty("activated", this.activated);
        var1.addProperty("level", this.level);
        var1.addProperty("xp", this.xp);
        var1.addProperty("hp", this.hp);
        var1.addProperty("mp", this.mp);
        var1.addProperty("sp", this.sp);
        JsonArray var2 = new JsonArray();

        for (TemplateInstance var4 : this.templates) {
            JsonObject var5 = new JsonObject();
            var5.addProperty("id", var4.templateId());
            var5.addProperty("level", var4.level());
            var5.addProperty("xp", var4.xp());
            var2.add(var5);
        }

        var1.add("templates", var2);
        if (this.loadedTemplateId != null) {
            var1.addProperty("loaded", this.loadedTemplateId);
        }

        if (this.newestTemplateId != null) {
            var1.addProperty("newest", this.newestTemplateId);
        }

        var1.addProperty("pendingDraws", this.pendingDraws);
        var1.addProperty("drawUnlocked", this.drawUnlocked);
        var1.addProperty("totalDraws", this.totalDraws);
        JsonObject var6 = new JsonObject();

        for (Entry var8 : this.values.entrySet()) {
            var6.addProperty((String)var8.getKey(), (Number)var8.getValue());
        }

        var1.add("values", var6);
        return var1;
    }

    public String toJsonString() {
        return this.toJson().toString();
    }

    public static PlayerSystemData fromJson(JsonObject var0) {
        PlayerSystemData var1 = new PlayerSystemData();
        var1.activated = getBool(var0, "activated");
        var1.level = var0.has("level") ? var0.get("level").getAsInt() : 0;
        var1.xp = getDouble(var0, "xp", 0.0);
        var1.hp = getDouble(var0, "hp", -1.0);
        var1.mp = getDouble(var0, "mp", -1.0);
        var1.sp = getDouble(var0, "sp", -1.0);
        if (var0.has("templates")) {
            for (JsonElement var3 : var0.getAsJsonArray("templates")) {
                JsonObject var4 = var3.getAsJsonObject();
                var1.templates
                    .add(new TemplateInstance(var4.get("id").getAsString(), var4.has("level") ? var4.get("level").getAsInt() : 1, getDouble(var4, "xp", 0.0)));
            }
        }

        var1.loadedTemplateId = var0.has("loaded") ? var0.get("loaded").getAsString() : null;
        var1.newestTemplateId = var0.has("newest") ? var0.get("newest").getAsString() : null;
        var1.pendingDraws = var0.has("pendingDraws") ? var0.get("pendingDraws").getAsInt() : 0;
        var1.drawUnlocked = getBool(var0, "drawUnlocked");
        var1.totalDraws = var0.has("totalDraws") ? var0.get("totalDraws").getAsLong() : 0L;
        if (var0.has("values")) {
            for (Entry var6 : var0.getAsJsonObject("values").entrySet()) {
                var1.values.put((String)var6.getKey(), ((JsonElement)var6.getValue()).getAsDouble());
            }
        }

        return var1;
    }

    public static PlayerSystemData fromJsonString(String var0) {
        if (var0 != null && !var0.isEmpty()) {
            try {
                return fromJson(JsonParser.parseString(var0).getAsJsonObject());
            } catch (RuntimeException var2) {
                return new PlayerSystemData();
            }
        } else {
            return new PlayerSystemData();
        }
    }

    public PlayerSystemData copy() {
        return fromJson(this.toJson());
    }

    private static boolean getBool(JsonObject var0, String var1) {
        return var0.has(var1) && var0.get(var1).getAsBoolean();
    }

    private static double getDouble(JsonObject var0, String var1, double var2) {
        return var0.has(var1) ? var0.get(var1).getAsDouble() : var2;
    }
}
