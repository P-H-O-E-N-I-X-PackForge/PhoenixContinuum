package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A button in the Continuum map's own style (the one the mission planner uses), for screens that use widgets. */
final class MapButton extends Button {

    private boolean selected;

    private MapButton(int x, int y, int w, int h, String label, OnPress onPress) {
        super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
    }

    static MapButton of(int x, int y, int w, int h, String label, OnPress onPress) {
        return new MapButton(x, y, w, h, label, onPress);
    }

    MapButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    MapButton enabled(boolean enabled) {
        this.active = enabled;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHovered();
        int fill = !active ? MapUi.BUTTON_OFF : selected ? MapUi.BUTTON_ON : hover ? MapUi.BUTTON_HOVER :
                MapUi.BUTTON;
        g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        g.renderOutline(getX(), getY(), width, height,
                active ? (selected ? MapUi.FRAME : MapUi.PANEL_LINE) : MapUi.BUTTON_LINE);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), width - 6);
        g.drawCenteredString(font, label, getX() + width / 2, getY() + (height - 8) / 2,
                active ? MapUi.TITLE : MapUi.DIM);
    }
}
