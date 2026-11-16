package com.rpgtemplate.forge.fx;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;

/**
 * 服务端非粒子特效入口。
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

    public static void send(ServerLevel level, double x, double y, double z,
                            int shape, int rgb, float size, int life, float spread, Pts pts) {
        double[] all = pts.array();
        int total = all.length / 3;
        for (int from = 0; from < total; from += MAX_POINTS) {
            int to = Math.min(total, from + MAX_POINTS);
            double[] chunk = Arrays.copyOfRange(all, from * 3, to * 3);
            int seed = ThreadLocalRandom.current().nextInt();
            FxMsg msg = new FxMsg(shape, rgb, size, life, spread, seed, chunk);
            FxNet.sendNear(level, x, y, z, RANGE, msg);
        }
    }

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
