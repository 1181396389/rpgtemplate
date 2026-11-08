package com.rpgtemplate.forge.client.hud;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.nothingthere.NtForms;
import com.rpgtemplate.templates.ItachiTemplate;
import com.rpgtemplate.templates.KazumaTemplate;
import com.rpgtemplate.templates.TanjiroTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;

/**
 * 统一的左上角 HUD 状态通知扩展注册中心。
 *
 * <p>各角色模板的形态、适应层数、病症负荷、反噬倒计时与癖好状态统一通过此接口汇入 {@link SystemHud}，
 * 避免各模板各自绘制独立悬浮窗造成界面割裂。
 */
public final class HudNoticeRegistry {
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        PROVIDERS.add(HudNoticeRegistry::collectBuiltinTemplateNotices);
    }

    private HudNoticeRegistry() {
    }

    public record Entry(String text, int color) {
    }

    @FunctionalInterface
    public interface Provider {
        void collect(PlayerSystemData data, long gameTick, List<Entry> out);
    }

    public static void register(Provider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static List<Entry> collect(PlayerSystemData data, long gameTick) {
        List<Entry> out = new ArrayList<>();
        if (data == null || !data.isActivated()) {
            return out;
        }
        for (Provider provider : PROVIDERS) {
            provider.collect(data, gameTick, out);
        }
        return out;
    }

    private static void collectBuiltinTemplateNotices(PlayerSystemData data, long gameTick, List<Entry> out) {
        String tpl = data.loadedTemplateId();
        if (tpl == null || tpl.isEmpty()) {
            return;
        }
        switch (tpl) {
            case "rpgtemplate:nothing_there" -> collectNothingThere(data, gameTick, out);
            case CronusKeys.TEMPLATE_ID -> collectCronus(data, gameTick, out);
            case ItachiTemplate.TEMPLATE_ID -> collectItachi(data, gameTick, out);
            case TanjiroTemplate.TEMPLATE_ID -> collectTanjiro(data, gameTick, out);
            case KazumaTemplate.TEMPLATE_ID -> collectKazuma(data, gameTick, out);
            default -> {
            }
        }
    }

    private static void collectCronus(PlayerSystemData data, long gameTick, List<Entry> out) {
        double overheatUntil = data.value(CronusKeys.KEY_OVERHEAT_UNTIL, 0.0);
        if (overheatUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.cronus.overheat", secs(overheatUntil - gameTick)), UiTheme.E_RED));
        }
        if (CronusKeys.isTransformed(data)) {
            String mode = tr(CronusKeys.weaponMode(data) == CronusKeys.MODE_GUN
                ? "hud.rpgtemplate.cronus.mode_gun"
                : "hud.rpgtemplate.cronus.mode_chainsaw");
            int timeDefPct = (int) Math.round(data.value(CronusKeys.KEY_TIME_DEF, 0.0) * 100.0);
            out.add(new Entry(tr("hud.rpgtemplate.cronus.henshin", mode, timeDefPct), UiTheme.E_JADE));
        } else {
            out.add(new Entry(tr("hud.rpgtemplate.cronus.human"), UiTheme.E_MUTED));
        }
        if (CronusKeys.isPaused(data)) {
            String left = secs(data.value(CronusKeys.KEY_PAUSE_LEFT_SEC, 0.0) * 20.0);
            int pending = (int) Math.round(data.value(CronusKeys.KEY_PENDING_DMG, 0.0));
            out.add(new Entry(tr("hud.rpgtemplate.cronus.paused", left, pending), UiTheme.E_GOLD_TEXT));
        }
        if (data.value(CronusKeys.KEY_GAUNTLET_UNTIL, 0.0) > gameTick) {
            int stacks = (int) Math.round(data.value(CronusKeys.KEY_GAUNTLET_STACKS, 0.0));
            if (stacks > 0) {
                out.add(new Entry(tr("hud.rpgtemplate.cronus.gauntlet", stacks, stacks * 10), UiTheme.E_CYAN));
            }
        }
        int viral = (int) Math.round(data.value(CronusKeys.KEY_VIRAL, 0.0));
        if (viral > 0) {
            out.add(new Entry(tr("hud.rpgtemplate.cronus.viral", viral),
                viral >= 75 ? UiTheme.E_RED : UiTheme.E_VIOLET));
        }
    }

    private static void collectNothingThere(PlayerSystemData data, long gameTick, List<Entry> out) {
        double hatch = data.value(NtForms.KEY_COCOON_HATCH, 0.0);
        if (hatch > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.nt.cocoon", secs(hatch - gameTick)), UiTheme.E_VIOLET));
            return;
        }
        int form = NtForms.current(data);
        int adapt = (int) data.value(NtForms.KEY_ADAPT, 0.0);
        out.add(new Entry(tr("hud.rpgtemplate.nt.form", NtForms.NAMES[form], adapt), UiTheme.E_CYAN));

        double cocoonCd = data.value(NtForms.KEY_COCOON_UNTIL, 0.0) - gameTick;
        if (cocoonCd > 0.0) {
            out.add(new Entry(tr("hud.rpgtemplate.nt.cocoon_cd", secs(cocoonCd)), UiTheme.E_GOLD_TEXT));
        }
        double mentalUntil = data.value(NtForms.KEY_MENTAL_WOUND_UNTIL, 0.0);
        if (mentalUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.nt.mental_wound", secs(mentalUntil - gameTick)), UiTheme.E_DEEP_RED));
        }
        if (data.value(NtForms.KEY_SHELL_DECAYED, 0.0) > 0.5) {
            out.add(new Entry(tr("hud.rpgtemplate.nt.shell_decay"), UiTheme.E_RED));
        }
    }

    private static void collectItachi(PlayerSystemData data, long gameTick, List<Entry> out) {
        int strain = (int) Math.round(data.value(ItachiTemplate.KEY_STRAIN, 0.0));
        if (strain > 0) {
            out.add(new Entry(tr("hud.rpgtemplate.itachi.strain", strain),
                strain >= 70 ? UiTheme.E_DEEP_RED : UiTheme.E_VIOLET));
        }
    }

    private static void collectTanjiro(PlayerSystemData data, long gameTick, List<Entry> out) {
        double coughUntil = data.value(TanjiroTemplate.KEY_COUGH_UNTIL, 0.0);
        if (coughUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.tanjiro.cough", secs(coughUntil - gameTick)), UiTheme.E_RED));
        } else {
            int hypoxia = (int) Math.round(data.value(TanjiroTemplate.KEY_HYPOXIA, 0.0));
            if (hypoxia > 0) {
                out.add(new Entry(tr("hud.rpgtemplate.tanjiro.hypoxia", hypoxia),
                    hypoxia >= 60 ? UiTheme.E_RED : UiTheme.E_GOLD_TEXT));
            }
        }
        double breathUntil = data.value(TanjiroTemplate.KEY_BREATH_UNTIL, 0.0);
        if (breathUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.tanjiro.breath", secs(breathUntil - gameTick)), UiTheme.E_CYAN));
        }
    }

    private static void collectKazuma(PlayerSystemData data, long gameTick, List<Entry> out) {
        double stealthUntil = data.value(KazumaTemplate.KEY_STEALTH_UNTIL, 0.0);
        if (stealthUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.kazuma.stealth", secs(stealthUntil - gameTick)), UiTheme.E_JADE));
        }
        double cowardUntil = data.value(KazumaTemplate.KEY_COWARD_UNTIL, 0.0);
        if (cowardUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.kazuma.coward", secs(cowardUntil - gameTick)), UiTheme.E_GOLD_TEXT));
        }
    }

    private static String tr(String key, Object... args) {
        return Component.m_237110_(key, args).getString();
    }

    private static String secs(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
