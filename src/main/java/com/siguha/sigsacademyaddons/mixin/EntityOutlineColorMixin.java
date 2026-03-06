package com.siguha.sigsacademyaddons.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityOutlineColorMixin {

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void sig_getTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof PokemonEntity pokemonEntity)) return;

        HuntEntityTracker tracker = SigsAcademyAddonsClient.getHuntEntityTracker();
        if (tracker == null) return;

        int color = tracker.getColor(pokemonEntity.getId());
        if (color != -1 && tracker.hasLineOfSight(pokemonEntity.getId())) {
            cir.setReturnValue(color);
        }
    }
}
