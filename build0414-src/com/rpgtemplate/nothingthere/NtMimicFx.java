package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.fx.Fx;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 殷红迷雾·血雾弥漫的非粒子特效。只做血雾气团（不做尖刺、环或柱，不加音效）。
 * 通过 Fx 通道发送几何参数，客户端绘制朝向镜头的软烟团。
 */
final class NtMimicFx {
    private static final int MIST = 0xB32429;
    private static final int CLOT = 0x5C080D;

    private NtMimicFx() {
    }

    /** 施放：血雾从持有者身上向外喷出一团气体（雾团，不做环或柱）。 */
    static void cast(ServerPlayer player, double radius) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        double px = player.m_20185_();
        double py = player.m_20186_() + 1.0;
        double pz = player.m_20189_();
        Fx.burst(level, Fx.PUFF, MIST, 0.8f, 36, 1.0f, px, py, pz, 36,
                radius * 0.6, 0.6, radius * 0.6);
        Fx.burst(level, Fx.PUFF, CLOT, 0.6f, 30, 0.8f, px, py, pz, 18,
                radius * 0.4, 0.4, radius * 0.4);
    }

    /**
     * 每段命中：目标处爆出血块与暗红雾团；持有者周围随段数扩大的雾团。
     *
     * @param index 第几段（从 0 开始）
     */
    static void hit(ServerPlayer player, int index, int segments, double radius, List<LivingEntity> targets) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        for (LivingEntity entity : targets) {
            double x = entity.m_20185_();
            double y = entity.m_20186_() + entity.m_20206_() * 0.5;
            double z = entity.m_20189_();
            Fx.burst(level, Fx.SPARK, CLOT, 0.15f, 16, 0.5f, x, y, z, 10, 0.3, 0.4, 0.3);
            Fx.burst(level, Fx.PUFF, MIST, 0.5f, 20, 0.5f, x, y, z, 8, 0.25, 0.25, 0.25);
        }
        double px = player.m_20185_();
        double py = player.m_20186_() + 0.9;
        double pz = player.m_20189_();
        double reach = radius * (index + 1.0) / segments;
        Fx.burst(level, Fx.PUFF, MIST, 0.7f, 24, 0.8f, px, py, pz, 14,
                reach * 0.8, 0.5, reach * 0.8);
    }
}
