package net.phoenix.continuum.client.screen;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.phoenix.continuum.network.ContinuumNetwork;
import net.phoenix.continuum.common.SwarmReport;
import net.phoenix.continuum.common.Swarms;
import net.phoenix.continuum.network.C2SReceiverRefreshPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * A Swarm Receiver: what it holds, what your swarms and shells are making, and a live graph of both over the last
 * minute (it asks for a fresh reading every second).
 */
public class ContinuumPowerScreen extends Screen {

    private static final int W = 360;
    private static final int GRAPH_H = 70;
    private static final int SAMPLES = 60;

    private SwarmReport report;
    private final BlockPos pos;
    private final Deque<long[]> history = new ArrayDeque<>();
    private long lastAsk = Util.getMillis();
    private long lastSample;

    public ContinuumPowerScreen(SwarmReport report, BlockPos pos) {
        super(Component.literal(report.title()));
        this.report = report;
        this.pos = pos;
        sample();
    }

    public BlockPos at() {
        return pos;
    }

    public void update(SwarmReport next) {
        this.report = next;
        sample();
    }

    private void sample() {
        long now = Util.getMillis();
        if (now - lastSample < 700L) return;
        lastSample = now;
        history.addLast(new long[] { report.stored(), report.teamPerTick() });
        while (history.size() > SAMPLES) history.removeFirst();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (now - lastAsk > 1000L) {
            lastAsk = now;
            net.phoenix.continuum.network.ContinuumNetwork.sendToServer(new C2SReceiverRefreshPacket(pos));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB0000000);
    }

    private String per(long value) {
        return String.format(Locale.ROOT, "%,d %s/t", value, report.unit());
    }

    private int panelHeight() {
        int rows = Math.max(1, report.entries().size());
        return 142 + GRAPH_H + rows * 22 + (report.note().isEmpty() ? 0 : 24);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g);

        int h = panelHeight();
        int x = (width - W) / 2;
        int y = Math.max(8, (height - h) / 2);
        MapUi.panel(g, x, y, W, h);

        g.drawString(font, report.title().toUpperCase(Locale.ROOT), x + 10, y + 9, MapUi.TITLE);
        boolean live = report.teamPerTick() > 0;
        String status = live ? "RECEIVING" : "IDLE";
        g.drawString(font, status, x + W - 10 - font.width(status), y + 9, live ? MapUi.GOOD : MapUi.WARN);

        g.drawString(font, "Your swarms and shells make", x + 10, y + 26, MapUi.DIM);
        String total = per(report.teamPerTick());
        g.drawString(font, total, x + W - 10 - font.width(total), y + 26, live ? MapUi.GOOD : MapUi.WARN);

        int ty = y + 42;
        if (report.capacity() > 0) {
            g.drawString(font, "Stored here", x + 10, ty, MapUi.DIM);
            String stored = String.format(Locale.ROOT, "%,d / %,d %s", report.stored(), report.capacity(),
                    report.unit());
            g.drawString(font, font.plainSubstrByWidth(stored, W - 100), x + W - 10 -
                    Math.min(font.width(stored), W - 100), ty, MapUi.TEXT);
            MapUi.bar(g, x + 10, ty + 12, W - 20, 6,
                    (float) (report.stored() / Math.max(1.0, (double) report.capacity())), MapUi.GOOD);
            ty += 26;
        }
        if (report.maxOut() > 0) {
            g.drawString(font, "Passes on up to " + per(report.maxOut()), x + 10, ty, MapUi.DIM);
            ty += 12;
        }

        graph(g, x + 10, ty + 4, W - 20, GRAPH_H);
        ty += GRAPH_H + 18;

        g.drawString(font, "BUILT", x + 10, ty, MapUi.TITLE);
        ty += 13;
        if (report.entries().isEmpty()) {
            g.drawString(font, "Nothing yet. Build swarms and shells from a Swarm Control block.", x + 10, ty,
                    MapUi.DIM);
            ty += 13;
        }
        for (Swarms.Entry entry : report.entries()) {
            g.fill(x + 6, ty - 2, x + W - 6, ty + 18, MapUi.PANEL_SOLID);
            g.renderOutline(x + 6, ty - 2, W - 12, 20, MapUi.BUTTON_LINE);
            String what = entry.name() + "  -  " + (entry.shell() ? "shell" : "swarm") + " " + entry.level() + "/" +
                    entry.max();
            g.drawString(font, font.plainSubstrByWidth(what, W - 150), x + 10, ty, MapUi.TEXT);
            String output = per(entry.perTick());
            g.drawString(font, output, x + W - 10 - font.width(output), ty,
                    entry.perTick() > 0 ? MapUi.GOOD : MapUi.WARN);
            MapUi.bar(g, x + 10, ty + 10, W - 20, 3, entry.level() / (float) Math.max(1, entry.max()),
                    entry.shell() ? MapUi.WARN : MapUi.GOOD);
            ty += 22;
        }
        if (!report.note().isEmpty()) {
            ty += 4;
            for (var line : font.split(Component.literal(report.note()), W - 20)) {
                g.drawString(font, line, x + 10, ty, MapUi.DIM, false);
                ty += 10;
            }
        }

        for (Renderable renderable : this.renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }

    /** The team's output (bars) and what this receiver holds (line), over the last minute. */
    private void graph(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, MapUi.PANEL_SOLID);
        g.renderOutline(x, y, w, h, MapUi.BUTTON_LINE);
        for (int i = 1; i < 4; i++) g.fill(x + 1, y + h * i / 4, x + w - 1, y + h * i / 4 + 1, 0x22ffffff);
        if (history.size() < 2) {
            g.drawCenteredString(font, "Gathering readings...", x + w / 2, y + h / 2 - 4, MapUi.DIM);
            return;
        }
        long maxOut = 1L;
        long maxStored = Math.max(1L, report.capacity());
        for (long[] sample : history) maxOut = Math.max(maxOut, sample[1]);
        int n = history.size();
        float step = (w - 2) / (float) (SAMPLES - 1);
        int i = 0;
        int previousY = -1;
        for (long[] sample : history) {
            int px = x + 1 + Math.round((SAMPLES - n + i) * step);
            int barH = Math.round((h - 2) * sample[1] / (float) maxOut);
            if (barH > 0) {
                g.fill(px, y + h - 1 - barH, Math.min(x + w - 1, px + Math.max(1, Math.round(step))), y + h - 1,
                        0x886affb0 & 0x00ffffff | 0x66000000);
            }
            int lineY = y + h - 1 - Math.round((h - 2) * Math.min(1.0f, sample[0] / (float) maxStored));
            if (previousY >= 0) {
                g.fill(px - Math.max(1, Math.round(step)), Math.min(lineY, previousY), px,
                        Math.max(lineY, previousY) + 1, 0xFFffd27a);
            }
            previousY = lineY;
            i++;
        }
        g.drawString(font, "output", x + 4, y + 3, 0xFF6affb0);
        g.drawString(font, "stored", x + 4 + font.width("output") + 8, y + 3, 0xFFffd27a);
        String peak = per(maxOut);
        g.drawString(font, peak, x + w - 4 - font.width(peak), y + 3, MapUi.DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int h = panelHeight();
        int x = (width - W) / 2;
        int y = Math.max(8, (height - h) / 2);
        if (!MapUi.inside(mouseX, mouseY, x, y, W, h)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
