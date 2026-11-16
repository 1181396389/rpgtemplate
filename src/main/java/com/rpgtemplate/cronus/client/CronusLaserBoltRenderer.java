package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.cronus.CronusLaserBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 光线枪模式普攻真实激光弹（{@link CronusLaserBoltEntity}）3D 渲染器：
 * 实心白绿激光核 + 交叉翡翠辉光剑脊 + 双螺旋脉冲环，白天黑夜皆高对比锐利可见。
 */
public final class CronusLaserBoltRenderer extends EntityRenderer<CronusLaserBoltEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/fx/cronus_white.png");

    public CronusLaserBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(CronusLaserBoltEntity entity, float entityYaw, float partialTick,
                        PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Vec3 vel = entity.getDeltaMovement();
        Vec3 dir;
        if (vel.lengthSqr() > 1e-6) {
            dir = vel.normalize();
        } else {
            float yRad = entity.getYRot() * ((float) Math.PI / 180.0f);
            float xRad = entity.getXRot() * ((float) Math.PI / 180.0f);
            float cx = Mth.cos(xRad);
            dir = new Vec3(Mth.sin(yRad) * cx, Mth.sin(xRad), Mth.cos(yRad) * cx);
        }

        Vector3f fwd = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        Vector3f refUp = Math.abs(fwd.y) < 0.92f ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f right = new Vector3f(fwd).cross(refUp).normalize();
        Vector3f up = new Vector3f(right).cross(fwd).normalize();

        float len = 2.65f;
        float t = (entity.tickCount + partialTick) * 0.85f;

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        // 1) 实色半透明翠白内芯（确保在亮天空/白昼背景下依旧极高对比度）
        VertexConsumer solid = buffers.getBuffer(CronusRenderTypes.translucent(WHITE));
        drawBeamCross(solid, mat, fwd, right, up, -len * 0.72f, len * 0.28f, 0.068f,
            0.78f, 1.0f, 0.88f, 0.95f, 0.10f, 0.95f, 0.45f, 0.20f);

        // 2) 加色发光翡翠外鞘 + 旋转双星棱面
        VertexConsumer add = buffers.getBuffer(CronusRenderTypes.additive(WHITE));
        drawBeamCross(add, mat, fwd, right, up, -len * 0.85f, len * 0.36f, 0.165f,
            0.12f, 1.0f, 0.48f, 0.88f, 0.05f, 0.72f, 0.34f, 0.05f);

        // 倾斜 45° 旋转十字辉光增强 3D 体积感
        float cs = Mth.cos(t);
        float sn = Mth.sin(t);
        Vector3f rRot = new Vector3f(right).mul(cs).add(new Vector3f(up).mul(sn));
        Vector3f uRot = new Vector3f(up).mul(cs).sub(new Vector3f(right).mul(sn));
        drawBeamCross(add, mat, fwd, rRot, uRot, -len * 0.55f, len * 0.30f, 0.22f,
            0.35f, 1.0f, 0.65f, 0.55f, 0.05f, 0.65f, 0.28f, 0.02f);

        // 弹头微环光晕
        drawRing(add, mat, fwd, right, up, len * 0.05f, 0.09f, 0.22f, 0.70f, 1.0f, 0.78f, 0.75f);

        poseStack.popPose();
    }

    private static void drawBeamCross(VertexConsumer vc, Matrix4f mat,
                                      Vector3f fwd, Vector3f right, Vector3f up,
                                      float z0, float z1, float halfW,
                                      float r1, float g1, float b1, float a1,
                                      float r0, float g0, float b0, float a0) {
        emitQuad(vc, mat, fwd, right, z0, z1, halfW, r0, g0, b0, a0, r1, g1, b1, a1);
        emitQuad(vc, mat, fwd, up, z0, z1, halfW, r0, g0, b0, a0, r1, g1, b1, a1);
    }

    private static void emitQuad(VertexConsumer vc, Matrix4f mat,
                                 Vector3f fwd, Vector3f side,
                                 float z0, float z1, float w,
                                 float r0, float g0, float b0, float a0,
                                 float r1, float g1, float b1, float a1) {
        float x0a = fwd.x * z0 - side.x * w * 0.45f;
        float y0a = fwd.y * z0 - side.y * w * 0.45f;
        float z0a = fwd.z * z0 - side.z * w * 0.45f;

        float x0b = fwd.x * z0 + side.x * w * 0.45f;
        float y0b = fwd.y * z0 + side.y * w * 0.45f;
        float z0b = fwd.z * z0 + side.z * w * 0.45f;

        float x1b = fwd.x * z1 + side.x * w;
        float y1b = fwd.y * z1 + side.y * w;
        float z1b = fwd.z * z1 + side.z * w;

        float x1a = fwd.x * z1 - side.x * w;
        float y1a = fwd.y * z1 - side.y * w;
        float z1a = fwd.z * z1 - side.z * w;

        vc.addVertex(mat, x0a, y0a, z0a).setColor(r0, g0, b0, a0).setUv(0f, 0f);
        vc.addVertex(mat, x0b, y0b, z0b).setColor(r0, g0, b0, a0).setUv(1f, 0f);
        vc.addVertex(mat, x1b, y1b, z1b).setColor(r1, g1, b1, a1).setUv(1f, 1f);
        vc.addVertex(mat, x1a, y1a, z1a).setColor(r1, g1, b1, a1).setUv(0f, 1f);
    }

    private static void drawRing(VertexConsumer vc, Matrix4f mat,
                                 Vector3f fwd, Vector3f right, Vector3f up,
                                 float z, float rIn, float rOut,
                                 float r, float g, float b, float a) {
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segs);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

            float ix0 = fwd.x * z + (right.x * c0 + up.x * s0) * rIn;
            float iy0 = fwd.y * z + (right.y * c0 + up.y * s0) * rIn;
            float iz0 = fwd.z * z + (right.z * c0 + up.z * s0) * rIn;

            float ox0 = fwd.x * z + (right.x * c0 + up.x * s0) * rOut;
            float oy0 = fwd.y * z + (right.y * c0 + up.y * s0) * rOut;
            float oz0 = fwd.z * z + (right.z * c0 + up.z * s0) * rOut;

            float ox1 = fwd.x * z + (right.x * c1 + up.x * s1) * rOut;
            float oy1 = fwd.y * z + (right.y * c1 + up.y * s1) * rOut;
            float oz1 = fwd.z * z + (right.z * c1 + up.z * s1) * rOut;

            float ix1 = fwd.x * z + (right.x * c1 + up.x * s1) * rIn;
            float iy1 = fwd.y * z + (right.y * c1 + up.y * s1) * rIn;
            float iz1 = fwd.z * z + (right.z * c1 + up.z * s1) * rIn;

            vc.addVertex(mat, ix0, iy0, iz0).setColor(r, g, b, a).setUv(0f, 0f);
            vc.addVertex(mat, ox0, oy0, oz0).setColor(r, g, b, 0.05f).setUv(1f, 0f);
            vc.addVertex(mat, ox1, oy1, oz1).setColor(r, g, b, 0.05f).setUv(1f, 1f);
            vc.addVertex(mat, ix1, iy1, iz1).setColor(r, g, b, a).setUv(0f, 1f);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CronusLaserBoltEntity entity) {
        return WHITE;
    }
}
