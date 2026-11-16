package com.rpgtemplate.forge.client.gui;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU path for the 万界 UI. One program ({@code rpgtemplate:rpg_fx}) renders the space backdrop,
 * card surfaces, rim sweeps and haze bands as single quads, replacing thousands of CPU fills
 * per frame.
 *
 * <p>Every draw method returns {@code true} when the GPU path handled the call. On any failure
 * (shader missing, uniform missing, exception, or a GL error on the first draws) the GPU path is
 * switched off for the session and the caller falls back to the CPU drawing code.
 *
 * <p>Set {@code -Drpgtemplate.noFxShader=true} to force the CPU path for A/B checks.
 *
 * <p>Srg notes: {@code GuiGraphics.pose()} = pose, {@code GuiGraphics.flush()} = flush,
 * {@code PoseStack.last()} = last, {@code Pose.pose()} = pose,
 * {@code Tesselator.getInstance()} = getInstance, {@code getBuilder()} = getBuilder,
 * {@code BufferBuilder.end} = end, {@code BufferUploader.drawWithShader} = drawWithShader,
 * {@code VertexConsumer.addVertex(Matrix4f,..)} = vertex,
 * {@code uv} = uv, {@code color(int..)} = color, {@code endVertex} = endVertex,
 * {@code ShaderInstance.getUniform} = getUniform, {@code Uniform.set(float..)} = set / set / set / set,
 * {@code DefaultVertexFormat.POSITION_TEX_COLOR} = POSITION_TEX_COLOR, {@code ResourceLocation.fromNamespaceAndPath} = tryBuild.
 */
@EventBusSubscriber(modid = "rpgtemplate", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FxShader {
    private static final Logger LOG = LoggerFactory.getLogger(FxShader.class);
    private static final ResourceLocation ID = ResourceLocation.tryBuild("rpgtemplate", "rpg_fx");
    /** Quad margin around a card, leaves room for bloom and the drop shadow. */
    private static final int MARGIN = 14;
    /** Time is wrapped so the float shader inputs keep precision for long sessions. */
    private static final double TIME_WRAP = 3600.0;
    /** Kill switch for A/B checks. */
    private static final boolean DISABLED = Boolean.getBoolean("rpgtemplate.noFxShader");
    /** GL error checks left after binding. They run on the first few draws only. */
    private static int glChecks;

    private static volatile ShaderInstance program;
    private static Uniform uSize;
    private static Uniform uInner;
    private static Uniform uRad;
    private static Uniform uFace;
    private static Uniform uAccent;
    private static Uniform uAccent2;
    private static Uniform uFx;
    private static Uniform uWave;
    private static Uniform uRipple;
    private static Uniform uMode;

    private FxShader() {
    }

    /** True while the program is bound, every uniform resolved, and no failure has occurred. */
    public static boolean ready() {
        return !DISABLED && program != null;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), ID, DefaultVertexFormat.POSITION_TEX_COLOR),
                FxShader::bind);
        } catch (Exception e) {
            program = null;
            LOG.warn("rpg_fx shader failed to load, using CPU fallback", e);
        }
    }

    private static void bind(ShaderInstance s) {
        Uniform size = s.getUniform("uSize");
        Uniform inner = s.getUniform("uInner");
        Uniform rad = s.getUniform("uRad");
        Uniform face = s.getUniform("uFace");
        Uniform accent = s.getUniform("uAccent");
        Uniform accent2 = s.getUniform("uAccent2");
        Uniform fx = s.getUniform("uFx");
        Uniform wave = s.getUniform("uWave");
        Uniform ripple = s.getUniform("uRipple");
        Uniform mode = s.getUniform("uMode");
        if (size == null || inner == null || rad == null || face == null || accent == null
            || accent2 == null || fx == null || wave == null || ripple == null || mode == null) {
            LOG.warn("rpg_fx uniform missing, using CPU fallback");
            program = null;
            return;
        }
        uSize = size;
        uInner = inner;
        uRad = rad;
        uFace = face;
        uAccent = accent;
        uAccent2 = accent2;
        uFx = fx;
        uWave = wave;
        uRipple = ripple;
        uMode = mode;
        glChecks = 3;
        program = s;
        LOG.info("rpg_fx shader bound");
    }

    /** Any failure turns the GPU path off for the rest of the session. Callers switch to CPU. */
    private static void disable(String what, Throwable t) {
        program = null;
        LOG.error("rpg_fx failed in {}, CPU fallback active for this session", what, t);
    }

    // ------------------------------------------------------------------ drawing

    /** Full-window space backdrop: nebula, starfield, dimensional ring, planet, shooting star. */
    public static boolean backdrop(GuiGraphics g, int w, int h, float[] ripple) {
        if (!ready()) {
            return false;
        }
        try {
            uSize.set(w, h);
            uInner.set(0f, 0f, w, h);
            uRad.set(0f, 0f, 0f, 0f);
            uFace.set(1f, 1f, 1f, 1f);
            uAccent.set(0.416f, 0.173f, 0.941f, 1f);
            uAccent2.set(0.110f, 0.820f, 0.960f, 1f);
            uFx.set(0f, 0f, 0f, 0f);
            uWave.set(wrapTime(), 0f, 0f, 0f);
            uRipple.set(ripple[0], ripple[1], ripple[2], ripple[3]);
            uMode.set(0f, 1f, 0f, 0f);
            draw(g, 0, 0, w, h);
            return true;
        } catch (Throwable t) {
            disable("backdrop", t);
            return false;
        }
    }

    /**
     * Card surface. The quad extends {@link #MARGIN} px beyond the card on every side so the
     * bloom and drop shadow have room. Caller draws text and brackets on top.
     */
    public static boolean card(GuiGraphics g, int x, int y, int w, int h, float alpha, int accent) {
        if (!ready()) {
            return false;
        }
        try {
            int qw = w + MARGIN * 2;
            int qh = h + MARGIN * 2;
            uSize.set(qw, qh);
            uInner.set(MARGIN, MARGIN, w, h);
            // corner radius, chamfer (top-right and bottom-left), border width, shadow strength
            uRad.set(2.5f, 8f, 1.4f, 0.30f);
            // glass face: dark translucent; the final alpha is applied by uMode.y
            uFace.set(rgbR(UiTheme.E_CARD), rgbG(UiTheme.E_CARD), rgbB(UiTheme.E_CARD), UiTheme.GLASS);
            uAccent.set(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.set(0.110f, 0.820f, 0.960f, 1f);
            // scanline strength, conic strength, grain, bloom
            uFx.set(0.04f, 0.9f, 0.018f, 0.22f);
            uWave.set(wrapTime(), 0f, 0f, 0f);
            uRipple.set(0f, 0f, 99f, 0f);
            uMode.set(1f, clamp01(alpha), 0f, 0f);
            draw(g, x - MARGIN, y - MARGIN, qw, qh);
            return true;
        } catch (Throwable t) {
            disable("card", t);
            return false;
        }
    }

    /**
     * Rim only: a conic sweep around the rectangle and scanlines, with a transparent face.
     * Replaces the CPU {@code Fx.conic} and {@code Fx.scanlines}.
     */
    public static boolean rim(GuiGraphics g, int x, int y, int w, int h, int accent, float conic,
                              float scan, float alpha) {
        if (!ready() || w <= 4 || h <= 4) {
            return false;
        }
        try {
            uSize.set(w, h);
            uInner.set(0f, 0f, w, h);
            uRad.set(0f, 0f, 1f, 0f);
            uFace.set(1f, 1f, 1f, 0f);
            uAccent.set(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.set(0.110f, 0.820f, 0.960f, 1f);
            uFx.set(clamp01(scan), clamp01(conic), 0f, 0f);
            uWave.set(wrapTime(), 0f, 0f, 0f);
            uRipple.set(0f, 0f, 99f, 0f);
            uMode.set(3f, clamp01(alpha), 0f, 0f);
            draw(g, x, y, w, h);
            return true;
        } catch (Throwable t) {
            disable("rim", t);
            return false;
        }
    }

    /** Horizontal haze band (header glow). The band is centred on the rect and spans half its height. */
    public static boolean haze(GuiGraphics g, int x, int y, int w, int h, int accent, float alpha) {
        if (!ready()) {
            return false;
        }
        try {
            uSize.set(w, h);
            uInner.set(0f, 0f, w, h);
            uRad.set(0f, 0f, 0f, 0f);
            uFace.set(1f, 1f, 1f, clamp01(alpha));
            uAccent.set(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.set(0.110f, 0.820f, 0.960f, 1f);
            uFx.set(0f, 0f, 0f, 0f);
            uWave.set(wrapTime(), 0f, 0f, 0f);
            uRipple.set(0f, 0f, 99f, 0f);
            uMode.set(2f, 1f, 0.5f, 0.5f);
            draw(g, x, y, w, h);
            return true;
        } catch (Throwable t) {
            disable("haze", t);
            return false;
        }
    }

    /** Ripple state for the backdrop: origin x, origin y, age in seconds, amplitude. */
    public static float[] ripple(float x, float y, float ageSeconds, float amplitude) {
        return new float[] {x, y, ageSeconds, amplitude};
    }

    private static void draw(GuiGraphics g, int x, int y, int w, int h) {
        // Flush any buffered GuiGraphics draws first so our immediate quad layers cleanly on top.
        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        buf.addVertex(m, x0, y0, 0f).setUv(0f, 0f).setColor(255, 255, 255, 255);
        buf.addVertex(m, x0, y1, 0f).setUv(0f, 1f).setColor(255, 255, 255, 255);
        buf.addVertex(m, x1, y1, 0f).setUv(1f, 1f).setColor(255, 255, 255, 255);
        buf.addVertex(m, x1, y0, 0f).setUv(1f, 0f).setColor(255, 255, 255, 255);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> program);
        try {
            // drawWithShader = BufferUploader.drawWithShader (binds rpg_fx & uploads matrices/uniforms).
            BufferUploader.drawWithShader(buf.buildOrThrow());
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
        if (glChecks > 0) {
            glChecks--;
            int err = GL11.glGetError();
            if (err != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("GL error 0x" + Integer.toHexString(err) + " after rpg_fx draw");
            }
        }
    }

    private static float wrapTime() {
        return (float) (UiTheme.seconds() % TIME_WRAP);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static float rgbR(int c) {
        return ((c >> 16) & 0xFF) / 255f;
    }

    private static float rgbG(int c) {
        return ((c >> 8) & 0xFF) / 255f;
    }

    private static float rgbB(int c) {
        return (c & 0xFF) / 255f;
    }
}
