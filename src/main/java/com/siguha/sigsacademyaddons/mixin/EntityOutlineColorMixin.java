package com.siguha.sigsacademyaddons.mixin;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.feature.hideout.GruntFinderTracker;
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
        Entity self = (Entity) (Object) this;

        if (self instanceof PokemonEntity pokemonEntity) {
            HuntEntityTracker tracker = SigsAcademyAddonsClient.getHuntEntityTracker();
            if (tracker == null) return;

            int color = tracker.getColor(pokemonEntity.getId());
            if (color != -1 && tracker.hasLineOfSight(pokemonEntity.getId())) {
                cir.setReturnValue(color);
            }
        } else if (self instanceof NPCEntity npc) {
            GruntFinderTracker gruntTracker = SigsAcademyAddonsClient.getGruntFinderTracker();
            if (gruntTracker == null) return;

            if (gruntTracker.isMatched(npc.getId())) {
                cir.setReturnValue(GruntFinderTracker.GRUNT_COLOR);
            }
        }
    }
}
