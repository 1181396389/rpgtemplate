package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.GoreMsg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 客户端血效果（重做版）。没有粒子、没有贴图，全部几何绘制，AFTER_PARTICLES 阶段渲染。
 * 所有元素都挂在一个「锚点」（生物）上，每帧按锚点的插值位置绘制，所以会跟随生物移动：
 * - Wound：贴在生物身体表面的血迹贴片，持续约 5 秒后淡出；
 * - Drip：沿身体表面向下流的血滴，落到脚下后变成 Pool；
 * - Pool：生物脚下的血池；
 * - Puff：施法者身边的血雾，围绕玩家身体，朝向镜头。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NtGoreFx {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int LIGHT = 0xF000F0;
    private static final int MAX_FX = 700;
    private static final int PATCH_RIM = 10;
    private static final int PUFF_RIM = 14;
    /** 伤口与血池淡出时长（tick）。 */
    private static final int FADE = 60;

    private static final List<Fx> FX = new ArrayList<>();
    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();

    private NtGoreFx() {
    }

    // ---------------------------------------------------------------- 接收消息

    public static void accept(GoreMsg m) {
        ClientLevel level = Minecraft.m_91087_().f_91073_;
        if (level == null) {
            return;
        }
        Anchor anchor = ANCHORS.computeIfAbsent(m.entityId, Anchor::new);
        if (!anchor.seen) {
            anchor.update(level);
        }
        Random rnd = new Random(m.seed);
        switch (m.kind) {
            case GoreMsg.WOUND -> add(new Wound(anchor, Math.max(20, m.count), m.radius, rnd));
            case GoreMsg.CAST_MIST -> {
                for (int i = 0; i < m.count; i++) {
                    add(new Puff(anchor, Math.max(0.6, m.radius), rnd));
                }
            }
            default -> {
            }
        }
    }

    private static void add(Fx fx) {
        FX.add(fx);
        while (FX.size() > MAX_FX) {
            FX.remove(0);
        }
    }

    // ---------------------------------------------------------------- 事件

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        ClientLevel level = mc.f_91073_;
        if (level == null) {
            FX.clear();
            ANCHORS.clear();
            return;
        }
        if (FX.isEmpty()) {
            ANCHORS.clear();
            return;
        }
        for (Anchor a : ANCHORS.values()) {
            a.update(level);
        }
        for (Fx fx : new ArrayList<>(FX)) {
            fx.tick();
        }
        FX.removeIf(fx -> fx.dead);
        ANCHORS.values().removeIf(a -> FX.stream().noneMatch(fx -> fx.anchor == a));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) {
            return;
        }
        Vec3 cam = event.getCamera().m_90583_();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource src = mc.m_91269_().m_110104_();
        RenderType type = RenderType.m_110473_(WHITE);
        VertexConsumer vc = src.m_6299_(type);
        Painter painter = new Painter(vc, pose.m_85850_().m_252922_(), pose.m_85850_().m_252943_(), cam);
        for (Fx fx : FX) {
            fx.draw(painter, partial);
        }
        src.m_109912_(type);
    }

    // ---------------------------------------------------------------- 锚点

    /** 生物锚点：记录上一帧与当前帧的位置，绘制时按 partialTick 插值。生物消失后保留最后位置。 */
    private static final class Anchor {
        final int id;
        double x;
        double y;
        double z;
        double px;
        double py;
        double pz;
        double width = 0.6;
        double height = 1.8;
        boolean seen;

        Anchor(int id) {
            this.id = id;
        }

        void update(ClientLevel level) {
            Entity e = level.m_6815_(id);
            if (e == null) {
                return;
            }
            double nx = e.m_20185_();
            double ny = e.m_20186_();
            double nz = e.m_20189_();
            if (!seen) {
                px = nx;
                py = ny;
                pz = nz;
                seen = true;
            } else {
                px = x;
                py = y;
                pz = z;
            }
            x = nx;
            y = ny;
            z = nz;
            width = Math.max(0.3, e.m_20205_());
            height = Math.max(0.5, e.m_20206_());
        }

        double lx(float p) {
            return px + (x - px) * p;
        }

        double ly(float p) {
            return py + (y - py) * p;
        }

        double lz(float p) {
            return pz + (z - pz) * p;
        }

        /** 身体半径（用于把血迹贴在身体表面）。 */
        double radius() {
            return width * 0.5 + 0.02;
        }
    }

    // ---------------------------------------------------------------- 绘制工具

    /** 一个带颜色、法线的顶点（世界坐标）。 */
    private record V(double x, double y, double z, float r, float g, float b, float a,
                     double nx, double ny, double nz) {
    }

    /** 把世界坐标顶点转为相机相对坐标后写入缓冲。所有图元以四边形输出，并双面绘制。 */
    private static final class Painter {
        final VertexConsumer vc;
        final Matrix4f m;
        final Matrix3f n;
        final Vec3 cam;
        final Vector3f tp = new Vector3f();
        final Vector3f tn = new Vector3f();

        Painter(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam) {
            this.vc = vc;
            this.m = m;
            this.n = n;
            this.cam = cam;
        }

        void put(V v) {
            m.transformPosition((float) (v.x - cam.f_82479_), (float) (v.y - cam.f_82480_),
                    (float) (v.z - cam.f_82481_), tp);
            n.transform((float) v.nx, (float) v.ny, (float) v.nz, tn);
            vc.m_5954_(tp.x, tp.y, tp.z, v.r, v.g, v.b, v.a, 0.0F, 0.0F,
                    OverlayTexture.f_118083_, LIGHT, tn.x, tn.y, tn.z);
        }

        void quad(V a, V b, V c, V d) {
            put(a);
            put(b);
            put(c);
            put(d);
            put(a);
            put(d);
            put(c);
            put(b);
        }

        void tri(V a, V b, V c) {
            quad(a, b, c, c);
        }
    }

    private static double[] norm(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-9) {
            return new double[]{0, 1, 0};
        }
        return new double[]{x / len, y / len, z / len};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // ---------------------------------------------------------------- 元素

    private abstract static class Fx {
        final Anchor anchor;
        final int life;
        int age;
        boolean dead;

        Fx(Anchor anchor, int life) {
            this.anchor = anchor;
            this.life = life;
        }

        void tick() {
            age++;
            if (age >= life) {
                dead = true;
            }
        }

        abstract void draw(Painter p, float partial);
    }

    /** 伤口：贴在身体表面的若干血迹贴片，并持续生成血滴。 */
    private static final class Wound extends Fx {
        final int duration;
        final Random rnd;
        final int spots;
        final double[] ang;
        final double[] h;
        final double[] size;
        final float[] tone;
        final float[][] rim;

        Wound(Anchor anchor, int duration, float intensity, Random rnd) {
            super(anchor, duration + FADE);
            this.duration = duration;
            this.rnd = rnd;
            float k = Math.max(0.5f, intensity);
            this.spots = 4 + Math.round(k * 2f) + rnd.nextInt(3);
            ang = new double[spots];
            h = new double[spots];
            size = new double[spots];
            tone = new float[spots];
            rim = new float[spots][PATCH_RIM];
            double scale = Math.max(0.7, Math.min(1.5, anchor.width / 0.6));
            for (int i = 0; i < spots; i++) {
                ang[i] = rnd.nextDouble() * Math.PI * 2.0;
                h[i] = anchor.height * (0.3 + 0.55 * rnd.nextDouble());
                size[i] = (0.07 + 0.09 * rnd.nextDouble()) * k * scale;
                tone[i] = 0.85f + 0.3f * rnd.nextFloat();
                for (int j = 0; j < PATCH_RIM; j++) {
                    rim[i][j] = 0.65f + 0.35f * rnd.nextFloat();
                }
            }
            // 受击瞬间的喷溅：先出三滴快的
            for (int k2 = 0; k2 < 3; k2++) {
                spawnDrip(true);
            }
        }

        private void spawnDrip(boolean fast) {
            int s = rnd.nextInt(spots);
            double speed = fast ? 0.10 + 0.05 * rnd.nextDouble() : 0.05 + 0.03 * rnd.nextDouble();
            FX.add(new Drip(anchor, ang[s], h[s], speed, rnd));
        }

        @Override
        void tick() {
            age++;
            if (age >= life) {
                dead = true;
                return;
            }
            if (age < duration && age % 4 == 0) {
                spawnDrip(false);
            }
        }

        @Override
        void draw(Painter p, float partial) {
            if (!anchor.seen) {
                return;
            }
            double lx = anchor.lx(partial);
            double ly = anchor.ly(partial);
            double lz = anchor.lz(partial);
            double radius = anchor.radius();
            float fadeAll = clamp01((life - age - partial) / FADE);
            float grow = clamp01((age + partial) / 8f);
            for (int s = 0; s < spots; s++) {
                double ca = Math.cos(ang[s]);
                double sa = Math.sin(ang[s]);
                // 贴片平面：法线 n = (ca,0,sa)，切向 t = (-sa,0,ca)，竖直 up = (0,1,0)
                double ox = ca * (radius + 0.005);
                double oz = sa * (radius + 0.005);
                double cx = lx + ox;
                double cy = ly + h[s];
                double cz = lz + oz;
                double rr = size[s] * (0.35 + 0.65 * grow);
                V c = new V(cx, cy, cz, 0.45f * tone[s], 0.03f, 0.05f, 0.92f * fadeAll, ca, 0, sa);
                V[] ring = new V[PATCH_RIM];
                for (int j = 0; j < PATCH_RIM; j++) {
                    double phi = Math.PI * 2.0 * j / PATCH_RIM;
                    double u = Math.cos(phi) * rr * rim[s][j];
                    double v = Math.sin(phi) * rr * rim[s][j];
                    ring[j] = new V(cx - sa * u, cy + v, cz + ca * u,
                            0.24f * tone[s], 0.01f, 0.03f, 0.85f * fadeAll, ca, 0, sa);
                }
                for (int j = 0; j < PATCH_RIM; j++) {
                    p.tri(c, ring[j], ring[(j + 1) % PATCH_RIM]);
                }
            }
        }
    }

    /** 血滴：沿身体表面向下流，长度与速度随机，落到脚下后生成血池。 */
    private static final class Drip extends Fx {
        final double ang;
        final double h0;
        final double speed;
        final double len;
        final double width;
        final float tone;
        final Random rnd;

        Drip(Anchor anchor, double ang, double h0, double speed, Random rnd) {
            super(anchor, 400);
            this.ang = ang;
            this.h0 = h0;
            this.speed = speed;
            this.len = 0.18 + 0.2 * rnd.nextDouble();
            this.width = 0.025 + 0.025 * rnd.nextDouble();
            this.tone = 0.9f + 0.2f * rnd.nextFloat();
            this.rnd = rnd;
        }

        @Override
        void tick() {
            age++;
            double bottom = h0 - speed * age;
            if (bottom <= 0.02) {
                FX.add(new Pool(anchor, ang, anchor.radius() * (0.5 + 0.6 * rnd.nextDouble()), rnd));
                dead = true;
            }
            if (age >= life) {
                dead = true;
            }
        }

        @Override
        void draw(Painter p, float partial) {
            if (!anchor.seen) {
                return;
            }
            double bottom = Math.max(0.02, h0 - speed * (age + partial));
            double top = Math.min(h0, bottom + len);
            if (top - bottom < 0.01) {
                return;
            }
            double lx = anchor.lx(partial);
            double ly = anchor.ly(partial);
            double lz = anchor.lz(partial);
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            double R = anchor.radius() + 0.005;
            double ox = lx + ca * R;
            double oz = lz + sa * R;
            double tx = -sa * width;
            double tz = ca * width;
            V hb1 = new V(ox + tx, ly + bottom, oz + tz, 0.5f * tone, 0.03f, 0.06f, 0.95f, ca, 0, sa);
            V hb2 = new V(ox - tx, ly + bottom, oz - tz, 0.5f * tone, 0.03f, 0.06f, 0.95f, ca, 0, sa);
            V tt2 = new V(ox - tx * 0.6, ly + top, oz - tz * 0.6, 0.35f, 0.02f, 0.04f, 0.35f, ca, 0, sa);
            V tt1 = new V(ox + tx * 0.6, ly + top, oz + tz * 0.6, 0.35f, 0.02f, 0.04f, 0.35f, ca, 0, sa);
            p.quad(hb1, hb2, tt2, tt1);
        }
    }

    /** 血池：生物脚下的血迹，淡出前保持约 4 秒。 */
    private static final class Pool extends Fx {
        final double ang;
        final double dist;
        final double size;
        final float tone;
        final float[] rim = new float[PATCH_RIM];

        Pool(Anchor anchor, double ang, double dist, Random rnd) {
            super(anchor, 240 + rnd.nextInt(60));
            this.ang = ang;
            this.dist = dist;
            this.size = 0.14 + 0.16 * rnd.nextDouble();
            this.tone = 0.85f + 0.3f * rnd.nextFloat();
            for (int j = 0; j < PATCH_RIM; j++) {
                rim[j] = 0.65f + 0.35f * rnd.nextFloat();
            }
        }

        @Override
        void draw(Painter p, float partial) {
            if (!anchor.seen) {
                return;
            }
            double cx = anchor.lx(partial) + Math.cos(ang) * dist;
            double cz = anchor.lz(partial) + Math.sin(ang) * dist;
            double cy = anchor.ly(partial) + 0.02;
            float grow = clamp01((age + partial) / 10f);
            float fade = clamp01((life - age - partial) / 50f);
            double r = size * (0.3 + 0.7 * grow);
            V c = new V(cx, cy, cz, 0.4f * tone, 0.02f, 0.04f, 0.9f * fade, 0, 1, 0);
            V[] ring = new V[PATCH_RIM];
            for (int j = 0; j < PATCH_RIM; j++) {
                double phi = Math.PI * 2.0 * j / PATCH_RIM;
                double rr = r * rim[j];
                ring[j] = new V(cx + Math.cos(phi) * rr, cy, cz + Math.sin(phi) * rr,
                        0.22f * tone, 0.01f, 0.03f, 0.85f * fade, 0, 1, 0);
            }
            for (int j = 0; j < PATCH_RIM; j++) {
                p.tri(c, ring[j], ring[(j + 1) % PATCH_RIM]);
            }
        }
    }

    /** 血雾：围绕施法者身体的云团，朝向镜头，跟随施法者移动，寿命约 2–3 秒。 */
    private static final class Puff extends Fx {
        final double ang;
        final double dist;
        final double hgt;
        final double radius;
        final float base;
        final float[] rim = new float[PUFF_RIM];

        Puff(Anchor anchor, double reach, Random rnd) {
            super(anchor, 40 + rnd.nextInt(18));
            this.ang = rnd.nextDouble() * Math.PI * 2.0;
            this.dist = reach * (0.55 + 0.5 * rnd.nextDouble());
            this.hgt = 0.2 + 1.5 * rnd.nextDouble();
            this.radius = 0.45 + 0.4 * rnd.nextDouble();
            this.base = 0.7f + 0.3f * rnd.nextFloat();
            for (int j = 0; j < PUFF_RIM; j++) {
                rim[j] = 0.7f + 0.3f * rnd.nextFloat();
            }
        }

        @Override
        void draw(Painter p, float partial) {
            if (!anchor.seen) {
                return;
            }
            float t = clamp01((age + partial) / life);
            double spread = 1.0 + 0.25 * t;
            double cx = anchor.lx(partial) + Math.cos(ang) * dist * spread;
            double cy = anchor.ly(partial) + hgt + 0.15 * t;
            double cz = anchor.lz(partial) + Math.sin(ang) * dist * spread;
            float r = (float) radius * (0.6f + 0.8f * t);
            float a = 0.5f * base * clamp01(t * 5f) * (1f - t);

            double[] toCam = norm(p.cam.f_82479_ - cx, p.cam.f_82480_ - cy, p.cam.f_82481_ - cz);
            double[] right = cross(new double[]{0, 1, 0}, toCam);
            if (Math.abs(right[0]) + Math.abs(right[1]) + Math.abs(right[2]) < 1e-6) {
                right = new double[]{1, 0, 0};
            }
            right = norm(right[0], right[1], right[2]);
            double[] up = cross(toCam, right);

            V c = new V(cx, cy, cz, 0.6f, 0.08f, 0.11f, a, toCam[0], toCam[1], toCam[2]);
            V[] mid = new V[PUFF_RIM];
            V[] edge = new V[PUFF_RIM];
            for (int j = 0; j < PUFF_RIM; j++) {
                double phi = Math.PI * 2.0 * j / PUFF_RIM;
                double cos = Math.cos(phi);
                double sin = Math.sin(phi);
                double rr1 = r * 0.55 * rim[j];
                double rr2 = r * rim[j];
                mid[j] = offset(cx, cy, cz, right, up, cos * rr1, sin * rr1,
                        0.5f * a, 0.5f, 0.05f, 0.08f, toCam);
                edge[j] = offset(cx, cy, cz, right, up, cos * rr2, sin * rr2,
                        0f, 0.35f, 0.03f, 0.06f, toCam);
            }
            for (int j = 0; j < PUFF_RIM; j++) {
                int k = (j + 1) % PUFF_RIM;
                p.tri(c, mid[j], mid[k]);
                p.quad(mid[j], edge[j], edge[k], mid[k]);
            }
        }

        private static V offset(double cx, double cy, double cz, double[] right, double[] up,
                                double ox, double oz, float alpha, float r, float g, float b, double[] n) {
            return new V(cx + right[0] * ox + up[0] * oz, cy + right[1] * ox + up[1] * oz,
                    cz + right[2] * ox + up[2] * oz, r, g, b, alpha, n[0], n[1], n[2]);
        }
    }
}
