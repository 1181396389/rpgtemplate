package com.rpgtemplate.forge.client;

import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.client.hud.ChantOverlay;
import com.rpgtemplate.forge.client.hud.SystemHud;
import com.rpgtemplate.forge.client.render.TechniqueEffectRenderer;
import com.rpgtemplate.forge.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(
    modid = "rpgtemplate",
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        ClientKeys.register(event);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(RpgTemplateMod.id("system_panel"), (gui, dt) -> SystemHud.render(gui, dt.getGameTimeDeltaPartialTick(false)));
        event.registerAboveAll(RpgTemplateMod.id("rewards"), (gui, dt) -> RainbowEffect.render(gui));
        event.registerAboveAll(RpgTemplateMod.id("chant"), (gui, dt) -> ChantOverlay.render(gui, dt.getGameTimeDeltaPartialTick(false)));
    }

    @SubscribeEvent
    public static void onRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TECHNIQUE_EFFECT.get(), TechniqueEffectRenderer::new);
    }
}
