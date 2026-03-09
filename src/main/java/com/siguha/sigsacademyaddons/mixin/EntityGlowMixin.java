package com.siguha.sigsacademyaddons.mixin;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.siguha.sigsacademyaddons.SigsAcademyAddonsClient;
import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.hideout.GruntFinderTracker;
import com.siguha.sigsacademyaddons.feature.safari.HuntEntityTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void sig_isCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        Entity self = (Entity) (Object) this;
        HudConfig config = SigsAcademyAddonsClient.getHudConfig();
        if (config == null) return;

        if (self instanceof PokemonEntity pokemonEntity) {
            if (!config.isSafariQuestMonGlow()) return;

            HuntEntityTracker tracker = SigsAcademyAddonsClient.getHuntEntityTracker();
            if (tracker == null) return;

            if (tracker.isMatched(pokemonEntity.getId()) && tracker.hasLineOfSight(pokemonEntity.getId())) {
                cir.setReturnValue(true);
            }
        } else if (self instanceof NPCEntity npc) {
            GruntFinderTracker gruntTracker = SigsAcademyAddonsClient.getGruntFinderTracker();
            if (gruntTracker == null) return;

            if (gruntTracker.isMatched(npc.getId())) {
                cir.setReturnValue(true);
            }
        }
    }
}
