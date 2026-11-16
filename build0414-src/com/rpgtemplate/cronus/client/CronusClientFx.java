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
    private static final List<HitImpactFx> IMPACTS = new ArrayList<>();
    private static final List<BeamLineFx> BEAMS = new ArrayList<>();
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
        final double driftX, driftZ;
        final float tiltRad;
        final int kind;
        int age;
        final int maxAge;

        PopupFx(double x, double y, double z, int kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            double ang = Math.random() * Math.PI * 2.0;
            double spd = 0.24 + Math.random() * 0.24;
            this.driftX = Math.cos(ang) * spd;
            this.driftZ = Math.sin(ang) * spd;
            this.tiltRad = (float) ((Math.random() - 0.5) * Math.toRadians(20.0));
            this.kind = kind;
            this.maxAge = (kind == CronusFxMsg.POPUP_CRITICAL || kind == CronusFxMsg.POPUP_ITEM_GET) ? 26 : 22;
        }
    }

    /** 打击瞬间 3D 空间冲击波环 + 立体斩击光弧 + 放射穿刺尖芒（纯动态几何，零廉价粒子）。 */
    private static final class HitImpactFx {
        final double x, y, z;
        final int kind; // POPUP_HIT / GREAT / CRITICAL / GUARD
        final float yawRad;
        final float slashTiltRad;
        int age;
        final int maxAge;

        HitImpactFx(double x, double y, double z, int kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.yawRad = (float) (Math.random() * Math.PI * 2.0);
            this.slashTiltRad = (float) (Math.toRadians(24.0 + Math.random() * 38.0) * (Math.random() < 0.5 ? 1.0 : -1.0));
            this.maxAge = (kind == CronusFxMsg.POPUP_CRITICAL) ? 13 : (kind == CronusFxMsg.POPUP_GREAT ? 11 : 9);
        }
    }

    /** 3D 加色光束与瞬移时空轨线（缺陷护罩Ⅱ 双管翡翠光束 / 时序瞬移金绿残影轨，零廉价粒子）。 */
    private static final class BeamLineFx {
        final double x0, y0, z0;
        final double x1, y1, z1;
        final int style; // 0=BEAM_GUN, 1=BEAM_BLINK
        int age;
        final int maxAge;

        BeamLineFx(double x0, double y0, double z0, double x1, double y1, double z1, int style) {
            this.x0 = x0; this.y0 = y0; this.z0 = z0;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.style = style;
            this.maxAge = (style == CronusFxMsg.BEAM_BLINK) ? 12 : 8;
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
                double r = Math.max(1.0, msg.param + 3.0);
                double rSq = r * r;
                TRAPS.removeIf(t -> {
                    double dx = t.x - msg.x;
                    double dz = t.z - msg.z;
                    return dx * dx + dz * dz <= rSq;
                });
                DOMAINS.add(new DomainFx(msg.entityId, msg.x, msg.y, msg.z, msg.param, 22, true));
            }
            case CronusFxMsg.KIND_ARCADE_POPUP -> {
                if (POPUPS.size() < 48) {
                    double jx = (Math.random() - 0.5) * 0.55;
                    double jy = (Math.random() - 0.15) * 0.35;
                    double jz = (Math.random() - 0.5) * 0.55;
                    POPUPS.add(new PopupFx(msg.x + jx, msg.y + jy, msg.z + jz, msg.extra));
                }
                if (msg.extra != CronusFxMsg.POPUP_ITEM_GET && IMPACTS.size() < 48) {
                    IMPACTS.add(new HitImpactFx(msg.x, msg.y - 0.15, msg.z, msg.extra));
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
            case CronusFxMsg.KIND_HENSHIN_SEQ ->
                CronusSpriteFx.spawnHenshin(msg.entityId, msg.x, msg.y, msg.z);
            case CronusFxMsg.KIND_HENSHIN_FAST ->
                CronusSpriteFx.spawnFastHenshin(msg.entityId, msg.x, msg.y, msg.z);
            case CronusFxMsg.KIND_SUIT_STATE ->
                CronusSuitAppearance.setSuitState(msg.entityId, msg.extra != 0,
                    Math.max(0, Math.round(msg.param)),
                    Math.max(0, (int) Math.round(msg.tx)));
            case CronusFxMsg.KIND_SPRITE_FX ->
                CronusSpriteFx.spawnBurst(msg.extra, msg.entityId, msg.x, msg.y, msg.z, msg.param);
            case CronusFxMsg.KIND_ENERGY_ICON ->
                CronusSpriteFx.spawnEnergyIcon(msg.entityId, msg.extra, msg.x, msg.y, msg.z, (int) Math.max(20.0f, msg.param));
            case CronusFxMsg.KIND_BEAM_LINE -> {
                if (BEAMS.size() < 40) {
                    BEAMS.add(new BeamLineFx(msg.x, msg.y, msg.z, msg.tx, msg.ty, msg.tz, msg.extra));
                }
            }
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
            IMPACTS.clear();
            BEAMS.clear();
            RIPPLES.clear();
            TRAPS.clear();
            CronusSpriteFx.clear();
            return;
        }
        if (mc.m_91104_()) {
            return;
        }
        clientTick++;
        CronusSpriteFx.tick(mc);

        Iterator<DomainFx> dit = DOMAINS.iterator();
        while (dit.hasNext()) {
            DomainFx d = dit.next();
            if (++d.age >= d.maxAge) {
                dit.remove();
            }
        }
        Iterator<PopupFx> pit = POPUPS.iterator();
        while (pit.hasNext()) {
            PopupFx p = pit.next();
            if (++p.age >= p.maxAge) {
                pit.remove();
            }
        }
        Iterator<HitImpactFx> iit = IMPACTS.iterator();
        while (iit.hasNext()) {
            HitImpactFx imp = iit.next();
            if (++imp.age >= imp.maxAge) {
                iit.remove();
            }
        }
        Iterator<BeamLineFx> bit = BEAMS.iterator();
        while (bit.hasNext()) {
            BeamLineFx b = bit.next();
            if (++b.age >= b.maxAge) {
                bit.remove();
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
        if (!selfIsCronus && DOMAINS.isEmpty() && POPUPS.isEmpty() && IMPACTS.isEmpty()
            && BEAMS.isEmpty() && RIPPLES.isEmpty() && TRAPS.isEmpty() && !CronusSpriteFx.hasActive()) {
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

        // 4. 自身变身态背部微型编年史金绿光轮（变身巨型钟表演期间隐藏，避免与主钟表重叠）
        if (selfIsCronus && CronusKeys.isTransformed(selfData)
            && !mc.f_91066_.m_92176_().m_90612_()
            && !CronusSpriteFx.isEntityInHenshin(self.m_19879_())) {
            double px = Mth.m_14139_(pt, self.f_19854_, self.m_20185_());
            double py = Mth.m_14139_(pt, self.f_19855_, self.m_20186_()) + 1.15;
            double pz = Mth.m_14139_(pt, self.f_19856_, self.m_20189_());
            renderBackHalo(vc, mat, px, py, pz, self.m_146908_(), (clientTick + pt) * 0.05f);
        }

        src.m_109912_(type);

        // 5. 打击瞬间 3D 加色辉光冲击波、斩击光弧、双管翡翠光束与瞬移时空轨线（纯几何动态特效，零廉价粒子）
        if (!IMPACTS.isEmpty() || !BEAMS.isEmpty()) {
            RenderType impactRt = CronusRenderTypes.additive(WHITE);
            VertexConsumer ivc = src.m_6299_(impactRt);
            for (HitImpactFx imp : IMPACTS) {
                renderHitImpact(ivc, mat, cam, imp, pt);
            }
            for (BeamLineFx beam : BEAMS) {
                renderBeamLine(ivc, mat, cam, beam, pt);
            }
            src.m_109912_(impactRt);
        }

        // 6. 在地面 Pause/Restart 时钟领域叠加高清发光黄金罗马数字环
        if (!DOMAINS.isEmpty()) {
            for (DomainFx d : DOMAINS) {
                float t = (d.age + pt) / (float) d.maxAge;
                float alpha = d.restarting ? (1.0f - t) * 0.95f : 0.88f;
                float r = d.restarting ? d.radius * (1.0f + t * 0.22f) : d.radius;
                float rot = d.restarting ? t * 2.4f : 0.0f;
                CronusSpriteFx.renderGroundClockOverlay(src, mat, d.x, d.y + 0.075, d.z, r, rot, alpha);
            }
        }

        // 7. Cronus 视角下自动扫描周围 <15% HP 目标，渲染高清特摄「OUT!」绝版贴图标记
        if (selfIsCronus) {
            boolean paused = CronusKeys.isPaused(selfData);
            AABB scan = self.m_20191_().m_82377_(24.0, 12.0, 24.0);
            for (LivingEntity ent : mc.f_91073_.m_45976_(LivingEntity.class, scan)) {
                if (ent == self || !ent.m_6084_() || ent.m_21233_() <= 0f) {
                    continue;
                }
                if (ent.m_21223_() <= ent.m_21233_() * 0.15f) {
                    double ex = Mth.m_14139_(pt, ent.f_19854_, ent.m_20185_());
                    double ey = Mth.m_14139_(pt, ent.f_19855_, ent.m_20186_()) + ent.m_20206_() + 0.62;
                    double ez = Mth.m_14139_(pt, ent.f_19856_, ent.m_20189_());
                    renderOverheadOutBadge(src, mat, cam, ex, ey, ez, paused, clientTick + pt);
                }
            }
        }

        // 8. 高清特摄漫画风 3D 打击弹出贴图（HIT! / GREAT! / CRITICAL! / GUARD! / ITEM GET!）
        for (PopupFx p : POPUPS) {
            renderArcadePopup(src, mat, cam, p, pt);
        }

        pose.m_85849_();

        // 9. 3D 特摄序列图集演出（背部巨型变身钟表 / 7.8s 五层变身 / 头顶能量道具能力币 / 放射绿雷 / 天降落雷）
        CronusSpriteFx.renderAll(mc, src, pose, cam, pt);
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

    // ==================== 4. 头顶高清特摄「OUT!」绝版标识 ====================

    private static void renderOverheadOutBadge(MultiBufferSource.BufferSource src, Matrix4f mat, Vec3 cam,
                                               double x, double y, double z, boolean paused, float time) {
        float pulse = paused ? (0.88f + 0.12f * Mth.m_14031_(time * 0.55f)) : 0.90f;
        float bob = 0.065f * Mth.m_14031_(time * 0.18f);
        float scale = paused ? (1.06f + 0.08f * Mth.m_14031_(time * 0.45f)) : 0.92f;
        float tilt = paused ? 0.05f * Mth.m_14031_(time * 0.35f) : 0.0f;
        CronusSpriteFx.renderHitSticker(src, mat, cam, x, y + bob, z, 5,
            0.70f * scale, 0.465f * scale, tilt, pulse);
    }

    // ==================== 5. 高清特摄漫画风 3D 打击弹出贴图 ====================

    private static void renderArcadePopup(MultiBufferSource.BufferSource src, Matrix4f mat, Vec3 cam,
                                          PopupFx p, float pt) {
        float t = Mth.m_14036_((p.age + pt) / (float) p.maxAge, 0.0f, 1.0f);
        float easeOut = 1.0f - (1.0f - t) * (1.0f - t);

        // 街机漫画弹跳曲线：0..14% 瞬间重击弹出(0.28->1.26)，14..28% 回弹定格(1.26->1.00)，末段轻盈淡出
        float popScale;
        if (t < 0.14f) {
            float u = t / 0.14f;
            popScale = 0.28f + 0.98f * (1.0f - (1.0f - u) * (1.0f - u));
        } else if (t < 0.28f) {
            float u = (t - 0.14f) / 0.14f;
            popScale = 1.26f - 0.26f * u;
        } else {
            popScale = 1.00f + (t - 0.28f) * 0.08f;
        }
        float alpha = t > 0.72f ? Mth.m_14036_((1.0f - t) / 0.28f, 0.0f, 1.0f) : 0.99f;

        double px = p.x + p.driftX * easeOut;
        double py = p.y + 0.18 + easeOut * 0.62;
        double pz = p.z + p.driftZ * easeOut;

        int cellIdx;
        float baseHalfW;
        switch (p.kind) {
            case CronusFxMsg.POPUP_GREAT -> {
                cellIdx = 1;
                baseHalfW = 0.80f;
            }
            case CronusFxMsg.POPUP_CRITICAL -> {
                cellIdx = 2;
                baseHalfW = 0.98f;
            }
            case CronusFxMsg.POPUP_GUARD -> {
                cellIdx = 3;
                baseHalfW = 0.74f;
            }
            case CronusFxMsg.POPUP_ITEM_GET -> {
                cellIdx = 4;
                baseHalfW = 0.82f;
            }
            default -> {
                cellIdx = 0;
                baseHalfW = 0.64f;
            }
        }
        float halfW = baseHalfW * popScale;
        float halfH = halfW * (341.0f / 512.0f);
        CronusSpriteFx.renderHitSticker(src, mat, cam, px, py, pz, cellIdx, halfW, halfH, p.tiltRad, alpha);
    }

    // ==================== 6. 3D 立体打击冲击波、斩击光弧与闪光星芒 ====================

    private static void renderHitImpact(VertexConsumer vc, Matrix4f mat, Vec3 cam, HitImpactFx imp, float pt) {
        float t = Mth.m_14036_((imp.age + pt) / (float) imp.maxAge, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        float alpha = (1.0f - t) * (1.0f - t * 0.35f);
        if (alpha <= 0.02f) {
            return;
        }

        // 按打击等级分层配置配色、尺寸与斩击弧数量
        int mainRgb, subRgb, coreRgb;
        float ringScale;
        int spikeCount;
        switch (imp.kind) {
            case CronusFxMsg.POPUP_GREAT -> {
                mainRgb = 0xFFB703;
                subRgb = 0xFF4D2A;
                coreRgb = 0xFFF7C2;
                ringScale = 1.38f;
                spikeCount = 8;
            }
            case CronusFxMsg.POPUP_CRITICAL -> {
                mainRgb = 0xFF2E93;
                subRgb = 0x18F29A;
                coreRgb = 0xFFF4B8;
                ringScale = 1.85f;
                spikeCount = 10;
            }
            case CronusFxMsg.POPUP_GUARD -> {
                mainRgb = 0x2CE8DC;
                subRgb = 0xA8FFF9;
                coreRgb = 0xFFFFFF;
                ringScale = 1.25f;
                spikeCount = 6;
            }
            default -> {
                mainRgb = 0x35F2FF;
                subRgb = 0xFFE44D;
                coreRgb = 0xFFFFFF;
                ringScale = 1.00f;
                spikeCount = 6;
            }
        }

        BillboardBasis bb = BillboardBasis.towardsCamera(cam, imp.x, imp.y, imp.z);

        // A. 命中前 3 帧的高能十字星闪光核
        if (t < 0.38f) {
            float flashP = 1.0f - t / 0.38f;
            float starLen = (0.52f + 0.55f * (1.0f - flashP)) * ringScale;
            float starThick = 0.085f * ringScale * flashP;
            drawBillboardDiamond(vc, mat, bb, starLen, starThick, imp.slashTiltRad, coreRgb, flashP * 0.95f);
            drawBillboardDiamond(vc, mat, bb, starLen * 0.72f, starThick * 0.85f,
                imp.slashTiltRad + (float) (Math.PI * 0.5), mainRgb, flashP * 0.85f);
        }

        // B. 3D 倾斜扩散冲击波环（GUARD 为六边形能量盾波纹，其余为流畅多段圆形冲击波）
        int ringSegs = (imp.kind == CronusFxMsg.POPUP_GUARD) ? 6 : 28;
        float outR = (0.28f + 1.12f * ease) * ringScale;
        float bandW = Math.max(0.03f, (0.24f * (1.0f - t * 0.78f)) * ringScale);
        drawTiltedAddRing(vc, mat, imp.x, imp.y, imp.z,
            Math.max(0.04f, outR - bandW), outR, ringSegs,
            imp.yawRad, imp.slashTiltRad * 0.65f, mainRgb, alpha * 0.88f);

        if (imp.kind == CronusFxMsg.POPUP_GREAT || imp.kind == CronusFxMsg.POPUP_CRITICAL
            || imp.kind == CronusFxMsg.POPUP_GUARD) {
            float inOutR = (0.18f + 0.78f * ease) * ringScale;
            float inBandW = Math.max(0.02f, 0.14f * (1.0f - t * 0.80f) * ringScale);
            drawTiltedAddRing(vc, mat, imp.x, imp.y, imp.z,
                Math.max(0.02f, inOutR - inBandW), inOutR, ringSegs,
                imp.yawRad + 0.55f, -imp.slashTiltRad * 0.55f, subRgb, alpha * 0.78f);
        }

        // C. 立体月牙斩击光弧（HIT 1道，GREAT 交叉2道，CRITICAL 三重交错斩弧）
        if (imp.kind != CronusFxMsg.POPUP_GUARD) {
            float slashR = (0.55f + 0.92f * ease) * ringScale;
            float slashW = (0.22f * (1.0f - t * 0.72f)) * ringScale;
            float sweepOffset = -0.35f + ease * 0.65f;
            drawTiltedSlashArc(vc, mat, imp.x, imp.y, imp.z, slashR, slashW,
                imp.yawRad + sweepOffset, imp.slashTiltRad, 14, coreRgb, mainRgb, alpha * 0.95f);

            if (imp.kind == CronusFxMsg.POPUP_GREAT || imp.kind == CronusFxMsg.POPUP_CRITICAL) {
                drawTiltedSlashArc(vc, mat, imp.x, imp.y, imp.z, slashR * 0.92f, slashW * 0.88f,
                    imp.yawRad + 1.85f - sweepOffset * 0.8f, -imp.slashTiltRad * 0.9f, 14,
                    coreRgb, subRgb, alpha * 0.90f);
            }
            if (imp.kind == CronusFxMsg.POPUP_CRITICAL) {
                drawTiltedSlashArc(vc, mat, imp.x, imp.y, imp.z, slashR * 1.12f, slashW * 0.95f,
                    imp.yawRad + 3.65f + sweepOffset, imp.slashTiltRad * 0.25f, 14,
                    0xFFFFFF, 0xFFE066, alpha * 0.92f);
            }
        }

        // D. 放射穿刺光针（取代传统廉价溅射粒子，沿屏幕平面向外高速飞刺）
        for (int i = 0; i < spikeCount; i++) {
            float ang = imp.yawRad + (float) (Math.PI * 2.0 * i / spikeCount) + (i % 2 == 0 ? 0.14f : -0.11f);
            float dist0 = (0.20f + 0.88f * ease) * ringScale * (i % 2 == 0 ? 1.0f : 0.76f);
            float spikeLen = (0.34f * (1.0f - t * 0.65f)) * ringScale * (i % 2 == 0 ? 1.15f : 0.82f);
            float halfW = (0.042f * (1.0f - t * 0.55f)) * ringScale;
            int spRgb = (i % 2 == 0) ? coreRgb : subRgb;
            drawBillboardNeedle(vc, mat, bb, ang, dist0, dist0 + spikeLen, halfW, spRgb, alpha * 0.88f);
        }
    }

    private static void drawBillboardDiamond(VertexConsumer vc, Matrix4f mat, BillboardBasis b,
                                             float halfLen, float halfThick, float rollRad,
                                             int rgb, float alpha) {
        double c = Math.cos(rollRad), s = Math.sin(rollRad);
        double ax = b.rx * c + b.ux * s, ay = b.ry * c + b.uy * s, az = b.rz * c + b.uz * s;
        double px = b.ux * c - b.rx * s, py = b.uy * c - b.ry * s, pz = b.uz * c - b.rz * s;
        emitAdditiveQuad(vc, mat,
            (float) (b.cx - ax * halfLen), (float) (b.cy - ay * halfLen), (float) (b.cz - az * halfLen),
            (float) (b.cx + px * halfThick), (float) (b.cy + py * halfThick), (float) (b.cz + pz * halfThick),
            (float) (b.cx + ax * halfLen), (float) (b.cy + ay * halfLen), (float) (b.cz + az * halfLen),
            (float) (b.cx - px * halfThick), (float) (b.cy - py * halfThick), (float) (b.cz - pz * halfThick),
            rgb, alpha);
    }

    private static void drawBillboardNeedle(VertexConsumer vc, Matrix4f mat, BillboardBasis b,
                                            float ang, float r0, float r1, float halfW,
                                            int rgb, float alpha) {
        double c = Math.cos(ang), s = Math.sin(ang);
        double dx = b.rx * c + b.ux * s, dy = b.ry * c + b.uy * s, dz = b.rz * c + b.uz * s;
        double px = b.ux * c - b.rx * s, py = b.uy * c - b.ry * s, pz = b.uz * c - b.rz * s;
        double rm = r0 + (r1 - r0) * 0.32;
        emitAdditiveQuad(vc, mat,
            (float) (b.cx + dx * r0), (float) (b.cy + dy * r0), (float) (b.cz + dz * r0),
            (float) (b.cx + dx * rm + px * halfW), (float) (b.cy + dy * rm + py * halfW), (float) (b.cz + dz * rm + pz * halfW),
            (float) (b.cx + dx * r1), (float) (b.cy + dy * r1), (float) (b.cz + dz * r1),
            (float) (b.cx + dx * rm - px * halfW), (float) (b.cy + dy * rm - py * halfW), (float) (b.cz + dz * rm - pz * halfW),
            rgb, alpha);
    }

    private static void drawTiltedAddRing(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                                          float inR, float outR, int segs, float yawRad, float tiltRad,
                                          int rgb, float alpha) {
        double cyaw = Math.cos(yawRad), syaw = Math.sin(yawRad);
        double ct = Math.cos(tiltRad), st = Math.sin(tiltRad);
        // 平面正交基 U=(cyaw, 0, syaw), V=(-syaw*ct, st, cyaw*ct)
        double ux = cyaw, uy = 0.0, uz = syaw;
        double vx = -syaw * ct, vy = st, vz = cyaw * ct;

        for (int i = 0; i < segs; i++) {
            double a0 = (Math.PI * 2.0 * i) / segs;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double d0x = ux * c0 + vx * s0, d0y = uy * c0 + vy * s0, d0z = uz * c0 + vz * s0;
            double d1x = ux * c1 + vx * s1, d1y = uy * c1 + vy * s1, d1z = uz * c1 + vz * s1;

            emitAdditiveQuad(vc, mat,
                (float) (cx + d0x * inR), (float) (cy + d0y * inR), (float) (cz + d0z * inR),
                (float) (cx + d0x * outR), (float) (cy + d0y * outR), (float) (cz + d0z * outR),
                (float) (cx + d1x * outR), (float) (cy + d1y * outR), (float) (cz + d1z * outR),
                (float) (cx + d1x * inR), (float) (cy + d1y * inR), (float) (cz + d1z * inR),
                rgb, alpha);
        }
    }

    /** 3D 倾斜月牙斩击弧：两头收尖、中段饱满高亮，形成锐利特摄剑气/拳压弧光。 */
    private static void drawTiltedSlashArc(VertexConsumer vc, Matrix4f mat, double cx, double cy, double cz,
                                           float radius, float maxBandW, float yawRad, float tiltRad,
                                           int segs, int coreRgb, int edgeRgb, float alpha) {
        double cyaw = Math.cos(yawRad), syaw = Math.sin(yawRad);
        double ct = Math.cos(tiltRad), st = Math.sin(tiltRad);
        double ux = cyaw, uy = 0.0, uz = syaw;
        double vx = -syaw * ct, vy = st, vz = cyaw * ct;
        double arcSpan = Math.PI * 0.92;
        double startA = -arcSpan * 0.5;

        for (int i = 0; i < segs; i++) {
            float p0 = i / (float) segs;
            float p1 = (i + 1) / (float) segs;
            double a0 = startA + arcSpan * p0;
            double a1 = startA + arcSpan * p1;

            // 月牙包络：两端收尖为 0，中段厚实
            float env0 = Mth.m_14031_(p0 * (float) Math.PI);
            float env1 = Mth.m_14031_(p1 * (float) Math.PI);
            float w0 = maxBandW * env0;
            float w1 = maxBandW * env1;

            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);
            double d0x = ux * c0 + vx * s0, d0y = uy * c0 + vy * s0, d0z = uz * c0 + vz * s0;
            double d1x = ux * c1 + vx * s1, d1y = uy * c1 + vy * s1, d1z = uz * c1 + vz * s1;

            // 外缘彩色剑气层
            emitAdditiveQuad(vc, mat,
                (float) (cx + d0x * (radius - w0)), (float) (cy + d0y * (radius - w0)), (float) (cz + d0z * (radius - w0)),
                (float) (cx + d0x * (radius + w0 * 0.45f)), (float) (cy + d0y * (radius + w0 * 0.45f)), (float) (cz + d0z * (radius + w0 * 0.45f)),
                (float) (cx + d1x * (radius + w1 * 0.45f)), (float) (cy + d1y * (radius + w1 * 0.45f)), (float) (cz + d1z * (radius + w1 * 0.45f)),
                (float) (cx + d1x * (radius - w1)), (float) (cy + d1y * (radius - w1)), (float) (cz + d1z * (radius - w1)),
                edgeRgb, alpha * 0.85f);

            // 内核炽白高光脊线
            float cw0 = w0 * 0.36f, cw1 = w1 * 0.36f;
            emitAdditiveQuad(vc, mat,
                (float) (cx + d0x * (radius - cw0)), (float) (cy + d0y * (radius - cw0)), (float) (cz + d0z * (radius - cw0)),
                (float) (cx + d0x * (radius + cw0 * 0.6f)), (float) (cy + d0y * (radius + cw0 * 0.6f)), (float) (cz + d0z * (radius + cw0 * 0.6f)),
                (float) (cx + d1x * (radius + cw1 * 0.6f)), (float) (cy + d1y * (radius + cw1 * 0.6f)), (float) (cz + d1z * (radius + cw1 * 0.6f)),
                (float) (cx + d1x * (radius - cw1)), (float) (cy + d1y * (radius - cw1)), (float) (cz + d1z * (radius - cw1)),
                coreRgb, alpha);
        }
    }

    // ==================== 6b. 3D 立体双管翡翠光束与时序瞬移轨线 ====================

    private static void renderBeamLine(VertexConsumer vc, Matrix4f mat, Vec3 cam, BeamLineFx b, float pt) {
        float t = Mth.m_14036_((b.age + pt) / (float) b.maxAge, 0.0f, 1.0f);
        float alpha = (1.0f - t) * (1.0f - t * 0.25f);
        if (alpha <= 0.02f) {
            return;
        }
        double dx = b.x1 - b.x0, dy = b.y1 - b.y0, dz = b.z1 - b.z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.05) {
            return;
        }
        double dirX = dx / len, dirY = dy / len, dirZ = dz / len;
        // 构造与光束方向正交的二维基底 (u, v)
        double refX = Math.abs(dirY) > 0.9 ? 1.0 : 0.0;
        double refY = Math.abs(dirY) > 0.9 ? 0.0 : 1.0;
        double refZ = 0.0;
        double ux = dirY * refZ - dirZ * refY;
        double uy = dirZ * refX - dirX * refZ;
        double uz = dirX * refY - dirY * refX;
        double uLen = Math.max(1e-4, Math.sqrt(ux * ux + uy * uy + uz * uz));
        ux /= uLen; uy /= uLen; uz /= uLen;
        double vx = dirY * uz - dirZ * uy;
        double vy = dirZ * ux - dirX * uz;
        double vz = dirX * uy - dirY * ux;

        if (b.style == CronusFxMsg.BEAM_GUN) {
            // Gashacon 缺陷护罩Ⅱ 双管翡翠光束：上下并行两道翠青高能激光 + 白炽内芯 + 枪口/落点闪光
            float outerW = 0.058f * (1.0f - t * 0.65f);
            float coreW = 0.024f * (1.0f - t * 0.72f);
            double sep = 0.075;
            for (int k = -1; k <= 1; k += 2) {
                double ox = vx * sep * k, oy = vy * sep * k, oz = vz * sep * k;
                drawCrossBeamRibbon(vc, mat,
                    b.x0 + ox, b.y0 + oy, b.z0 + oz,
                    b.x1 + ox * 0.25, b.y1 + oy * 0.25, b.z1 + oz * 0.25,
                    ux, uy, uz, vx, vy, vz, outerW, 0x18F29A, alpha * 0.85f);
                drawCrossBeamRibbon(vc, mat,
                    b.x0 + ox, b.y0 + oy, b.z0 + oz,
                    b.x1 + ox * 0.25, b.y1 + oy * 0.25, b.z1 + oz * 0.25,
                    ux, uy, uz, vx, vy, vz, coreW, 0xE6FFFF, alpha);
            }
            // 枪口与命中终点高能十字闪光
            BillboardBasis muzzleBb = BillboardBasis.towardsCamera(cam, b.x0, b.y0, b.z0);
            drawBillboardDiamond(vc, mat, muzzleBb, 0.28f * (1.0f - t * 0.5f), 0.055f * (1.0f - t), 0.35f, 0x35F2FF, alpha * 0.9f);
            BillboardBasis endBb = BillboardBasis.towardsCamera(cam, b.x1, b.y1, b.z1);
            drawBillboardDiamond(vc, mat, endBb, 0.42f * (0.6f + t * 0.6f), 0.07f * (1.0f - t), -0.4f, 0x18F29A, alpha);
            drawBillboardDiamond(vc, mat, endBb, 0.34f * (0.6f + t * 0.6f), 0.055f * (1.0f - t), 1.15f, 0xFFFFFF, alpha);
        } else {
            // 时序瞬移金绿时空残影轨：宽幅翡翠外鞘 + 金色核心丝带 + 沿轨 3 道扩张时空光环
            float outerW = 0.16f * (1.0f - t * 0.78f);
            float coreW = 0.055f * (1.0f - t * 0.82f);
            drawCrossBeamRibbon(vc, mat, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1,
                ux, uy, uz, vx, vy, vz, outerW, 0x18F29A, alpha * 0.72f);
            drawCrossBeamRibbon(vc, mat, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1,
                ux, uy, uz, vx, vy, vz, coreW, 0xFFE066, alpha * 0.95f);

            int ringSegs = 18;
            for (int rIdx = 1; rIdx <= 3; rIdx++) {
                double frac = rIdx * 0.25;
                double rx = b.x0 + dx * frac, ry = b.y0 + dy * frac, rz = b.z0 + dz * frac;
                float rOut = 0.28f + t * 0.45f;
                float rIn = Math.max(0.05f, rOut - 0.07f * (1.0f - t * 0.6f));
                int rgb = (rIdx == 2) ? 0xFFE066 : 0x18F29A;
                for (int s = 0; s < ringSegs; s++) {
                    double a0 = (Math.PI * 2.0 * s) / ringSegs;
                    double a1 = (Math.PI * 2.0 * (s + 1)) / ringSegs;
                    double c0 = Math.cos(a0), s0 = Math.sin(a0);
                    double c1 = Math.cos(a1), s1 = Math.sin(a1);
                    double p0x = ux * c0 + vx * s0, p0y = uy * c0 + vy * s0, p0z = uz * c0 + vz * s0;
                    double p1x = ux * c1 + vx * s1, p1y = uy * c1 + vy * s1, p1z = uz * c1 + vz * s1;
                    emitAdditiveQuad(vc, mat,
                        (float) (rx + p0x * rIn), (float) (ry + p0y * rIn), (float) (rz + p0z * rIn),
                        (float) (rx + p0x * rOut), (float) (ry + p0y * rOut), (float) (rz + p0z * rOut),
                        (float) (rx + p1x * rOut), (float) (ry + p1y * rOut), (float) (rz + p1z * rOut),
                        (float) (rx + p1x * rIn), (float) (ry + p1y * rIn), (float) (rz + p1z * rIn),
                        rgb, alpha * 0.85f);
                }
            }
        }
    }

    private static void drawCrossBeamRibbon(VertexConsumer vc, Matrix4f mat,
                                            double x0, double y0, double z0,
                                            double x1, double y1, double z1,
                                            double ux, double uy, double uz,
                                            double vx, double vy, double vz,
                                            float halfW, int rgb, float alpha) {
        emitAdditiveQuad(vc, mat,
            (float) (x0 - ux * halfW), (float) (y0 - uy * halfW), (float) (z0 - uz * halfW),
            (float) (x0 + ux * halfW), (float) (y0 + uy * halfW), (float) (z0 + uz * halfW),
            (float) (x1 + ux * halfW), (float) (y1 + uy * halfW), (float) (z1 + uz * halfW),
            (float) (x1 - ux * halfW), (float) (y1 - uy * halfW), (float) (z1 - uz * halfW),
            rgb, alpha);
        emitAdditiveQuad(vc, mat,
            (float) (x0 - vx * halfW), (float) (y0 - vy * halfW), (float) (z0 - vz * halfW),
            (float) (x0 + vx * halfW), (float) (y0 + vy * halfW), (float) (z0 + vz * halfW),
            (float) (x1 + vx * halfW), (float) (y1 + vy * halfW), (float) (z1 + vz * halfW),
            (float) (x1 - vx * halfW), (float) (y1 - vy * halfW), (float) (z1 - vz * halfW),
            rgb, alpha);
    }

    private static void emitAdditiveQuad(VertexConsumer vc, Matrix4f mat,
                                         float ax, float ay, float az,
                                         float bx, float by, float bz,
                                         float cx, float cy, float cz,
                                         float dx, float dy, float dz,
                                         int rgb, float alpha) {
        float rf = ((rgb >> 16) & 0xFF) / 255.0f;
        float gf = ((rgb >> 8) & 0xFF) / 255.0f;
        float bf = (rgb & 0xFF) / 255.0f;
        float af = Mth.m_14036_(alpha, 0.0f, 1.0f);
        vc.m_252986_(mat, ax, ay, az).m_85950_(rf, gf, bf, af).m_7421_(0F, 0F).m_5752_();
        vc.m_252986_(mat, bx, by, bz).m_85950_(rf, gf, bf, af).m_7421_(1F, 0F).m_5752_();
        vc.m_252986_(mat, cx, cy, cz).m_85950_(rf, gf, bf, af).m_7421_(1F, 1F).m_5752_();
        vc.m_252986_(mat, dx, dy, dz).m_85950_(rf, gf, bf, af).m_7421_(0F, 1F).m_5752_();
    }

    // ==================== 底层纯几何图元 ====================

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
