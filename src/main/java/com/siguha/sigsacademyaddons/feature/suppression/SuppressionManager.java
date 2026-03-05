package com.siguha.sigsacademyaddons.feature.suppression;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class SuppressionManager {

    private static final ResourceKey<Level> RAIDS_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("raids", "raids")
    );
    private static final ResourceKey<Level> HIDEOUT_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("hideout", "hideouts")
    );
    private static final ResourceKey<Level> DUNGEON_DIM = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("dungeons", "dungeons")
    );

    private final HudConfig hudConfig;
    private boolean suppressed = false;

    public SuppressionManager(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void tick() {
        suppressed = computeSuppressed();
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    private boolean computeSuppressed() {
        if (hudConfig.isHudHidden()) {
            return true;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        ResourceKey<Level> currentDim = client.player.level().dimension();

        if (hudConfig.isSuppressInRaids() && RAIDS_DIM.equals(currentDim)) {
            return true;
        }
        if (hudConfig.isSuppressInHideouts() && HIDEOUT_DIM.equals(currentDim)) {
            return true;
        }
        if (hudConfig.isSuppressInDungeons() && DUNGEON_DIM.equals(currentDim)) {
            return true;
        }

        if (hudConfig.isSuppressInBattles()) {
            try {
                if (CobblemonClient.INSTANCE.getBattle() != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}
