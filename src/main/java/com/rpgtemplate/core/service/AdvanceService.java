package com.rpgtemplate.core.service;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;

public final class AdvanceService {
    private AdvanceService() {
    }

    public static AdvanceService.Status check(PlayerSystemData var0, String var1, TemplateRegistry var2) {
        TemplateInstance var3 = var0.findTemplate(var1).orElse(null);
        if (var3 == null) {
            return AdvanceService.Status.NOT_OWNED;
        } else {
            TemplateDefinition var4 = var2.find(var1).orElse(null);
            TemplateDefinition var5 = var2.advancementOf(var1).orElse(null);
            if (var4 != null && var5 != null) {
                if (var3.level() < var4.maxLevel()) {
                    return AdvanceService.Status.NOT_MAX_LEVEL;
                } else {
                    return var0.level() < var5.advanceRequiredPlayerLevel() ? AdvanceService.Status.PLAYER_LEVEL_TOO_LOW : AdvanceService.Status.SUCCESS;
                }
            } else {
                return AdvanceService.Status.NO_ADVANCEMENT;
            }
        }
    }

    public static AdvanceService.Status advance(PlayerSystemData var0, String var1, TemplateRegistry var2, CoreConfig var3) {
        AdvanceService.Status var4 = check(var0, var1, var2);
        if (var4 != AdvanceService.Status.SUCCESS) {
            return var4;
        } else {
            TemplateInstance var5 = var0.findTemplate(var1).orElseThrow(IllegalStateException::new);
            TemplateDefinition var6 = var2.advancementOf(var1).orElseThrow(IllegalStateException::new);
            var5.setTemplateId(var6.id());
            var5.setXp(0.0);
            if (var1.equals(var0.loadedTemplateId())) {
                var0.setLoadedTemplateId(var6.id());
            }

            var0.setNewestTemplateId(var6.id());
            var0.setDrawUnlocked(false);
            LevelingService.checkDrawUnlock(var0, var2, null);
            StatCalculator.normalizeCurrent(var0, var2, var3);
            return AdvanceService.Status.SUCCESS;
        }
    }

    public static enum Status {
        SUCCESS,
        NOT_OWNED,
        NO_ADVANCEMENT,
        NOT_MAX_LEVEL,
        PLAYER_LEVEL_TOO_LOW;
    }
}
