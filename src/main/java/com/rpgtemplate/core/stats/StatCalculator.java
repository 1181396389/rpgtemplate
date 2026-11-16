package com.rpgtemplate.core.stats;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.production.CraftMath;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import java.util.Locale;

public final class StatCalculator {
    private StatCalculator() {
    }

    public static double maxStat(StatType var0, PlayerSystemData var1, TemplateRegistry var2, CoreConfig var3) {
        double var4 = maxStatRaw(var0, var1, var2, var3);
        double var6;
        return var0 == StatType.HP && (var6 = var1.value("hp_max_penalty", 0.0)) > 0.0 ? Math.max(var4 * 0.01, var4 - var6) : var4;
    }

    public static double maxStatRaw(StatType var0, PlayerSystemData var1, TemplateRegistry var2, CoreConfig var3) {
        double var4 = StatFormula.levelStat(var0, var1.level(), var3);
        double var6 = 1.0 + loadedBonus(var0, var1, var2, var3) + collectionBonus(var0, var1, var2, var3) + lineageBonus(var0, var1);
        double var8 = var0 == StatType.HP && var1.isActivated() && "rpgtemplate:sukuna_20_fingers".equals(var1.loadedTemplateId()) ? 1.3 : 1.0;
        return CraftMath.enhanceStat(StatFormula.clamp(var4 * Math.max(0.0, var6) * var8, var3.statCap), var0, var1, var3);
    }

    public static double lineageBonus(StatType var0, PlayerSystemData var1) {
        return Math.max(0.0, var1.value("lineage.bonus." + var0.name().toLowerCase(Locale.ROOT), 0.0));
    }

    public static double loadedBonus(StatType var0, PlayerSystemData var1, TemplateRegistry var2, CoreConfig var3) {
        TemplateInstance var4 = var1.loadedTemplate().orElse(null);
        if (var4 == null) {
            return 0.0;
        } else {
            TemplateDefinition var5 = var2.find(var4.templateId()).orElse(null);
            if (var5 == null) {
                return 0.0;
            } else {
                double var6 = 1.0 + (double)Math.max(0, var4.level() - 1) * var3.templateLevelBonusPerLevel;
                return var5.bonus(var0) * var5.quality().bonusMultiplier() * var6;
            }
        }
    }

    public static double collectionBonus(StatType var0, PlayerSystemData var1, TemplateRegistry var2, CoreConfig var3) {
        if (var3.collectionBonusRatio <= 0.0) {
            return 0.0;
        } else {
            double var4 = 0.0;

            for (TemplateInstance var7 : var1.templates()) {
                TemplateDefinition var8;
                if (!var7.templateId().equals(var1.loadedTemplateId()) && (var8 = var2.find(var7.templateId()).orElse(null)) != null) {
                    var4 += var8.bonus(var0) * var8.quality().bonusMultiplier();
                }
            }

            return var4 * var3.collectionBonusRatio;
        }
    }

    public static void normalizeCurrent(PlayerSystemData var0, TemplateRegistry var1, CoreConfig var2) {
        double var3 = maxStat(StatType.HP, var0, var1, var2);
        double var5 = maxStat(StatType.MP, var0, var1, var2);
        double var7 = maxStat(StatType.SP, var0, var1, var2);
        var0.setHp(var0.hp() < 0.0 ? var3 : Math.min(var0.hp(), var3));
        var0.setMp(var0.mp() < 0.0 ? var5 : Math.min(var0.mp(), var5));
        var0.setSp(var0.sp() < 0.0 ? var7 : Math.min(var0.sp(), var7));
    }

    public static void refill(PlayerSystemData var0, TemplateRegistry var1, CoreConfig var2) {
        var0.setHp(maxStat(StatType.HP, var0, var1, var2));
        var0.setMp(maxStat(StatType.MP, var0, var1, var2));
        var0.setSp(maxStat(StatType.SP, var0, var1, var2));
    }
}
