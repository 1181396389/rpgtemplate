package com.rpgtemplate.forge.api.event;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;

public class TemplateLoadEvent extends RpgPlayerEvent implements ICancellableEvent {
    @Nullable
    private final String oldTemplateId;
    @Nullable
    private final String newTemplateId;

    public TemplateLoadEvent(ServerPlayer player, @Nullable String oldTemplateId, @Nullable String newTemplateId) {
        super(player);
        this.oldTemplateId = oldTemplateId;
        this.newTemplateId = newTemplateId;
    }

    @Nullable
    public String getOldTemplateId() {
        return this.oldTemplateId;
    }

    @Nullable
    public String getNewTemplateId() {
        return this.newTemplateId;
    }
}
