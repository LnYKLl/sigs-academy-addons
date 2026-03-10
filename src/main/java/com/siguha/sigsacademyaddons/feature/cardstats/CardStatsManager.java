package com.siguha.sigsacademyaddons.feature.cardstats;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class CardStatsManager {

    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final String CARD_ALBUM_ID = "academy:card_album";
    private static final String ACADEMY_MODIFIER_NAMESPACE = "academy";

    private static final Map<String, String> STAT_DISPLAY_NAMES = new LinkedHashMap<>();

    static {
        STAT_DISPLAY_NAMES.put("movement_speed", "Movement Speed");
        STAT_DISPLAY_NAMES.put("block_interaction_range", "Block Reach");
        STAT_DISPLAY_NAMES.put("block_break_speed", "Block Break Speed");
        STAT_DISPLAY_NAMES.put("armor", "Armor");
        STAT_DISPLAY_NAMES.put("armor_toughness", "Armor Toughness");
        STAT_DISPLAY_NAMES.put("max_health", "Max Health");
        STAT_DISPLAY_NAMES.put("attack_speed", "Attack Speed");
        STAT_DISPLAY_NAMES.put("water_movement_efficiency", "Water Speed");
        STAT_DISPLAY_NAMES.put("submerged_mining_speed", "Underwater Mining");
        STAT_DISPLAY_NAMES.put("safe_fall_distance", "Safe Fall Dist.");
        STAT_DISPLAY_NAMES.put("attack_damage", "Attack Damage");
        STAT_DISPLAY_NAMES.put("knockback_resistance", "Knockback Res.");
        STAT_DISPLAY_NAMES.put("movement_efficiency", "Move Efficiency");
        STAT_DISPLAY_NAMES.put("oxygen_bonus", "Oxygen Bonus");
        STAT_DISPLAY_NAMES.put("sneaking_speed", "Sneak Speed");
        STAT_DISPLAY_NAMES.put("mining_efficiency", "Mining Efficiency");
        STAT_DISPLAY_NAMES.put("entity_interaction_range", "Entity Reach");
        STAT_DISPLAY_NAMES.put("luck", "Luck");
        STAT_DISPLAY_NAMES.put("jump_strength", "Jump Strength");
        STAT_DISPLAY_NAMES.put("scale", "Scale");
        STAT_DISPLAY_NAMES.put("step_height", "Step Height");
        STAT_DISPLAY_NAMES.put("gravity", "Gravity");

        STAT_DISPLAY_NAMES.put("capture_experience", "Capture XP");
        STAT_DISPLAY_NAMES.put("shiny_chance", "Shiny Chance");
        STAT_DISPLAY_NAMES.put("type_spawn_chance", "Type Spawn Chance");
        STAT_DISPLAY_NAMES.put("ev_yield", "EV Yield");
        STAT_DISPLAY_NAMES.put("rare_shiny_chance", "Rare Shiny Chance");
    }

    public record StatEntry(String displayName, double value, AttributeModifier.Operation operation) {}

    private volatile List<StatEntry> playerStats = Collections.emptyList();
    private volatile List<StatEntry> cardStats = Collections.emptyList();
    private volatile boolean hasCardAlbum = false;
    private int tickCounter = 0;

    @SuppressWarnings("unchecked")
    private static final Holder<Attribute>[] VANILLA_ATTRIBUTES = new Holder[] {
            Attributes.MOVEMENT_SPEED,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.MAX_HEALTH,
            Attributes.ATTACK_SPEED,
            Attributes.ATTACK_DAMAGE,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.LUCK,
            Attributes.SAFE_FALL_DISTANCE,
            Attributes.JUMP_STRENGTH,
            Attributes.SCALE,
            Attributes.STEP_HEIGHT,
            Attributes.GRAVITY,
            Attributes.OXYGEN_BONUS,
    };

    public void tick() {
        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            playerStats = Collections.emptyList();
            cardStats = Collections.emptyList();
            hasCardAlbum = false;
            return;
        }

        scanPlayerAttributes(player);
        scanCardAlbum(player);
    }

    private void scanPlayerAttributes(LocalPlayer player) {
        List<StatEntry> stats = new ArrayList<>();

        for (Holder<Attribute> attrHolder : VANILLA_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attrHolder);
            if (instance == null) continue;

            for (AttributeModifier modifier : instance.getModifiers()) {
                if (modifier.id().getNamespace().equals(ACADEMY_MODIFIER_NAMESPACE)) {
                    String attrKey = extractStatKey(attrHolder.unwrapKey()
                            .map(key -> key.location().getPath())
                            .orElse("unknown"));
                    String displayName = getDisplayName(attrKey);
                    stats.add(new StatEntry(displayName, modifier.amount(), modifier.operation()));
                }
            }
        }

        checkPlayerAttribute(player, "player.block_interaction_range", stats);
        checkPlayerAttribute(player, "player.block_break_speed", stats);
        checkPlayerAttribute(player, "player.submerged_mining_speed", stats);
        checkPlayerAttribute(player, "player.sneaking_speed", stats);
        checkPlayerAttribute(player, "player.mining_efficiency", stats);
        checkPlayerAttribute(player, "player.entity_interaction_range", stats);
        checkPlayerAttribute(player, "generic.water_movement_efficiency", stats);
        checkPlayerAttribute(player, "generic.movement_efficiency", stats);

        playerStats = stats;
    }

    private void checkPlayerAttribute(LocalPlayer player, String path, List<StatEntry> stats) {
        ResourceLocation attrId = ResourceLocation.withDefaultNamespace(path);
        Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attrId);
        if (holder.isEmpty()) return;

        AttributeInstance instance = player.getAttribute(holder.get());
        if (instance == null) return;

        String attrKey = extractStatKey(path);
        String displayName = getDisplayName(attrKey);
        boolean alreadyAdded = stats.stream().anyMatch(e -> e.displayName().equals(displayName));
        if (alreadyAdded) return;

        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().getNamespace().equals(ACADEMY_MODIFIER_NAMESPACE)) {
                stats.add(new StatEntry(displayName, modifier.amount(), modifier.operation()));
            }
        }
    }

    private void scanCardAlbum(LocalPlayer player) {
        ItemStack albumStack = findCardAlbum(player);
        if (albumStack == null || albumStack.isEmpty()) {
            hasCardAlbum = false;
            cardStats = Collections.emptyList();
            return;
        }

        hasCardAlbum = true;

        try {
            Tag savedTag = albumStack.save(player.registryAccess());
            if (!(savedTag instanceof CompoundTag root)) {
                cardStats = Collections.emptyList();
                return;
            }

            cardStats = parseCardAlbumNbt(root);
        } catch (Exception e) {
            SigsAcademyAddons.LOGGER.debug("[SAA CardStats] Failed to parse card album NBT", e);
            cardStats = Collections.emptyList();
        }
    }

    private List<StatEntry> parseCardAlbumNbt(CompoundTag root) {
        Map<String, Double> cobblemonStats = new LinkedHashMap<>();

        CompoundTag components = root.getCompound("components");
        if (components.isEmpty()) return Collections.emptyList();

        CompoundTag container = findContainerTag(components);
        if (container == null || !container.contains("items", Tag.TAG_LIST)) {
            return Collections.emptyList();
        }

        ListTag items = container.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag cardEntry = items.getCompound(i);
            parseCardEntry(cardEntry, cobblemonStats);
        }

        List<StatEntry> stats = new ArrayList<>();
        for (Map.Entry<String, Double> entry : cobblemonStats.entrySet()) {
            String displayName = getDisplayName(entry.getKey());
            stats.add(new StatEntry(displayName, entry.getValue(),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        return stats;
    }

    private CompoundTag findContainerTag(CompoundTag components) {
        if (components.contains("academy:card_album_container", Tag.TAG_COMPOUND)) {
            return components.getCompound("academy:card_album_container");
        }
        for (String key : components.getAllKeys()) {
            if (key.contains("card_album_container")) {
                Tag tag = components.get(key);
                if (tag instanceof CompoundTag ct) return ct;
            }
        }
        return null;
    }

    private void parseCardEntry(CompoundTag cardEntry, Map<String, Double> cobblemonStats) {
        CompoundTag cardData = findCardData(cardEntry);
        if (cardData == null) return;

        if (!cardData.contains("modifiers", Tag.TAG_LIST)) return;

        ListTag modifiers = cardData.getList("modifiers", Tag.TAG_COMPOUND);
        for (int j = 0; j < modifiers.size(); j++) {
            CompoundTag modifier = modifiers.getCompound(j);
            String source = modifier.getString("source");
            if (source.isEmpty()) continue;

            if (source.startsWith("minecraft:")) continue;

            String baseStatKey = stripTierSuffix(source);
            double value = extractModifierValue(modifier);

            cobblemonStats.merge(baseStatKey, value, Double::sum);
        }
    }

    private CompoundTag findCardData(CompoundTag cardEntry) {
        if (cardEntry.contains("nbt", Tag.TAG_COMPOUND)) {
            CompoundTag nbt = cardEntry.getCompound("nbt");
            if (nbt.contains("academy:card", Tag.TAG_COMPOUND)) {
                return nbt.getCompound("academy:card");
            }
            for (String key : nbt.getAllKeys()) {
                if (key.contains("card") && !key.contains("container") && !key.contains("album")) {
                    Tag tag = nbt.get(key);
                    if (tag instanceof CompoundTag ct && ct.contains("modifiers")) return ct;
                }
            }
        }

        if (cardEntry.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag comps = cardEntry.getCompound("components");
            if (comps.contains("academy:card", Tag.TAG_COMPOUND)) {
                return comps.getCompound("academy:card");
            }
            for (String key : comps.getAllKeys()) {
                if (key.contains("card") && !key.contains("container") && !key.contains("album")) {
                    Tag tag = comps.get(key);
                    if (tag instanceof CompoundTag ct && ct.contains("modifiers")) return ct;
                }
            }
        }

        if (cardEntry.contains("academy:card", Tag.TAG_COMPOUND)) {
            return cardEntry.getCompound("academy:card");
        }

        return null;
    }

    private double extractModifierValue(CompoundTag modifier) {
        if (!modifier.contains("attribute", Tag.TAG_COMPOUND)) return 0;
        CompoundTag attribute = modifier.getCompound("attribute");

        if (!attribute.contains("value", Tag.TAG_COMPOUND)) return 0;
        CompoundTag value = attribute.getCompound("value");

        if (!value.contains("modifiers", Tag.TAG_LIST)) return 0;
        ListTag valueMods = value.getList("modifiers", Tag.TAG_COMPOUND);

        for (int k = 0; k < valueMods.size(); k++) {
            CompoundTag valueMod = valueMods.getCompound(k);
            String type = valueMod.getString("type");
            if (type.equals("assign")) {
                if (valueMod.contains("value", Tag.TAG_COMPOUND)) {
                    CompoundTag assignValue = valueMod.getCompound("value");
                    if (assignValue.contains("value")) {
                        return assignValue.getDouble("value");
                    }
                }
                if (valueMod.contains("value")) {
                    return valueMod.getDouble("value");
                }
            }
        }

        return 0;
    }

    private ItemStack findCardAlbum(LocalPlayer player) {
        for (Slot slot : player.inventoryMenu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.equals(CARD_ALBUM_ID)) {
                return stack;
            }
        }
        return null;
    }

    static String stripTierSuffix(String source) {
        return source.replaceAll("_t\\d+$", "");
    }

    static String extractStatKey(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0) {
            path = path.substring(lastDot + 1);
        }
        return stripTierSuffix(path);
    }

    static String getDisplayName(String statKey) {
        String name = STAT_DISPLAY_NAMES.get(statKey);
        if (name != null) return name;

        String[] parts = statKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    public static String formatValue(StatEntry entry) {
        double value = entry.value();
        if (entry.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                || entry.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            double pct = value * 100.0;
            if (pct == Math.floor(pct)) {
                return String.format("+%.0f%%", pct);
            }
            return String.format("+%.1f%%", pct);
        }
        if (value == Math.floor(value)) {
            return String.format("+%.0f", value);
        }
        return String.format("+%.2f", value);
    }

    public List<StatEntry> getPlayerStats() {
        return playerStats;
    }

    public List<StatEntry> getCardStats() {
        return cardStats;
    }

    public boolean hasCardAlbum() {
        return hasCardAlbum;
    }

    public boolean hasAnyStats() {
        return !playerStats.isEmpty() || !cardStats.isEmpty();
    }
}
