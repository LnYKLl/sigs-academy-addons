package com.siguha.sigsacademyaddons.mixin;

import com.siguha.sigsacademyaddons.handler.ParticleCapture;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ParticlePacketMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void saa_onParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!ParticleCapture.isCapturing()) return;
        ParticleCapture.onParticlePacket(
                packet.getParticle(),
                packet.getX(), packet.getY(), packet.getZ(),
                packet.getXDist(), packet.getYDist(), packet.getZDist(),
                packet.getMaxSpeed(), packet.getCount(),
                packet.isOverrideLimiter()
        );
    }
}
