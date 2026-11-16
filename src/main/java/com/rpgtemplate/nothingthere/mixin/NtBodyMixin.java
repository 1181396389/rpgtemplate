package com.rpgtemplate.nothingthere.mixin;

import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the player's dimensions (including eye height automatically in 1.21.1) and registers
 * Nothing There synced entity data flags on Player (MC 1.21.1 Mojmap).
 */
@Mixin(Player.class)
public abstract class NtBodyMixin {
    @Inject(method = "getDefaultDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void nt$scaleDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player self = (Player) (Object) this;
        if (!NtBody.isHuman(self)) {
            return;
        }
        EntityDimensions base = cir.getReturnValue();
        cir.setReturnValue(base.scale(NtBody.HITBOX_WIDTH_SCALE, NtBody.HITBOX_HEIGHT_SCALE));
    }

    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
            at = @At("TAIL"), remap = false)
    private void nt$defineSyncedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(NtBody.DATA_HUMAN, Boolean.FALSE);
        builder.define(NtBody.DATA_GOODBYE, Boolean.FALSE);
        builder.define(NtBody.DATA_BLADE, Boolean.FALSE);
        builder.define(NtBody.DATA_WHIP, Boolean.FALSE);
        builder.define(NtBody.DATA_MUSCLE, Boolean.FALSE);
        builder.define(NtBody.DATA_COCOON, Boolean.FALSE);
        builder.define(NtBody.DATA_HOLLE, Boolean.FALSE);
        builder.define(NtBody.DATA_OH_CHARGE, Boolean.FALSE);
        builder.define(NtBody.DATA_OH_SLAM, Boolean.FALSE);
        builder.define(NtBody.DATA_MIST_ANIM, Boolean.FALSE);
        builder.define(NtBody.DATA_CORPSE_ANIM, Boolean.FALSE);
        builder.define(NtBody.DATA_ATTACK_ANIM, 0);
        builder.define(NtBody.DATA_MIST_LAYERS, 0);
        builder.define(NtBody.DATA_EGO, Boolean.FALSE);
        builder.define(NtBody.DATA_BLEED_CNT, 0);
    }
}
