package com.rpgtemplate.nothingthere.mixin;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /**
     * Eye height follows the human scale. Player.getStandingEyeHeight is a fixed 1.62 in vanilla, so without this the
     * first-person camera stays at chest height in a 3-block body. Multiplied by the same ratio as the hitbox.
     */
    @Inject(method = "m_6431_(Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;)F",
        at = @At("RETURN"), cancellable = true, remap = false)
    private void nt$scaleEyeHeight(Pose pose, EntityDimensions dims, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (!NtBody.isHuman(self)) {
            return;
        }
        cir.setReturnValue(cir.getReturnValue() * NtBody.HITBOX_HEIGHT_SCALE);
    }

    /**
     * Defines the synced flags for every player, so remote clients receive them. Injected at the end of the
     * constructor: defineSynchedData is inherited from Entity, so Player itself cannot be targeted for it.
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void nt$defineSyncedData(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        self.m_20088_().m_135372_(NtBody.DATA_HUMAN, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_GOODBYE, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_BLADE, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_COCOON, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_HOLLE, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_OH_CHARGE, Boolean.FALSE);
        self.m_20088_().m_135372_(NtBody.DATA_OH_SLAM, Boolean.FALSE);
    }
}
