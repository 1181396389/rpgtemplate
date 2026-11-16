package com.rpgtemplate.forge.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgtemplate.core.template.TemplateJsonParser;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.client.hud.ChantOverlay;
import com.rpgtemplate.forge.network.ChantPayload;
import com.rpgtemplate.forge.network.RainbowBurstPayload;
import com.rpgtemplate.forge.network.SyncSystemDataPayload;
import com.rpgtemplate.forge.network.SyncTemplatesPayload;
import java.util.ArrayList;
import java.util.Map.Entry;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void onSystemData(SyncSystemDataPayload var0) {
        try {
            ClientSystemData.update(var0.json());
        } catch (RuntimeException var2) {
            RpgTemplateMod.LOGGER.error("[RPG转生模板] 系统数据同步解析失败", var2);
        }
    }

    public static void onTemplates(SyncTemplatesPayload var0) {
        try {
            JsonObject var1 = JsonParser.parseString(var0.json()).getAsJsonObject();
            ArrayList var2 = new ArrayList();

            for (Entry var4 : var1.entrySet()) {
                var2.add(TemplateJsonParser.parse((String)var4.getKey(), ((JsonElement)var4.getValue()).getAsJsonObject()));
            }

            TemplateRegistry.client().replaceAll(var2);
        } catch (RuntimeException var5) {
            RpgTemplateMod.LOGGER.error("[RPG转生模板] 模板同步解析失败", var5);
        }
    }

    public static void onChant(ChantPayload var0) {
        ChantOverlay.accept(var0);
    }

    public static void onRainbow(RainbowBurstPayload var0) {
        RainbowEffect.start(var0.kind(), var0.accentColor());
    }
}
