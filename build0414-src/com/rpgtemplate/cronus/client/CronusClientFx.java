package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.cronus.CronusFxMsg;
import com.rpgtemplate.cronus.CronusKeys;
import com.rpgtemplate.forge.client.ClientSystemData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 假面骑士 Cronus 纯动态几何世界渲染器（零原版廉价粒子堆砌）：
 * <ul>
 *   <li>地面巨型罗马数字（I~XII）时钟领域与骤停/狂转表针</li>
 *   <li>原剧街机风 3D 矢量打击弹出字：{@code HIT! / GREAT! / CRITICAL! / GUARD! / ITEM GET!}</li>
 *   <li>低血量（&lt;15%）目标头顶自动浮现霓虹翠绿 {@code OUT} 绝版处决标识</li>
 *   <li>时停悬停构造物（断罪十字刻四光锯 / 全方位死角包围网 12 光弹）与回旋踢旋转表针</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CronusClientFx {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final int FULLBRIGHT = 0xF000F0;

    private static final String[] ROMAN = {
        "XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"
    };

    private static final List<DomainFx> DOMAINS = new ArrayList<>();
    private static final List<PopupFx> POPUPS = new ArrayList<>();
    private static final List<RippleFx> RIPPLES = new ArrayList<>();
    private static final List<TrapFx> TRAPS = new ArrayList<>();
    private static long clientTick = 0L;

    private static final class DomainFx {
        final int ownerId;
        double x, y, z;
        float radius;
        int age;
        int maxAge;
        boolean restarting;

        DomainFx(int ownerId, double x, double y, double z, float radius, int maxAge, boolean restarting) {
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.maxAge = maxAge;
            this.restarting = restarting;
        }
    }

    private static final class PopupFx {
        final double x, y, z;
        final int kind;
        int age;
        final int maxAge = 22;

        PopupFx(double x, double y, double z, int kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
        }
    }

    private static final class RippleFx {
        final double x, y, z;
        final float maxRadius;
        final int style; // 0=普通波纹, 1=变身表盘, 2=Reset逆转, 3=瞬踢, 4=蓄力回旋踢
        int age;
        final int maxAge;

        RippleFx(double x, double y, double z, float maxRadius, int style, int maxAge) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxRadius = maxRadius;
            this.style = style;
            this.maxAge = maxAge;
        }
    }

    private static final class TrapFx {
        final int targetId;
        double x, y, z;
        final int trapKind; // 0=十字光锯, 1=12枚包围光弹
        int age;
        final int maxAge = 160;

        TrapFx(int targetId, double x, double y, double z, int trapKind) {
            this.targetId = targetId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.trapKind = trapKind;
        }
    }

    private CronusClientFx() {
    }

    public static void accept(CronusFxMsg msg) {
        switch (msg.kind) {
            case CronusFxMsg.KIND_DOMAIN_PAUSE -> {
                for (DomainFx d : DOMAINS) {
                    if (d.ownerId == msg.entityId && !d.restarting) {
                        d.x = msg.x;
                        d.y = msg.y;
                        d.z = msg.z;
                        d.radius = msg.param;
                        d.age = 0;
                        d.maxAge = Math.max(35, msg.extra);
                        return;
                    }
                }
                DOMAINS.add(new DomainFx(msg.entityId, msg.x, msg.y, msg.z, msg.param, Math.max(35, msg.extra), false));
            }
            case CronusFxMsg.KIND_DOMAIN_RESTART -> {
                DOMAINS.removeIf(d -> d.ownerId == msg.entityId);
                TRAPS.clear();
                DOMAINS.add(new DomainFx(msg.entityId, msg.x, msg.y, msg.z, msg.param, 22, true));
            }
            case CronusFxMsg.KIND_ARCADE_POPUP -> {
                if (POPUPS.size() < 48) {
                    double jx = (Math.random() - 0.5) * 0.45;
                    double jy = (Math.random() - 0.2) * 0.30;
                    double jz = (Math.random() - 0.5) * 0.45;
                    POPUPS.add(new PopupFx(msg.x + jx, msg.y + jy, msg.z + jz, msg.extra));
                }
            }
            case CronusFxMsg.KIND_RIDER_KICK ->
                RIPPLES.add(new RippleFx(msg.x, msg.y, msg.z, msg.param, msg.extra == 1 ? 4 : 3, 18));
            case CronusFxMsg.KIND_PAUSE_TRAP ->
                TRAPS.add(new TrapFx(msg.entityId, msg.x, msg.y, msg.z, msg.extra));
            case CronusFxMsg.KIND_OUT_BANNER ->
                CronusHudOverlay.triggerOutBanner();
            case CronusFxMsg.KIND_CLOCK_RIPPLE ->
                RIPPLES.add(new RippleFx(msg.x, msg.y, msg.z, msg.param, msg.extra, 18));
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91073_ == null) {
            DOMAINS.clear();
            POPUPS.clear();
            RIPPLES.clear();
            TRAPS.clear();
            return;
        }
        if (mc.m_91104_()) {
            return;
        }
        clientTick++;

        Iterator<DomainFx> dit = DOMAINS.iterator();
        while (dit.hasNext()) {
            DomainFx d = dit.next();
            if (++d.age >= d.maxAge) {
                dit.remove();
            }
        }
        Iterator<PopupFx> pit = POPUPS.iterator();
        while (pit.hasNext()) {
            if (++pit.next().age >= 22) {
                pit.remove();
            }
        }
        Iterator<RippleFx> rit = RIPPLES.iterator();
        while (rit.hasNext()) {
            RippleFx r = rit.next();
            if (++r.age >= r.maxAge) {
                rit.remove();
            }
        }
        Iterator<TrapFx> tit = TRAPS.iterator();
        while (tit.hasNext()) {
            TrapFx t = tit.next();
            Entity e = mc.f_91073_.m_6815_(t.targetId);
            if (e != null && e.m_6084_()) {
                t.x = e.m_20185_();
                t.y = e.m_20186_() + e.m_20206_() * 0.5;
                t.z = e.m_20189_();
            }
            if (++t.age >= t.maxAge) {
                tit.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.m_91087_();
        LocalPlayer self = mc.f_91074_;
        if (self == null || mc.f_91073_ == null) {
            return;
        }

        PlayerSystemData selfData = ClientSystemData.get();
        boolean selfIsCronus = selfData != null && selfData.isActivated()
            && CronusKeys.TEMPLATE_ID.equals(selfData.loadedTemplateId());
        if (!selfIsCronus && DOMAINS.isEmpty() && POPUPS.isEmpty() && RIPPLES.isEmpty() && TRAPS.isEmpty()) {
            return;
        }

        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().m_90583_();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource src = mc.m_91269_().m_110104_();
        RenderType type = RenderType.m_110473_(WHITE);
        VertexConsumer vc = src.m_6299_(type);

        pose.m_85836_();
        pose.m_85837_(-cam.f_82479_, -cam.f_82480_, -cam.f_82481_);
        Matrix4f mat = pose.m_85850_().m_252922_();

        // 1. 地面巨型罗马数字时钟领域（Pause 定格 / Restart 碎裂爆发）
        for (DomainFx d : DOMAINS) {
            renderClockDomain(vc, mat, d, pt);
        }

        // 2. 变身/三段跳/Reset/骑士踢时钟几何波纹与旋转表针
        for (RippleFx r : RIPPLES) {
            renderRipple(vc, mat, r, pt);
        }

        // 3. 时停专属悬停武装陷阱（十字光锯 / 12枚包围光弹）
        for (TrapFx t : TRAPS) {
            renderSuspendedTrap(vc, mat, t, pt);
        }

        // 4. 自身变身态背部微型编年史金绿光轮（为后续外部盔甲模型提供即时变身视觉锚点）
        if (selfIsCronus && CronusKeys.isTransformed(selfData) && !mc.f_91066_.m_92176_().m_90612_()) {
            double px = Mth.m_14139_(pt, self.f_19854_, self.m_20185_());
            double py = Mth.m_14139_(pt, self.f_19855_, self.m_20186_()) + 1.15;
            double pz = Mth.m_14139_(pt, self.f_19856_, self.m_20189_());
            renderBackHalo(vc, mat, px, py, pz, self.m_146908_(), (clientTick + pt) * 0.05f);
        }

        // 5. Cronus 视角下自动扫描周围 <15% HP 目标，渲染头顶霓虹绿「OUT」绝版标记
        if (selfIsCronus) {
            boolean paused = CronusKeys.isPaused(selfData);
            AABB scan = self.m_20191_().m_82377_(24.0, 12.0, 24.0);
            for (LivingEntity ent : mc.f_91073_.m_45976_(LivingEntity.class, scan)) {
                if (ent == self || !ent.m_6084_() || ent.m_21233_() <= 0f) {
                    continue;
                }
                if (ent.m_21223_() <= ent.m_21233_() * 0.15f) {
                    double ex = Mth.m_14139_(pt, ent.f_19854_, ent.m_20185_());
                    double ey = Mth.m_14139_(pt, ent.f_19855_, ent.m_20186_()) + ent.m_20206_() + 0.55;
                    double ez = Mth.m_14139_(pt, ent.f_19856_, ent.m_20189_());
                    renderOverheadOutBadge(vc, mat, cam, ex, ey, ez, paused, clientTick + pt);
                }
            }
        }

        // 6. 原剧街机风 3D 动态矢量字（HIT! / GREAT! / CRITICAL! / GUARD! / ITEM GET!）
        for (PopupFx p : POPUPS) {
            renderArcadePopup(vc, mat, cam, p, pt);
        }

        pose.m_85849_();
        src.m_109912_(type);
    }

    // ==================== 1. 罗马数字时钟领域 ====================

    private static void renderClockDomain(VertexConsumer vc, Matrix4f mat, DomainFx d, float pt) {
        float t = (d.age + pt) / (float) d.maxAge;
        float alpha = d.restarting ? (1.0f - t) * 0.95f : 0.62f;
        float r = d.restarting ? d.radius * (1.0f + t * 0.22f) : d.radius;
        double y = d.y + 0.04;

        int mainRgb = d.restarting ? 0xFFE066 : 0x18F29A;
        int subRgb = 0xD4AF37;

        // 外双环与内枢环
        drawFlatRing(vc, mat, d.x, y, d.z, r - 0.18f, r, 64, mainRgb, alpha);
        drawFlatRing(vc, mat, d.x, y, d.z, r * 0.80f - 0.10f, r * 0.80f, 64, subRgb, alpha * 0.82f);
        drawFlatRing(vc, mat, d.x, y, d.z, 0.45f, 0.68f, 24, mainRgb, alpha);

        // 60 格精密刻度线 + 12 座罗马数字（XII, I .. XI）
        for (int i = 0; i < 60; i++) {
            double ang = (Math.PI * 2.0 * i) / 60.0;
            boolean major = (i % 5 == 0);
            float inR = major ? r * 0.82f : r * 0.92f;
            float outR = r - 0.22f;
            float thick = major ? 0.11f : 0.045f;
            drawRadialSpoke(vc, mat, d.x, y + 0.005, d.z, ang, inR, outR, thick, major ? subRgb : mainRgb, alpha);
        }
        for (int h = 0; h < 12; h++) {
            double ang = -Math.PI * 0.5 + (Math.PI * 2.0 * h) / 12.0;
            double gx = d.x + Math.cos(ang) * (r * 0.89f);
            double gz = d.z + Math.sin(ang) * (r * 0.89f);
            drawGroundRoman(vc, mat, gx, y + 0.012, gz, ang + Math.PI * 0.5, ROMAN[h], r * 0.036f, subRgb, alpha);
        }

        // 时针与分针：Pause 期间呈机械卡顿定格抖动，Restart 期间飞速顺时针狂转并升起光柱
        double secStep = d.restarting
            ? (d.age + pt) * 0.85
            : Math.floor((clientTick) / 20.0) * (Math.PI * 2.0 / 60.0);
        double minAng = -Math.PI * 0.5 + secStep;
        double hourAng = -Math.PI * 0.5 + secStep * 0.25 + 1.1;
        drawRadialSpoke(vc, mat, d.x, y + 0.018, d.z, minAng, -0.55f, r * 0.76f, 0.16f, mainRgb, alpha * 0.95f);
        drawRadialSpoke(vc, mat, d.x, y + 0.022, d.z, hourAng, -0.40f, r * 0.50f, 0.22f, subRgb, alpha * 0.95f);

        if (d.restarting) {
            float pillarH = 7.5f * (1.0f - t * 0.4f);
            for (int k = 0; k < 12; k++) {
                double ang = (Math.PI * 2.0 * k) / 12.0 + t * 0.9;
                double px = d.x + Math.cos(ang) * (r * 0.85f);
                double pz = d.z + Math.sin(ang) * (r * 0.85f);
                drawVerticalBeam(vc, mat, px, y, pz, 0.14f, pillarH, 0x2AF598, alpha * 0.75f);
            }
        }
    }

    // ==================== 2. 波纹与回旋踢旋转表针 ====================

    private static void renderRipple(VertexConsumer vc, Matrix4f mat, RippleFx r, float pt) {
        float t = Mth.m_14036_((r.age + pt) / (float) r.maxAge, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - t) * (1.0f - t);
        float curR = Math.max(0.25f, r.maxRadius * ease);
        float alpha = (1.0f - t) * 0.90f;
        int rgb = (r.style == 2) ? 0xFFD166 : 0x18F29A;

        drawFlatRing(vc, mat, r.x, r.y + 0.03, r.z, Math.max(0.05f, curR - 0.18f), curR, 40, rgb, alpha);
        if (r.style == 4 || r.style == 1 || r.style == 2) {
            double dir = (r.style == 2) ? -1.0 : 1.0;
            double spin = dir * t * Math.PI * 3.5;
            drawRadialSpoke(vc, mat, r.x, r.y + 0.06, r.z, spin, -0.3f, curR * 0.95f, 0.18f, 0xFFE066, alpha);
            drawRadialSpoke(vc, mat, r.x, r.y + 0.06, r.z, spin + Math.PI * 0.65, -0.2f, curR * 0.65f, 0.15f, rgb, alpha);
        }
        if (r.style == 1) {
            drawVerticalBeam(vc, mat, r.x, r.y, r.z, 0.45f * (1.0f - t), 4.2f, 0x18F29A, alpha * 0.65f);
        }
    }

    // ==================== 3. 时停专属悬停构造物 ====================

    private static void renderSuspendedTrap(VertexConsumer vc, Matrix4f mat, TrapFx trap, float pt) {
        float pulse = 0.78f + 0.20f * Mth.m_14031_((trap.age + pt) * 0.35f);
        if (trap.trapKind == 0) {
            // 断罪十字刻：目标四周悬停 4 柄翡翠链锯光刃，指向目标胸口
            for (int i = 0; i < 4; i++) {
                double ang = Math.PI * 0.25 + (Math.PI * 0.5 * i);
                double sx = trap.x + Math.cos(ang) * 2.1;
                double sz = trap.z + Math.sin(ang) * 2.1;
                drawRadialSpoke(vc, mat, trap.x, trap.y, trap.z, ang, 0.55f, 2.15f, 0.16f, 0x18F29A, pulse);
                drawVerticalBeam(vc, mat, sx, trap.y - 0.35, sz, 0.08f, 0.70f, 0xFFE066, pulse);
            }
        } else {
            // 全方位死角包围网：12 枚悬停光弹环绕目标立体锁定
            for (int i = 0; i < 12; i++) {
                double ang = (Math.PI * 2.0 * i) / 12.0;
                double oy = ((i % 3) - 1) * 0.55;
                double ox = trap.x + Math.cos(ang) * 1.95;
                double oz = trap.z + Math.sin(ang) * 1.95;
                drawVerticalBeam(vc, mat, ox, trap.y + oy - 0.14, oz, 0.14f, 0.28f, 0x2AF598, pulse);
                drawRadialSpoke(vc, mat, trap.x, trap.y + oy, trap.z, ang, 1.35f, 1.95f, 0.04f, 0xFFE066, pulse * 0.7f);
            }
        }
    }

    private static void renderBackHalo(VertexConsumer vc, Matrix4f mat, double x, double y, double z,
                                       float yawDeg, float spin) {
        double backRad = Math.toRadians(yawDeg + 90.0);
        double bx = x - Math.cos(backRad) * 0.32;
        double bz = z - Math.sin(backRad) * 0.32;
        drawFlatRing(vc, mat, bx, y + 0.55, bz, 0.42f, 0.48f, 24, 0x18F29A, 0.65f);
        drawRadialSpoke(vc, mat, bx, y + 0.55, bz, spin, 0.0f, 0.44f, 0.04f, 0xFFE066, 0.75f);
    }

    // ==================== 4. 头顶霓虹绿「OUT」绝版标识 ====================

    private static void renderOverheadOutBadge(VertexConsumer vc, Matrix4f mat, Vec3 cam,
                                               double x, double y, double z, boolean paused, float time) {
        float pulse = paused ? (0.85f + 0.15f * Mth.m_14031_(time * 0.6f)) : 0.80f;
        float bob = 0.06f * Mth.m_14031_(time * 0.18f);
        BillboardBasis basis = BillboardBasis.towardsCamera(cam, x, y + bob, z);
        float scale = paused ? 0.082f : 0.068f;

        // 黑色硬朗描边底衬 + 荧光绿「OUT」矢量字 + 下方倒三角处决准星
        drawVectorText(vc, mat, basis.offset(0.0, 0.0, -0.015), "OUT", scale, 0x061A12, pulse * 0.92f, 0.08f);
        drawVectorText(vc, mat, basis, "OUT", scale, paused ? 0x38FF9C : 0x18F29A, pulse, 0.0f);

        // 下指处决箭头
        quadBillboard(vc, mat, basis, -0.14f, -0.18f, 0.14f, -0.12f, 0xFFE066, pulse);
        quadBillboard(vc, mat, basis, -0.06f, -0.26f, 0.06f, -0.18f, 0xFFE066, pulse);
    }

    // ==================== 5. 原剧街机风 3D 矢量打击弹出字 ====================

    private static void renderArcadePopup(VertexConsumer vc, Matrix4f mat, Vec3 cam, PopupFx p, float pt) {
        float t = Mth.m_14036_((p.age + pt) / (float) p.maxAge, 0.0f, 1.0f);
        // 弹跳放大曲线：前 20% 迅速张开，随后稳健上浮淡出
        float popScale = t < 0.18f ? (0.45f + (t / 0.18f) * 0.75f) : (1.20f - (t - 0.18f) * 0.25f);
        float alpha = t > 0.70f ? (1.0f - (t - 0.70f) / 0.30f) : 0.98f;
        double py = p.y + t * 0.55;

        String label;
        int mainColor;
        int accentColor;
        float baseSize;
        switch (p.kind) {
            case CronusFxMsg.POPUP_GREAT -> {
                label = "GREAT!";
                mainColor = 0xFFB703;
                accentColor = 0xFFF3B0;
                baseSize = 0.072f;
            }
            case CronusFxMsg.POPUP_CRITICAL -> {
                label = "CRITICAL!";
                mainColor = 0xFF2E93;
                accentColor = 0xFFE066;
                baseSize = 0.086f;
            }
            case CronusFxMsg.POPUP_GUARD -> {
                label = "GUARD!";
                mainColor = 0x2EC4B6;
                accentColor = 0xCBF3F0;
                baseSize = 0.068f;
            }
            case CronusFxMsg.POPUP_ITEM_GET -> {
                label = "ITEM GET!";
                mainColor = 0xFFD166;
                accentColor = 0x18F29A;
                baseSize = 0.074f;
            }
            default -> {
                label = "HIT!";
                mainColor = 0x35F2FF;
                accentColor = 0xFFE44D;
                baseSize = 0.066f;
            }
        }

        float s = baseSize * popScale;
        BillboardBasis basis = BillboardBasis.towardsCamera(cam, p.x, py, p.z);

        // 街机爆炸底衬芒星（CRITICAL! / GREAT! 专属）
        if (p.kind == CronusFxMsg.POPUP_CRITICAL || p.kind == CronusFxMsg.POPUP_GREAT) {
            float burstW = label.length() * s * 2.2f;
            quadBillboard(vc, mat, basis.offset(0, 0, -0.03), -burstW, -s * 0.7f, burstW, s * 0.7f, accentColor, alpha * 0.32f);
        }
        // 厚黑立体街机阴影层 + 高亮霓虹主字层
        drawVectorText(vc, mat, basis.offset(s * 0.25, -s * 0.25, -0.02), label, s, 0x0B0E14, alpha * 0.92f, 0.06f);
        drawVectorText(vc, mat, basis, label, s, mainColor, alpha, 0.0f);
    }

    // ==================== 底层纯几何图元与矢量字库 ====================

    private static final class BillboardBasis {
        final double cx, cy, cz;
        final double rx, ry, rz;
        final double ux, uy, uz;
        final double fx, fy, fz;

        BillboardBasis(double cx, double cy, double cz,
                       double rx, double ry, double rz,
                       double ux, double uy, double uz,
                       double fx, double fy, double fz) {
            this.cx = cx; this.cy = cy; this.cz = cz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.ux = ux; this.uy = uy; this.uz = uz;
            this.fx = fx; this.fy = fy; this.fz = fz;
        }

        static BillboardBasis towardsCamera(Vec3 cam, double x, double y, double z) {
            double dx = cam.f_82479_ - x;
            double dy = cam.f_82480_ - y;
            double dz = cam.f_82481_ - z;
            double len = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double fx = dx / len, fy = dy / len, fz = dz / len;
            // right = up(0,1,0) x forward
            double rx = fz, ry = 0.0, rz = -fx;
            double rLen = Math.max(1e-4, Math.hypot(rx, rz));
            rx /= rLen; rz /= rLen;
            // true up = forward x right
            double ux = fy * rz - fz * ry;
            double uy = fz * rx - fx * rz;
            double uz = fx * ry - fy * rx;
            return new BillboardBasis(x, y, z, rx, ry, rz, ux, uy, uz, fx, fy, fz);
        }

        BillboardBasis offset(double rightOff, double upOff, double fwdOff) {
            return new BillboardBasis(
                cx + rx * rightOff + ux * upOff + fx * fwdOff,
                cy + ry * rightOff + uy * upOff + fy * fwdOff,
                cz + rz * rightOff + uz * upOff + fz * fwdOff,
                rx, ry, rz, ux, uy, uz, fx, fy, fz);
        }
    }

    /** 使用 5x5 像素风硬朗街机矢量矩阵渲染无贴图 3D 字母（HIT! / GREAT! / CRITICAL! / GUARD! / ITEM GET! / OUT）。 */
    private static void drawVectorText(VertexConsumer vc, Matrix4f mat, BillboardBasis b,
                                       String text, float pixel, int rgb, float alpha, float pad) {
        float charAdvance = 4.2f * pixel;
        float totalW = text.length() * charAdvance - 1.2f * pixel;
        float startX = -totalW * 0.5f;
        float startY = -2.5f * pixel;

        for (int idx = 0; idx < text.length(); idx++) {
            int mask = glyphMask3x5(text.charAt(idx));
            if (mask == 0) {
                continue;
            }
            float ox = startX + idx * charAdvance;
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    int bit = 1 << ((4 - row) * 3 + (2 - col));
                    if ((mask & bit) != 0) {
                        float x0 = ox + col * pixel - pad * pixel;
                        float y0 = startY + row * pixel - pad * pixel;
                        float x1 = x0 + pixel + pad * 2f * pixel;
                        float y1 = y0 + pixel + pad * 2f * pixel;
                        quadBillboard(vc, mat, b, x0, y0, x1, y1, rgb, alpha);
                    }
                }
            }
        }
    }

    /** 3x5 紧凑高辨识街机点阵字模（15-bit 位图，高位在左上）。 */
    private static int glyphMask3x5(char ch) {
        return switch (Character.toUpperCase(ch)) {
            case 'A' -> 0b010_101_111_101_101;
            case 'C' -> 0b111_100_100_100_111;
            case 'D' -> 0b110_101_101_101_110;
            case 'E' -> 0b111_100_110_100_111;
            case 'G' -> 0b111_100_101_101_111;
            case 'H' -> 0b101_101_111_101_101;
            case 'I' -> 0b111_010_010_010_111;
            case 'L' -> 0b100_100_100_100_111;
            case 'M' -> 0b101_111_111_101_101;
            case 'O' -> 0b111_101_101_101_111;
            case 'R' -> 0b110_101_110_101_101;
            case 'T' -> 0b111_010_010_010_010;
            case 'U' -> 0b101_101_101_101_111;
            case '!' -> 0b010_010_010_000_010;
            default -> 0;
        };
    }

    private static void drawGroundRoman(VertexConsumer vc, Matrix4f mat, double cx, double y, double cz,
                                        double tangentAng, String roman, float s, int rgb, float alpha) {
        double tx = Math.cos(tangentAng), tz = Math.sin(tangentAng);
        double nx = -tz, nz = tx;
        float spacing = s * 1.45f;
        float start = -(roman.length() - 1) * spacing * 0.5f;
        for (int i = 0; i < roman.length(); i++) {
            double gx = cx + tx * (start + i * spacing);
            double gz = cz + tz * (start + i * spacing);
            char c = roman.charAt(i);
            if (c == 'I') {
                drawGroundSegment(vc, mat, gx - nx * s, y, gz - nz * s, gx + nx * s, gz + nz * s, s * 0.28f, rgb, alpha);
            } else if (c == 'V') {
                drawGroundSegment(vc, mat, gx - tx * s * 0.45 - nx * s, y, gz - tz * s * 0.45 - nz * s,
                    gx + nx * s, gz + nz * s, s * 0.25f, rgb, alpha);
                drawGroundSegment(vc, mat, gx + tx * s * 0.45 - nx * s, y, gz + tz * s * 0.45 - nz * s,
                    gx + nx * s, gz + nz * s, s * 0.25f, rgb, alpha);
            } else if (c == 'X') {
                drawGroundSegment(vc, mat, gx - tx * s * 0.45 - nx * s, y, gz - tz * s * 0.45 - nz * s,
                    gx + tx * s * 0.45 + nx * s, gz + tz * s * 0.45 + nz * s, s * 0.25f, rgb, alpha);
                drawGroundSegment(vc, mat, gx + tx * s * 0.45 - nx * s, y, gz + tz * s * 0.45 - nz * s,
                    gx - tx * s * 0.45 + nx * s, gz - tz * s * 0.45 + nz * s, s * 0.25f, rgb, alpha);
            }
        }
    }

    private static void drawGroundSegment(VertexConsumer vc, Matrix4f mat,
                                          double x0, double y, double z0, double x1, double z1,
                                          float halfW, int rgb, float alpha) {
        double dx = x1 - x0, dz = z1 - z0;
        double len = Math.max(1e-4, Math.hypot(dx, dz));
        double px = (-dz / len) * halfW, pz = (dx / len) * halfW;
        emitDoubleSidedQuad(vc, mat,
            (float) (x0 - px), (float) y, (float) (z0 - pz),
            (float) (x0 + px), (float) y, (float) (z0 + pz),
            (float) (x1 + px), (float) y, (float) (z1 + pz),
            (float) (x1 - px), (float) y, (float) (z1 - pz),
            rgb, alpha);
    }

    private static void drawFlatRing(VertexConsumer vc, Matrix4f mat, double cx, double y, double cz,
                                     float inR, float outR, int segs, int rgb, float alpha) {
        for (int i = 0; i < segs; i++) {
            double a0 = (Math.PI * 2.0 * i) / segs;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            emitDoubleSidedQuad(vc, mat,
                (float) cx + c0 * inR, (float) y, (float) cz + s0 * inR,
                (float) cx + c0 * outR, (float) y, (float) cz + s0 * outR,
                (float) cx + c1 * outR, (float) y, (float) cz + s1 * outR,
                (float) cx + c1 * inR, (float) y, (float) cz + s1 * inR,
                rgb, alpha);
        }
    }

    private static void drawRadialSpoke(VertexConsumer vc, Matrix4f mat, double cx, double y, double cz,
                                        double ang, float r0, float r1, float halfW, int rgb, float alpha) {
        double cos = Math.cos(ang), sin = Math.sin(ang);
        drawGroundSegment(vc, mat,
            cx + cos * r0, y, cz + sin * r0,
            cx + cos * r1, cz + sin * r1,
            halfW, rgb, alpha);
    }

    private static void drawVerticalBeam(VertexConsumer vc, Matrix4f mat, double x, double y, double z,
                                         float halfW, float height, int rgb, float alpha) {
        float x0 = (float) x - halfW, x1 = (float) x + halfW;
        float z0 = (float) z - halfW, z1 = (float) z + halfW;
        float y0 = (float) y, y1 = (float) y + height;
        emitDoubleSidedQuad(vc, mat, x0, y0, (float) z, x1, y0, (float) z, x1, y1, (float) z, x0, y1, (float) z, rgb, alpha);
        emitDoubleSidedQuad(vc, mat, (float) x, y0, z0, (float) x, y0, z1, (float) x, y1, z1, (float) x, y1, z0, rgb, alpha);
    }

    private static void quadBillboard(VertexConsumer vc, Matrix4f mat, BillboardBasis b,
                                      float u0, float v0, float u1, float v1, int rgb, float alpha) {
        float ax = (float) (b.cx + b.rx * u0 + b.ux * v0);
        float ay = (float) (b.cy + b.ry * u0 + b.uy * v0);
        float az = (float) (b.cz + b.rz * u0 + b.uz * v0);

        float bx = (float) (b.cx + b.rx * u1 + b.ux * v0);
        float by = (float) (b.cy + b.ry * u1 + b.uy * v0);
        float bz = (float) (b.cz + b.rz * u1 + b.uz * v0);

        float cx = (float) (b.cx + b.rx * u1 + b.ux * v1);
        float cy = (float) (b.cy + b.ry * u1 + b.uy * v1);
        float cz = (float) (b.cz + b.rz * u1 + b.uz * v1);

        float dx = (float) (b.cx + b.rx * u0 + b.ux * v1);
        float dy = (float) (b.cy + b.ry * u0 + b.uy * v1);
        float dz = (float) (b.cz + b.rz * u0 + b.uz * v1);

        emitDoubleSidedQuad(vc, mat, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, rgb, alpha);
    }

    private static void emitDoubleSidedQuad(VertexConsumer vc, Matrix4f mat,
                                            float ax, float ay, float az,
                                            float bx, float by, float bz,
                                            float cx, float cy, float cz,
                                            float dx, float dy, float dz,
                                            int rgb, float alpha) {
        float rf = ((rgb >> 16) & 0xFF) / 255.0f;
        float gf = ((rgb >> 8) & 0xFF) / 255.0f;
        float bf = (rgb & 0xFF) / 255.0f;
        float af = Mth.m_14036_(alpha, 0.0f, 1.0f);
        org.joml.Vector4f pA = new org.joml.Vector4f(ax, ay, az, 1.0f).mul(mat);
        org.joml.Vector4f pB = new org.joml.Vector4f(bx, by, bz, 1.0f).mul(mat);
        org.joml.Vector4f pC = new org.joml.Vector4f(cx, cy, cz, 1.0f).mul(mat);
        org.joml.Vector4f pD = new org.joml.Vector4f(dx, dy, dz, 1.0f).mul(mat);

        vc.m_5954_(pA.x, pA.y, pA.z, rf, gf, bf, af, 0F, 0F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, 1F, 0F);
        vc.m_5954_(pB.x, pB.y, pB.z, rf, gf, bf, af, 1F, 0F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, 1F, 0F);
        vc.m_5954_(pC.x, pC.y, pC.z, rf, gf, bf, af, 1F, 1F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, 1F, 0F);
        vc.m_5954_(pD.x, pD.y, pD.z, rf, gf, bf, af, 0F, 1F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, 1F, 0F);

        vc.m_5954_(pD.x, pD.y, pD.z, rf, gf, bf, af, 0F, 1F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, -1F, 0F);
        vc.m_5954_(pC.x, pC.y, pC.z, rf, gf, bf, af, 1F, 1F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, -1F, 0F);
        vc.m_5954_(pB.x, pB.y, pB.z, rf, gf, bf, af, 1F, 0F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, -1F, 0F);
        vc.m_5954_(pA.x, pA.y, pA.z, rf, gf, bf, af, 0F, 0F, OverlayTexture.f_118083_, FULLBRIGHT, 0F, -1F, 0F);
    }
}
