package com.rpgtemplate.forge.mask.client;
import net.minecraft.client.resources.PlayerSkin;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/** 将认知阻碍面具 3D 渲染层挂载到所有玩家皮肤模型渲染器（default / slim）。 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PerceptionMaskClientSetup {
    private PerceptionMaskClientSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                PerceptionMaskLayer.attach(renderer);
            }
        }
    }
}
