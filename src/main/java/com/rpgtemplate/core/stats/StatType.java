package com.rpgtemplate.core.stats;

public enum StatType {
    HP(20.0),
    MP(10.0),
    SP(100.0),
    DEF(1.0);

    private final double base;

    private StatType(double var3) {
        this.base = var3;
    }

    public double base() {
        return this.base;
    }
}
