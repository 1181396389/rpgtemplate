package com.rpgtemplate.core.service;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.template.TemplateRegistry;

public final class TemplateLoadService {
    public static final int SYNC_TICKS = 20;
    public static final int SWITCH_COOLDOWN_TICKS = 20;

    private TemplateLoadService() {
    }

    public static TemplateLoadService.Status load(PlayerSystemData var0, String var1, TemplateRegistry var2, CoreConfig var3) {
        if (!var0.isActivated()) {
            return TemplateLoadService.Status.NOT_ACTIVATED;
        } else if (var0.findTemplate(var1).isEmpty()) {
            return TemplateLoadService.Status.NOT_OWNED;
        } else if (var1.equals(var0.loadedTemplateId())) {
            return TemplateLoadService.Status.ALREADY_LOADED;
        } else {
            var0.setLoadedTemplateId(var1);
            StatCalculator.normalizeCurrent(var0, var2, var3);
            return TemplateLoadService.Status.SUCCESS;
        }
    }

    public static TemplateLoadService.Status unload(PlayerSystemData var0, TemplateRegistry var1, CoreConfig var2) {
        if (!var0.isActivated()) {
            return TemplateLoadService.Status.NOT_ACTIVATED;
        } else {
            var0.setLoadedTemplateId(null);
            StatCalculator.normalizeCurrent(var0, var1, var2);
            return TemplateLoadService.Status.SUCCESS;
        }
    }

    public static enum Status {
        SUCCESS,
        NOT_ACTIVATED,
        NOT_OWNED,
        ALREADY_LOADED;
    }
}
