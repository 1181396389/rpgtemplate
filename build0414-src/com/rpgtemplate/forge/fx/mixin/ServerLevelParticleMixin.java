package com.rpgtemplate.forge.fx.mixin;

import com.rpgtemplate.forge.fx.Fx;
import com.rpgtemplate.forge.fx.FxStyle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端粒子拦截：所有 ServerLevel.sendParticles 调用（包括没有源码的旧技能与实体）
 * 都改为发送非粒子图元（Fx 通道），不再发送原版粒子包。require=0：找不到目标时不生效，不会崩溃。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelParticleMixin {
    @Inject(method = "m_8767_(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpgtemplate$sendAsFx(ParticleOptions options, double x, double y, double z, int count,
                                      double dx, double dy, double dz, double speed,
                                      CallbackInfoReturnable<Integer> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        FxStyle style = FxStyle.of(options);
        Fx.burst(level, style.shape, style.rgb, style.size, style.life, style.spread,
                x, y, z, Math.max(1, Math.min(48, count)), dx, dy, dz);
        cir.setReturnValue(count);
    }
}
