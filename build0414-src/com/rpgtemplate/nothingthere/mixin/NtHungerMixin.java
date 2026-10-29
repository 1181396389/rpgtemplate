package com.rpgtemplate.nothingthere.mixin;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import com.rpgtemplate.nothingthere.NtPassives;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 延缓饥饿：当玩家拥有延缓饥饿被动时，causeFoodExhaustion 的增量会乘以 exhaustion_multiplier（默认 0.5）。
 * 只在服务端生效。客户端不需要这个修改，饱食度由服务端决定。
 */
@Mixin(Player.class)
public abstract class NtHungerMixin {
    @ModifyVariable(method = "m_36399_(F)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private float nt$slowHunger(float amount) {
        Player self = (Player) (Object) this;
        if (self.m_9236_().m_5776_() || !SystemDataManager.available(self)) {
            return amount;
        }
        PlayerSystemData data = SystemDataManager.get(self);
        if (data == null || !data.isActivated()) {
            return amount;
        }
        return TraitLookup.activePassive(data, NtPassives.HUNGER_SAVE)
            .map(trait -> amount * (float) TraitLookup.param(trait, "exhaustion_multiplier", 0.5))
            .orElse(amount);
    }
}
