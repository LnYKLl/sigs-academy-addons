package com.siguha.sigsacademyaddons.feature.portal;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class PortalParticleDetector {

    private static final int SCAN_DURATION_TICKS = 60;
    private static final double SCAN_RADIUS = 64.0;
    private static final int MIN_CLUSTER_SIZE = 20;
    private static final long PRESENCE_TIMEOUT_MS = 10_000;
    private static final double PRESENCE_RADIUS = 10.0;

    private static boolean scanning = false;
    private static int scanTicksRemaining = 0;
    private static final List<double[]> scanPositions = new ArrayList<>();
    private static PortalManager pendingManager = null;

    private static BlockPos trackedPortalPos = null;
    private static long lastSnowstormNearPortal = 0;

    private static ParticleType<?> snowstormType;
    private static boolean typeResolved = false;

    public static void startScan(PortalManager manager) {
        scanPositions.clear();
        scanning = true;
        scanTicksRemaining = SCAN_DURATION_TICKS;
        pendingManager = manager;
        SigsAcademyAddons.LOGGER.info("[SAA] Portal particle scan started ({}s, {}b radius)",
                SCAN_DURATION_TICKS / 20, (int) SCAN_RADIUS);
    }

    public static boolean isScanning() {
        return scanning;
    }

    public static void startTracking(BlockPos pos) {
        trackedPortalPos = pos;
        lastSnowstormNearPortal = System.currentTimeMillis();
        SigsAcademyAddons.LOGGER.info("[SAA] Started presence tracking at {} {} {}",
                pos.getX(), pos.getY(), pos.getZ());
    }

    public static void clearPresenceTracking() {
        trackedPortalPos = null;
        lastSnowstormNearPortal = 0;
    }

    public static void onParticle(ParticleOptions particle, double x, double y, double z) {
        if (!scanning && trackedPortalPos == null) return;

        if (!isSnowstormParticle(particle)) return;

        if (scanning) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                double dx = x - player.getX();
                double dy = y - player.getY();
                double dz = z - player.getZ();
                if (Math.abs(dx) <= SCAN_RADIUS && Math.abs(dy) <= SCAN_RADIUS && Math.abs(dz) <= SCAN_RADIUS) {
                    scanPositions.add(new double[]{x, y, z});
                }
            }
        }

        if (trackedPortalPos != null) {
            double dx = x - (trackedPortalPos.getX() + 0.5);
            double dy = y - (trackedPortalPos.getY() + 0.5);
            double dz = z - (trackedPortalPos.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < PRESENCE_RADIUS) {
                lastSnowstormNearPortal = System.currentTimeMillis();
            }
        }
    }

    private static boolean isSnowstormParticle(ParticleOptions particle) {
        if (!typeResolved) {
            typeResolved = true;
            try {
                ResourceLocation snowstormId = ResourceLocation.fromNamespaceAndPath("cobblemon", "snowstorm");
                snowstormType = BuiltInRegistries.PARTICLE_TYPE.get(snowstormId);
            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.warn("[SAA] Could not resolve cobblemon:snowstorm particle type", e);
                snowstormType = null;
            }
        }
        return snowstormType != null && particle.getType() == snowstormType;
    }

    public static void tick() {
        if (!scanning) return;
        scanTicksRemaining--;
        if (scanTicksRemaining <= 0) {
            scanning = false;
            BlockPos detected = analyzeCluster();
            if (pendingManager != null) {
                pendingManager.onScanComplete(detected);
            }
            if (detected != null) {
                SigsAcademyAddons.LOGGER.info("[SAA] Portal detected at {} {} {} ({} snowstorm particles in scan)",
                        detected.getX(), detected.getY(), detected.getZ(), scanPositions.size());
            } else {
                SigsAcademyAddons.LOGGER.warn("[SAA] Portal scan complete — no clusters found ({} snowstorm particles collected)",
                        scanPositions.size());
            }
            scanPositions.clear();
            pendingManager = null;
        }
    }

    public static boolean isPortalStillPresent() {
        if (trackedPortalPos == null) return true;
        return (System.currentTimeMillis() - lastSnowstormNearPortal) < PRESENCE_TIMEOUT_MS;
    }

    public static void clearTracking() {
        trackedPortalPos = null;
        lastSnowstormNearPortal = 0;
        scanning = false;
        scanPositions.clear();
        pendingManager = null;
    }

    private static BlockPos analyzeCluster() {
        if (scanPositions.isEmpty()) return null;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        Map<Integer, List<double[]>> clusters = new HashMap<>();
        for (double[] pos : scanPositions) {
            int roundedX = (int) Math.round(pos[0]);
            clusters.computeIfAbsent(roundedX, k -> new ArrayList<>()).add(pos);
        }

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (Map.Entry<Integer, List<double[]>> entry : clusters.entrySet()) {
            List<double[]> cluster = entry.getValue();
            if (cluster.size() < MIN_CLUSTER_SIZE) continue;

            double avgX = 0, avgY = 0, avgZ = 0;
            for (double[] pos : cluster) {
                avgX += pos[0];
                avgY += pos[1];
                avgZ += pos[2];
            }
            avgX /= cluster.size();
            avgY /= cluster.size();
            avgZ /= cluster.size();

            double dx = avgX - player.getX();
            double dz = avgZ - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            SigsAcademyAddons.LOGGER.info("[SAA] Cluster at X={}: {} particles, centroid ({}, {}, {}), distance={}",
                    entry.getKey(), cluster.size(),
                    String.format("%.1f", avgX), String.format("%.1f", avgY), String.format("%.1f", avgZ),
                    String.format("%.1f", dist));

            if (dist < bestDist) {
                bestDist = dist;
                best = new BlockPos((int) Math.round(avgX), (int) Math.round(avgY), (int) Math.round(avgZ));
            }
        }

        return best;
    }
}
