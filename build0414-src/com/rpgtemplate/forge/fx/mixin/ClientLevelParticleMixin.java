package com.rpgtemplate.forge.fx.mixin;

import com.rpgtemplate.forge.fx.FxStyle;
import com.rpgtemplate.forge.fx.client.FxRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端粒子拦截：ClientLevel.addParticle（所有变体）改为本地生成非粒子图元。仅客户端加载。
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleMixin {
    @Inject(method = "m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpgtemplate$addFx(ParticleOptions options, double x, double y, double z,
                                   double vx, double vy, double vz, CallbackInfo ci) {
        FxRenderer.spawnLocal(FxStyle.of(options), x, y, z, vx, vy, vz);
        ci.cancel();
    }

    @Inject(method = "m_7107_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpgtemplate$addAlwaysFx(ParticleOptions options, double x, double y, double z,
                                         double vx, double vy, double vz, CallbackInfo ci) {
        FxRenderer.spawnLocal(FxStyle.of(options), x, y, z, vx, vy, vz);
        ci.cancel();
    }

    @Inject(method = "m_6493_(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpgtemplate$addForcedFx(ParticleOptions options, boolean force, double x, double y, double z,
                                         double vx, double vy, double vz, CallbackInfo ci) {
        FxRenderer.spawnLocal(FxStyle.of(options), x, y, z, vx, vy, vz);
        ci.cancel();
    }

    @Inject(method = "m_6485_(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpgtemplate$addForcedAlwaysFx(ParticleOptions options, boolean force, double x, double y, double z,
                                               double vx, double vy, double vz, CallbackInfo ci) {
        FxRenderer.spawnLocal(FxStyle.of(options), x, y, z, vx, vy, vz);
        ci.cancel();
    }
}
