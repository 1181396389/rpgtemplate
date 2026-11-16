package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 假面骑士 Cronus 自定义 3D 序列图集渲染类型：
 * <ul>
 *   <li>{@link #additive(ResourceLocation)}：加色辉光（发光罗马数字、放射翠绿闪电、天降落雷），无深度写入、双面可见、双线性平滑采样。</li>
 *   <li>{@link #translucent(ResourceLocation)}：透明混合（编年史选卡面板、3D 飞行卡带），保留本体实色与荧光边缘，无深度写入防图层裁切。</li>
 * </ul>
 */
public final class CronusRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> ADDITIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> PIXEL_TRANSLUCENT_CACHE = new ConcurrentHashMap<>();

    private CronusRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int bufSize,
                              boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setup, Runnable clear) {
        super(name, fmt, mode, bufSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** 加色发光渲染（用于发光时钟数字与翠绿闪电序列图集）。 */
    public static RenderType additive(ResourceLocation tex) {
        return ADDITIVE_CACHE.computeIfAbsent(tex, loc -> {
            CompositeState state = CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(loc, true, false))
                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY) // ADDITIVE_TRANSPARENCY
                .setCullState(RenderStateShard.NO_CULL) // NO_CULL
                .setWriteMaskState(RenderStateShard.COLOR_WRITE) // COLOR_WRITE
                .createCompositeState(false);
            return RenderType.create(
                "rpgtemplate_cronus_add_" + loc.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                false,
                state);
        });
    }

    /** 半透明实色渲染（用于编年史卡牌与飞行卡带序列图集）。 */
    public static RenderType translucent(ResourceLocation tex) {
        return TRANSLUCENT_CACHE.computeIfAbsent(tex, loc -> {
            CompositeState state = CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(loc, true, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY) // TRANSLUCENT_TRANSPARENCY
                .setCullState(RenderStateShard.NO_CULL) // NO_CULL
                .setWriteMaskState(RenderStateShard.COLOR_WRITE) // COLOR_WRITE
                .createCompositeState(false);
            return RenderType.create(
                "rpgtemplate_cronus_alpha_" + loc.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                false,
                state);
        });
    }

    /** 像素锐利半透明渲染（最近邻采样 blur=false，用于假面骑士 Ex-Aid 像素风能量道具硬币图标）。 */
    public static RenderType pixelTranslucent(ResourceLocation tex) {
        return PIXEL_TRANSLUCENT_CACHE.computeIfAbsent(tex, loc -> {
            CompositeState state = CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(loc, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY) // TRANSLUCENT_TRANSPARENCY
                .setCullState(RenderStateShard.NO_CULL) // NO_CULL
                .setWriteMaskState(RenderStateShard.COLOR_WRITE) // COLOR_WRITE
                .createCompositeState(false);
            return RenderType.create(
                "rpgtemplate_cronus_pix_" + loc.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                65536,
                false,
                false,
                state);
        });
    }
}
