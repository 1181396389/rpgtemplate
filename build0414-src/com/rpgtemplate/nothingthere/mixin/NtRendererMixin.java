package com.rpgtemplate.nothingthere.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rpgtemplate.nothingthere.NtBody;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only. Makes the rendered 一无所有 body match the hitbox (NtBody.HITBOX_*). PlayerRenderer.scale already
 * applies the vanilla 0.9375; this multiplies on top of it so the total is the hitbox ratio. Injected at RETURN so
 * the layers (the hand weapons) are drawn with the same pose.
 */
@Mixin(PlayerRenderer.class)
public abstract class NtRendererMixin {
    /** The vanilla scale that PlayerRenderer.scale applies before this injection. */
    private static final float VANILLA_SCALE = 0.9375F;

    @Inject(method = "m_7546_(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
        at = @At("RETURN"), remap = false)
    private void nt$scaleBody(AbstractClientPlayer player, PoseStack pose, float partialTick, CallbackInfo ci) {
        if (!NtBody.isHuman(player)) {
            return;
        }
        pose.m_85841_(NtBody.HITBOX_WIDTH_SCALE / VANILLA_SCALE, NtBody.HITBOX_HEIGHT_SCALE / VANILLA_SCALE,
            NtBody.HITBOX_WIDTH_SCALE / VANILLA_SCALE);
    }
}
