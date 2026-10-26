package com.rpgtemplate.forge.production.client.mixin;
import com.rpgtemplate.forge.production.client.BuildVisuals;import net.minecraft.client.model.PlayerModel;import net.minecraft.world.entity.LivingEntity;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerModel.class)
public abstract class BuildPlayerModelMixin {
 @Inject(method="m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("RETURN"),remap=false)
 private void build$pose(LivingEntity entity,float limb,float amount,float age,float yaw,float pitch,CallbackInfo ci){BuildVisuals.model((PlayerModel<?>)(Object)this,entity);}
}
