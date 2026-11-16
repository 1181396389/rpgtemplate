package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpgtemplate.forge.ext.FugaBlastEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class FugaBlastRenderer extends EntityRenderer<FugaBlastEntity> {
    public FugaBlastRenderer(Context var1) {
        super(var1);
        this.shadowRadius = 0.0F;
    }

    public boolean shouldRender(FugaBlastEntity var1, Frustum var2, double var3, double var5, double var7) {
        return var2.isVisible(var1.getBoundingBoxForCulling());
    }

    public void render(FugaBlastEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        float var7 = (float)var1.tickCount + var3;
        int var8 = Math.max(1, var1.life());
        float var9 = var7 / (float)var8;
        if (!(var9 >= 1.0F)) {
            float var10 = var1.radius();
            float var11 = var1.height();
            boolean var12 = var1.open();
            float var13 = (float)var1.getId() * 0.173F % 1.0F;
            VertexConsumer var14 = var5.getBuffer(ExtRenderTypes.fire());
            Vec3 var15 = this.entityRenderDispatcher.camera.getPosition();
            float var16 = (float)(var15.x - var1.getX());
            float var17 = (float)(var15.y - var1.getY());
            float var18 = (float)(var15.z - var1.getZ());
            FireGeom.setView(var16, var17, var18);
            var4.pushPose();
            Matrix4f var19 = var4.last().pose();
            int var20 = var10 > 20.0F ? 40 : 28;
            float var21 = 1.0F - FireGeom.smooth(0.2F, 0.55F, var9);
            if (var21 > 0.01F) {
                float var22 = var10 * FireGeom.easeOut(var7 / 7.0F) * (var12 ? 1.1F : 1.0F);
                FireGeom.sphere(var14, var19, var22, var20, var20 / 2, 0.75F, var13, var21 * 0.9F);
                FireGeom.sphere(var14, var19, var22 * 0.7F, var20, var20 / 2, 0.95F, var13 + 0.4F, var21);
                if (var12) {
                    FireGeom.sphere(var14, var19, var22 * 0.4F, 20, 10, 1.0F, var13 + 0.7F, var21);
                }
            }

            float var30 = var7 / 14.0F;
            if (var30 < 1.0F) {
                float var23 = var10 * 2.4F * FireGeom.easeOut(var30);
                FireGeom.ring(var14, var19, 0.25F, var23 * 0.82F, var23, 64, 0.8F, var13, (1.0F - var30) * 0.9F);
            }

            if (var12) {
                float var31 = (var7 - 6.0F) / 18.0F;
                if (var31 > 0.0F && var31 < 1.0F) {
                    float var24 = var10 * 3.2F * FireGeom.easeOut(var31);
                    FireGeom.ring(var14, var19, 0.4F, var24 * 0.88F, var24, 72, 1.0F, var13 + 0.5F, (1.0F - var31) * 0.9F);
                }
            }

            float var32 = FireGeom.easeOut((var7 - 2.0F) / 14.0F);
            float var33 = 1.0F - FireGeom.smooth(0.65F, 1.0F, var9);
            if (var32 > 0.0F && var33 > 0.01F) {
                float var25 = var11 * var32;
                float var26 = var10 * (var12 ? 0.5F : 0.45F) * (0.35F + 0.65F * FireGeom.easeOut(var7 / 6.0F));
                FireGeom.cylinder(var14, var19, 0.0F, var10 * 0.9F, var10 * 0.95F, var26, var20, 4, 0.6F, var13, 0.75F * var33, 0.25F * var33);
                FireGeom.cylinder(var14, var19, 0.0F, var25, var26 * 1.15F, var26 * 0.8F, var20, 10, 0.55F, var13 + 0.1F, 0.7F * var33, 0.2F * var33);
                FireGeom.cylinder(var14, var19, 0.0F, var25 * 1.03F, var26 * 0.8F, var26 * 0.55F, var20, 10, 0.8F, var13 + 0.2F, 0.85F * var33, 0.35F * var33);
                FireGeom.cylinder(var14, var19, 0.0F, var25 * 1.06F, var26 * 0.38F, var26 * 0.28F, 20, 10, 1.0F, var13 + 0.3F, 0.95F * var33, 0.6F * var33);
                var4.pushPose();
                var4.translate(0.0, (double)var25, 0.0);
                var4.scale(1.0F, 0.55F, 1.0F);
                FireGeom.setView(var16, (var17 - var25) / 0.55F, var18);
                FireGeom.sphere(var14, var4.last().pose(), var26 * 1.7F, var20, var20 / 2, 0.65F, var13 + 0.6F, 0.7F * var33);
                var4.popPose();
                FireGeom.setView(var16, var17, var18);
                if (var12) {
                    for (int var27 = 0; var27 < 6; var27++) {
                        float var28 = (var7 * 0.9F + (float)var27 * var11 / 6.0F) % var11 * var32;
                        float var29 = 1.0F - var28 / Math.max(1.0F, var11) * 0.3F;
                        var4.pushPose();
                        var4.mulPose(Axis.YP.rotationDegrees(var7 * 6.0F + (float)var27 * 60.0F));
                        FireGeom.ring(
                            var14,
                            var4.last().pose(),
                            var28,
                            var26 * 1.25F * var29,
                            var26 * 1.6F * var29,
                            40,
                            1.0F,
                            var13 + (float)var27 * 0.11F,
                            0.75F * var33
                        );
                        var4.popPose();
                    }
                }
            }

            var4.popPose();
            FireGeom.clearView();
        }
    }

    public ResourceLocation getTextureLocation(FugaBlastEntity var1) {
        return ExtRenderTypes.GLOW;
    }
}
