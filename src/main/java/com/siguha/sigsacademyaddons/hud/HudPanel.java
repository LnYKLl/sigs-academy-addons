package com.siguha.sigsacademyaddons.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public interface HudPanel {

    String getPanelId();

    boolean shouldRender();

    boolean hasVisibleContent();

    int getContentWidth(Font font);

    int getContentHeight(Font font);

    default int getContentHeight(Font font, int panelWidth) {
        return getContentHeight(font);
    }

    void renderContent(GuiGraphics graphics, Font font, int panelWidth);

    void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker);
}
