package com.rpgtemplate.nothingthere;

import com.rpgtemplate.forge.fx.Fx;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 形态 4（脊骨鞭）与形态 5（全收）的攻击效果。非粒子几何特效（经 Fx 通道发送到客户端绘制）和音效。
 *
 * 脊骨鞭：挥击弧线上留下骨色火花，并播放鞭响（NtSounds.HIT 提高音调，短而脆）。
 * 全收：每个命中目标处爆出暗红与血块色火花；施法者周围出现一圈血肉脉冲环，并播放沉闷的肉击声（降低音调）。
 */
public final class NtFx {
    private static final int BONE = 0xEADEBD;
    private static final int BONE_DARK = 0x94856B;
    private static final int FLESH = 0xB32429;
    private static final int CLOT = 0x5C080D;

    private NtFx() {
    }

    /** 脊骨鞭：沿挥击弧线（与 NtForms.sweep 同一弧度）铺出骨色火花，并播放鞭响。 */
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
        Fx.Pts bone = new Fx.Pts();
        Fx.Pts dark = new Fx.Pts();
        for (int s = 0; s <= steps; s++) {
            double a = base - half + (2.0 * half) * s / steps;
            Fx.Pts target = s % 3 == 0 ? bone : dark;
            for (double r = 1.2; r <= range; r += 0.9) {
                target.add(px + Math.cos(a) * r, py, pz + Math.sin(a) * r);
            }
        }
        Fx.send(level, px, py, pz, Fx.SPARK, BONE, 0.12f, 14, 0.0f, bone);
        Fx.send(level, px, py, pz, Fx.SPARK, BONE_DARK, 0.12f, 14, 0.0f, dark);
        level.m_6263_(null, px, py, pz, NtSounds.HIT, SoundSource.MASTER, 0.8f, 1.6f);
    }

    /**
     * 全收：每个命中目标处爆出血块与暗红火花；施法者周围出现血肉脉冲环。
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
            Fx.burst(level, Fx.SPARK, CLOT, 0.16f, 18, 0.5f, x, y, z, 14, 0.35, 0.45, 0.35);
            Fx.burst(level, Fx.SPARK, FLESH, 0.14f, 16, 0.5f, x, y, z, 10, 0.30, 0.30, 0.30);
        }
        double px = player.m_20185_();
        double py = player.m_20186_() + 0.9;
        double pz = player.m_20189_();
        Fx.send(level, px, py, pz, Fx.RING, FLESH, 0.16f, 14, 1.3f, new Fx.Pts().add(px, py, pz));
        level.m_6263_(null, px, py, pz, NtSounds.HIT, SoundSource.MASTER, 0.9f, 0.7f);
    }
}
