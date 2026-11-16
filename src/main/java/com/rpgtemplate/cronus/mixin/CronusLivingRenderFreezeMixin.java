package com.rpgtemplate.cronus.mixin;

import com.rpgtemplate.cronus.client.CronusClientFx;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 【Pause】客户端渲染亚帧定格 Mixin：
 * 锁定处于完全时停状态的生物的 {@code getBob}（呼吸/摆翼/触手摆动计时），消除帧间 partialTick 微颤。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CronusLivingRenderFreezeMixin<T extends LivingEntity> {
    @Inject(method = "getBob(Lnet/minecraft/world/entity/LivingEntity;F)F",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void rpgtemplate$freezeBob(T entity, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (entity != null && CronusClientFx.isEntityFrozen(entity.getId())) {
            cir.setReturnValue((float) entity.tickCount);
        }
    }
}
