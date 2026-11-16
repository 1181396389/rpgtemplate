package com.rpgtemplate.core.service;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatFormula;
import com.rpgtemplate.core.template.LineageEntry;
import com.rpgtemplate.core.template.LineageReward;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;

public final class LineageDrawService {
    public static final String FAIL_PREFIX = "lineage.fail.";
    public static final String BONUS_PREFIX = "lineage.bonus.";
    public static final String TRAIT_PREFIX = "lineage.trait.";
    public static final String TRAIT_GLOBAL = "global";

    private LineageDrawService() {
    }

    public static String failKey(String var0) {
        return "lineage.fail." + var0;
    }

    public static String bonusKey(String var0) {
        return "lineage.bonus." + var0.toLowerCase(Locale.ROOT);
    }

    public static String traitKey(String var0, String var1) {
        return "lineage.trait." + var0 + "|" + var1;
    }

    public static int failCount(PlayerSystemData var0, String var1) {
        return (int)Math.round(var0.value(failKey(var1), 0.0));
    }

    public static List<TemplateDefinition> candidates(PlayerSystemData var0, String var1, TemplateRegistry var2) {
        ArrayList var3 = new ArrayList();

        for (TemplateDefinition var5 : var2.lineageChildrenOf(var1)) {
            if (var0.level() >= var5.requiredPlayerLevel()) {
                var3.add(var5);
            }
        }

        return var3;
    }

    public static int requiredPlayerLevel(String var0, TemplateRegistry var1) {
        int var2 = Integer.MAX_VALUE;

        for (TemplateDefinition var4 : var1.lineageChildrenOf(var0)) {
            var2 = Math.min(var2, var4.requiredPlayerLevel());
        }

        return var2 == Integer.MAX_VALUE ? 0 : var2;
    }

    public static LineageDrawService.Status check(PlayerSystemData var0, String var1, String var2, TemplateRegistry var3) {
        LineageDrawService.Status var4 = check(var0, var1, var3);
        if (var4 == LineageDrawService.Status.SUCCESS && var2 != null) {
            boolean var5 = var3.lineageChildrenOf(var1).stream().anyMatch(var1x -> var1x.id().equals(var2));
            if (!var5) {
                return LineageDrawService.Status.BRANCH_UNAVAILABLE;
            } else {
                return candidates(var0, var1, var3).stream().anyMatch(var1x -> var1x.id().equals(var2))
                    ? LineageDrawService.Status.SUCCESS
                    : LineageDrawService.Status.PLAYER_LEVEL_TOO_LOW;
            }
        } else {
            return var4;
        }
    }

    public static LineageDrawService.Status check(PlayerSystemData var0, String var1, TemplateRegistry var2) {
        if (!var0.isActivated()) {
            return LineageDrawService.Status.NOT_ACTIVATED;
        } else {
            TemplateInstance var3 = var0.findTemplate(var1).orElse(null);
            if (var3 == null) {
                return LineageDrawService.Status.NOT_OWNED;
            } else {
                TemplateDefinition var4 = var2.find(var1).orElse(null);
                if (var4 != null && !var2.lineageChildrenOf(var1).isEmpty()) {
                    if (!var4.levelless() && var3.level() < var4.maxLevel()) {
                        return LineageDrawService.Status.NOT_MAX_LEVEL;
                    } else if (candidates(var0, var1, var2).isEmpty()) {
                        return LineageDrawService.Status.PLAYER_LEVEL_TOO_LOW;
                    } else if (var0.pendingDraws() <= 0) {
                        return LineageDrawService.Status.NO_PENDING;
                    } else {
                        return !var0.isDrawUnlocked() ? LineageDrawService.Status.LOCKED : LineageDrawService.Status.SUCCESS;
                    }
                } else {
                    return LineageDrawService.Status.NO_LINEAGE;
                }
            }
        }
    }

    public static LineageDrawService.Result draw(PlayerSystemData var0, String var1, TemplateRegistry var2, CoreConfig var3, double var4, DoubleSupplier var6) {
        return draw(var0, var1, null, var2, var3, var4, var6);
    }

    public static LineageDrawService.Result draw(
        PlayerSystemData var0, String var1, String var2, TemplateRegistry var3, CoreConfig var4, double var5, DoubleSupplier var7
    ) {
        LineageDrawService.Status var9 = check(var0, var1, var2, var3);
        if (var9 != LineageDrawService.Status.SUCCESS) {
            return new LineageDrawService.Result(var9, var3.find(var1).orElse(null), var2 == null ? null : var3.find(var2).orElse(null));
        } else {
            TemplateInstance var10 = var0.findTemplate(var1).orElseThrow(IllegalStateException::new);
            TemplateDefinition var11 = var3.find(var1).orElseThrow(IllegalStateException::new);
            List<TemplateDefinition> var12 = candidates(var0, var1, var3);
            TemplateDefinition var13 = var2 == null
                ? pickBranch(var12, var7)
                : var12.stream().filter(var1x -> var1x.id().equals(var2)).findFirst().orElseThrow(IllegalStateException::new);
            LineageEntry var14 = var13.lineage();
            var0.setPendingDraws(var0.pendingDraws() - 1);
            var0.incrementTotalDraws();
            String var15 = failKey(var1);
            int var16 = failCount(var0, var1);
            double var17 = var14.effectiveChance(var5);
            boolean var19 = var14.pityReached(var16);
            boolean var8 = var19 || var7.getAsDouble() < var17;
            if (var8) {
                int var24 = var14.mergedLevel(var10.level(), var13.maxLevel());
                var10.setTemplateId(var13.id());
                var10.setLevel(var24);
                var10.setXp(0.0);
                if (var1.equals(var0.loadedTemplateId())) {
                    var0.setLoadedTemplateId(var13.id());
                }

                var0.setNewestTemplateId(var13.id());
                var0.setDrawUnlocked(false);
                LevelingService.checkDrawUnlock(var0, var3, null);
                var0.removeValue(var15);
                StatCalculator.normalizeCurrent(var0, var3, var4);
                LineageDrawService.Result var22 = new LineageDrawService.Result(LineageDrawService.Status.SUCCESS, var11, var13);
                var22.chance = var17;
                var22.pityTriggered = var19;
                var22.mergedLevel = var24;
                var22.failsBefore = var16;
                return var22;
            } else {
                var0.setValue(var15, (double)(++var16));
                LineageDrawService.Result var21 = new LineageDrawService.Result(LineageDrawService.Status.FAILED, var11, var13);
                var21.chance = var17;
                var21.failsBefore = var16 - 1;
                var21.consolation = pickReward(var14.consolation(), var7);
                if (var21.consolation != null) {
                    var16 = applyConsolation(var0, var21, var16, var15, var3, var4);
                }

                var21.fails = var16;
                var21.pityIn = var14.pity() > 0 ? Math.max(0, var14.pity() - var16) : -1;
                StatCalculator.normalizeCurrent(var0, var3, var4);
                return var21;
            }
        }
    }

    private static int applyConsolation(PlayerSystemData var0, LineageDrawService.Result var1, int var2, String var3, TemplateRegistry var4, CoreConfig var5) {
        LineageReward var7 = var1.consolation;
        String var8 = var7.type();
        switch (var8) {
            case "xp":
                double var18;
                var1.compensationXp = var18 = StatFormula.xpToNextLevel(var0.level(), var5) * Math.max(0.0, var7.num("ratio", 0.3));
                var1.xpResult = LevelingService.addXp(var0, var18, var4, var5);
                var1.consolationApplied = true;
                break;
            case "draw":
                var0.setPendingDraws(var0.pendingDraws() + Math.max(1, var7.integer("count", 1)));
                var1.consolationApplied = true;
                break;
            case "shard":
                var0.setValue(var3, (double)(var2 += Math.max(1, var7.integer("count", 1))));
                var1.consolationApplied = true;
                break;
            case "stat_bonus":
                String var17 = bonusKey(var7.str("stat", "hp"));
                double var19 = var0.value(var17, 0.0);
                double var13 = Math.max(0.0, var7.num("max", 0.3));
                double var15 = Math.min(var13, var19 + Math.max(0.0, var7.num("value", 0.02)));
                var0.setValue(var17, var15);
                var1.consolationApplied = var15 > var19;
                break;
            case "trait":
                if (var7.trait() != null) {
                    String var10 = var7.global() ? "global" : var4.rootOf(var1.source.id());
                    String var11 = traitKey(var10, var7.trait().type());
                    var1.consolationApplied = var0.value(var11, 0.0) <= 0.0;
                    var0.setValue(var11, 1.0);
                }
        }

        return var2;
    }

    static TemplateDefinition pickBranch(List<TemplateDefinition> var0, DoubleSupplier var1) {
        double var2 = 0.0;

        for (TemplateDefinition var5 : var0) {
            var2 += var5.lineage().safeWeight();
        }

        double var8 = var1.getAsDouble() * var2;

        for (TemplateDefinition var7 : var0) {
            if ((var8 -= var7.lineage().safeWeight()) < 0.0) {
                return var7;
            }
        }

        return (TemplateDefinition)var0.get(var0.size() - 1);
    }

    static LineageReward pickReward(List<LineageReward> var0, DoubleSupplier var1) {
        if (var0 != null && !var0.isEmpty()) {
            double var2 = 0.0;

            for (LineageReward var5 : var0) {
                var2 += var5.weight();
            }

            if (var2 <= 0.0) {
                return null;
            } else {
                double var8 = var1.getAsDouble() * var2;

                for (LineageReward var7 : var0) {
                    if ((var8 -= var7.weight()) < 0.0) {
                        return var7;
                    }
                }

                return (LineageReward)var0.get(var0.size() - 1);
            }
        } else {
            return null;
        }
    }

    public static final class Result {
        public final LineageDrawService.Status status;
        public final TemplateDefinition source;
        public final TemplateDefinition target;
        public double chance;
        public boolean pityTriggered;
        public int mergedLevel;
        public int failsBefore;
        public int fails;
        public int pityIn = -1;
        public LineageReward consolation;
        public boolean consolationApplied;
        public double compensationXp;
        public LevelingService.Result xpResult;

        Result(LineageDrawService.Status var1, TemplateDefinition var2, TemplateDefinition var3) {
            this.status = var1;
            this.source = var2;
            this.target = var3;
        }
    }

    public static enum Status {
        SUCCESS,
        FAILED,
        NOT_ACTIVATED,
        NOT_OWNED,
        NO_LINEAGE,
        NOT_MAX_LEVEL,
        PLAYER_LEVEL_TOO_LOW,
        NO_PENDING,
        LOCKED,
        BRANCH_UNAVAILABLE;
    }
}
