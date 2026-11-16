package com.rpgtemplate.forge.mask.mixin;

import com.rpgtemplate.forge.mask.PerceptionMaskRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 佩戴认知阻碍面具乘坐矿车经过探测铁轨时，不触发探测铁轨的红石输出。 */
@Mixin(DetectorRailBlock.class)
public abstract class MaskDetectorRailMixin {
    @Inject(method = "getInteractingMinecartOfType(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Ljava/lang/Class;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true, remap = false)
    private <T extends AbstractMinecart> void mask$filterMaskedMinecarts(
            Level level, BlockPos pos, Class<T> clazz, Predicate<Entity> pred,
            CallbackInfoReturnable<List<T>> cir) {
        List<T> orig = cir.getReturnValue();
        if (orig == null || orig.isEmpty()) {
            return;
        }
        List<T> filtered = null;
        for (int i = 0; i < orig.size(); i++) {
            T cart = orig.get(i);
            if (PerceptionMaskRules.shouldIgnoreRedstone(cart)) {
                if (filtered == null) {
                    filtered = new ArrayList<>(orig.subList(0, i));
                }
            } else if (filtered != null) {
                filtered.add(cart);
            }
        }
        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }
}
