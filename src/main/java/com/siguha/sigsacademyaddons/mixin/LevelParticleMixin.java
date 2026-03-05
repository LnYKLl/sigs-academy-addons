package com.siguha.sigsacademyaddons.mixin;

import com.siguha.sigsacademyaddons.handler.ParticleCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class LevelParticleMixin {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"), require = 0)
    private void saa_onAddParticle8(ParticleOptions particle, boolean force,
                                     double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed,
                                     CallbackInfo ci) {
        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onLevelParticle(particle, x, y, z, "level-add8");
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"), require = 0)
    private void saa_onAddAlwaysVisible8(ParticleOptions particle, boolean force,
                                          double x, double y, double z,
                                          double xSpeed, double ySpeed, double zSpeed,
                                          CallbackInfo ci) {
        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onLevelParticle(particle, x, y, z, "level-always8");
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), require = 0)
    private void saa_onAddParticle7(ParticleOptions particle,
                                     double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed,
                                     CallbackInfo ci) {
        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onLevelParticle(particle, x, y, z, "level-add7");
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), require = 0)
    private void saa_onAddAlwaysVisible7(ParticleOptions particle,
                                          double x, double y, double z,
                                          double xSpeed, double ySpeed, double zSpeed,
                                          CallbackInfo ci) {
        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onLevelParticle(particle, x, y, z, "level-always7");
    }
}
