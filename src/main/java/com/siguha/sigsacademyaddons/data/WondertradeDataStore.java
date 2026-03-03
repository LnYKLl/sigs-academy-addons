package com.siguha.sigsacademyaddons.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

public class WondertradeDataStore {

    private static final String WT_FILE = "wt-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<WtData>() {}.getType();

    public void save(long cooldownEndTimeMs) {
        try {
            Path filePath = getFilePath();
            Files.createDirectories(filePath.getParent());

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();
            WtData data = new WtData(serverAddress, cooldownEndTimeMs);

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA WtDataStore] failed to save wt data", e);
        }
    }

    public long load() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return -1;
            }

            String serverAddress = SigsAcademyAddons.getCurrentServerAddress();

            try (Reader reader = Files.newBufferedReader(filePath)) {
                WtData data = GSON.fromJson(reader, DATA_TYPE);
                if (data == null || !serverAddress.equals(data.serverAddress)) {
                    return -1;
                }
                return data.cooldownEndTimeMs;
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.error("[SAA WtDataStore] failed to load wt data", e);
            return -1;
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(getFilePath());
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.warn("[SAA WtDataStore] failed to clear wt data", e);
        }
    }

    private Path getFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(SigsAcademyAddons.CONFIG_DIR)
                .resolve(WT_FILE);
    }

    private record WtData(String serverAddress, long cooldownEndTimeMs) {}
}
