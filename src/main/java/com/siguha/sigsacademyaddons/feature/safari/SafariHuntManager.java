package com.siguha.sigsacademyaddons.feature.safari;

import com.siguha.sigsacademyaddons.SigsAcademyAddons;
import com.siguha.sigsacademyaddons.data.HuntDataStore;
import com.siguha.sigsacademyaddons.handler.CatchDetector;
import com.siguha.sigsacademyaddons.handler.ScreenInterceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// parses hunts from the npc screen, tracks progress, and correlates catches
public class SafariHuntManager {

    // lore parsing patterns
    private static final Pattern CAUGHT_PATTERN = Pattern.compile("Caught:\\s*(\\d+)/(\\d+)");
    private static final Pattern RESET_PATTERN = Pattern.compile("Resets in (.+)");
    private static final Pattern TIME_PARTS_PATTERN = Pattern.compile("(\\d+)h\\s*(\\d+)m\\s*(\\d+)s");
    private static final Pattern STAR_PATTERN = Pattern.compile("[\\u2B50\\u2605\\u2606*]+");

    private final HuntDataStore dataStore;
    private List<SafariHuntData> activeHunts = new ArrayList<>();

    // unattributed catches since last screen scrape
    private int pendingUpdates = 0;

    // expiration check cooldown (~5 seconds = 100 ticks)
    private int expirationCheckCooldown = 0;

    public SafariHuntManager(HuntDataStore dataStore) {
        this.dataStore = dataStore;
        this.activeHunts = dataStore.load();
        int before = activeHunts.size();
        activeHunts.removeIf(SafariHuntData::isResetExpired);
        int removed = before - activeHunts.size();
        if (removed > 0) {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Removed {} expired hunts on load ({} remaining)",
                    removed, activeHunts.size());
            dataStore.save(activeHunts);
        } else if (!activeHunts.isEmpty()) {
            SigsAcademyAddons.LOGGER.info("[sig Safari] Loaded {} persisted hunts from disk", activeHunts.size());
        }
    }

    public void tick() {
        if (activeHunts.isEmpty()) return;

        expirationCheckCooldown--;
        if (expirationCheckCooldown > 0) return;
        expirationCheckCooldown = 100;

        int before = activeHunts.size();
        activeHunts.removeIf(SafariHuntData::isResetExpired);
        if (activeHunts.size() < before) {
            dataStore.save(activeHunts);
            SigsAcademyAddons.LOGGER.info("[sig Safari] Removed expired hunts — {} remaining", activeHunts.size());
        }
    }

    public void onHuntsScreenScraped(List<ScreenInterceptor.ScrapedHuntItem> scrapedItems) {
        List<SafariHuntData> parsedHunts = new ArrayList<>();

        for (ScreenInterceptor.ScrapedHuntItem item : scrapedItems) {
            try {
                SafariHuntData hunt = parseHuntItem(item);
                if (hunt != null) {
                    parsedHunts.add(hunt);
                    SigsAcademyAddons.LOGGER.debug("[sig Safari] Parsed hunt: {}", hunt);
                }
            } catch (Exception e) {
                SigsAcademyAddons.LOGGER.warn("[sig Safari] Failed to parse hunt item '{}': {}",
                        item.name(), e.getMessage());
            }
        }

        if (!parsedHunts.isEmpty()) {
            this.activeHunts = parsedHunts;
            this.pendingUpdates = 0; // fresh data from screen
            dataStore.save(activeHunts);
            SigsAcademyAddons.LOGGER.info("[sig Safari] Updated {} active hunts (pending updates cleared)", activeHunts.size());
        }
    }

    public void onHuntProgressUpdate() {
        pendingUpdates++;
        SigsAcademyAddons.LOGGER.info("[sig Safari] Hunt progress updated! ({} pending — open HUNTS NPC to refresh)",
                pendingUpdates);
    }

    public int getPendingUpdates() {
        return pendingUpdates;
    }

    public void onPokemonCaught(CatchDetector.CaughtPokemonInfo catchInfo) {
        if (activeHunts.isEmpty()) {
            return;
        }

        SigsAcademyAddons.LOGGER.debug("[sig Safari] Correlating catch: {} (types={}, eggGroups={})",
                catchInfo.speciesName(), catchInfo.types(), catchInfo.eggGroups());

        List<SafariHuntData> matchingHunts = new ArrayList<>();

        for (SafariHuntData hunt : activeHunts) {
            if (hunt.isComplete()) {
                continue;
            }

            if (doesCatchMatchHunt(catchInfo, hunt)) {
                matchingHunts.add(hunt);
            }
        }

        if (matchingHunts.isEmpty()) {
            SigsAcademyAddons.LOGGER.debug("[sig Safari] No matching hunts for caught Pokemon: {}",
                    catchInfo.speciesName());
            return;
        }

        for (SafariHuntData hunt : matchingHunts) {
            hunt.incrementCaught();
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Incremented hunt '{}' → {}",
                    hunt.getDisplayName(), hunt.getProgressString());
        }

        if (pendingUpdates > 0) {
            pendingUpdates--;
            SigsAcademyAddons.LOGGER.debug("[sig Safari] Catch attributed — pending updates: {}", pendingUpdates);
        }

        dataStore.save(activeHunts);
    }

    private boolean doesCatchMatchHunt(CatchDetector.CaughtPokemonInfo catchInfo, SafariHuntData hunt) {
        switch (hunt.getCategory()) {
            case TYPE:
                for (String target : hunt.getTargets()) {
                    for (String pokemonType : catchInfo.types()) {
                        if (pokemonType.equalsIgnoreCase(target.trim())) {
                            return true;
                        }
                    }
                }
                return false;

            case EGG_GROUP:
                // normalized comparison for egg groups (e.g. "Water 2" vs "WATER2")
                for (String target : hunt.getTargets()) {
                    String normalizedTarget = SigsAcademyAddons.normalizeForComparison(target);
                    for (String pokemonEggGroup : catchInfo.eggGroups()) {
                        String normalizedEggGroup = SigsAcademyAddons.normalizeForComparison(pokemonEggGroup);
                        if (normalizedEggGroup.equals(normalizedTarget)) {
                            return true;
                        }
                    }
                }
                return false;

            case UNKNOWN:
                return false;

            default:
                return false;
        }
    }

    // returns a sorted list of hunts by type hunt to egg hunt
    public List<SafariHuntData> getActiveHunts() {
        List<SafariHuntData> sorted = new ArrayList<>(activeHunts);
        sorted.sort(Comparator.comparingInt(h -> h.getCategory().ordinal()));
        return Collections.unmodifiableList(sorted);
    }

    public boolean hasActiveHunts() {
        return !activeHunts.isEmpty();
    }

    public void clearHunts() {
        activeHunts.clear();
        dataStore.save(activeHunts);
    }

    private SafariHuntData parseHuntItem(ScreenInterceptor.ScrapedHuntItem item) {
        String displayName = item.name();
        List<String> loreLines = item.loreLines();

        int caught = 0;
        int total = 0;
        String resetTimeText = "";
        List<String> rewards = new ArrayList<>();
        boolean inRewardsSection = false;

        for (String line : loreLines) {
            String cleanLine = stripFormatting(line);

            Matcher caughtMatcher = CAUGHT_PATTERN.matcher(cleanLine);
            if (caughtMatcher.find()) {
                caught = Integer.parseInt(caughtMatcher.group(1));
                total = Integer.parseInt(caughtMatcher.group(2));
                continue;
            }

            Matcher resetMatcher = RESET_PATTERN.matcher(cleanLine);
            if (resetMatcher.find()) {
                resetTimeText = resetMatcher.group(1).trim();
                continue;
            }

            if (cleanLine.contains("Rewards:")) {
                inRewardsSection = true;
                continue;
            }

            if (inRewardsSection && !cleanLine.isBlank()) {
                rewards.add(cleanLine.trim());
            }
        }

        String cleanName = stripFormatting(displayName);

        int stars = countStars(cleanName);
        String nameWithoutStars = STAR_PATTERN.matcher(cleanName).replaceAll("").trim();

        SafariHuntData.HuntCategory category;
        List<String> targets;

        if (nameWithoutStars.toLowerCase().contains("egg group")) {
            category = SafariHuntData.HuntCategory.EGG_GROUP;
            String eggGroupName = nameWithoutStars
                    .replaceAll("(?i)egg\\s*group", "")
                    .trim();
            targets = List.of(eggGroupName);
        } else if (nameWithoutStars.toLowerCase().contains("type")) {
            category = SafariHuntData.HuntCategory.TYPE;
            // "ice type" to "ice", "dark flying type" to ["dark", "flying"]
            String typePart = nameWithoutStars
                    .replaceAll("(?i)type", "")
                    .trim();
            targets = List.of(typePart.split("\\s+"));
        } else {
            category = SafariHuntData.HuntCategory.UNKNOWN;
            targets = List.of(nameWithoutStars);
        }

        long resetEndTimeMs = parseResetTimeToAbsolute(resetTimeText);

        return new SafariHuntData(
                cleanName, category, targets,
                caught, total, resetTimeText, resetEndTimeMs, stars, rewards
        );
    }

    private static long parseResetTimeToAbsolute(String resetTimeText) {
        if (resetTimeText == null || resetTimeText.isEmpty()) return 0;

        Matcher m = TIME_PARTS_PATTERN.matcher(resetTimeText);
        if (!m.find()) return 0;

        try {
            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));
            int seconds = Integer.parseInt(m.group(3));

            long totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L;

            // round down 5s for network delay
            totalMs = Math.max(0, totalMs - 5000L);

            return System.currentTimeMillis() + totalMs;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // strips section-sign formatting, thai resource-pack chars, and star unicode
    private static String stripFormatting(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("\u00A7[0-9a-fk-or]", "");
        stripped = stripped.replaceAll("[\\u0E00-\\u0E7F]", "");
        stripped = stripped.replaceAll("[\\u2B50\\u2605\\u2606]", "");
        return stripped.trim();
    }

    private static int countStars(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '\u2B50' || c == '\u2605' || c == '\u2606' || c == '*') {
                count++;
            }
            if (c == '\u0E47') {
                count++;
            }
        }
        return count;
    }
}
