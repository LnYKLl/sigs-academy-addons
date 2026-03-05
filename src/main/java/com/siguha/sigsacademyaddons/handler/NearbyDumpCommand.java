package com.siguha.sigsacademyaddons.handler;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class NearbyDumpCommand {

    public static String execute(int radius) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) return null;

        BlockPos center = player.blockPosition();
        int radiusSq = radius * radius;

        StringBuilder sb = new StringBuilder();
        sb.append("=== SAA Nearby Dump ===\n");
        sb.append("Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("Player: ").append(center.getX()).append(", ").append(center.getY()).append(", ").append(center.getZ()).append("\n");
        sb.append("Radius: ").append(radius).append("\n");
        sb.append("Dimension: ").append(level.dimension().location()).append("\n\n");

        dumpBlocks(sb, level, center, radius, radiusSq);
        dumpEntities(sb, level, player, center, radius);

        try {
            Path dumpDir = Path.of(SigsAcademyAddons.CONFIG_DIR, "dumps");
            Files.createDirectories(dumpDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dumpFile = dumpDir.resolve("nearby-dump-" + timestamp + ".txt");
            Files.writeString(dumpFile, sb.toString());
            return dumpFile.toAbsolutePath().toString();
        } catch (IOException e) {
            SigsAcademyAddons.LOGGER.error("[SAA] Failed to write dump file", e);
            return null;
        }
    }

    private static void dumpBlocks(StringBuilder sb, ClientLevel level, BlockPos center, int radius, int radiusSq) {
        sb.append("========== BLOCKS ==========\n\n");
        int blockCount = 0;
        int blockEntityCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    sb.append("Block @ ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
                    sb.append(" | ").append(blockId);

                    Map<Property<?>, Comparable<?>> properties = state.getValues();
                    if (!properties.isEmpty()) {
                        sb.append(" [");
                        boolean first = true;
                        for (Map.Entry<Property<?>, Comparable<?>> entry : properties.entrySet()) {
                            if (!first) sb.append(", ");
                            sb.append(entry.getKey().getName()).append("=").append(entry.getValue());
                            first = false;
                        }
                        sb.append("]");
                    }

                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntityCount++;
                        ResourceLocation beType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
                        sb.append(" BE=").append(beType);
                        try {
                            CompoundTag nbt = blockEntity.saveWithoutMetadata(level.registryAccess());
                            if (!nbt.isEmpty()) {
                                sb.append(" NBT=").append(nbt);
                            }
                        } catch (Exception e) {
                            sb.append(" NBT=[error: ").append(e.getMessage()).append("]");
                        }
                    }

                    sb.append("\n");
                    blockCount++;
                }
            }
        }

        sb.append("\nBlocks: ").append(blockCount).append(" | Block Entities: ").append(blockEntityCount).append("\n\n");
    }

    private static void dumpEntities(StringBuilder sb, ClientLevel level, LocalPlayer player, BlockPos center, int radius) {
        sb.append("========== ENTITIES ==========\n\n");

        AABB scanBox = new AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius + 1, center.getY() + radius + 1, center.getZ() + radius + 1
        );

        List<Entity> entities = level.getEntities(player, scanBox);
        int entityCount = 0;

        for (Entity entity : entities) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            sb.append("Entity: ").append(entityId).append("\n");
            sb.append("  Pos: ").append(String.format("%.2f, %.2f, %.2f", entity.getX(), entity.getY(), entity.getZ())).append("\n");
            sb.append("  UUID: ").append(entity.getUUID()).append("\n");
            sb.append("  ID: ").append(entity.getId()).append("\n");
            sb.append("  Class: ").append(entity.getClass().getName()).append("\n");

            if (entity.getCustomName() != null) {
                sb.append("  Name: ").append(entity.getCustomName().getString()).append("\n");
            }

            if (entity.getTags() != null && !entity.getTags().isEmpty()) {
                sb.append("  Tags: ").append(entity.getTags()).append("\n");
            }

            try {
                CompoundTag nbt = new CompoundTag();
                entity.saveWithoutId(nbt);
                sb.append("  NBT: ").append(nbt).append("\n");
            } catch (Exception e) {
                sb.append("  NBT: [error: ").append(e.getMessage()).append("]\n");
            }

            sb.append("\n");
            entityCount++;
        }

        sb.append("Total entities: ").append(entityCount).append("\n");
    }
}
