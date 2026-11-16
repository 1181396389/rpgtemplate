package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpgtemplate.forge.ext.FugaArrowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class FugaArrowRenderer extends EntityRenderer<FugaArrowEntity> {
    public FugaArrowRenderer(Context var1) {
        super(var1);
        this.shadowRadius = 0.0F;
    }

    public boolean shouldRender(FugaArrowEntity var1, Frustum var2, double var3, double var5, double var7) {
        return true;
    }

    public void render(FugaArrowEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        boolean var7 = var1.state() == 0;
        float var8 = var7 ? var1.charge() : 1.0F;
        boolean var9 = var1.open();
        double var10 = 0.0;
        double var12 = 0.0;
        double var14 = 0.0;
        float var16;
        float var17;
        if (var7) {
            Entity var18 = var1.level().getEntity(var1.ownerId());
            if (var18 == null) {
                return;
            }

            Vec3 var19 = FugaArrowEntity.handPos(var18, var3);
            Minecraft var20 = Minecraft.getInstance();
            if (var18 == var20.getCameraEntity() && var20.options.getCameraType().isFirstPerson()) {
                Vec3 var21 = var18.getViewVector(var3);
                Vec3 var22 = var21.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                var19 = var18.getEyePosition(var3).add(var21.scale(1.1)).add(var22.scale(0.35)).add(0.0, -0.35, 0.0);
            }

            var10 = var19.x - Mth.lerp((double)var3, var1.xo, var1.getX());
            var12 = var19.y - Mth.lerp((double)var3, var1.yo, var1.getY());
            var14 = var19.z - Mth.lerp((double)var3, var1.zo, var1.getZ());
            var16 = var18.getViewYRot(var3);
            var17 = var18.getViewXRot(var3);
        } else {
            var16 = Mth.rotLerp(var3, var1.yRotO, var1.getYRot());
            var17 = Mth.lerp(var3, var1.xRotO, var1.getXRot());
        }

        float var27 = (float)var1.tickCount + var3;
        float var28 = (float)var1.getId() * 0.137F % 1.0F;
        float var29 = (0.3F + 0.7F * var8) * (var9 ? 1.55F : 1.0F);
        float var30 = 1.0F + 0.06F * Mth.sin(var27 * 1.7F);
        VertexConsumer var31 = var5.getBuffer(ExtRenderTypes.fire());
        var4.pushPose();
        var4.translate(var10, var12, var14);
        var4.mulPose(Axis.YP.rotationDegrees(-var16));
        var4.mulPose(Axis.XP.rotationDegrees(var17 + 90.0F));
        Matrix4f var23 = var4.last().pose();
        float var24 = (var7 ? 1.5F : 2.0F) * var29;
        FireGeom.cylinder(var31, var23, -var24 * 0.6F, var24 * 0.42F, 0.055F * var29, 0.055F * var29, 10, 3, 1.0F, var28, 0.95F, 0.95F);
        FireGeom.cylinder(var31, var23, var24 * 0.42F, var24 * 0.78F, 0.17F * var29, 0.0F, 12, 2, 1.0F, var28, 0.95F, 0.8F);
        FireGeom.cylinder(var31, var23, var24 * 0.55F, -var24 * 0.95F, 0.1F * var29 * var30, 0.3F * var29 * var30, 16, 6, 0.75F, var28, 0.85F, 0.15F);
        FireGeom.cylinder(var31, var23, var24 * 0.3F, -var24 * 0.7F, 0.18F * var29, 0.42F * var29 * var30, 16, 5, 0.5F, var28 + 0.3F, 0.45F, 0.0F);
        FireGeom.sphere(var31, var23, 0.32F * var29 * var30, 14, 8, 0.9F, var28, 0.55F + 0.35F * var8);
        if (!var7) {
            float var25 = 3.5F * var29;
            FireGeom.cylinder(var31, var23, -var24 * 0.4F, -var24 * 0.4F - var25, 0.22F * var29, 0.03F * var29, 14, 6, 0.65F, var28 + 0.5F, 0.8F, 0.0F);
        }

        if (var9) {
            for (int var32 = 0; var32 < 3; var32++) {
                float var26 = (var27 * 0.12F + (float)var32 / 3.0F) % 1.0F * var24 * 1.4F - var24 * 0.8F;
                var4.pushPose();
                var4.mulPose(Axis.YP.rotationDegrees(var27 * 25.0F + (float)var32 * 120.0F));
                FireGeom.ring(var31, var4.last().pose(), var26, 0.32F * var29, 0.5F * var29, 20, 1.0F, var28 + (float)var32 * 0.2F, 0.8F);
                var4.popPose();
            }
        }

        var4.popPose();
    }

    public ResourceLocation getTextureLocation(FugaArrowEntity var1) {
        return ExtRenderTypes.GLOW;
    }
}
