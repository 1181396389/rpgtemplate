package com.rpgtemplate.nothingthere;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端血效果入口（重做版）。不再在地面生成血溅，只发送两类消息：
 * 1. WOUND：血迹贴在被击中的生物身上，随生物移动，持续滴血，血滴落到脚下形成血池。
 * 2. CAST_MIST：血雾围绕施法者（玩家）身边生成。
 * 客户端 NtGoreFx 负责几何绘制，不使用粒子。
 */
public final class NtGore {
    private static final double RANGE = 128.0;
    /** 伤口持续时间（tick），约 5 秒。 */
    private static final int WOUND_TICKS = 100;

    private NtGore() {
    }

    private static int seed() {
        return ThreadLocalRandom.current().nextInt();
    }

    private static void send(ServerLevel level, Entity anchor, GoreMsg msg) {
        GoreNet.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        anchor.m_20185_(), anchor.m_20186_(), anchor.m_20189_(), RANGE, level.m_46472_())),
                msg);
    }

    /** 命中生物：血迹贴在目标身上，持续滴血并在脚下形成血池。 */
    public static void hitBurst(ServerLevel level, Entity target) {
        // count = 持续 tick，radius = 强度（影响伤口数量与大小）
        send(level, target, new GoreMsg(GoreMsg.WOUND, 0, 0, 0, 0, 0, 0,
                1.0f, WOUND_TICKS, seed(), target.m_19879_()));
    }

    /** 施法者身边的血雾（环绕玩家身体，跟随玩家移动）。 */
    public static void castMist(ServerLevel level, Entity caster) {
        send(level, caster, new GoreMsg(GoreMsg.CAST_MIST, 0, 0, 0, 0, 0, 0,
                1.6f, 8, seed(), caster.m_19879_()));
    }
}
