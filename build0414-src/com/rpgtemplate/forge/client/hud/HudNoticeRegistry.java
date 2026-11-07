package com.rpgtemplate.forge.client.hud;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.client.gui.UiTheme;
import com.rpgtemplate.nothingthere.NtForms;
import com.rpgtemplate.templates.ItachiTemplate;
import com.rpgtemplate.templates.KazumaTemplate;
import com.rpgtemplate.templates.OkitaTemplate;
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
            case OkitaTemplate.TEMPLATE_ID -> collectOkita(data, gameTick, out);
            case ItachiTemplate.TEMPLATE_ID -> collectItachi(data, gameTick, out);
            case TanjiroTemplate.TEMPLATE_ID -> collectTanjiro(data, gameTick, out);
            case KazumaTemplate.TEMPLATE_ID -> collectKazuma(data, gameTick, out);
            default -> {
            }
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

    private static void collectOkita(PlayerSystemData data, long gameTick, List<Entry> out) {
        int strain = (int) Math.round(data.value(OkitaTemplate.KEY_TB, 0.0));
        if (strain > 0) {
            out.add(new Entry(tr("hud.rpgtemplate.okita.tb", strain),
                strain >= 75 ? UiTheme.E_RED : UiTheme.E_GOLD_TEXT));
        }
        double parryUntil = data.value(OkitaTemplate.KEY_PARRY_UNTIL, 0.0);
        if (parryUntil > gameTick) {
            out.add(new Entry(tr("hud.rpgtemplate.okita.parry", secs(parryUntil - gameTick)), UiTheme.E_CYAN));
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
