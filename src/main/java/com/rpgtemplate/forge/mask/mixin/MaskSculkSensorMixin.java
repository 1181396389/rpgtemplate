package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 踩在幽匿感测体/校频幽匿感测体上也不会触发红石信号。 */
@Mixin(SculkSensorBlock.class)
public abstract class MaskSculkSensorMixin {
    @Inject(method = "stepOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$blockSculkStep(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (PerceptionMaskRules.shouldIgnoreRedstone(entity)) {
            ci.cancel();
        }
    }
}
