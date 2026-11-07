package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.NtBody;
import com.rpgtemplate.nothingthere.NtMimicBlade;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.world.InteractionHand;
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
 * 拟态的血雾特效，不用粒子：客户端直接画半透明四边形（做法同 NtGoreFx）。
 * <ul>
 *   <li>血雾气团：持剑且有血雾层数时，身边画 N 层软烟团（N = 层数，满 5 层颜色更亮）。
 *       自己在第一人称时不画，免得挡住视野。</li>
 *   <li>攻击弧光：每次左键攻击在身前画一道血色弧光（左轻击横扫、右重击竖劈、突刺重击直刺）。</li>
 *   <li>流血闪光：施加流血时，攻击者胸口扩散一圈血色光环。</li>
 * </ul>
 * 状态来自同步数据：攻击段 DATA_ATTACK_ANIM、血雾层数 DATA_MIST_LAYERS、流血计数 DATA_BLEED_CNT。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NtMistFx {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int LIGHT = 0xF000F0;
    /** 只处理这个范围内的玩家（格）。 */
    private static final double SCAN = 96.0;
    /** 攻击弧光寿命、流血闪光寿命（tick）。 */
    private static final double ARC_LIFE = 8.0;
    private static final double BLEED_LIFE = 6.0;
    /** 与 NtMimicCombat.MIST_MAX 相同（客户端包不能直接访问该常量）。 */
    private static final int FULL_LAYERS = 5;
    private static final double BODY_HEIGHT = 1.9;
    private static final double CHEST = 1.1;
    private static final double TAU = Math.PI * 2.0;
    private static final double GOLDEN_ANGLE = 2.399963;
    private static final int PUFFS_PER_LAYER = 6;
    private static final int PUFF_SEGMENTS = 8;

    /** 每个玩家的最近状态（只在客户端 tick 里更新）。 */
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private NtMistFx() {
    }

    private static final class Track {
        boolean init;
        int attack;
        int attackStep = -1;
        double attackAt = -1e9;
        int bleed;
        double bleedAt = -1e9;
    }

    // ---------------------------------------------------------------- 状态（客户端 tick）

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer local = mc.f_91074_;
        ClientLevel level = mc.f_91073_;
        if (local == null || level == null) {
            return;
        }
        double now = level.m_46467_();
        Set<UUID> seen = new HashSet<>();
        for (Player p : players(level, local)) {
            UUID id = p.m_20148_();
            seen.add(id);
            Track t = TRACKS.computeIfAbsent(id, k -> new Track());
            int code = NtBody.attackAnimValue(p);
            int bleed = NtBody.bleedCount(p);
            if (!t.init) {
                // 第一次看到这个玩家：只记下当前计数，不播放旧的攻击或流血
                t.init = true;
                t.attack = code;
                t.bleed = bleed;
                continue;
            }
            if (code != t.attack) {
                t.attack = code;
                t.attackStep = (code & 3) - 1;
                t.attackAt = now;
            }
            if (bleed != t.bleed) {
                t.bleed = bleed;
                t.bleedAt = now;
            }
        }
        // 范围外或已离开的玩家：丢弃状态，再次进入范围时重新开始
        TRACKS.keySet().retainAll(seen);
    }

    // ---------------------------------------------------------------- 绘制

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRACKS.isEmpty()) {
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
        double now = level.m_46467_() + event.getPartialTick();

        MultiBufferSource.BufferSource src = mc.m_91269_().m_110104_();
        RenderType type = RenderType.m_110473_(WHITE);
        VertexConsumer vc = src.m_6299_(type);
        PoseStack pose = event.getPoseStack();
        Painter g = new Painter(vc, pose.m_85850_().m_252922_(), pose.m_85850_().m_252943_(), cam);

        for (Player p : players(level, local)) {
            Track t = TRACKS.get(p.m_20148_());
            if (t == null || !t.init) {
                continue;
            }
            boolean self = p == local && firstPerson;
            if (!self && NtMimicBlade.isBlade(p.m_21120_(InteractionHand.MAIN_HAND))) {
                int layers = NtBody.mistLayers(p);
                if (layers > 0) {
                    drawAura(g, p, layers, now);
                }
            }
            if (!self && t.attackStep >= 0 && now - t.attackAt < ARC_LIFE) {
                drawArc(g, p, t.attackStep, now - t.attackAt);
            }
            if (!self && now - t.bleedAt < BLEED_LIFE) {
                drawBleed(g, p, now - t.bleedAt);
            }
        }
        src.m_109912_(type);
    }

    private static List<Player> players(ClientLevel level, LocalPlayer local) {
        return level.m_6443_(Player.class, local.m_20191_().m_82400_(SCAN), p -> p.m_6084_());
    }

    /**
     * 血雾气团：每层若干朝向镜头的软烟团，绕身体缓慢翻涌、向上飘散并逐渐消失。
     * 没有圆柱壁面，也没有随角度摆动的半径，所以不会出现柱状或一抖一抖的边缘。
     */
    private static void drawAura(Painter g, Player p, int layers, double now) {
        double x = p.m_20185_();
        double y = p.m_20186_();
        double z = p.m_20189_();
        boolean full = layers >= FULL_LAYERS;
        float cr = full ? 0.85F : 0.50F;
        float cg = full ? 0.08F : 0.04F;
        float cb = full ? 0.10F : 0.06F;
        float peak = full ? 0.30F : 0.20F;
        for (int i = 0; i < layers; i++) {
            for (int k = 0; k < PUFFS_PER_LAYER; k++) {
                double ph = k * GOLDEN_ANGLE + i * 1.3;
                double rot = now * 0.012 + ph;
                double rad = 0.40 + 0.10 * i + 0.10 * Math.sin(now * 0.05 + ph);
                double life = frac(now * 0.012 + k * 0.173 + i * 0.311);
                double py = y + 0.2 + life * (BODY_HEIGHT - 0.4);
                double size = 0.45 + 0.35 * life;
                float a = (float) (peak * Math.sin(Math.PI * life));
                puff(g, x + Math.cos(rot) * rad, py, z + Math.sin(rot) * rad, size, cr, cg, cb, a);
            }
        }
    }

    /** 朝向镜头的软烟团：中心不透明、边缘完全透明的圆盘（与 FxRenderer 的 PUFF 一致）。 */
    private static void puff(Painter g, double cx, double cy, double cz, double rad,
                             float r, float gg, float b, float a) {
        if (a <= 0.003F || rad <= 0.001) {
            return;
        }
        double tx = g.cam.f_82479_ - cx;
        double ty = g.cam.f_82480_ - cy;
        double tz = g.cam.f_82481_ - cz;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1e-6) {
            return;
        }
        tx /= len;
        ty /= len;
        tz /= len;
        // right = (0,1,0) × toCam，up = toCam × right
        double rx = tz;
        double ry = 0.0;
        double rz = -tx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) {
            rx = 1.0;
            rz = 0.0;
        } else {
            rx /= rl;
            rz /= rl;
        }
        double ux = ty * rz - tz * ry;
        double uy = tz * rx - tx * rz;
        double uz = tx * ry - ty * rx;
        P c = new P(cx, cy, cz, a);
        P[] rim = new P[PUFF_SEGMENTS];
        for (int j = 0; j < PUFF_SEGMENTS; j++) {
            double phi = TAU * j / PUFF_SEGMENTS;
            double co = Math.cos(phi) * rad;
            double si = Math.sin(phi) * rad;
            rim[j] = new P(cx + rx * co + ux * si, cy + ry * co + uy * si, cz + rz * co + uz * si, 0.0F);
        }
        for (int j = 0; j < PUFF_SEGMENTS; j++) {
            P q = rim[(j + 1) % PUFF_SEGMENTS];
            g.quad(r, gg, b, c, rim[j], q, q);
        }
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /**
     * 攻击弧光（step: 0 左轻击横扫、1 右重击竖劈、2 突刺重击直刺）。
     * 弧光随 age 逐渐展开，并在 ARC_LIFE 内淡出。
     */
    private static void drawArc(Painter g, Player p, int step, double age) {
        float a = (float) (0.85 * (1.0 - age / ARC_LIFE));
        if (a <= 0.01F) {
            return;
        }
        Vec3 look = p.m_20154_();
        double fx = look.f_82479_;
        double fz = look.f_82481_;
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 1e-6) {
            fx = 0.0;
            fz = 1.0;
        } else {
            fx /= len;
            fz /= len;
        }
        double sx = -fz;
        double sz = fx;
        double x = p.m_20185_();
        double z = p.m_20189_();
        double cy = p.m_20186_() + CHEST;
        double prog = Math.min(1.0, age / 3.0);
        int n = 10;
        if (step == 0) {
            // 左轻击：水平横扫，从身体右侧扫到左侧
            for (int k = 0; k < n; k++) {
                double t0 = 1.25 - 2.5 * prog * k / n;
                double t1 = 1.25 - 2.5 * prog * (k + 1) / n;
                float ak = a * (k + 1F) / n;
                g.quad(1.0F, 0.14F, 0.16F,
                    sweep(x, cy, z, fx, fz, sx, sz, t0, 0.9, ak), sweep(x, cy, z, fx, fz, sx, sz, t0, 1.9, ak),
                    sweep(x, cy, z, fx, fz, sx, sz, t1, 1.9, ak), sweep(x, cy, z, fx, fz, sx, sz, t1, 0.9, ak));
            }
        } else if (step == 1) {
            // 右重击：竖劈，从头顶劈到身前下方
            for (int k = 0; k < n; k++) {
                double t0 = 1.3 - 2.4 * prog * k / n;
                double t1 = 1.3 - 2.4 * prog * (k + 1) / n;
                float ak = a * (k + 1F) / n;
                g.quad(1.0F, 0.10F, 0.12F,
                    lift(x, cy, z, fx, fz, t0, 1.0, ak), lift(x, cy, z, fx, fz, t0, 2.0, ak),
                    lift(x, cy, z, fx, fz, t1, 2.0, ak), lift(x, cy, z, fx, fz, t1, 1.0, ak));
            }
        } else {
            // 突刺重击：从胸前向前伸出的楔形，外层暗红、内芯亮
            double tip = 0.5 + 2.2 * prog;
            double bx = x + fx * 0.3;
            double bz = z + fz * 0.3;
            g.quad(1.0F, 0.14F, 0.16F,
                new P(bx + sx * 0.22, cy, bz + sz * 0.22, a), new P(bx - sx * 0.22, cy, bz - sz * 0.22, a),
                new P(x + fx * tip, cy, z + fz * tip, a), new P(x + fx * tip, cy, z + fz * tip, a));
            double ct = 0.5 + 2.2 * prog * 0.92;
            g.quad(1.0F, 0.45F, 0.40F,
                new P(bx + sx * 0.08, cy, bz + sz * 0.08, a), new P(bx - sx * 0.08, cy, bz - sz * 0.08, a),
                new P(x + fx * ct, cy, z + fz * ct, a), new P(x + fx * ct, cy, z + fz * ct, a));
        }
    }

    /** 流血闪光：攻击者胸口扩散的一圈血色光环。 */
    private static void drawBleed(Painter g, Player p, double age) {
        float a = (float) (0.7 * (1.0 - age / BLEED_LIFE));
        if (a <= 0.01F) {
            return;
        }
        double x = p.m_20185_();
        double y = p.m_20186_() + CHEST;
        double z = p.m_20189_();
        double r = 0.35 + 0.18 * age;
        int n = 16;
        for (int k = 0; k < n; k++) {
            double a0 = TAU * k / n;
            double a1 = TAU * (k + 1) / n;
            g.quad(0.85F, 0.05F, 0.08F,
                ring(x, y, z, a0, r, a), ring(x, y, z, a1, r, a),
                ring(x, y, z, a1, r + 0.08, a), ring(x, y, z, a0, r + 0.08, a));
        }
    }

    /** 水平面内的点：角度 0 为前方，正角度转向右侧。 */
    private static P sweep(double x, double y, double z, double fx, double fz, double sx, double sz,
                           double angle, double radius, float a) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new P(x + (fx * c + sx * s) * radius, y, z + (fz * c + sz * s) * radius, a);
    }

    /** 竖直面内的点：角度 0 为前方水平，正角度朝上。 */
    private static P lift(double x, double y, double z, double fx, double fz,
                          double angle, double radius, float a) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new P(x + fx * c * radius, y + s * radius, z + fz * c * radius, a);
    }

    /** 以 (x, z) 为中心的水平圆上的点。 */
    private static P ring(double x, double y, double z, double angle, double radius, float a) {
        return new P(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, a);
    }

    // ---------------------------------------------------------------- 绘制工具（同 NtGoreFx）

    /** 一个世界坐标顶点（颜色由 quad 统一给出，a 为透明度）。 */
    private record P(double x, double y, double z, float a) {
    }

    /** 世界坐标转为相机相对坐标后写入缓冲；所有图元按四边形输出，并双面绘制。 */
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

        void put(P p, float r, float g, float b) {
            m.transformPosition((float) (p.x() - cam.f_82479_), (float) (p.y() - cam.f_82480_),
                (float) (p.z() - cam.f_82481_), tp);
            n.transform(0.0F, 1.0F, 0.0F, tn);
            vc.m_5954_(tp.x, tp.y, tp.z, r, g, b, p.a(), 0.0F, 0.0F,
                OverlayTexture.f_118083_, LIGHT, tn.x, tn.y, tn.z);
        }

        void quad(float r, float g, float b, P p0, P p1, P p2, P p3) {
            put(p0, r, g, b);
            put(p1, r, g, b);
            put(p2, r, g, b);
            put(p3, r, g, b);
            put(p0, r, g, b);
            put(p3, r, g, b);
            put(p2, r, g, b);
            put(p1, r, g, b);
        }
    }
}
