package com.rpgtemplate.forge.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;

public class XpGainEvent extends RpgPlayerEvent implements ICancellableEvent {
    private final ResourceLocation source;
    private double amount;

    public XpGainEvent(ServerPlayer player, double amount, ResourceLocation source) {
        super(player);
        this.amount = amount;
        this.source = source;
    }

    public double getAmount() {
        return this.amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public ResourceLocation getSource() {
        return this.source;
    }
}
