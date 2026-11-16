package com.rpgtemplate.nothingthere;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 服务端血效果入口（重做版）。
 */
public final class NtGore {
    private static final double RANGE = 128.0;
    private static final int WOUND_TICKS = 100;

    private NtGore() {
    }

    private static int seed() {
        return ThreadLocalRandom.current().nextInt();
    }

    private static void send(ServerLevel level, Entity anchor, GoreMsg msg) {
        GoreNet.sendNear(level, anchor.getX(), anchor.getY(), anchor.getZ(), RANGE, msg);
    }

    public static void hitBurst(ServerLevel level, Entity target) {
        send(level, target, new GoreMsg(GoreMsg.WOUND, 0, 0, 0, 0, 0, 0,
                1.0f, WOUND_TICKS, seed(), target.getId()));
    }

    public static void castMist(ServerLevel level, Entity caster) {
        send(level, caster, new GoreMsg(GoreMsg.CAST_MIST, 0, 0, 0, 0, 0, 0,
                1.6f, 8, seed(), caster.getId()));
    }
}
