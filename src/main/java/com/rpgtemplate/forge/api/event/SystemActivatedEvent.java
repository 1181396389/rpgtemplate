package com.rpgtemplate.forge.api.event;

import net.minecraft.server.level.ServerPlayer;

public class SystemActivatedEvent extends RpgPlayerEvent {
    public SystemActivatedEvent(ServerPlayer var1) {
        super(var1);
    }
}
