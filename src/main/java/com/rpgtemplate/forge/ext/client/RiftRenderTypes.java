package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;

public final class RiftRenderTypes extends RenderType {
    private static RenderType glow;
    private static RenderType voidSheet;

    private RiftRenderTypes(String var1, VertexFormat var2, Mode var3, int var4, boolean var5, boolean var6, Runnable var7, Runnable var8) {
        super(var1, var2, var3, var4, var5, var6, var7, var8);
    }

    public static RenderType glow() {
        if (glow == null) {
            glow = create(
                "rpgtemplate_world_rift_glow",
                DefaultVertexFormat.POSITION_COLOR,
                Mode.QUADS,
                262144,
                false,
                false,
                CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            );
        }

        return glow;
    }

    public static RenderType voidSheet() {
        if (voidSheet == null) {
            voidSheet = create(
                "rpgtemplate_world_rift_void",
                DefaultVertexFormat.POSITION_COLOR,
                Mode.QUADS,
                131072,
                false,
                true,
                CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)
            );
        }

        return voidSheet;
    }
}
