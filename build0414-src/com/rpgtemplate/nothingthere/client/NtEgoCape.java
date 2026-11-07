package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.NtBody;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
 * E.G.O展现的血色披风：挂在背后，用简单的 Verlet 链条模拟重力摆动。
 * 上端固定在肩背（随身体朝向），每一段受重力下垂、受身体移动的惯性带动，并保持固定段长。
 * 本地玩家第一人称时不画（避免挡住视野）。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NtEgoCape {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int LIGHT = 0xF000F0;
    private static final double SCAN = 96.0;
    /** 披风分段数与每段长度（总长约 1.3 格，垂到脚踝附近）。 */
    private static final int ROWS = 6;
    private static final double ROW_LEN = 0.22;
    private static final double GRAVITY = 0.030;
    private static final double DAMP = 0.90;
    /** 肩背锚点：身体中心后方 0.18 格、高度 1.35 格。 */
    private static final double BACK = 0.18;
    private static final double SHOULDER = 1.35;
    /** 半宽：顶部 0.34，底部略宽。 */
    private static final double HALF_TOP = 0.34;
    private static final double HALF_STEP = 0.03;

    private static final Map<UUID, Cape> CAPES = new HashMap<>();

    private NtEgoCape() {
    }

    /** 一条披风的状态：pos/old 为各段的世界坐标；第 0 段是锚点。 */
    private static final class Cape {
        final double[][] pos = new double[ROWS + 1][3];
        final double[][] old = new double[ROWS + 1][3];
        boolean init;
        double[] lastAnchor = new double[3];
    }

    // ---------------------------------------------------------------- 模拟（客户端 tick）

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        ClientLevel level = mc.f_91073_;
        LocalPlayer local = mc.f_91074_;
        if (level == null || local == null) {
            CAPES.clear();
            return;
        }
        Set<UUID> active = new HashSet<>();
        for (Player p : level.m_6443_(Player.class, local.m_20191_().m_82400_(SCAN), Player::m_6084_)) {
            if (!NtBody.egoActive(p)) {
                continue;
            }
            UUID id = p.m_20148_();
            active.add(id);
            Cape c = CAPES.computeIfAbsent(id, k -> new Cape());
            step(c, p);
        }
        CAPES.keySet().retainAll(active);
    }

    private static void step(Cape c, Player p) {
        double[] anchor = anchor(p);
        if (!c.init || dist(anchor, c.lastAnchor) > 3.0) {
            // 第一次看到，或者被传送：从锚点垂下，立即就位
            for (int i = 0; i <= ROWS; i++) {
                for (int d = 0; d < 3; d++) {
                    c.pos[i][d] = anchor[d] - (d == 1 ? ROW_LEN * i : 0.0);
                    c.old[i][d] = c.pos[i][d];
                }
            }
            c.init = true;
        }
        c.lastAnchor = anchor.clone();
        // 锚点固定在身上
        for (int d = 0; d < 3; d++) {
            c.pos[0][d] = anchor[d];
            c.old[0][d] = anchor[d];
        }
        // 惯性 + 重力
        for (int i = 1; i <= ROWS; i++) {
            for (int d = 0; d < 3; d++) {
                double v = (c.pos[i][d] - c.old[i][d]) * DAMP;
                c.old[i][d] = c.pos[i][d];
                c.pos[i][d] += v;
            }
            c.pos[i][1] -= GRAVITY;
        }
        // 约束：每段保持固定长度，逐段从上往下投影
        for (int it = 0; it < 3; it++) {
            for (int i = 1; i <= ROWS; i++) {
                double dx = c.pos[i][0] - c.pos[i - 1][0];
                double dy = c.pos[i][1] - c.pos[i - 1][1];
                double dz = c.pos[i][2] - c.pos[i - 1][2];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-6) {
                    c.pos[i][1] = c.pos[i - 1][1] - ROW_LEN;
                    continue;
                }
                double k = ROW_LEN / len;
                c.pos[i][0] = c.pos[i - 1][0] + dx * k;
                c.pos[i][1] = c.pos[i - 1][1] + dy * k;
                c.pos[i][2] = c.pos[i - 1][2] + dz * k;
            }
        }
    }

    /** 肩背锚点：身体中心沿朝向的反方向偏移。 */
    private static double[] anchor(Player p) {
        double yaw = Math.toRadians(p.m_146908_());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        return new double[] {p.m_20185_() - fx * BACK, p.m_20186_() + SHOULDER, p.m_20189_() - fz * BACK};
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------------------------------------------------------------- 绘制（AFTER_PARTICLES）

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CAPES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = mc.f_91074_;
        ClientLevel level = mc.f_91073_;
        if (local == null || level == null) {
            return;
        }
        boolean firstPerson = mc.f_91066_.m_92176_().m_90612_();
        Vec3 cam = event.getCamera().m_90583_();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource src = mc.m_91269_().m_110104_();
        RenderType type = RenderType.m_110473_(WHITE);
        VertexConsumer vc = src.m_6299_(type);
        Painter g = new Painter(vc, pose.m_85850_().m_252922_(), pose.m_85850_().m_252943_(), cam);

        for (Player p : level.m_6443_(Player.class, local.m_20191_().m_82400_(SCAN), Player::m_6084_)) {
            Cape c = CAPES.get(p.m_20148_());
            if (c == null || !c.init) {
                continue;
            }
            if (p == local && firstPerson) {
                continue;
            }
            draw(g, p, c);
        }
        src.m_109912_(type);
    }

    private static void draw(Painter g, Player p, Cape c) {
        double yaw = Math.toRadians(p.m_146908_());
        // 水平右向量（与 fx 垂直）；披风的宽度方向
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double bx = Math.sin(yaw);   // 背面法线：朝向的反方向 (sin, -cos)
        double bz = -Math.cos(yaw);
        V[] left = new V[ROWS + 1];
        V[] right = new V[ROWS + 1];
        for (int i = 0; i <= ROWS; i++) {
            double half = HALF_TOP + HALF_STEP * i;
            double x = c.pos[i][0];
            double y = c.pos[i][1];
            double z = c.pos[i][2];
            // 顶部偏暗（贴身），底部偏亮
            float t = i / (float) ROWS;
            float r = 0.36F + 0.40F * t;
            float gg = 0.02F + 0.03F * t;
            float b = 0.04F + 0.04F * t;
            float a = 0.92F - 0.08F * t;
            left[i] = new V(x - rx * half, y, z - rz * half, r, gg, b, a, bx, 0.0, bz);
            right[i] = new V(x + rx * half, y, z + rz * half, r, gg, b, a, bx, 0.0, bz);
        }
        for (int i = 0; i < ROWS; i++) {
            g.quad(left[i], right[i], right[i + 1], left[i + 1]);
        }
    }

    /** 带颜色与法线的世界坐标顶点。 */
    private record V(double x, double y, double z, float r, float g, float b, float a,
                     double nx, double ny, double nz) {
    }

    /** 世界坐标转相机相对坐标后写入缓冲；四边形双面输出。 */
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
    }
}
