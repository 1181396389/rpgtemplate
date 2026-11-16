package com.rpgtemplate.core.xp;

import java.util.HashMap;
import java.util.Map;

public final class DiminishingTracker {
    private final Map<String, DiminishingTracker.Entry> entries = new HashMap<>();
    private final double factor;
    private final long window;
    private final double floor;

    public DiminishingTracker(double var1, long var3, double var5) {
        this.factor = var1;
        this.window = var3;
        this.floor = var5;
    }

    public double hit(String var1, long var2) {
        if (this.entries.size() > 512) {
            this.entries.entrySet().removeIf(var3 -> var2 - var3.getValue().lastTime() > this.window);
        }

        DiminishingTracker.Entry var4 = this.entries.get(var1);
        int var5 = var4 != null && var2 - var4.lastTime() <= this.window ? var4.count() : 0;
        this.entries.put(var1, new DiminishingTracker.Entry(var5 + 1, var2));
        return Math.max(this.floor, Math.pow(this.factor, (double)var5));
    }

    public double peek(String var1, long var2) {
        DiminishingTracker.Entry var4 = this.entries.get(var1);
        int var5 = var4 != null && var2 - var4.lastTime() <= this.window ? var4.count() : 0;
        return Math.max(this.floor, Math.pow(this.factor, (double)var5));
    }

    private static record Entry(int count, long lastTime) {
    }
}
