package com.rpgtemplate.forge.api.event;

import net.minecraft.server.level.ServerPlayer;

public class LevelUpEvent extends RpgPlayerEvent {
    private final int oldLevel;
    private final int newLevel;

    public LevelUpEvent(ServerPlayer var1, int var2, int var3) {
        super(var1);
        this.oldLevel = var2;
        this.newLevel = var3;
    }

    public int getOldLevel() {
        return this.oldLevel;
    }

    public int getNewLevel() {
        return this.newLevel;
    }
}
