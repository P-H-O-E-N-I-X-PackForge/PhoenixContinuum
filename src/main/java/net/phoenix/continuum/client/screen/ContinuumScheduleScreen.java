package net.phoenix.continuum.client.screen;

import net.phoenix.continuum.client.ContinuumText;
import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.util.FormattedCharSequence;
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

public class ContinuumScheduleScreen extends ContinuumFitScreen {

    private static final String K = "phoenix_continuum.continuum_schedule.";

    private static final int W = 332;
    private static final int ROW = 30;
    private static final int LINE = 10;
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

    // the texts at the top and above the list wrap to the panel, so everything under them moves down with them
    private List<FormattedCharSequence> destLines = List.of();
    private List<FormattedCharSequence> descLines = List.of();
    private List<FormattedCharSequence> noteLines = List.of();

    public ContinuumScheduleScreen(Screen parent, ResourceLocation destination, BlockPos pad, Mission.Type type,
                                   int probes, int count) {
        super(ContinuumLang.tr("phoenix_continuum.continuum_schedule_screen.schedule"));
        this.parent = parent;
        this.destination = destination;
        this.pad = pad;
        this.type = type;
        this.probes = probes;
        this.count = count;
    }

    static String time(int seconds) {
        if (seconds <= 0) return ContinuumText.str(K + "time_now");
        if (seconds < 3600) return ContinuumText.str(K + "time_min", seconds / 60);
        if (seconds % 3600 == 0) return ContinuumText.str(K + "time_h", seconds / 3600);
        return ContinuumText.str(K + "time_h_min", seconds / 3600, seconds % 3600 / 60);
    }

    private static String capText(int cap) {
        if (cap == 0) return ContinuumText.str(K + "cap_none");
        return ContinuumText.str(cap == 1 ? K + "cap_one" : K + "cap_many", cap);
    }

    private String destinationText() {
        ContinuumBody body = ContinuumData.body(destination);
        String text = ContinuumText.str(K + "type_to", type.label(),
                body != null ? body.name() : destination.getPath());
        if (count > 1) text += ContinuumText.str(K + "times", count);
        if (probes > 0) text += ContinuumText.str(K + "probes", probes);
        return text;
    }

    private void wrapText() {
        int w = W - 20;
        destLines = font.split(FormattedText.of(destinationText()), w);
        descLines = font.split(FormattedText.of(ContinuumText.str(K + "each_run_spends_a_rocket")), w);
        noteLines = font.split(FormattedText.of(ContinuumText.str(K + "waits_and_tells_you_once")), w);
    }

    /** How far the controls sit lower than they would with one line of each text. */
    private int headExtra() {
        return (Math.max(1, destLines.size()) - 1) * LINE + (Math.max(1, descLines.size()) - 1) * LINE;
    }

    private int noteExtra() {
        return (Math.max(1, noteLines.size()) - 1) * LINE;
    }

    private int panelX() {
        return (width - W) / 2;
    }

    private int panelH() {
        return 246 + headExtra() + noteExtra() + Math.max(1, shown.size()) * ROW;
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
        super.init();
        wrapText();
        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(C2SScheduleActionPacket.list(pad));
        }
        int x = panelX() + 10;
        int y = panelY() + 46 + headExtra();
        int w = W - 20;

        addRenderableWidget(MapButton.of(x, y, w, 16, ContinuumText.str(K + "first_run",
                delayIndex == 0 ? ContinuumText.str(K + "at_once") :
                        ContinuumText.str(K + "in_time", time(DELAYS[delayIndex]))),
                b -> {
                    delayIndex = (delayIndex + 1) % DELAYS.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(MapButton.of(x, y + 20, w, 16, INTERVALS[intervalIndex] == 0 ?
                ContinuumText.str(K + "repeat_never") :
                ContinuumText.str(K + "repeat_every", time(INTERVALS[intervalIndex])), b -> {
                    intervalIndex = (intervalIndex + 1) % INTERVALS.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(MapButton.of(x, y + 40, w, 16, ContinuumText.str(K + "stop_after",
                INTERVALS[intervalIndex] == 0 ? capText(1) : capText(CAPS[capIndex])), b -> {
                    capIndex = (capIndex + 1) % CAPS.length;
                    rebuildWidgets();
                }).enabled(INTERVALS[intervalIndex] != 0));
        addRenderableWidget(MapButton.of(x, y + 60, w, 16, ContinuumText.str(K + "take_cargo",
                ContinuumText.str(autoCollect ? K + "yes" : K + "no")), b -> {
                    autoCollect = !autoCollect;
                    rebuildWidgets();
                }).selected(autoCollect));
        addRenderableWidget(MapButton.of(x, y + 80, w, 16,
                ContinuumText.str(K + "name_and_crew", ExpeditionDraft.summary()),
                b -> Minecraft.getInstance().setScreen(new ContinuumExpeditionScreen(this))));

        addRenderableWidget(MapButton.of(x, y + 106, (w - 6) / 2, 18, ContinuumText.str(K + "create"), b -> {
            int interval = INTERVALS[intervalIndex];
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(C2SScheduleActionPacket.create(pad,
                    destination, type, probes, count, ExpeditionDraft.name, new ArrayList<>(ExpeditionDraft.crew),
                    DELAYS[delayIndex], interval, interval == 0 ? 1 : CAPS[capIndex], autoCollect));
        }));
        addRenderableWidget(MapButton.of(x + (w - 6) / 2 + 6, y + 106, (w - 6) / 2, 18, ContinuumText.str(K + "back"),
                b -> onClose()));

        int ry = listY();
        for (ScheduleService.View view : shown) {
            final ScheduleService.View row = view;
            addRenderableWidget(MapButton.of(panelX() + W - 10 - 100, ry + 2, 48, 14,
                    ContinuumText.str(view.enabled() ? K + "pause" : K + "start"),
                    b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            C2SScheduleActionPacket.toggle(pad, row.id()))));
            addRenderableWidget(MapButton.of(panelX() + W - 10 - 48, ry + 2, 48, 14, ContinuumText.str(K + "delete"),
                    b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            C2SScheduleActionPacket.delete(pad, row.id()))));
            ry += ROW;
        }
    }

    private int listY() {
        return panelY() + 206 + headExtra() + noteExtra();
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

    private int drawLines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y, int color) {
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y, color, false);
            y += LINE;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g, mouseX, mouseY, partialTick);

        int x = panelX();
        int y = panelY();
        MapUi.panel(g, x, y, W, panelH());
        g.drawString(font, ContinuumText.str(K + "schedule_a_launch"), x + 10, y + 9, MapUi.TITLE);
        int ty = drawLines(g, destLines, x + 10, y + 24, MapUi.DIM);
        drawLines(g, descLines, x + 10, ty + 2, MapUi.DIM);

        int noteY = y + 176 + headExtra();
        drawLines(g, noteLines, x + 10, noteY, MapUi.DIM);

        int ry = listY();
        g.fill(x + 6, ry - 16, x + W - 6, ry - 15, MapUi.PANEL_LINE);
        g.drawString(font, ContinuumText.str(K + "this_pad_s_schedules"), x + 10, ry - 12, MapUi.TITLE);
        if (shown.isEmpty()) {
            g.drawString(font, ContinuumText.str(K + "none_yet"), x + 10, ry + 4, MapUi.DIM);
        }
        for (ScheduleService.View view : shown) {
            g.fill(x + 6, ry, x + W - 6, ry + ROW - 4, MapUi.PANEL_SOLID);
            g.renderOutline(x + 6, ry, W - 12, ROW - 4, MapUi.BUTTON_LINE);
            ContinuumBody to = ContinuumData.body(view.destination());
            String what = ContinuumText.str(K + "type_to", view.type().label(),
                    to != null ? to.name() : view.destination().getPath());
            g.drawString(font, font.plainSubstrByWidth(what, W - 130), x + 10, ry + 3, MapUi.TEXT);
            String when = view.interval() <= 0 ? ContinuumText.str(K + "single_run") :
                    ContinuumText.str(K + "every_done", time((int) (view.interval() / 1000L)),
                            view.runs() + (view.cap() > 0 ? "/" + view.cap() : ""));
            String state = !view.enabled() ? ContinuumText.str(K + "paused") :
                    !view.failure().isEmpty() ? ContinuumText.str(K + "waiting", net.phoenix.continuum.common.ContinuumLang.plain(view.failure())) :
                            ContinuumText.str(K + "next_in", ContinuumMissions.durationText(
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
