package com.rpgtemplate.core.service;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatFormula;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;

public final class LevelingService {
    private static final int MAX_LEVELS_PER_CALL = 10000;

    private LevelingService() {
    }

    public static LevelingService.Result addXp(PlayerSystemData var0, double var1, TemplateRegistry var3, CoreConfig var4) {
        LevelingService.Result var5 = new LevelingService.Result();
        if (var0.isActivated() && !(var1 <= 0.0) && !Double.isNaN(var1)) {
            var1 = StatFormula.clamp(var1, var4.statCap);
            double var6 = var0.xp() + var1;
            int var8 = var0.level();

            for (int var9 = 0; var9++ < 10000; var5.playerLevelsGained++) {
                double var10 = StatFormula.xpToNextLevel(var8, var4);
                if (var6 < var10) {
                    break;
                }

                var6 -= var10;
                var8++;
            }

            var0.setLevel(var8);
            var0.setXp(var6);
            if (var5.playerLevelsGained > 0) {
                var0.setPendingDraws(var0.pendingDraws() + var5.playerLevelsGained);
            }

            TemplateInstance var13 = var0.loadedTemplate().orElse(null);
            if (var13 != null) {
                TemplateDefinition var14 = var3.find(var13.templateId()).orElse(null);
                if (var14 != null && !var14.levelless()) {
                    addTemplateXp(var13, var14, var1, var4, var5);
                }
            }

            checkDrawUnlock(var0, var3, var5);
            if (var5.playerLevelsGained > 0) {
                StatCalculator.refill(var0, var3, var4);
            } else {
                StatCalculator.normalizeCurrent(var0, var3, var4);
            }

            return var5;
        } else {
            return var5;
        }
    }

    public static double templateXpToNext(int var0, TemplateDefinition var1, CoreConfig var2) {
        double var3 = 1.0 + (double)var1.quality().ordinal() * 0.5;
        return Math.max(1.0, var2.templateXpBase * Math.pow((double)Math.max(1, var0), var2.templateXpPower) * var3);
    }

    private static void addTemplateXp(TemplateInstance var0, TemplateDefinition var1, double var2, CoreConfig var4, LevelingService.Result var5) {
        int var6 = var1.maxLevel();
        if (var0.level() >= var6) {
            var0.setXp(0.0);
        } else {
            double var7 = var0.xp() + var2;
            int var9 = var0.level();

            while (var9 < var6) {
                double var10 = templateXpToNext(var9, var1, var4);
                if (var7 < var10) {
                    break;
                }

                var7 -= var10;
                var9++;
                var5.templateLevelsGained++;
            }

            if (var9 >= var6) {
                var7 = 0.0;
                var5.templateReachedMax = true;
            }

            var0.setLevel(var9);
            var0.setXp(var7);
        }
    }

    public static void checkDrawUnlock(PlayerSystemData var0, TemplateRegistry var1, LevelingService.Result var2) {
        if (!var0.isDrawUnlocked()) {
            TemplateInstance var3 = var0.findTemplate(var0.newestTemplateId()).orElse(null);
            if (var3 == null) {
                var0.setDrawUnlocked(true);
                if (var2 != null) {
                    var2.drawJustUnlocked = true;
                }
            } else {
                TemplateDefinition var4 = var1.find(var3.templateId()).orElse(null);
                if (var4 == null || var4.levelless() || var3.level() >= var4.maxLevel()) {
                    var0.setDrawUnlocked(true);
                    if (var2 != null) {
                        var2.drawJustUnlocked = true;
                    }
                }
            }
        }
    }

    public static void setLevel(PlayerSystemData var0, int var1, TemplateRegistry var2, CoreConfig var3) {
        int var4 = var1 - var0.level();
        var0.setLevel(var1);
        var0.setXp(0.0);
        if (var4 > 0) {
            var0.setPendingDraws(var0.pendingDraws() + var4);
        }

        StatCalculator.refill(var0, var2, var3);
    }

    public static double maxHp(PlayerSystemData var0, TemplateRegistry var1, CoreConfig var2) {
        return StatCalculator.maxStat(StatType.HP, var0, var1, var2);
    }

    public static final class Result {
        public int playerLevelsGained;
        public int templateLevelsGained;
        public boolean templateReachedMax;
        public boolean drawJustUnlocked;

        public boolean anyChange() {
            return this.playerLevelsGained > 0 || this.templateLevelsGained > 0 || this.drawJustUnlocked;
        }
    }
}
