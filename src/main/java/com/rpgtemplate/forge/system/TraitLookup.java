package com.rpgtemplate.forge.system;

import com.google.gson.JsonObject;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.service.LineageDrawService;
import com.rpgtemplate.core.template.LineageReward;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TraitLookup {
    private TraitLookup() {
    }

    public static Optional<TraitEntry> loadedPassive(PlayerSystemData var0, String var1) {
        String var2 = var0.loadedTemplateId();
        if (var2 == null) {
            return Optional.empty();
        } else {
            for (TraitEntry var4 : TemplateRegistry.get().effectivePassives(var2)) {
                if (var4.type().equals(var1)) {
                    return Optional.of(var4);
                }
            }

            return find(lineageTraits(var0, TemplateRegistry.get()), var1);
        }
    }

    public static Optional<TraitEntry> loadedWeakness(PlayerSystemData var0, String var1) {
        String var2 = var0.loadedTemplateId();
        return var2 == null ? Optional.empty() : find(TemplateRegistry.get().effectiveWeaknesses(var2), var1);
    }

    public static Optional<TraitEntry> loadedNegative(PlayerSystemData var0, String var1) {
        String var2 = var0.loadedTemplateId();
        return var2 == null ? Optional.empty() : find(TemplateRegistry.get().effectiveNegativeStates(var2), var1);
    }

    public static Optional<TraitEntry> loadedQuirk(PlayerSystemData var0, String var1) {
        String var2 = var0.loadedTemplateId();
        return var2 == null ? Optional.empty() : find(TemplateRegistry.get().effectiveQuirks(var2), var1);
    }

    private static Optional<TraitEntry> find(List<TraitEntry> var0, String var1) {
        for (TraitEntry var3 : var0) {
            if (var3.type().equals(var1)) {
                return Optional.of(var3);
            }
        }

        return Optional.empty();
    }

    public static boolean paramBool(TraitEntry var0, String var1, boolean var2) {
        JsonObject var3 = var0.params();
        return var3 != null && var3.has(var1) ? var3.get(var1).getAsBoolean() : var2;
    }

    public static double param(TraitEntry var0, String var1, double var2) {
        JsonObject var4 = var0.params();
        return var4 != null && var4.has(var1) ? var4.get(var1).getAsDouble() : var2;
    }

    public static String paramStr(TraitEntry var0, String var1, String var2) {
        JsonObject var3 = var0.params();
        return var3 != null && var3.has(var1) && !var3.get(var1).isJsonNull() ? var3.get(var1).getAsString() : var2;
    }

    public static int unlockLevel(TraitEntry var0) {
        return (int)param(var0, "unlock_level", 0.0);
    }

    public static boolean unlocked(PlayerSystemData var0, TraitEntry var1) {
        int var2 = unlockLevel(var1);
        return var2 <= 0 || var0.loadedTemplate().map(TemplateInstance::level).orElse(0) >= var2;
    }

    public static Optional<TraitEntry> activePassive(PlayerSystemData var0, String var1) {
        return loadedPassive(var0, var1).filter(var1x -> unlocked(var0, var1x));
    }

    public static List<TraitEntry> lineageTraits(PlayerSystemData var0, TemplateRegistry var1) {
        ArrayList<TraitEntry> var2 = new ArrayList<>();
        String var3 = var0.loadedTemplateId();
        String var4 = var3 == null ? null : var1.rootOf(var3);

        for (TemplateDefinition var6 : var1.all()) {
            if (var6.lineage() != null) {
                for (LineageReward var8 : var6.lineage().consolation()) {
                    TraitEntry var9 = var8.trait();
                    if (var9 != null) {
                        String var10 = var8.global() ? "global" : var1.rootOf(var6.id());
                        if (var0.value(LineageDrawService.traitKey(var10, var9.type()), 0.0) > 0.0
                            && (var8.global() || var10.equals(var4))
                            && var2.stream().noneMatch(var1x -> var1x.type().equals(var9.type()))) {
                            var2.add(var9);
                        }
                    }
                }
            }
        }

        return var2;
    }

    public static double techniqueDamageBonus(PlayerSystemData var0) {
        if (var0.isActivated() && var0.loadedTemplateId() != null) {
            double var1 = 0.0;

            for (TraitEntry var4 : lineageTraits(var0, TemplateRegistry.get())) {
                var1 += param(var4, "technique_damage_bonus", 0.0);
            }

            return Math.max(0.0, var1);
        } else {
            return 0.0;
        }
    }
}
