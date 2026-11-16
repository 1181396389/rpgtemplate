package com.rpgtemplate.forge.ext.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public final class FireGeom {
    public static final float MODE_COLUMN = 0.0F;
    public static final float MODE_SPHERE = 0.5F;
    public static final float MODE_RING = 1.0F;
    private static boolean view;
    private static float vx;
    private static float vy;
    private static float vz;

    private FireGeom() {
    }

    public static void setView(float var0, float var1, float var2) {
        view = true;
        vx = var0;
        vy = var1;
        vz = var2;
    }

    public static void clearView() {
        view = false;
    }

    private static float rim(float var0, float var1, float var2, float var3, float var4, float var5) {
        if (!view) {
            return 1.0F;
        } else {
            float var6 = vx - var0;
            float var7 = vy - var1;
            float var8 = vz - var2;
            float var9 = (float)Math.sqrt((double)(var6 * var6 + var7 * var7 + var8 * var8));
            if (var9 < 1.0E-4F) {
                return 1.0F;
            } else {
                float var10 = Math.abs((var3 * var6 + var4 * var7 + var5 * var8) / var9);
                return smooth(0.05F, 0.65F, var10);
            }
        }
    }

    private static void v(
        VertexConsumer var0, Matrix4f var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10
    ) {
        var0.addVertex(var1, var2, var3, var4).setColor(clamp(var5), clamp(var6), clamp(var7), clamp(var8)).setUv(var9, var10);
    }

    private static float clamp(float var0) {
        return var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
    }

    public static void cylinder(
        VertexConsumer var0,
        Matrix4f var1,
        float var2,
        float var3,
        float var4,
        float var5,
        int var6,
        int var7,
        float var8,
        float var9,
        float var10,
        float var11
    ) {
        for (int var12 = 0; var12 < var7; var12++) {
            float var13 = (float)var12 / (float)var7;
            float var14 = (float)(var12 + 1) / (float)var7;
            float var15 = var2 + (var3 - var2) * var13;
            float var16 = var2 + (var3 - var2) * var14;
            float var17 = var4 + (var5 - var4) * var13;
            float var18 = var4 + (var5 - var4) * var14;
            float var19 = var10 + (var11 - var10) * var13;
            float var20 = var10 + (var11 - var10) * var14;

            for (int var21 = 0; var21 < var6; var21++) {
                float var22 = (float)var21 / (float)var6;
                float var23 = (float)(var21 + 1) / (float)var6;
                double var24 = (double)var22 * Math.PI * 2.0;
                double var26 = (double)var23 * Math.PI * 2.0;
                float var28 = (float)Math.cos(var24);
                float var29 = (float)Math.sin(var24);
                float var30 = (float)Math.cos(var26);
                float var31 = (float)Math.sin(var26);
                v(
                    var0,
                    var1,
                    var28 * var17,
                    var15,
                    var29 * var17,
                    var8,
                    var9,
                    0.0F,
                    var19 * rim(var28 * var17, var15, var29 * var17, var28, 0.0F, var29),
                    var22,
                    var13
                );
                v(
                    var0,
                    var1,
                    var30 * var17,
                    var15,
                    var31 * var17,
                    var8,
                    var9,
                    0.0F,
                    var19 * rim(var30 * var17, var15, var31 * var17, var30, 0.0F, var31),
                    var23,
                    var13
                );
                v(
                    var0,
                    var1,
                    var30 * var18,
                    var16,
                    var31 * var18,
                    var8,
                    var9,
                    0.0F,
                    var20 * rim(var30 * var18, var16, var31 * var18, var30, 0.0F, var31),
                    var23,
                    var14
                );
                v(
                    var0,
                    var1,
                    var28 * var18,
                    var16,
                    var29 * var18,
                    var8,
                    var9,
                    0.0F,
                    var20 * rim(var28 * var18, var16, var29 * var18, var28, 0.0F, var29),
                    var22,
                    var14
                );
            }
        }
    }

    public static void sphere(VertexConsumer var0, Matrix4f var1, float var2, int var3, int var4, float var5, float var6, float var7) {
        for (int var8 = 0; var8 < var4; var8++) {
            float var9 = (float)var8 / (float)var4;
            float var10 = (float)(var8 + 1) / (float)var4;
            double var11 = (-Math.PI / 2) + (double)var9 * Math.PI;
            double var13 = (-Math.PI / 2) + (double)var10 * Math.PI;
            float var15 = (float)Math.sin(var11) * var2;
            float var16 = (float)Math.sin(var13) * var2;
            float var17 = (float)Math.cos(var11) * var2;
            float var18 = (float)Math.cos(var13) * var2;

            for (int var19 = 0; var19 < var3; var19++) {
                float var20 = (float)var19 / (float)var3;
                float var21 = (float)(var19 + 1) / (float)var3;
                float var22 = (float)Math.cos((double)var20 * Math.PI * 2.0);
                float var23 = (float)Math.sin((double)var20 * Math.PI * 2.0);
                float var24 = (float)Math.cos((double)var21 * Math.PI * 2.0);
                float var25 = (float)Math.sin((double)var21 * Math.PI * 2.0);
                float var26 = 1.0F / Math.max(1.0E-4F, var2);
                v(
                    var0,
                    var1,
                    var22 * var17,
                    var15,
                    var23 * var17,
                    var5,
                    var6,
                    0.5F,
                    var7 * rim(var22 * var17, var15, var23 * var17, var22 * var17 * var26, var15 * var26, var23 * var17 * var26),
                    var20,
                    var9
                );
                v(
                    var0,
                    var1,
                    var24 * var17,
                    var15,
                    var25 * var17,
                    var5,
                    var6,
                    0.5F,
                    var7 * rim(var24 * var17, var15, var25 * var17, var24 * var17 * var26, var15 * var26, var25 * var17 * var26),
                    var21,
                    var9
                );
                v(
                    var0,
                    var1,
                    var24 * var18,
                    var16,
                    var25 * var18,
                    var5,
                    var6,
                    0.5F,
                    var7 * rim(var24 * var18, var16, var25 * var18, var24 * var18 * var26, var16 * var26, var25 * var18 * var26),
                    var21,
                    var10
                );
                v(
                    var0,
                    var1,
                    var22 * var18,
                    var16,
                    var23 * var18,
                    var5,
                    var6,
                    0.5F,
                    var7 * rim(var22 * var18, var16, var23 * var18, var22 * var18 * var26, var16 * var26, var23 * var18 * var26),
                    var20,
                    var10
                );
            }
        }
    }

    public static void ring(VertexConsumer var0, Matrix4f var1, float var2, float var3, float var4, int var5, float var6, float var7, float var8) {
        for (int var9 = 0; var9 < var5; var9++) {
            float var10 = (float)var9 / (float)var5;
            float var11 = (float)(var9 + 1) / (float)var5;
            float var12 = (float)Math.cos((double)var10 * Math.PI * 2.0);
            float var13 = (float)Math.sin((double)var10 * Math.PI * 2.0);
            float var14 = (float)Math.cos((double)var11 * Math.PI * 2.0);
            float var15 = (float)Math.sin((double)var11 * Math.PI * 2.0);
            v(var0, var1, var12 * var3, var2, var13 * var3, var6, var7, 1.0F, var8, var10, 0.0F);
            v(var0, var1, var14 * var3, var2, var15 * var3, var6, var7, 1.0F, var8, var11, 0.0F);
            v(var0, var1, var14 * var4, var2, var15 * var4, var6, var7, 1.0F, var8, var11, 1.0F);
            v(var0, var1, var12 * var4, var2, var13 * var4, var6, var7, 1.0F, var8, var10, 1.0F);
        }
    }

    public static float easeOut(float var0) {
        var0 = var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
        float var1 = 1.0F - var0;
        return 1.0F - var1 * var1 * var1;
    }

    public static float smooth(float var0, float var1, float var2) {
        float var3 = (var2 - var0) / (var1 - var0);
        var3 = var3 < 0.0F ? 0.0F : (var3 > 1.0F ? 1.0F : var3);
        return var3 * var3 * (3.0F - 2.0F * var3);
    }
}
