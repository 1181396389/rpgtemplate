package com.rpgtemplate.forge.mask.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.cronus.client.CronusRenderTypes;
import com.rpgtemplate.forge.mask.PerceptionBlockingMaskItem;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 认知阻碍面具 3D 渲染层（外观基于《废墟图书馆》罗兰同款纯黑全脸面具，零原版粒子）：
 * <ul>
 *   <li><b>所有视角（含自身第三人称与背包预览）</b>：紧贴头部绘制纯黑立体轮廓面具，完全覆盖脸部并带两侧哑光束带。</li>
 *   <li><b>其他玩家视角（即便准星正对看见后）</b>：额外在脸部前方与两侧叠加多层双线性平滑黑雾认知模糊滤层，
 *       呈现《废墟图书馆》中「黑色遮盖并且模糊」的认知扭曲观感。</li>
 * </ul>
 */
public final class PerceptionMaskLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation FACE_TEX =
        ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/perception_blocking_mask_face.png");
    private static final ResourceLocation BLUR_TEX =
        ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/perception_blocking_mask_blur.png");

    public PerceptionMaskLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static void attach(PlayerRenderer renderer) {
        try {
            Method add = LivingEntityRenderer.class.getDeclaredMethod("addLayer", RenderLayer.class);
            add.setAccessible(true);
            add.invoke(renderer, new PerceptionMaskLayer(renderer));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to attach PerceptionMaskLayer", e);
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                        float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                        float netHeadYaw, float headPitch) {
        if (!PerceptionBlockingMaskItem.isWearingMask(player)) {
            return;
        }
        pose.pushPose();
        // 严格跟随头部旋转、低头、抬头、歪头姿态
        this.getParentModel().head.translateAndRotate(pose);
        Matrix4f mat = pose.last().pose();

        // 1. 纯黑罗兰同款立体面具主体（覆盖整张脸并高于二层皮肤/帽层，无 Z-fighting）
        RenderType faceRt = CronusRenderTypes.translucent(FACE_TEX);
        VertexConsumer fvc = buffers.getBuffer(faceRt);

        float xIn = 4.22f / 16.0f;
        float xOut = 4.62f / 16.0f;
        float yTop = -8.28f / 16.0f;
        float yBot = 0.22f / 16.0f;
        float zFront = -4.64f / 16.0f;
        float zCheek = -2.35f / 16.0f;
        float zBack = 4.60f / 16.0f;

        // 正面纯黑光滑面具主面板（略带曲面厚重感，纯深黑不受高亮环境光泛白）
        emitQuad(fvc, mat,
            -xIn, yTop, zFront, 0.0f, 0.0f,
             xIn, yTop, zFront, 1.0f, 0.0f,
             xIn, yBot, zFront, 1.0f, 1.0f,
            -xIn, yBot, zFront, 0.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 0.99f);

        // 左/右曲面颊翼包边，从侧面也严密遮覆脸颊边缘
        emitQuad(fvc, mat,
            -xOut, yTop + 0.02f, zCheek, 0.02f, 0.12f,
            -xIn,  yTop,         zFront, 0.12f, 0.10f,
            -xIn,  yBot,         zFront, 0.12f, 0.90f,
            -xOut, yBot - 0.02f, zCheek, 0.02f, 0.88f,
            0.78f, 0.78f, 0.84f, 0.96f);
        emitQuad(fvc, mat,
             xIn,  yTop,         zFront, 0.88f, 0.10f,
             xOut, yTop + 0.02f, zCheek, 0.98f, 0.12f,
             xOut, yBot - 0.02f, zCheek, 0.98f, 0.88f,
             xIn,  yBot,         zFront, 0.88f, 0.90f,
            0.78f, 0.78f, 0.84f, 0.96f);

        // 额头与下颌微收边，令轮廓圆润贴合罗兰全脸面具造型
        emitQuad(fvc, mat,
            -xIn, yTop - 0.015f, -3.55f / 16.0f, 0.15f, 0.02f,
             xIn, yTop - 0.015f, -3.55f / 16.0f, 0.85f, 0.02f,
             xIn, yTop,           zFront,        0.85f, 0.10f,
            -xIn, yTop,           zFront,        0.15f, 0.10f,
            0.72f, 0.72f, 0.78f, 0.94f);
        emitQuad(fvc, mat,
            -xIn, yBot,           zFront,        0.15f, 0.90f,
             xIn, yBot,           zFront,        0.85f, 0.90f,
             xIn, yBot + 0.015f, -3.55f / 16.0f, 0.85f, 0.98f,
            -xIn, yBot + 0.015f, -3.55f / 16.0f, 0.15f, 0.98f,
            0.65f, 0.65f, 0.72f, 0.94f);

        // 两侧与后脑纯黑固定束带（取纹理纯黑中心采样点）
        float strapTop = -4.75f / 16.0f;
        float strapBot = -3.65f / 16.0f;
        emitQuad(fvc, mat,
            -xOut, strapTop, zBack,  0.48f, 0.48f,
            -xOut, strapTop, zCheek, 0.52f, 0.48f,
            -xOut, strapBot, zCheek, 0.52f, 0.52f,
            -xOut, strapBot, zBack,  0.48f, 0.52f,
            0.70f, 0.70f, 0.76f, 0.95f);
        emitQuad(fvc, mat,
             xOut, strapTop, zCheek, 0.48f, 0.48f,
             xOut, strapTop, zBack,  0.52f, 0.48f,
             xOut, strapBot, zBack,  0.52f, 0.52f,
             xOut, strapBot, zCheek, 0.48f, 0.52f,
            0.70f, 0.70f, 0.76f, 0.95f);
        emitQuad(fvc, mat,
             xOut, strapTop, zBack,  0.48f, 0.48f,
            -xOut, strapTop, zBack,  0.52f, 0.48f,
            -xOut, strapBot, zBack,  0.52f, 0.52f,
             xOut, strapBot, zBack,  0.48f, 0.52f,
            0.65f, 0.65f, 0.70f, 0.95f);

        if (buffers instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(faceRt);
        }

        // 2. 其他玩家视角专属：「黑色遮盖并且模糊」多层认知滤网黑雾扭曲
        Minecraft mc = Minecraft.getInstance();
        boolean viewedByOther = mc.player != null && player != mc.player;
        if (viewedByOther) {
            RenderType blurRt = CronusRenderTypes.translucent(BLUR_TEX);
            VertexConsumer bvc = buffers.getBuffer(blurRt);
            double t = ageInTicks * 0.14;

            // 中心浓黑模糊核心层（紧贴脸部正前）
            drawBlurPlane(bvc, mat,
                0.0f, -4.05f / 16.0f, -4.92f / 16.0f,
                5.35f / 16.0f, 5.15f / 16.0f, 0.96f);

            // 中层：微幅高频水平认知抖动黑模糊，令五官边缘不断模糊失焦
            float jx1 = (float) (Math.sin(t * 9.7) * 0.018 + Math.cos(t * 17.3) * 0.010);
            float jy1 = (float) (Math.cos(t * 8.1) * 0.012);
            drawBlurPlane(bvc, mat,
                jx1, -4.05f / 16.0f + jy1, -5.36f / 16.0f,
                5.85f / 16.0f, 5.55f / 16.0f, 0.84f);

            // 外层：反向漂移柔焦黑雾光晕，彻底模糊整个面部与前额/下颌轮廓
            float jx2 = (float) (-Math.cos(t * 11.3 + 1.1) * 0.024);
            float jy2 = (float) (Math.sin(t * 6.9 + 2.0) * 0.016);
            drawBlurPlane(bvc, mat,
                jx2, -4.05f / 16.0f + jy2, -5.82f / 16.0f,
                6.35f / 16.0f, 6.05f / 16.0f, 0.68f);

            // 左右侧颊模糊遮罩（确保 3/4 侧视角度也看不到脸部细节，全是一团黑色模糊）
            float sx = 4.92f / 16.0f;
            emitQuad(bvc, mat,
                -sx, -9.0f / 16.0f, -0.5f / 16.0f, 0.0f, 0.0f,
                -sx, -9.0f / 16.0f, -6.0f / 16.0f, 1.0f, 0.0f,
                -sx,  0.9f / 16.0f, -6.0f / 16.0f, 1.0f, 1.0f,
                -sx,  0.9f / 16.0f, -0.5f / 16.0f, 0.0f, 1.0f,
                0.95f, 0.95f, 1.0f, 0.72f);
            emitQuad(bvc, mat,
                 sx, -9.0f / 16.0f, -6.0f / 16.0f, 0.0f, 0.0f,
                 sx, -9.0f / 16.0f, -0.5f / 16.0f, 1.0f, 0.0f,
                 sx,  0.9f / 16.0f, -0.5f / 16.0f, 1.0f, 1.0f,
                 sx,  0.9f / 16.0f, -6.0f / 16.0f, 0.0f, 1.0f,
                0.95f, 0.95f, 1.0f, 0.72f);
        }

        pose.popPose();
    }

    private static void drawBlurPlane(VertexConsumer vc, Matrix4f mat,
                                      float cx, float cy, float z,
                                      float halfW, float halfH, float alpha) {
        emitQuad(vc, mat,
            cx - halfW, cy - halfH, z, 0.0f, 0.0f,
            cx + halfW, cy - halfH, z, 1.0f, 0.0f,
            cx + halfW, cy + halfH, z, 1.0f, 1.0f,
            cx - halfW, cy + halfH, z, 0.0f, 1.0f,
            1.0f, 1.0f, 1.0f, alpha);
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat,
                                 float ax, float ay, float az, float au, float av,
                                 float bx, float by, float bz, float bu, float bv,
                                 float cx, float cy, float cz, float cu, float cv,
                                 float dx, float dy, float dz, float du, float dv,
                                 float r, float g, float b, float a) {
        vc.addVertex(mat, ax, ay, az).setColor(r, g, b, a).setUv(au, av);
        vc.addVertex(mat, bx, by, bz).setColor(r, g, b, a).setUv(bu, bv);
        vc.addVertex(mat, cx, cy, cz).setColor(r, g, b, a).setUv(cu, cv);
        vc.addVertex(mat, dx, dy, dz).setColor(r, g, b, a).setUv(du, dv);
    }
}
