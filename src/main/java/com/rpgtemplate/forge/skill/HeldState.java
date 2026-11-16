package com.rpgtemplate.forge.skill;

public final class HeldState {
    public final int slot;
    public final String skillId;
    public final long startTick;
    public final boolean sneakAtPress;
    public int stage;
    public int mode;
    public long lastPulse;
    public boolean charging;
    public int count;
    public long nextTick;
    public boolean burst;
    private boolean finished;

    HeldState(int var1, String var2, long var3, boolean var5) {
        this.slot = var1;
        this.skillId = var2;
        this.startTick = var3;
        this.sneakAtPress = var5;
    }

    public int heldTicks(long var1) {
        return (int)(var1 - this.startTick);
    }

    public void finish() {
        this.finished = true;
    }

    public boolean finished() {
        return this.finished;
    }
}
