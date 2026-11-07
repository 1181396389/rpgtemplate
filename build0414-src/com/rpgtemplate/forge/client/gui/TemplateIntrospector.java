package com.rpgtemplate.forge.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgtemplate.core.template.TemplateDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端模板扩展元数据读取器（双通道：内置 JSON 资源 + {@link TemplateDefinition} 反射兜底）。
 *
 * <p>用于 {@link SystemPanelScreen} 完整展示角色模板的「来源时期、属性加成、被动、弱点、负面病症、癖好」，
 * 兼容基础包内置模板（如宿傩多阶段线）与数据包/资源目录模板（如一无所有及新增角色模板）。
 */
public final class TemplateIntrospector {
    private static final Map<String, Meta> CACHE = new ConcurrentHashMap<>();

    private TemplateIntrospector() {
    }

    public record TraitView(String name, String description) {
    }

    public record Meta(
        String source,
        String period,
        double hpBonus,
        double mpBonus,
        double spBonus,
        double defBonus,
        List<TraitView> passives,
        List<TraitView> weaknesses,
        List<TraitView> negatives,
        List<TraitView> quirks
    ) {
        public int drawbackCount() {
            return weaknesses.size() + negatives.size() + quirks.size();
        }
    }

    public static Meta inspect(TemplateDefinition def) {
        if (def == null || def.id() == null) {
            return empty();
        }
        return CACHE.computeIfAbsent(def.id(), id -> load(def));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Meta empty() {
        return new Meta("", "", 0.0, 0.0, 0.0, 0.0, List.of(), List.of(), List.of(), List.of());
    }

    private static Meta load(TemplateDefinition def) {
        JsonObject json = readClasspathJson(def.id());
        if (json != null) {
            String source = str(json, "source");
            String period = str(json, "period");
            JsonObject bonus = json.has("bonus") && json.get("bonus").isJsonObject()
                ? json.getAsJsonObject("bonus") : null;
            double hp = num(bonus, "hp");
            double mp = num(bonus, "mp");
            double sp = num(bonus, "sp");
            double df = bonus != null && bonus.has("def") ? num(bonus, "def") : num(bonus, "defense");
            return new Meta(
                source,
                period,
                hp,
                mp,
                sp,
                df,
                parseTraits(json, "passives"),
                parseTraits(json, "weaknesses"),
                parseTraits(json, "negatives"),
                parseTraits(json, "quirks")
            );
        }
        return reflectFromDef(def);
    }

    private static JsonObject readClasspathJson(String id) {
        int colon = id.indexOf(':');
        String ns = colon >= 0 ? id.substring(0, colon) : "rpgtemplate";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String resPath = "data/" + ns + "/rpg_templates/" + path + ".json";
        try (InputStream in = TemplateIntrospector.class.getClassLoader().getResourceAsStream(resPath)) {
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement el = JsonParser.parseReader(reader);
                return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<TraitView> parseTraits(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray arr = root.getAsJsonArray(key);
        List<TraitView> list = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String name = str(obj, "name");
                if (name.isEmpty()) {
                    name = str(obj, "type");
                }
                String desc = str(obj, "description");
                if (!name.isEmpty() || !desc.isEmpty()) {
                    list.add(new TraitView(name, desc));
                }
            } else if (el.isJsonPrimitive()) {
                list.add(new TraitView(el.getAsString(), ""));
            }
        }
        return List.copyOf(list);
    }

    private static Meta reflectFromDef(TemplateDefinition def) {
        String source = invokeStr(def, "source");
        String period = invokeStr(def, "period");
        List<TraitView> passives = invokeTraitList(def, "passives");
        List<TraitView> weaknesses = invokeTraitList(def, "weaknesses");
        List<TraitView> negatives = invokeTraitList(def, "negatives");
        List<TraitView> quirks = invokeTraitList(def, "quirks");
        return new Meta(source, period, 0.0, 0.0, 0.0, 0.0, passives, weaknesses, negatives, quirks);
    }

    private static List<TraitView> invokeTraitList(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object val = m.invoke(target);
            if (!(val instanceof Collection<?> col) || col.isEmpty()) {
                return List.of();
            }
            List<TraitView> out = new ArrayList<>(col.size());
            for (Object item : col) {
                if (item == null) {
                    continue;
                }
                String name = invokeStr(item, "name");
                if (name.isEmpty()) {
                    name = invokeStr(item, "type");
                }
                String desc = invokeStr(item, "description");
                if (!name.isEmpty() || !desc.isEmpty()) {
                    out.add(new TraitView(name, desc));
                }
            }
            return List.copyOf(out);
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    private static String invokeStr(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            return v instanceof String s ? s : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static double num(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return 0.0;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
