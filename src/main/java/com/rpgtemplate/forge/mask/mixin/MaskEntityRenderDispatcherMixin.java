package com.rpgtemplate.forge.mask.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.forge.mask.client.PerceptionMaskClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 仅客户端：其他玩家视线中，若准星没有正对着佩戴认知阻碍面具的玩家，
 * 则完全跳过实体渲染（含本体、护甲、手持物、斗篷、脚底阴影与名牌），使其彻底从视野认知中消失。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MaskEntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$hideUnlessCrosshairAligned(Entity entity, double x, double y, double z,
                                                 float yaw, float partialTick, PoseStack pose,
                                                 MultiBufferSource buffers, int packedLight, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer target
            && PerceptionMaskClient.shouldHideFromViewer(target, partialTick)) {
            ci.cancel();
        }
    }
}
