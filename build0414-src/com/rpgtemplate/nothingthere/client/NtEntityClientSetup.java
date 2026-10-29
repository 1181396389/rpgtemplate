package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 绑定一无所有实体的渲染器（Holle 骨刺）。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NtEntityClientSetup {
    private NtEntityClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NtEntities.HOLLE_SPIKE, HolleSpikeRenderer::new);
    }
}
