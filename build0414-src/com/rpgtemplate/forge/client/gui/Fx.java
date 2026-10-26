package com.rpgtemplate.forge.client.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 万界 CPU effect kit (phase A). Everything here is built from rectangle fills and
 * 1px lines: no particles, no GL state, no shaders. Each helper is meant to sit at
 * roughly 5% of the visual weight of the surface it decorates, so callers pass low
 * alpha and keep the effect to one or two elements per card.
 */
public final class Fx {
    private Fx() {
    }

    private static final Map<Long, float[]> SPRINGS = new HashMap<>();
    private static long lastSpringSweep;

    // ------------------------------------------------------------------ motion

    /**
     * Damped spring keyed by a stable id. State is {position, velocity, lastNanos}.
     * Stiffness k controls speed, damping c controls overshoot (c around 2*sqrt(k) is critical).
     */
    public static float spring(long key, float target, float k, float c) {
        if (UiPreferences.reducedMotion) {
            return target;
        }
        long now = System.nanoTime();
        if (now - lastSpringSweep > 5_000_000_000L) {
            SPRINGS.clear();
            lastSpringSweep = now;
        }
        float[] s = SPRINGS.get(key);
        if (s == null) {
            s = new float[] {target, 0f, now};
            SPRINGS.put(key, s);
            return target;
        }
        float dt = Math.min(1f / 30f, (float) ((now - (long) s[2]) / 1.0e9));
        s[2] = now;
        // semi-implicit Euler, split into small steps for stability at low frame rates
        int steps = Math.max(1, (int) Math.ceil(dt / (1f / 120f)));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float acc = k * (target - s[0]) - c * s[1];
            s[1] += acc * h;
            s[0] += s[1] * h;
        }
        return s[0];
    }

    /** Ease-out spring preset: quick, one small overshoot. Good for bars and counters. */
    public static float bounce(long key, float target) {
        return spring(key, target, 180f, 15f);
    }

    /** Squash and stretch for a 0..1 progress value. Returns {scaleX, scaleY} about the centre. */
    public static float[] squash(float p) {
        float t = UiTheme.clamp01(p);
        float decay = (1f - t) * (1f - t);
        float wave = (float) Math.sin(t * Math.PI * 3.0);
        return new float[] {1f + 0.07f * decay * wave, 1f - 0.05f * decay * wave};
    }

    /** Horizontal flip-in scale for a 0..1 progress value (card reveal). */
    public static float flip(float p) {
        float t = UiTheme.clamp01(p);
        return (float) Math.sin(t * Math.PI / 2.0);
    }

    /** Magnetic hover: returns {dx, dy} pulling a control toward the cursor inside a radius. */
    public static int[] magnet(int mx, int my, int x, int y, int w, int h, float strength, float radius) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = mx - cx;
        float dy = my - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= 0.5f || dist > radius) {
            return new int[] {0, 0};
        }
        float fall = 1f - dist / radius;
        fall = fall * fall;
        return new int[] {Math.round(dx * strength * fall), Math.round(dy * strength * fall)};
    }

    /** Parallax offset: deeper layers move less. depth 0..1, mouse relative to centre in -1..1. */
    public static int parallax(float mouseAxis, float depth) {
        return Math.round(mouseAxis * depth * 6f);
    }

    // ------------------------------------------------------------------ surfaces

    /**
     * Conic border: a bright head with a fading tail that travels around the perimeter.
     * Drawn as 1px edge runs, so cost is linear in perimeter / step.
     */
    public static void conic(GuiGraphics g, int x, int y, int w, int h, int color, float strength) {
        if (w <= 4 || h <= 4) {
            return;
        }
        if (FxShader.rim(g, x, y, w, h, color, strength, 0f, 1f)) {
            return;
        }
        double t = UiTheme.seconds();
        float head = (float) ((t * 0.22) % 1.0);
        int step = 3;
        float per = 2f * (w + h);
        for (float d = 0f; d < per; d += step) {
            float p = d / per;
            float age = (head - p + 1f) % 1f;
            float a = Math.max(0f, 1f - age / 0.22f);
            if (a <= 0.02f) {
                continue;
            }
            a = a * a * strength;
            int c = UiTheme.alpha(a, color);
            int sx = (int) d;
            // map the running distance onto the four edges, clockwise from the top-left
            if (sx < w) {
                g.m_280509_(x + sx, y, x + sx + step, y + 1, c);
            } else if (sx < w + h) {
                int o = sx - w;
                g.m_280509_(x + w - 1, y + o, x + w, y + o + step, c);
            } else if (sx < 2 * w + h) {
                int o = sx - w - h;
                g.m_280509_(x + w - o - step, y + h - 1, x + w - o, y + h, c);
            } else {
                int o = sx - 2 * w - h;
                g.m_280509_(x, y + h - o - step, x + 1, y + h - o, c);
            }
        }
    }

    /** Holographic scanlines: 1px rows every 3px that drift slowly. */
    public static void scanlines(GuiGraphics g, int x, int y, int w, int h, int color, float a) {
        if (FxShader.rim(g, x, y, w, h, color, 0f, a, 1f)) {
            return;
        }
        int drift = (int) ((UiTheme.seconds() * 6.0) % 3.0);
        int c = UiTheme.alpha(a, color);
        for (int row = y + drift; row < y + h; row += 3) {
            g.m_280509_(x, row, x + w, row + 1, c);
        }
    }

    /** Soft shadow: stacked translucent rings that fall off outward and sit low. */
    public static void softShadow(GuiGraphics g, int x, int y, int w, int h, int color, float a) {
        for (int i = 0; i < 4; i++) {
            int grow = 1 + i * 2;
            float k = a * (1f - i / 4f) * 0.35f;
            g.m_280509_(x - grow, y + h + i, x + w + grow, y + h + i + 2, UiTheme.alpha(k, color));
        }
    }

    /** Contact ambient occlusion: a short dark gradient along the base of a surface. */
    public static void contactAo(GuiGraphics g, int x, int y, int w, int h, float a) {
        for (int i = 0; i < 3; i++) {
            float k = a * (1f - i / 3f);
            g.m_280509_(x + 2, y + h + i, x + w - 2, y + h + i + 1, UiTheme.alpha(k, UiTheme.E_INK));
        }
    }

    /** Volumetric fog band: a horizontal haze whose density drifts along x. */
    public static void fogBand(GuiGraphics g, int x, int y, int w, int h, int color, float a) {
        if (FxShader.haze(g, x, y, w, h, color, a)) {
            return;
        }
        double t = UiTheme.seconds();
        int seg = Math.max(8, w / 48);
        for (int row = 0; row < h; row++) {
            float rowK = (float) Math.sin(Math.PI * (row + 0.5) / h);
            for (int sx = 0; sx < w; sx += seg) {
                float dens = 0.5f + 0.5f * (float) Math.sin(sx * 0.045 + t * 0.9 + row * 0.4);
                float k = a * rowK * dens;
                if (k < 0.02f) {
                    continue;
                }
                g.m_280509_(x + sx, y + row, Math.min(x + w, x + sx + seg), y + row + 1, UiTheme.alpha(k, color));
            }
        }
    }

    /** Lens flare: two anamorphic streaks and a hex ring of four dots. Static shape, pulsing alpha. */
    public static void lensFlare(GuiGraphics g, int cx, int cy, int r, int color, float a) {
        float pulse = a * (0.75f + 0.25f * UiTheme.breathe(2.2, cx * 0.1));
        int c = UiTheme.alpha(pulse, color);
        g.m_280509_(cx - r * 2, cy, cx + r * 2, cy + 1, c);
        g.m_280509_(cx, cy - r, cx + 1, cy + r, c);
        int core = UiTheme.alpha(pulse, UiTheme.E_WHITE);
        g.m_280509_(cx - 1, cy - 1, cx + 2, cy + 2, core);
        g.m_280509_(cx + r - 1, cy - 1, cx + r, cy, c);
        g.m_280509_(cx - r, cy - 1, cx - r + 1, cy, c);
        g.m_280509_(cx - 1, cy + r - 1, cx, cy + r, c);
        g.m_280509_(cx - 1, cy - r, cx, cy - r + 1, c);
    }

    /**
     * ECG trace: a PQRST heartbeat scrolling left to right inside a box.
     * rate is cycles per second. The trace is a 1px polyline.
     */
    public static void ecg(GuiGraphics g, int x, int y, int w, int h, int color, float a, double rate) {
        int period = 26;
        int base = y + h / 2;
        double t = UiTheme.seconds();
        int prevY = base;
        int c = UiTheme.alpha(a, color);
        for (int i = 0; i < w; i++) {
            double u = ((i + t * rate * period) % period) / period;
            double v = ecgShape(u);
            int py = base - (int) Math.round(v * (h / 2.0));
            int top = Math.min(prevY, py);
            int bot = Math.max(prevY, py) + 1;
            g.m_280509_(x + i, top, x + i + 1, bot, c);
            prevY = py;
        }
    }

    private static double ecgShape(double u) {
        if (u < 0.10) {
            return 0.0;
        }
        if (u < 0.20) {
            return 0.18 * Math.sin((u - 0.10) / 0.10 * Math.PI);            // P wave
        }
        if (u < 0.30) {
            return 0.0;
        }
        if (u < 0.34) {
            return -0.15 * Math.sin((u - 0.30) / 0.04 * Math.PI);          // Q dip
        }
        if (u < 0.40) {
            return Math.sin((u - 0.34) / 0.06 * Math.PI) * 0.95;           // R spike
        }
        if (u < 0.46) {
            return -0.25 * Math.sin((u - 0.40) / 0.06 * Math.PI);          // S dip
        }
        if (u < 0.62) {
            return 0.0;
        }
        if (u < 0.80) {
            return 0.28 * Math.sin((u - 0.62) / 0.18 * Math.PI);           // T wave
        }
        return 0.0;
    }

    /**
     * LED dot matrix meter: cols x rows dots, lit left to right by ratio, with one scanning
     * column that brightens as it passes. Replaces a solid bar with a discrete read-out.
     */
    public static void ledMatrix(GuiGraphics g, int x, int y, int cols, int rows, int cell, double ratio, int color) {
        int lit = (int) Math.round(ratio * cols);
        int scan = (int) ((UiTheme.seconds() * 6.0) % cols);
        for (int cx = 0; cx < cols; cx++) {
            boolean on = cx < lit;
            for (int r = 0; r < rows; r++) {
                int px = x + cx * (cell + 1);
                int py = y + r * (cell + 1);
                float a;
                if (on) {
                    a = cx == scan ? 1f : 0.82f;
                } else {
                    a = 0.1f;
                }
                g.m_280509_(px, py, px + cell, py + cell, UiTheme.alpha(a, on ? color : UiTheme.E_EDGE));
            }
        }
    }

    /**
     * Bezier energy line from (x0,y0) to (x1,y1) with the control points pulled sideways by bend.
     * A single bright segment travels along it, so the line reads as energy flowing.
     */
    public static void energyLine(GuiGraphics g, int x0, int y0, int x1, int y1, float bend, int color, float a) {
        double t = UiTheme.seconds();
        float cx1 = x0 + (x1 - x0) * 0.35f;
        float cy1 = y0 + (y1 - y0) * 0.0f - bend;
        float cx2 = x0 + (x1 - x0) * 0.65f;
        float cy2 = y1 + bend;
        int n = 48;
        int px = x0;
        int py = y0;
        float head = (float) ((t * 0.5) % 1.0);
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n;
            float v = 1f - u;
            float bx = v * v * v * x0 + 3 * v * v * u * cx1 + 3 * v * u * u * cx2 + u * u * u * x1;
            float by = v * v * v * y0 + 3 * v * v * u * cy1 + 3 * v * u * u * cy2 + u * u * u * y1;
            int nx = Math.round(bx);
            int ny = Math.round(by);
            float dist = Math.abs(u - head);
            float glow = Math.max(0f, 1f - dist / 0.08f);
            int c = UiTheme.alpha(Math.min(1f, a * (0.35f + glow)), glow > 0.2f ? UiTheme.E_WHITE : color);
            UiTheme.line(g, px, py, nx, ny, c);
            px = nx;
            py = ny;
        }
    }

    // ------------------------------------------------------------------ timing

    /** Staggered entrance: returns a downward offset that settles to zero, one step per index. */
    public static int stagger(long startNanos, int index, int stepMs, int maxOffset) {
        float e = UiTheme.enter(startNanos + index * stepMs * 1_000_000L);
        return Math.round((1f - e) * maxOffset);
    }

    /**
     * Keyframe timeline. pairs = {t0, v0, t1, v1, ...} with times in seconds since startNanos.
     * Linear between keys, holds the last value after the end. Under reduced motion it returns the last value.
     */
    public static float keyframe(long startNanos, float... pairs) {
        int last = pairs.length - 2;
        if (UiPreferences.reducedMotion || startNanos == 0L) {
            return pairs[last + 1];
        }
        float t = (float) ((System.nanoTime() - startNanos) / 1.0e9);
        if (t <= pairs[0]) {
            return pairs[1];
        }
        for (int i = 0; i < last; i += 2) {
            float t0 = pairs[i];
            float t1 = pairs[i + 2];
            if (t <= t1) {
                float u = (t - t0) / Math.max(1e-6f, t1 - t0);
                return pairs[i + 1] + (pairs[i + 3] - pairs[i + 1]) * UiTheme.clamp01(u);
            }
        }
        return pairs[last + 1];
    }

    // ------------------------------------------------------------------ numbers

    /**
     * Odometer digits: each place rolls continuously, so 41.5 shows the tens digit halfway
     * between 4 and 5. Right-aligned at rightX. Only meaningful for non-negative integers
     * in value, the caller decides whether to use it.
     */
    public static void odometer(GuiGraphics g, Font font, double value, int rightX, int y, int color) {
        double v = Math.max(0.0, value);
        // values within 0.15 of an integer are that integer, so settled counters never ghost
        double nearest = Math.rint(v);
        if (Math.abs(v - nearest) < 0.15) {
            v = nearest;
        }
        long whole = (long) Math.floor(v);
        int places = Math.max(1, String.valueOf(whole).length());
        int dw = font.m_92895_("0");
        double unit = 1.0;
        int x = rightX - dw;
        for (int k = 0; k < places; k++) {
            double pos;
            if (k == 0) {
                pos = v % 10.0;
            } else {
                // higher places only move while the place below is near its rollover (smooth carry)
                double lower = v / (unit / 10.0);
                double fracLower = lower - Math.floor(lower);
                pos = Math.floor(v / unit) % 10.0 + smoothstep(0.8, 1.0, fracLower);
            }
            int base = (int) Math.floor(pos) % 10;
            double frac = pos - Math.floor(pos);
            // near a digit the place is static, so only a real roll shows two digits
            if (frac > 0.85) {
                base = (base + 1) % 10;
                frac = 0.0;
            } else if (frac < 0.15) {
                frac = 0.0;
            } else {
                frac = (frac - 0.15) / 0.70;
            }
            if (frac <= 0.0) {
                UiTheme.text(g, font, String.valueOf(base), x, y, color);
            } else {
                // the outgoing digit climbs up and fades, the incoming digit rises from below
                int next = (base + 1) % 10;
                float f = (float) frac;
                int up = Math.round(f * 8f);
                int down = Math.round((1f - f) * 8f);
                UiTheme.text(g, font, String.valueOf(base), x, y - up, UiTheme.alpha(1f - f * 0.7f, color));
                UiTheme.text(g, font, String.valueOf(next), x, y + down, UiTheme.alpha(0.3f + f * 0.7f, color));
            }
            x -= dw;
            unit *= 10.0;
        }
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }
}
