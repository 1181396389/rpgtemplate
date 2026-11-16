package com.rpgtemplate.nothingthere.client;
import net.minecraft.client.resources.PlayerSkin;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/** Adds the 一无所有 body layer to every player skin renderer (default and slim). */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NtBodyClientSetup {
    private NtBodyClientSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            NtBodyLayer.attach(renderer);
        }
    }
}
