package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenix.continuum.common.SwarmReport;
import net.phoenix.continuum.common.Swarms;

import java.util.Locale;

public class ContinuumPowerScreen extends Screen {

    private static final int W = 320;

    private final SwarmReport report;

    public ContinuumPowerScreen(SwarmReport report) {
        super(Component.literal(report.title()));
        this.report = report;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xA0000000);
    }

    private String per(long value) {
        return String.format(Locale.ROOT, "%,d %s/t", value, report.unit());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int rows = Math.max(1, report.entries().size());
        int h = 118 + rows * 13 + (report.note().isEmpty() ? 0 : 22);
        int x = (width - W) / 2;
        int y = Math.max(8, (height - h) / 2);
        MapUi.panel(g, x, y, W, h);

        g.drawString(font, report.title(), x + 10, y + 9, MapUi.TITLE);
        g.drawString(font, "Your team's swarms and shells make", x + 10, y + 26, MapUi.DIM);
        String total = per(report.teamPerTick());
        g.drawString(font, total, x + W - 10 - font.width(total), y + 26,
                report.teamPerTick() > 0 ? MapUi.GOOD : MapUi.WARN);

        if (report.capacity() > 0) {
            g.drawString(font, "Stored here", x + 10, y + 42, MapUi.DIM);
            String stored = String.format(Locale.ROOT, "%,d / %,d %s", report.stored(), report.capacity(),
                    report.unit());
            g.drawString(font, font.plainSubstrByWidth(stored, W - 100), x + W - 10 -
                    Math.min(font.width(stored), W - 100), y + 42, MapUi.TEXT);
            MapUi.bar(g, x + 10, y + 54, W - 20, 6,
                    (float) (report.stored() / Math.max(1.0, (double) report.capacity())), MapUi.GOOD);
        }
        if (report.maxOut() > 0) {
            g.drawString(font, "Passes on up to " + per(report.maxOut()), x + 10, y + 66, MapUi.DIM);
        }

        int ty = y + 84;
        g.drawString(font, "Built", x + 10, ty, MapUi.TITLE);
        ty += 13;
        if (report.entries().isEmpty()) {
            g.drawString(font, "Nothing yet. Rockets carry Swarm Collector Pods or Shell Segments to a star or black hole.",
                    x + 10, ty, MapUi.DIM);
            ty += 13;
        }
        for (Swarms.Entry entry : report.entries()) {
            String what = entry.name() + "  -  " + (entry.shell() ? "shell" : "swarm") + " " + entry.level() + "/" +
                    entry.max();
            g.drawString(font, font.plainSubstrByWidth(what, W - 130), x + 10, ty, MapUi.TEXT);
            String output = per(entry.perTick());
            g.drawString(font, output, x + W - 10 - font.width(output), ty,
                    entry.perTick() > 0 ? MapUi.GOOD : MapUi.WARN);
            ty += 13;
        }
        if (!report.note().isEmpty()) {
            ty += 6;
            for (var line : font.split(Component.literal(report.note()), W - 20)) {
                g.drawString(font, line, x + 10, ty, MapUi.DIM, false);
                ty += 10;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rows = Math.max(1, report.entries().size());
        int h = 118 + rows * 13 + (report.note().isEmpty() ? 0 : 22);
        int x = (width - W) / 2;
        int y = Math.max(8, (height - h) / 2);
        if (!MapUi.inside(mouseX, mouseY, x, y, W, h)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
