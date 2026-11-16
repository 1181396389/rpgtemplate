package com.rpgtemplate.forge.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.SyncTemplatesPayload;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerPlayer;

public final class TemplateSync {
    private TemplateSync() {
    }

    public static void send(ServerPlayer var0) {
        JsonObject var1 = new JsonObject();

        for (Entry var3 : TemplateReloadListener.raw().entrySet()) {
            var1.add((String)var3.getKey(), (JsonElement)var3.getValue());
        }

        PacketDistributor.sendToPlayer(var0, new SyncTemplatesPayload(var1.toString()));
    }
}
