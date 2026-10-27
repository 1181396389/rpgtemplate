package com.rpgtemplate.nothingthere.mixin;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the player's dimensions for the 一无所有 human form. Eye height follows automatically
 * because Entity's default eye height is a fraction of the returned height.
 */
@Mixin(Player.class)
public abstract class NtBodyMixin {
    @Inject(method = "m_6972_", at = @At("RETURN"), cancellable = true, remap = false)
    private void nt$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        if (!NtBody.isHuman(self)) {
            return;
        }
        EntityDimensions base = cir.getReturnValue();
        cir.setReturnValue(base.m_20390_(NtBody.HITBOX_WIDTH_SCALE, NtBody.HITBOX_HEIGHT_SCALE));
    }
}
