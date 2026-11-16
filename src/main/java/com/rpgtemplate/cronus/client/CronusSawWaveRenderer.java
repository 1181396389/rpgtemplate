package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpgtemplate.cronus.CronusSawWaveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 电锯模式必杀技一：巨大绿色回旋刃形能量波（{@link CronusSawWaveEntity}）3D 渲染器：
 * 直径 3.1 格实体 12 齿弧形回旋锯刃盘 + 翠绿实心能量刀腹 + 高亮白绿切割外刃 + 金绿编年史轴环；
 * 命中敌人进入压制绞杀阶段后转速骤升 4 倍并爆发出高频金绿斩击冲击环！
 */
public final class CronusSawWaveRenderer extends EntityRenderer<CronusSawWaveEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/fx/cronus_white.png");

    public CronusSawWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CronusSawWaveEntity entity, float entityYaw, float partialTick,
                        PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        boolean pinning = entity.isPinning();
        float time = entity.tickCount + partialTick;
        float spinDeg = time * (pinning ? 118.0f : 32.0f);
        float scale = pinning ? (1.06f + 0.08f * Mth.sin(time * 1.4f)) : 1.0f;

        poseStack.pushPose();
        poseStack.translate(0.0f, 0.35f, 0.0f);
        // 略微倾斜刀盘呈现帅气的斜切回旋刃姿态
        poseStack.mulPose(Axis.XP.rotationDegrees(pinning ? 18.0f : 14.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDeg));
        poseStack.scale(scale, scale, scale);

        Matrix4f mat = poseStack.last().pose();

        // 1) 实色半透明翠绿回旋锯腹与 12 枚弧形锯齿刀锋（确保白天黑夜都有极强金属光刃质感）
        VertexConsumer solid = buffers.getBuffer(CronusRenderTypes.translucent(WHITE));
        drawAnnulus(solid, mat, 0.42f, 1.18f, 0.0f, 0.04f, 0.85f, 0.36f, 0.68f, 0.72f, 1.0f, 0.80f, 0.90f);
        drawSawTeeth(solid, mat, 12, 1.14f, 1.62f, 0.0f,
            0.22f, 1.0f, 0.52f, 0.92f, 0.90f, 1.0f, 0.94f, 0.96f);

        // 2) 加色发光双层立体厚度刀刃（上下各偏移 0.08 格形成厚重 3D 能量刃盘）
        VertexConsumer add = buffers.getBuffer(CronusRenderTypes.additive(WHITE));
        drawSawTeeth(add, mat, 12, 1.05f, 1.76f, 0.06f,
            0.12f, 1.0f, 0.48f, 0.82f, 0.82f, 1.0f, 0.86f, 0.88f);
        drawSawTeeth(add, mat, 12, 1.05f, 1.76f, -0.06f,
            0.12f, 1.0f, 0.48f, 0.82f, 0.82f, 1.0f, 0.86f, 0.88f);

        // 外缘高速旋转虚影光环
        drawAnnulus(add, mat, 1.12f, 1.88f, 0.0f,
            0.15f, 1.0f, 0.55f, pinning ? 0.75f : 0.50f,
            0.05f, 0.85f, 0.36f, 0.04f);

        // 中心 Cronus 金绿时钟轴环
        drawAnnulus(add, mat, 0.16f, 0.48f, 0.02f,
            pinning ? 1.0f : 0.78f, 1.0f, 0.48f, 0.92f,
            0.20f, 1.0f, 0.50f, 0.55f);

        // 压制高频绞杀阶段：追加垂直十字撕裂刃光与外扩激波环
        if (pinning) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(76.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(-spinDeg * 0.7f));
            drawAnnulus(add, poseStack.last().pose(), 0.75f, 1.65f, 0.0f,
                0.85f, 1.0f, 0.55f, 0.65f, 0.10f, 0.95f, 0.42f, 0.04f);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void drawAnnulus(VertexConsumer vc, Matrix4f mat,
                                    float rIn, float rOut, float y,
                                    float ri, float gi, float bi, float ai,
                                    float ro, float go, float bo, float ao) {
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            vc.addVertex(mat, c0 * rIn, y, s0 * rIn).setColor(ri, gi, bi, ai).setUv(0f, 0f);
            vc.addVertex(mat, c0 * rOut, y, s0 * rOut).setColor(ro, go, bo, ao).setUv(1f, 0f);
            vc.addVertex(mat, c1 * rOut, y, s1 * rOut).setColor(ro, go, bo, ao).setUv(1f, 1f);
            vc.addVertex(mat, c1 * rIn, y, s1 * rIn).setColor(ri, gi, bi, ai).setUv(0f, 1f);
        }
    }

    /** 绘制 12 枚向前弯钩的回旋刃锯齿（旋转时形成极具侵略性的电锯回旋镖轮廓）。 */
    private static void drawSawTeeth(VertexConsumer vc, Matrix4f mat, int teeth,
                                     float rBase, float rTip, float y,
                                     float rb, float gb, float bb, float ab,
                                     float rt, float gt, float bt, float at) {
        float step = (float) (Math.PI * 2.0 / teeth);
        for (int i = 0; i < teeth; i++) {
            float a0 = i * step;
            float aMid = a0 + step * 0.42f;
            float aTip = a0 + step * 0.88f; // 锯齿尖端前掠呈弯钩回旋刃形
            float a1 = a0 + step * 0.68f;

            float x0 = Mth.cos(a0) * rBase, z0 = Mth.sin(a0) * rBase;
            float x1 = Mth.cos(a1) * rBase, z1 = Mth.sin(a1) * rBase;
            float xt = Mth.cos(aTip) * rTip, zt = Mth.sin(aTip) * rTip;
            float xm = Mth.cos(aMid) * (rBase + (rTip - rBase) * 0.68f);
            float zm = Mth.sin(aMid) * (rBase + (rTip - rBase) * 0.68f);

            vc.addVertex(mat, x0, y, z0).setColor(rb, gb, bb, ab).setUv(0f, 0f);
            vc.addVertex(mat, x1, y, z1).setColor(rb, gb, bb, ab).setUv(1f, 0f);
            vc.addVertex(mat, xt, y, zt).setColor(rt, gt, bt, at).setUv(1f, 1f);
            vc.addVertex(mat, xm, y, zm).setColor(rt, gt, bt, at * 0.9f).setUv(0f, 1f);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CronusSawWaveEntity entity) {
        return WHITE;
    }
}
