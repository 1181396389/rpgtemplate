package com.rpgtemplate.forge.client.hud;

import com.google.gson.JsonObject;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.NumberFormatter;
import com.rpgtemplate.core.template.CostType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.ui.UiLayout;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.client.ClientKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.forge.client.gui.Fx;
import com.rpgtemplate.forge.client.gui.UiPreferences;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.forge.possession.client.ClientPossession;
import com.rpgtemplate.forge.skill.RctFatigue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * In-game HUD (v3, 白刃 × 纹章): a resource crest (top-left) and a skill tray (bottom-right).
 * Geometry is in base units, scaled by {@link UiLayout#hudScale}.
 */
public final class SystemHud {
    private static final int RES_W = 190;
    private static final int DOCK_W = 214;
    private static final int BLOCKS = 10;
    private static final int TILE_W = 46;
    private static final int TILE_H = 58;
    private static final int TILE_H_DETAIL = 68;
    private static final int TILE_GAP = 6;

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static int ticks;
    private static int pendingSince;
    private static double pendingXp;
    /** One XP gain shown at a time. Gains that land while it is alive add to the same number. */
    private static final int XP_POP_LIFE = 40;
    private static int xpPopBorn = -1000;
    private static double xpPopTotal;
    private static long levelUpNanos;
    private static final long HUD_SPRING = 0x48554400L;
    private static final long HUD_XP_LED = 0x48554F00L;
    private static final double[] current;
    private static final double[] previous;
    private static boolean initialized;
    private static UiLayout.Rect resources;
    private static UiLayout.Rect dock;
    private static float scale;

    private SystemHud() {
    }

    private static String tr(String key, Object... args) {
        return Component.m_237110_("hud.rpgtemplate." + key, args).getString();
    }

    public static UiLayout.Rect resourceBounds() {
        return resources;
    }

    public static UiLayout.Rect skillBounds() {
        return dock;
    }

    public static float renderScale() {
        return scale;
    }

    public static void onXpGained(double amount, boolean levelUp) {
        if (!((Boolean) ModConfigs.CLIENT.xpPopups.get())) {
            return;
        }
        pendingXp += amount;
        if (pendingSince < 0) {
            pendingSince = ticks;
        }
        if (levelUp) {
            flush(true);
        }
    }

    private static void flush(boolean levelUp) {
        if (pendingXp <= 0.0) {
            return;
        }
        xpPopTotal = ticks - xpPopBorn < XP_POP_LIFE ? xpPopTotal + pendingXp : pendingXp;
        xpPopBorn = ticks;
        if (levelUp) {
            POPUPS.add(new Popup(tr("ui.level_up"), ticks + 5, UiTheme.E_RED));
            levelUpNanos = System.nanoTime();
        }
        while (POPUPS.size() > 4) {
            POPUPS.remove(0);
        }
        pendingXp = 0.0;
        pendingSince = -1;
    }

    public static void tick() {
        if (pendingSince >= 0 && ++ticks - pendingSince >= 6) {
            flush(false);
        }
        POPUPS.removeIf(popup -> ticks - popup.born() > 50);
        PlayerSystemData data = ClientSystemData.get();
        if (!data.isActivated()) {
            initialized = false;
            POPUPS.clear();
            pendingXp = 0.0;
            pendingSince = -1;
            xpPopBorn = -1000;
            xpPopTotal = 0.0;
            return;
        }
        double[] target = {
            Math.max(0.0, data.hp()), Math.max(0.0, data.mp()),
            Math.max(0.0, data.sp()), Math.max(0.0, data.xp())
        };
        for (int i = 0; i < 4; i++) {
            if (!initialized) {
                current[i] = target[i];
                previous[i] = target[i];
            }
            previous[i] = current[i];
            current[i] = UiPreferences.reducedMotion ? target[i] : current[i] + (target[i] - current[i]) * 0.35;
        }
        initialized = true;
    }

    public static void render(GuiGraphics g, float partial) {
        renderInternal(g, partial, false);
    }

    public static void renderForEditor(GuiGraphics g, float partial) {
        renderInternal(g, partial, true);
    }

    private static void renderInternal(GuiGraphics g, float partial, boolean editor) {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91073_ == null || mc.f_91066_.f_92062_ || mc.f_91066_.f_92063_) {
            return;
        }
        if (!editor && (!((Boolean) ModConfigs.CLIENT.hudEnabled.get()) || mc.f_91080_ != null)) {
            return;
        }
        PlayerSystemData data = ClientSystemData.get();
        if (!data.isActivated()) {
            return;
        }
        int view = ClientPossession.state().view();
        // Possession panels (views 1, 2, 7) own the screen; the system HUD yields to them.
        if (!editor && (view == 1 || view == 2 || view == 7)) {
            return;
        }

        long gameTick = mc.f_91073_.m_46467_();
        Font font = mc.f_91062_;
        TemplateInstance inst = data.loadedTemplate().orElse(null);
        TemplateDefinition def = inst == null ? null
            : (TemplateDefinition) TemplateRegistry.client().find(inst.templateId()).orElse(null);
        List<SkillEntry> skills = def == null ? List.of() : TemplateRegistry.client().effectiveSkills(def.id());
        boolean paged = skills.size() > 4;
        int page = paged ? ClientKeys.page() : 0;
        int first = page * 4;
        int shown = Math.max(0, Math.min(4, skills.size() - first));
        List<Notice> notices = notices(data, gameTick);
        boolean detail = UiPreferences.hudDetails;
        int tileH = detail ? TILE_H_DETAIL : TILE_H;

        int resH = 100 + notices.size() * 12 + (notices.isEmpty() ? 0 : 4);
        int dockH = def == null ? 40 : 50 + (paged ? 12 : 0) + tileH + 8;

        scale = UiLayout.hudScale(((Double) ModConfigs.CLIENT.hudScale.get()).floatValue(),
            g.m_280182_(), g.m_280206_(), 440, Math.max(resH, dockH) + 45);
        int rx = UiLayout.anchor((Integer) ModConfigs.CLIENT.hudX.get(), (int) Math.ceil(RES_W * scale), g.m_280182_());
        int ry = UiLayout.anchor((Integer) ModConfigs.CLIENT.hudY.get(), (int) Math.ceil(resH * scale), g.m_280206_());
        int dx = UiPreferences.dockX < 0 ? g.m_280182_() - (int) Math.ceil(DOCK_W * scale) - 8 : UiPreferences.dockX;
        int dy = UiPreferences.dockY < 0 ? g.m_280206_() - (int) Math.ceil(dockH * scale) - 48 : UiPreferences.dockY;
        dx = UiLayout.anchor(dx, (int) Math.ceil(DOCK_W * scale), g.m_280182_());
        dy = UiLayout.anchor(dy, (int) Math.ceil(dockH * scale), g.m_280206_());
        resources = new UiLayout.Rect(rx, ry, (int) Math.ceil(RES_W * scale), (int) Math.ceil(resH * scale));
        dock = new UiLayout.Rect(dx, dy, (int) Math.ceil(DOCK_W * scale), (int) Math.ceil(dockH * scale));

        float opacity = ((Double) ModConfigs.CLIENT.hudOpacity.get()).floatValue();
        drawResources(g, font, data, rx, ry, resH, notices, opacity, partial);
        drawPopups(g, font, resH);
        drawXpPopup(g, font);
        int rw = (int) Math.ceil(RES_W * scale);
        int rh = (int) Math.ceil(resH * scale);
        int dw = (int) Math.ceil(DOCK_W * scale);
        int dh = (int) Math.ceil(dockH * scale);
        // dock sits on a faint haze band so it reads as floating in the scene
        Fx.fogBand(g, dx, dy - 12, dw, 12, UiTheme.E_CYAN, 0.12f * opacity);
        drawDock(g, font, data, inst, def, skills, first, shown, paged, page, detail, tileH, dx, dy, dockH, opacity, gameTick);
        Fx.contactAo(g, dx, dy, dw, dh, 0.16f * opacity);
        // level-up: an energy line runs from the resource card down to the dock, with a flare at the Lv badge
        if (levelUpNanos != 0L) {
            double since = (System.nanoTime() - levelUpNanos) / 1.0e9;
            if (since < 1.6) {
                // keyframed: the line ignites, holds, then fades; the flare flashes earlier and dies sooner
                float fade = Fx.keyframe(levelUpNanos, 0f, 0f, 0.12f, 1f, 0.5f, 0.8f, 1.6f, 0f);
                float flare = Fx.keyframe(levelUpNanos, 0f, 0f, 0.08f, 1f, 0.35f, 0.4f, 1.0f, 0f);
                Fx.energyLine(g, rx + rw / 2, ry + rh, dx + dw / 2, dy, 22f * scale, UiTheme.E_CYAN, 0.7f * fade);
                Fx.lensFlare(g, rx + (int) (30 * scale), ry + (int) (14 * scale), Math.max(3, (int) (5 * scale)), UiTheme.E_GOLD, flare);
            }
        }
    }

    private static void drawResources(GuiGraphics g, Font font, PlayerSystemData data, int rx, int ry, int resH,
                                      List<Notice> notices, float opacity, float partial) {
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_((float) rx, (float) ry, 0.0f);
        g.m_280168_().m_85841_(scale, scale, 1.0f);
        UiTheme.card(g, 0, 0, RES_W, resH, opacity);
        accentBand(g, RES_W);
        // conic rim light and a low-alpha holo scanline sheet sit under the content
        Fx.conic(g, 0, 0, RES_W, resH, UiTheme.E_CYAN, 0.9f * opacity);
        Fx.softShadow(g, 0, 0, RES_W, resH, UiTheme.E_VIOLET, 0.6f * opacity);
        Fx.scanlines(g, 2, 4, RES_W - 4, resH - 6, UiTheme.E_CYAN, 0.05f * opacity);

        UiTheme.emblem(g, 10, 8, UiTheme.E_GOLD);
        UiTheme.text(g, font, "Lv." + data.level(), 24, 9, UiTheme.E_INK);
        // XP as a 14x2 LED dot matrix beside the level
        double xpNeed = ClientSystemData.xpToNext();
        double xpRatio = xpNeed <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, data.xp() / xpNeed));
        Fx.ledMatrix(g, 70, 10, 14, 2, 2, Fx.bounce(HUD_XP_LED, (float) xpRatio), UiTheme.E_GOLD);
        UiTheme.right(g, font, tr("ui.resources"), RES_W - 10, 9, UiTheme.E_FAINT);
        UiTheme.seam(g, 10, 24, RES_W - 20, UiTheme.alpha(1f, UiTheme.E_VIOLET));

        double[] now = {data.hp(), data.mp(), data.sp(), data.xp()};
        double[] max = {ClientSystemData.maxHp(), ClientSystemData.maxMp(), ClientSystemData.maxSp(), ClientSystemData.xpToNext()};
        String[] names = {"HP", "MP", "SP", "XP"};
        int[] colours = {UiTheme.E_RED, UiTheme.E_INDIGO, UiTheme.E_JADE, UiTheme.E_GOLD_TEXT};
        float t = Math.max(0.0f, Math.min(1.0f, partial));
        for (int n = 0; n < 4; n++) {
            int y = 30 + n * 15;
            double penalty = n == 0 ? data.value("hp_max_penalty", 0.0) : 0.0;
            double shown = initialized ? previous[n] + (current[n] - previous[n]) * t : now[n];
            double ratio = (max[n] + penalty) <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, shown / (max[n] + penalty)));
            // the lit segments overshoot slightly when the value jumps
            ratio = Math.max(0.0, Math.min(1.0, Fx.bounce(HUD_SPRING + n, (float) ratio)));
            if (n == 0 && ratio < 0.25) {
                // low health: the HP row breathes red
                g.m_280509_(8, y - 1, RES_W - 8, y + 11, UiTheme.alpha(0.08f + 0.12f * UiTheme.breathe(6.0, 0.0), UiTheme.E_RED));
            }
            UiTheme.cube(g, 10, y, 10, colours[n]);
            UiTheme.text(g, font, names[n], 24, y + 1, colours[n]);
            double lit = ratio * BLOCKS;
            for (int i = 0; i < BLOCKS; i++) {
                float a = (float) Math.max(0.0, Math.min(1.0, lit - i));
                // lit segments shimmer slightly, each on its own phase
                float shim = a > 0.0f ? a * (0.9f + 0.1f * UiTheme.breathe(3.0, i * 0.6 + n)) : 0.12f;
                UiTheme.block(g, 46 + i * 6, y + 1, 4, 8, colours[n], shim);
            }
            String value = NumberFormatter.format(Math.max(0.0, now[n])) + " / " + NumberFormatter.format(max[n]);
            UiTheme.right(g, font, UiTheme.fit(font, value, 66), RES_W - 10, y, UiTheme.E_INK);
        }
        UiTheme.seam(g, 10, 84, RES_W - 20, UiTheme.alpha(0.5f, UiTheme.E_VIOLET));
        UiTheme.text(g, font, "DEF", 10, 90, UiTheme.E_FAINT);
        UiTheme.text(g, font, NumberFormatter.format(ClientSystemData.derived("defense", 0.0)), 34, 90, UiTheme.E_INK);
        // live heartbeat trace between DEF and the key hint
        Fx.ecg(g, 66, 88, 56, 10, UiTheme.E_RED, 0.7f, 1.1);
        UiTheme.right(g, font, ClientKeys.PANEL.m_90863_().getString() + "  " + tr("ui.panel"), RES_W - 10, 90, UiTheme.E_FAINT);

        int ny = 104;
        for (Notice notice : notices) {
            float blink = notice.color == UiTheme.E_RED || notice.color == UiTheme.E_DEEP_RED
                ? 0.55f + 0.45f * UiTheme.breathe(4.0, ny * 0.3) : 1.0f;
            g.m_280509_(10, ny, 13, ny + 9, UiTheme.alpha(blink, notice.color));
            UiTheme.text(g, font, UiTheme.fit(font, notice.text, RES_W - 30), 17, ny + 1, UiTheme.E_INK);
            ny += 12;
        }
        g.m_280168_().m_85849_();
    }

    /** Dual-tone identity strip along the top edge: 次元 violet into 空间 cyan. */
    private static void accentBand(GuiGraphics g, int w) {
        int half = w / 2;
        g.m_280509_(0, 0, half, 3, UiTheme.alpha(1f, UiTheme.E_VIOLET));
        g.m_280509_(half, 0, w, 3, UiTheme.alpha(1f, UiTheme.E_CYAN));
        // a white glint travels across the band
        int sx = (int) (((UiTheme.seconds() * 0.35) % 1.0) * (w + 24)) - 12;
        int l = Math.max(0, sx);
        int r = Math.min(w, sx + 14);
        if (r > l) {
            g.m_280509_(l, 0, r, 3, UiTheme.alpha(0.9f, UiTheme.E_WHITE));
        }
    }

    /**
     * Floating gains without a box: gold text with an ink outline (legible on any scene),
     * a cyan rift seam that grows out from under the text, and a short rise then fade.
     */
    private static void drawPopups(GuiGraphics g, Font font, int resH) {
        int stack = 0;
        for (Popup popup : POPUPS) {
            int age = ticks - popup.born();
            if (age < 0) {
                continue;
            }
            float fade = Math.min(1.0f, (float) age / 3.0f) * Math.max(0.0f, 1.0f - (float) age / 50.0f);
            int lift = UiPreferences.reducedMotion ? 0 : (int) (UiTheme.ease((float) age / 14.0f) * 4.0f);
            int y = resH + 8 + stack++ * 13 - lift;
            int x = 12;
            int w = font.m_92895_(popup.text());
            // seam grows to the text width over the first 8 ticks, then holds
            int seam = Math.min(w, Math.max(2, age * w / 8));
            g.m_280509_(x, y + 11, x + seam, y + 12, UiTheme.alpha(fade * 0.9f, UiTheme.E_CYAN));
            if (seam > 0) {
                g.m_280509_(x + seam - 1, y + 10, x + seam + 1, y + 13, UiTheme.alpha(fade, UiTheme.E_WHITE));
            }
            int ink = UiTheme.alpha(fade * 0.95f, UiTheme.E_INK);
            g.m_280056_(font, popup.text(), x - 1, y + 2, ink, false);
            g.m_280056_(font, popup.text(), x + 1, y + 2, ink, false);
            g.m_280056_(font, popup.text(), x, y + 1, ink, false);
            g.m_280056_(font, popup.text(), x, y + 3, ink, false);
            g.m_280056_(font, popup.text(), x, y + 2, UiTheme.alpha(fade, popup.color()), false);
        }
    }

    /**
     * The XP gain as one number above the screen centre. It bounces on each new gain, rises
     * slowly, and fades out before XP_POP_LIFE ends.
     */
    private static void drawXpPopup(GuiGraphics g, Font font) {
        int age = ticks - xpPopBorn;
        if (xpPopBorn < 0 || age < 0 || age >= XP_POP_LIFE) {
            return;
        }
        String text = "+" + NumberFormatter.format(xpPopTotal) + " XP";
        float fade = Math.min(1.0f, age / 2.0f) * Math.max(0.0f, 1.0f - Math.max(0, age - 26) / 14.0f);
        float bounce = UiPreferences.reducedMotion ? 0.0f : Math.max(0.0f, 1.0f - age / 5.0f);
        float s = 1.0f + 0.5f * bounce * bounce;
        int lift = UiPreferences.reducedMotion ? 0 : (int) (UiTheme.ease(age / 24.0f) * 14.0f);
        int cx = g.m_280182_() / 2;
        int cy = g.m_280206_() / 2 - 34 - lift;
        int w = font.m_92895_(text);
        int x = -w / 2;
        int ink = UiTheme.alpha(fade * 0.95f, UiTheme.E_INK);
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_((float) cx, (float) cy, 0.0f);
        g.m_280168_().m_85841_(s, s, 1.0f);
        g.m_280056_(font, text, x - 1, 2, ink, false);
        g.m_280056_(font, text, x + 1, 2, ink, false);
        g.m_280056_(font, text, x, 1, ink, false);
        g.m_280056_(font, text, x, 3, ink, false);
        g.m_280056_(font, text, x, 2, UiTheme.alpha(fade, UiTheme.E_GOLD_TEXT), false);
        g.m_280168_().m_85849_();
    }

    private static void drawDock(GuiGraphics g, Font font, PlayerSystemData data, TemplateInstance inst,
                                 TemplateDefinition def, List<SkillEntry> skills, int first, int shown,
                                 boolean paged, int page, boolean detail, int tileH,
                                 int dx, int dy, int dockH, float opacity, long gameTick) {
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_((float) dx, (float) dy, 0.0f);
        g.m_280168_().m_85841_(scale, scale, 1.0f);
        UiTheme.card(g, 0, 0, DOCK_W, dockH, opacity);
        accentBand(g, DOCK_W);

        if (def == null) {
            UiTheme.text(g, font, tr("no_template"), 14, 16, UiTheme.E_FAINT);
            g.m_280168_().m_85849_();
            return;
        }

        int qc = UiTheme.quality(def.quality().color());
        String qLabel = def.quality().label();
        int chipW = font.m_92895_(qLabel) + 8;
        g.m_280509_(DOCK_W - 10 - chipW, 8, DOCK_W - 10, 20, UiTheme.alpha(1f, UiTheme.E_CARD));
        g.m_280509_(DOCK_W - 10 - chipW, 8, DOCK_W - 10, 9, UiTheme.alpha(1f, qc));
        UiTheme.right(g, font, qLabel, DOCK_W - 14, 11, qc);
        UiTheme.text(g, font, UiTheme.fit(font, def.displayName(), DOCK_W - 28 - chipW), 14, 11, UiTheme.E_INK);

        boolean capped = def.levelless() || inst.level() >= def.maxLevel();
        String level = def.levelless() ? tr("ui.base") : "Lv." + inst.level() + " / " + def.maxLevel();
        UiTheme.text(g, font, level, 14, 24, UiTheme.E_MUTED);
        UiTheme.bar(g, 14, 34, DOCK_W - 28, 4,
            capped ? 1.0 : inst.xp(), capped ? 1.0 : ClientSystemData.templateXpToNext(), qc);

        int y = 46;
        if (paged) {
            UiTheme.text(g, font, ClientKeys.pageMark(page) + " / " + (skills.size() + 3) / 4, 14, y, UiTheme.E_GOLD_TEXT);
            UiTheme.right(g, font, ClientKeys.pageKeyLabel() + "  " + tr("ui.page"), DOCK_W - 14, y, UiTheme.E_FAINT);
            y += 12;
        }
        int x = 6;
        for (int i = 0; i < shown; i++) {
            SkillEntry skill = skills.get(first + i);
            drawTile(g, font, data, inst, def, skill, i, x + i * (TILE_W + TILE_GAP), y, tileH, detail, gameTick);
        }
        g.m_280168_().m_85849_();
    }

    private static void drawTile(GuiGraphics g, Font font, PlayerSystemData data, TemplateInstance inst,
                                 TemplateDefinition def, SkillEntry skill, int index, int x, int y, int h,
                                 boolean detail, long gameTick) {
        boolean locked = !skill.unlockedAt(inst.level());
        boolean sealed = gameTick < data.value("sealed_until", 0.0);
        boolean burnout = gameTick < data.value("burnout_until", 0.0)
            && !skill.id().equals("rpgtemplate:reverse_cursed_technique") && !bool(skill.params(), "burnout_exempt");
        double cooldown = Math.max(0.0, data.value("cd:" + skill.id(), 0.0) - (double) gameTick);
        double cost = SkillFormula.cost(skill.baseCost(), def.quality());
        if (skill.id().equals("rpgtemplate:domain_amplification")) {
            cost = ClientSystemData.maxMp() * 0.015;
        }
        if (skill.id().equals("rpgtemplate:hollow_wicker_basket")) {
            cost = ClientSystemData.maxMp() * 0.02;
        }
        boolean affordable = skill.costType() == CostType.MP ? data.mp() >= cost
            : (skill.costType() != CostType.SP || data.sp() >= cost);

        String status;
        if (locked) {
            status = "Lv." + skill.unlockLevel();
        } else if (sealed) {
            status = tr("ui.sealed");
        } else if (burnout) {
            status = tr("ui.burnout");
        } else if (cooldown > 0.0) {
            status = String.format(Locale.ROOT, "%.1fs", cooldown / 20.0);
        } else {
            status = affordable ? tr("ui.ready") : tr("ui.empty");
        }
        String tagKey = skill.id().equals("rpgtemplate:domain_amplification") ? "king.amp"
            : (skill.id().equals("rpgtemplate:hollow_wicker_basket") ? "king.basket" : "");
        if (!tagKey.isEmpty() && data.value(tagKey, 0.0) > gameTick) {
            status = String.format(Locale.ROOT, "%.1fs", (data.value(tagKey, 0.0) - gameTick) / 20.0);
        }

        int stateColour = locked ? UiTheme.E_FAINT
            : (sealed || burnout || !affordable ? UiTheme.E_DEEP_RED : (cooldown > 0.0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_JADE));
        int iconColour = locked ? UiTheme.E_FAINT : (skill.costType() == CostType.SP ? UiTheme.E_JADE : UiTheme.E_INDIGO);

        g.m_280509_(x, y, x + TILE_W, y + h, UiTheme.alpha(1f, UiTheme.E_INK));
        g.m_280509_(x + 1, y + 1, x + TILE_W - 1, y + h - 1, UiTheme.alpha(1f, UiTheme.E_CARD));
        g.m_280509_(x + 1, y + 1, x + TILE_W - 1, y + 3, UiTheme.alpha(1f, stateColour));

        UiTheme.key(g, font, UiTheme.fit(font, ClientKeys.physicalKeyLabel(index), 20), x + 4, y + 6);
        UiTheme.cube(g, x + 11, y + 20, 24, iconColour);

        UiTheme.text(g, font, UiTheme.fit(font, skill.name(), TILE_W - 6), x + 3, y + h - 27, locked ? UiTheme.E_FAINT : UiTheme.E_INK);
        UiTheme.text(g, font, UiTheme.fit(font, status, TILE_W - 6), x + 3, y + h - 17, stateColour);
        if (detail) {
            UiTheme.text(g, font, UiTheme.fit(font, NumberFormatter.format(cost), TILE_W - 6), x + 3, y + h - 9,
                skill.costType() == CostType.SP ? UiTheme.E_JADE : UiTheme.E_INDIGO);
        }

        double cdMax = Math.max(1, skill.cooldownTicks());
        double progress = cooldown > 0.0 ? cdMax - Math.min(cooldown, cdMax) : 1.0;
        double progressMax = cooldown > 0.0 ? cdMax : 1.0;
        UiTheme.bar(g, x + 3, y + h - 5, TILE_W - 6, 2, progress, progressMax, cooldown > 0.0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_EDGE);
        if (status.equals(tr("ui.ready"))) {
            UiTheme.marching(g, x, y, TILE_W, h, UiTheme.E_JADE, 1f);
        }
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }

    private static List<Notice> notices(PlayerSystemData data, long gameTick) {
        List<Notice> out = new ArrayList<>();
        if (gameTick < data.value("sealed_until", 0.0)) {
            out.add(new Notice(tr("sealed", secs(data.value("sealed_until", 0.0) - gameTick)), UiTheme.E_DEEP_RED));
        }
        if (gameTick < data.value("burnout_until", 0.0)) {
            out.add(new Notice(tr("burnout", secs(data.value("burnout_until", 0.0) - gameTick)), UiTheme.E_DEEP_RED));
        }
        if (gameTick < data.value("domain.until", 0.0)) {
            out.add(new Notice(tr("domain", secs(data.value("domain.until", 0.0) - gameTick),
                String.format(Locale.ROOT, "%.0f", data.value("domain.radius", 0.0)))
                + String.format(Locale.ROOT, " \u00b7 %.2f MP/s", data.value("king.domain_upkeep", 0.0)), UiTheme.E_VIOLET));
        }
        if (data.value("king.amp", 0.0) > gameTick) {
            out.add(new Notice("\u9886\u57df\u5c55\u5ef6 " + secs(data.value("king.amp", 0.0) - gameTick) + "s \u00b7 1.5% MP/s", UiTheme.E_VIOLET));
        }
        if (data.value("king.basket", 0.0) > gameTick) {
            out.add(new Notice("\u5f25\u865a\u845b\u7b3c " + secs(data.value("king.basket", 0.0) - gameTick) + "s \u00b7 2% MP/s", UiTheme.E_GOLD_TEXT));
        }
        if (gameTick < data.value("black_flash.zone_until", 0.0)) {
            out.add(new Notice(tr("black_flash", (int) data.value("black_flash.streak", 0.0),
                secs(data.value("black_flash.zone_until", 0.0) - gameTick)), UiTheme.E_RED));
        }
        if (data.value("hp_max_penalty", 0.0) > 0.0) {
            out.add(new Notice(tr("wound", NumberFormatter.format(data.value("hp_max_penalty", 0.0))), UiTheme.E_GOLD_TEXT));
        }
        if (RctFatigue.active(data, gameTick) || RctFatigue.factor(data, gameTick) > 1.005) {
            out.add(new Notice(tr(RctFatigue.active(data, gameTick) ? "rct_active" : "rct_fatigue",
                String.format(Locale.ROOT, "%.2f", RctFatigue.factor(data, gameTick))), UiTheme.E_INDIGO));
        }
        return out;
    }

    private static String secs(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    static {
        pendingSince = -1;
        current = new double[] {0.0, 0.0, 0.0, 0.0};
        previous = new double[] {0.0, 0.0, 0.0, 0.0};
        resources = new UiLayout.Rect(8, 8, RES_W, 100);
        dock = new UiLayout.Rect(0, 0, DOCK_W, 120);
        scale = 1.0f;
    }

    private record Popup(String text, int born, int color) {
    }

    private record Notice(String text, int color) {
    }
}
