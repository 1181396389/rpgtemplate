package com.rpgtemplate.forge.combat;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.forge.ModConfigs;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class TerrainFrameBudget {
    private static MinecraftServer server;
    private static int tick = Integer.MIN_VALUE;
    private static int checks;
    private static int breaks;
    private static long nanos;

    private TerrainFrameBudget() {
    }

    private static void refresh(MinecraftServer var0) {
        int var1 = var0.getTickCount();
        if (server != var0 || tick != var1) {
            server = var0;
            tick = var1;
            checks = 0;
            breaks = 0;
            nanos = 0L;
        }
    }

    public static boolean available(MinecraftServer var0) {
        if (var0 == null) {
            return false;
        } else {
            refresh(var0);
            return checks < (Integer)ModConfigs.SERVER.terrainFrameChecks.get()
                && breaks < (Integer)ModConfigs.SERVER.terrainFrameBlocks.get()
                && nanos < (long)((Double)ModConfigs.SERVER.terrainFrameMillis.get() * 1000000.0);
        }
    }

    static void account(long var0, int var2) {
        checks++;
        breaks += var2;
        nanos = nanos + Math.max(0L, var0);
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent var0) {
        server = null;
        tick = Integer.MIN_VALUE;
        checks = 0;
        breaks = 0;
        nanos = 0L;
    }
}
