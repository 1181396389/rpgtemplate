package com.rpgtemplate.forge.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public abstract class RpgPlayerEvent extends Event {
    private final ServerPlayer player;

    protected RpgPlayerEvent(ServerPlayer var1) {
        this.player = var1;
    }

    public ServerPlayer getPlayer() {
        return this.player;
    }
}
