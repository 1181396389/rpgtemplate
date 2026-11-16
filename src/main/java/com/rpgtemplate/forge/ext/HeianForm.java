package com.rpgtemplate.forge.ext;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.skill.ChantScaling;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;

public final class HeianForm {
    public static final String TYPE = "rpgtemplate:heian_form";

    private HeianForm() {
    }

    public static Optional<TraitEntry> active(PlayerSystemData var0) {
        return var0 != null && var0.isActivated() && var0.loadedTemplateId() != null
            ? TraitLookup.activePassive(var0, "rpgtemplate:heian_form")
            : Optional.empty();
    }

    public static double adjust(TraitEntry var0, String var1, double var2) {
        if (var0 == null) {
            return var2;
        } else if (ChantScaling.isChantKey(var1)) {
            return ChantScaling.chantTicks(var2, TraitLookup.param(var0, "chant_speed", 2.0));
        } else {
            return ChantScaling.isSlownessKey(var1) ? ChantScaling.slowness(var2, TraitLookup.param(var0, "slowness_reduction", 9.0)) : var2;
        }
    }

    public static boolean allowsChantSkip(TraitEntry var0) {
        return var0 != null && TraitLookup.paramBool(var0, "chant_skip", true);
    }
}
