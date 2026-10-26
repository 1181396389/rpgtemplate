package com.rpgtemplate.forge.production.client.mixin;
import com.rpgtemplate.forge.production.client.BuildVisuals;import net.minecraft.client.renderer.entity.player.PlayerRenderer;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.renderer.MultiBufferSource;import com.mojang.blaze3d.vertex.PoseStack;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class BuildPlayerRendererMixin {
 @Inject(method="m_7392_(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),remap=false)
 private void build$begin(AbstractClientPlayer p,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){BuildVisuals.begin(p.m_20148_());}
 @Inject(method="m_7392_(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"),remap=false)
 private void build$end(AbstractClientPlayer p,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){BuildVisuals.end();}
 @Inject(method="m_7523_(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",at=@At("RETURN"),remap=false)
 private void build$root(AbstractClientPlayer p,PoseStack poses,float age,float yaw,float partial,CallbackInfo ci){BuildVisuals.root(p,poses,partial);}
}
