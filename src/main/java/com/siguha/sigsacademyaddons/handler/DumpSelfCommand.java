package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

public class DumpSelfCommand {

    public static String execute() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("=== SAA Player Self-Dump ===\n");
        sb.append("Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("Dimension: ").append(level.dimension().location()).append("\n\n");

        dumpBasicInfo(sb, player);
        dumpFullNbt(sb, player);
        dumpSynchedEntityData(sb, player);
        dumpEffects(sb, player);
        dumpAttributes(sb, player);
        dumpScoreboard(sb, player, level);
        dumpTags(sb, player);

        try {
            Path dumpDir = Path.of(SigsAcademyAddons.CONFIG_DIR, "dumps");
            Files.createDirectories(dumpDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dumpFile = dumpDir.resolve("self-dump-" + timestamp + ".txt");
            Files.writeString(dumpFile, sb.toString());
            return dumpFile.toAbsolutePath().toString();
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.error("[SAA] Failed to write self-dump file", e);
            return null;
        }
    }

    private static void dumpBasicInfo(StringBuilder sb, LocalPlayer player) {
        sb.append("========== BASIC INFO ==========\n\n");
        sb.append("Name: ").append(player.getName().getString()).append("\n");
        sb.append("UUID: ").append(player.getUUID()).append("\n");
        sb.append("Entity ID: ").append(player.getId()).append("\n");
        sb.append("Class: ").append(player.getClass().getName()).append("\n");
        sb.append("Position: ").append(String.format("%.2f, %.2f, %.2f", player.getX(), player.getY(), player.getZ())).append("\n");
        sb.append("Block Position: ").append(player.blockPosition()).append("\n");
        sb.append("Rotation: yaw=").append(String.format("%.2f", player.getYRot()))
                .append(" pitch=").append(String.format("%.2f", player.getXRot())).append("\n");
        sb.append("Health: ").append(String.format("%.1f / %.1f", player.getHealth(), player.getMaxHealth())).append("\n");
        sb.append("Food: ").append(player.getFoodData().getFoodLevel())
                .append(" Saturation: ").append(String.format("%.1f", player.getFoodData().getSaturationLevel())).append("\n");
        sb.append("XP Level: ").append(player.experienceLevel)
                .append(" Progress: ").append(String.format("%.2f", player.experienceProgress))
                .append(" Total: ").append(player.totalExperience).append("\n");
        sb.append("Game Mode: ").append(player.isCreative() ? "Creative" : player.isSpectator() ? "Spectator" : "Survival/Adventure").append("\n");
        sb.append("On Ground: ").append(player.onGround()).append("\n");
        sb.append("In Water: ").append(player.isInWater()).append("\n");
        sb.append("Sprinting: ").append(player.isSprinting()).append("\n");
        sb.append("Sneaking: ").append(player.isShiftKeyDown()).append("\n");
        sb.append("Vehicle: ").append(player.getVehicle() != null ? player.getVehicle().getClass().getSimpleName() + " (id=" + player.getVehicle().getId() + ")" : "None").append("\n");
        sb.append("\n");
    }

    private static void dumpFullNbt(StringBuilder sb, LocalPlayer player) {
        sb.append("========== FULL ENTITY NBT ==========\n\n");
        try {
            CompoundTag nbt = new CompoundTag();
            player.saveWithoutId(nbt);

            sb.append("Top-level NBT keys: ").append(nbt.getAllKeys()).append("\n\n");

            sb.append(nbt.toString()).append("\n\n");

            sb.append("--- Potentially mod-specific keys ---\n");
            for (String key : nbt.getAllKeys()) {
                if (!isVanillaPlayerKey(key)) {
                    sb.append("  [MOD?] ").append(key).append(" = ").append(nbt.get(key)).append("\n");
                }
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("[Error serializing player NBT: ").append(e.getMessage()).append("]\n\n");
        }
    }

    private static void dumpSynchedEntityData(StringBuilder sb, LocalPlayer player) {
        sb.append("========== SYNCHED ENTITY DATA ==========\n\n");
        try {
            SynchedEntityData data = player.getEntityData();
            sb.append("Non-default values:\n");
            var items = data.getNonDefaultValues();
            if (items != null) {
                for (var item : items) {
                    sb.append("  id=").append(item.id()).append(" value=").append(item.value())
                            .append(" (").append(item.value().getClass().getSimpleName()).append(")\n");
                }
            } else {
                sb.append("  (all default values)\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("[Error reading synched data: ").append(e.getMessage()).append("]\n\n");
        }
    }

    private static void dumpEffects(StringBuilder sb, LocalPlayer player) {
        sb.append("========== ACTIVE EFFECTS ==========\n\n");
        Collection<MobEffectInstance> effects = player.getActiveEffects();
        if (effects.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (MobEffectInstance effect : effects) {
                sb.append("  ").append(effect.getEffect().value().getDescriptionId())
                        .append(" | Amp=").append(effect.getAmplifier())
                        .append(" Duration=").append(effect.getDuration())
                        .append(" Ambient=").append(effect.isAmbient())
                        .append(" Visible=").append(effect.isVisible())
                        .append("\n");
            }
        }
        sb.append("\n");
    }

    private static void dumpAttributes(StringBuilder sb, LocalPlayer player) {
        sb.append("========== ATTRIBUTES ==========\n\n");
        try {
            player.getAttributes().getSyncableAttributes().forEach(attr -> {
                sb.append("  ").append(attr.getAttribute().value().getDescriptionId())
                        .append(" = ").append(String.format("%.4f", attr.getValue()))
                        .append(" (base=").append(String.format("%.4f", attr.getBaseValue())).append(")");
                if (!attr.getModifiers().isEmpty()) {
                    sb.append(" modifiers=").append(attr.getModifiers());
                }
                sb.append("\n");
            });
        } catch (Exception e) {
            sb.append("[Error reading attributes: ").append(e.getMessage()).append("]\n");
        }
        sb.append("\n");
    }

    private static void dumpScoreboard(StringBuilder sb, LocalPlayer player, ClientLevel level) {
        sb.append("========== SCOREBOARD ==========\n\n");
        try {
            var scoreboard = level.getScoreboard();
            var teams = scoreboard.getPlayerTeam(player.getScoreboardName());
            sb.append("Player scoreboard name: ").append(player.getScoreboardName()).append("\n");
            sb.append("Team: ").append(teams != null ? teams.getName() + " (display: " + teams.getDisplayName().getString() + ")" : "None").append("\n");

            sb.append("Objectives:\n");
            var playerScores = scoreboard.listPlayerScores(player);
            for (var entry : playerScores.entrySet()) {
                sb.append("  ").append(entry.getKey().getName())
                        .append(" (").append(entry.getKey().getDisplayName().getString()).append(")")
                        .append(" = ").append(entry.getValue()).append("\n");
            }
            if (playerScores.isEmpty()) {
                // Fallback: just list objectives
                for (var objective : scoreboard.getObjectives()) {
                    sb.append("  ").append(objective.getName())
                            .append(" (").append(objective.getDisplayName().getString()).append(")\n");
                }
            }
        } catch (Exception e) {
            sb.append("[Error reading scoreboard: ").append(e.getMessage()).append("]\n");
        }
        sb.append("\n");
    }

    private static void dumpTags(StringBuilder sb, LocalPlayer player) {
        sb.append("========== ENTITY TAGS ==========\n\n");
        var tags = player.getTags();
        if (tags == null || tags.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (String tag : tags) {
                sb.append("  ").append(tag).append("\n");
            }
        }
        sb.append("\n");
    }

    private static boolean isVanillaPlayerKey(String key) {
        return switch (key) {
            case "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround",
                 "Invulnerable", "PortalCooldown", "UUID", "CustomName", "CustomNameVisible",
                 "Silent", "NoGravity", "Glowing", "TicksFrozen", "HasVisualFire",
                 "Tags", "Passengers",
                 // LivingEntity
                 "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount",
                 "Attributes", "ActiveEffects", "FallFlying", "SleepingX", "SleepingY", "SleepingZ",
                 "Brain",
                 // Player
                 "DataVersion", "Inventory", "EnderItems", "SelectedItemSlot",
                 "Score", "foodLevel", "foodExhaustionLevel", "foodSaturationLevel", "foodTickTimer",
                 "XpLevel", "XpP", "XpTotal", "XpSeed", "SpawnX", "SpawnY", "SpawnZ",
                 "SpawnForced", "SpawnDimension", "SpawnAngle",
                 "abilities", "recipeBook", "warden_spawn_tracker",
                 "previousPlayerGameType", "playerGameType",
                 "Dimension", "RootVehicle", "ShoulderEntityLeft", "ShoulderEntityRight",
                 "SeenCredits", "LastDeathLocation", "current_explosion_impact_pos",
                 "enteredNetherPosition" -> true;
            default -> false;
        };
    }
}
