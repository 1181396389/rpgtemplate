package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

public final class ExtRenderTypes extends RenderType {
    public static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/effect/glow.png");
    private static RenderType fire;

    private ExtRenderTypes(String name, VertexFormat fmt, Mode mode, int bufSize, boolean crumble, boolean sort, Runnable setup, Runnable clear) {
        super(name, fmt, mode, bufSize, crumble, sort, setup, clear);
    }

    public static RenderType fire() {
        if (fire == null) {
            fire = create(
                "rpgtemplate_fuga_fire",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                Mode.QUADS,
                262144,
                false,
                false,
                CompositeState.builder()
                    .setShaderState(
                        new RenderStateShard.ShaderStateShard(
                            () -> ExtClientMod.fireShader != null ? ExtClientMod.fireShader : GameRenderer.getPositionTexColorShader()
                        )
                    )
                    .setTextureState(new RenderStateShard.TextureStateShard(GLOW, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            );
        }
        return fire;
    }
}
