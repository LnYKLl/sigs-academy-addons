package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager;
import com.siguha.sigsacademyaddons.feature.cardstats.CardStatsManager.StatEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class CardStatsHudRenderer implements HudPanel {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_SPACING = 6;
    private static final int PANEL_MIN_WIDTH = 120;

    private static final int COLOR_BG = 0xAA000000;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_SECTION_HEADER = 0xFF55FFFF;
    private static final int COLOR_STAT_NAME = 0xFFFFFFFF;
    private static final int COLOR_STAT_VALUE = 0xFF55FF55;

    private final CardStatsManager cardStatsManager;
    private final HudConfig hudConfig;

    public CardStatsHudRenderer(CardStatsManager cardStatsManager, HudConfig hudConfig) {
        this.cardStatsManager = cardStatsManager;
        this.hudConfig = hudConfig;
    }

    @Override
    public String getPanelId() {
        return "cardstats";
    }

    @Override
    public boolean shouldRender() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return false;
        if (!hudConfig.isCardStatsMenuEnabled()) return false;
        return cardStatsManager.hasCardAlbum() && cardStatsManager.hasAnyStats();
    }

    @Override
    public boolean hasVisibleContent() {
        return shouldRender();
    }

    @Override
    public int getContentWidth(Font font) {
        if (hudConfig.isCompact()) {
            return calculateCompactWidth(font);
        }
        return calculateFullWidth(font);
    }

    @Override
    public int getContentHeight(Font font) {
        if (hudConfig.isCompact()) {
            return calculateCompactHeight();
        }
        return calculateFullHeight();
    }

    @Override
    public void renderContent(GuiGraphics graphics, Font font, int panelWidth) {
        if (hudConfig.isCompact()) {
            renderCompact(graphics, font, panelWidth);
        } else {
            renderFull(graphics, font, panelWidth);
        }
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!shouldRender()) return;
        if (hudConfig.isInGroup("cardstats")) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float scale = hudConfig.getCardStatsScale();

        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int panelWidth = getContentWidth(font);
        int panelHeight = getContentHeight(font);

        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);

        int panelX = hudConfig.getCardStatsPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getCardStatsPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, panelWidth, panelHeight, COLOR_BG);
        }

        renderContent(graphics, font, panelWidth);

        graphics.pose().popPose();
    }

    private void renderFull(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;

        String header = "SAA Stats";
        int headerWidth = font.width(header);
        graphics.drawString(font, header, (panelWidth - headerWidth) / 2, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        if (!playerStats.isEmpty()) {
            y += 2;
            graphics.fill(PADDING, y, panelWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;

            graphics.drawString(font, "Player", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : playerStats) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }

        if (!cardStatsList.isEmpty()) {
            y += 2;
            graphics.fill(PADDING, y, panelWidth - PADDING, y + 1, 0xFF555555);
            y += SECTION_SPACING;

            graphics.drawString(font, "Cards", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : cardStatsList) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }
    }

    private void renderCompact(GuiGraphics graphics, Font font, int panelWidth) {
        int y = PADDING;

        String title = "Stats";
        graphics.drawString(font, title, PADDING, y, COLOR_HEADER, true);
        y += LINE_HEIGHT;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        if (!playerStats.isEmpty()) {
            graphics.drawString(font, "Player", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : playerStats) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }

        if (!cardStatsList.isEmpty()) {
            graphics.drawString(font, "Cards", PADDING, y, COLOR_SECTION_HEADER, true);
            y += LINE_HEIGHT;

            for (StatEntry entry : cardStatsList) {
                y = renderStatLine(graphics, font, y, panelWidth, entry);
            }
        }
    }

    private int renderStatLine(GuiGraphics graphics, Font font, int y, int panelWidth, StatEntry entry) {
        graphics.drawString(font, entry.displayName(), PADDING + 2, y, COLOR_STAT_NAME, true);

        String valueStr = CardStatsManager.formatValue(entry);
        int valueWidth = font.width(valueStr);
        graphics.drawString(font, valueStr, panelWidth - PADDING - valueWidth, y, COLOR_STAT_VALUE, true);

        return y + LINE_HEIGHT;
    }

    private int calculateFullWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        for (StatEntry entry : playerStats) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        for (StatEntry entry : cardStatsList) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        String header = "SAA Stats";
        maxWidth = Math.max(maxWidth, font.width(header) + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Player") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Cards") + PADDING * 2);

        return maxWidth;
    }

    private int calculateFullHeight() {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += LINE_HEIGHT;

        if (!playerStats.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += playerStats.size() * LINE_HEIGHT;
        }

        if (!cardStatsList.isEmpty()) {
            height += 2 + SECTION_SPACING;
            height += LINE_HEIGHT;
            height += cardStatsList.size() * LINE_HEIGHT;
        }

        height += PADDING;
        return height;
    }

    private int calculateCompactWidth(Font font) {
        int maxWidth = PANEL_MIN_WIDTH;

        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        maxWidth = Math.max(maxWidth, font.width("Stats") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Player") + PADDING * 2);
        maxWidth = Math.max(maxWidth, font.width("Cards") + PADDING * 2);

        for (StatEntry entry : playerStats) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        for (StatEntry entry : cardStatsList) {
            int lineWidth = font.width(entry.displayName()) + 8 + font.width(CardStatsManager.formatValue(entry));
            maxWidth = Math.max(maxWidth, lineWidth + PADDING * 2 + 2);
        }

        return maxWidth;
    }

    private int calculateCompactHeight() {
        List<StatEntry> playerStats = cardStatsManager.getPlayerStats();
        List<StatEntry> cardStatsList = cardStatsManager.getCardStats();

        int height = PADDING;
        height += LINE_HEIGHT;
        if (!playerStats.isEmpty()) {
            height += LINE_HEIGHT;
            height += playerStats.size() * LINE_HEIGHT;
        }
        if (!cardStatsList.isEmpty()) {
            height += LINE_HEIGHT;
            height += cardStatsList.size() * LINE_HEIGHT;
        }
        height += PADDING;
        return height;
    }
}
