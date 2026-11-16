package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 规则等级生效内阻止监守者感知、嗅探或锁定认知阻碍面具佩戴者。 */
@Mixin(Warden.class)
public abstract class MaskWardenMixin {
    @Inject(method = "m_219385_(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
        cancellable = true, remap = false)
    private void mask$blockWardenTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity le
            && PerceptionMaskRules.blocksMob(le, (LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
