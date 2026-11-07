package com.rpgtemplate.forge.fx;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;

/**
 * 服务端非粒子特效入口。不使用 ParticleTypes / sendParticles，只发送几何参数，
 * 客户端用 AFTER_PARTICLES 阶段绘制（见 com.rpgtemplate.forge.fx.client.FxRenderer）。
 *
 * <ul>
 *   <li>SPARK：朝向镜头的小亮点，带少量漂移与重力；</li>
 *   <li>PUFF：朝向镜头的柔软烟团，缓慢上飘并扩大；</li>
 *   <li>RING：水平扩张的圆环；</li>
 *   <li>SPHERE：三个正交圆环组成的扩张球壳。</li>
 * </ul>
 */
public final class Fx {
    public static final int SPARK = 0;
    public static final int PUFF = 1;
    public static final int RING = 2;
    public static final int SPHERE = 3;

    private static final int MAX_POINTS = 96;
    private static final double RANGE = 96.0;

    private Fx() {
    }

    /** 一组点的构造器。 */
    public static final class Pts {
        private double[] a = new double[24];
        private int n;

        public Pts add(double x, double y, double z) {
            if (n + 3 > a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[n++] = x;
            a[n++] = y;
            a[n++] = z;
            return this;
        }

        double[] array() {
            return Arrays.copyOf(a, n);
        }
    }

    /** 以 (x,y,z) 为中心发送一组点；超过 {@link #MAX_POINTS} 时自动拆包。 */
    public static void send(ServerLevel level, double x, double y, double z,
                            int shape, int rgb, float size, int life, float spread, Pts pts) {
        double[] all = pts.array();
        int total = all.length / 3;
        for (int from = 0; from < total; from += MAX_POINTS) {
            int to = Math.min(total, from + MAX_POINTS);
            double[] chunk = Arrays.copyOfRange(all, from * 3, to * 3);
            int seed = ThreadLocalRandom.current().nextInt();
            FxMsg msg = new FxMsg(shape, rgb, size, life, spread, seed, chunk);
            FxNet.CHANNEL.send(PacketDistributor.NEAR.with(
                    () -> new PacketDistributor.TargetPoint(x, y, z, RANGE, level.m_46472_())), msg);
        }
    }

    /** 在 (x,y,z) 附近 ±(jx,jy,jz) 的box内随机生成 count 个点并发送。 */
    public static void burst(ServerLevel level, int shape, int rgb, float size, int life, float spread,
                             double x, double y, double z, int count, double jx, double jy, double jz) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Pts pts = new Pts();
        for (int i = 0; i < count; i++) {
            pts.add(x + (rnd.nextDouble() * 2.0 - 1.0) * jx,
                    y + (rnd.nextDouble() * 2.0 - 1.0) * jy,
                    z + (rnd.nextDouble() * 2.0 - 1.0) * jz);
        }
        send(level, x, y, z, shape, rgb, size, life, spread, pts);
    }
}
