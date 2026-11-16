package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpgtemplate.cronus.CronusEnergyOrbEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 光线枪模式必杀技二：巨大绿色追踪爆炸弹丸（{@link CronusEnergyOrbEntity}）3D 体积球渲染器：
 * 直径约 1.5 格巨型翠绿等离子核 + 双层呼吸光晕壳 + 交叉旋转陀螺能量环 + 尾焰拖尾；
 * 时停极速追踪模式下融入炽金高能脉动，视觉辨识度拉满。
 */
public final class CronusEnergyOrbRenderer extends EntityRenderer<CronusEnergyOrbEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("rpgtemplate", "textures/fx/cronus_white.png");

    public CronusEnergyOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void m_7392_(CronusEnergyOrbEntity entity, float entityYaw, float partialTick,
                        PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        boolean fast = entity.isFastMode();
        float time = entity.f_19797_ + partialTick;
        float pulse = 1.0f + 0.08f * Mth.m_14031_(time * (fast ? 0.85f : 0.45f));
        float baseRadius = (fast ? 0.78f : 0.72f) * pulse;

        poseStack.m_85836_();
        // 球体居中到碰撞箱中心
        poseStack.m_252880_(0.0f, 0.65f, 0.0f);

        // 1) 实色翠白高能内球（确保任何天空亮度下都是巨大的实感绿色弹丸）
        VertexConsumer solid = buffers.m_6299_(CronusRenderTypes.translucent(WHITE));
        Matrix4f mat = poseStack.m_85850_().m_252922_();
        drawSphere(solid, mat, baseRadius * 0.62f, 10, 14,
            fast ? 0.82f : 0.68f, 1.0f, fast ? 0.62f : 0.78f, 0.94f);
        drawSphere(solid, mat, baseRadius * 0.80f, 10, 14,
            0.08f, 0.92f, 0.36f, 0.52f);

        // 2) 外层加色霓虹翡翠等离子发光壳
        VertexConsumer add = buffers.m_6299_(CronusRenderTypes.additive(WHITE));
        drawSphere(add, mat, baseRadius * 1.02f, 10, 14,
            fast ? 0.36f : 0.10f, 1.0f, fast ? 0.42f : 0.50f, 0.42f);
        drawSphere(add, mat, baseRadius * 1.24f, 8, 12,
            0.06f, 0.88f, 0.38f, 0.18f);

        // 3) 彗星能量尾焰（沿反速度方向拉出绿色等离子拖尾）
        Vec3 vel = entity.m_20184_();
        if (vel.m_82556_() > 1e-5) {
            Vec3 back = vel.m_82541_().m_82490_(-1.0);
            Vector3f bVec = new Vector3f((float) back.f_82479_, (float) back.f_82480_, (float) back.f_82481_);
            Vector3f refUp = Math.abs(bVec.y) < 0.92f ? new Vector3f(0f, 1f, 0f) : new Vector3f(1f, 0f, 0f);
            Vector3f right = new Vector3f(bVec).cross(refUp).normalize();
            Vector3f up = new Vector3f(right).cross(bVec).normalize();
            float tailLen = fast ? 3.2f : 1.9f;
            drawTailCone(add, mat, bVec, right, up, baseRadius * 0.68f, tailLen,
                fast ? 0.55f : 0.15f, 1.0f, fast ? 0.42f : 0.52f, 0.72f);
        }

        // 4) 双道高速反向旋转的翡翠/赤金能量陀螺光环
        float spin = time * (fast ? 18.0f : 9.0f);
        poseStack.m_85836_();
        poseStack.m_252781_(Axis.f_252436_.m_252977_(spin));
        poseStack.m_252781_(Axis.f_252529_.m_252977_(36.0f));
        drawGyroRing(add, poseStack.m_85850_().m_252922_(), baseRadius * 1.12f, baseRadius * 1.32f,
            fast ? 1.0f : 0.20f, 1.0f, fast ? 0.45f : 0.62f, 0.80f);
        poseStack.m_85849_();

        poseStack.m_85836_();
        poseStack.m_252781_(Axis.f_252436_.m_252977_(-spin * 1.25f));
        poseStack.m_252781_(Axis.f_252403_.m_252977_(52.0f));
        drawGyroRing(add, poseStack.m_85850_().m_252922_(), baseRadius * 1.22f, baseRadius * 1.44f,
            0.12f, 1.0f, 0.52f, 0.72f);
        poseStack.m_85849_();

        poseStack.m_85849_();
    }

    private static void drawSphere(VertexConsumer vc, Matrix4f mat, float radius, int stacks, int slices,
                                   float r, float g, float b, float a) {
        for (int i = 0; i < stacks; i++) {
            float phi0 = (float) (-Math.PI * 0.5 + Math.PI * i / stacks);
            float phi1 = (float) (-Math.PI * 0.5 + Math.PI * (i + 1) / stacks);
            float y0 = Mth.m_14031_(phi0) * radius;
            float r0 = Mth.m_14089_(phi0) * radius;
            float y1 = Mth.m_14031_(phi1) * radius;
            float r1 = Mth.m_14089_(phi1) * radius;

            for (int j = 0; j < slices; j++) {
                float th0 = (float) (Math.PI * 2.0 * j / slices);
                float th1 = (float) (Math.PI * 2.0 * (j + 1) / slices);
                float c0 = Mth.m_14089_(th0), s0 = Mth.m_14031_(th0);
                float c1 = Mth.m_14089_(th1), s1 = Mth.m_14031_(th1);

                vc.m_252986_(mat, r0 * c0, y0, r0 * s0).m_85950_(r, g, b, a).m_7421_(0f, 0f).m_5752_();
                vc.m_252986_(mat, r0 * c1, y0, r0 * s1).m_85950_(r, g, b, a).m_7421_(1f, 0f).m_5752_();
                vc.m_252986_(mat, r1 * c1, y1, r1 * s1).m_85950_(r, g, b, a).m_7421_(1f, 1f).m_5752_();
                vc.m_252986_(mat, r1 * c0, y1, r1 * s0).m_85950_(r, g, b, a).m_7421_(0f, 1f).m_5752_();
            }
        }
    }

    private static void drawGyroRing(VertexConsumer vc, Matrix4f mat, float rIn, float rOut,
                                     float r, float g, float b, float a) {
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segs);
            float c0 = Mth.m_14089_(a0), s0 = Mth.m_14031_(a0);
            float c1 = Mth.m_14089_(a1), s1 = Mth.m_14031_(a1);

            vc.m_252986_(mat, c0 * rIn, 0f, s0 * rIn).m_85950_(r, g, b, a).m_7421_(0f, 0f).m_5752_();
            vc.m_252986_(mat, c0 * rOut, 0f, s0 * rOut).m_85950_(r, g, b, 0.05f).m_7421_(1f, 0f).m_5752_();
            vc.m_252986_(mat, c1 * rOut, 0f, s1 * rOut).m_85950_(r, g, b, 0.05f).m_7421_(1f, 1f).m_5752_();
            vc.m_252986_(mat, c1 * rIn, 0f, s1 * rIn).m_85950_(r, g, b, a).m_7421_(0f, 1f).m_5752_();
        }
    }

    private static void drawTailCone(VertexConsumer vc, Matrix4f mat, Vector3f back, Vector3f right, Vector3f up,
                                     float startW, float tailLen, float r, float g, float b, float a) {
        emitTailPlane(vc, mat, back, right, startW, tailLen, r, g, b, a);
        emitTailPlane(vc, mat, back, up, startW, tailLen, r, g, b, a);
    }

    private static void emitTailPlane(VertexConsumer vc, Matrix4f mat, Vector3f back, Vector3f side,
                                      float w, float len, float r, float g, float b, float a) {
        float ex = back.x * len, ey = back.y * len, ez = back.z * len;
        vc.m_252986_(mat, -side.x * w, -side.y * w, -side.z * w).m_85950_(r, g, b, a).m_7421_(0f, 0f).m_5752_();
        vc.m_252986_(mat, side.x * w, side.y * w, side.z * w).m_85950_(r, g, b, a).m_7421_(1f, 0f).m_5752_();
        vc.m_252986_(mat, ex + side.x * 0.05f, ey + side.y * 0.05f, ez + side.z * 0.05f).m_85950_(r, g, b, 0.0f).m_7421_(1f, 1f).m_5752_();
        vc.m_252986_(mat, ex - side.x * 0.05f, ey - side.y * 0.05f, ez - side.z * 0.05f).m_85950_(r, g, b, 0.0f).m_7421_(0f, 1f).m_5752_();
    }

    @Override
    public ResourceLocation m_5478_(CronusEnergyOrbEntity entity) {
        return WHITE;
    }
}
