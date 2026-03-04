package com.siguha.sigsacademyaddons.hud;

import com.siguha.sigsacademyaddons.config.HudConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

public class HudGroupRenderer {

    private static final int COLOR_BG = 0xAA000000;

    private final HudConfig hudConfig;
    private final Map<String, HudPanel> panelRegistry = new LinkedHashMap<>();

    public HudGroupRenderer(HudConfig hudConfig) {
        this.hudConfig = hudConfig;
    }

    public void registerPanel(HudPanel panel) {
        panelRegistry.put(panel.getPanelId(), panel);
    }

    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        List<String> groupOrder = hudConfig.getJoinedGroup();
        Set<String> inGroup = new HashSet<>(groupOrder);

        for (HudPanel panel : panelRegistry.values()) {
            if (!inGroup.contains(panel.getPanelId())) {
                panel.onHudRender(graphics, deltaTracker);
            }
        }

        if (groupOrder.size() >= 2) {
            renderGroup(graphics, client, groupOrder);
        }
    }

    private void renderGroup(GuiGraphics graphics, Minecraft client, List<String> groupOrder) {
        Font font = client.font;

        List<HudPanel> visiblePanels = new ArrayList<>();
        for (String id : groupOrder) {
            HudPanel panel = panelRegistry.get(id);
            if (panel != null && panel.shouldRender() && panel.hasVisibleContent()) {
                visiblePanels.add(panel);
            }
        }
        if (visiblePanels.isEmpty()) return;

        boolean transparent = hudConfig.getHudStyle() == HudConfig.HudStyle.TRANSPARENT;

        int maxContentWidth = 0;
        List<Integer> contentHeights = new ArrayList<>();
        for (HudPanel panel : visiblePanels) {
            maxContentWidth = Math.max(maxContentWidth, panel.getContentWidth(font));
            contentHeights.add(panel.getContentHeight(font));
        }

        int totalHeight = 0;
        for (int i = 0; i < contentHeights.size(); i++) {
            totalHeight += contentHeights.get(i);
        }

        float scale = hudConfig.getGroupScale();
        double guiScale = client.getWindow().getGuiScale();
        float minScale = (float) (6.0 / (8.0 * guiScale));
        float maxScale = (float) (32.0 / (8.0 * guiScale));
        scale = Math.max(minScale, Math.min(maxScale, scale));

        int scaledWidth = Math.round(maxContentWidth * scale);
        int scaledHeight = Math.round(totalHeight * scale);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int panelX = hudConfig.getGroupPanelX(screenWidth, scaledWidth);
        int panelY = hudConfig.getGroupPanelY(screenHeight, scaledHeight);

        graphics.pose().pushPose();
        graphics.pose().translate(panelX, panelY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        if (!transparent) {
            graphics.fill(0, 0, maxContentWidth, totalHeight, COLOR_BG);
        }

        int currentY = 0;
        for (int i = 0; i < visiblePanels.size(); i++) {
            HudPanel panel = visiblePanels.get(i);

            graphics.pose().pushPose();
            graphics.pose().translate(0, currentY, 0);
            panel.renderContent(graphics, font, maxContentWidth);
            graphics.pose().popPose();

            currentY += contentHeights.get(i);
        }

        graphics.pose().popPose();
    }
}
