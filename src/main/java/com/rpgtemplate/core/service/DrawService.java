package com.rpgtemplate.core.service;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.stats.StatFormula;
import com.rpgtemplate.core.template.Quality;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.DoubleSupplier;

public final class DrawService {
    private DrawService() {
    }

    public static DrawService.Status check(PlayerSystemData var0) {
        if (!var0.isActivated()) {
            return DrawService.Status.NOT_ACTIVATED;
        } else if (var0.pendingDraws() <= 0) {
            return DrawService.Status.NO_PENDING;
        } else {
            return !var0.isDrawUnlocked() ? DrawService.Status.LOCKED : DrawService.Status.SUCCESS;
        }
    }

    public static DrawService.Result draw(PlayerSystemData var0, TemplateRegistry var1, CoreConfig var2, double var3, DoubleSupplier var5) {
        DrawService.Status var6 = check(var0);
        if (var6 != DrawService.Status.SUCCESS) {
            return new DrawService.Result(var6, null, 0.0);
        } else {
            HashSet var7 = new HashSet();

            for (TemplateInstance var9 : var0.templates()) {
                var7.add(var1.rootOf(var9.templateId()));
            }

            EnumMap<Quality, List<TemplateDefinition>> var12 = new EnumMap<>(Quality.class);

            for (TemplateDefinition var10 : var1.all()) {
                if (var10.drawable() && var10.quality().drawable() && !var7.contains(var1.rootOf(var10.id()))) {
                    var12.computeIfAbsent(var10.quality(), var0x -> new ArrayList<>()).add(var10);
                }
            }

            var0.setPendingDraws(var0.pendingDraws() - 1);
            var0.incrementTotalDraws();
            if (var12.isEmpty()) {
                double var15 = StatFormula.xpToNextLevel(var0.level(), var2) * var2.emptyPoolXpRatio;
                var0.setXp(var0.xp() + var15);
                return new DrawService.Result(DrawService.Status.POOL_EMPTY, null, var15);
            } else {
                Quality var14 = rollQuality(var12, var3, var5);
                List var16 = (List)var12.get(var14);
                TemplateDefinition var11 = (TemplateDefinition)var16.get(Math.min(var16.size() - 1, (int)(var5.getAsDouble() * (double)var16.size())));
                var0.addTemplate(new TemplateInstance(var11.id(), 1, 0.0));
                var0.setNewestTemplateId(var11.id());
                var0.setDrawUnlocked(false);
                return new DrawService.Result(DrawService.Status.SUCCESS, var11, 0.0);
            }
        }
    }

    static Quality rollQuality(Map<Quality, List<TemplateDefinition>> var0, double var1, DoubleSupplier var3) {
        double var4 = 0.0;
        EnumMap<Quality, Double> var6 = new EnumMap<>(Quality.class);

        for (Quality var8 : var0.keySet()) {
            double var9 = var8.weight();
            if (var8.ordinal() >= Quality.SR.ordinal()) {
                var9 *= 1.0 + Math.max(0.0, var1);
            }

            var6.put(var8, var9);
            var4 += var9;
        }

        double var12 = var3.getAsDouble() * var4;
        Quality var13 = null;

        for (Entry<Quality, Double> var11 : var6.entrySet()) {
            var13 = (Quality)var11.getKey();
            var12 -= var11.getValue();
            if (var12 < 0.0) {
                return (Quality)var11.getKey();
            }
        }

        return var13;
    }

    public static final class Result {
        public final DrawService.Status status;
        public final TemplateDefinition template;
        public final double compensationXp;

        Result(DrawService.Status var1, TemplateDefinition var2, double var3) {
            this.status = var1;
            this.template = var2;
            this.compensationXp = var3;
        }
    }

    public static enum Status {
        SUCCESS,
        NOT_ACTIVATED,
        NO_PENDING,
        LOCKED,
        POOL_EMPTY;
    }
}
