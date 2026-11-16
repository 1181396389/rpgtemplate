package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/** 绑定一无所有实体的渲染器（Holle 骨刺）。 */
@EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NtEntityClientSetup {
    private NtEntityClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NtEntities.HOLLE_SPIKE, HolleSpikeRenderer::new);
    }
}
