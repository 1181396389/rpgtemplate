package com.rpgtemplate.nothingthere;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** 血效果专用广播辅助（协议已在 ModNetwork 统一注册）。 */
public final class GoreNet {
    private GoreNet() {
    }

    public static void register() {
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, double range, GoreMsg msg) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, range, msg);
    }
}
