package com.rpgtemplate.core.template;

import com.rpgtemplate.core.stats.StatType;
import java.util.List;

public record TemplateDefinition(
    String id,
    String name,
    String source,
    String period,
    Quality quality,
    TemplateType type,
    int maxLevelOverride,
    String parent,
    int advanceRequiredPlayerLevel,
    double bonusHp,
    double bonusMp,
    double bonusSp,
    double bonusDef,
    List<SkillEntry> skills,
    List<TraitEntry> passives,
    List<TraitEntry> weaknesses,
    List<TraitEntry> negativeStates,
    List<TraitEntry> quirks,
    String description,
    boolean drawable,
    LineageEntry lineage
) {
    public TemplateDefinition(
        String var1,
        String var2,
        String var3,
        String var4,
        Quality var5,
        TemplateType var6,
        int var7,
        String var8,
        int var9,
        double var10,
        double var12,
        double var14,
        List<SkillEntry> var16,
        List<TraitEntry> var17,
        List<TraitEntry> var18,
        List<TraitEntry> var19,
        List<TraitEntry> var20,
        String var21,
        boolean var22,
        LineageEntry var23
    ) {
        this(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var12, var14, var10, var16, var17, var18, var19, var20, var21, var22, var23);
    }

    public int maxLevel() {
        return this.maxLevelOverride > 0 ? this.maxLevelOverride : this.quality.maxLevel();
    }

    public boolean levelless() {
        return this.maxLevel() <= 0;
    }

    public boolean viaLineage() {
        return this.parent != null && this.lineage != null;
    }

    public int requiredPlayerLevel() {
        return Math.max(this.advanceRequiredPlayerLevel, this.lineage == null ? 0 : this.lineage.playerLevel());
    }

    public double bonus(StatType var1) {
        switch (var1) {
            case HP:
                return this.bonusHp;
            case MP:
                return this.bonusMp;
            case SP:
                return this.bonusSp;
            case DEF:
                return this.bonusDef;
            default:
                return 0.0;
        }
    }

    public String displayName() {
        return this.period != null && !this.period.isEmpty() ? this.name + "［" + this.period + "］" : this.name;
    }
}
