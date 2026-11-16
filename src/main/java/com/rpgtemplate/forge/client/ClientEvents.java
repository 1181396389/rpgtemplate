package com.rpgtemplate.forge.client;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.client.hud.ChantOverlay;
import com.rpgtemplate.forge.client.hud.SystemHud;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate",
    value = {Dist.CLIENT}
)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post var0) {
        if (true) {
            ClientKeys.tick();
            SystemHud.tick();
            ChantOverlay.tick();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(LoggingOut var0) {
        ClientSystemData.clear();
        ClientKeys.reset();
        ChantOverlay.clear();
        RainbowEffect.clear();
        TemplateRegistry.client().replaceAll(List.of());
    }
}
