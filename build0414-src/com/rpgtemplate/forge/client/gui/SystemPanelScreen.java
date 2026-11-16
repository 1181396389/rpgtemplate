package com.rpgtemplate.forge.client.gui;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.NumberFormatter;
import com.rpgtemplate.core.template.CostType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.cronus.CronusGearManager;
import com.rpgtemplate.cronus.CronusItems;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.core.ui.UiLayout;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.client.ClientKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import com.rpgtemplate.forge.mask.PerceptionBlockingMaskItem;
import com.rpgtemplate.forge.mask.PerceptionMaskBootstrap;
import com.rpgtemplate.forge.mask.PerceptionMaskManager;
import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.PanelActionPayload;
import com.rpgtemplate.forge.skill.RctFatigue;
import com.rpgtemplate.forge.system.PanelActions;
import com.rpgtemplate.forge.system.TravelerVaultStorage;
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
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * K-key system panel (v4, 次元 × 空间 × 维度).
 *
 * <p>Four tabs (status, templates, codex, settings) keep the same server protocol as before:
 * {@code load}, {@code unload}, {@code advance}, {@code draw}, {@code lineage_draw} and {@code setting}.
 * Every colour goes through {@link UiTheme}. Layout adapts responsively to small viewports / large
 * GUI scales with CJK-aware multi-line wrapping, independent detail scrolling, and hover tooltips.
 */
public final class SystemPanelScreen extends Screen {
    private static final int HEADER_H = 34;
    private static final int TAB_H = 18;
    private static final int PAD = 12;
    private static final int ROW_H = 22;
    private static final int MIN_CELL_W = 148;
    private static final int CELL_H = 32;
    private static final long REVEAL_MS = 2600L;
    private static final long TAB_GLIDE = 0x7AB0000000000001L;
    private static final long STATUS_GLIDE = 0x51A7000000000000L;
    private static final long STATUS_SPRING = 0x51A7500000000000L;
    private static final long SCROLL_SPRING = 0x5C20000000000001L;
    private static final long GROUP_GLIDE = 0x6C0B000000000001L;
    private static final long SETTING_GLIDE = 0x5E77000000000000L;

    private static Tab tab = Tab.STATUS;
    private static String selectedId;
    private static String selectedItemId = "perception_blocking_mask";
    private static int detailTab;
    private static String revealId;
    private static long revealStartMs = -1L;
    private static boolean revealPending;
    private static String pendingLoadId;
    private static int settingsGroup;
    private static long pendingLoadMs;
    private static long tabStartNanos;

    private final List<Hit> hits = new ArrayList<>();
    private List<Component> pendingTooltip;
    private int left;
    private int top;
    private int pw;
    private int ph;
    private int ticks;
    private double listScroll;
    private double detailScroll;
    private int lastDetailY;
    private int lastDetailH;
    private double itemsScroll;
    private double itemsDetailScroll;
    private int lastItemsDetailY;
    private int lastItemsDetailH;
    private double codexScroll;
    private double codexDetailScroll;
    private int lastCodexDetailY;
    private int lastCodexDetailH;
    private double settingsScroll;
    private EditBox search;
    private String query = "";

    private enum Tab {
        STATUS, TEMPLATES, ITEMS, CODEX, SETTINGS
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
        // On small/high-GUI-scale viewports, use nearly the full window width/height with minimal margin
        int w = Math.max(rect.w(), Math.min(760, Math.max(220, this.f_96543_ - 12)));
        int h = Math.max(rect.h(), Math.min(460, Math.max(180, this.f_96544_ - 12)));
        w = Math.min(w, Math.max(1, this.f_96543_ - 8));
        h = Math.min(h, Math.max(1, this.f_96544_ - 8));
        this.pw = w;
        this.ph = h;
        this.left = (this.f_96543_ - w) / 2;
        this.top = (this.f_96544_ - h) / 2;
        int sx = this.left + PAD;
        int sy = this.top + contentTop();
        this.search = new EditBox(this.f_96547_, sx, sy, Math.max(90, Math.min(220, this.pw / 3)), 16,
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
            this.codexDetailScroll = 0.0;
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
        if (key >= 49 && key <= 53) {
            switchTab(Tab.values()[key - 49]);
            return true;
        }
        // Up/Down/PageUp/PageDown scroll active detail panel on small screens
        if (key == 265 || key == 264 || key == 266 || key == 267) {
            double delta = (key == 265 ? -18.0 : key == 264 ? 18.0 : key == 266 ? -54.0 : 54.0);
            if (tab == Tab.TEMPLATES) {
                this.detailScroll = Math.max(0.0, this.detailScroll + delta);
                return true;
            } else if (tab == Tab.ITEMS) {
                this.itemsDetailScroll = Math.max(0.0, this.itemsDetailScroll + delta);
                return true;
            } else if (tab == Tab.CODEX) {
                this.codexDetailScroll = Math.max(0.0, this.codexDetailScroll + delta);
                return true;
            } else if (tab == Tab.SETTINGS) {
                this.settingsScroll = Math.max(0.0, this.settingsScroll + delta);
                return true;
            }
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
            case TEMPLATES -> {
                if (this.lastDetailH > 0 && inside(mx, my, area[0], this.lastDetailY, area[2], this.lastDetailH)) {
                    this.detailScroll = Math.max(0.0, this.detailScroll + step);
                } else {
                    this.listScroll = Math.max(0.0, this.listScroll + step);
                }
            }
            case ITEMS -> {
                if (this.lastItemsDetailH > 0 && inside(mx, my, area[0], this.lastItemsDetailY, area[2], this.lastItemsDetailH)) {
                    this.itemsDetailScroll = Math.max(0.0, this.itemsDetailScroll + step);
                } else {
                    this.itemsScroll = Math.max(0.0, this.itemsScroll + step);
                }
            }
            case CODEX -> {
                if (this.lastCodexDetailH > 0 && inside(mx, my, area[0], this.lastCodexDetailY, area[2], this.lastCodexDetailH)) {
                    this.codexDetailScroll = Math.max(0.0, this.codexDetailScroll + step);
                } else {
                    this.codexScroll = Math.max(0.0, this.codexScroll + step);
                }
            }
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
        this.pendingTooltip = null;
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
            case STATUS -> renderStatus(g, data, mx, my);
            case TEMPLATES -> renderTemplates(g, data, mx, my);
            case ITEMS -> renderItems(g, data, mx, my);
            case CODEX -> renderCodex(g, data, mx, my);
            case SETTINGS -> renderSettings(g, data, mx, my);
        }
        UiTheme.enterPop(g);
        renderFooter(g, mx, my);
        super.m_88315_(g, mx, my, partial);
        if (this.pendingTooltip != null && !this.pendingTooltip.isEmpty()) {
            g.m_280666_(this.f_96547_, this.pendingTooltip, mx, my);
        }
    }

    private void renderHeader(GuiGraphics g, PlayerSystemData data) {
        Font font = this.f_96547_;
        UiTheme.emblem(g, this.left + PAD, this.top + 10, UiTheme.E_GOLD);
        UiTheme.orbit(g, this.left + PAD + 4, this.top + 14, 7, UiTheme.E_CYAN, 1f, UiTheme.seconds() * 1.3);
        String lvText = "Lv." + data.level();
        int lvW = font.m_92895_(lvText);
        String titleStr = t("title");
        int availTitle = Math.max(40, this.pw - PAD * 2 - 22 - lvW);
        float titleScale = font.m_92895_(titleStr) * 2 <= availTitle ? 2.0f
            : (font.m_92895_(titleStr) * 3 / 2 <= availTitle ? 1.5f : 1.0f);
        int ty = titleScale >= 1.9f ? this.top + 9 : (titleScale >= 1.4f ? this.top + 10 : this.top + 12);
        g.m_280168_().m_85836_();
        g.m_280168_().m_252880_((float) (this.left + PAD + 14), (float) ty, 0.0f);
        g.m_280168_().m_85841_(titleScale, titleScale, 1.0f);
        UiTheme.text(g, font, fitText(font, titleStr, (int) (availTitle / titleScale)), 0, 0, UiTheme.E_INK);
        g.m_280168_().m_85849_();
        UiTheme.right(g, font, lvText, this.left + this.pw - PAD, this.top + 11, UiTheme.E_VIOLET);
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
            String shown = fitText(this.f_96547_, label, Math.max(16, bw - 12));
            UiTheme.text(g, this.f_96547_, shown, bx + 6, by + 5, active ? UiTheme.E_VIOLET : UiTheme.E_MUTED);
            if (hover && !shown.equals(label)) {
                offerPlainTooltip(label, UiTheme.E_INK);
            }
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

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int y = this.top + this.ph - 14;
        UiTheme.seam(g, this.left + PAD, y - 6, this.pw - PAD * 2, UiTheme.alpha(0.35f, UiTheme.E_CYAN));
        int avail = this.pw - PAD * 2;
        String full = t("v4.footer");
        String msg = UiTheme.fit(this.f_96547_, full, avail);
        UiTheme.text(g, this.f_96547_, msg, this.left + PAD, y, UiTheme.E_FAINT);
        if (!msg.equals(full) && inside(mx, my, this.left + PAD, y - 2, avail, 13)) {
            offerPlainTooltip(full, UiTheme.E_MUTED);
        }
    }

    // ------------------------------------------------------------------ status tab

    private void renderStatus(GuiGraphics g, PlayerSystemData data, int mx, int my) {
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
            int nameEnd = a[0] + 22 + font.m_92895_(names[i]);
            int valBudget = Math.max(32, rightEdge - nameEnd);
            String fullVal = nowText + maxText;
            if (nowText.equals(String.valueOf((long) Math.max(0.0, shownNow))) && font.m_92895_(fullVal) <= valBudget) {
                // plain integer: the current value rolls like an odometer
                UiTheme.right(g, font, maxText, rightEdge, textY, UiTheme.E_INK);
                Fx.odometer(g, font, Math.max(0.0, shownNow), rightEdge - font.m_92895_(maxText), textY, UiTheme.E_INK);
            } else {
                String fittedVal = UiTheme.fit(font, fullVal, valBudget);
                UiTheme.right(g, font, fittedVal, rightEdge, textY, UiTheme.E_INK);
                if (!fittedVal.equals(fullVal) && inside(mx, my, a[0], y, lw, rowH)) {
                    offerPlainTooltip(names[i] + ": " + fullVal, UiTheme.E_INK);
                }
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
            String kShown = fitText(font, stats[i][0], tileW - 10);
            String vShown = fitText(font, stats[i][1], tileW - 10);
            UiTheme.text(g, font, kShown, x + 6, y + 4, UiTheme.E_MUTED);
            UiTheme.text(g, font, vShown, x + 6, y + tileH - 12, UiTheme.E_INK);
            if ((!kShown.equals(stats[i][0]) || !vShown.equals(stats[i][1])) && inside(mx, my, x, y, tileW, tileH)) {
                offerPlainTooltip(stats[i][0] + ": " + stats[i][1], UiTheme.E_INK);
            }
        }

        int by = a[1] + a[3] - footH;
        UiTheme.frame(g, a[0], by, a[2], footH, 1f);
        Minecraft mc = Minecraft.m_91087_();
        long tick = mc.f_91073_ == null ? 0L : mc.f_91073_.m_46467_();
        UiTheme.text(g, font, t("v4.status.loaded"), a[0] + 8, by + 6, UiTheme.E_MUTED);
        boolean rct = RctFatigue.active(data, tick);
        String rctText = rct ? t("status.rct_active", String.format(Locale.ROOT, "%.2f", RctFatigue.factor(data, tick))) : "";
        int rctReserve = rct ? font.m_92895_(rctText) + 16 : 16;
        TemplateInstance loadedInst = data.loadedTemplate().orElse(null);
        TemplateDefinition loadedDef = loadedInst == null ? null
            : TemplateRegistry.client().find(loadedInst.templateId()).orElse(null);
        String loaded = loadedDef == null ? t("status.no_template")
            : loadedDef.displayName() + "  Lv." + loadedInst.level();
        String loadedShown = fitText(font, loaded, Math.max(40, a[2] - 16 - rctReserve));
        UiTheme.text(g, font, loadedShown, a[0] + 8, by + 18, UiTheme.E_INK);
        if (loadedDef != null && inside(mx, my, a[0], by, a[2], footH) && !loadedShown.equals(loaded)) {
            offerTemplateTooltip(loadedDef, loadedInst, TemplateIntrospector.inspect(loadedDef));
        }
        if (rct) {
            UiTheme.right(g, font, rctText, a[0] + a[2] - 8, by + 18, UiTheme.E_INDIGO);
        }
    }

    // ------------------------------------------------------------------ templates tab (vertical cards)

    private static final int MIN_CARD_W = 104;
    private static final int CARD_H = 84;
    private static final int CARD_H_COMPACT = 36;

    /** Template that gates the next draw: newest owned, levelled template below its attunement / max level. */
    private static String gateTemplateName(List<TemplateInstance> owned) {
        for (int i = owned.size() - 1; i >= 0; i--) {
            TemplateInstance inst = owned.get(i);
            TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
            if (def != null && !def.levelless() && inst.level() < def.maxLevel()) {
                int attune = Math.min(def.maxLevel(), 10);
                return def.name() + " Lv." + inst.level() + "/" + attune;
            }
        }
        return t("status.draw_gate_fallback");
    }

    private void renderTemplates(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        List<TemplateInstance> owned = data.templates();

        // header row: caption on the left, draw state in the middle, draw button on the right
        String ownedLabel = t("templates.owned", owned.size());
        UiTheme.text(g, font, ownedLabel, a[0], a[1] + 4, UiTheme.E_MUTED);
        boolean unlocked = data.isDrawUnlocked();
        int pending = data.pendingDraws();
        String drawState = !unlocked ? t("status.draw_locked_attune", pending, gateTemplateName(owned))
            : pending > 0 ? t("status.draw_ready", pending) : t("status.draw_none");
        int drawW = Math.max(72, Math.min(110, font.m_92895_(t("btn.draw")) + 18));
        int drawX = a[0] + a[2] - drawW;
        int stateX = a[0] + font.m_92895_(ownedLabel) + 10;
        int stateBudget = Math.max(24, drawX - stateX - 8);
        String stateShown = fitText(font, drawState, stateBudget);
        UiTheme.text(g, font, stateShown, stateX, a[1] + 4,
            unlocked && pending > 0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_MUTED);
        if (!stateShown.equals(drawState) && inside(mx, my, stateX, a[1], stateBudget, 16)) {
            offerPlainTooltip(drawState, unlocked && pending > 0 ? UiTheme.E_GOLD_TEXT : UiTheme.E_INK);
        }
        boolean drawOn = unlocked && pending > 0;
        boolean drawHover = inside(mx, my, drawX, a[1], drawW, 16);
        UiTheme.buttonSurface(g, drawX, a[1], drawW, 16, drawHover && drawOn, false, UiTheme.E_GOLD);
        if (drawOn) {
            UiTheme.marching(g, drawX, a[1], drawW, 16, UiTheme.E_GOLD_TEXT, 1f);
        }
        UiTheme.text(g, font, fitText(font, t("btn.draw"), drawW - 12), drawX + 6, a[1] + 4, drawOn ? UiTheme.E_INK : UiTheme.E_FAINT);
        if (drawOn) {
            this.hits.add(new Hit(drawX, a[1], drawW, 16, () -> send("draw", "")));
        }

        // detail card is sized generously so wrapped skills/descriptions have comfortable room
        int detailH = Math.max(106, Math.min(200, a[3] * 58 / 100));
        int dy = a[1] + a[3] - detailH;
        int dh = detailH;
        this.lastDetailY = dy;
        this.lastDetailH = dh;
        int gridTop = a[1] + 22;
        int gridH = Math.max(0, dy - 6 - gridTop);
        boolean compact = gridH < 2 * CARD_H + 6;
        int cardH = compact ? CARD_H_COMPACT : CARD_H;
        int gap = 6;
        int minW = compact ? MIN_CARD_W : 92;
        int cols = Math.max(1, (a[2] + gap) / (minW + gap));
        int cardW = Math.max(76, (a[2] - gap * (cols - 1)) / cols);
        int rowsCount = (owned.size() + cols - 1) / cols;
        double maxScroll = Math.max(0, rowsCount * (cardH + gap) - gridH);
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
            int x = a[0] + col * (cardW + gap);
            int y = gridTop + row * (cardH + gap) - (int) scrollShown;
            // no scissor: a card is drawn only when it lies completely inside the grid
            if (y < gridTop || y + cardH > gridTop + gridH) {
                continue;
            }
            TemplateDefinition def = TemplateRegistry.client().find(inst.templateId()).orElse(null);
            String id = inst.templateId();
            boolean selected = id.equals(selectedId);
            int qc = def == null ? UiTheme.E_EDGE : UiTheme.templateColor(def);
            int qText = def == null ? UiTheme.E_MUTED : UiTheme.templateTextColor(def);
            boolean hover = inside(mx, my, x, y, cardW, cardH);
            int drawY = y - Math.round(UiTheme.hover(x, y, cardW, cardH, hover) * 2f);
            // flip-in on tab entry: the surface opens from its centre line, staggered by index
            float flipP = Fx.flip(UiTheme.enter(tabStartNanos + i * 35_000_000L));
            if (flipP < 1f) {
                int flipW = Math.max(4, Math.round(cardW * flipP));
                UiTheme.buttonSurface(g, x + (cardW - flipW) / 2, drawY, flipW, cardH, false, selected, qc);
                continue;
            }
            // soft shadow under the card; a hovered card lifts its shadow
            Fx.softShadow(g, x, drawY, cardW, cardH, UiTheme.E_SHADOW, hover ? 0.5f : 0.18f);
            UiTheme.buttonSurface(g, x, drawY, cardW, cardH, hover, selected, qc);
            if (selected) {
                UiTheme.marching(g, x, drawY, cardW, cardH, UiTheme.E_VIOLET, 1f);
            }
            UiTheme.foil(g, x, drawY, cardW, cardH, qc, (x * 0.37f + i * 0.21f) % 1.0f);
            String primaryName = def == null ? id : def.name();
            String period = def == null ? "" : TemplateIntrospector.inspect(def).period();
            String lv = def == null ? "" : (def.levelless() ? def.quality().label() : "Lv." + inst.level());
            String subLine = period.isEmpty() ? lv : (lv.isEmpty() ? period : lv + " \u00b7 " + period);
            if (compact) {
                g.m_280509_(x + 3, drawY + 3, x + 6, drawY + cardH - 3, UiTheme.alpha(1f, qc));
                int qChipW = def == null ? 0 : font.m_92895_(def.quality().label()) + 6;
                UiTheme.text(g, font, fitText(font, primaryName, Math.max(24, cardW - 14 - qChipW)), x + 9, drawY + 6, UiTheme.E_INK);
                if (def != null) {
                    UiTheme.rightExact(g, font, def.quality().label(), x + cardW - 5, drawY + 6, qText);
                }
                UiTheme.text(g, font, fitText(font, subLine, Math.max(24, cardW - 14)), x + 9, drawY + 19, UiTheme.E_MUTED);
            } else {
                g.m_280509_(x + 3, drawY + 3, x + cardW - 3, drawY + 8, UiTheme.alpha(1f, qc));
                UiTheme.cube(g, x + (cardW - 22) / 2, drawY + 14, 22, qc);
                UiTheme.text(g, font, fitText(font, primaryName, cardW - 10), x + 5, drawY + 46, UiTheme.E_INK);
                if (!period.isEmpty()) {
                    UiTheme.text(g, font, fitText(font, period, cardW - 10), x + 5, drawY + 58, UiTheme.E_FAINT);
                }
                UiTheme.text(g, font, fitText(font, lv, cardW - 32), x + 5, drawY + 70, UiTheme.E_MUTED);
                if (def != null) {
                    UiTheme.rightExact(g, font, def.quality().label(), x + cardW - 5, drawY + 70, qText);
                }
            }
            if (hover && def != null) {
                offerTemplateTooltip(def, inst, TemplateIntrospector.inspect(def));
            }
            if (revealActive() && id.equals(revealId)) {
                float p = (System.currentTimeMillis() - revealStartMs) / (float) REVEAL_MS;
                UiTheme.revealHalo(g, x, drawY, cardW, cardH, 1f, p);
            }
            final String target = id;
            this.hits.add(new Hit(x, y, cardW, cardH, () -> {
                selectedId = target;
                this.detailScroll = 0.0;
            }));
        }
        if (maxScroll > 0.5 && gridH > 12) {
            int trackX = a[0] + a[2] - 2;
            g.m_280509_(trackX, gridTop, trackX + 2, gridTop + gridH, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) gridH * gridH / (rowsCount * (cardH + gap))));
            int thumbY = gridTop + (int) Math.round((this.listScroll / maxScroll) * (gridH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.85f, UiTheme.E_VIOLET));
        }

        // detail card (bottom part)
        UiTheme.frame(g, a[0], dy, a[2], dh, 1f);
        TemplateInstance selectedInst = selectedId == null ? null : data.findTemplate(selectedId).orElse(null);
        TemplateDefinition def = selectedInst == null ? null : TemplateRegistry.client().find(selectedInst.templateId()).orElse(null);
        if (selectedInst == null || def == null) {
            UiTheme.text(g, font, t("templates.details"), a[0] + 10, dy + 10, UiTheme.E_FAINT);
            return;
        }
        TemplateIntrospector.Meta meta = TemplateIntrospector.inspect(def);
        int qc = UiTheme.templateColor(def);
        int qText = UiTheme.templateTextColor(def);
        int ix = a[0] + 10;
        int iw = a[2] - 20;
        String titleLine = composeTitle(def, meta);
        String qLabel = def.quality().label();
        int qReserve = font.m_92895_(qLabel) + 12;
        String titleShown = fitText(font, titleLine, Math.max(40, iw - qReserve));
        UiTheme.text(g, font, titleShown, ix, dy + 7, UiTheme.E_INK);
        if (!titleShown.equals(titleLine) && inside(mx, my, ix, dy + 5, iw - qReserve, 12)) {
            offerTemplateTooltip(def, selectedInst, meta);
        }
        UiTheme.rightExact(g, font, qLabel, a[0] + a[2] - 10, dy + 7, qText);
        String lvText = def.levelless() ? t("templates.levelless") : "Lv." + selectedInst.level() + " / " + def.maxLevel();
        UiTheme.text(g, font, lvText, ix, dy + 19, UiTheme.E_MUTED);
        boolean capped = def.levelless() || selectedInst.level() >= def.maxLevel();
        UiTheme.bar(g, ix, dy + 29, iw, 3, capped ? 1.0 : selectedInst.xp(),
            capped ? 1.0 : ClientSystemData.templateXpToNext(selectedInst.templateId()), qc);

        int by = dy + 35;
        boolean loadedHere = selectedInst.templateId().equals(data.loadedTemplateId());
        boolean cooling = pendingLoadId != null;
        int bw = Math.max(46, Math.min(64, iw / 7));
        int bx = ix;
        boolean mainOn = loadedHere || !cooling;
        boolean mainHover = inside(mx, my, bx, by, bw, 15);
        UiTheme.buttonSurface(g, bx, by, bw, 15, mainHover && mainOn, loadedHere, UiTheme.E_VIOLET);
        String mainLbl = cooling && !loadedHere ? t("btn.load_cd") : t(loadedHere ? "btn.unload" : "btn.load");
        UiTheme.text(g, font, fitText(font, mainLbl, bw - 8), bx + 4, by + 3, mainOn ? UiTheme.E_INK : UiTheme.E_FAINT);
        final String id = selectedInst.templateId();
        final boolean unloadNow = loadedHere;
        this.hits.add(new Hit(bx, by, bw, 15, () -> {
            if (unloadNow) {
                send("unload", "");
            } else if (pendingLoadId == null) {
                pendingLoadId = id;
                pendingLoadMs = System.currentTimeMillis();
                send("load", id);
            }
        }));
        bx += bw + 4;

        java.util.Optional<TemplateDefinition> nextTier = TemplateRegistry.client().advancementOf(def.id());
        if (nextTier.isPresent()) {
            boolean atMax = selectedInst.level() >= def.maxLevel();
            boolean advOn = atMax && data.level() >= nextTier.get().advanceRequiredPlayerLevel();
            boolean advHover = inside(mx, my, bx, by, bw, 15);
            UiTheme.buttonSurface(g, bx, by, bw, 15, advHover && advOn, false, UiTheme.E_JADE);
            UiTheme.text(g, font, fitText(font, t("btn.advance"), bw - 8), bx + 4, by + 3, advOn ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (advOn) {
                this.hits.add(new Hit(bx, by, bw, 15, () -> send("advance", id)));
            }
            bx += bw + 4;
        }
        if (def.lineage() != null) {
            boolean atMax = def.levelless() || selectedInst.level() >= def.maxLevel();
            boolean linOn = atMax && data.level() >= def.lineage().playerLevel()
                && data.isDrawUnlocked() && data.pendingDraws() > 0;
            boolean linHover = inside(mx, my, bx, by, bw, 15);
            UiTheme.buttonSurface(g, bx, by, bw, 15, linHover && linOn, false, UiTheme.E_CYAN);
            UiTheme.text(g, font, fitText(font, t("btn.lineage"), bw - 8), bx + 4, by + 3, linOn ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (linOn) {
                this.hits.add(new Hit(bx, by, bw, 15, () -> send("lineage_draw", id)));
            }
            bx += bw + 4;
        }

        // Sub-tabs: [技能] [被动/加成] [弱点/癖好] — auto-drop to next row on narrow screens so nothing overlaps
        List<SkillEntry> skills = TemplateRegistry.client().effectiveSkills(def.id());
        String[] subLabels = {
            t("detail.skills") + "(" + skills.size() + ")",
            t("detail.passives") + "(" + meta.passives().size() + ")",
            t("detail.drawbacks_tab") + "(" + meta.drawbackCount() + ")"
        };
        int[] subColors = {UiTheme.E_VIOLET, UiTheme.E_JADE, UiTheme.E_RED};
        int[] subWidths = new int[subLabels.length];
        int totalSubW = 0;
        for (int s = 0; s < subLabels.length; s++) {
            subWidths[s] = font.m_92895_(subLabels[s]) + 10;
            totalSubW += subWidths[s] + (s > 0 ? 4 : 0);
        }
        boolean wrapSubTabs = bx + 6 + totalSubW > a[0] + a[2] - 10;
        int subY = wrapSubTabs ? by + 18 : by;
        if (wrapSubTabs) {
            int sxLeft = ix;
            for (int s = 0; s < subLabels.length; s++) {
                int sw = Math.min(subWidths[s], Math.max(36, (iw - 8) / 3));
                boolean activeSub = detailTab == s;
                boolean hoverSub = inside(mx, my, sxLeft, subY, sw, 15);
                UiTheme.buttonSurface(g, sxLeft, subY, sw, 15, hoverSub, activeSub, subColors[s]);
                UiTheme.text(g, font, fitText(font, subLabels[s], sw - 6), sxLeft + 3, subY + 3, activeSub ? subColors[s] : UiTheme.E_MUTED);
                final int targetSub = s;
                this.hits.add(new Hit(sxLeft, subY, sw, 15, () -> {
                    detailTab = targetSub;
                    this.detailScroll = 0.0;
                }));
                sxLeft += sw + 4;
            }
        } else {
            int sxRight = a[0] + a[2] - 10;
            for (int s = subLabels.length - 1; s >= 0; s--) {
                int sw = subWidths[s];
                sxRight -= sw;
                boolean activeSub = detailTab == s;
                boolean hoverSub = inside(mx, my, sxRight, subY, sw, 15);
                UiTheme.buttonSurface(g, sxRight, subY, sw, 15, hoverSub, activeSub, subColors[s]);
                UiTheme.text(g, font, subLabels[s], sxRight + 5, subY + 3, activeSub ? subColors[s] : UiTheme.E_MUTED);
                final int targetSub = s;
                this.hits.add(new Hit(sxRight, subY, sw, 15, () -> {
                    detailTab = targetSub;
                    this.detailScroll = 0.0;
                }));
                sxRight -= 4;
            }
        }

        int bodyTop = subY + 19;
        int bodyBottom = dy + dh - 6;
        int bodyH = Math.max(12, bodyBottom - bodyTop);
        int textBudget = Math.max(60, iw - 14);
        List<DetailLine> lines = buildDetailLines(font, def, selectedInst, meta, skills, textBudget);
        int lineH = 11;
        double maxDetailScroll = Math.max(0.0, lines.size() * lineH - bodyH);
        this.detailScroll = Math.min(this.detailScroll, maxDetailScroll);
        int scrollPx = (int) Math.round(this.detailScroll);
        for (int i = 0; i < lines.size(); i++) {
            int ry = bodyTop + i * lineH - scrollPx;
            if (ry < bodyTop || ry + 9 > bodyBottom) {
                continue;
            }
            DetailLine dl = lines.get(i);
            if (dl.showDot()) {
                UiTheme.block(g, ix, ry + 2, 4, 5, dl.dotColor(), 0.9f);
            }
            int tx = ix + (dl.showDot() ? 8 : 10);
            int rightReserve = dl.rightText().isEmpty() ? 0 : font.m_92895_(dl.rightText()) + 8;
            UiTheme.text(g, font, fitText(font, dl.leftText(), Math.max(24, ix + iw - 6 - tx - rightReserve)), tx, ry, dl.textColor());
            if (!dl.rightText().isEmpty()) {
                UiTheme.right(g, font, dl.rightText(), ix + iw - 6, ry, dl.rightColor());
            }
        }
        if (maxDetailScroll > 0.5) {
            int trackX = a[0] + a[2] - 4;
            g.m_280509_(trackX, bodyTop, trackX + 2, bodyBottom, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) bodyH * bodyH / (lines.size() * lineH)));
            int thumbY = bodyTop + (int) Math.round((this.detailScroll / maxDetailScroll) * (bodyH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.9f, qc));
        }
    }

    private record DetailLine(boolean showDot, int dotColor, String leftText, int textColor, String rightText, int rightColor) {
    }

    private List<DetailLine> buildDetailLines(Font font, TemplateDefinition def, TemplateInstance inst,
                                              TemplateIntrospector.Meta meta, List<SkillEntry> skills, int maxW) {
        List<DetailLine> out = new ArrayList<>();
        int indentW = Math.max(48, maxW - 4);
        if (detailTab == 0) {
            if (skills.isEmpty()) {
                out.add(new DetailLine(true, UiTheme.E_EDGE, t("detail.none"), UiTheme.E_FAINT, "", UiTheme.E_FAINT));
                return out;
            }
            for (SkillEntry skill : skills) {
                boolean ok = skill.unlockedAt(inst.level());
                double cost = SkillFormula.cost(skill.baseCost(), def.quality());
                String res = skill.costType() == CostType.SP ? "SP" : "MP";
                double cdSec = skill.cooldownTicks() / 20.0;
                String right = NumberFormatter.format(cost) + " " + res
                    + (cdSec > 0.0 ? String.format(Locale.ROOT, " \u00b7 %.1fs", cdSec) : "");
                String prefix = ok ? "" : "[Lv." + skill.unlockLevel() + "] ";
                int dotCol = ok ? UiTheme.E_VIOLET : UiTheme.E_EDGE;
                int nameCol = ok ? UiTheme.E_INK : UiTheme.E_FAINT;
                int rightCol = ok ? (skill.costType() == CostType.SP ? UiTheme.E_JADE : UiTheme.E_INDIGO) : UiTheme.E_FAINT;
                String title = prefix + skill.name();
                int rightW = font.m_92895_(right) + 10;
                if (font.m_92895_(title) + rightW <= maxW) {
                    out.add(new DetailLine(true, dotCol, title, nameCol, right, rightCol));
                } else {
                    List<String> wrappedTitle = wrapText(font, title, maxW);
                    for (int k = 0; k < wrappedTitle.size(); k++) {
                        out.add(new DetailLine(k == 0, dotCol, wrappedTitle.get(k), nameCol, "", rightCol));
                    }
                    out.add(new DetailLine(false, dotCol, right, rightCol, "", rightCol));
                }
                if (skill.description() != null && !skill.description().isEmpty()) {
                    for (String line : wrapText(font, skill.description(), indentW)) {
                        out.add(new DetailLine(false, dotCol, line, ok ? UiTheme.E_MUTED : UiTheme.E_FAINT, "", UiTheme.E_FAINT));
                    }
                }
            }
        } else if (detailTab == 1) {
            double mult = def.quality().bonusMultiplier();
            String bonusLine = String.format(Locale.ROOT,
                "%s: HP +%.0f%% \u00b7 MP +%.0f%% \u00b7 SP +%.0f%% \u00b7 DEF +%.0f%%  (%s +%d)",
                t("detail.bonus_loaded"),
                meta.hpBonus() * mult * 100.0,
                meta.mpBonus() * mult * 100.0,
                meta.spBonus() * mult * 100.0,
                meta.defBonus() * mult * 100.0,
                t("detail.rule_bonus"),
                def.quality().ruleBonus());
            appendWrappedDetail(font, out, UiTheme.E_GOLD_TEXT, bonusLine, UiTheme.E_GOLD_TEXT, maxW);
            if (meta.passives().isEmpty()) {
                out.add(new DetailLine(true, UiTheme.E_EDGE, t("detail.none"), UiTheme.E_FAINT, "", UiTheme.E_FAINT));
            } else {
                for (TemplateIntrospector.TraitView tv : meta.passives()) {
                    appendTraitWrapped(font, out, "", tv, UiTheme.E_JADE, maxW);
                }
            }
        } else {
            appendTraitGroupWrapped(font, out, t("detail.weaknesses"), meta.weaknesses(), UiTheme.E_RED, maxW);
            appendTraitGroupWrapped(font, out, t("detail.negatives"), meta.negatives(), UiTheme.E_GOLD_TEXT, maxW);
            appendTraitGroupWrapped(font, out, t("detail.quirks"), meta.quirks(), UiTheme.E_CYAN, maxW);
            if (def.description() != null && !def.description().isEmpty()) {
                appendWrappedDetail(font, out, UiTheme.E_VIOLET,
                    "[" + t("detail.description") + "] " + def.description(),
                    UiTheme.E_MUTED, maxW);
            }
            if (out.isEmpty()) {
                out.add(new DetailLine(true, UiTheme.E_EDGE, t("detail.none"), UiTheme.E_FAINT, "", UiTheme.E_FAINT));
            }
        }
        return out;
    }

    private static void appendWrappedDetail(Font font, List<DetailLine> out, int dotColor,
                                            String text, int textColor, int maxW) {
        List<String> lines = wrapText(font, text, maxW);
        for (int i = 0; i < lines.size(); i++) {
            out.add(new DetailLine(i == 0, dotColor, lines.get(i), textColor, "", UiTheme.E_FAINT));
        }
    }

    private static void appendTraitGroupWrapped(Font font, List<DetailLine> out, String tag,
                                                List<TemplateIntrospector.TraitView> traits, int color, int maxW) {
        for (TemplateIntrospector.TraitView tv : traits) {
            appendTraitWrapped(font, out, "[" + tag + "] ", tv, color, maxW);
        }
    }

    private static void appendTraitWrapped(Font font, List<DetailLine> out, String tagPrefix,
                                           TemplateIntrospector.TraitView tv, int color, int maxW) {
        String header = tagPrefix + tv.name();
        if (tv.description().isEmpty()) {
            appendWrappedDetail(font, out, color, header, UiTheme.E_INK, maxW);
            return;
        }
        String combined = header + "\uff1a" + tv.description();
        if (font.m_92895_(combined) <= maxW) {
            out.add(new DetailLine(true, color, combined, UiTheme.E_INK, "", UiTheme.E_FAINT));
            return;
        }
        List<String> hLines = wrapText(font, header, maxW);
        for (int i = 0; i < hLines.size(); i++) {
            out.add(new DetailLine(i == 0, color, hLines.get(i), UiTheme.E_INK, "", UiTheme.E_FAINT));
        }
        for (String line : wrapText(font, tv.description(), Math.max(48, maxW - 4))) {
            out.add(new DetailLine(false, color, line, UiTheme.E_MUTED, "", UiTheme.E_FAINT));
        }
    }

    // ------------------------------------------------------------------ items & vault tab

    private record PanelItemEntry(
        String id,
        ItemStack icon,
        String title,
        String origin,
        int accent,
        String statusText,
        int statusColor,
        String quickLabel,
        boolean quickEnabled,
        int quickAccent,
        Runnable quickAction,
        String primaryLabel,
        boolean primaryEnabled,
        int primaryAccent,
        Runnable primaryAction,
        String secondaryLabel,
        boolean secondaryEnabled,
        int secondaryAccent,
        Runnable secondaryAction,
        String tooltip,
        List<DetailSpec> details
    ) {
    }

    private record DetailSpec(int dotColor, String tag, String body) {
    }

    private List<PanelItemEntry> buildPanelItems(PlayerSystemData data) {
        List<PanelItemEntry> out = new ArrayList<>();
        Minecraft mc = Minecraft.m_91087_();
        boolean wearingMask = mc.f_91074_ != null && PerceptionBlockingMaskItem.isWearingMask(mc.f_91074_);
        int maskState = (int) Math.round(data.value(PerceptionMaskManager.KEY_STATE, 0.0));
        boolean held = maskState == PerceptionMaskManager.STATE_HELD;
        boolean dropped = maskState == PerceptionMaskManager.STATE_DROPPED_NEARBY;
        boolean lost = maskState == PerceptionMaskManager.STATE_LOST;

        String statusText = wearingMask ? t("mask.state_worn")
            : (held ? t("mask.state_held")
                : (dropped ? t("mask.state_dropped")
                    : (lost ? t("mask.state_lost") : t("mask.state_stored"))));
        int statusColor = wearingMask ? UiTheme.E_CYAN
            : (held ? UiTheme.E_JADE
                : (dropped ? UiTheme.E_GOLD_TEXT
                    : (lost ? UiTheme.E_RED : UiTheme.E_MUTED)));

        String quickLabel = held ? t("items.quick_store")
            : (dropped ? t("items.quick_dropped")
                : (lost ? t("items.quick_reclaim") : t("items.quick_take")));
        boolean quickEnabled = !dropped;
        int quickAccent = held ? UiTheme.E_CYAN : (quickEnabled ? UiTheme.E_VIOLET : UiTheme.E_EDGE);
        Runnable quickAct = held ? () -> send("mask_store", "") : () -> send("mask_take", "");

        String primaryLabel = wearingMask ? t("mask.btn_unequip_store")
            : (held ? t("mask.btn_store")
                : (dropped ? t("mask.btn_dropped")
                    : (lost ? t("mask.btn_reclaim") : t("mask.btn_take_bag"))));
        boolean primaryEnabled = !dropped;
        int primaryAccent = held ? UiTheme.E_CYAN : (primaryEnabled ? UiTheme.E_VIOLET : UiTheme.E_EDGE);
        Runnable primaryAct = held ? () -> send("mask_store", "") : () -> send("mask_take", "");

        String secondaryLabel = wearingMask ? "" : t("mask.btn_equip");
        boolean secondaryEnabled = !wearingMask && !dropped;
        int secondaryAccent = UiTheme.E_JADE;
        Runnable secondaryAct = () -> send("mask_equip", "");

        List<DetailSpec> maskDetails = List.of(
            new DetailSpec(UiTheme.E_VIOLET, t("mask.detail_visual_tag"), t("mask.detail_visual_body")),
            new DetailSpec(UiTheme.E_JADE, t("mask.detail_mob_tag"), t("mask.detail_mob_body")),
            new DetailSpec(UiTheme.E_GOLD_TEXT, t("mask.detail_redstone_tag"), t("mask.detail_redstone_body")),
            new DetailSpec(UiTheme.E_CYAN, t("mask.detail_player_tag"), t("mask.detail_player_body")),
            new DetailSpec(UiTheme.E_INDIGO, t("mask.detail_anchor_tag"), t("mask.detail_anchor_body"))
        );

        out.add(new PanelItemEntry(
            "perception_blocking_mask",
            new ItemStack(PerceptionMaskBootstrap.MASK.get()),
            t("mask.title"),
            t("mask.origin"),
            UiTheme.E_VIOLET,
            statusText,
            statusColor,
            quickLabel,
            quickEnabled,
            quickAccent,
            quickAct,
            primaryLabel,
            primaryEnabled,
            primaryAccent,
            primaryAct,
            secondaryLabel,
            secondaryEnabled,
            secondaryAccent,
            secondaryAct,
            t("mask.tooltip"),
            maskDetails
        ));

        // 拥有假面骑士 Cronus 模板后，自动在「物品」界面收纳【故障驱动器Ⅱ】与【假面骑士编年史转玩卡带】
        if (CronusKeys.ownsCronusTemplate(data)) {
            int dState = (int) Math.round(data.value(CronusKeys.KEY_DRIVER_STATE, 0.0));
            boolean dHeld = dState == CronusGearManager.STATE_HELD;
            boolean dDropped = dState == CronusGearManager.STATE_DROPPED_NEARBY;
            boolean dLost = dState == CronusGearManager.STATE_LOST;

            String dStatus = dHeld ? t("cronus_gear.state_held")
                : (dDropped ? t("cronus_gear.state_dropped")
                    : (dLost ? t("cronus_gear.state_lost") : t("cronus_gear.state_stored")));
            int dStatusColor = dHeld ? UiTheme.E_JADE
                : (dDropped ? UiTheme.E_GOLD_TEXT
                    : (dLost ? UiTheme.E_RED : UiTheme.E_MUTED));
            String dQuick = dHeld ? t("items.quick_store")
                : (dDropped ? t("items.quick_dropped")
                    : (dLost ? t("items.quick_reclaim") : t("items.quick_take")));
            boolean dEnabled = !dDropped;
            int dAccentBtn = dHeld ? UiTheme.E_CYAN : (dEnabled ? UiTheme.E_JADE : UiTheme.E_EDGE);
            Runnable dAct = dHeld ? () -> send("cronus_driver_store", "") : () -> send("cronus_driver_take", "");
            String dPrimary = dHeld ? t("cronus_gear.btn_store")
                : (dDropped ? t("cronus_gear.btn_dropped")
                    : (dLost ? t("cronus_gear.btn_reclaim") : t("cronus_gear.btn_take")));

            int gState = (int) Math.round(data.value(CronusKeys.KEY_GASHAT_STATE, 0.0));
            boolean gInDriver = gState == CronusGearManager.STATE_IN_DRIVER;
            boolean gHeld = gState == CronusGearManager.STATE_HELD;
            boolean gDropped = gState == CronusGearManager.STATE_DROPPED_NEARBY;
            boolean gLost = gState == CronusGearManager.STATE_LOST;
            boolean canTakeSet = (!dHeld && !dDropped) || (!gHeld && !gInDriver && !gDropped);

            List<DetailSpec> driverDetails = List.of(
                new DetailSpec(UiTheme.E_JADE, t("cronus_gear.driver_d1_tag"), t("cronus_gear.driver_d1_body")),
                new DetailSpec(UiTheme.E_CYAN, t("cronus_gear.driver_d2_tag"), t("cronus_gear.driver_d2_body")),
                new DetailSpec(UiTheme.E_GOLD_TEXT, t("cronus_gear.driver_d3_tag"), t("cronus_gear.driver_d3_body")),
                new DetailSpec(UiTheme.E_VIOLET, t("cronus_gear.driver_d4_tag"), t("cronus_gear.driver_d4_body"))
            );
            out.add(new PanelItemEntry(
                "buggle_driver_ii",
                new ItemStack(CronusItems.BUGGLE_DRIVER_II.get()),
                t("cronus_gear.driver_title"),
                t("cronus_gear.origin"),
                UiTheme.E_JADE,
                dStatus,
                dStatusColor,
                dQuick,
                dEnabled,
                dAccentBtn,
                dAct,
                dPrimary,
                dEnabled,
                dAccentBtn,
                dAct,
                canTakeSet ? t("cronus_gear.btn_take_set") : "",
                canTakeSet,
                UiTheme.E_GOLD_TEXT,
                () -> send("cronus_set_take", ""),
                t("cronus_gear.driver_tooltip"),
                driverDetails
            ));

            boolean gOut = gHeld || gInDriver;
            String gStatus = gInDriver ? t("cronus_gear.state_in_driver")
                : (gHeld ? t("cronus_gear.state_held")
                    : (gDropped ? t("cronus_gear.state_dropped")
                        : (gLost ? t("cronus_gear.state_lost") : t("cronus_gear.state_stored"))));
            int gStatusColor = gInDriver ? UiTheme.E_CYAN
                : (gHeld ? UiTheme.E_JADE
                    : (gDropped ? UiTheme.E_GOLD_TEXT
                        : (gLost ? UiTheme.E_RED : UiTheme.E_MUTED)));
            String gQuick = gOut ? t("items.quick_store")
                : (gDropped ? t("items.quick_dropped")
                    : (gLost ? t("items.quick_reclaim") : t("items.quick_take")));
            boolean gEnabled = !gDropped;
            int gAccentBtn = gOut ? UiTheme.E_CYAN : (gEnabled ? UiTheme.E_JADE : UiTheme.E_EDGE);
            Runnable gAct = gOut ? () -> send("cronus_gashat_store", "") : () -> send("cronus_gashat_take", "");
            String gPrimary = gOut ? t("cronus_gear.btn_store")
                : (gDropped ? t("cronus_gear.btn_dropped")
                    : (gLost ? t("cronus_gear.btn_reclaim") : t("cronus_gear.btn_take")));

            List<DetailSpec> gashatDetails = List.of(
                new DetailSpec(UiTheme.E_JADE, t("cronus_gear.gashat_d1_tag"), t("cronus_gear.gashat_d1_body")),
                new DetailSpec(UiTheme.E_CYAN, t("cronus_gear.gashat_d2_tag"), t("cronus_gear.gashat_d2_body")),
                new DetailSpec(UiTheme.E_GOLD_TEXT, t("cronus_gear.gashat_d3_tag"), t("cronus_gear.gashat_d3_body"))
            );
            out.add(new PanelItemEntry(
                "chronicle_gashat",
                new ItemStack(CronusItems.CHRONICLE_GASHAT.get()),
                t("cronus_gear.gashat_title"),
                t("cronus_gear.origin"),
                UiTheme.E_CYAN,
                gStatus,
                gStatusColor,
                gQuick,
                gEnabled,
                gAccentBtn,
                gAct,
                gPrimary,
                gEnabled,
                gAccentBtn,
                gAct,
                canTakeSet ? t("cronus_gear.btn_take_set") : "",
                canTakeSet,
                UiTheme.E_GOLD_TEXT,
                () -> send("cronus_set_take", ""),
                t("cronus_gear.gashat_tooltip"),
                gashatDetails
            ));
        }
        return out;
    }

    private void renderItems(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        List<PanelItemEntry> items = buildPanelItems(data);

        // 顶部栏：左侧专属道具数 · 中间随身仓库已用格数 · 右侧一键打开 27 格随身仓库
        String artLabel = t("items.artifacts_count", items.size());
        UiTheme.text(g, font, artLabel, a[0], a[1] + 4, UiTheme.E_MUTED);

        int vaultUsed = Math.max(0, Math.min(TravelerVaultStorage.SLOTS,
            (int) Math.round(data.value(TravelerVaultStorage.KEY_VAULT_USED, 0.0))));
        String vaultBtnTxt = t("items.btn_open_vault");
        int vaultBtnW = Math.max(78, Math.min(126, font.m_92895_(vaultBtnTxt) + 18));
        int vaultBtnX = a[0] + a[2] - vaultBtnW;
        String vaultState = t("items.vault_status", vaultUsed, TravelerVaultStorage.SLOTS);
        int stateX = a[0] + font.m_92895_(artLabel) + 10;
        int stateBudget = Math.max(24, vaultBtnX - stateX - 8);
        String stateShown = fitText(font, vaultState, stateBudget);
        UiTheme.text(g, font, stateShown, stateX, a[1] + 4, vaultUsed > 0 ? UiTheme.E_CYAN : UiTheme.E_MUTED);
        if (inside(mx, my, stateX, a[1], stateBudget, 16)) {
            offerPlainTooltip(t("items.vault_tooltip", vaultUsed, TravelerVaultStorage.SLOTS), UiTheme.E_INK);
        }

        boolean vaultHover = inside(mx, my, vaultBtnX, a[1], vaultBtnW, 16);
        UiTheme.buttonSurface(g, vaultBtnX, a[1], vaultBtnW, 16, vaultHover, false, UiTheme.E_CYAN);
        UiTheme.text(g, font, fitText(font, vaultBtnTxt, vaultBtnW - 10), vaultBtnX + 5, a[1] + 4, UiTheme.E_INK);
        if (vaultHover) {
            offerPlainTooltip(t("items.vault_tooltip", vaultUsed, TravelerVaultStorage.SLOTS), UiTheme.E_INK);
        }
        this.hits.add(new Hit(vaultBtnX, a[1], vaultBtnW, 16, () -> send("vault_open", "")));

        // 底部详情区高度自适应；中间网格自动扩展，便于未来收纳更多专属道具
        int detailH = Math.max(106, Math.min(188, a[3] * 54 / 100));
        int dy = a[1] + a[3] - detailH;
        this.lastItemsDetailY = dy;
        this.lastItemsDetailH = detailH;
        int gridTop = a[1] + 22;
        int gridH = Math.max(46, dy - 6 - gridTop);

        int cardH = 44;
        int gap = 6;
        int cols = a[2] >= 420 ? 2 : 1;
        int cardW = Math.max(140, (a[2] - gap * (cols - 1)) / cols);
        int rowsCount = Math.max(1, (items.size() + cols - 1) / cols);
        double maxScroll = Math.max(0.0, rowsCount * (cardH + gap) - gridH);
        this.itemsScroll = Math.min(this.itemsScroll, maxScroll);

        PanelItemEntry selected = items.isEmpty() ? null : items.get(0);
        for (PanelItemEntry entry : items) {
            if (entry.id().equals(selectedItemId)) {
                selected = entry;
                break;
            }
        }
        if (selected != null) {
            selectedItemId = selected.id();
        }

        for (int i = 0; i < items.size(); i++) {
            PanelItemEntry entry = items.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = a[0] + col * (cardW + gap);
            int y = gridTop + row * (cardH + gap) - (int) Math.round(this.itemsScroll);
            if (y < gridTop || y + cardH > gridTop + gridH) {
                continue;
            }
            boolean isSel = selected != null && entry.id().equals(selected.id());
            boolean hover = inside(mx, my, x, y, cardW, cardH);
            int drawY = y - Math.round(UiTheme.hover(x, y, cardW, cardH, hover) * 1.5f);
            Fx.softShadow(g, x, drawY, cardW, cardH, UiTheme.E_SHADOW, hover ? 0.42f : 0.16f);
            UiTheme.buttonSurface(g, x, drawY, cardW, cardH, hover, isSel, entry.accent());
            if (isSel) {
                UiTheme.marching(g, x, drawY, cardW, cardH, UiTheme.E_VIOLET, 1f);
            }
            g.m_280509_(x + 3, drawY + 4, x + 6, drawY + cardH - 4, UiTheme.alpha(1f, entry.accent()));

            int slotX = x + 11;
            int slotY = drawY + (cardH - 24) / 2;
            UiTheme.inset(g, slotX, slotY, 24, 24);
            g.m_280480_(entry.icon(), slotX + 4, slotY + 4);

            int qBtnW = Math.max(44, Math.min(64, font.m_92895_(entry.quickLabel()) + 12));
            int qBtnX = x + cardW - qBtnW - 7;
            int qBtnY = drawY + (cardH - 18) / 2;
            boolean qHover = inside(mx, my, qBtnX, qBtnY, qBtnW, 18);
            UiTheme.buttonSurface(g, qBtnX, qBtnY, qBtnW, 18, qHover && entry.quickEnabled(), false, entry.quickAccent());
            UiTheme.text(g, font, fitText(font, entry.quickLabel(), qBtnW - 8), qBtnX + 4, qBtnY + 5,
                entry.quickEnabled() ? UiTheme.E_INK : UiTheme.E_FAINT);

            int textX = slotX + 30;
            int textBudget = Math.max(32, qBtnX - textX - 6);
            UiTheme.text(g, font, fitText(font, entry.title(), textBudget), textX, drawY + 7, UiTheme.E_INK);
            UiTheme.text(g, font, fitText(font, entry.origin(), textBudget), textX, drawY + 18, UiTheme.E_FAINT);
            UiTheme.text(g, font, fitText(font, entry.statusText(), textBudget), textX, drawY + 29, entry.statusColor());

            if (hover && !qHover) {
                offerPlainTooltip(entry.tooltip(), UiTheme.E_INK);
            }
            final String targetItemId = entry.id();
            this.hits.add(new Hit(x, y, cardW, cardH, () -> {
                selectedItemId = targetItemId;
                this.itemsDetailScroll = 0.0;
            }));
            if (entry.quickEnabled()) {
                this.hits.add(new Hit(qBtnX, qBtnY, qBtnW, 18, entry.quickAction()));
            }
        }

        // 若网格下方还有空白，展示可点击的随身仓库快捷入口条，方便随时存取任意物品
        int belowCardsY = gridTop + rowsCount * (cardH + gap) + 2;
        if (belowCardsY + 24 <= gridTop + gridH && maxScroll <= 0.5) {
            int stripH = Math.min(34, gridTop + gridH - belowCardsY);
            boolean stripHover = inside(mx, my, a[0], belowCardsY, a[2], stripH);
            UiTheme.buttonSurface(g, a[0], belowCardsY, a[2], stripH, stripHover, false, UiTheme.E_CYAN);
            String tip = t("items.vault_banner", vaultUsed, TravelerVaultStorage.SLOTS);
            UiTheme.text(g, font, fitText(font, tip, a[2] - 16), a[0] + 8, belowCardsY + (stripH - 8) / 2,
                stripHover ? UiTheme.E_INK : UiTheme.E_MUTED);
            if (stripHover) {
                offerPlainTooltip(t("items.vault_tooltip", vaultUsed, TravelerVaultStorage.SLOTS), UiTheme.E_INK);
            }
            this.hits.add(new Hit(a[0], belowCardsY, a[2], stripH, () -> send("vault_open", "")));
        }

        if (maxScroll > 0.5 && gridH > 12) {
            int trackX = a[0] + a[2] - 2;
            g.m_280509_(trackX, gridTop, trackX + 2, gridTop + gridH, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) gridH * gridH / (rowsCount * (cardH + gap))));
            int thumbY = gridTop + (int) Math.round((this.itemsScroll / maxScroll) * (gridH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.85f, UiTheme.E_VIOLET));
        }

        // 底部详情与操作面板
        UiTheme.frame(g, a[0], dy, a[2], detailH, 1f);
        if (selected == null) {
            UiTheme.text(g, font, t("items.empty"), a[0] + 10, dy + 10, UiTheme.E_FAINT);
            return;
        }
        int ix = a[0] + 10;
        int iw = a[2] - 20;
        g.m_280480_(selected.icon(), ix, dy + 6);
        int statusW = font.m_92895_(selected.statusText()) + 10;
        String headLine = selected.title() + "  \u00b7  " + selected.origin();
        UiTheme.text(g, font, fitText(font, headLine, Math.max(40, iw - 22 - statusW)), ix + 22, dy + 10, UiTheme.E_INK);
        UiTheme.right(g, font, selected.statusText(), a[0] + a[2] - 10, dy + 10, selected.statusColor());

        // 操作按钮行：主操作（拿取 / 存回 / 补领）+ 次操作（一键佩戴）
        int by = dy + 27;
        int bx = ix;
        int pW = Math.max(66, Math.min(110, font.m_92895_(selected.primaryLabel()) + 16));
        boolean pHover = inside(mx, my, bx, by, pW, 17);
        UiTheme.buttonSurface(g, bx, by, pW, 17, pHover && selected.primaryEnabled(), false, selected.primaryAccent());
        UiTheme.text(g, font, fitText(font, selected.primaryLabel(), pW - 10), bx + 5, by + 4,
            selected.primaryEnabled() ? UiTheme.E_INK : UiTheme.E_FAINT);
        if (selected.primaryEnabled()) {
            this.hits.add(new Hit(bx, by, pW, 17, selected.primaryAction()));
        }
        bx += pW + 6;

        if (!selected.secondaryLabel().isEmpty()) {
            int sW = Math.max(62, Math.min(104, font.m_92895_(selected.secondaryLabel()) + 16));
            boolean sHover = inside(mx, my, bx, by, sW, 17);
            UiTheme.buttonSurface(g, bx, by, sW, 17, sHover && selected.secondaryEnabled(), false, selected.secondaryAccent());
            UiTheme.text(g, font, fitText(font, selected.secondaryLabel(), sW - 10), bx + 5, by + 4,
                selected.secondaryEnabled() ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (selected.secondaryEnabled()) {
                this.hits.add(new Hit(bx, by, sW, 17, selected.secondaryAction()));
            }
        }

        // 详情特性可滚动列表
        int bodyTop = by + 22;
        int bodyBottom = dy + detailH - 6;
        int bodyH = Math.max(12, bodyBottom - bodyTop);
        int textBudget = Math.max(60, iw - 14);
        List<DetailLine> dLines = new ArrayList<>();
        for (DetailSpec spec : selected.details()) {
            String full = "[" + spec.tag() + "] " + spec.body();
            appendWrappedDetail(font, dLines, spec.dotColor(), full, UiTheme.E_MUTED, textBudget);
        }
        int lineH = 11;
        double maxDScroll = Math.max(0.0, dLines.size() * lineH - bodyH);
        this.itemsDetailScroll = Math.min(this.itemsDetailScroll, maxDScroll);
        int scrollPx = (int) Math.round(this.itemsDetailScroll);
        for (int i = 0; i < dLines.size(); i++) {
            int ry = bodyTop + i * lineH - scrollPx;
            if (ry < bodyTop || ry + 9 > bodyBottom) {
                continue;
            }
            DetailLine dl = dLines.get(i);
            if (dl.showDot()) {
                UiTheme.block(g, ix, ry + 2, 4, 5, dl.dotColor(), 0.9f);
            }
            int tx = ix + (dl.showDot() ? 8 : 10);
            UiTheme.text(g, font, fitText(font, dl.leftText(), Math.max(24, ix + iw - 6 - tx)), tx, ry, dl.textColor());
        }
        if (maxDScroll > 0.5) {
            int trackX = a[0] + a[2] - 4;
            g.m_280509_(trackX, bodyTop, trackX + 2, bodyBottom, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) bodyH * bodyH / (dLines.size() * lineH)));
            int thumbY = bodyTop + (int) Math.round((this.itemsDetailScroll / maxDScroll) * (bodyH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.9f, selected.accent()));
        }
    }

    // ------------------------------------------------------------------ codex tab

    private void renderCodex(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        this.search.f_93624_ = true;
        this.search.m_88315_(g, mx, my, 0f);
        int stripH = Math.max(76, Math.min(134, a[3] * 38 / 100));
        int sy = a[1] + a[3] - stripH;
        this.lastCodexDetailY = sy;
        this.lastCodexDetailH = stripH;
        int gridTop = a[1] + 22;
        int gridH = Math.max(24, sy - 6 - gridTop);
        int gap = 6;
        int cols = Math.max(1, (a[2] + gap) / (MIN_CELL_W + gap));
        int cellW = Math.max(118, (a[2] - gap * (cols - 1)) / cols);

        String needle = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
        List<TemplateDefinition> defs = new ArrayList<>();
        int ownedCount = 0;
        for (TemplateDefinition def : TemplateRegistry.client().all()) {
            boolean owned = data.findTemplate(def.id()).isPresent();
            if (owned) {
                ownedCount++;
            }
            if (!needle.isEmpty() && !(def.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || def.name().toLowerCase(Locale.ROOT).contains(needle)
                || def.id().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            defs.add(def);
        }
        int total = TemplateRegistry.client().all().size();
        UiTheme.right(g, font, t("v4.codex_count", ownedCount, total), a[0] + a[2], a[1] + 4, UiTheme.E_MUTED);

        int rowsCount = (defs.size() + cols - 1) / cols;
        double maxScroll = Math.max(0, rowsCount * (CELL_H + gap) - gridH);
        this.codexScroll = Math.min(this.codexScroll, maxScroll);
        if (defs.isEmpty()) {
            UiTheme.text(g, font, t("codex.unknown"), a[0] + 4, gridTop + 4, UiTheme.E_FAINT);
        }
        for (int i = 0; i < defs.size(); i++) {
            TemplateDefinition def = defs.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = a[0] + col * (cellW + gap);
            int y = gridTop + row * (CELL_H + gap) - (int) this.codexScroll;
            if (y < gridTop || y + CELL_H > gridTop + gridH) {
                continue;
            }
            boolean owned = data.findTemplate(def.id()).isPresent();
            int qc = UiTheme.templateColor(def);
            int qText = UiTheme.templateTextColor(def);
            boolean selected = def.id().equals(selectedId);
            boolean hover = inside(mx, my, x, y, cellW, CELL_H);
            int ly = y - Math.round(UiTheme.hover(x, y, cellW, CELL_H, hover && owned) * 2f);
            if (owned) {
                UiTheme.buttonSurface(g, x, ly, cellW, CELL_H, hover, selected, qc);
                if (selected) {
                    UiTheme.marching(g, x, ly, cellW, CELL_H, UiTheme.E_VIOLET, 1f);
                }
                g.m_280509_(x + 2, ly + 2, x + 5, ly + CELL_H - 2, UiTheme.alpha(1f, qc));
                UiTheme.cube(g, x + cellW - 18, ly + 10, 12, qc);
            } else {
                // undiscovered: inset well with diagonal hatching
                UiTheme.inset(g, x, ly, cellW, CELL_H);
                int hatch = UiTheme.mix(UiTheme.E_INSET, UiTheme.E_EDGE, 0.35f);
                for (int d = 18; d < cellW + CELL_H; d += 22) {
                    UiTheme.line(g, x + d, ly + 2, x + d - (CELL_H - 4), ly + CELL_H - 2, hatch);
                }
            }
            int textW = Math.max(36, cellW - 28);
            TemplateIntrospector.Meta cellMeta = owned ? TemplateIntrospector.inspect(def) : null;
            String label = owned ? def.name() : t("codex.locked");
            UiTheme.text(g, font, UiTheme.fit(font, label, textW), x + 9, ly + 5,
                owned ? UiTheme.E_INK : UiTheme.E_FAINT);
            String qLabel = def.quality().label();
            if (owned) {
                UiTheme.textExact(g, font, qLabel, x + 9, ly + 18, qText);
                String period = cellMeta.period();
                if (!period.isEmpty()) {
                    int px = x + 9 + font.m_92895_(qLabel) + 5;
                    int pBudget = (x + cellW - 22) - px;
                    if (pBudget > 16) {
                        UiTheme.text(g, font, UiTheme.fit(font, "\u00b7 " + period, pBudget), px, ly + 18, UiTheme.E_MUTED);
                    }
                }
                if (hover) {
                    offerTemplateTooltip(def, data.findTemplate(def.id()).orElse(null), cellMeta);
                }
            } else {
                UiTheme.text(g, font, UiTheme.fit(font, qLabel, textW), x + 9, ly + 18, UiTheme.E_FAINT);
            }
            final String id = def.id();
            this.hits.add(new Hit(x, y, cellW, CELL_H, () -> {
                selectedId = id;
                this.codexDetailScroll = 0.0;
            }));
        }
        if (maxScroll > 0.5 && gridH > 12) {
            int trackX = a[0] + a[2] - 2;
            g.m_280509_(trackX, gridTop, trackX + 2, gridTop + gridH, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) gridH * gridH / (rowsCount * (CELL_H + gap))));
            int thumbY = gridTop + (int) Math.round((this.codexScroll / maxScroll) * (gridH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.85f, UiTheme.E_VIOLET));
        }

        // Scrollable wrapped detail card at the bottom of Codex
        UiTheme.frame(g, a[0], sy, a[2], stripH, 1f);
        TemplateDefinition sel = selectedId == null ? null : TemplateRegistry.client().find(selectedId).orElse(null);
        if (sel != null && data.findTemplate(sel.id()).isPresent()) {
            TemplateIntrospector.Meta meta = TemplateIntrospector.inspect(sel);
            int qc = UiTheme.templateColor(sel);
            int qText = UiTheme.templateTextColor(sel);
            int innerX = a[0] + 8;
            int innerW = a[2] - 20;
            String head = composeTitle(sel, meta);
            String qInfo = t("codex.quality_info",
                sel.maxLevel(),
                String.format(Locale.ROOT, "\u00d7%.1f", sel.quality().bonusMultiplier()),
                String.format(Locale.ROOT, "\u00d7%.0f", sel.quality().costMultiplier()),
                "+" + sel.quality().ruleBonus());

            List<DetailLine> cLines = new ArrayList<>();
            int qInfoW = font.m_92895_(qInfo) + 10;
            if (font.m_92895_(head) + qInfoW <= innerW) {
                cLines.add(new DetailLine(true, qc, head, UiTheme.E_INK, qInfo, qText));
            } else {
                List<String> headLines = wrapText(font, head, innerW);
                for (int k = 0; k < headLines.size(); k++) {
                    cLines.add(new DetailLine(k == 0, qc, headLines.get(k), UiTheme.E_INK, "", qText));
                }
                for (String ql : wrapText(font, qInfo, Math.max(48, innerW - 4))) {
                    cLines.add(new DetailLine(false, qc, ql, qText, "", qText));
                }
            }
            if (sel.description() != null && !sel.description().isEmpty()) {
                for (String dl : wrapText(font, sel.description(), Math.max(48, innerW - 4))) {
                    cLines.add(new DetailLine(false, qc, dl, UiTheme.E_MUTED, "", UiTheme.E_FAINT));
                }
            }
            String drawbacks = summarizeDrawbacks(meta);
            if (!drawbacks.isEmpty() && !drawbacks.equals(t("detail.none"))) {
                for (String wl : wrapText(font, drawbacks, Math.max(48, innerW - 4))) {
                    cLines.add(new DetailLine(false, UiTheme.E_GOLD_TEXT, wl, UiTheme.E_GOLD_TEXT, "", UiTheme.E_FAINT));
                }
            }

            int bodyTop = sy + 6;
            int bodyBottom = sy + stripH - 5;
            int bodyH = Math.max(12, bodyBottom - bodyTop);
            int lineH = 11;
            double maxCScroll = Math.max(0.0, cLines.size() * lineH - bodyH);
            this.codexDetailScroll = Math.min(this.codexDetailScroll, maxCScroll);
            int cScrollPx = (int) Math.round(this.codexDetailScroll);
            for (int i = 0; i < cLines.size(); i++) {
                int ry = bodyTop + i * lineH - cScrollPx;
                if (ry < bodyTop || ry + 9 > bodyBottom) {
                    continue;
                }
                DetailLine dl = cLines.get(i);
                if (dl.showDot()) {
                    UiTheme.block(g, innerX, ry + 2, 4, 5, dl.dotColor(), 0.9f);
                }
                int tx = innerX + (dl.showDot() ? 8 : 0);
                int rReserve = dl.rightText().isEmpty() ? 0 : font.m_92895_(dl.rightText()) + 8;
                String lShown = fitText(font, dl.leftText(), Math.max(24, innerX + innerW - tx - rReserve));
                if (dl.textColor() == qText) {
                    UiTheme.textExact(g, font, lShown, tx, ry, qText);
                } else {
                    UiTheme.text(g, font, lShown, tx, ry, dl.textColor());
                }
                if (!dl.rightText().isEmpty()) {
                    UiTheme.rightExact(g, font, dl.rightText(), innerX + innerW, ry, dl.rightColor());
                }
            }
            if (maxCScroll > 0.5) {
                int trackX = a[0] + a[2] - 4;
                g.m_280509_(trackX, bodyTop, trackX + 2, bodyBottom, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
                int thumbH = Math.max(8, (int) Math.round((double) bodyH * bodyH / (cLines.size() * lineH)));
                int thumbY = bodyTop + (int) Math.round((this.codexDetailScroll / maxCScroll) * (bodyH - thumbH));
                g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.9f, qc));
            }
        } else {
            UiTheme.text(g, font, t("codex.locked"), a[0] + 8, sy + 18, UiTheme.E_FAINT);
        }
    }

    private static String summarizeDrawbacks(TemplateIntrospector.Meta meta) {
        List<String> tags = new ArrayList<>();
        for (TemplateIntrospector.TraitView tv : meta.weaknesses()) {
            tags.add("[" + t("detail.weaknesses") + "] " + tv.name());
        }
        for (TemplateIntrospector.TraitView tv : meta.negatives()) {
            tags.add("[" + t("detail.negatives") + "] " + tv.name());
        }
        for (TemplateIntrospector.TraitView tv : meta.quirks()) {
            tags.add("[" + t("detail.quirks") + "] " + tv.name());
        }
        return tags.isEmpty() ? t("detail.none") : String.join("  \u00b7  ", tags);
    }

    // ------------------------------------------------------------------ settings tab

    private void renderSettings(GuiGraphics g, PlayerSystemData data, int mx, int my) {
        int[] a = contentArea();
        Font font = this.f_96547_;
        boolean terrainAllowed = ClientSystemData.derivedBool("terrainAllowed", true);
        boolean terrainOn = PanelActions.setting(data, "terrain_destruction");

        // group navigation column (adapts cleanly on narrow screens)
        int nw = Math.min(96, Math.max(66, a[2] / 4));
        String[] groups = {t("v4.group.hud"), t("v4.group.play"), t("v4.group.display")};
        for (int i = 0; i < groups.length; i++) {
            int y = a[1] + i * 24;
            boolean active = settingsGroup == i;
            boolean hover = inside(mx, my, a[0], y, nw, 20);
            UiTheme.buttonSurface(g, a[0], y, nw, 20, hover, active, UiTheme.E_VIOLET);
            String gShown = fitText(font, groups[i], nw - 12);
            UiTheme.text(g, font, gShown, a[0] + 8, y + 6, active ? UiTheme.E_VIOLET : UiTheme.E_MUTED);
            if (hover && !gShown.equals(groups[i])) {
                offerPlainTooltip(groups[i], UiTheme.E_INK);
            }
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
        String ctrlLbl = t("v4.set.controls");
        String ctrlShown = fitText(font, ctrlLbl, nw - 12);
        UiTheme.text(g, font, ctrlShown, a[0] + 8, tools + 6, UiTheme.E_INK);
        if (hover1 && !ctrlShown.equals(ctrlLbl)) {
            offerPlainTooltip(ctrlLbl, UiTheme.E_INK);
        }
        this.hits.add(new Hit(a[0], tools, nw, 20, () -> {
            Minecraft mc = Minecraft.m_91087_();
            mc.m_91152_(new ControlsScreen(this, mc.f_91066_));
        }));
        int tools2 = tools + 24;
        boolean hover2 = inside(mx, my, a[0], tools2, nw, 20);
        UiTheme.buttonSurface(g, a[0], tools2, nw, 20, hover2, false, UiTheme.E_CYAN);
        String hudEdLbl = t("v4.set.hud_editor");
        String hudEdShown = fitText(font, hudEdLbl, nw - 12);
        UiTheme.text(g, font, hudEdShown, a[0] + 8, tools2 + 6, UiTheme.E_INK);
        if (hover2 && !hudEdShown.equals(hudEdLbl)) {
            offerPlainTooltip(hudEdLbl, UiTheme.E_INK);
        }
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
        int rowBottom = a[1] + a[3] - 6;
        int availRowsH = Math.max(rowH, rowBottom - rowTop);
        double maxSetScroll = Math.max(0.0, rows.size() * (rowH + 4) - availRowsH);
        this.settingsScroll = Math.min(this.settingsScroll, maxSetScroll);
        int setScrollPx = (int) Math.round(this.settingsScroll);
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int y = rowTop + i * (rowH + 4) - setScrollPx;
            if (y < rowTop || y + rowH > rowBottom) {
                continue;
            }
            boolean hover = inside(mx, my, rx + 6, y, rw - 12, rowH) && row.enabled;
            UiTheme.buttonSurface(g, rx + 6, y, rw - 12, rowH, hover, false, UiTheme.E_VIOLET);
            int trackW = 26;
            int trackH = 12;
            int trackX = rx + rw - 16 - trackW;
            int trackY = y + (rowH - trackH) / 2;
            String lockedTxt = row.enabled ? "" : t("v4.set.terrain_locked");
            int lockedReserve = row.enabled ? 0 : font.m_92895_(lockedTxt) + 12;
            String fullLabel = t(row.labelKey);
            int labelBudget = Math.max(32, trackX - (rx + 16) - 8 - lockedReserve);
            String shownLabel = fitText(font, fullLabel, labelBudget);
            UiTheme.text(g, font, shownLabel, rx + 16, y + 7, row.enabled ? UiTheme.E_INK : UiTheme.E_FAINT);
            if (!shownLabel.equals(fullLabel) && inside(mx, my, rx + 6, y, rw - 12, rowH)) {
                offerPlainTooltip(fullLabel, UiTheme.E_INK);
            }
            // the knob glides between off and on; the key is per label so it survives group switches
            float knob = UiTheme.approach(SETTING_GLIDE + (row.labelKey().hashCode() & 0xFFFF), row.on ? 1f : 0f);
            drawSwitch(g, trackX, trackY, trackW, trackH, knob, row.enabled);
            if (!row.enabled) {
                UiTheme.right(g, font, lockedTxt, trackX - 8, y + 7, UiTheme.E_FAINT);
            }
            if (row.enabled) {
                this.hits.add(new Hit(rx + 6, y, rw - 12, rowH, row.action));
            }
        }
        if (maxSetScroll > 0.5) {
            int trackX = rx + rw - 4;
            g.m_280509_(trackX, rowTop, trackX + 2, rowBottom, UiTheme.alpha(0.35f, UiTheme.E_EDGE));
            int thumbH = Math.max(8, (int) Math.round((double) availRowsH * availRowsH / (rows.size() * (rowH + 4))));
            int thumbY = rowTop + (int) Math.round((this.settingsScroll / maxSetScroll) * (availRowsH - thumbH));
            g.m_280509_(trackX, thumbY, trackX + 2, thumbY + thumbH, UiTheme.alpha(0.9f, UiTheme.E_VIOLET));
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

    // ------------------------------------------------------------------ text wrapping & tooltips

    private static String fitText(Font font, String s, int maxWidth) {
        return UiTheme.fit(font, s, maxWidth);
    }

    /** Clean title combining template name, source and period without duplicating {@code [period]}. */
    private static String composeTitle(TemplateDefinition def, TemplateIntrospector.Meta meta) {
        StringBuilder sb = new StringBuilder(def.name());
        if (meta != null && !meta.source().isEmpty()) {
            sb.append("  \u00b7  \u300a").append(meta.source()).append("\u300b");
        }
        String period = meta != null && !meta.period().isEmpty() ? meta.period() : def.period();
        if (period != null && !period.isEmpty()) {
            sb.append(" [").append(period).append("]");
        }
        return sb.toString();
    }

    /**
     * CJK- and Latin-aware word wrapper against a pixel width budget. Splits explicit newlines,
     * wraps at spaces for Latin runs when possible, and breaks cleanly between CJK characters.
     */
    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int budget = Math.max(24, maxWidth);
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            if (font.m_92895_(paragraph) <= budget) {
                out.add(paragraph);
                continue;
            }
            int len = paragraph.length();
            int start = 0;
            while (start < len) {
                int end = start;
                int lastBreak = -1;
                while (end < len) {
                    String slice = paragraph.substring(start, end + 1);
                    if (font.m_92895_(slice) > budget) {
                        break;
                    }
                    char ch = paragraph.charAt(end);
                    if (ch == ' ' || ch == '\u3000' || ch == '\uff0c' || ch == '\u3002'
                        || ch == '\uff1b' || ch == '\u3001' || ch == '\u00b7' || ch == '/') {
                        lastBreak = end + 1;
                    }
                    end++;
                }
                if (end == start) {
                    // Single glyph wider than budget: still advance by 1 to guarantee progress
                    end = start + 1;
                } else if (end < len && lastBreak > start && isLatinWordChar(paragraph.charAt(end - 1))
                    && isLatinWordChar(paragraph.charAt(end))) {
                    end = lastBreak;
                }
                String line = paragraph.substring(start, end).stripTrailing();
                if (!line.isEmpty()) {
                    out.add(line);
                }
                start = end;
                while (start < len && paragraph.charAt(start) == ' ') {
                    start++;
                }
            }
        }
        return out;
    }

    private static boolean isLatinWordChar(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-';
    }

    private static Component colored(String text, int rgb) {
        return Component.m_237113_(text).m_130948_(Style.f_131099_.m_178520_(rgb & 0xFFFFFF));
    }

    private int tooltipMaxWidth() {
        return Math.max(140, Math.min(260, this.f_96543_ - 28));
    }

    private void offerPlainTooltip(String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (String s : wrapText(this.f_96547_, text, tooltipMaxWidth())) {
            lines.add(colored(s, color));
        }
        this.pendingTooltip = lines;
    }

    private void offerTemplateTooltip(TemplateDefinition def, TemplateInstance inst, TemplateIntrospector.Meta meta) {
        if (def == null) {
            return;
        }
        int maxW = tooltipMaxWidth();
        int qText = UiTheme.templateTextColor(def);
        List<Component> lines = new ArrayList<>();
        String title = def.name() + "  [" + def.quality().label() + "]";
        for (String s : wrapText(this.f_96547_, title, maxW)) {
            lines.add(colored(s, qText));
        }
        StringBuilder sub = new StringBuilder();
        if (meta != null && !meta.source().isEmpty()) {
            sub.append("\u300a").append(meta.source()).append("\u300b");
        }
        String period = meta != null && !meta.period().isEmpty() ? meta.period() : def.period();
        if (period != null && !period.isEmpty()) {
            if (sub.length() > 0) {
                sub.append(" \u00b7 ");
            }
            sub.append(period);
        }
        if (sub.length() > 0) {
            for (String s : wrapText(this.f_96547_, sub.toString(), maxW)) {
                lines.add(colored(s, UiTheme.E_INK));
            }
        }
        if (inst != null) {
            String lv = def.levelless() ? t("templates.levelless") : "Lv." + inst.level() + " / " + def.maxLevel();
            lines.add(colored(lv, UiTheme.E_CYAN));
        }
        if (def.description() != null && !def.description().isEmpty()) {
            for (String s : wrapText(this.f_96547_, def.description(), maxW)) {
                lines.add(colored(s, UiTheme.E_MUTED));
            }
        }
        this.pendingTooltip = lines;
    }
}
