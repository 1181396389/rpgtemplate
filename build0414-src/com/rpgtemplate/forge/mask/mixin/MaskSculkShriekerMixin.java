package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 踩在幽匿尖啸体上也不会触发尖啸或召唤监守者。 */
@Mixin(SculkShriekerBlock.class)
public abstract class MaskSculkShriekerMixin {
    @Inject(method = "m_141947_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$blockShriekerStep(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (PerceptionMaskRules.shouldIgnoreRedstone(entity)) {
            ci.cancel();
        }
    }
}
