package com.rpgtemplate.core.config;

public final class CoreConfig {
    public final GrowthMode growthMode;
    public final double exponentialBase;
    public final double statCap;
    public final double linearXpBase;
    public final double linearXpPower;
    public final double expXpBase;
    public final double expXpGrowth;
    public final double templateXpBase;
    public final double templateXpPower;
    public final double templateLevelBonusPerLevel;
    public final double collectionBonusRatio;
    public final double emptyPoolXpRatio;
    private static volatile CoreConfig current = builder().build();

    private CoreConfig(CoreConfig.Builder var1) {
        this.growthMode = var1.growthMode;
        this.exponentialBase = var1.exponentialBase;
        this.statCap = var1.statCap;
        this.linearXpBase = var1.linearXpBase;
        this.linearXpPower = var1.linearXpPower;
        this.expXpBase = var1.expXpBase;
        this.expXpGrowth = var1.expXpGrowth;
        this.templateXpBase = var1.templateXpBase;
        this.templateXpPower = var1.templateXpPower;
        this.templateLevelBonusPerLevel = var1.templateLevelBonusPerLevel;
        this.collectionBonusRatio = var1.collectionBonusRatio;
        this.emptyPoolXpRatio = var1.emptyPoolXpRatio;
    }

    public static CoreConfig get() {
        return current;
    }

    public static void set(CoreConfig var0) {
        current = var0;
    }

    public static CoreConfig.Builder builder() {
        return new CoreConfig.Builder();
    }

    public static final class Builder {
        private GrowthMode growthMode = GrowthMode.LINEAR;
        private double exponentialBase = 2.0;
        private double statCap = 1.0E300;
        private double linearXpBase = 100.0;
        private double linearXpPower = 1.5;
        private double expXpBase = 100.0;
        private double expXpGrowth = 1.6;
        private double templateXpBase = 50.0;
        private double templateXpPower = 1.4;
        private double templateLevelBonusPerLevel = 0.05;
        private double collectionBonusRatio = 0.1;
        private double emptyPoolXpRatio = 0.5;

        public CoreConfig.Builder growthMode(GrowthMode var1) {
            this.growthMode = var1;
            return this;
        }

        public CoreConfig.Builder exponentialBase(double var1) {
            this.exponentialBase = Math.max(1.0001, var1);
            return this;
        }

        public CoreConfig.Builder statCap(double var1) {
            this.statCap = var1;
            return this;
        }

        public CoreConfig.Builder linearXpBase(double var1) {
            this.linearXpBase = var1;
            return this;
        }

        public CoreConfig.Builder linearXpPower(double var1) {
            this.linearXpPower = var1;
            return this;
        }

        public CoreConfig.Builder expXpBase(double var1) {
            this.expXpBase = var1;
            return this;
        }

        public CoreConfig.Builder expXpGrowth(double var1) {
            this.expXpGrowth = Math.max(1.0001, var1);
            return this;
        }

        public CoreConfig.Builder templateXpBase(double var1) {
            this.templateXpBase = var1;
            return this;
        }

        public CoreConfig.Builder templateXpPower(double var1) {
            this.templateXpPower = var1;
            return this;
        }

        public CoreConfig.Builder templateLevelBonusPerLevel(double var1) {
            this.templateLevelBonusPerLevel = var1;
            return this;
        }

        public CoreConfig.Builder collectionBonusRatio(double var1) {
            this.collectionBonusRatio = Math.max(0.0, var1);
            return this;
        }

        public CoreConfig.Builder emptyPoolXpRatio(double var1) {
            this.emptyPoolXpRatio = Math.max(0.0, var1);
            return this;
        }

        public CoreConfig build() {
            return new CoreConfig(this);
        }
    }
}
