package com.rpgtemplate.cronus.mixin;

import com.rpgtemplate.cronus.client.CronusClientFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 【Pause】客户端真·时空定格 Mixin：
 * 当服务端同步确认某实体处于完全时停（FULL）状态时，暂停其客户端 tick 与位置/朝向插值推进，
 * 使怪物、箭矢、火球、TNT、下落方块、掉落物在肉眼视觉上完全凝固成时空雕像（绝不触碰任何原版粒子系统）。
 */
@Mixin(ClientLevel.class)
public abstract class CronusClientLevelFreezeMixin {
    @Inject(method = "m_104639_(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void rpgtemplate$freezeClientEntityTick(Entity entity, CallbackInfo ci) {
        if (entity == null || entity instanceof LocalPlayer) {
            return;
        }
        if (!CronusClientFx.isEntityFrozen(entity.m_19879_())) {
            return;
        }
        // 锁定上一帧插值锚点，彻底消除客户端残余晃动与位移插值
        entity.f_19854_ = entity.m_20185_();
        entity.f_19855_ = entity.m_20186_();
        entity.f_19856_ = entity.m_20189_();
        entity.f_19859_ = entity.m_146908_();
        entity.f_19860_ = entity.m_146909_();
        if (entity instanceof LivingEntity le) {
            le.f_20884_ = le.f_20883_;
            le.f_20886_ = le.f_20885_;
            le.f_20920_ = le.f_20921_;
        }
        ci.cancel();
    }
}
