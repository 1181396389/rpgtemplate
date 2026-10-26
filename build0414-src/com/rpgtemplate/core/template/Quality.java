/*
 * Decompiled with CFR 0.152.
 */
package com.rpgtemplate.core.template;

import java.util.Locale;

public enum Quality {
    BASE(0.0, 0, 0.5, 1.0, 0, -2039584),
    N(40.0, 10, 1.0, 1.0, 0, -5197648),
    R(30.0, 20, 1.5, 2.0, 1, -11162881),
    SR(18.0, 30, 2.2, 4.0, 2, -4952833),
    SSR(8.0, 40, 3.2, 8.0, 3, -14006),
    UR(3.5, 50, 4.5, 16.0, 5, -42406),
    EX(0.5, 60, 6.0, 32.0, 8, -1),
    EX_PLUS(0.15, 70, 8.0, 64.0, 10, 0xFF90E8FF),
    LR(0.05, 80, 10.0, 128.0, 12, 0xFFFFB469),
    MR(0.01, 90, 12.0, 256.0, 15, 0xFFFF88CF);

    private final double weight;
    private final int maxLevel;
    private final double bonusMultiplier;
    private final double costMultiplier;
    private final int ruleBonus;
    private final int color;

    private Quality(double d, int n2, double d2, double d3, int n3, int n4) {
        this.weight = d;
        this.maxLevel = n2;
        this.bonusMultiplier = d2;
        this.costMultiplier = d3;
        this.ruleBonus = n3;
        this.color = n4;
    }

    public double weight() {
        return this.weight;
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    public double bonusMultiplier() {
        return this.bonusMultiplier;
    }

    public double costMultiplier() {
        return this.costMultiplier;
    }

    public int ruleBonus() {
        return this.ruleBonus;
    }

    public int color() {
        return this.color;
    }

    public String label() { return this == EX_PLUS ? "EX+" : name(); }

    public boolean drawable() {
        return this != BASE;
    }

    public static Quality parse(String string) {
        return Quality.valueOf(string.trim().toUpperCase(Locale.ROOT).replace("EX+", "EX_PLUS"));
    }
}

