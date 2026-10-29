package com.rpgtemplate.nothingthere.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.nothingthere.HolleSpike;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Holle 骨刺弹射物的渲染器。模型来自 Blockbench 模型 holle_spike（见 {@link HolleSpikeModel}）：
 * 尖端位于实体位置，主体沿 -X 向后延伸；根部暗红，骨白主体带三根倒刺。
 * 朝向由实体的 yaw/pitch 决定，因此骨刺始终指向飞行方向。
 * 颜色写在顶点上，贴图使用纯白，所以不依赖外部纹理文件。
 */
public final class HolleSpikeRenderer extends EntityRenderer<HolleSpike> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final float[] DARK_RED = {0.361F, 0.063F, 0.125F};   // #5C1020 (holle_spike_root)
    private static final float[] BONE = {0.929F, 0.902F, 0.839F};      // #EDE6D6 (holle_spike_bone)

    /** 每个顶点 11 个 float：x y z r g b nx ny nz u v。 */
    private static float[] MESH;
    private static int MESH_VERTS;

    public HolleSpikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation m_5478_(HolleSpike entity) {
        return WHITE;
    }

    @Override
    public void m_7392_(HolleSpike entity, float yaw, float partialTick, PoseStack pose,
                        MultiBufferSource buffers, int light) {
        if (MESH == null) {
            buildMesh();
        }
        pose.m_85836_();
        // 投射物的朝向约定与原版箭矢相同（Projectile.shoot：yRot = atan2(x, z)，xRot = atan2(y, 水平距离)）。
        // 与 ArrowRenderer 一致：先绕 Y 转 (yRot - 90°)，再绕 Z 转 xRot，使模型的 +X（尖端方向）指向飞行方向。
        pose.m_252781_(new Quaternionf().rotationY((float) Math.toRadians(entity.m_146908_() - 90.0F)));
        pose.m_252781_(new Quaternionf().rotationZ((float) Math.toRadians(entity.m_146909_())));
        VertexConsumer vc = buffers.m_6299_(RenderType.m_110458_(WHITE));
        Matrix4f m = pose.m_85850_().m_252922_();
        Matrix3f nm = pose.m_85850_().m_252943_();
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();
        for (int v = 0; v < MESH_VERTS; v++) {
            int i = v * 11;
            m.transformPosition(MESH[i], MESH[i + 1], MESH[i + 2], p);
            nm.transform(MESH[i + 6], MESH[i + 7], MESH[i + 8], n);
            n.normalize();
            vc.m_5954_(p.x, p.y, p.z, MESH[i + 3], MESH[i + 4], MESH[i + 5], 1.0F,
                MESH[i + 9], MESH[i + 10], OverlayTexture.f_118083_, light, n.x, n.y, n.z);
        }
        pose.m_85849_();
        super.m_7392_(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * 由模型四边形生成顶点数组。每个四边形按平面法线着色，并额外生成一个反向的背面，
     * 使骨刺从任何方向都可见（不依赖渲染类型的剔除设置）。
     */
    private static void buildMesh() {
        float[] pos = HolleSpikeModel.POS;
        int[] quads = HolleSpikeModel.QUADS;
        int quadCount = quads.length / 4;
        MESH_VERTS = quadCount * 4 * 2;
        MESH = new float[MESH_VERTS * 11];
        int o = 0;
        for (int q = 0; q < quadCount; q++) {
            float[] col = q < HolleSpikeModel.ROOT_QUADS ? DARK_RED : BONE;
            int a = quads[q * 4], b = quads[q * 4 + 1], c = quads[q * 4 + 2], d = quads[q * 4 + 3];
            // 平面法线：取两条对角线的叉积，对重复顶点的退化四边形退化为 (a,b,c) 三角形
            float[] n = faceNormal(pos, a, b, c, d);
            // 正面
            o = putVertex(o, pos, a, col, n, 0);
            o = putVertex(o, pos, b, col, n, 0);
            o = putVertex(o, pos, c, col, n, 0);
            o = putVertex(o, pos, d, col, n, 0);
            // 背面（顶点顺序反向，法线取反）
            o = putVertex(o, pos, d, col, n, 1);
            o = putVertex(o, pos, c, col, n, 1);
            o = putVertex(o, pos, b, col, n, 1);
            o = putVertex(o, pos, a, col, n, 1);
        }
    }

    private static int putVertex(int o, float[] pos, int idx, float[] col, float[] n, int back) {
        float s = back == 1 ? -1.0F : 1.0F;
        MESH[o] = pos[idx * 3];
        MESH[o + 1] = pos[idx * 3 + 1];
        MESH[o + 2] = pos[idx * 3 + 2];
        MESH[o + 3] = col[0];
        MESH[o + 4] = col[1];
        MESH[o + 5] = col[2];
        MESH[o + 6] = s * n[0];
        MESH[o + 7] = s * n[1];
        MESH[o + 8] = s * n[2];
        MESH[o + 9] = 0.5F;
        MESH[o + 10] = 0.5F;
        return o + 11;
    }

    private static float[] faceNormal(float[] pos, int a, int b, int c, int d) {
        // 用 (c - a) x (d - b) 得到四边形法线；若退化则回退到 (b - a) x (c - a)
        float[] ac = sub(pos, c, a);
        float[] bd = sub(pos, d, b);
        float[] n = cross(ac, bd);
        if (length(n) < 1e-9F) {
            n = cross(sub(pos, b, a), sub(pos, c, a));
        }
        if (length(n) < 1e-9F) {
            return new float[]{0.0F, 1.0F, 0.0F};
        }
        n = normalise(n);
        // 让法线指向远离骨刺中心线的一侧（否则正面光照会反）
        float cx = (pos[a * 3] + pos[b * 3] + pos[c * 3] + pos[d * 3]) / 4.0F;
        float cy = (pos[a * 3 + 1] + pos[b * 3 + 1] + pos[c * 3 + 1] + pos[d * 3 + 1]) / 4.0F;
        float cz = (pos[a * 3 + 2] + pos[b * 3 + 2] + pos[c * 3 + 2] + pos[d * 3 + 2]) / 4.0F;
        float t = Math.max(0.0F, Math.min(1.0F, (cx + HolleSpikeModel.LENGTH) / HolleSpikeModel.LENGTH));
        float axisY = HolleSpikeModel.AXIS_DROP * t * t - HolleSpikeModel.AXIS_DROP;
        float[] out = {cx, cy - axisY, cz};
        if (n[0] * out[0] + n[1] * out[1] + n[2] * out[2] < 0.0F) {
            n = new float[]{-n[0], -n[1], -n[2]};
        }
        return n;
    }

    private static float[] sub(float[] pos, int i, int j) {
        return new float[]{pos[i * 3] - pos[j * 3], pos[i * 3 + 1] - pos[j * 3 + 1], pos[i * 3 + 2] - pos[j * 3 + 2]};
    }

    private static float[] cross(float[] u, float[] v) {
        return new float[]{u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static float[] normalise(float[] v) {
        float l = length(v);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
