package com.rpgtemplate.forge.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgtemplate.core.template.TemplateJsonParser;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.RpgTemplateMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class TemplateReloadListener extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "rpg_templates";
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, JsonObject> raw = Collections.emptyMap();

    public TemplateReloadListener() {
        super(GSON, "rpg_templates");
    }

    public static Map<String, JsonObject> raw() {
        return raw;
    }

    protected void apply(Map<ResourceLocation, JsonElement> var1, ResourceManager var2, ProfilerFiller var3) {
        ArrayList var4 = new ArrayList();
        LinkedHashMap var5 = new LinkedHashMap();

        for (Entry var7 : var1.entrySet()) {
            String var8 = ((ResourceLocation)var7.getKey()).toString();

            try {
                JsonObject var9 = ((JsonElement)var7.getValue()).getAsJsonObject();
                var4.add(TemplateJsonParser.parse(var8, var9));
                var5.put(var8, var9);
            } catch (Exception var10) {
                RpgTemplateMod.LOGGER.error("[RPG转生模板] 模板 {} 解析失败: {}", var8, var10.getMessage());
            }
        }

        List var11 = TemplateRegistry.get().replaceAll(var4);
        var11.forEach(var0 -> RpgTemplateMod.LOGGER.warn("[RPG转生模板] {}", var0));
        if (TemplateRegistry.get().find("rpgtemplate:traveler").isEmpty()) {
            RpgTemplateMod.LOGGER.warn("[RPG转生模板] 缺少基础模板 {}", "rpgtemplate:traveler");
        }

        raw = Collections.unmodifiableMap(var5);
        RpgTemplateMod.LOGGER.info("[RPG转生模板] 已加载 {} 个模板", var4.size());
    }
}
