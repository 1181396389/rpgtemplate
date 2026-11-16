package com.rpgtemplate.core.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AdaptationModel {
    private final Map<String, AdaptationModel.Entry> entries = new LinkedHashMap<>();

    public AdaptationModel.Result hit(String var1, long var2, boolean var4, AdaptationModel.Params var5, int var6) {
        if (var1 != null && var5.maxTurns() > 0) {
            int var7 = Math.max(0, Math.min(var5.maxTurns(), var6));
            AdaptationModel.Entry var8 = this.entries.computeIfAbsent(var1, var0 -> new AdaptationModel.Entry());
            decay(var8, var2, var5);
            if (var8.turns > var7) {
                var8.turns = var7;
                var8.progress = 0.0;
            }

            int var9 = var8.turns;
            double var10 = var5.multiplier(var9);
            var8.lastHit = var2;
            boolean var12 = false;
            if (var8.turns < var7) {
                var8.progress = var8.progress + (var4 ? var5.heavyWeight() : 1.0);
                if (var8.progress + 1.0E-9 >= var5.hitsPerTurn()) {
                    var8.progress = 0.0;
                    var8.turns++;
                    var12 = true;
                }
            }

            return new AdaptationModel.Result(var10, var9, var8.turns, var12);
        } else {
            return new AdaptationModel.Result(1.0, 0, 0, false);
        }
    }

    public int turns(String var1, long var2, AdaptationModel.Params var4) {
        AdaptationModel.Entry var5 = this.entries.get(var1);
        if (var5 == null) {
            return 0;
        } else {
            decay(var5, var2, var4);
            return var5.turns;
        }
    }

    public double progress(String var1) {
        AdaptationModel.Entry var2 = this.entries.get(var1);
        return var2 == null ? 0.0 : var2.progress;
    }

    public Map<String, Integer> snapshot(long var1, AdaptationModel.Params var3) {
        LinkedHashMap var4 = new LinkedHashMap();

        for (Map.Entry var6 : this.entries.entrySet()) {
            decay((AdaptationModel.Entry)var6.getValue(), var1, var3);
            if (((AdaptationModel.Entry)var6.getValue()).turns > 0) {
                var4.put((String)var6.getKey(), ((AdaptationModel.Entry)var6.getValue()).turns);
            }
        }

        return Collections.unmodifiableMap(var4);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public void clear() {
        this.entries.clear();
    }

    private static void decay(AdaptationModel.Entry var0, long var1, AdaptationModel.Params var3) {
        if (var0.lastHit > 0L || var0.turns != 0 || var0.progress != 0.0) {
            long var4 = var1 - var0.lastHit;
            if (var4 >= var3.decayTicks()) {
                long var6 = var4 / var3.decayTicks();
                var0.turns = (int)Math.max(0L, (long)var0.turns - var6);
                var0.progress = 0.0;
                var0.lastHit = var0.lastHit + var6 * var3.decayTicks();
            }
        }
    }

    private static final class Entry {
        double progress;
        int turns;
        long lastHit;
    }

    public static record Params(double hitsPerTurn, double reductionPerTurn, int maxTurns, long decayTicks, double heavyWeight) {
        public Params(double hitsPerTurn, double reductionPerTurn, int maxTurns, long decayTicks, double heavyWeight) {
            hitsPerTurn = Math.max(1.0, hitsPerTurn);
            reductionPerTurn = Math.max(0.0, Math.min(0.5, reductionPerTurn));
            maxTurns = Math.max(0, Math.min(8, maxTurns));
            decayTicks = Math.max(1L, decayTicks);
            heavyWeight = Math.max(1.0, heavyWeight);
            this.hitsPerTurn = hitsPerTurn;
            this.reductionPerTurn = reductionPerTurn;
            this.maxTurns = maxTurns;
            this.decayTicks = decayTicks;
            this.heavyWeight = heavyWeight;
        }

        public double multiplier(int var1) {
            return Math.max(0.2, 1.0 - this.reductionPerTurn * (double)Math.max(0, var1));
        }
    }

    public static record Result(double multiplier, int turnsBefore, int turnsAfter, boolean turned) {
    }
}
