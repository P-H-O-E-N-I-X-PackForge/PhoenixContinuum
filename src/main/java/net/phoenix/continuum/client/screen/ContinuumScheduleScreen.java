package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.client.ExpeditionDraft;
import net.phoenix.continuum.common.ContinuumMissions;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.common.ScheduleService;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.network.C2SScheduleActionPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A pad's schedules: a new one for the mission the planner was set up with, and the ones the pad already has. A
 * schedule launches as the pad's owner, with no one there, from what the pad holds.
 */
public class ContinuumScheduleScreen extends Screen {

    private static final int W = 332;
    private static final int ROW = 30;
    private static final int[] DELAYS = { 0, 60, 300, 900, 3600, 14400 };
    private static final int[] INTERVALS = { 0, 60, 300, 900, 1800, 3600, 14400, 43200, 86400 };
    private static final int[] CAPS = { 1, 2, 3, 5, 10, 25, 100, 0 };

    private final Screen parent;
    private final ResourceLocation destination;
    private final BlockPos pad;
    private final Mission.Type type;
    private final int probes;
    private final int count;

    private int delayIndex = 1;
    private int intervalIndex;
    private int capIndex;
    private boolean autoCollect = true;
    private boolean requested;
    private List<ScheduleService.View> shown = List.of();

    public ContinuumScheduleScreen(Screen parent, ResourceLocation destination, BlockPos pad, Mission.Type type,
                                   int probes, int count) {
        super(Component.literal("Schedule"));
        this.parent = parent;
        this.destination = destination;
        this.pad = pad;
        this.type = type;
        this.probes = probes;
        this.count = count;
    }

    static String time(int seconds) {
        if (seconds <= 0) return "now";
        if (seconds < 3600) return seconds / 60 + " min";
        if (seconds % 3600 == 0) return seconds / 3600 + " h";
        return seconds / 3600 + " h " + seconds % 3600 / 60 + " min";
    }

    private static String capText(int cap) {
        return cap == 0 ? "no limit" : cap + (cap == 1 ? " run" : " runs");
    }

    private int panelX() {
        return (width - W) / 2;
    }

    private int panelH() {
        return 228 + Math.max(1, shown.size()) * ROW;
    }

    private int panelY() {
        return Math.max(8, (height - panelH()) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(C2SScheduleActionPacket.list(pad));
        }
        int x = panelX() + 10;
        int y = panelY() + 46;
        int w = W - 20;

        addRenderableWidget(MapButton.of(x, y, w, 16, "First run: " +
                (delayIndex == 0 ? "at once" : "in " + time(DELAYS[delayIndex])), b -> {
                    delayIndex = (delayIndex + 1) % DELAYS.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(MapButton.of(x, y + 20, w, 16, "Repeat: " + (INTERVALS[intervalIndex] == 0 ?
                "never (one run)" : "every " + time(INTERVALS[intervalIndex])), b -> {
                    intervalIndex = (intervalIndex + 1) % INTERVALS.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(MapButton.of(x, y + 40, w, 16, "Stop after: " + (INTERVALS[intervalIndex] == 0 ?
                "1 run" : capText(CAPS[capIndex])), b -> {
                    capIndex = (capIndex + 1) % CAPS.length;
                    rebuildWidgets();
                }).enabled(INTERVALS[intervalIndex] != 0));
        addRenderableWidget(MapButton.of(x, y + 60, w, 16, "Pad takes the cargo in on return: " +
                (autoCollect ? "yes" : "no"), b -> {
                    autoCollect = !autoCollect;
                    rebuildWidgets();
                }).selected(autoCollect));
        addRenderableWidget(MapButton.of(x, y + 80, w, 16, "Name and crew: " + ExpeditionDraft.summary(),
                b -> Minecraft.getInstance().setScreen(new ContinuumExpeditionScreen(this))));

        addRenderableWidget(MapButton.of(x, y + 106, (w - 6) / 2, 18, "Create schedule", b -> {
            int interval = INTERVALS[intervalIndex];
            PacketDistributor.sendToServer(C2SScheduleActionPacket.create(pad, destination, type, probes, count,
                    ExpeditionDraft.name, new ArrayList<>(ExpeditionDraft.crew), DELAYS[delayIndex], interval,
                    interval == 0 ? 1 : CAPS[capIndex], autoCollect));
        }));
        addRenderableWidget(MapButton.of(x + (w - 6) / 2 + 6, y + 106, (w - 6) / 2, 18, "Back", b -> onClose()));

        int ry = panelY() + 188;
        for (ScheduleService.View view : shown) {
            final ScheduleService.View row = view;
            addRenderableWidget(MapButton.of(panelX() + W - 10 - 100, ry + 2, 48, 14,
                    view.enabled() ? "Pause" : "Start",
                    b -> PacketDistributor.sendToServer(C2SScheduleActionPacket.toggle(pad, row.id()))));
            addRenderableWidget(MapButton.of(panelX() + W - 10 - 48, ry + 2, 48, 14, "Delete",
                    b -> PacketDistributor.sendToServer(C2SScheduleActionPacket.delete(pad, row.id()))));
            ry += ROW;
        }
    }

    @Override
    public void tick() {
        List<ScheduleService.View> now = ContinuumClientState.schedules(pad);
        if (!now.equals(shown)) {
            shown = now;
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, MapUi.SCREEN_BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g, mouseX, mouseY, partialTick);

        int x = panelX();
        int y = panelY();
        MapUi.panel(g, x, y, W, panelH());
        ContinuumBody body = ContinuumData.body(destination);
        g.drawString(font, "SCHEDULE A LAUNCH", x + 10, y + 9, MapUi.TITLE);
        g.drawString(font, type.label() + " to " + (body != null ? body.name() : destination.getPath()) +
                (count > 1 ? "  x" + count : "") + (probes > 0 ? "  (" + probes + " probes)" : ""), x + 10, y + 24,
                MapUi.DIM);
        g.drawString(font, "Each run spends a rocket, its cargo and the launch energy from this pad.", x + 10,
                y + 36, MapUi.DIM);

        g.drawString(font, "Waits and tells you once if it cannot launch.", x + 10, y + 176, MapUi.DIM);

        int ry = y + 188;
        g.drawString(font, "THIS PAD'S SCHEDULES", x + 10, ry - 12, MapUi.TITLE);
        if (shown.isEmpty()) {
            g.drawString(font, "None yet.", x + 10, ry + 4, MapUi.DIM);
        }
        for (ScheduleService.View view : shown) {
            g.fill(x + 6, ry, x + W - 6, ry + ROW - 4, MapUi.PANEL_SOLID);
            g.renderOutline(x + 6, ry, W - 12, ROW - 4, MapUi.BUTTON_LINE);
            ContinuumBody to = ContinuumData.body(view.destination());
            String what = view.type().label() + " to " + (to != null ? to.name() : view.destination().getPath());
            g.drawString(font, font.plainSubstrByWidth(what, W - 130), x + 10, ry + 3, MapUi.TEXT);
            String when = view.interval() <= 0 ? "single run" : "every " + time((int) (view.interval() / 1000L)) +
                    ", " + view.runs() + (view.cap() > 0 ? "/" + view.cap() : "") + " done";
            String state = !view.enabled() ? "paused" : !view.failure().isEmpty() ? "waiting: " + view.failure() :
                    String.format(Locale.ROOT, "next in %s", ContinuumMissions.durationText(
                            Math.max(0L, view.nextFire() - ContinuumClientState.now())));
            g.drawString(font, font.plainSubstrByWidth(when + "  -  " + state, W - 124), x + 10, ry + 14,
                    view.failure().isEmpty() ? MapUi.DIM : MapUi.WARN);
            ry += ROW;
        }

        for (Renderable renderable : this.renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
