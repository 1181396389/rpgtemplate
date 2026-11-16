package com.rpgtemplate.forge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDistributor {
    private PacketDistributor() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player != null && player.connection != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendToServer(CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }
}
