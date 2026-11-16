package com.rpgtemplate.forge.skillbar;

import net.neoforged.neoforge.network.PacketDistributor;

public final class TacticalNet {
    private TacticalNet() {
    }

    public static void sendToServer(TacticalSkillMsg msg) {
        PacketDistributor.sendToServer(msg);
    }
}
