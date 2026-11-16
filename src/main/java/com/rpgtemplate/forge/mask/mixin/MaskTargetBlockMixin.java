package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 佩戴认知阻碍面具的玩家射出的投射物击中标靶方块时不会触发红石信号。 */
@Mixin(TargetBlock.class)
public abstract class MaskTargetBlockMixin {
    @Inject(method = "onProjectileHit(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/entity/projectile/Projectile;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$blockTargetSignal(Level level, BlockState state, BlockHitResult hit,
                                        Projectile projectile, CallbackInfo ci) {
        if (PerceptionMaskRules.shouldIgnoreRedstone(projectile)) {
            ci.cancel();
        }
    }
}
