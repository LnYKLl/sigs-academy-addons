package com.siguha.sigsacademyaddons.mixin;

import com.siguha.sigsacademyaddons.feature.portal.PortalParticleDetector;
import com.siguha.sigsacademyaddons.handler.ParticleCapture;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), require = 0)
    private void saa_onCreateParticle(ParticleOptions particle, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       CallbackInfoReturnable<Particle> cir) {
        PortalParticleDetector.onParticle(particle, x, y, z);

        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onLevelParticle(particle, x, y, z, "engine");
    }
}
