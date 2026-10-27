package com.rpgtemplate.forge.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rpgtemplate.core.ui.UiLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared visual language for every RPG screen and HUD element (v4).
 *
 * <p>Style: "次元 × 空间 × 维度". A cool starlight ground, stacked dimension planes
 * (offset violet layers under every card), isometric voxel cubes, orbit rings,
 * and rift seams. Bright surfaces and saturated accents, with ink-navy text.
 * Body text always reaches {@link #MIN_CONTRAST} against the card surface.
 *
 * <p>Callers keep using the legacy constants and {@link #palette(int)}: legacy
 * colours are resolved to ink tones by {@link UiLayout#color(int)} and then mapped
 * into this palette, so every existing screen restyles at once.
 */
public final class UiTheme {
    // ---- legacy constants (kept for binary compatibility; values are inlined by callers) ----
    public static final int BG = 593696;
    public static final int SURFACE = 1121586;
    public static final int RAISED = 1780806;
    public static final int LINE = 3492456;
    public static final int GOLD = 9362676;
    public static final int TEXT = 0xEEF4FF;
    public static final int MUTED = 10532045;
    public static final int FAINT = 7045535;
    public static final int HP = 15827355;
    public static final int MP = 7588341;
    public static final int SP = 9099960;
    public static final int XP = 12559095;
    public static final int DANGER = 16746392;
    public static final int VIOLET = 11442932;
    public static final int AMBER = 15520934;
    public static final int WHITE = 16777215;
    public static final int INK = 0x0D0B1E;
    public static final int VERMILION = 0xE0177A;
    public static final int MOTE = 0x9AA6C4;

    // ---- v4 palette: 次元 × 空间 × 维度 ----
    public static final int E_INK = 0xF1F4FF;   // primary text and light hairlines on glass
    public static final int E_PAPER = 0x07061A;   // deep surface
    public static final int E_CARD = 0x0C0A24;   // glass base (translucent when drawn)
    public static final int E_INSET = 0x1B1948;   // wells and tracks
    public static final int E_EDGE = 0x6F7ED0;   // hairlines
    public static final int E_MUTED = 0xB7C2EC;   // secondary text
    public static final int E_FAINT = 0x8C99D2;   // tertiary text
    public static final int E_RED = 0xD4127A;        // rift magenta (alert)
    public static final int E_DEEP_RED = 0xFF6FB6;   // rift magenta, text-safe on glass
    public static final int E_GOLD = 0xFFAA00;       // 维度 amber (highlight)
    public static final int E_GOLD_TEXT = 0xFFCB6B;   // amber, text-safe on glass
    public static final int E_VIOLET = 0x9B7BFF;   // dimension violet
    public static final int E_CYAN = 0x45E3FF;   // space cyan
    public static final int E_JADE = 0x5CF2CC;   // ready / stable, text-safe on glass
    public static final int E_INDIGO = 0x7FB2FF;   // space blue (MP)
    public static final int E_WHITE = 0xFFFFFF;
    public static final int E_SHADOW = 0x04030D;     // true dark, for shadows and vignettes
    /** Opacity of a glass card face. Low enough that the starfield reads through the panel. */
    public static final float GLASS = 0.66f;

    /** Minimum contrast ratio for body text on {@link #E_CARD}. */
    public static final float MIN_CONTRAST = 7.0f;

    private static final long EPOCH = System.nanoTime();
    private static final Map<Long, Motion> HOVERS = new LinkedHashMap<>();
    private static final Map<Long, Motion> SMOOTH = new LinkedHashMap<>();
    private static final ArrayDeque<Float> ALPHA_STACK = new ArrayDeque<>();
    private static final Map<Integer, Integer> READABLE = new HashMap<>();
    private static final Map<Integer, Integer> INK_TO_E = new HashMap<>();
    private static float pulseX;
    private static float pulseY;
    private static long pulseNanos;

    static {
        put(0x1f1b16, E_INK);
        put(0x5e5649, E_MUTED);
        put(0x7a7163, E_FAINT);
        put(0x8c8273, E_EDGE);
        put(0xb8332a, E_RED);
        put(0x9e1b14, E_DEEP_RED);
        put(0x8a5f12, E_GOLD_TEXT);
        put(0x2f6b58, E_JADE);
        put(0x2e4a7a, E_INDIGO);
        put(0x6b3d6e, E_VIOLET);
        put(0xf4eee1, E_PAPER);
        put(0xfbf7ee, E_CARD);
        put(0xeae2cf, E_INSET);
        put(0xdadbdb, 0xE0E4F4);
        put(0xdae1d6, 0xDDE8F4);
        put(0xe4d9da, 0xEBE2F6);
        put(0xe9dfcb, 0xEDE6F8);
        put(0xf0d8cf, 0xF2E2F0);
        put(0xf0e9d9, 0xF0EEFA);
        put(0xffffff, E_WHITE);
    }

    private UiTheme() {
    }

    private static void put(int ink, int e) {
        INK_TO_E.put(ink, e);
    }

    /** v4 is a single light theme. Kept for source compatibility. */
    public static boolean dark() {
        return false;
    }

    public static int pick(int light, int dark) {
        return light;
    }

    static int darkOf(int argb) {
        return argb;
    }

    /** Resolves any legacy or v4 colour to the v4 palette. Idempotent. */
    public static int palette(int color) {
        int ink = UiLayout.color(color) & 0xFFFFFF;
        Integer mapped = INK_TO_E.get(ink);
        return mapped == null ? ink : mapped;
    }

    public static int mix(int a, int b, float t) {
        float k = clamp01(t);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public static int alpha(float f, int color) {
        int a = Math.round(clamp01(f) * 255f);
        return (a << 24) | (palette(color) & 0xFFFFFF);
    }

    static float luminance(int rgb) {
        float r = linear(((rgb >> 16) & 255) / 255f);
        float g = linear(((rgb >> 8) & 255) / 255f);
        float b = linear((rgb & 255) / 255f);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float linear(float c) {
        return c <= 0.03928f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    static float contrast(int a, int b) {
        float la = luminance(a);
        float lb = luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    /** Readability floor: pull text toward ink until it reaches {@link #MIN_CONTRAST} on card. */
    static int readable(int rgb) {
        int key = rgb & 0xFFFFFF;
        Integer cached = READABLE.get(key);
        if (cached != null) {
            return cached;
        }
        int out = E_INK;
        for (int i = 0; i <= 20; i++) {
            int candidate = mix(key, E_INK, i / 20f);
            if (contrast(candidate, E_CARD) >= MIN_CONTRAST) {
                out = candidate;
                break;
            }
        }
        if (READABLE.size() > 512) {
            READABLE.clear();
        }
        READABLE.put(key, out);
        return out;
    }

    // ------------------------------------------------------------- animation

    public static double seconds() {
        if (UiPreferences.reducedMotion) {
            return 0.0;
        }
        return (double) (System.nanoTime() - EPOCH) / 1.0e9;
    }

    public static float ease(float t) {
        float k = 1f - clamp01(t);
        return 1f - k * k * k;
    }

    public static float enter(long startNanos) {
        if (UiPreferences.reducedMotion) {
            return 1f;
        }
        return ease((float) ((System.nanoTime() - startNanos) / 1.0e9 / 0.28));
    }

    public static float approach(long key, float target) {
        long now = System.nanoTime();
        Motion m = SMOOTH.get(key);
        if (m == null) {
            if (SMOOTH.size() > 256) {
                SMOOTH.clear();
            }
            m = new Motion(now);
            m.value = target;
            SMOOTH.put(key, m);
            return target;
        }
        if (UiPreferences.reducedMotion) {
            m.value = target;
        } else {
            float dt = clamp01((float) ((now - m.last) / 1.0e9));
            m.value += (target - m.value) * (1f - (float) Math.exp(-dt * 16f));
        }
        m.last = now;
        return m.value;
    }

    public static float hover(int x, int y, int w, int h, boolean hovered) {
        long key = ((long) x & 0xFFFFL) << 48 | ((long) y & 0xFFFFL) << 32 | ((long) w & 0xFFFFL) << 16 | ((long) h & 0xFFFFL);
        float target = hovered ? 1f : 0f;
        if (UiPreferences.reducedMotion) {
            return target;
        }
        return approach(key ^ 0x5A5A5A5AL, target);
    }

    public static void pulse(double x, double y) {
        pulseX = (float) x;
        pulseY = (float) y;
        pulseNanos = System.nanoTime();
    }

    /** Expanding violet ring from the last {@link #pulse}. */
    public static void ripple(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (UiPreferences.reducedMotion || pulseNanos == 0L) {
            return;
        }
        float t = (float) ((System.nanoTime() - pulseNanos) / 1.0e9 / 0.42);
        if (t < 0f || t > 1f) {
            return;
        }
        int grow = Math.round(ease(t) * 12f);
        ring(g, x - grow, y - grow, w + grow * 2, h + grow * 2, alpha((1f - t) * 0.7f, color));
    }

    public static void enterPush(GuiGraphics g, float progress, float offsetY) {
        float p = clamp01(progress);
        ALPHA_STACK.push(RenderSystem.getShaderColor()[3]);
        RenderSystem.setShaderColor(1f, 1f, 1f, p);
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_(0f, (1f - ease(p)) * offsetY, 0f);
    }

    public static void enterPop(GuiGraphics g) {
        g.m_280168_().m_85849_();
        float restore = ALPHA_STACK.isEmpty() ? 1f : ALPHA_STACK.pop();
        RenderSystem.setShaderColor(1f, 1f, 1f, restore);
    }

    // ------------------------------------------------------------ primitives

    /** Scanline polygon fill. */
    public static void poly(GuiGraphics g, int[] xs, int[] ys, int argb) {
        int n = xs.length;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y : ys) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        for (int y = minY; y < maxY; y++) {
            double sy = y + 0.5;
            List<Double> cross = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double y1 = ys[i];
                double y2 = ys[j];
                if ((y1 <= sy && y2 > sy) || (y2 <= sy && y1 > sy)) {
                    double t = (sy - y1) / (y2 - y1);
                    cross.add(xs[i] + t * (xs[j] - xs[i]));
                }
            }
            Collections.sort(cross);
            for (int k = 0; k + 1 < cross.size(); k += 2) {
                int x1 = (int) Math.round(cross.get(k));
                int x2 = (int) Math.round(cross.get(k + 1));
                if (x2 > x1) {
                    g.m_280509_(x1, y, x2, y + 1, argb);
                }
            }
        }
    }

    /** Rectangle with the top-right corner cut by {@code cut} pixels. */
    private static void cutRect(GuiGraphics g, int x, int y, int w, int h, int cut, int argb) {
        int c = Math.min(cut, Math.min(w, h) / 3);
        if (c <= 0) {
            g.m_280509_(x, y, x + w, y + h, argb);
            return;
        }
        poly(g, new int[] {x, x + w - c, x + w, x + w, x}, new int[] {y, y, y + c, y + h, y + h}, argb);
    }

    /**
     * Glass card: a translucent dark face, a hairline edge and a single violet top rule.
     * No solid blocks. The shader draws it when available; the CPU fallback matches it.
     */
    public static void card(GuiGraphics g, int x, int y, int w, int h, float f) {
        float a = clamp01(f);
        if (w <= 8 || h <= 8) {
            return;
        }
        float breathe = breathe(1.6, x * 0.05 + y * 0.03);
        if (!FxShader.card(g, x, y, w, h, a, E_VIOLET)) {
            cardFallback(g, x, y, w, h, a, breathe);
        }
        trim(g, x, y, w, h, a, breathe);
    }

    /** CPU glass surface used when the rpg_fx shader is unavailable. */
    private static void cardFallback(GuiGraphics g, int x, int y, int w, int h, float a, float breathe) {
        int cut = 8;
        cutRect(g, x + 1, y + 1, w - 2, h - 2, cut - 1, alpha(a * GLASS, E_CARD));
        ring(g, x, y, w, h, alpha(a * (0.55f + 0.1f * breathe), E_EDGE));
        g.m_280509_(x + 2, y + 2, x + w - cut - 1, y + 3, alpha(a * 0.9f, E_VIOLET));
    }

    /**
     * Frame: hairline outline, cyan corner ticks, and a faint glass tint. No solid fill.
     * Used for sub-regions (stat tiles, detail strips) so the panel does not read as stacked blocks.
     */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, float f) {
        float a = clamp01(f);
        if (w <= 8 || h <= 8) {
            return;
        }
        g.m_280509_(x + 1, y + 1, x + w - 1, y + h - 1, alpha(a * 0.35f, E_CARD));
        ring(g, x, y, w, h, alpha(a * 0.45f, E_EDGE));
        int t = Math.min(6, Math.min(w, h) / 3);
        int c = alpha(a, E_CYAN);
        g.m_280509_(x, y, x + t, y + 1, c);
        g.m_280509_(x, y, x + 1, y + t, c);
        g.m_280509_(x + w - t, y, x + w, y + 1, c);
        g.m_280509_(x + w - 1, y, x + w, y + t, c);
        g.m_280509_(x, y + h - 1, x + t, y + h, c);
        g.m_280509_(x, y + h - t, x + 1, y + h, c);
        g.m_280509_(x + w - t, y + h - 1, x + w, y + h, c);
        g.m_280509_(x + w - 1, y + h - t, x + w, y + h, c);
    }

    /** Corner brackets and the bottom seam. Always CPU: a few fills, drawn on top of either surface. */
    private static void trim(GuiGraphics g, int x, int y, int w, int h, float a, float breathe) {
        if (h >= 30) {
            int bc = alpha(a * 0.8f, E_CYAN);
            g.m_280509_(x + 4, y + 6, x + 9, y + 7, bc);
            g.m_280509_(x + 4, y + 6, x + 5, y + 11, bc);
            g.m_280509_(x + 4, y + h - 7, x + 9, y + h - 6, bc);
            g.m_280509_(x + 4, y + h - 12, x + 5, y + h - 6, bc);
        }
        seam(g, x + 6, y + h - 4, w - 12, alpha(a * (0.3f + 0.2f * breathe), E_CYAN));
    }

    /** Dimensional seam: a 1px rift with small diamond nodes at both ends and centre. */
    public static void seam(GuiGraphics g, int x, int y, int w, int argb) {
        if (w <= 0) {
            return;
        }
        g.m_280509_(x, y, x + w, y + 1, argb);
        int[] nodes = {x, x + w / 2, x + w - 1};
        for (int nx : nodes) {
            g.m_280509_(nx - 1, y - 1, nx + 2, y, argb);
            g.m_280509_(nx - 1, y + 1, nx + 2, y + 2, argb);
            g.m_280509_(nx, y - 2, nx + 1, y - 1, argb);
            g.m_280509_(nx, y + 2, nx + 1, y + 3, argb);
        }
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.m_280509_(x, y, x + w, y + h, alpha(1f, E_INSET));
        ring(g, x, y, w, h, alpha(0.9f, E_EDGE));
    }

    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        ring(g, x, y, w, h, alpha(1f, color));
    }

    private static void ring(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.m_280509_(x, y, x + w, y + 1, argb);
        g.m_280509_(x, y + h - 1, x + w, y + h, argb);
        g.m_280509_(x, y, x + 1, y + h, argb);
        g.m_280509_(x + w - 1, y, x + w, y + h, argb);
    }

    /**
     * Button: no fill block. Idle is a dim hairline; hover adds a faint tint and a brighter edge;
     * active adds an accent tint, an accent edge and a 2 px accent underline. A sheen crosses
     * hovered and active buttons.
     */
    public static void buttonSurface(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active, int accent) {
        int acc = palette(accent);
        if (active) {
            g.m_280509_(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0.16f, acc));
            ring(g, x, y, w, h, alpha(0.9f, acc));
            g.m_280509_(x + 1, y + h - 2, x + w - 1, y + h, alpha(1f, acc));
        } else if (hovered) {
            g.m_280509_(x + 1, y + 1, x + w - 1, y + h - 1, alpha(0.08f, E_INK));
            ring(g, x, y, w, h, alpha(0.75f, E_EDGE));
        } else {
            ring(g, x, y, w, h, alpha(0.35f, E_EDGE));
        }
        if ((hovered || active) && w > 14 && h > 6) {
            double t = seconds();
            int span = w + 20;
            int sx = x - 10 + (int) (((t * 0.8 + x * 0.011) % 1.0) * span);
            int l = Math.max(x + 2, sx);
            int r = Math.min(x + w - 2, sx + 10);
            if (r > l) {
                g.m_280509_(l, y + 2, r, y + h - 3, alpha(active ? 0.18f : 0.08f, active ? E_WHITE : E_CYAN));
            }
        }
    }

    public static void text(GuiGraphics g, Font font, String s, int x, int y, int c) {
        if (s == null || s.isEmpty()) {
            return;
        }
        g.m_280056_(font, s, x, y, 0xFF000000 | readable(palette(c)), false);
    }

    /** Trims {@code s} with an ellipsis so it fits within {@code maxWidth} pixels. */
    public static String fit(Font font, String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (font.m_92895_(s) <= maxWidth) {
            return s;
        }
        String ell = "...";
        int budget = maxWidth - font.m_92895_(ell);
        if (budget <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = out.toString() + s.charAt(i);
            if (font.m_92895_(next) > budget) {
                break;
            }
            out.append(s.charAt(i));
        }
        return out + ell;
    }

    public static void right(GuiGraphics g, Font font, String s, int rightX, int y, int c) {
        if (s == null || s.isEmpty()) {
            return;
        }
        text(g, font, s, rightX - font.m_92895_(s), y, c);
    }

    /** Keycap with a visible 3D lip. Label is drawn in ink on the cap face. */
    public static void key(GuiGraphics g, Font font, String label, int x, int y) {
        String s = label == null ? "" : label;
        int w = Math.max(12, font.m_92895_(s) + 4);
        int h = 11;
        g.m_280509_(x, y, x + w, y + h, alpha(1f, E_EDGE));
        g.m_280509_(x + 1, y + 1, x + w - 1, y + h - 3, alpha(1f, E_CARD));
        g.m_280509_(x + 1, y + h - 3, x + w - 1, y + h - 2, alpha(1f, E_VIOLET));
        g.m_280056_(font, s, x + 2, y + 2, 0xFF000000 | E_INK, false);
    }

    /** Segmented track with a violet-to-colour fill proportion. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, double value, double max, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.m_280509_(x, y, x + w, y + h, alpha(1f, E_INSET));
        ring(g, x, y, w, h, alpha(0.8f, E_EDGE));
        double ratio = max <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / max));
        int fill = (int) Math.round((w - 2) * ratio);
        if (fill > 0) {
            g.m_280509_(x + 1, y + 1, x + 1 + fill, y + h - 1, alpha(1f, color));
            g.m_280509_(x + 1, y + 1, x + 1 + fill, y + 2, alpha(0.5f, E_WHITE));
        }
    }

    /** Voxel segment: a lit colour block with a bright top lip; unlit blocks stay pale. */
    public static void block(GuiGraphics g, int x, int y, int w, int h, int color, float lit) {
        int c = palette(color);
        float a = clamp01(lit);
        if (a <= 0.13f) {
            g.m_280509_(x, y, x + w, y + h, alpha(1f, E_INSET));
            return;
        }
        g.m_280509_(x, y, x + w, y + h, alpha(a, c));
        g.m_280509_(x, y, x + w, y + 1, alpha(a * 0.6f, E_WHITE));
    }

    /** Isometric voxel cube, three faces: lit top, base side, shaded side. */
    public static void cube(GuiGraphics g, int x, int y, int s, int color) {
        int c = palette(color);
        int top = mix(c, E_WHITE, 0.45f);
        int right = mix(c, E_SHADOW, 0.28f);
        int half = s / 2;
        int q = Math.max(1, s / 4);
        int tq = Math.max(1, s * 3 / 4);
        poly(g, new int[] {x + half, x + s, x + half, x}, new int[] {y, y + q, y + half, y + q}, alpha(1f, top));
        poly(g, new int[] {x, x + half, x + half, x}, new int[] {y + q, y + half, y + s, y + tq}, alpha(1f, c));
        poly(g, new int[] {x + half, x + s, x + s, x + half}, new int[] {y + half, y + q, y + tq, y + s}, alpha(1f, right));
    }

    /** Thin straight line of any slope, 1px wide. */
    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int c = alpha(1f, color);
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            g.m_280509_(x1, y1, x1 + 1, y1 + 1, c);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int px = (int) Math.round(x1 + (x2 - x1) * t);
            int py = (int) Math.round(y1 + (y2 - y1) * t);
            g.m_280509_(px, py, px + 1, py + 1, c);
        }
    }

    /** Orbit: dots on a tilted ellipse (space), slowly rotating with {@code angle}. */
    public static void orbit(GuiGraphics g, int cx, int cy, int r, int color, float f, double angle) {
        int c = palette(color);
        double tilt = 0.42;
        for (int i = 0; i < 24; i++) {
            double a = angle + i * Math.PI / 12.0;
            int px = (int) Math.round(cx + Math.cos(a) * r);
            int py = (int) Math.round(cy + Math.sin(a) * r * tilt);
            float k = (i % 6 == 0) ? 1f : 0.4f;
            g.m_280509_(px, py, px + 1, py + 1, alpha(clamp01(f) * k, c));
        }
    }

    /** Level emblem: a space ring around a gold dimensional core, with a cyan satellite. */
    public static void emblem(GuiGraphics g, int x, int y, int color) {
        int c = palette(color);
        int ring = alpha(1f, E_VIOLET);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24.0;
            int px = x + 4 + (int) Math.round(Math.cos(a) * 4.0);
            int py = y + 4 + (int) Math.round(Math.sin(a) * 4.0);
            g.m_280509_(px, py, px + 1, py + 1, ring);
        }
        g.m_280509_(x + 2, y + 3, x + 7, y + 5, alpha(1f, c));
        g.m_280509_(x + 3, y + 2, x + 6, y + 6, alpha(1f, c));
        g.m_280509_(x + 6, y + 0, x + 8, y + 2, alpha(1f, E_CYAN));
    }

    /**
     * Animated backdrop: a sky gradient, a perspective floor whose rows travel toward the
     * viewer, a tilted dimensional ring with depth-shaded nodes, parallax stars, a slow scan
     * line and a faint vignette. Every motion reads {@link #seconds()}, so reduced motion freezes it.
     */
    public static void backdrop(GuiGraphics g, int w, int h) {
        if (FxShader.backdrop(g, w, h, rippleState())) {
            return;
        }
        backdropFallback(g, w, h);
    }

    /** Ripple state for the shader backdrop: origin, age in seconds, amplitude. */
    private static float[] rippleState() {
        float age = pulseNanos == 0L ? 99f : (float) ((System.nanoTime() - pulseNanos) / 1e9);
        float amp = age < 1.5f ? 1f - age / 1.5f : 0f;
        return FxShader.ripple(pulseX, pulseY, age, amp);
    }

    /** CPU backdrop used when the rpg_fx shader is unavailable. */
    private static void backdropFallback(GuiGraphics g, int w, int h) {
        double t = seconds();
        int horizon = h * 58 / 100;
        int bands = 12;
        for (int i = 0; i < bands; i++) {
            int y0 = i * h / bands;
            int y1 = (i + 1) * h / bands;
            g.m_280509_(0, y0, w, y1, alpha(1f, mix(0x0C0926, 0x26166B, i / (float) (bands - 1))));
        }
        g.m_280509_(0, horizon, w, h, alpha(1f, 0x1A1148));

        // perspective floor: horizontal rows drift toward the viewer, rays fan out from the horizon
        double travel = (t * 0.32) % 1.0;
        int rows = 12;
        for (int k = 0; k < rows; k++) {
            double z = (k + travel) / rows;
            int y = horizon + (int) Math.round(z * z * (h - horizon));
            if (y <= horizon || y >= h) {
                continue;
            }
            g.m_280509_(0, y, w, y + 1, alpha((float) (0.10 + 0.30 * z * z), E_VIOLET));
        }
        int cx = w / 2;
        int rayColour = mix(0xE2E6F8, E_VIOLET, 0.16f);
        for (int i = -8; i <= 8; i++) {
            line(g, cx, horizon, cx + i * w / 8, h - 1, rayColour);
        }

        // dimensional ring: depth decides brightness, every sixth node is a larger cyan waypoint
        int rcy = horizon - h / 6;
        double rx = Math.min(w, h) * 0.5;
        double ry = rx * 0.28;
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI / 36.0 + t * 0.22;
            double s = Math.sin(a);
            int px = cx + (int) Math.round(Math.cos(a) * rx);
            int py = rcy + (int) Math.round(s * ry);
            boolean major = i % 6 == 0;
            float depth = (float) (0.5 + 0.5 * s);
            int sz = major ? 2 : 1;
            g.m_280509_(px, py, px + sz, py + sz, alpha(0.22f + 0.6f * depth, major ? E_CYAN : E_VIOLET));
        }

        // parallax stars: three layers drift at different speeds and twinkle out of phase
        for (int i = 0; i < 90; i++) {
            int layer = i % 3;
            double speed = 3.0 + layer * 5.0;
            double x = (rnd(i * 2 + 1) * w - t * speed) % w;
            if (x < 0) {
                x += w;
            }
            int y = (int) (rnd(i * 2 + 2) * horizon);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2 + layer * 0.7) + i * 1.7);
            int s = layer == 2 ? 2 : 1;
            g.m_280509_((int) x, y, (int) x + s, y + s,
                alpha(0.2f + 0.5f * tw * (layer + 1) / 3f, layer == 1 ? E_CYAN : E_VIOLET));
        }

        // slow scan line and a faint vignette
        int scan = (int) ((t * 26.0) % (h + 40)) - 20;
        for (int k = 0; k < 6; k++) {
            g.m_280509_(0, scan + k, w, scan + k + 1, alpha(0.07f * (1f - k / 6f), E_CYAN));
        }
        for (int k = 0; k < 10; k++) {
            int c = alpha(0.035f * (1f - k / 10f), E_SHADOW);
            g.m_280509_(0, k, w, k + 1, c);
            g.m_280509_(0, h - 1 - k, w, h - k, c);
            g.m_280509_(k, 0, k + 1, h, c);
            g.m_280509_(w - 1 - k, 0, w - k, h, c);
        }
    }

    /** Deterministic pseudo-random value in [0, 1) for a given integer seed. */
    static float rnd(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    /** 0..1 sine pulse. Holds at 0.5 when reduced motion is on. */
    public static float breathe(double speed, double phase) {
        if (UiPreferences.reducedMotion) {
            return 0.5f;
        }
        return 0.5f + 0.5f * (float) Math.sin(seconds() * speed + phase);
    }

    /** Dashed outline that crawls clockwise. Marks ready and selected states. */
    public static void marching(GuiGraphics g, int x, int y, int w, int h, int color, float f) {
        if (w <= 4 || h <= 4) {
            return;
        }
        int c = alpha(f, color);
        int off = (int) (seconds() * 9.0) % 3;
        int perim = 2 * (w + h);
        for (int p = 0; p < perim; p += 2) {
            if ((p / 2 + off) % 3 == 0) {
                continue;
            }
            int px;
            int py;
            if (p < w) {
                px = x + p;
                py = y;
            } else if (p < w + h) {
                px = x + w - 1;
                py = y + (p - w);
            } else if (p < 2 * w + h) {
                px = x + w - 1 - (p - w - h);
                py = y + h - 1;
            } else {
                px = x;
                py = y + h - 1 - (p - 2 * w - h);
            }
            g.m_280509_(px, py, px + 1, py + 1, c);
        }
    }

    public static void revealHalo(GuiGraphics g, int x, int y, int w, int h, float f, float f2) {
        float a = clamp01(f) * (1f - clamp01(f2));
        if (a <= 0f) {
            return;
        }
        for (int k = 1; k <= 3; k++) {
            ring(g, x - k * 2, y - k * 2, w + k * 4, h + k * 4, alpha(a * (0.6f - k * 0.15f), E_VIOLET));
        }
    }

    // ------------------------------------------------------------- particles

    private static final int MOTE_CAP = 224;
    private static final float[] MOTE_X = new float[MOTE_CAP];
    private static final float[] MOTE_Y = new float[MOTE_CAP];
    private static final float[] MOTE_VX = new float[MOTE_CAP];
    private static final float[] MOTE_VY = new float[MOTE_CAP];
    private static final float[] MOTE_G = new float[MOTE_CAP];
    private static final float[] MOTE_LIFE = new float[MOTE_CAP];
    private static final float[] MOTE_MAX = new float[MOTE_CAP];
    private static final float[] MOTE_ALPHA = new float[MOTE_CAP];
    private static final float[] MOTE_SIZE = new float[MOTE_CAP];
    private static final int[] MOTE_COLOUR = new int[MOTE_CAP];
    private static final java.util.Random RNG = new java.util.Random(0x6A1F2B3C4D5E6F70L);
    private static int moteCursor;
    private static double moteClock = -1.0;

    private static int nextMote() {
        int s = moteCursor;
        moteCursor = (moteCursor + 1) % MOTE_CAP;
        return s;
    }

    /** Burst of particles that fly out, slow down and fall. Used for clicks and tab changes. */
    public static void spawn(float x, float y, int color, int count, float speed) {
        if (UiPreferences.reducedMotion) {
            return;
        }
        int c = palette(color);
        for (int i = 0; i < count; i++) {
            int s = nextMote();
            double a = RNG.nextDouble() * Math.PI * 2.0;
            float v = speed * (0.35f + RNG.nextFloat() * 0.9f);
            MOTE_X[s] = x;
            MOTE_Y[s] = y;
            MOTE_VX[s] = (float) Math.cos(a) * v;
            MOTE_VY[s] = (float) Math.sin(a) * v - speed * 0.4f;
            MOTE_G[s] = 110f;
            MOTE_LIFE[s] = 0f;
            MOTE_MAX[s] = 0.45f + RNG.nextFloat() * 0.6f;
            MOTE_ALPHA[s] = 0.95f;
            MOTE_SIZE[s] = RNG.nextFloat() < 0.3f ? 2f : 1f;
            MOTE_COLOUR[s] = c;
        }
    }

    /** Sparse rising dust inside a rect. {@code rate} is the chance per frame of spawning one mote. */
    public static void dust(int x, int y, int w, int h, float rate) {
        if (UiPreferences.reducedMotion || w <= 0 || h <= 0 || RNG.nextFloat() > rate) {
            return;
        }
        int s = nextMote();
        MOTE_X[s] = x + RNG.nextFloat() * w;
        MOTE_Y[s] = y + h - 1;
        MOTE_VX[s] = (RNG.nextFloat() - 0.5f) * 3f;
        MOTE_VY[s] = -(6f + RNG.nextFloat() * 10f);
        MOTE_G[s] = 0f;
        MOTE_LIFE[s] = 0f;
        MOTE_MAX[s] = 3f + RNG.nextFloat() * 3f;
        MOTE_ALPHA[s] = 0.5f;
        MOTE_SIZE[s] = RNG.nextFloat() < 0.2f ? 2f : 1f;
        MOTE_COLOUR[s] = RNG.nextBoolean() ? E_CYAN : E_VIOLET;
    }

    /** Advances and draws every live particle. Call once per frame, after the content. */
    public static void motes(GuiGraphics g) {
        double now = seconds();
        float dt = moteClock < 0.0 ? 0f : (float) Math.min(0.1, now - moteClock);
        moteClock = now;
        float drag = (float) Math.exp(-3.0 * dt);
        for (int i = 0; i < MOTE_CAP; i++) {
            if (MOTE_MAX[i] <= 0f) {
                continue;
            }
            MOTE_LIFE[i] += dt;
            if (MOTE_LIFE[i] >= MOTE_MAX[i]) {
                MOTE_MAX[i] = 0f;
                continue;
            }
            MOTE_VX[i] *= drag;
            MOTE_VY[i] = MOTE_VY[i] * drag + MOTE_G[i] * dt;
            MOTE_X[i] += MOTE_VX[i] * dt;
            MOTE_Y[i] += MOTE_VY[i] * dt;
            float k = MOTE_LIFE[i] / MOTE_MAX[i];
            float fadeIn = Math.min(1f, k * 12f);
            float a = MOTE_ALPHA[i] * (1f - k) * fadeIn;
            int px = Math.round(MOTE_X[i]);
            int py = Math.round(MOTE_Y[i]);
            int sz = (int) MOTE_SIZE[i];
            g.m_280509_(px, py, px + sz, py + sz, alpha(a, MOTE_COLOUR[i]));
        }
    }

    // ------------------------------------------------------------- title

    /** Title with a chromatic split (cyan and violet ghosts) and a short jitter every few seconds. */
    public static void titleGlitch(GuiGraphics g, Font font, String s, int x, int y, int color) {
        if (s == null || s.isEmpty()) {
            return;
        }
        double ph = seconds() % 5.3;
        int jitter = ph < 0.10 ? 2 : (ph > 0.16 && ph < 0.20 ? -1 : 0);
        int split = 1 + (int) (breathe(1.3, 0.0) * 1.5);
        g.m_280056_(font, s, x - split, y, 0x6600B3D9, false);
        g.m_280056_(font, s, x + split, y, 0x666A2CF0, false);
        g.m_280056_(font, s, x + jitter, y, 0xFF000000 | readable(palette(color)), false);
    }

    // ------------------------------------------------------------- foil

    /** Diagonal light band that crawls across a card, tinted by its quality colour. */
    public static void foil(GuiGraphics g, int x, int y, int w, int h, int color, float phase) {
        if (w <= 8 || h <= 8 || UiPreferences.reducedMotion) {
            return;
        }
        int c = palette(color);
        int span = w + h / 3 + 12;
        int sx = x - h / 3 - 6 + (int) ((((seconds() * 0.22) + phase) % 1.0) * span);
        for (int yy = y + 6; yy < y + h - 3; yy++) {
            int bx = sx + (yy - y) / 3;
            int l = Math.max(x + 1, bx);
            int r = Math.min(x + w - 1, bx + 6);
            if (r > l) {
                g.m_280509_(l, yy, r, yy + 1, alpha(0.16f, c));
            }
        }
    }

    // ------------------------------------------------------------- watermark motifs

    /**
     * Per-tab watermark drawn on the card face under the content. Tinted, never full strength,
     * so text stays readable. kind: 0 orrery, 1 rift, 2 star chart, 3 circuit.
     */
    public static void motif(GuiGraphics g, int kind, int x, int y, int w, int h) {
        if (w < 24 || h < 24) {
            return;
        }
        double t = seconds();
        switch (kind) {
            case 0 -> orrery(g, x, y, w, h, t);
            case 1 -> rift(g, x, y, w, h, t);
            case 2 -> starChart(g, x, y, w, h, t);
            default -> circuit(g, x, y, w, h, t);
        }
    }

    private static void orrery(GuiGraphics g, int x, int y, int w, int h, double t) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int rings = 3;
        for (int r = 0; r < rings; r++) {
            double rx = w * 0.46 * (r + 1) / rings;
            double ry = rx * 0.34;
            double spin = t * (0.42 - r * 0.11) * (r % 2 == 0 ? 1.0 : -1.0);
            int tint = r == 1 ? mix(E_CARD, E_CYAN, 0.12f) : mix(E_CARD, E_VIOLET, 0.12f);
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI / 32.0;
                int px = cx + (int) Math.round(Math.cos(a) * rx);
                int py = cy + (int) Math.round(Math.sin(a) * ry);
                g.m_280509_(px, py, px + 1, py + 1, tint);
            }
            for (int s = 0; s < 2; s++) {
                double a = spin + s * Math.PI;
                int sx = cx + (int) Math.round(Math.cos(a) * rx);
                int sy = cy + (int) Math.round(Math.sin(a) * ry);
                g.m_280509_(sx - 1, sy - 1, sx + 2, sy + 2, mix(E_CARD, r == 1 ? E_CYAN : E_VIOLET, 0.45f));
            }
        }
        cube(g, cx - 3, cy - 3, 6, mix(E_CARD, E_GOLD, 0.35f));
    }

    private static void rift(GuiGraphics g, int x, int y, int w, int h, double t) {
        int step = 24;
        int tint = mix(E_CARD, E_CYAN, 0.12f);
        double off = (t * 20.0) % step;
        for (double k = -h; k < w + h; k += step) {
            clippedDiagonal(g, x, y, w, h, (int) (x + k + off), tint);
        }
    }

    private static void clippedDiagonal(GuiGraphics g, int x, int y, int w, int h, int topX, int colour) {
        for (int yy = y; yy < y + h; yy++) {
            int xx = topX - (yy - y);
            int l = Math.max(x, xx);
            int r = Math.min(x + w, xx + 2);
            if (r > l) {
                g.m_280509_(l, yy, r, yy + 1, colour);
            }
        }
    }

    private static void starChart(GuiGraphics g, int x, int y, int w, int h, double t) {
        int nodes = 14;
        int[] nx = new int[nodes];
        int[] ny = new int[nodes];
        for (int n = 0; n < nodes; n++) {
            nx[n] = x + 6 + Math.round(rnd(n * 2 + 1) * (w - 12) + (float) Math.sin(t * 0.3 + n) * 3f);
            ny[n] = y + 6 + Math.round(rnd(n * 2 + 2) * (h - 12) + (float) Math.cos(t * 0.25 + n) * 3f);
        }
        int link = mix(E_CARD, E_VIOLET, 0.10f);
        int gold = mix(E_CARD, E_GOLD, 0.08f);
        for (int n = 0; n + 1 < nodes; n++) {
            line(g, nx[n], ny[n], nx[n + 1], ny[n + 1], link);
            if (n + 3 < nodes) {
                line(g, nx[n], ny[n], nx[n + 3], ny[n + 3], gold);
            }
        }
        for (int n = 0; n < nodes; n++) {
            int tint = mix(E_CARD, n % 3 == 0 ? E_GOLD : E_VIOLET, 0.35f + 0.25f * breathe(1.5, n));
            g.m_280509_(nx[n] - 1, ny[n] - 1, nx[n] + 2, ny[n] + 2, tint);
        }
    }

    private static void circuit(GuiGraphics g, int x, int y, int w, int h, double t) {
        int lanes = 6;
        for (int k = 0; k < lanes; k++) {
            int ly = y + (k + 1) * h / (lanes + 1);
            int base = mix(E_CARD, E_VIOLET, 0.09f);
            g.m_280509_(x, ly, x + w, ly + 1, base);
            int px = x + (int) (((t * (26 + k * 9) + k * 53) % (w + 16)) - 8);
            int l = Math.max(x, px);
            int r = Math.min(x + w, px + 6);
            if (r > l) {
                g.m_280509_(l, ly - 1, r, ly + 2, mix(E_CARD, E_CYAN, 0.55f));
            }
            for (int j = 1; j < 4; j++) {
                int jx = x + j * w / 4;
                g.m_280509_(jx - 1, ly - 2, jx + 2, ly + 3, mix(E_CARD, E_VIOLET, 0.18f));
            }
        }
    }

    /** Quality colour, normalised for the v4 palette. */
    public static int quality(int color) {
        return palette(color);
    }

    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static final class Motion {
        private float value;
        private long last;

        private Motion(long now) {
            this.last = now;
        }
    }
}
