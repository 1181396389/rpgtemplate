package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rpgtemplate.forge.ext.WorldRiftEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class WorldRiftRenderer extends EntityRenderer<WorldRiftEntity> {
    private static final float CR = 0.92F;
    private static final float CG = 0.1F;
    private static final float CB = 0.16F;

    public WorldRiftRenderer(Context var1) {
        super(var1);
        this.shadowRadius = 0.0F;
    }

    public boolean shouldRender(WorldRiftEntity var1, Frustum var2, double var3, double var5, double var7) {
        return var2.isVisible(var1.getBoundingBoxForCulling());
    }

    public ResourceLocation getTextureLocation(WorldRiftEntity var1) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public void render(WorldRiftEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        float var7 = (float)var1.tickCount + var3;
        int var8 = Math.max(2, var1.life());
        if (!(var7 >= (float)var8)) {
            float var9 = Math.max(1.0F, var1.travelTicks());
            float var10 = var1.range();
            float var11 = Math.min(var10, var7 * var1.speed());
            float var12 = Math.max(1.0F, (float)var8 - var9);
            float var13 = clamp01((var7 - var9) / var12);
            float var14 = (float)Math.pow((double)(1.0F - smooth(var13)), 1.4);
            if (!(var14 <= 0.004F) && !(var11 <= 0.05F)) {
                float var15 = Math.max(0.3F, Math.min(1.0F, var1.power()));
                Vec3 var16 = var1.forward().normalize();
                Vec3 var17 = var1.side();
                var17 = var17.subtract(var16.scale(var17.dot(var16))).normalize();
                Vec3 var18 = var16.cross(var17).normalize();
                Vector3f var19 = new Vector3f((float)var16.x, (float)var16.y, (float)var16.z);
                Vector3f var20 = new Vector3f((float)var17.x, (float)var17.y, (float)var17.z);
                Vector3f var21 = new Vector3f((float)var18.x, (float)var18.y, (float)var18.z);
                Vec3 var22 = this.entityRenderDispatcher.camera.getPosition();
                Vector3f var23 = new Vector3f((float)(var22.x - var1.getX()), (float)(var22.y - var1.getY()), (float)(var22.z - var1.getZ()));
                float var24 = new Vector3f(var23).sub(new Vector3f(var19).mul(var11)).length();
                int var25 = var24 > 220.0F ? 10 : (var24 > 120.0F ? 18 : 32);
                var4.pushPose();
                Matrix4f var26 = var4.last().pose();
                float var27 = 1.0F - 0.85F * smooth(var13);
                VertexConsumer var28 = var5.getBuffer(RiftRenderTypes.voidSheet());
                sheet(var28, var26, var19, var20, var21, var1, var11, var25, var27, 0.0F, 0.02F, 0.0F, 0.03F, 0.78F * var14 * var15, true);
                VertexConsumer var29 = var5.getBuffer(RiftRenderTypes.glow());
                float var30 = Math.min(15.0F, Math.max(1.0F, var1.thickness()));
                int var31 = var30 > 2.0F && var24 < 120.0F ? 3 : 1;

                for (int var32 = 0; var32 < var31; var32++) {
                    float var33 = var31 == 1 ? 0.0F : -var30 * 0.5F + var30 * (float)var32 / (float)(var31 - 1);
                    float var34 = var31 == 1 ? 1.0F : (var32 != 0 && var32 != var31 - 1 ? 0.25F : 0.55F);
                    sheet(var29, var26, var19, var20, var21, var1, var11, var25, var27, var33, 0.8F, 0.85F, 0.92F, 0.12F * var14 * var34, false);
                }

                float var63 = 0.1F + 0.06F * var15;
                int var64 = Math.max(6, var25 / 2);

                for (int var65 = 0; var65 < var64; var65++) {
                    float var35 = var11 * (float)var65 / (float)var64;
                    float var36 = var11 * (float)(var65 + 1) / (float)var64;
                    float var37 = frontGlow(var11 - var35) * var14;
                    float var38 = frontGlow(var11 - var36) * var14;
                    Vector3f var39 = mul(var19, var35);
                    Vector3f var40 = mul(var19, var36);
                    ribbon(var29, var26, var39, var40, var23, var63 * 5.0F, 0.8F, 0.85F, 0.92F, var37 * 0.55F, var38 * 0.55F);
                    ribbon(var29, var26, var39, var40, var23, var63, 1.0F, 0.95F, 0.95F, var37, var38);
                }

                for (byte var66 = -1; var66 <= 1; var66 += 2) {
                    for (int var68 = 0; var68 < var64; var68++) {
                        float var71 = var11 * (float)var68 / (float)var64;
                        float var75 = var11 * (float)(var68 + 1) / (float)var64;
                        Vector3f var79 = add(mul(var19, var71), mul(var20, (float)var66 * var1.halfWidth(var71) * var27));
                        Vector3f var83 = add(mul(var19, var75), mul(var20, (float)var66 * var1.halfWidth(var75) * var27));
                        float var87 = frontGlow(var11 - var71) * var14 * 0.6F;
                        float var41 = frontGlow(var11 - var75) * var14 * 0.6F;
                        ribbon(var29, var26, var79, var83, var23, 0.45F, 0.8F, 0.85F, 0.92F, var87, var41);
                        ribbon(var29, var26, var79, var83, var23, 0.07F, 1.0F, 0.85F, 0.85F, var87, var41);
                    }
                }

                float var67 = (var7 < var9 ? 1.0F : 1.0F - smooth(clamp01(var13 * 3.0F))) * var15;
                if (var67 > 0.01F) {
                    float var69 = var1.halfWidth(var11);
                    int var72 = var24 > 120.0F ? 8 : 16;

                    for (int var76 = 0; var76 < var72; var76++) {
                        float var80 = -var69 + 2.0F * var69 * (float)var76 / (float)var72;
                        float var84 = -var69 + 2.0F * var69 * (float)(var76 + 1) / (float)var72;
                        float var88 = edgeFalloff(var80 / var69);
                        float var91 = edgeFalloff(var84 / var69);
                        float var42 = 0.06F * var69 * (1.0F - var80 / var69 * (var80 / var69));
                        float var43 = 0.06F * var69 * (1.0F - var84 / var69 * (var84 / var69));
                        Vector3f var44 = add(mul(var19, var11 + var42), mul(var20, var80));
                        Vector3f var45 = add(mul(var19, var11 + var43), mul(var20, var84));
                        ribbon(var29, var26, var44, var45, var23, 0.8F + var30 * 0.12F, 0.8F, 0.85F, 0.92F, 0.55F * var67 * var88, 0.55F * var67 * var91);
                        ribbon(var29, var26, var44, var45, var23, 0.45F, 1.0F, 1.0F, 1.0F, var67 * var88, var67 * var91);
                    }
                }

                float var70 = var7 * Math.max(1.0F, var1.speed()) * 0.82F - 6.0F;
                if (var70 > 0.5F && var70 < var11 - 0.5F) {
                    float var73 = var1.halfWidth(var70) * var27;
                    float var77 = 0.55F * var14 * frontGlow(var11 - var70);
                    if (var73 > 0.05F && var77 > 0.01F) {
                        int var81 = var24 > 120.0F ? 6 : 12;

                        for (int var85 = 0; var85 < var81; var85++) {
                            float var89 = -var73 + 2.0F * var73 * (float)var85 / (float)var81;
                            float var92 = -var73 + 2.0F * var73 * (float)(var85 + 1) / (float)var81;
                            float var94 = edgeFalloff(var89 / var73);
                            float var96 = edgeFalloff(var92 / var73);
                            Vector3f var98 = add(mul(var19, var70), mul(var20, var89));
                            Vector3f var100 = add(mul(var19, var70), mul(var20, var92));
                            ribbon(var29, var26, var98, var100, var23, 1.4F, 0.8F, 0.85F, 0.92F, var77 * var94, var77 * var96);
                            ribbon(var29, var26, var98, var100, var23, 0.28F, 1.0F, 1.0F, 1.0F, var77 * var94, var77 * var96);
                        }
                    }
                }

                RandomSource var74 = RandomSource.create((long)var1.getId() * 341873128712L + 132897987541L);
                int var78 = var24 > 220.0F ? 4 : (var24 > 120.0F ? 8 : 14);

                for (int var82 = 0; var82 < var78; var82++) {
                    float var86 = (0.06F + 0.9F * var74.nextFloat()) * var10;
                    float var90 = (var74.nextFloat() * 2.0F - 1.0F) * 0.85F;
                    boolean var93 = var74.nextBoolean();
                    float var95 = 1.5F + var74.nextFloat() * (var93 ? 4.5F : 7.0F);
                    float var97 = var74.nextBoolean() ? 1.0F : -1.0F;
                    byte var99 = 5;
                    float[] var101 = new float[var99 + 1];
                    float[] var46 = new float[var99 + 1];

                    for (int var47 = 0; var47 <= var99; var47++) {
                        var101[var47] = (var74.nextFloat() - 0.5F) * 0.9F;
                        var46[var47] = (var74.nextFloat() - 0.5F) * 0.9F;
                    }

                    if (!(var86 > var11)) {
                        float var102 = (var11 - var86) / Math.max(0.5F, var1.speed());
                        float var48 = clamp01(var102 / 2.0F);
                        float var49 = 0.88F + 0.12F * (float)Math.sin((double)(var7 * 0.45F + (float)var82 * 2.3F));
                        float var50 = var48 * var14 * var49 * 0.9F;
                        if (!(var50 <= 0.01F)) {
                            Vector3f var51 = add(mul(var19, var86), mul(var20, var90 * var1.halfWidth(var86) * var27));
                            Vector3f var52 = var93 ? mul(var21, var97) : mul(var20, var97);
                            Vector3f var53 = var93 ? var20 : var19;
                            Vector3f var54 = var93 ? var19 : var21;
                            Vector3f var55 = var51;
                            float var56 = clamp01(var102 / 4.0F);

                            for (int var57 = 1; var57 <= var99; var57++) {
                                float var58 = var95 * var56 * (float)var57 / (float)var99;
                                Vector3f var59 = add(add(var51, mul(var52, var58)), add(mul(var53, var101[var57] * 0.6F), mul(var54, var46[var57] * 0.3F)));
                                float var60 = var50 * (1.0F - (float)(var57 - 1) / (float)var99);
                                float var61 = var50 * (1.0F - (float)var57 / (float)var99);
                                ribbon(var29, var26, var55, var59, var23, 0.22F, 0.8F, 0.85F, 0.92F, var60 * 0.6F, var61 * 0.6F);
                                ribbon(var29, var26, var55, var59, var23, 0.05F, 1.0F, 0.95F, 0.95F, var60, var61);
                                var55 = var59;
                            }
                        }
                    }
                }

                var4.popPose();
                super.render(var1, var2, var3, var4, var5, var6);
            }
        }
    }

    private static void sheet(
        VertexConsumer var0,
        Matrix4f var1,
        Vector3f var2,
        Vector3f var3,
        Vector3f var4,
        WorldRiftEntity var5,
        float var6,
        int var7,
        float var8,
        float var9,
        float var10,
        float var11,
        float var12,
        float var13,
        boolean var14
    ) {
        if (!(var13 <= 0.003F)) {
            for (int var15 = 0; var15 < var7; var15++) {
                float var16 = var6 * (float)var15 / (float)var7;
                float var17 = var6 * (float)(var15 + 1) / (float)var7;
                float var18 = var5.halfWidth(var16) * var8;
                float var19 = var5.halfWidth(var17) * var8;
                float var20 = var14 ? 1.0F : 0.35F + 0.65F * frontGlow(var6 - var16);
                float var21 = var14 ? 1.0F : 0.35F + 0.65F * frontGlow(var6 - var17);
                float var22 = var13 * var20;
                float var23 = var13 * var21;
                Vector3f var24 = add(mul(var2, var16), mul(var4, var9));
                Vector3f var25 = add(mul(var2, var17), mul(var4, var9));

                for (byte var26 = -1; var26 <= 1; var26 += 2) {
                    Vector3f var27 = add(var24, mul(var3, (float)var26 * var18 * 0.6F));
                    Vector3f var28 = add(var25, mul(var3, (float)var26 * var19 * 0.6F));
                    Vector3f var29 = add(var24, mul(var3, (float)var26 * var18));
                    Vector3f var30 = add(var25, mul(var3, (float)var26 * var19));
                    quad(var0, var1, var24, var22, var27, var22 * 0.85F, var28, var23 * 0.85F, var25, var23, var10, var11, var12);
                    quad(var0, var1, var27, var22 * 0.85F, var29, 0.0F, var30, 0.0F, var28, var23 * 0.85F, var10, var11, var12);
                }
            }
        }
    }

    private static void ribbon(
        VertexConsumer var0,
        Matrix4f var1,
        Vector3f var2,
        Vector3f var3,
        Vector3f var4,
        float var5,
        float var6,
        float var7,
        float var8,
        float var9,
        float var10
    ) {
        if (!(var9 <= 0.003F) || !(var10 <= 0.003F)) {
            Vector3f var11 = new Vector3f(var3).sub(var2);
            Vector3f var12 = new Vector3f(var2).add(var3).mul(0.5F);
            Vector3f var13 = new Vector3f(var12).sub(var4);
            Vector3f var14 = new Vector3f(var11).cross(var13);
            float var15 = var14.length();
            if (!(var15 < 1.0E-5F)) {
                var14.mul(var5 / var15);
                Vector3f var16 = new Vector3f(var2).add(var14);
                Vector3f var17 = new Vector3f(var2).sub(var14);
                Vector3f var18 = new Vector3f(var3).add(var14);
                Vector3f var19 = new Vector3f(var3).sub(var14);
                quad(var0, var1, var2, var9, var16, 0.0F, var18, 0.0F, var3, var10, var6, var7, var8);
                quad(var0, var1, var2, var9, var17, 0.0F, var19, 0.0F, var3, var10, var6, var7, var8);
            }
        }
    }

    private static void quad(
        VertexConsumer var0,
        Matrix4f var1,
        Vector3f var2,
        float var3,
        Vector3f var4,
        float var5,
        Vector3f var6,
        float var7,
        Vector3f var8,
        float var9,
        float var10,
        float var11,
        float var12
    ) {
        var0.addVertex(var1, var2.x, var2.y, var2.z).setColor(var10, var11, var12, clamp01(var3));
        var0.addVertex(var1, var4.x, var4.y, var4.z).setColor(var10, var11, var12, clamp01(var5));
        var0.addVertex(var1, var6.x, var6.y, var6.z).setColor(var10, var11, var12, clamp01(var7));
        var0.addVertex(var1, var8.x, var8.y, var8.z).setColor(var10, var11, var12, clamp01(var9));
    }

    private static float frontGlow(float var0) {
        return 0.35F + 0.65F * (float)Math.exp((double)(-Math.max(0.0F, var0) / 14.0F));
    }

    private static float edgeFalloff(float var0) {
        float var1 = Math.abs(var0);
        return 1.0F - var1 * var1 * var1;
    }

    private static Vector3f mul(Vector3f var0, float var1) {
        return new Vector3f(var0).mul(var1);
    }

    private static Vector3f add(Vector3f var0, Vector3f var1) {
        return new Vector3f(var0).add(var1);
    }

    private static float clamp01(float var0) {
        return var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
    }

    private static float smooth(float var0) {
        float var1 = clamp01(var0);
        return var1 * var1 * (3.0F - 2.0F * var1);
    }
}
