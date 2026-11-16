package com.rpgtemplate.core.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TemplateJsonParser {
    private TemplateJsonParser() {
    }

    public static TemplateDefinition parse(String var0, JsonObject var1) {
        String var2 = str(var1, "name", var0);
        String var3 = str(var1, "source", "");
        String var4 = str(var1, "period", "");
        Quality var5 = Quality.parse(str(var1, "quality", "N"));
        TemplateType var6 = TemplateType.parse(str(var1, "type", "BALANCED"));
        int var7 = integer(var1, "max_level", 0);
        String var8 = var1.has("parent") && !var1.get("parent").isJsonNull() ? var1.get("parent").getAsString() : null;
        int var9 = 0;
        if (var1.has("advance_condition")) {
            JsonObject var10 = var1.getAsJsonObject("advance_condition");
            var9 = integer(var10, "player_level", 0);
        }

        double var20 = 0.0;
        double var12 = 0.0;
        double var14 = 0.0;
        if (var1.has("bonus")) {
            JsonObject var16 = var1.getAsJsonObject("bonus");
            var20 = dbl(var16, "hp", 0.0);
            var12 = dbl(var16, "mp", 0.0);
            var14 = dbl(var16, "sp", 0.0);
        }

        ArrayList var21 = new ArrayList();
        if (var1.has("skills")) {
            for (JsonElement var18 : var1.getAsJsonArray("skills")) {
                var21.add(parseSkill(var18.getAsJsonObject()));
            }
        }

        LineageEntry var22 = var8 != null && var1.has("lineage") && var1.get("lineage").isJsonObject() ? parseLineage(var1.getAsJsonObject("lineage")) : null;
        boolean var23 = var5.drawable() && var8 == null;
        boolean var19 = var1.has("drawable") ? var1.get("drawable").getAsBoolean() : var23;
        return new TemplateDefinition(
            var0,
            var2,
            var3,
            var4,
            var5,
            var6,
            var7,
            var8,
            var9,
            var20,
            var12,
            var14,
            var1.has("bonus") ? dbl(var1.getAsJsonObject("bonus"), "def", var20) : 0.0,
            Collections.unmodifiableList(var21),
            traits(var1, "passives"),
            traits(var1, "weaknesses"),
            traits(var1, "negative_states"),
            traits(var1, "quirks"),
            str(var1, "description", ""),
            var19,
            var22
        );
    }

    private static SkillEntry parseSkill(JsonObject var0) {
        if (!var0.has("id")) {
            throw new IllegalArgumentException("技能缺少 id 字段");
        } else {
            String var1 = var0.get("id").getAsString();
            JsonObject var2 = var0.has("params") ? var0.getAsJsonObject("params") : new JsonObject();
            return new SkillEntry(
                var1,
                str(var0, "name", var1),
                CostType.parse(str(var0, "cost_type", "MP")),
                dbl(var0, "base_cost", 0.0),
                dbl(var0, "coefficient", 1.0),
                integer(var0, "cooldown_ticks", 20),
                str(var0, "description", ""),
                var2,
                Math.max(0, integer(var0, "unlock_level", 0)),
                bool(var0, "remove", false)
            );
        }
    }

    static LineageEntry parseLineage(JsonObject var0) {
        ArrayList var1 = new ArrayList();
        if (var0.has("consolation") && var0.get("consolation").isJsonArray()) {
            for (JsonElement var3 : var0.getAsJsonArray("consolation")) {
                if (var3.isJsonObject()) {
                    JsonObject var4 = var3.getAsJsonObject();
                    if (!var4.has("type")) {
                        throw new IllegalArgumentException("lineage.consolation 中的条目缺少 type 字段");
                    }

                    var1.add(new LineageReward(var4.get("type").getAsString(), Math.max(0.0, dbl(var4, "weight", 1.0)), var4));
                }
            }
        }

        return new LineageEntry(
            dbl(var0, "chance", 1.0),
            Math.max(0, integer(var0, "pity", 0)),
            dbl(var0, "weight", 1.0),
            Math.max(0, integer(var0, "player_level", 0)),
            dbl(var0, "carry_level", 1.0),
            Collections.unmodifiableList(var1)
        );
    }

    private static List<TraitEntry> traits(JsonObject var0, String var1) {
        if (!var0.has(var1)) {
            return Collections.emptyList();
        } else {
            JsonArray var2 = var0.getAsJsonArray(var1);
            ArrayList var3 = new ArrayList();

            for (JsonElement var5 : var2) {
                JsonObject var6 = var5.getAsJsonObject();
                if (!var6.has("type")) {
                    throw new IllegalArgumentException(var1 + " 中的条目缺少 type 字段");
                }

                JsonObject var7 = var6.has("params") ? var6.getAsJsonObject("params") : new JsonObject();
                String var8 = var6.get("type").getAsString();
                var3.add(new TraitEntry(var8, str(var6, "name", var8), str(var6, "description", ""), var7, bool(var6, "remove", false)));
            }

            return Collections.unmodifiableList(var3);
        }
    }

    private static String str(JsonObject var0, String var1, String var2) {
        return var0.has(var1) && !var0.get(var1).isJsonNull() ? var0.get(var1).getAsString() : var2;
    }

    private static int integer(JsonObject var0, String var1, int var2) {
        return var0.has(var1) && var0.get(var1).isJsonPrimitive() ? var0.get(var1).getAsInt() : var2;
    }

    private static double dbl(JsonObject var0, String var1, double var2) {
        return var0.has(var1) && var0.get(var1).isJsonPrimitive() ? var0.get(var1).getAsDouble() : var2;
    }

    private static boolean bool(JsonObject var0, String var1, boolean var2) {
        return var0.has(var1) && var0.get(var1).isJsonPrimitive() ? var0.get(var1).getAsBoolean() : var2;
    }
}
