package com.rpgtemplate.forge.client.gui;

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
import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.PanelActionPayload;
import com.rpgtemplate.forge.skill.RctFatigue;
import com.rpgtemplate.forge.system.PanelActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * K-key system panel (v4, 次元 × 空间 × 维度).
 *
 * <p>Four tabs (status, templates, codex, settings) keep the same server protocol as before:
 * {@code load}, {@code unload}, {@code advance}, {@code draw}, {@code lineage_draw} and {@code setting}.
 * Every colour goes through {@link UiTheme}. Layout is laid out by hand against the panel rect,
 * and scrolling is done by culling rows, so no scissor calls are needed.
 */
public final class SystemPanelScreen extends Screen {
    private static final int HEADER_H = 34;
    private static final int TAB_H = 18;
    private static final int PAD = 12;
    private static final int ROW_H = 22;
    private static final int CELL_W = 118;
    private static final int CELL_H = 30;
    private static final int DETAIL_STRIP_H = 30;
    private static final long REVEAL_MS = 2600L;
    private static final long TAB_GLIDE = 0x7AB0000000000001L;
    private static final long STATUS_GLIDE = 0x51A7000000000000L;
    private static final long STATUS_SPRING = 0x51A7500000000000L;
    private static final long SCROLL_SPRING = 0x5C20000000000001L;
    private static final long GROUP_GLIDE = 0x6C0B000000000001L;
    private static final long SETTING_GLIDE = 0x5E77000000000000L;

    private static Tab tab = Tab.STATUS;
    private static String selectedId;
    private static String revealId;
    private static long revealStartMs = -1L;
    private static boolean revealPending;
    private static String pendingLoadId;
    private static int settingsGroup;
    private static long pendingLoadMs;
    private static long tabStartNanos;

    private final List<Hit> hits = new ArrayList<>();
    private int left;
    private int top;
    private int pw;
    private int ph;
    private int ticks;
    private double listScroll;
    private double codexScroll;
    private double settingsScroll;
    private EditBox search;
    private String query = "";

    private enum Tab {
        STATUS, TEMPLATES, CODEX, SETTINGS
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public SystemPanelScreen() {
        super(Component.m_237115_("gui.rpgtemplate.title"));
    }

    // ------------------------------------------------------------------ public contract

    /** Called by the network layer when a template is granted or drawn. Highlights it on the next view. */
    public static void onNewTemplate(String templateId) {
        if (templateId == null) {
            return;
        }
        revealId = templateId;
        selectedId = templateId;
        Minecraft minecraft = Minecraft.m_91087_();
        if (minecraft.f_91080_ instanceof SystemPanelScreen) {
            revealStartMs = System.currentTimeMillis();
            revealPending = false;
        } else {
            revealPending = true;
        }
    }

    public static void clearReveal() {
        revealPending = false;
        revealStartMs = -1L;
    }

    public static void selectTab(int index) {
        tab = Tab.values()[Math.floorMod(index, Tab.values().length)];
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void m_7856_() {
        UiLayout.Rect rect = UiLayout.panel(this.f_96543_, this.f_96544_);
        this.pw = rect.w();
        this.ph = rect.h();
        this.left = rect.x();
        this.top = rect.y();
        int sx = this.left + PAD;
        int sy = this.top + contentTop();
        this.search = new EditBox(this.f_96547_, sx, sy, Math.max(90, this.pw / 3), 16,
            Component.m_237115_("gui.rpgtemplate.ui.search"));
        this.search.m_94199_(64);
        this.search.m_94144_(this.query);
        this.search.m_94182_(false);
        this.search.m_94202_(UiTheme.E_INK);
        this.search.m_257771_(Component.m_237115_("gui.rpgtemplate.ui.search_hint"));
        this.search.m_94151_(text -> {
            this.query = text;
            this.listScroll = 0.0;
            this.codexScroll = 0.0;
        });
        this.search.f_93624_ = tab == Tab.CODEX;
        this.m_142416_(this.search);
        if (revealPending && revealId != null) {
            revealPending = false;
            revealStartMs = System.currentTimeMillis();
        }
    }

    @Override
    public boolean m_7043_() {
        return false;
    }

    @Override
    public void m_86600_() {
        ++this.ticks;
        PlayerSystemData data = ClientSystemData.get();
        if (!data.isActivated()) {
            this.m_7379_();
        } else if (pendingLoadId != null && (pendingLoadId.equals(data.loadedTemplateId())
            || System.currentTimeMillis() - pendingLoadMs > 3000L)) {
            pendingLoadId = null;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean m_7933_(int key, int scan, int mods) {
        if (revealActive() && (key == 256 || key == 257 || key == 32)) {
            clearReveal();
            return true;
        }
        if (this.search != null && this.search.f_93624_ && this.search.m_93696_()) {
            if (key == 256) {
                this.search.m_93692_(false);
                return true;
            }
            return super.m_7933_(key, scan, mods);
        }
        if (key == 70 && Screen.m_96637_() && tab == Tab.CODEX) {
            this.m_7522_((GuiEventListener) this.search);
            this.search.m_93692_(true);
            return true;
        }
        if (ClientKeys.PANEL.m_90832_(key, scan)) {
            this.m_7379_();
            return true;
        }
        if (key >= 49 && key <= 52) {
            switchTab(Tab.values()[key - 49]);
            return true;
        }
        return super.m_7933_(key, scan, mods);
    }

    @Override
    public boolean m_6375_(double mx, double my, int button) {
        if (revealActive() && button == 0) {
            clearReveal();
            return true;
        }
        if (button == 0) {
            if (this.search != null && this.search.f_93624_ && this.search.m_5953_(mx, my)) {
                this.m_7522_((GuiEventListener) this.search);
                return this.search.m_6375_(mx, my, button);
            }
            if (this.search != null) {
                this.search.m_93692_(false);
            }
            for (int i = this.hits.size() - 1; i >= 0; --i) {
                Hit hit = this.hits.get(i);
                if (inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    click(1.0f);
                    UiTheme.pulse(mx, my);
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.m_6375_(mx, my, button);
    }

    @Override
    public boolean m_6050_(double mx, double my, double delta) {
        int[] area = contentArea();
        if (!inside(mx, my, area[0], area[1], area[2], area[3])) {
            return super.m_6050_(mx, my, delta);
        }
        double step = -delta * 16.0;
        switch (tab) {
            case TEMPLATES -> this.listScroll = Math.max(0.0, this.listScroll + step);
            case CODEX -> this.codexScroll = Math.max(0.0, this.codexScroll + step);
            case SETTINGS -> this.settingsScroll = Math.max(0.0, this.settingsScroll + step);
            default -> {
                return super.m_6050_(mx, my, delta);
            }
        }
        return true;
    }

    private void switchTab(Tab next) {
        if (tab != next) {
            tab = next;
            tabStartNanos = System.nanoTime();
            // shader ripple from the tab bar instead of a particle burst
            UiTheme.pulse(this.left + this.pw / 2.0, this.top + HEADER_H + 14.0);
            if (this.search != null) {
                this.search.m_93692_(false);
                this.search.f_93624_ = next == Tab.CODEX;
            }
            click(1.15f);
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void click(float pitch) {
        Minecraft.m_91087_().m_91106_().m_120367_(
            (SoundInstance) SimpleSoundInstance.m_119755_((SoundEvent) SoundEvents.f_12490_.m_203334_(), pitch, 0.35f));
    }

    private static void send(String action, String arg) {
        if (Minecraft.m_91087_().m_91403_() != null) {
            PacketDistributor.sendToServer(new PanelActionPayload(action, arg == null ? "" : arg));
        }
    }

    // ------------------------------------------------------------------ layout helpers

    private int contentTop() {
        return HEADER_H + 26;
    }

    /** Content rect: x, y, w, h in screen space. */
    private int[] contentArea() {
        int x = this.left + PAD;
        int y = this.top + contentTop();
        int w = this.pw - PAD * 2;
        int h = this.top + this.ph - 22 - y;
        return new int[] {x, y, w, Math.max(10, h)};
    }

    private static String t(String key, Object... args) {
        return Component.m_237110_("gui.rpgtemplate." + key, args).getString();
    }

    private static int argb(float a, int color) {
        return UiTheme.alpha(a, color);
    }

    private static boolean revealActive() {
        return revealStartMs > 0L && System.currentTimeMillis() - revealStartMs < REVEAL_MS;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void m_88315_(GuiGraphics g, int mx, int my, float partial) {
        this.hits.clear();
        UiTheme.backdrop(g, this.f_96543_, this.f_96544_);
        UiTheme.card(g, this.left, this.top, this.pw, this.ph, 1f);
        // rim light travels the card edge; the card sits on a soft contact shadow
        Fx.conic(g, this.left, this.top, this.pw, this.ph, UiTheme.E_VIOLET, 0.5f);
        Fx.contactAo(g, this.left, this.top, this.pw, this.ph, 0.14f);
        // each tab has its own watermark on the card face, under everything else
        // watermark parallax: it drifts a few pixels against the cursor, so it reads as a deeper layer
        float nxm = Math.max(-1f, Math.min(1f, (mx - (this.left + this.pw / 2f)) / (this.pw / 2f)));
        UiTheme.motif(g, tab.ordinal(), this.left + 2 + Fx.parallax(nxm, 0.5f), this.top + HEADER_H + 2, this.pw - 4, this.ph - HEADER_H - 4);
        PlayerSystemData data = ClientSystemData.get();
        Fx.fogBand(g, this.left + 2, this.top + 2, this.pw - 4, HEADER_H - 4, UiTheme.E_CYAN, 0.06f);
        renderHeader(g, data);
        renderTabs(g, mx, my);
        // the body slides up and fades in whenever the tab changes
        UiTheme.enterPush(g, UiTheme.enter(tabStartNanos), 8f);
        switch (tab) {
            case STATUS -> renderStatus(g, data);
            case TEMPLATES -> renderTemplates(g, data, mx, my);
            case CODEX -> renderCodex(g, data, mx, my);
            case SETTINGS -> renderSettings(g, data, mx, my);
        }
        UiTheme.enterPop(g);
        renderFooter(g);
        super.m_88315_(g, mx, my, partial);
    }

    private void renderHeader(GuiGraphics g, PlayerSystemData data) {
        Font font = this.f_96547_;
        UiTheme.emblem(g, this.left + PAD, this.top + 10, UiTheme.E_GOLD);
        UiTheme.orbit(g, this.left + PAD + 4, this.top + 14, 7, UiTheme.E_CYAN, 1f, UiTheme.seconds() * 1.3);
        // title at an integer 2x scale: a clear step above the 9 px body text, still crisp
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_((float) (this.left + PAD + 14), (float) (this.top + 9), 0.0f);
        g.m_280168_().m_85841_(2.0f, 2.0f, 1.0f);
        UiTheme.text(g, font, t("title"), 0, 0, UiTheme.E_INK);
        g.m_280168_().m_85849_();
        UiTheme.right(g, font, "Lv." + data.level(), this.left + this.pw - PAD, this.top + 11, UiTheme.E_VIOLET);
        UiTheme.seam(g, this.left + PAD, this.top + HEADER_H, this.pw - PAD * 2, UiTheme.alpha(1f, UiTheme.E_VIOLET));
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Tab[] tabs = Tab.values();
        int x0 = this.left + PAD;
        int gap = 4;
        int bw = (this.pw - PAD * 2 - gap * (tabs.length - 1)) / tabs.length;
        int y = this.top + HEADER_H + 4;
        for (int i = 0; i < tabs.length; i++) {
            int x = x0 + i * (bw + gap);
            boolean active = tab == tabs[i];
            boolean hover = inside(mx, my, x, y, bw, TAB_H);
            int ly = y - Math.round(UiTheme.hover(x, y, bw, TAB_H, hover && !active));
            // magnetic hover: the hovered tab leans toward the cursor; the hit box stays put
            int[] mg = hover ? Fx.magnet(mx, my, x, y, bw, TAB_H, 0.18f, 90f) : new int[] {0, 0};
            int bx = x + mg[0];
            int by = ly + mg[1];
            UiTheme.buttonSurface(g, bx, by, bw, TAB_H, hover, active, UiTheme.E_VIOLET);
            String label = (i + 1) + "  " + t("v4.tab." + tabs[i].name().toLowerCase(Locale.ROOT));
            UiTheme.text(g, this.f_96547_, label, bx + 8, by + 5, active ? UiTheme.E_VIOLET : UiTheme.E_MUTED);
            final Tab target = tabs[i];
            this.hits.add(new Hit(x, y, bw, TAB_H, () -> switchTab(target)));
        }
        // sliding indicator: glides between tabs on the same easing as the hover states
        float glide = UiTheme.approach(TAB_GLIDE, tab.ordinal());
        int ix = Math.round(x0 + glide * (bw + gap));
        int iy = y + TAB_H + 1;
        // the indicator squashes on arrival and settles back
        float[] sq = Fx.squash(UiTheme.enter(tabStartNanos));
        int iw = Math.max(4, Math.round(bw * sq[0]));
        int ixs = ix + (bw - iw) / 2;
        g.m_280509_(ixs, iy, ixs + iw, iy + 2, UiTheme.alpha(1f, UiTheme.E_VIOLET));
        g.m_280509_(ixs + 2, iy + 2, ixs + iw - 2, iy + 3, UiTheme.alpha(0.3f, UiTheme.E_VIOLET));
    }

    private void renderFooter(GuiGraphics g) {
        int y = this.top + this.ph - 14;
        UiTheme.seam(g, this.left + PAD, y - 6, this.pw - PAD * 2, UiTheme.alpha(0.35f, UiTheme.E_CYAN));
        int avail = this.pw - PAD * 2;
        String msg = UiTheme.fit(this.f_96547_, t("v4.footer"), avail);
        UiTheme.text(g, this.f_96547_, msg, this.left + PAD, y, UiTheme.E_FAINT);
    }

    // ------------------------------------------------------------------ status tab

    private void renderStatus(GuiGraphics g, PlayerSystemData data) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        int lw = a[2] * 56 / 100;
        int rx = a[0] + lw + 10;
        int rw = a[2] - lw - 10;

        // vertical budget: section caption (12) + rows + gap (6) + loaded strip (footH)
        final int footH = 36;
        int top = a[1] + 12;
        int avail = a[3] - 12 - footH - 6;
        int rowH = Math.min(30, (avail - 9) / 4);
        int tileH = Math.min(34, (avail - 6) / 3);

        UiTheme.text(g, font, t("v4.status.resources"), a[0], a[1], UiTheme.E_MUTED);
        String[] names = {t("v4.stat.hp"), t("v4.stat.mp"), t("v4.stat.sp"), t("v4.stat.xp")};
        int[] colours = {UiTheme.E_RED, UiTheme.E_INDIGO, UiTheme.E_JADE, UiTheme.E_GOLD_TEXT};
        double[] now = {data.hp(), data.mp(), data.sp(), data.xp()};
        double[] max = {ClientSystemData.maxHp(), ClientSystemData.maxMp(), ClientSystemData.maxSp(), ClientSystemData.xpToNext()};
        double orbitAngle = UiTheme.seconds() * 0.6;
        double clock = UiTheme.seconds();
        for (int i = 0; i < 4; i++) {
            // rows settle in one after another instead of arriving together
            int y = top + i * (rowH + 3) + Fx.stagger(tabStartNanos, i, 60, 10);
            int textY = y + 3;
            int laneY = y + rowH - 5;
            // numbers glide toward the real value instead of snapping
            double shownNow = UiTheme.approach(STATUS_GLIDE + i, (float) now[i]);
            double ratio = max[i] <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, shownNow / max[i]));
            // the lane spring overshoots a little on change; the dot is clamped to the lane
            ratio = Math.max(0.0, Math.min(1.0, Fx.bounce(STATUS_SPRING + i, (float) ratio)));
            UiTheme.inset(g, a[0], y, lw, rowH);
            if (i == 0 && ratio < 0.3) {
                // HP heartbeat: a red wash that pulses while health is low
                g.m_280509_(a[0] + 1, y + 1, a[0] + lw - 1, y + rowH - 1,
                    UiTheme.alpha(0.08f + 0.12f * UiTheme.breathe(6.0, 0.0), UiTheme.E_RED));
            }
            UiTheme.cube(g, a[0] + 6, textY, 8, colours[i]);
            UiTheme.text(g, font, names[i], a[0] + 18, textY, colours[i]);
            String nowText = NumberFormatter.format(Math.max(0.0, shownNow));
            String maxText = " / " + NumberFormatter.format(max[i]);
            int rightEdge = a[0] + lw - 6;
            if (nowText.equals(String.valueOf((long) Math.max(0.0, shownNow)))) {
                // plain integer: the current value rolls like an odometer
                UiTheme.right(g, font, maxText, rightEdge, textY, UiTheme.E_INK);
                Fx.odometer(g, font, Math.max(0.0, shownNow), rightEdge - font.m_92895_(maxText), textY, UiTheme.E_INK);
            } else {
                UiTheme.right(g, font, UiTheme.fit(font, nowText + maxText, lw - 110), rightEdge, textY, UiTheme.E_INK);
            }
            // orbit lane: hairline with ticks every 10 percent, a travelling dot at the current value
            int x0 = a[0] + 8;
            int x1 = a[0] + lw - 8;
            UiTheme.line(g, x0, laneY, x1, laneY, UiTheme.E_EDGE);
            for (int t2 = 0; t2 <= 10; t2++) {
                int tx = x0 + (x1 - x0) * t2 / 10;
                UiTheme.line(g, tx, laneY - 2, tx, laneY + 2, UiTheme.E_EDGE);
            }
            int dot = x0 + (int) Math.round((x1 - x0) * ratio);
            int fill = dot - x0;
            if (fill > 6) {
                // energy motes drifting along the filled part of the lane
                for (int k = 0; k < 4; k++) {
                    float ph = (float) ((clock * (0.18 + k * 0.07) + i * 0.17 + k * 0.29) % 1.0);
                    int mx2 = x0 + (int) (ph * fill);
                    int my2 = laneY + 2 + (k % 2);
                    g.m_280509_(mx2, my2, mx2 + 1, my2 + 1, UiTheme.alpha(0.85f, UiTheme.E_WHITE));
                }
            }
            g.m_280509_(x0, laneY - 1, dot, laneY + 1, UiTheme.alpha(1f, colours[i]));
            UiTheme.orbit(g, dot, laneY, 4, colours[i], 1f, orbitAngle + i);
            UiTheme.cube(g, dot - 3, laneY - 3, 6, colours[i]);
        }

        UiTheme.text(g, font, t("v4.status.derived"), rx, a[1], UiTheme.E_MUTED);
        String[][] stats = {
            {t("v4.stat.defense"), NumberFormatter.format(ClientSystemData.derived("defense", 0.0))},
            {t("v4.stat.luck"), NumberFormatter.format(ClientSystemData.derived("drawLuck", 0.0))},
            {t("v4.stat.collection"), NumberFormatter.format(ClientSystemData.derived("collectionRatio", 0.0) * 100.0) + "%"},
            {t("v4.stat.tpl_bonus"), NumberFormatter.format(ClientSystemData.derived("templateLevelBonus", 0.0))},
            {t("v4.stat.skill_dmg"), NumberFormatter.format(ClientSystemData.derived("skillDamageConversion", 0.0))},
            {t("v4.stat.skill_heal"), NumberFormatter.format(ClientSystemData.derived("skillHealConversion", 0.0))},
        };
        int tileW = (rw - 6) / 2;
        for (int i = 0; i < stats.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = rx + col * (tileW + 6);
            int y = top + row * (tileH + 3);
            UiTheme.frame(g, x, y, tileW, tileH, 1f);
            UiTheme.text(g, font, fitText(font, stats[i][0], tileW - 10), x + 6, y + 4, UiTheme.E_MUTED);
            UiTheme.text(g, font, fitText(font, stats[i][1], tileW - 10), x + 6, y + tileH - 12, UiTheme.E_INK);
        }

        int by = a[1] + a[3] - footH;
        UiTheme.frame(g, a[0], by, a[2], footH, 1f);
        Minecraft mc = Minecraft.m_91087_();
        long tick = mc.f_91073_ == null ? 0L : mc.f_91073_.m_46467_();
        UiTheme.text(g, font, t("v4.status.loaded"), a[0] + 10, by + 6, UiTheme.E_MUTED);
        String loaded = data.loadedTemplate().map(inst -> {
            TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
            return def == null ? t("status.no_template") : def.displayName() + "  Lv." + inst.level();
        }).orElse(t("status.no_template"));
        UiTheme.text(g, font, fitText(font, loaded, a[2] - 130), a[0] + 10, by + 18, UiTheme.E_INK);
        if (RctFatigue.active(data, tick)) {
            UiTheme.right(g, font, t("status.rct_active", String.format(Locale.ROOT, "%.2f", RctFatigue.factor(data, tick))),
                a[0] + a[2] - 10, by + 18, UiTheme.E_INDIGO);
        }
    }

    // ------------------------------------------------------------------ templates tab (vertical cards)

    private static final int CARD_W = 76;
    private static final int CARD_H = 88;
    private static final int CARD_H_COMPACT = 36;

    /** Template that gates the next draw: first owned, levelled template below its max level. */
    private static String gateTemplateName(List<TemplateInstance> owned) {
        for (TemplateInstance inst : owned) {
            TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
            if (def != null && !def.levelless() && inst.level() < def.maxLevel()) {
                return def.displayName();
            }
        }
        return t("status.draw_gate_fallback");
    }

    private void renderTemplates(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        List<TemplateInstance> owned = data.templates();

        // header row: caption on the left, draw button on the right
        UiTheme.text(g, font, t("templates.owned", owned.size()), a[0], a[1] + 4, UiTheme.E_MUTED);
        boolean unlocked = data.isDrawUnlocked();
        int pending = data.pendingDraws();
        String drawState = !unlocked ? t("status.draw_locked", pending, gateTemplateName(owned))
            : pending > 0 ? t("status.draw_ready", pending) : t("status.draw_none");
        int drawW = 110;
        int drawX = a[0] + a[2] - drawW;
        UiTheme.text(g, font, fitText(font, drawState, a[2] - drawW - 150), a[0] + 120, a[1] + 4,
            unlocked && pending > 0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_MUTED);
        boolean drawOn = unlocked && pending > 0;
        boolean drawHover = inside(mx, my, drawX, a[1], drawW, 16);
        UiTheme.buttonSurface(g, drawX, a[1], drawW, 16, drawHover && drawOn, false, UiTheme.E_GOLD);
        if (drawOn) {
            UiTheme.marching(g, drawX, a[1], drawW, 16, UiTheme.E_GOLD_TEXT, 1f);
        }
        UiTheme.text(g, font, t("btn.draw"), drawX + 8, a[1] + 4, drawOn ? UiTheme.E_INK : UiTheme.E_FAINT);
        if (drawOn) {
            this.hits.add(new Hit(drawX, a[1], drawW, 16, () -> send("draw", "")));
        }

        // detail card is sized first; the card grid gets the height that is left above it
        int detailH = Math.max(76, Math.min(112, a[3] * 52 / 100));
        int dy = a[1] + a[3] - detailH;
        int dh = detailH;
        int gridTop = a[1] + 22;
        int gridH = Math.max(0, dy - 6 - gridTop);
        boolean compact = gridH < 2 * CARD_H + 6;
        int cardH = compact ? CARD_H_COMPACT : CARD_H;
        int cols = Math.max(1, (a[2] + 6) / (CARD_W + 6));
        int rowsCount = (owned.size() + cols - 1) / cols;
        double maxScroll = Math.max(0, rowsCount * (cardH + 6) - gridH);
        this.listScroll = Math.min(this.listScroll, maxScroll);
        // inertial scroll: cards trail the wheel target on a spring instead of snapping
        float scrollShown = Fx.spring(SCROLL_SPRING, (float) this.listScroll, 140f, 20f);
        if (owned.isEmpty()) {
            UiTheme.text(g, font, t("templates.none"), a[0] + 4, gridTop + 4, UiTheme.E_FAINT);
        }
        for (int i = 0; i < owned.size(); i++) {
            TemplateInstance inst = owned.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = a[0] + col * (CARD_W + 6);
            int y = gridTop + row * (cardH + 6) - (int) scrollShown;
            // no scissor: a card is drawn only when it lies completely inside the grid
            if (y < gridTop || y + cardH > gridTop + gridH) {
                continue;
            }
            TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
            String id = inst.templateId();
            boolean selected = id.equals(selectedId);
            int qc = def == null ? UiTheme.E_EDGE : UiTheme.quality(def.quality().color());
            boolean hover = inside(mx, my, x, y, CARD_W, cardH);
            y -= Math.round(UiTheme.hover(x, y, CARD_W, cardH, hover) * 2f);
            // flip-in on tab entry: the surface opens from its centre line, staggered by index
            float flipP = Fx.flip(UiTheme.enter(tabStartNanos + i * 35_000_000L));
            if (flipP < 1f) {
                int flipW = Math.max(4, Math.round(CARD_W * flipP));
                UiTheme.buttonSurface(g, x + (CARD_W - flipW) / 2, y, flipW, cardH, false, selected, qc);
                continue;
            }
            // soft shadow under the card; a hovered card lifts its shadow
            Fx.softShadow(g, x, y, CARD_W, cardH, UiTheme.E_SHADOW, hover ? 0.5f : 0.18f);
            UiTheme.buttonSurface(g, x, y, CARD_W, cardH, hover, selected, qc);
            if (selected) {
                UiTheme.marching(g, x, y, CARD_W, cardH, UiTheme.E_VIOLET, 1f);
            }
            UiTheme.foil(g, x, y, CARD_W, cardH, qc, (x * 0.37f + i * 0.21f) % 1.0f);
            String name = def == null ? id : def.displayName();
            String lv = def == null ? "" : (def.levelless() ? "" : "Lv." + inst.level());
            if (compact) {
                g.m_280509_(x + 3, y + 3, x + 6, y + cardH - 3, UiTheme.alpha(1f, qc));
                UiTheme.text(g, font, fitText(font, name, CARD_W - 12), x + 9, y + 6, UiTheme.E_INK);
                UiTheme.text(g, font, lv, x + 9, y + 18, UiTheme.E_MUTED);
            } else {
                g.m_280509_(x + 3, y + 3, x + CARD_W - 3, y + 8, UiTheme.alpha(1f, qc));
                UiTheme.cube(g, x + (CARD_W - 22) / 2, y + 16, 22, qc);
                UiTheme.text(g, font, fitText(font, name, CARD_W - 10), x + 5, y + 58, UiTheme.E_INK);
                UiTheme.text(g, font, lv, x + 5, y + 72, UiTheme.E_MUTED);
            }
            if (revealActive() && id.equals(revealId)) {
                float p = (System.currentTimeMillis() - revealStartMs) / (float) REVEAL_MS;
                UiTheme.revealHalo(g, x, y, CARD_W, cardH, 1f, p);
            }
            final String target = id;
            this.hits.add(new Hit(x, y, CARD_W, cardH, () -> selectedId = target));
        }

        // detail card (bottom part)
        UiTheme.frame(g, a[0], dy, a[2], dh, 1f);
        TemplateInstance selectedInst = selectedId == null ? null : data.findTemplate(selectedId).orElse(null);
        TemplateDefinition def = selectedInst == null ? null : TemplateRegistry.client().find(selectedInst.templateId()).orElse(null);
        if (selectedInst == null || def == null) {
            UiTheme.text(g, font, t("templates.details"), a[0] + 10, dy + 10, UiTheme.E_FAINT);
            return;
        }
        int qc = UiTheme.quality(def.quality().color());
        int ix = a[0] + 10;
        int iw = a[2] - 20;
        UiTheme.text(g, font, fitText(font, def.displayName(), iw - 70), ix, dy + 8, UiTheme.E_INK);
        UiTheme.right(g, font, def.quality().label(), a[0] + a[2] - 10, dy + 8, qc);
        String lvText = def.levelless() ? t("templates.levelless") : "Lv." + selectedInst.level() + " / " + def.maxLevel();
        UiTheme.text(g, font, lvText, ix, dy + 20, UiTheme.E_MUTED);
        boolean capped = def.levelless() || selectedInst.level() >= def.maxLevel();
        UiTheme.bar(g, ix, dy + 31, iw, 4, capped ? 1.0 : selectedInst.xp(),
            capped ? 1.0 : ClientSystemData.templateXpToNext(selectedInst.templateId()), qc);

        int by = dy + 40;
        boolean loadedHere = selectedInst.templateId().equals(data.loadedTemplateId());
        boolean cooling = pendingLoadId != null;
        int bw = 74;
        int bx = ix;
        boolean mainOn = loadedHere || !cooling;
        boolean mainHover = inside(mx, my, bx, by, bw, 16);
        UiTheme.buttonSurface(g, bx, by, bw, 16, mainHover && mainOn, loadedHere, UiTheme.E_VIOLET);
        UiTheme.text(g, font, cooling && !loadedHere ? t("btn.load_cd") : t(loadedHere ? "btn.unload" : "btn.load"),
            bx + 6, by + 4, mainOn ? UiTheme.E_INK : UiTheme.E_FAINT);
        final String id = selectedInst.templateId();
        final boolean unloadNow = loadedHere;
        this.hits.add(new Hit(bx, by, bw, 16, () -> {
            if (unloadNow) {
                send("unload", "");
            } else if (pendingLoadId == null) {
                pendingLoadId = id;
                pendingLoadMs = System.currentTimeMillis();
                send("load", id);
            }
        }));
        bx += bw + 6;

        if (def.advanceRequiredPlayerLevel() > 0 || def.maxLevelOverride() > 0) {
            boolean advOn = data.level() >= def.advanceRequiredPlayerLevel();
            boolean advHover = inside(mx, my, bx, by, bw, 16);
            UiTheme.buttonSurface(g, bx, by, bw, 16, advHover && advOn, false, UiTheme.E_JADE);
            UiTheme.text(g, font, t("btn.advance"), bx + 6, by + 4, advOn ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (advOn) {
                this.hits.add(new Hit(bx, by, bw, 16, () -> send("advance", id)));
            }
            bx += bw + 6;
        }
        if (def.lineage() != null) {
            boolean linOn = data.level() >= def.lineage().playerLevel();
            boolean linHover = inside(mx, my, bx, by, bw, 16);
            UiTheme.buttonSurface(g, bx, by, bw, 16, linHover && linOn, false, UiTheme.E_CYAN);
            UiTheme.text(g, font, t("btn.lineage"), bx + 6, by + 4, linOn ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (linOn) {
                this.hits.add(new Hit(bx, by, bw, 16, () -> send("lineage_draw", id)));
            }
        }

        // skills in two columns
        int sy = by + 24;
        UiTheme.text(g, font, t("detail.skills"), ix, sy, UiTheme.E_MUTED);
        sy += 12;
        int colW = iw / 2;
        int idx = 0;
        for (SkillEntry skill : TemplateRegistry.client().effectiveSkills(def.id())) {
            int sx = ix + (idx % 2) * colW;
            int ry = sy + (idx / 2) * 11;
            idx++;
            if (ry > a[1] + a[3] - 10) {
                break;
            }
            boolean unlocked2 = skill.unlockedAt(selectedInst.level());
            UiTheme.block(g, sx, ry + 2, 6, 6, unlocked2 ? UiTheme.E_VIOLET : UiTheme.E_EDGE, unlocked2 ? 1f : 0.5f);
            UiTheme.text(g, font, fitText(font, skill.name(), colW - 62), sx + 10, ry, unlocked2 ? UiTheme.E_INK : UiTheme.E_FAINT);
            double cost = SkillFormula.cost(skill.baseCost(), def.quality());
            String costText = t("v4.cost", NumberFormatter.format(cost)) + " " + (skill.costType() == CostType.SP ? "SP" : "MP");
            UiTheme.right(g, font, costText, sx + colW - 6, ry, unlocked2 ? UiTheme.E_INDIGO : UiTheme.E_FAINT);
        }
    }

    // ------------------------------------------------------------------ codex tab

    private void renderCodex(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        this.search.f_93624_ = true;
        this.search.m_88315_(g, mx, my, 0f);
        int gridTop = a[1] + 22;
        int gridH = a[3] - 22 - DETAIL_STRIP_H - 6;
        int cols = Math.max(1, (a[2] + 6) / (CELL_W + 6));

        String needle = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
        List<TemplateDefinition> defs = new ArrayList<>();
        int ownedCount = 0;
        for (TemplateDefinition def : TemplateRegistry.client().all()) {
            boolean owned = data.findTemplate(def.id()).isPresent();
            if (owned) {
                ownedCount++;
            }
            if (!needle.isEmpty() && !(def.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || def.id().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            defs.add(def);
        }
        int total = TemplateRegistry.client().all().size();
        UiTheme.right(g, font, t("v4.codex_count", ownedCount, total), a[0] + a[2], a[1] + 4, UiTheme.E_MUTED);

        int rowsCount = (defs.size() + cols - 1) / cols;
        double maxScroll = Math.max(0, rowsCount * (CELL_H + 6) - gridH);
        this.codexScroll = Math.min(this.codexScroll, maxScroll);
        if (defs.isEmpty()) {
            UiTheme.text(g, font, t("codex.unknown"), a[0] + 4, gridTop + 4, UiTheme.E_FAINT);
        }
        for (int i = 0; i < defs.size(); i++) {
            TemplateDefinition def = defs.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = a[0] + col * (CELL_W + 6);
            int y = gridTop + row * (CELL_H + 6) - (int) this.codexScroll;
            if (y < gridTop - CELL_H || y > gridTop + gridH) {
                continue;
            }
            boolean owned = data.findTemplate(def.id()).isPresent();
            int qc = UiTheme.quality(def.quality().color());
            boolean selected = def.id().equals(selectedId);
            boolean hover = inside(mx, my, x, y, CELL_W, CELL_H);
            int ly = y - Math.round(UiTheme.hover(x, y, CELL_W, CELL_H, hover && owned) * 2f);
            if (owned) {
                UiTheme.buttonSurface(g, x, ly, CELL_W, CELL_H, hover, selected, qc);
                if (selected) {
                    UiTheme.marching(g, x, ly, CELL_W, CELL_H, UiTheme.E_VIOLET, 1f);
                }
                g.m_280509_(x + 2, ly + 2, x + 5, ly + CELL_H - 2, UiTheme.alpha(1f, qc));
                UiTheme.cube(g, x + CELL_W - 18, ly + 9, 12, qc);
            } else {
                // undiscovered: inset well with diagonal hatching
                UiTheme.inset(g, x, ly, CELL_W, CELL_H);
                int hatch = UiTheme.mix(UiTheme.E_INSET, UiTheme.E_EDGE, 0.35f);
                for (int d = 18; d < CELL_W + CELL_H; d += 22) {
                    UiTheme.line(g, x + d, ly + 2, x + d - (CELL_H - 4), ly + CELL_H - 2, hatch);
                }
            }
            String label = owned ? def.displayName() : t("codex.locked");
            UiTheme.text(g, font, UiTheme.fit(font, label, CELL_W - 26), x + 9, ly + 5,
                owned ? UiTheme.E_INK : UiTheme.E_FAINT);
            UiTheme.text(g, font, UiTheme.fit(font, def.quality().label(), CELL_W - 26), x + 9, ly + 17,
                owned ? qc : UiTheme.E_FAINT);
            final String id = def.id();
            this.hits.add(new Hit(x, y, CELL_W, CELL_H, () -> selectedId = id));
        }

        int sy = a[1] + a[3] - DETAIL_STRIP_H;
        UiTheme.frame(g, a[0], sy, a[2], DETAIL_STRIP_H, 1f);
        TemplateDefinition sel = selectedId == null ? null : TemplateRegistry.client().find(selectedId).orElse(null);
        if (sel != null && data.findTemplate(sel.id()).isPresent()) {
            UiTheme.text(g, font, UiTheme.fit(font, sel.displayName() + "  \u00b7  " + sel.description(), a[2] - 16),
                a[0] + 8, sy + 10, UiTheme.E_INK);
        } else {
            UiTheme.text(g, font, t("codex.locked"), a[0] + 8, sy + 10, UiTheme.E_FAINT);
        }
    }

    // ------------------------------------------------------------------ settings tab

    private void renderSettings(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        boolean terrainAllowed = ClientSystemData.derivedBool("terrainAllowed", true);
        boolean terrainOn = PanelActions.setting(data, "terrain_destruction");

        // group navigation column
        int nw = 96;
        String[] groups = {t("v4.group.hud"), t("v4.group.play"), t("v4.group.display")};
        for (int i = 0; i < groups.length; i++) {
            int y = a[1] + i * 24;
            boolean active = settingsGroup == i;
            boolean hover = inside(mx, my, a[0], y, nw, 20);
            UiTheme.buttonSurface(g, a[0], y, nw, 20, hover, active, UiTheme.E_VIOLET);
            UiTheme.text(g, font, groups[i], a[0] + 8, y + 6, active ? UiTheme.E_VIOLET : UiTheme.E_MUTED);
            final int target = i;
            this.hits.add(new Hit(a[0], y, nw, 20, () -> {
                settingsGroup = target;
                this.settingsScroll = 0.0;
            }));
        }
        // active-group marker glides between the group buttons
        float groupY = UiTheme.approach(GROUP_GLIDE, settingsGroup * 24.0f);
        int gyPx = a[1] + Math.round(groupY);
        g.m_280509_(a[0] + 1, gyPx + 3, a[0] + 4, gyPx + 17, UiTheme.alpha(1f, UiTheme.E_VIOLET));
        int tools = a[1] + groups.length * 24 + 10;
        boolean hover1 = inside(mx, my, a[0], tools, nw, 20);
        UiTheme.buttonSurface(g, a[0], tools, nw, 20, hover1, false, UiTheme.E_CYAN);
        UiTheme.text(g, font, t("v4.set.controls"), a[0] + 8, tools + 6, UiTheme.E_INK);
        this.hits.add(new Hit(a[0], tools, nw, 20, () -> {
            Minecraft mc = Minecraft.m_91087_();
            mc.m_91152_(new ControlsScreen(this, mc.f_91066_));
        }));
        int tools2 = tools + 24;
        boolean hover2 = inside(mx, my, a[0], tools2, nw, 20);
        UiTheme.buttonSurface(g, a[0], tools2, nw, 20, hover2, false, UiTheme.E_CYAN);
        UiTheme.text(g, font, t("v4.set.hud_editor"), a[0] + 8, tools2 + 6, UiTheme.E_INK);
        this.hits.add(new Hit(a[0], tools2, nw, 20, () -> Minecraft.m_91087_().m_91152_(new HudEditorScreen(this))));

        // rows of the selected group
        List<SettingRow> rows = new ArrayList<>();
        if (settingsGroup == 0) {
            rows.add(new SettingRow("v4.set.hud", cfg(ModConfigs.CLIENT.hudEnabled), true, () -> toggleCfg(ModConfigs.CLIENT.hudEnabled)));
            rows.add(new SettingRow("v4.set.xp", cfg(ModConfigs.CLIENT.xpPopups), true, () -> toggleCfg(ModConfigs.CLIENT.xpPopups)));
            rows.add(new SettingRow("v4.set.details", UiPreferences.hudDetails, true, () -> {
                UiPreferences.hudDetails = !UiPreferences.hudDetails;
                UiPreferences.save();
            }));
        } else if (settingsGroup == 1) {
            rows.add(new SettingRow("v4.set.page", cfg(ModConfigs.CLIENT.skillPageAutoReturn), true, () -> toggleCfg(ModConfigs.CLIENT.skillPageAutoReturn)));
            rows.add(new SettingRow("v4.set.terrain", terrainOn && terrainAllowed, terrainAllowed, () -> {
                if (!terrainAllowed) {
                    return;
                }
                boolean next = !PanelActions.setting(data, "terrain_destruction");
                data.setValue("setting.terrain_destruction", next ? 1.0 : 0.0);
                send("setting", "terrain_destruction=" + (next ? 1 : 0));
            }));
        } else {
            rows.add(new SettingRow("v4.set.reduced", UiPreferences.reducedMotion, true, () -> {
                UiPreferences.reducedMotion = !UiPreferences.reducedMotion;
                UiPreferences.save();
            }));
            rows.add(new SettingRow("v4.set.aura", cfg(ModConfigs.CLIENT.rainbowAura), true, () -> toggleCfg(ModConfigs.CLIENT.rainbowAura)));
        }

        int rx = a[0] + nw + 8;
        int rw = a[2] - nw - 8;
        int rowH = 24;
        UiTheme.frame(g, rx, a[1], rw, a[3], 1f);
        int rowTop = a[1] + 8;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int y = rowTop + i * (rowH + 4);
            boolean hover = inside(mx, my, rx + 6, y, rw - 12, rowH) && row.enabled;
            UiTheme.buttonSurface(g, rx + 6, y, rw - 12, rowH, hover, false, UiTheme.E_VIOLET);
            UiTheme.text(g, font, t(row.labelKey), rx + 16, y + 7, row.enabled ? UiTheme.E_INK : UiTheme.E_FAINT);
            int trackW = 26;
            int trackH = 12;
            int trackX = rx + rw - 16 - trackW;
            int trackY = y + (rowH - trackH) / 2;
            // the knob glides between off and on; the key is per label so it survives group switches
            float knob = UiTheme.approach(SETTING_GLIDE + (row.labelKey().hashCode() & 0xFFFF), row.on ? 1f : 0f);
            drawSwitch(g, trackX, trackY, trackW, trackH, knob, row.enabled);
            if (!row.enabled) {
                UiTheme.right(g, font, t("v4.set.terrain_locked"), trackX - 8, y + 7, UiTheme.E_FAINT);
            }
            if (row.enabled) {
                this.hits.add(new Hit(rx + 6, y, rw - 12, rowH, row.action));
            }
        }
    }

    /** Pill switch with a gliding knob. {@code knob} runs 0 (off) to 1 (on). */
    private static void drawSwitch(GuiGraphics g, int x, int y, int w, int h, float knob, boolean enabled) {
        float k = Math.max(0f, Math.min(1f, knob));
        float a = enabled ? 1f : 0.45f;
        int track = UiTheme.mix(UiTheme.E_INSET, UiTheme.E_JADE, k);
        g.m_280509_(x + 1, y + 1, x + w - 1, y + h - 1, UiTheme.alpha(a, track));
        UiTheme.border(g, x, y, w, h, UiTheme.E_EDGE);
        int kx = x + 2 + Math.round(k * (w - h));
        int ks = h - 4;
        UiTheme.border(g, kx, y + 2, ks, ks, UiTheme.E_EDGE);
        g.m_280509_(kx + 1, y + 3, kx + ks - 1, y + ks + 1, UiTheme.alpha(a, UiTheme.E_CARD));
    }

    private static boolean cfg(ForgeConfigSpec.BooleanValue value) {
        return (Boolean) value.get();
    }

    private static void toggleCfg(ForgeConfigSpec.BooleanValue value) {
        value.set(!(Boolean) value.get());
        value.save();
    }

    private record SettingRow(String labelKey, boolean on, boolean enabled, Runnable action) {
    }

    // ------------------------------------------------------------------ small helper

    private static String fitText(Font font, String s, int maxWidth) {
        return UiTheme.fit(font, s, maxWidth);
    }
}
