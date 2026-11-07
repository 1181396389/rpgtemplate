package com.rpgtemplate.forge.fx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.forge.fx.Fx;
import com.rpgtemplate.forge.fx.FxMsg;
import com.rpgtemplate.forge.fx.FxStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
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
 * 非粒子特效的客户端绘制：没有粒子系统、没有贴图，全部是几何图元，AFTER_PARTICLES 阶段渲染。
 * 图元：SPARK 镜头朝向小亮片、PUFF 镜头朝向软烟团、RING 水平圆环、SPHERE 三正交圆环球壳。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FxRenderer {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int LIGHT = 0xF000F0;
    private static final int MAX = 1200;
    private static final int RIM = 10;

    private static final List<Mote> MOTES = new ArrayList<>();

    private FxRenderer() {
    }

    // ---------------------------------------------------------------- 接收消息

    public static void accept(FxMsg m) {
        ClientLevel level = Minecraft.m_91087_().f_91073_;
        if (level == null) {
            return;
        }
        float r = ((m.rgb >> 16) & 0xFF) / 255f;
        float g = ((m.rgb >> 8) & 0xFF) / 255f;
        float b = (m.rgb & 0xFF) / 255f;
        for (int i = 0; i < m.count(); i++) {
            Random rnd = new Random(m.seed * 31L + i);
            MOTES.add(new Mote(m.shape, m.pts[i * 3], m.pts[i * 3 + 1], m.pts[i * 3 + 2],
                    rnd, r, g, b, m.size, Math.max(4, m.life), m.spread));
        }
        while (MOTES.size() > MAX) {
            MOTES.remove(0);
        }
    }

    /** 客户端本地生成一个图元（addParticle 拦截用，不经过网络）。 */
    public static void spawnLocal(FxStyle style, double x, double y, double z,
                                  double vx, double vy, double vz) {
        accept(new FxMsg(style.shape, style.rgb, style.size, style.life, style.spread,
                (int) (x * 31.0 + y * 17.0 + z * 7.0), new double[]{x, y, z}));
    }

    // ---------------------------------------------------------------- 事件

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.m_91087_().f_91073_ == null) {
            MOTES.clear();
            return;
        }
        if (MOTES.isEmpty()) {
            return;
        }
        for (Mote mote : MOTES) {
            mote.tick();
        }
        MOTES.removeIf(mote -> mote.dead);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MOTES.isEmpty()) {
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
        for (Mote mote : MOTES) {
            mote.draw(painter, partial);
        }
        src.m_109912_(type);
    }

    // ---------------------------------------------------------------- 图元

    /** 一个几何图元。位置随速度漂移，寿命结束后移除。 */
    private static final class Mote {
        final int shape;
        final float r;
        final float g;
        final float b;
        final float size;
        final float spread;
        final int life;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        int age;
        boolean dead;

        Mote(int shape, double x, double y, double z, Random rnd, float r, float g, float b,
             float size, int life, float spread) {
            this.shape = shape;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.g = g;
            this.b = b;
            this.size = Math.max(0.02f, size);
            this.life = life;
            this.spread = Math.max(0.05f, spread);
            // 单位球面上的随机方向
            double u = rnd.nextDouble() * 2.0 - 1.0;
            double th = rnd.nextDouble() * Math.PI * 2.0;
            double s = Math.sqrt(1.0 - u * u);
            double dx = s * Math.cos(th);
            double dy = u;
            double dz = s * Math.sin(th);
            if (shape == Fx.SPARK) {
                double sp = this.spread * (0.03 + 0.05 * rnd.nextDouble());
                vx = dx * sp;
                vy = dy * sp;
                vz = dz * sp;
            } else if (shape == Fx.PUFF) {
                double sp = this.spread * 0.012;
                vx = dx * sp;
                vy = 0.01 + 0.01 * rnd.nextDouble();
                vz = dz * sp;
            }
        }

        void tick() {
            age++;
            x += vx;
            y += vy;
            z += vz;
            if (shape == Fx.SPARK) {
                vy -= 0.004;
                vx *= 0.88;
                vy *= 0.88;
                vz *= 0.88;
            } else if (shape == Fx.PUFF) {
                vx *= 0.96;
                vz *= 0.96;
                vy *= 0.97;
            }
            if (age >= life) {
                dead = true;
            }
        }

        void draw(Painter p, float partial) {
            float t = clamp01((age + partial) / life);
            float fade = 1f - t;
            double cx = x + vx * partial;
            double cy = y + vy * partial;
            double cz = z + vz * partial;
            switch (shape) {
                case Fx.SPARK -> billboard(p, cx, cy, cz, size * (1f - 0.5f * t), r, g, b, 0.95f * fade);
                case Fx.PUFF -> billboard(p, cx, cy, cz, size * (0.6f + 0.9f * t), r, g, b,
                        0.42f * fade * clamp01(t * 6f));
                case Fx.RING -> {
                    double radius = spread * (0.25 + 0.75 * t);
                    ring(p, cx, cy, cz, radius, Math.max(0.02, size * (1f - 0.5f * t)),
                            new double[]{1, 0, 0}, new double[]{0, 0, 1}, r, g, b, 0.85f * fade);
                }
                case Fx.SPHERE -> {
                    double radius = spread * (0.2 + 0.8 * t);
                    double w = Math.max(0.02, size * (1f - 0.5f * t));
                    float a = 0.6f * fade;
                    ring(p, cx, cy, cz, radius, w, new double[]{1, 0, 0}, new double[]{0, 0, 1}, r, g, b, a);
                    ring(p, cx, cy, cz, radius, w, new double[]{1, 0, 0}, new double[]{0, 1, 0}, r, g, b, a);
                    ring(p, cx, cy, cz, radius, w, new double[]{0, 0, 1}, new double[]{0, 1, 0}, r, g, b, a);
                }
                default -> {
                }
            }
        }
    }

    /** 朝向镜头的圆盘：中心不透明，边缘透明。 */
    private static void billboard(Painter p, double cx, double cy, double cz, double rad,
                                  float r, float g, float b, float a) {
        if (a <= 0.003f || rad <= 0.001) {
            return;
        }
        double[] toCam = norm(p.cam.f_82479_ - cx, p.cam.f_82480_ - cy, p.cam.f_82481_ - cz);
        double[] right = cross(new double[]{0, 1, 0}, toCam);
        if (Math.abs(right[0]) + Math.abs(right[1]) + Math.abs(right[2]) < 1e-6) {
            right = new double[]{1, 0, 0};
        }
        right = norm(right[0], right[1], right[2]);
        double[] up = cross(toCam, right);
        V c = new V(cx, cy, cz, r, g, b, a, toCam[0], toCam[1], toCam[2]);
        V[] rim = new V[RIM];
        for (int j = 0; j < RIM; j++) {
            double phi = Math.PI * 2.0 * j / RIM;
            double co = Math.cos(phi) * rad;
            double si = Math.sin(phi) * rad;
            rim[j] = new V(cx + right[0] * co + up[0] * si, cy + right[1] * co + up[1] * si,
                    cz + right[2] * co + up[2] * si, r, g, b, 0f, toCam[0], toCam[1], toCam[2]);
        }
        for (int j = 0; j < RIM; j++) {
            p.tri(c, rim[j], rim[(j + 1) % RIM]);
        }
    }

    /** 由两个正交单位向量 u、v 张成平面上的圆环（带宽 width）。 */
    private static void ring(Painter p, double cx, double cy, double cz, double radius, double width,
                             double[] u, double[] v, float r, float g, float b, float a) {
        if (a <= 0.003f || radius <= 0.001) {
            return;
        }
        double[] n = norm(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]);
        double inner = Math.max(0.0, radius - width * 0.5);
        double outer = radius + width * 0.5;
        V[] in = new V[RIM];
        V[] out = new V[RIM];
        for (int j = 0; j < RIM; j++) {
            double phi = Math.PI * 2.0 * j / RIM;
            double co = Math.cos(phi);
            double si = Math.sin(phi);
            double ix = u[0] * co + v[0] * si;
            double iy = u[1] * co + v[1] * si;
            double iz = u[2] * co + v[2] * si;
            in[j] = new V(cx + ix * inner, cy + iy * inner, cz + iz * inner, r, g, b, a, n[0], n[1], n[2]);
            out[j] = new V(cx + ix * outer, cy + iy * outer, cz + iz * outer, r, g, b, a, n[0], n[1], n[2]);
        }
        for (int j = 0; j < RIM; j++) {
            int k = (j + 1) % RIM;
            p.quad(in[j], out[j], out[k], in[k]);
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
}
