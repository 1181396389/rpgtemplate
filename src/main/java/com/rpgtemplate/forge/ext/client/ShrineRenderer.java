package com.rpgtemplate.forge.ext.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpgtemplate.forge.ext.ShrineEntity;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ShrineRenderer extends EntityRenderer<ShrineEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/shrine_grain.png");
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "models/shrine/malevolent_shrine.json");
    private static List<float[]> faces;
    private static List<float[]> distantFaces;
    private static final float[][] UVS = new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}};
    private static float modelHeight = 18.0F;

    public ShrineRenderer(Context var1) {
        super(var1);
        this.shadowRadius = 0.0F;
    }

    private static List<float[]> faces() {
        if (faces != null) {
            return faces;
        } else {
            ArrayList<float[]> var0 = new ArrayList<>();

            try {
                Resource var1 = (Resource)Minecraft.getInstance().getResourceManager().getResource(MODEL).orElseThrow();

                try (InputStream var12 = var1.open()) {
                    JsonObject var14 = JsonParser.parseReader(new InputStreamReader(var12, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (var14.has("height")) {
                        modelHeight = var14.get("height").getAsFloat();
                    }

                    for (JsonElement var5 : var14.getAsJsonArray("boxes")) {
                        addBox(var0, var5.getAsJsonObject());
                    }
                }
            } catch (Exception var10) {
                LogManager.getLogger("rpgtemplate").error("Failed to load shrine model", var10);
                JsonObject var2 = new JsonObject();
                JsonArray var3 = new JsonArray();
                var3.add(-3);
                var3.add(0);
                var3.add(-3);
                JsonArray var4 = new JsonArray();
                var4.add(3);
                var4.add(8);
                var4.add(3);
                var2.add("f", var3);
                var2.add("t", var4);
                var2.addProperty("c", "7a1010");
                addBox(var0, var2);
            }

            faces = var0;
            distantFaces = new ArrayList<>();

            for (float[] var13 : var0) {
                double var15 = 0.0;
                double var17 = 0.0;

                for (int var7 = 0; var7 < 3; var7++) {
                    var15 += Math.pow((double)(var13[3 + var7] - var13[var7]), 2.0);
                    var17 += Math.pow((double)(var13[9 + var7] - var13[var7]), 2.0);
                }

                if (var15 * var17 >= 0.04 || var13[18] > 0.5F) {
                    distantFaces.add(var13);
                }
            }

            return var0;
        }
    }

    private static float[] vec(JsonObject var0, String var1, float var2) {
        if (!var0.has(var1)) {
            return new float[]{var2, var2, var2};
        } else {
            JsonArray var3 = var0.getAsJsonArray(var1);
            return new float[]{var3.get(0).getAsFloat(), var3.get(1).getAsFloat(), var3.get(2).getAsFloat()};
        }
    }

    private static void addBox(List<float[]> var0, JsonObject var1) {
        float[] var2 = vec(var1, "f", 0.0F);
        float[] var3 = vec(var1, "t", 1.0F);
        float[] var4 = vec(var1, "r", 0.0F);
        float[] var5 = var1.has("p") ? vec(var1, "p", 0.0F) : new float[]{(var2[0] + var3[0]) / 2.0F, (var2[1] + var3[1]) / 2.0F, (var2[2] + var3[2]) / 2.0F};
        int var6 = Integer.parseInt(var1.get("c").getAsString(), 16);
        float var7 = var1.has("g") && var1.get("g").getAsInt() != 0 ? 1.0F : 0.0F;
        float var8 = (float)(var6 >> 16 & 0xFF) / 255.0F;
        float var9 = (float)(var6 >> 8 & 0xFF) / 255.0F;
        float var10 = (float)(var6 & 0xFF) / 255.0F;
        float var11 = var2[0];
        float var12 = var2[1];
        float var13 = var2[2];
        float var14 = var3[0];
        float var15 = var3[1];
        float var16 = var3[2];
        float[][] var17 = new float[][]{
            {var11, var12, var13},
            {var14, var12, var13},
            {var14, var15, var13},
            {var11, var15, var13},
            {var11, var12, var16},
            {var14, var12, var16},
            {var14, var15, var16},
            {var11, var15, var16}
        };
        int[][] var18 = new int[][]{{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {3, 7, 6, 2}, {0, 1, 5, 4}};
        float[][] var19 = new float[][]{
            {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, -1.0F}, {1.0F, 0.0F, 0.0F}, {-1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}
        };
        double var20 = Math.toRadians((double)var4[0]);
        double var22 = Math.toRadians((double)var4[1]);
        double var24 = Math.toRadians((double)var4[2]);
        boolean[] var26 = new boolean[6];
        if (var1.has("hidden_faces")) {
            for (JsonElement var28 : var1.getAsJsonArray("hidden_faces")) {
                int var29 = var28.getAsInt();
                if (var29 >= 0 && var29 < 6) {
                    var26[var29] = true;
                }
            }
        }

        for (int var31 = 0; var31 < 6; var31++) {
            if (!var26[var31]) {
                float[] var32 = new float[19];

                for (int var33 = 0; var33 < 4; var33++) {
                    float[] var30 = rotate(var17[var18[var31][var33]], var5, var20, var22, var24);
                    var32[var33 * 3] = var30[0];
                    var32[var33 * 3 + 1] = var30[1];
                    var32[var33 * 3 + 2] = var30[2];
                }

                float[] var34 = rotate(var19[var31], new float[]{0.0F, 0.0F, 0.0F}, var20, var22, var24);
                var32[12] = var34[0];
                var32[13] = var34[1];
                var32[14] = var34[2];
                var32[15] = var8;
                var32[16] = var9;
                var32[17] = var10;
                var32[18] = var7;
                var0.add(var32);
            }
        }
    }

    private static float[] rotate(float[] var0, float[] var1, double var2, double var4, double var6) {
        double var8 = (double)(var0[0] - var1[0]);
        double var10 = (double)(var0[1] - var1[1]);
        double var12 = (double)(var0[2] - var1[2]);
        double var14 = Math.cos(var2);
        double var16 = Math.sin(var2);
        double var18 = var10 * var14 - var12 * var16;
        double var20 = var10 * var16 + var12 * var14;
        var14 = Math.cos(var4);
        var16 = Math.sin(var4);
        double var22 = var8 * var14 + var20 * var16;
        var20 = -var8 * var16 + var20 * var14;
        var14 = Math.cos(var6);
        var16 = Math.sin(var6);
        double var24 = var22 * var14 - var18 * var16;
        var18 = var22 * var16 + var18 * var14;
        return new float[]{(float)(var24 + (double)var1[0]), (float)(var18 + (double)var1[1]), (float)(var20 + (double)var1[2])};
    }

    public static void reload() {
        faces = null;
        distantFaces = null;
    }

    public boolean shouldRender(ShrineEntity var1, Frustum var2, double var3, double var5, double var7) {
        return var1.shouldRenderAtSqrDistance(var1.distanceToSqr(var3, var5, var7)) && var2.isVisible(var1.getBoundingBoxForCulling());
    }

    public void render(ShrineEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        List<float[]> var7 = faces();
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(var1.position()) > 6400.0 && distantFaces != null) {
            var7 = distantFaces;
        }

        float var8 = (float)var1.tickCount + var3;
        float var9 = var1.scale();
        float var10 = Math.min(1.0F, var8 / 30.0F);
        float var11 = 1.0F - (1.0F - var10) * (1.0F - var10) * (1.0F - var10);
        float var12 = var10 < 1.0F ? 0.06F * (float)Math.sin((double)(var8 * 2.7F)) : 0.0F;
        var4.pushPose();
        var4.translate(var12, -(1.0F - var11) * modelHeight * var9, var12 * 0.5F);
        var4.mulPose(Axis.YP.rotationDegrees(-var1.getYRot()));
        var4.scale(var9, var9, var9);
        Matrix4f var13 = var4.last().pose();
        PoseStack.Pose var14 = var4.last();
        int var15 = Math.max(LightTexture.block(var6), 7);
        int var16 = Math.max(LightTexture.sky(var6), 4);
        int var17 = LightTexture.pack(var15, var16);
        int var18 = 15728880;
        VertexConsumer var19 = var5.getBuffer(RenderType.entitySolid(TEXTURE));
        float[][] var20 = UVS;

        for (float[] var22 : var7) {
            int var23 = var22[18] > 0.5F ? var18 : var17;
            float var24 = var22[18] > 0.5F ? 0.88F + 0.12F * (float)Math.sin((double)(var8 * 0.15F)) : 1.0F;
            int var25 = (int)(var22[15] * 255.0F * var24);
            int var26 = (int)(var22[16] * 255.0F * var24);
            int var27 = (int)(var22[17] * 255.0F * var24);

            for (int var28 = 0; var28 < 4; var28++) {
                var19.addVertex(var13, var22[var28 * 3], var22[var28 * 3 + 1], var22[var28 * 3 + 2])
                    .setColor(var25, var26, var27, 255)
                    .setUv(var20[var28][0], var20[var28][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(var23)
                    .setNormal(var14, var22[12], var22[13], var22[14])
                    ;
            }
        }

        var4.popPose();
    }

    public ResourceLocation getTextureLocation(ShrineEntity var1) {
        return TEXTURE;
    }
}
