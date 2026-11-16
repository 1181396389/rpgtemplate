package com.rpgtemplate.cronus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.cronus.client.CronusSuitAppearance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 变身为假面骑士 Cronus 时，隐藏原版护甲模型渲染（仅隐藏视觉，保留全部护甲值与附魔加成），
 * 防止铁甲/钻石甲/下界合金甲穿出 Cronus 专属 3D 装甲外表面。
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class CronusArmorHideMixin {
    @Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void cronus$hideVanillaArmorWhileTransformed(PoseStack pose, MultiBufferSource buffers, int packedLight,
                                                         LivingEntity living, float limbSwing, float limbSwingAmount,
                                                         float partialTick, float ageInTicks, float netHeadYaw,
                                                         float headPitch, CallbackInfo ci) {
        if (CronusSuitAppearance.isTransformed(living)) {
            ci.cancel();
        }
    }
}
