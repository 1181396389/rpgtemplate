package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 规则等级生效内，任何非玩家生物均无法对佩戴认知阻碍面具的玩家建立攻击判定或视线感知。
 */
@Mixin(LivingEntity.class)
public abstract class MaskLivingEntityMixin {
    @Inject(method = "m_6779_(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"),
        cancellable = true, remap = false)
    private void mask$blockCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) && PerceptionMaskRules.blocksMob(target, self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "m_142582_(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
        cancellable = true, remap = false)
    private void mask$blockLineOfSight(Entity target, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) && target instanceof LivingEntity le
            && PerceptionMaskRules.blocksMob(le, self)) {
            cir.setReturnValue(false);
        }
    }
}
