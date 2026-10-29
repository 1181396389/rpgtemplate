package com.rpgtemplate.nothingthere;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 形态 4（脊骨鞭）与形态 5（全收）的攻击效果。服务端发送粒子（ServerLevel 会发给附近玩家）和音效。
 *
 * 脊骨鞭：挥击弧线上留下骨色粉尘，并播放鞭响（NtSounds.HIT 提高音调，短而脆）。
 * 全收：每个命中目标处爆出暗红与血块色粉尘；施法者周围出现一圈血肉脉冲，并播放沉闷的肉击声（降低音调）。
 */
public final class NtFx {
    private static final Vector3f BONE = new Vector3f(0.92f, 0.87f, 0.74f);
    private static final Vector3f BONE_DARK = new Vector3f(0.58f, 0.52f, 0.42f);
    private static final Vector3f FLESH = new Vector3f(0.70f, 0.14f, 0.16f);
    private static final Vector3f CLOT = new Vector3f(0.36f, 0.03f, 0.05f);

    private NtFx() {
    }

    /** 脊骨鞭：沿挥击弧线（与 NtForms.sweep 同一弧度）铺出骨色粉尘，并播放鞭响。 */
    public static void whip(ServerPlayer player, double range, double arcDegrees) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = player.m_20154_();
        double lx = look.f_82479_;
        double lz = look.f_82481_;
        if (Math.hypot(lx, lz) < 1e-6) {
            return;
        }
        double base = Math.atan2(lz, lx);
        double half = Math.toRadians(arcDegrees / 2.0);
        double px = player.m_20185_();
        double py = player.m_20186_() + 1.1;
        double pz = player.m_20189_();
        int steps = 12;
        for (int s = 0; s <= steps; s++) {
            double a = base - half + (2.0 * half) * s / steps;
            Vector3f colour = s % 3 == 0 ? BONE : BONE_DARK;
            for (double r = 1.2; r <= range; r += 0.9) {
                level.m_8767_(new DustParticleOptions(colour, 0.9f),
                    px + Math.cos(a) * r, py, pz + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        level.m_6263_(null, px, py, pz, NtSounds.HIT, SoundSource.MASTER, 0.8f, 1.6f);
    }

    /**
     * 全收：每个命中目标处爆出血块与暗红粉尘；施法者周围出现血肉脉冲（一圈粉尘向外扩散的环）。
     *
     * @param hits 挥击命中的实体（NtForms.sweep 返回值）
     */
    public static void muscle(ServerPlayer player, List<Entity> hits) {
        if (!(player.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        for (Entity entity : hits) {
            double x = entity.m_20185_();
            double y = entity.m_20186_() + entity.m_20206_() * 0.5;
            double z = entity.m_20189_();
            level.m_8767_(new DustParticleOptions(CLOT, 1.3f), x, y, z, 14, 0.35, 0.45, 0.35, 0.02);
            level.m_8767_(new DustParticleOptions(FLESH, 1.1f), x, y, z, 10, 0.30, 0.30, 0.30, 0.02);
        }
        double px = player.m_20185_();
        double py = player.m_20186_() + 0.9;
        double pz = player.m_20189_();
        for (int i = 0; i < 16; i++) {
            double a = 2.0 * Math.PI * i / 16;
            level.m_8767_(new DustParticleOptions(FLESH, 1.0f),
                px + Math.cos(a) * 1.3, py, pz + Math.sin(a) * 1.3, 1, 0.0, 0.2, 0.0, 0.0);
        }
        level.m_6263_(null, px, py, pz, NtSounds.HIT, SoundSource.MASTER, 0.9f, 0.7f);
    }
}
