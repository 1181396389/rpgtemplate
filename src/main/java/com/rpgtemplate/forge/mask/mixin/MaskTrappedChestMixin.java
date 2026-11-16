package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 佩戴认知阻碍面具打开陷阱箱时，不会令陷阱箱输出红石信号。 */
@Mixin(TrappedChestBlock.class)
public abstract class MaskTrappedChestMixin {
    @Inject(method = "getSignal(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
        at = @At("RETURN"), cancellable = true, remap = false)
    private void mask$suppressTrappedChestSignal(BlockState state, BlockGetter getter, BlockPos pos,
                                                 Direction dir, CallbackInfoReturnable<Integer> cir) {
        int raw = cir.getReturnValueI();
        if (raw <= 0 || !(getter instanceof Level level)) {
            return;
        }
        AABB box = new AABB(pos).inflate(7.5);
        List<Player> players = level.getEntitiesOfClass(Player.class, box);
        int maskedOpeners = 0;
        for (Player p : players) {
            if (p.containerMenu instanceof ChestMenu && PerceptionMaskRules.isActiveMaskWearer(p)) {
                maskedOpeners++;
            }
        }
        if (maskedOpeners > 0) {
            cir.setReturnValue(Math.max(0, raw - maskedOpeners));
        }
    }
}
