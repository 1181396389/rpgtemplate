package com.rpgtemplate.nothingthere.mixin;

import com.rpgtemplate.nothingthere.client.NtGoodbyeLean;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only. The goodbye strike leans the 一无所有 torso forward and crouches it (NtGoodbyeLean).
 * HEAD undoes the last frame's lean, TAIL applies this frame's lean after the vanilla pose.
 */
@Mixin(PlayerModel.class)
public abstract class NtPlayerModelMixin {
    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"), remap = false)
    private void nt$leanRestore(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch, CallbackInfo ci) {
        NtGoodbyeLean.restore((PlayerModel<?>) (Object) this);
    }

    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), remap = false)
    private void nt$leanApply(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch, CallbackInfo ci) {
        NtGoodbyeLean.apply((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
