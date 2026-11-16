package com.rpgtemplate.forge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.entity.TechniqueEffectEntity;
import com.rpgtemplate.forge.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class TechniqueEffectRenderer extends EntityRenderer<TechniqueEffectEntity> {
    private static final ResourceLocation INK = RpgTemplateMod.id("textures/effect/slash_ink.png");
    private static final ResourceLocation CRIMSON = RpgTemplateMod.id("textures/effect/slash_crimson.png");
    private static final ResourceLocation RING = RpgTemplateMod.id("textures/effect/ring.png");
    private static final ResourceLocation GLOW = RpgTemplateMod.id("textures/effect/glow.png");
    private static final ResourceLocation STREAK = RpgTemplateMod.id("textures/effect/streak.png");
    private static final ResourceLocation BEAM = RpgTemplateMod.id("textures/effect/beam.png");
    private static final boolean JJC = ModSounds.HAS_JJC;
    private static final ResourceLocation JJC_SLASH = RpgTemplateMod.id("textures/effect/jjc/slash_sukuna.png");
    private static final ResourceLocation DOMAIN_SLASH = RpgTemplateMod.id("textures/effect/jjc/domain_slash.png");
    private static final ResourceLocation JJC_CLEAVE = RpgTemplateMod.id("textures/effect/jjc/cleave_slash.png");
    private static final ResourceLocation JJC_CURSE = RpgTemplateMod.id("textures/effect/jjc/curse_power.png");
    private static final int CLEAVE_FRAMES = 10;
    private static final int CURSE_FRAMES = 12;

    public TechniqueEffectRenderer(Context var1) {
        super(var1);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }


    public ResourceLocation getTextureLocation(TechniqueEffectEntity var1) {
        return GLOW;
    }

    public boolean shouldRender(TechniqueEffectEntity var1, Frustum var2, double var3, double var5, double var7) {
        return var1.visibleTo(Minecraft.getInstance().player);
    }

    public void render(TechniqueEffectEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
        LocalPlayer var7 = Minecraft.getInstance().player;
        float var8;
        float var9;
        if (var1.visibleTo(var7) && !(var1.length() <= 0.0F) && !((var9 = (float)var1.tickCount + var3) >= (var8 = (float)var1.lifetime()))) {
            float var10 = Mth.clamp(var9 / var8, 0.0F, 1.0F);
            var4.pushPose();
            Entity var11 = var1.followId() >= 0 ? var1.level().getEntity(var1.followId()) : null;
            if (var11 != null) {
                Vec3 var12 = var11.getPosition(var3).subtract(var1.getPosition(var3));
                var4.translate(var12.x, var12.y, var12.z);
            }

            boolean var14 = var11 == Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
            if (var1.travelTicks() > 0) {
                Vec3 var13 = var1.flightOffset(var9);
                var4.translate(var13.x, var13.y, var13.z);
            }

            switch (var1.style()) {
                case 0:
                    if (JJC) {
                        this.renderSlashJjc(var1, var9, var4, var5);
                    } else {
                        this.renderSlash(var1, var10, var4, var5);
                    }
                    break;
                case 1:
                    if (JJC) {
                        this.renderCleaveJjc(var1, var9, var10, var4, var5);
                    } else {
                        this.renderCleave(var1, var10, var4, var5);
                    }
                    break;
                case 2:
                    this.renderRctRing(var1, var9, var10, var4, var5, var14);
                    break;
                case 3:
                    this.renderChargeAura(var1, var9, var10, var4, var5, var14);
                    break;
                case 4:
                    this.renderBeam(var1, var10, var4, var5);
                    break;
                case 5:
                    if ((Boolean)ModConfigs.CLIENT.rainbowAura.get()) {
                        this.renderRainbow(var1, var9, var10, var4, var5, var14);
                    }
                    break;
                case 6:
                    this.renderRctAura(var1, var9, var4, var5, var14);
                    break;
                case 7:
                    this.renderGiantGrid(var1, var9, var4, var5);
                    break;
                case 8:
                    this.renderDomainField(var1, var9, var4, var5);
            }

            var4.popPose();
        }
    }

    private void renderDomainField(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        Vec3 var5 = this.entityRenderDispatcher.camera.getPosition();
        double var6 = var5.x - var1.getX();
        double var8 = var5.y - var1.getY();
        double var10 = var5.z - var1.getZ();
        float var12 = Math.max(1.0F, var1.length());
        double var13 = Math.sqrt(var6 * var6 + var10 * var10);
        double var15 = Math.max(0.0, var13 - (double)var12);
        if (!(var15 > 128.0)) {
            int var17 = var15 > 64.0 ? 24 : (var15 > 24.0 ? 64 : 128);
            double var18 = Math.min(32.0, (double)var12);
            double var20 = var13 > (double)var12 * 0.75 ? (double)var12 * 0.75 / Math.max(1.0, var13) : 1.0;
            double var22 = var6 * var20;
            double var24 = var10 * var20;
            RandomSource var26 = RandomSource.create((long)var1.seed());
            VertexConsumer var27 = var4.getBuffer(RenderType.entityTranslucent(DOMAIN_SLASH));
            float var28 = Math.min(1.0F, var2 / 1.5F) * Math.min(1.0F, ((float)var1.lifetime() - var2) / 2.0F);

            for (int var29 = 0; var29 < var17; var29++) {
                double var30 = var26.nextDouble() * Math.PI * 2.0;
                double var32 = Math.sqrt(var26.nextDouble()) * var18;
                double var34 = var22 + Math.cos(var30) * var32;
                double var36 = var24 + Math.sin(var30) * var32;
                float var38 = var26.nextFloat() * 10.0F;
                float var39 = var26.nextFloat() * 180.0F;
                float var40 = 1.5F + var26.nextFloat() * 3.0F;
                double var41 = Math.max(0.2, Math.min((double)var1.width(), var8 + (var26.nextDouble() - 0.5) * 18.0));
                if (!(var34 * var34 + var36 * var36 > (double)(var12 * var12))) {
                    float var43 = (var2 + var38) % 10.0F;
                    int var44 = Math.min(9, (int)var43);
                    int var45 = (int)(210.0F * var28 * Math.min(1.0F, (10.0F - var43) / 2.0F));
                    var3.pushPose();
                    var3.translate(var34, var41, var36);
                    var3.mulPose(this.entityRenderDispatcher.cameraOrientation());
                    var3.mulPose(Axis.ZP.rotationDegrees(var39));
                    quadXY(var27, var3.last(), -var40, -var40, var40, var40, 0.0F, (float)var44 / 10.0F, 1.0F, (float)(var44 + 1) / 10.0F, 255, 255, 255, var45);
                    var3.popPose();
                }
            }
        }
    }

    private void renderGiantGrid(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        var3.mulPose(Axis.YP.rotationDegrees(-var1.effectYaw()));
        var3.mulPose(Axis.XP.rotationDegrees(var1.effectPitch()));
        int var5 = Math.max(3, Math.min(49, var1.param()));
        float var6 = var1.length() * 0.5F;
        float var7 = Mth.clamp(var2 / 2.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((var2 - (float)var1.travelTicks()) / 5.0F, 0.0F, 1.0F));
        VertexConsumer var8 = var4.getBuffer(RenderType.eyes(GLOW));

        for (int var9 = 0; var9 < var5; var9++) {
            float var10 = -var6 + var1.length() * (float)var9 / (float)(var5 - 1);
            quadXY(var8, var3.last(), -var6, var10 - 0.4F, var6, var10 + 0.4F, 0.0F, 0.0F, 1.0F, 1.0F, 170, 180, 195, (int)(90.0F * var7));
            quadXY(var8, var3.last(), var10 - 0.4F, -var6, var10 + 0.4F, var6, 0.0F, 0.0F, 1.0F, 1.0F, 170, 180, 195, (int)(90.0F * var7));
            quadXY(var8, var3.last(), -var6, var10 - 0.09F, var6, var10 + 0.09F, 0.0F, 0.0F, 1.0F, 1.0F, 245, 250, 255, (int)(255.0F * var7));
            quadXY(var8, var3.last(), var10 - 0.09F, -var6, var10 + 0.09F, var6, 0.0F, 0.0F, 1.0F, 1.0F, 245, 250, 255, (int)(255.0F * var7));
        }
    }

    private void renderSlash(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        var3.mulPose(Axis.YP.rotationDegrees(-var1.effectYaw()));
        var3.mulPose(Axis.XP.rotationDegrees(var1.effectPitch()));
        var3.mulPose(Axis.ZP.rotationDegrees(var1.roll()));
        Pose var5 = var3.last();
        float var6 = var1.length();
        float var7 = var1.width();
        boolean var8 = var1.param() == 1;
        VertexConsumer var9 = var4.getBuffer(RenderType.entityTranslucent(var8 ? CRIMSON : INK));
        float var10 = 0.22F;
        float var11 = 1.0F - cube(1.0F - Mth.clamp(var2 / 0.22F, 0.0F, 1.0F));
        float var12 = Mth.clamp((var2 - 0.22F) / 0.78F, 0.0F, 1.0F);
        float var13 = 1.0F - var12 * var12;
        float var14 = Mth.lerp(smooth(var12), 1.0F, 0.12F);
        float var15 = var2 * 0.1F * var6;
        float var16 = -var6 / 2.0F + var15;
        float var17 = var16 + var6 * var11;
        float var18 = var7 * var14;
        quadXY(var9, var5, var16, -var18, var17, var18, 0.0F, 0.0F, 1.0F, 1.0F, 255, 255, 255, (int)(255.0F * var13));
        RandomSource var19 = RandomSource.create((long)var1.seed());

        for (int var20 = 0; var20 < 2; var20++) {
            float var21 = 0.04F + var19.nextFloat() * 0.08F;
            float var22 = Mth.clamp((var2 - var21) / (1.0F - var21), 0.0F, 1.0F);
            if (!(var22 <= 0.0F)) {
                float var23 = 1.0F - cube(1.0F - Mth.clamp(var22 / 0.22F, 0.0F, 1.0F));
                float var24 = Mth.clamp(1.0F - Mth.clamp((var22 - 0.22F) / 0.78F, 0.0F, 1.0F), 0.0F, 1.0F);
                float var25 = (float)(var20 == 0 ? 1 : -1) * (var7 * 1.6F + var19.nextFloat() * var7 * 2.5F);
                float var26 = var6 * (0.45F + var19.nextFloat() * 0.35F);
                float var27 = -var26 / 2.0F + (var19.nextFloat() - 0.5F) * var6 * 0.3F + var15 * 1.3F;
                float var28 = var7 * 0.32F * Mth.lerp(1.0F - var24, 1.0F, 0.3F);
                var3.pushPose();
                var3.mulPose(Axis.ZP.rotationDegrees((var19.nextFloat() - 0.5F) * 6.0F));
                quadXY(
                    var9,
                    var3.last(),
                    var27,
                    var25 - var28,
                    var27 + var26 * var23,
                    var25 + var28,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    255,
                    255,
                    255,
                    (int)(200.0F * var24 * var24)
                );
                var3.popPose();
            }
        }

        float var29 = 1.0F - Math.abs(var2 - 0.22F) / 0.12F;
        if (var29 > 0.0F) {
            VertexConsumer var30 = var4.getBuffer(RenderType.eyes(GLOW));
            float var31 = var7 * 5.0F;
            int var32 = var8 ? 255 : 230;
            int var33 = var8 ? 70 : 210;
            int var34 = var8 ? 70 : 215;
            int var35 = (int)(255.0F * var29 * 0.55F);
            quadXY(
                var30,
                var5,
                -var6 / 2.0F + var15,
                -var31,
                var6 / 2.0F + var15,
                var31,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                scale(var32, var35),
                scale(var33, var35),
                scale(var34, var35),
                255
            );
        }
    }

    private void renderSlashJjc(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        var3.mulPose(Axis.YP.rotationDegrees(-var1.effectYaw()));
        var3.mulPose(Axis.XP.rotationDegrees(var1.effectPitch()));
        var3.mulPose(Axis.ZP.rotationDegrees(var1.roll()));
        int var5 = var1.travelTicks() > 0 ? var1.travelTicks() : Math.max(1, var1.lifetime() - 4);
        float var6 = var1.length() * (0.85F + 0.15F * Mth.clamp(var2 / 1.5F, 0.0F, 1.0F));
        float var7 = Mth.clamp(var2 / 0.8F, 0.0F, 1.0F);
        float var8 = 1.0F - Mth.clamp((var2 - (float)var5) / 4.0F, 0.0F, 1.0F);
        float var9 = var7 * var8;
        if (!(var9 <= 0.0F)) {
            boolean var10 = var1.param() == 1;
            float var11 = (float)var1.flightOffset(1.0F).length();
            if (var10) {
                VertexConsumer var12 = var4.getBuffer(RenderType.eyes(GLOW));
                int var13 = (int)(200.0F * var9);
                quadXY(
                    var12,
                    var3.last(),
                    -var6 * 0.55F,
                    -var6 * 0.13F,
                    var6 * 0.55F,
                    var6 * 0.13F,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    scale(255, var13),
                    scale(30, var13),
                    scale(40, var13),
                    255
                );
            }

            VertexConsumer var14 = var4.getBuffer(RenderType.entityTranslucent(JJC_SLASH));
            float var15 = var6 / 2.0F;
            if (var2 < (float)var5 && var11 > 0.01F) {
                var3.pushPose();
                var3.translate(0.0, 0.0, (double)(-var11 * 0.6F));
                quadRotUV(var14, var3.last(), -var15 * 0.9F, -var15 * 0.9F, var15 * 0.9F, var15 * 0.9F, 255, 255, 255, (int)(90.0F * var9));
                var3.popPose();
            }

            quadRotUV(var14, var3.last(), -var15, -var15, var15, var15, 255, 255, 255, (int)(255.0F * var9));
        }
    }

    private void renderCleaveJjc(TechniqueEffectEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5) {
        float var6 = var1.length();
        float var7 = var1.width();
        var4.translate(0.0, (double)(var7 / 2.0F), 0.0);
        int var8 = Math.min(24, Math.max(3, var1.param()) * 2);
        int var9 = var1.color();
        float var10 = (float)var1.lifetime();
        RandomSource var11 = RandomSource.create((long)var1.seed());
        VertexConsumer var12 = var5.getBuffer(RenderType.entityTranslucent(JJC_CLEAVE));

        for (int var13 = 0; var13 < var8; var13++) {
            float var14 = (var11.nextFloat() - 0.5F) * var6 * 0.7F;
            float var15 = (var11.nextFloat() - 0.5F) * Math.max(var7, 0.6F) * 0.9F;
            float var16 = (var11.nextFloat() - 0.5F) * var6 * 0.7F;
            float var17 = var11.nextFloat() * 0.45F * var10;
            float var18 = (float)(8 + (var11.nextInt(8) - 4));
            int var19 = var11.nextInt(10);
            float var20 = var6 * (0.45F + var11.nextFloat() * 0.35F);
            float var21 = var2 - var17;
            if (!(var21 < 0.0F) && !(var21 >= var18)) {
                int var22 = ((int)var21 + var19) % 10;
                float var23 = Mth.clamp((var18 - var21) / 2.0F, 0.0F, 1.0F);
                var4.pushPose();
                var4.translate((double)var14, (double)var15, (double)var16);
                var4.mulPose(this.entityRenderDispatcher.cameraOrientation());
                float var24 = var20 / 2.0F;
                quadXY(
                    var12,
                    var4.last(),
                    -var24,
                    -var24,
                    var24,
                    var24,
                    0.0F,
                    (float)var22 / 10.0F,
                    1.0F,
                    (float)(var22 + 1) / 10.0F,
                    r(var9),
                    g(var9),
                    b(var9),
                    (int)(255.0F * var23)
                );
                var4.popPose();
            }
        }

        float var25 = Math.max(0.0F, 1.0F - Math.abs(var3 - 0.55F) / 0.12F);
        if (var25 > 0.0F) {
            boolean var26 = (var9 & 0xFF00) < 32768;
            var4.mulPose(this.entityRenderDispatcher.cameraOrientation());
            VertexConsumer var27 = var5.getBuffer(RenderType.eyes(GLOW));
            int var28 = (int)(255.0F * var25 * (var26 ? 0.8F : 0.45F));
            float var29 = var6 * 0.8F;
            quadXY(
                var27,
                var4.last(),
                -var29,
                -var29,
                var29,
                var29,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                scale(255, var28),
                scale(var26 ? 40 : 230, var28),
                scale(var26 ? 40 : 235, var28),
                255
            );
        }
    }

    private void renderCurseWisps(
        TechniqueEffectEntity var1,
        float var2,
        PoseStack var3,
        MultiBufferSource var4,
        boolean var5,
        int var6,
        float var7,
        float var8,
        float var9,
        int var10,
        float var11
    ) {
        if (!(var11 <= 0.0F)) {
            RandomSource var12 = RandomSource.create((long)var1.seed() * 31L + 7L);
            VertexConsumer var13 = var4.getBuffer(RenderType.entityTranslucent(JJC_CURSE));

            for (int var14 = 0; var14 < var6; var14++) {
                float var15 = var12.nextFloat();
                float var16 = 12.0F + var12.nextFloat() * 8.0F;
                float var17 = var15 * (float) (Math.PI * 2) + var2 * (0.03F + var12.nextFloat() * 0.04F);
                float var18 = var7 * (0.55F + var12.nextFloat() * 0.6F);
                float var19 = var9 * (0.8F + var12.nextFloat() * 0.6F);
                float var20 = (var2 + var15 * var16) % var16;
                float var21 = var20 / var16;
                int var22 = Math.min(11, (int)(var21 * 12.0F));
                float var23 = var12.nextFloat() * var8 * 0.25F + var21 * var8 * 0.85F;
                float var24 = var11;
                if (var5) {
                    var23 *= 0.45F;
                    var24 = var11 * 0.55F;
                }

                var3.pushPose();
                var3.translate((double)(Mth.cos(var17) * var18), (double)var23, (double)(Mth.sin(var17) * var18));
                var3.mulPose(this.entityRenderDispatcher.cameraOrientation());
                float var25 = var19 / 2.0F;
                quadXY(
                    var13,
                    var3.last(),
                    -var25,
                    -var25,
                    var25,
                    var25,
                    0.0F,
                    (float)var22 / 12.0F,
                    1.0F,
                    (float)(var22 + 1) / 12.0F,
                    r(var10),
                    g(var10),
                    b(var10),
                    (int)(255.0F * Mth.clamp(var24, 0.0F, 1.0F))
                );
                var3.popPose();
            }
        }
    }

    private void renderCleave(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        var3.translate(0.0, (double)(var1.width() / 2.0F), 0.0);
        var3.mulPose(this.entityRenderDispatcher.cameraOrientation());
        float var5 = var1.length();
        int var6 = Math.max(2, var1.param());
        boolean var7 = (var1.color() & 0xFF00) < 32768;
        VertexConsumer var8 = var4.getBuffer(RenderType.entityTranslucent(var7 ? CRIMSON : INK));
        RandomSource var9 = RandomSource.create((long)var1.seed());
        float var10 = 0.55F;
        float var11 = var2 < 0.65F ? 1.0F : 1.0F - (var2 - 0.65F) / 0.35F;
        float var12 = Math.max(0.0F, 1.0F - Math.abs(var2 - var10) / 0.08F);
        float var13 = var9.nextFloat() * 180.0F;

        for (int var14 = 0; var14 < var6; var14++) {
            float var15 = var13 + (float)(var14 % 2 == 0 ? 0 : 90) + (var9.nextFloat() - 0.5F) * 24.0F;
            float var16 = (var9.nextFloat() - 0.5F) * var5 * 0.75F;
            float var17 = var5 * (0.8F + var9.nextFloat() * 0.45F);
            float var18 = (float)var14 * (0.42F / (float)var6);
            float var19 = 1.0F - cube(1.0F - Mth.clamp((var2 - var18) / 0.07F, 0.0F, 1.0F));
            if (!(var19 <= 0.0F)) {
                float var20 = var5 * 0.022F * (1.0F + var12 * 1.6F) * Mth.lerp(1.0F - var11, 1.0F, 0.3F);
                var3.pushPose();
                var3.mulPose(Axis.ZP.rotationDegrees(var15));
                float var21 = -var17 / 2.0F;
                quadXY(
                    var8, var3.last(), var21, var16 - var20, var21 + var17 * var19, var16 + var20, 0.0F, 0.0F, 1.0F, 1.0F, 255, 255, 255, (int)(255.0F * var11)
                );
                var3.popPose();
            }
        }

        float var22 = Math.max(0.0F, 1.0F - Math.abs(var2 - var10) / 0.12F);
        if (var22 > 0.0F) {
            VertexConsumer var23 = var4.getBuffer(RenderType.eyes(GLOW));
            int var24 = (int)(255.0F * var22 * (var7 ? 0.8F : 0.5F));
            float var25 = var5 * 0.8F;
            quadXY(
                var23,
                var3.last(),
                -var25,
                -var25,
                var25,
                var25,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                scale(255, var24),
                scale(var7 ? 40 : 230, var24),
                scale(var7 ? 40 : 235, var24),
                255
            );
        }
    }

    private void renderRctRing(TechniqueEffectEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, boolean var6) {
        float var7 = var1.width();
        float var8 = var7 * (1.0F - (1.0F - var3) * (1.0F - var3)) * (var6 ? 0.55F : 1.0F);
        float var9 = var1.length() * (1.0F - 0.3F * var3);
        float var10 = Mth.sin(var3 * (float) Math.PI) * 0.75F;
        int var11 = var1.color();
        VertexConsumer var12 = var5.getBuffer(RenderType.eyes(RING));
        int var13 = (int)(255.0F * var10);
        quadXZ(var12, var4.last(), -var9, var8, -var9, var9, var9, scale(r(var11), var13), scale(g(var11), var13), scale(b(var11), var13));
        if (JJC) {
            this.renderCurseWisps(var1, var2, var4, var5, var6, 6, var9 * 0.8F, var1.width(), 0.35F, 16777215, var10 * 1.2F);
        } else {
            VertexConsumer var14 = var5.getBuffer(RenderType.eyes(STREAK));
            RandomSource var15 = RandomSource.create((long)var1.seed());

            for (int var16 = 0; var16 < 3; var16++) {
                float var17 = var15.nextFloat() * 360.0F;
                float var18 = var9 * (0.6F + var15.nextFloat() * 0.3F);
                float var19 = var7 * 0.35F;
                float var20 = var8 - var19 * 0.5F;
                var4.pushPose();
                var4.mulPose(Axis.YP.rotationDegrees(var17));
                var4.translate((double)var18, 0.0, 0.0);
                var4.mulPose(Axis.YP.rotationDegrees(90.0F + var17 * 0.0F));
                int var21 = (int)((float)var13 * 0.6F);
                quadXY(
                    var14,
                    var4.last(),
                    -0.06F,
                    var20,
                    0.06F,
                    var20 + var19,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    scale(r(var11), var21),
                    scale(g(var11), var21),
                    scale(b(var11), var21),
                    255
                );
                var4.popPose();
            }
        }
    }

    private void renderRctAura(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4, boolean var5) {
        int var6 = Math.max(1, var1.param());
        float var7 = var1.length();
        float var8 = var1.width();
        float var9 = Math.max(1.0F, var1.roll());
        RandomSource var10 = RandomSource.create((long)var1.seed() * 131L + 3L);
        VertexConsumer var11 = var4.getBuffer(RenderType.entityTranslucent(JJC ? JJC_CURSE : GLOW));
        int var12 = r(var1.color());
        int var13 = g(var1.color());
        int var14 = b(var1.color());
        LocalPlayer var15 = Minecraft.getInstance().player;
        double var16 = var15 != null ? (double)var15.getEyeHeight() : 1.62;
        double var18 = Math.max(0.22, (double)var7 * 0.62);

        for (int var20 = 0; var20 < var6; var20++) {
            float var21 = var9 * (float)var20 / (float)var6 + var10.nextFloat() * (var9 / (float)var6);
            float var22 = (float)(11 + var10.nextInt(4));
            float var23 = var2 - var21;
            if (!(var23 < 0.0F) && !(var23 >= var22)) {
                float var24 = var23 / var22;
                float var25 = var10.nextFloat() * (float) (Math.PI * 2);
                float var26 = (float)(var10.nextBoolean() ? 1 : -1) * (0.02F + var10.nextFloat() * 0.03F);
                double var27 = (double)var8 * (0.15 + (double)var10.nextFloat() * 0.5);
                float var29 = var10.nextFloat() * (float) (Math.PI * 2);
                float var30 = 0.25F + var10.nextFloat() * 0.35F;
                float var31 = var25 + var26 * var23;
                double var32 = var18 * (0.85 + 0.3 * (double)var24) + (double)Mth.sin(var23 * var30 + var29) * 0.05;
                double var34 = (double)Mth.cos(var31) * var32;
                double var36 = (double)Mth.sin(var31) * var32;
                double var38 = var27 + (double)var23 * 0.035 + (double)Mth.sin(var23 * var30 * 1.7F + var29) * 0.04;
                float var40 = (0.055F + var10.nextFloat() * 0.05F) * (0.7F + 0.6F * Mth.sin(var24 * (float) Math.PI)) * Mth.clamp(var7 / 0.6F, 0.5F, 1.0F);
                float var41 = 0.8F * Mth.sin(Mth.clamp(var24, 0.0F, 1.0F) * (float) Math.PI);
                if (!(var41 < 0.02F)) {
                    if (var5) {
                        double var42;
                        var34 = (double)Mth.cos(var31) * (var42 = var32 * 2.2) + (double)Mth.sin(var23 * var30 + var29) * 0.12;
                        var36 = (double)Mth.sin(var31) * var42 + (double)Mth.cos(var23 * var30 * 0.8F + var29) * 0.12;
                        var40 *= 0.45F;
                        var41 *= 0.55F;
                        double var44 = (var38 *= 0.8) - var16;
                        if (var34 * var34 + var44 * var44 + var36 * var36 < 0.30250000000000005) {
                            continue;
                        }
                    }

                    float var46;
                    if (!((var46 = Mth.clamp((var8 * 1.25F - (float)var38) / 0.5F, 0.0F, 1.0F)) <= 0.02F)) {
                        var3.pushPose();
                        var3.translate(var34, var38, var36);
                        var3.mulPose(this.entityRenderDispatcher.cameraOrientation());
                        if (JJC) {
                            int var43 = (int)var23 % 12;
                            quadXY(
                                var11,
                                var3.last(),
                                -var40,
                                -var40,
                                var40,
                                var40,
                                0.0F,
                                (float)var43 / 12.0F,
                                1.0F,
                                (float)(var43 + 1) / 12.0F,
                                var12,
                                var13,
                                var14,
                                (int)(255.0F * var41 * var46)
                            );
                        } else {
                            quadXY(
                                var11,
                                var3.last(),
                                -var40 * 0.7F,
                                -var40,
                                var40 * 0.7F,
                                var40,
                                0.0F,
                                0.0F,
                                1.0F,
                                1.0F,
                                var12,
                                var13,
                                var14,
                                (int)(255.0F * var41 * var46)
                            );
                        }

                        var3.popPose();
                    }
                }
            }
        }
    }

    private void renderChargeAura(TechniqueEffectEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, boolean var6) {
        int var7 = var1.param();
        float var8 = (float)var1.lifetime();
        float var9 = Mth.clamp(var2 / 3.0F, 0.0F, 1.0F) * Mth.clamp((var8 - var2) / 5.0F, 0.0F, 1.0F);
        float var10 = (0.4F + (float)var7 * 0.2F) * var9;
        float var11 = var1.width() * (var6 ? 0.5F : 1.0F);
        int var12 = var1.color();
        VertexConsumer var13 = var5.getBuffer(RenderType.eyes(RING));
        float var14 = 0.9F + (float)var7 * 0.25F + Mth.sin(var2 * 0.5F) * 0.05F;
        int var15 = (int)(200.0F * var10);
        if (JJC) {
            this.renderCurseWisps(
                var1,
                var2,
                var4,
                var5,
                var6,
                Math.min(24, 8 + var7 * 4),
                var1.length() * 1.1F,
                var1.width(),
                0.45F + (float)var7 * 0.08F,
                13637656,
                Math.min(1.0F, var10 * 1.5F)
            );
        } else {
            int var16 = 6 + var7 * 3;

            for (int var17 = 0; var17 < 2; var17++) {
                VertexConsumer var18 = var5.getBuffer(var17 == 0 ? RenderType.entityTranslucent(STREAK) : RenderType.eyes(STREAK));
                RandomSource var19 = RandomSource.create((long)var1.seed());

                for (int var20 = 0; var20 < var16; var20++) {
                    float var21 = var19.nextFloat() * (float) (Math.PI * 2);
                    float var22 = var21 + var2 * (0.1F + var19.nextFloat() * 0.08F);
                    float var23 = var1.length() * (0.75F + var19.nextFloat() * 0.4F);
                    float var24 = var11 * (0.45F + var19.nextFloat() * 0.5F) * (0.85F + 0.15F * Mth.sin(var2 * 0.9F + var21));
                    float var25 = 0.12F + var19.nextFloat() * 0.1F + (float)var7 * 0.03F;
                    float var26 = var19.nextFloat() * 0.2F;
                    var4.pushPose();
                    var4.mulPose(Axis.YP.rotation(var22));
                    var4.translate((double)var23, 0.0, 0.0);
                    if (var17 == 0) {
                        int var27 = (int)(170.0F * var10);
                        quadXY(var18, var4.last(), -var25, var26, var25, var26 + var24, 0.0F, 0.0F, 1.0F, 1.0F, 25, 0, 4, var27);
                    } else {
                        int var28 = (int)(160.0F * var10);
                        quadXY(
                            var18,
                            var4.last(),
                            -var25 * 0.5F,
                            var26,
                            var25 * 0.5F,
                            var26 + var24 * 0.9F,
                            0.0F,
                            0.0F,
                            1.0F,
                            1.0F,
                            scale(r(var12) + 60, var28),
                            scale(g(var12), var28),
                            scale(b(var12), var28),
                            255
                        );
                    }

                    var4.popPose();
                }
            }
        }
    }

    private void renderBeam(TechniqueEffectEntity var1, float var2, PoseStack var3, MultiBufferSource var4) {
        var3.mulPose(Axis.YP.rotationDegrees(-var1.effectYaw()));
        var3.mulPose(Axis.XP.rotationDegrees(var1.effectPitch()));
        float var5 = var1.length();
        float var6 = var1.width() * (1.0F - var2 * 0.6F);
        int var7 = (int)(255.0F * (1.0F - var2));
        int var8 = var1.color();
        VertexConsumer var9 = var4.getBuffer(RenderType.eyes(BEAM));

        for (int var10 = 0; var10 < 2; var10++) {
            var3.pushPose();
            var3.mulPose(Axis.ZP.rotationDegrees((float)(var10 * 90)));
            Pose var11 = var3.last();
            vertex(var9, var11, -var6, 0.0F, 0.0F, 0.0F, 0.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, -var6, 0.0F, var5, 1.0F, 0.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, var6, 0.0F, var5, 1.0F, 1.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, var6, 0.0F, 0.0F, 0.0F, 1.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, var6, 0.0F, 0.0F, 0.0F, 1.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, var6, 0.0F, var5, 1.0F, 1.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, -var6, 0.0F, var5, 1.0F, 0.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            vertex(var9, var11, -var6, 0.0F, 0.0F, 0.0F, 0.0F, scale(r(var8), var7), scale(g(var8), var7), scale(b(var8), var7), 255);
            var3.popPose();
        }
    }

    private void renderRainbow(TechniqueEffectEntity var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, boolean var6) {
        float var7 = var1.width() * (var6 ? 0.6F : 1.15F);
        VertexConsumer var8 = var5.getBuffer(RenderType.eyes(STREAK));
        float var9 = Mth.clamp(var2 / 6.0F, 0.0F, 1.0F) * Mth.clamp(((float)var1.lifetime() - var2) / 10.0F, 0.0F, 1.0F) * (var6 ? 0.4F : 1.0F);
        byte var10 = 10;

        for (int var11 = 0; var11 < var10; var11++) {
            float var12 = ((float)var11 / (float)var10 + var2 * 0.02F) % 1.0F;
            int var13 = Mth.hsvToRgb(var12, 0.85F, 1.0F);
            float var14 = var2 * 0.22F + (float)var11 * (float) (Math.PI * 2) / (float)var10;
            float var15 = var1.length() * (1.0F - 0.4F * var3);
            float var16 = var3 * 1.3F % 1.0F * var7 * 0.6F;
            var4.pushPose();
            var4.mulPose(Axis.YP.rotation(var14));
            var4.translate((double)var15, 0.0, 0.0);
            int var17 = (int)(230.0F * var9);
            quadXY(
                var8,
                var4.last(),
                -0.09F,
                var16,
                0.09F,
                var16 + var7 * 0.55F,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                scale(r(var13), var17),
                scale(g(var13), var17),
                scale(b(var13), var17),
                255
            );
            var4.popPose();
        }

        VertexConsumer var18 = var5.getBuffer(RenderType.eyes(RING));

        for (int var19 = 0; var19 < 4; var19++) {
            float var21 = (var3 * 2.0F + (float)var19 * 0.25F) % 1.0F;
            float var23 = var21 * var7;
            float var25 = var1.length() * (1.1F - 0.5F * var21);
            int var27 = Mth.hsvToRgb(((float)var19 * 0.25F + var2 * 0.03F) % 1.0F, 0.8F, 1.0F);
            int var28 = (int)(200.0F * Mth.sin(var21 * (float) Math.PI) * var9);
            quadXZ(var18, var4.last(), -var25, var23, -var25, var25, var25, scale(r(var27), var28), scale(g(var27), var28), scale(b(var27), var28));
        }

        if (var3 > 0.75F) {
            float var20 = (var3 - 0.75F) / 0.25F;
            float var22 = 0.5F + var20 * 3.5F;
            int var24 = var1.color();
            int var26 = (int)(255.0F * (1.0F - var20));
            quadXZ(var18, var4.last(), -var22, var7 * 0.5F, -var22, var22, var22, scale(r(var24), var26), scale(g(var24), var26), scale(b(var24), var26));
        }
    }

    private static void vertex(
        VertexConsumer var0, Pose var1, float var2, float var3, float var4, float var5, float var6, int var7, int var8, int var9, int var10
    ) {
        var0.addVertex(var1.pose(), var2, var3, var4)
            .setColor(clamp255(var7), clamp255(var8), clamp255(var9), clamp255(var10))
            .setUv(var5, var6)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880)
            .setNormal(var1, 0.0F, 1.0F, 0.0F)
            ;
    }

    private static void quadXY(
        VertexConsumer var0,
        Pose var1,
        float var2,
        float var3,
        float var4,
        float var5,
        float var6,
        float var7,
        float var8,
        float var9,
        int var10,
        int var11,
        int var12,
        int var13
    ) {
        if (var13 > 0 && !(var4 - var2 <= 1.0E-4F)) {
            vertex(var0, var1, var2, var3, 0.0F, var6, var9, var10, var11, var12, var13);
            vertex(var0, var1, var4, var3, 0.0F, var8, var9, var10, var11, var12, var13);
            vertex(var0, var1, var4, var5, 0.0F, var8, var7, var10, var11, var12, var13);
            vertex(var0, var1, var2, var5, 0.0F, var6, var7, var10, var11, var12, var13);
            vertex(var0, var1, var2, var5, 0.0F, var6, var7, var10, var11, var12, var13);
            vertex(var0, var1, var4, var5, 0.0F, var8, var7, var10, var11, var12, var13);
            vertex(var0, var1, var4, var3, 0.0F, var8, var9, var10, var11, var12, var13);
            vertex(var0, var1, var2, var3, 0.0F, var6, var9, var10, var11, var12, var13);
        }
    }

    private static void quadRotUV(VertexConsumer var0, Pose var1, float var2, float var3, float var4, float var5, int var6, int var7, int var8, int var9) {
        if (var9 > 0) {
            vertex(var0, var1, var2, var3, 0.0F, 0.0F, 0.0F, var6, var7, var8, var9);
            vertex(var0, var1, var4, var3, 0.0F, 0.0F, 1.0F, var6, var7, var8, var9);
            vertex(var0, var1, var4, var5, 0.0F, 1.0F, 1.0F, var6, var7, var8, var9);
            vertex(var0, var1, var2, var5, 0.0F, 1.0F, 0.0F, var6, var7, var8, var9);
            vertex(var0, var1, var2, var5, 0.0F, 1.0F, 0.0F, var6, var7, var8, var9);
            vertex(var0, var1, var4, var5, 0.0F, 1.0F, 1.0F, var6, var7, var8, var9);
            vertex(var0, var1, var4, var3, 0.0F, 0.0F, 1.0F, var6, var7, var8, var9);
            vertex(var0, var1, var2, var3, 0.0F, 0.0F, 0.0F, var6, var7, var8, var9);
        }
    }

    private static void quadXZ(VertexConsumer var0, Pose var1, float var2, float var3, float var4, float var5, float var6, int var7, int var8, int var9) {
        if (var7 + var8 + var9 > 0) {
            vertex(var0, var1, var2, var3, var4, 0.0F, 0.0F, var7, var8, var9, 255);
            vertex(var0, var1, var2, var3, var6, 0.0F, 1.0F, var7, var8, var9, 255);
            vertex(var0, var1, var5, var3, var6, 1.0F, 1.0F, var7, var8, var9, 255);
            vertex(var0, var1, var5, var3, var4, 1.0F, 0.0F, var7, var8, var9, 255);
            vertex(var0, var1, var5, var3, var4, 1.0F, 0.0F, var7, var8, var9, 255);
            vertex(var0, var1, var5, var3, var6, 1.0F, 1.0F, var7, var8, var9, 255);
            vertex(var0, var1, var2, var3, var6, 0.0F, 1.0F, var7, var8, var9, 255);
            vertex(var0, var1, var2, var3, var4, 0.0F, 0.0F, var7, var8, var9, 255);
        }
    }

    private static int clamp255(int var0) {
        return var0 < 0 ? 0 : Math.min(255, var0);
    }

    private static int scale(int var0, int var1) {
        return clamp255(var0) * clamp255(var1) / 255;
    }

    private static int r(int var0) {
        return var0 >> 16 & 0xFF;
    }

    private static int g(int var0) {
        return var0 >> 8 & 0xFF;
    }

    private static int b(int var0) {
        return var0 & 0xFF;
    }

    private static float cube(float var0) {
        return var0 * var0 * var0;
    }

    private static float smooth(float var0) {
        return var0 * var0 * (3.0F - 2.0F * var0);
    }
}
