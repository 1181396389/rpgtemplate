package com.rpgtemplate.nothingthere.client;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the 一无所有 body layer to every player skin renderer (default and slim). */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NtBodyClientSetup {
    private NtBodyClientSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            NtBodyLayer.attach(renderer);
        }
    }
}
