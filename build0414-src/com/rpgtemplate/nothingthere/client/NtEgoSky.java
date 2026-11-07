package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.NtBody;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * E.G.O展现的天空与视野：
 * <ul>
 *   <li>血夜：该维度内任一玩家处于 E.G.O 展现时，天空盖上一层血色穹顶（AFTER_SKY 阶段绘制）；夜晚更浓。</li>
 *   <li>雾色：远景雾色向暗红偏移，与天空一致。</li>
 *   <li>视野血红：只对本地的 E.G.O 玩家生效，在暗角层之上叠一层低透明度的血红。</li>
 * </ul>
 * 强度每 tick 平滑过渡，不会突变。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NtEgoSky {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int LIGHT = 0xF000F0;
    /** 穹顶半径（格）：需小于远裁剪面，否则会被裁掉。 */
    private static final double DOME = 180.0;
    private static final int BANDS = 8;
    private static final int SEG = 16;
    private static final double TAU = Math.PI * 2.0;
    private static final float STEP = 0.02F;
    private static final float VISION_ALPHA = 0.24F;
    private static final int VISION_RGB = 0x5A0A0E;

    private static float sky;
    private static float vision;
    private static float night;

    private NtEgoSky() {
    }

    // ---------------------------------------------------------------- 状态

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        ClientLevel level = mc.f_91073_;
        LocalPlayer local = mc.f_91074_;
        if (level == null || local == null) {
            sky = 0.0F;
            vision = 0.0F;
            return;
        }
        // 只遍历客户端已加载的玩家列表（players()），不再对 ±3e7 的全域 AABB 做实体查询。
        boolean anyEgo = false;
        for (Player p : level.m_6907_()) {
            if (NtBody.egoActive(p)) {
                anyEgo = true;
                break;
            }
        }
        sky = approach(sky, anyEgo ? 1.0F : 0.0F);
        vision = approach(vision, NtBody.egoActive(local) ? 1.0F : 0.0F);
        night = nightFactor(level.m_46468_());
    }

    private static float approach(float v, float target) {
        float d = target - v;
        return v + Math.max(-STEP, Math.min(STEP, d));
    }

    /** 0 = 白天，1 = 夜晚（黄昏/黎明过渡）。时间按 24000 tick 循环。 */
    private static float nightFactor(long dayTime) {
        double t = ((dayTime % 24000L) + 24000L) % 24000L;
        double dusk = clamp01((t - 12000.0) / 1000.0);
        double dawn = clamp01((23000.0 - t) / 1000.0);
        return (float) Math.min(dusk, dawn);
    }

    /** 天空血色强度：白天 0.35，夜晚 0.9。 */
    private static float skyStrength() {
        return sky * (0.35F + 0.55F * night);
    }

    // ---------------------------------------------------------------- 雾色

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = skyStrength();
        if (s <= 0.01F) {
            return;
        }
        float k = Math.min(0.85F, s * 0.8F);
        event.setRed(lerp(event.getRed(), 0.42F, k));
        event.setGreen(lerp(event.getGreen(), 0.02F, k));
        event.setBlue(lerp(event.getBlue(), 0.03F, k));
    }

    // ---------------------------------------------------------------- 视野

    @SubscribeEvent
    public static void onVision(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type() || vision <= 0.01F) {
            return;
        }
        int w = event.getWindow().m_85445_();
        int h = event.getWindow().m_85446_();
        int a = Math.round(255.0F * VISION_ALPHA * vision);
        event.getGuiGraphics().m_280509_(0, 0, w, h, (a << 24) | VISION_RGB);
    }

    // ---------------------------------------------------------------- 天空穹顶

    @SubscribeEvent
    public static void onRenderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float s = skyStrength();
        Minecraft mc = Minecraft.m_91087_();
        if (s <= 0.01F || mc.f_91073_ == null) {
            return;
        }
        Vec3 cam = event.getCamera().m_90583_();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource src = mc.m_91269_().m_110104_();
        RenderType type = RenderType.m_110473_(WHITE);
        VertexConsumer vc = src.m_6299_(type);
        Painter g = new Painter(vc, pose.m_85850_().m_252922_(), pose.m_85850_().m_252943_(), cam);

        // 穹顶画在天空之上、地形之下；关闭深度测试，地形之后绘制会正常覆盖它。
        RenderSystem.disableDepthTest();
        float nightDim = 1.0F - 0.25F * night;
        for (int b = 0; b < BANDS; b++) {
            double e0 = (Math.PI / 2.0) * b / BANDS;
            double e1 = (Math.PI / 2.0) * (b + 1) / BANDS;
            V[] low = new V[SEG + 1];
            V[] high = new V[SEG + 1];
            for (int k = 0; k <= SEG; k++) {
                double h = TAU * k / SEG;
                low[k] = dome(cam, h, e0, s, nightDim);
                high[k] = dome(cam, h, e1, s, nightDim);
            }
            for (int k = 0; k < SEG; k++) {
                g.quad(low[k], low[k + 1], high[k + 1], high[k]);
            }
        }
        src.m_109912_(type);
        RenderSystem.enableDepthTest();
    }

    /** 穹顶上一点：仰角 e（0 为地平线，π/2 为天顶）。颜色与透明度随仰角变化。 */
    private static V dome(Vec3 cam, double h, double e, float s, float nightDim) {
        double t = e / (Math.PI / 2.0);
        double c = Math.cos(e);
        double x = cam.f_82479_ + Math.cos(h) * c * DOME;
        double y = cam.f_82480_ + Math.sin(e) * DOME;
        double z = cam.f_82481_ + Math.sin(h) * c * DOME;
        float r = lerp(0.62F, 0.22F, (float) t) * nightDim;
        float gg = lerp(0.06F, 0.00F, (float) t) * nightDim;
        float b = lerp(0.07F, 0.03F, (float) t) * nightDim;
        float a = s * (0.55F + 0.35F * (float) t);
        return new V(x, y, z, r, gg, b, a);
    }

    // ---------------------------------------------------------------- 工具

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** 带颜色的世界坐标顶点。 */
    private record V(double x, double y, double z, float r, float g, float b, float a) {
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
            n.transform(0.0F, -1.0F, 0.0F, tn);
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
