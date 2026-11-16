package com.rpgtemplate.forge.fx;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** 非粒子特效广播辅助（协议已在 ModNetwork 统一注册）。 */
public final class FxNet {
    private FxNet() {
    }

    public static void register() {
    }

    public static void sendNear(ServerLevel level, double x, double y, double z, double range, FxMsg msg) {
        PacketDistributor.sendToPlayersNear(level, null, x, y, z, range, msg);
    }
}
