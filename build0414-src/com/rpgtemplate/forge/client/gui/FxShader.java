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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
 * <p>Srg notes: {@code GuiGraphics.pose()} = m_280168_, {@code PoseStack.last()} = m_85850_,
 * {@code Pose.pose()} = m_252922_, {@code Tesselator.getInstance()} = m_85913_,
 * {@code getBuilder()} = m_85915_, {@code BufferBuilder.end} = m_231175_,
 * {@code BufferUploader.drawWithShader} = m_231209_, {@code VertexConsumer.vertex(Matrix4f,..)} = m_252986_,
 * {@code uv} = m_7421_, {@code color(int..)} = m_6122_, {@code endVertex} = m_5752_,
 * {@code ShaderInstance.getUniform} = m_173348_, {@code Uniform.set(float..)} = m_5985_ / m_7971_ / m_5889_ / m_5805_,
 * {@code DefaultVertexFormat.POSITION_TEX_COLOR} = f_85819_, {@code ResourceLocation.fromNamespaceAndPath} = m_214293_.
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FxShader {
    private static final Logger LOG = LoggerFactory.getLogger(FxShader.class);
    private static final ResourceLocation ID = ResourceLocation.m_214293_("rpgtemplate", "rpg_fx");
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
                new ShaderInstance(event.getResourceProvider(), ID, DefaultVertexFormat.f_85819_),
                FxShader::bind);
        } catch (Exception e) {
            program = null;
            LOG.warn("rpg_fx shader failed to load, using CPU fallback", e);
        }
    }

    private static void bind(ShaderInstance s) {
        Uniform size = s.m_173348_("uSize");
        Uniform inner = s.m_173348_("uInner");
        Uniform rad = s.m_173348_("uRad");
        Uniform face = s.m_173348_("uFace");
        Uniform accent = s.m_173348_("uAccent");
        Uniform accent2 = s.m_173348_("uAccent2");
        Uniform fx = s.m_173348_("uFx");
        Uniform wave = s.m_173348_("uWave");
        Uniform ripple = s.m_173348_("uRipple");
        Uniform mode = s.m_173348_("uMode");
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
            uSize.m_7971_(w, h);
            uInner.m_5805_(0f, 0f, w, h);
            uRad.m_5805_(0f, 0f, 0f, 0f);
            uFace.m_5805_(1f, 1f, 1f, 1f);
            uAccent.m_5805_(0.416f, 0.173f, 0.941f, 1f);
            uAccent2.m_5805_(0.110f, 0.820f, 0.960f, 1f);
            uFx.m_5805_(0f, 0f, 0f, 0f);
            uWave.m_5805_(wrapTime(), 0f, 0f, 0f);
            uRipple.m_5805_(ripple[0], ripple[1], ripple[2], ripple[3]);
            uMode.m_5805_(0f, 1f, 0f, 0f);
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
            uSize.m_7971_(qw, qh);
            uInner.m_5805_(MARGIN, MARGIN, w, h);
            // corner radius, chamfer (top-right and bottom-left), border width, shadow strength
            uRad.m_5805_(2.5f, 8f, 1.4f, 0.30f);
            // glass face: dark translucent; the final alpha is applied by uMode.y
            uFace.m_5805_(rgbR(UiTheme.E_CARD), rgbG(UiTheme.E_CARD), rgbB(UiTheme.E_CARD), UiTheme.GLASS);
            uAccent.m_5805_(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.m_5805_(0.110f, 0.820f, 0.960f, 1f);
            // scanline strength, conic strength, grain, bloom
            uFx.m_5805_(0.04f, 0.9f, 0.018f, 0.22f);
            uWave.m_5805_(wrapTime(), 0f, 0f, 0f);
            uRipple.m_5805_(0f, 0f, 99f, 0f);
            uMode.m_5805_(1f, clamp01(alpha), 0f, 0f);
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
            uSize.m_7971_(w, h);
            uInner.m_5805_(0f, 0f, w, h);
            uRad.m_5805_(0f, 0f, 1f, 0f);
            uFace.m_5805_(1f, 1f, 1f, 0f);
            uAccent.m_5805_(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.m_5805_(0.110f, 0.820f, 0.960f, 1f);
            uFx.m_5805_(clamp01(scan), clamp01(conic), 0f, 0f);
            uWave.m_5805_(wrapTime(), 0f, 0f, 0f);
            uRipple.m_5805_(0f, 0f, 99f, 0f);
            uMode.m_5805_(3f, clamp01(alpha), 0f, 0f);
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
            uSize.m_7971_(w, h);
            uInner.m_5805_(0f, 0f, w, h);
            uRad.m_5805_(0f, 0f, 0f, 0f);
            uFace.m_5805_(1f, 1f, 1f, clamp01(alpha));
            uAccent.m_5805_(rgbR(accent), rgbG(accent), rgbB(accent), 1f);
            uAccent2.m_5805_(0.110f, 0.820f, 0.960f, 1f);
            uFx.m_5805_(0f, 0f, 0f, 0f);
            uWave.m_5805_(wrapTime(), 0f, 0f, 0f);
            uRipple.m_5805_(0f, 0f, 99f, 0f);
            uMode.m_5805_(2f, 1f, 0.5f, 0.5f);
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
        Matrix4f m = g.m_280168_().m_85850_().m_252922_();
        BufferBuilder buf = Tesselator.m_85913_().m_85915_();
        buf.m_166779_(VertexFormat.Mode.QUADS, DefaultVertexFormat.f_85819_);
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;
        buf.m_252986_(m, x0, y0, 0f).m_7421_(0f, 0f).m_6122_(255, 255, 255, 255).m_5752_();
        buf.m_252986_(m, x0, y1, 0f).m_7421_(0f, 1f).m_6122_(255, 255, 255, 255).m_5752_();
        buf.m_252986_(m, x1, y1, 0f).m_7421_(1f, 1f).m_6122_(255, 255, 255, 255).m_5752_();
        buf.m_252986_(m, x1, y0, 0f).m_7421_(1f, 0f).m_6122_(255, 255, 255, 255).m_5752_();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> program);
        BufferUploader.m_231209_(buf.m_231175_());
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
