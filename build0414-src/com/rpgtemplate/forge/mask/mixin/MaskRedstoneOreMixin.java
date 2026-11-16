package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 踩踏或轻触红石矿石不会令其发光更新（防止触发侦测器等红石陷阱）。 */
@Mixin(RedStoneOreBlock.class)
public abstract class MaskRedstoneOreMixin {
    @Inject(method = "m_141947_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$blockOreStep(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (PerceptionMaskRules.shouldIgnoreRedstone(entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_6256_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void mask$blockOreAttack(BlockState state, Level level, BlockPos pos, Player player, CallbackInfo ci) {
        if (PerceptionMaskRules.isActiveMaskWearer(player)) {
            ci.cancel();
        }
    }
}
