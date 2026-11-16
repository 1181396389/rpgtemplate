package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 佩戴认知阻碍面具时：
 * <ul>
 *   <li>{@code isIgnoringBlockTriggers (isIgnoringBlockTriggers)} 返回 {@code true} —— 完全不触发所有压力板与绊线/绊线钩。</li>
 *   <li>{@code dampensVibrations (dampensVibrations)} 返回 {@code true} —— 完全不产生幽匿振动。</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class MaskEntityMixin {
    @Inject(method = "isIgnoringBlockTriggers()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$ignoreBlockTriggers(CallbackInfoReturnable<Boolean> cir) {
        if (PerceptionMaskRules.isActiveMaskWearer((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "dampensVibrations()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$dampenVibrations(CallbackInfoReturnable<Boolean> cir) {
        if (PerceptionMaskRules.isActiveMaskWearer((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
