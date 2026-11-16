package com.rpgtemplate.forge.ext.client;


import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.rpgtemplate.forge.ext.ExtRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import org.apache.logging.log4j.LogManager;

@EventBusSubscriber(
    modid = "rpgtemplate",
    bus = Bus.MOD,
    value = {Dist.CLIENT}
)
public final class ExtClientMod {
    public static ShaderInstance fireShader;
    public static KeyMapping RADIUS_UP;
    public static KeyMapping RADIUS_DOWN;

    private ExtClientMod() {
    }

    @SubscribeEvent
    public static void onRenderers(RegisterRenderers var0) {
        var0.registerEntityRenderer(ExtRegistry.FUGA_ARROW, FugaArrowRenderer::new);
        var0.registerEntityRenderer(ExtRegistry.FUGA_BLAST, FugaBlastRenderer::new);
        var0.registerEntityRenderer(ExtRegistry.SHRINE, ShrineRenderer::new);
    }

    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent var0) {
        try {
            var0.registerShader(
                new ShaderInstance(var0.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("rpgtemplate", "fuga_fire"), DefaultVertexFormat.POSITION_TEX_COLOR),
                var0x -> fireShader = var0x
            );
        } catch (Exception var2) {
            fireShader = null;
            LogManager.getLogger("rpgtemplate").error("Failed to load fuga_fire shader, falling back to vanilla", var2);
        }

        ShrineRenderer.reload();
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent var0) {
        RADIUS_UP = new KeyMapping("key.rpgtemplate.domain_radius_up", KeyConflictContext.IN_GAME, Type.KEYSYM, 93, "key.categories.rpgtemplate");
        RADIUS_DOWN = new KeyMapping("key.rpgtemplate.domain_radius_down", KeyConflictContext.IN_GAME, Type.KEYSYM, 91, "key.categories.rpgtemplate");
        var0.register(RADIUS_UP);
        var0.register(RADIUS_DOWN);
    }
}
