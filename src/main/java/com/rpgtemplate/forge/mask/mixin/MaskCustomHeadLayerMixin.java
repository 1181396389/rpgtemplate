package com.rpgtemplate.forge.mask.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.forge.mask.PerceptionBlockingMaskItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 取消原版 CustomHeadLayer 把头盔槽位非护甲物品画成漂浮平面牌子的默认行为，完全交由专属 3D 认知阻碍面具层绘制。 */
@Mixin(CustomHeadLayer.class)
public abstract class MaskCustomHeadLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$skipVanillaHeadItem(PoseStack pose, MultiBufferSource buffers, int packedLight,
                                          LivingEntity living, float limbSwing, float limbSwingAmount,
                                          float partialTick, float ageInTicks, float netHeadYaw,
                                          float headPitch, CallbackInfo ci) {
        if (PerceptionBlockingMaskItem.isMask(living.getItemBySlot(EquipmentSlot.HEAD))) {
            ci.cancel();
        }
    }
}
