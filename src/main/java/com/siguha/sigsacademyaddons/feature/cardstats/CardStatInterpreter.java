package com.siguha.sigsacademyaddons.feature.cardstats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class CardStatInterpreter {

    private static final Pattern TIER_SUFFIX_PATTERN = Pattern.compile("_t\\d+$");
    private static final Path SCALARS_PATH = resolveScalarsPath();
    private static final CardScalarTables DEFAULT_SCALARS = CardScalarTables.of(Map.of(
            "base", List.of(0.2d, 0.4d, 0.6d, 0.8d, 1.0d, 1.2d, 1.4d, 1.6d, 1.8d, 2.0d)
    ));

    private static volatile CardScalarTables cachedScalars = DEFAULT_SCALARS;
    private static volatile FileTime cachedModifiedTime;

    Map<String, Double> collectCardStats(CompoundTag root) {
        return collectCardStats(root, loadScalarTables());
    }

    Map<String, Double> collectCardStats(CompoundTag root, CardScalarTables scalars) {
        CompoundTag components = root.getCompound("components");
        if (components.isEmpty()) return Collections.emptyMap();

        CompoundTag container = findContainerTag(components);
        if (container == null || !container.contains("items", Tag.TAG_LIST)) {
            return Collections.emptyMap();
        }

        Map<String, Double> stats = new LinkedHashMap<>();
        ListTag items = container.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag cardEntry = items.getCompound(i);
            parseCardEntry(cardEntry, scalars, stats);
        }

        return stats;
    }

    static String normalizeCardStatKey(String source) {
        if (source == null || source.isBlank()) return "";
        return TIER_SUFFIX_PATTERN.matcher(source).replaceFirst("");
    }

    static double evaluateModifierValue(CompoundTag modifier, int grade, CardScalarTables scalars) {
        if (!modifier.contains("attribute", Tag.TAG_COMPOUND)) return 0;

        CompoundTag attribute = modifier.getCompound("attribute");
        if (attribute.contains("value", Tag.TAG_COMPOUND)) {
            EvalResult result = evaluateNode(attribute.getCompound("value"), grade, scalars);
            return result.present() ? result.value() : 0;
        }

        EvalResult result = evaluateNode(attribute, grade, scalars);
        return result.present() ? result.value() : 0;
    }

    private void parseCardEntry(CompoundTag cardEntry, CardScalarTables scalars, Map<String, Double> stats) {
        CompoundTag cardData = findCardData(cardEntry);
        if (cardData == null || !cardData.contains("modifiers", Tag.TAG_LIST)) return;

        int grade = cardData.contains("grade", Tag.TAG_ANY_NUMERIC) ? cardData.getInt("grade") : 0;
        ListTag modifiers = cardData.getList("modifiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < modifiers.size(); i++) {
            CompoundTag modifier = modifiers.getCompound(i);
            String source = modifier.getString("source");
            if (source.isEmpty() || source.startsWith("minecraft:")) continue;

            String key = normalizeCardStatKey(source);
            if (key.isEmpty()) continue;

            double value = evaluateModifierValue(modifier, grade, scalars);
            if (value == 0) continue;

            stats.merge(key, value, Double::sum);
        }
    }

    private static EvalResult evaluateNode(CompoundTag node, int grade, CardScalarTables scalars) {
        if (node.contains("modifiers", Tag.TAG_LIST)) {
            EvalResult current = EvalResult.absent();
            ListTag modifiers = node.getList("modifiers", Tag.TAG_COMPOUND);
            for (int i = 0; i < modifiers.size(); i++) {
                current = applyModifier(modifiers.getCompound(i), current, grade, scalars);
            }
            return current;
        }

        if (node.contains("value", Tag.TAG_COMPOUND)) {
            return evaluateNode(node.getCompound("value"), grade, scalars);
        }

        if (node.contains("value", Tag.TAG_ANY_NUMERIC)) {
            return EvalResult.of(node.getDouble("value"));
        }

        return EvalResult.absent();
    }

    private static EvalResult applyModifier(CompoundTag node, EvalResult current, int grade,
                                            CardScalarTables scalars) {
        String type = node.getString("type");
        return switch (type) {
            case "assign" -> evaluateValue(node, grade, scalars);
            case "add" -> {
                EvalResult value = evaluateValue(node, grade, scalars);
                if (!value.present()) {
                    yield current;
                }
                if (!current.present()) {
                    yield value;
                }
                yield EvalResult.of(current.value() + value.value());
            }
            case "multiply" -> {
                EvalResult value = evaluateValue(node, grade, scalars);
                if (!value.present()) {
                    yield current;
                }
                if (!current.present()) {
                    yield value;
                }
                yield EvalResult.of(current.value() * value.value());
            }
            case "card_scalar" -> current.present()
                    ? EvalResult.of(current.value() * scalars.getScalar(node.getString("id"), grade))
                    : current;
            default -> evaluateValue(node, grade, scalars);
        };
    }

    private static EvalResult evaluateValue(CompoundTag node, int grade, CardScalarTables scalars) {
        if (node.contains("value", Tag.TAG_COMPOUND)) {
            return evaluateNode(node.getCompound("value"), grade, scalars);
        }

        if (node.contains("value", Tag.TAG_ANY_NUMERIC)) {
            return EvalResult.of(node.getDouble("value"));
        }

        return EvalResult.absent();
    }

    private static CardScalarTables loadScalarTables() {
        try {
            if (!Files.exists(SCALARS_PATH)) {
                return resetCachedScalars();
            }

            FileTime modifiedTime = Files.getLastModifiedTime(SCALARS_PATH);
            CardScalarTables local = cachedScalars;
            if (Objects.equals(modifiedTime, cachedModifiedTime) && local != null) {
                return local;
            }

            synchronized (CardStatInterpreter.class) {
                if (Objects.equals(modifiedTime, cachedModifiedTime) && cachedScalars != null) {
                    return cachedScalars;
                }

                try (Reader reader = Files.newBufferedReader(SCALARS_PATH)) {
                    CardScalarTables loaded = CardScalarTables.load(reader);
                    cachedScalars = loaded;
                    cachedModifiedTime = modifiedTime;
                    return loaded;
                }
            }
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.debug("[SAA CardStats] Failed to load academy card scalars", e);
            return cachedScalars;
        }
    }

    private static CardScalarTables resetCachedScalars() {
        cachedScalars = DEFAULT_SCALARS;
        cachedModifiedTime = null;
        return DEFAULT_SCALARS;
    }

    private static CompoundTag findContainerTag(CompoundTag components) {
        if (components.contains("academy:card_album_container", Tag.TAG_COMPOUND)) {
            return components.getCompound("academy:card_album_container");
        }
        for (String key : components.getAllKeys()) {
            if (!key.contains("card_album_container")) continue;
            Tag tag = components.get(key);
            if (tag instanceof CompoundTag compoundTag) {
                return compoundTag;
            }
        }
        return null;
    }

    private static CompoundTag findCardData(CompoundTag cardEntry) {
        if (cardEntry.contains("nbt", Tag.TAG_COMPOUND)) {
            CompoundTag nbt = cardEntry.getCompound("nbt");
            CompoundTag direct = directCardData(nbt);
            if (direct != null) return direct;
        }

        if (cardEntry.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag components = cardEntry.getCompound("components");
            CompoundTag direct = directCardData(components);
            if (direct != null) return direct;
        }

        if (cardEntry.contains("academy:card", Tag.TAG_COMPOUND)) {
            return cardEntry.getCompound("academy:card");
        }

        return null;
    }

    private static CompoundTag directCardData(CompoundTag root) {
        if (root.contains("academy:card", Tag.TAG_COMPOUND)) {
            return root.getCompound("academy:card");
        }
        for (String key : root.getAllKeys()) {
            if (!key.contains("card") || key.contains("container") || key.contains("album")) continue;
            Tag tag = root.get(key);
            if (tag instanceof CompoundTag compoundTag && compoundTag.contains("modifiers")) {
                return compoundTag;
            }
        }
        return null;
    }

    private static Path resolveScalarsPath() {
        try {
            return FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("academy")
                    .resolve("card")
                    .resolve("scalars.json");
        } catch (Exception ignored) {
            return Path.of("config", "academy", "card", "scalars.json");
        }
    }

    record EvalResult(double value, boolean present) {
        static EvalResult absent() {
            return new EvalResult(0, false);
        }

        static EvalResult of(double value) {
            return new EvalResult(value, true);
        }
    }

    static final class CardScalarTables {
        private final Map<String, List<Double>> values;

        private CardScalarTables(Map<String, List<Double>> values) {
            this.values = values;
        }

        static CardScalarTables of(Map<String, List<Double>> values) {
            Map<String, List<Double>> normalized = new LinkedHashMap<>();
            values.forEach((key, entryValues) -> normalized.put(key, List.copyOf(entryValues)));
            return new CardScalarTables(normalized);
        }

        static CardScalarTables load(Reader reader) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject valuesObject = root.has("values") ? root.getAsJsonObject("values") : new JsonObject();
            Map<String, List<Double>> values = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> entry : valuesObject.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;

                JsonArray array = entry.getValue().getAsJsonArray();
                List<Double> scalarValues = new ArrayList<>(array.size());
                for (JsonElement element : array) {
                    scalarValues.add(element.getAsDouble());
                }
                values.put(entry.getKey(), scalarValues);
            }

            if (values.isEmpty()) {
                return DEFAULT_SCALARS;
            }
            return of(values);
        }

        double getScalar(String id, int grade) {
            if (grade <= 0) return 1.0d;

            List<Double> scalarValues = values.get(id);
            if (scalarValues == null || grade > scalarValues.size()) {
                return 1.0d;
            }

            return scalarValues.get(grade - 1);
        }
    }
}
