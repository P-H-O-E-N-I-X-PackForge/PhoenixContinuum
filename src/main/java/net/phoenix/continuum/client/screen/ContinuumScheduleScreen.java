package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

/**
 * A pad's schedules: the ones it has, and a new one for the mission the planner was set up with. A schedule
 * launches as the pad's owner, with no one there, from what the pad holds.
 */
public class ContinuumScheduleScreen extends Screen {

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

    private String capText(int cap) {
        return cap == 0 ? "no limit" : cap + (cap == 1 ? " run" : " runs");
    }

    @Override
    protected void init() {
        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(C2SScheduleActionPacket.list(pad));
        }
        int cx = width / 2;
        int top = Math.max(8, height / 2 - 120);

        addRenderableWidget(Button.builder(Component.literal("First run: " +
                (delayIndex == 0 ? "at once" : "in " + time(DELAYS[delayIndex]))), b -> {
                    delayIndex = (delayIndex + 1) % DELAYS.length;
                    rebuildWidgets();
                }).bounds(cx - 120, top + 40, 240, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Repeat: " + (INTERVALS[intervalIndex] == 0 ?
                "never (one run)" : "every " + time(INTERVALS[intervalIndex]))), b -> {
                    intervalIndex = (intervalIndex + 1) % INTERVALS.length;
                    rebuildWidgets();
                }).bounds(cx - 120, top + 60, 240, 18).build());
        Button cap = Button.builder(Component.literal("Stop after: " + (INTERVALS[intervalIndex] == 0 ? "1 run" :
                capText(CAPS[capIndex]))), b -> {
                    capIndex = (capIndex + 1) % CAPS.length;
                    rebuildWidgets();
                }).bounds(cx - 120, top + 80, 240, 18).build();
        cap.active = INTERVALS[intervalIndex] != 0;
        addRenderableWidget(cap);
        addRenderableWidget(Button.builder(Component.literal("Pad takes the rocket and cargo in on return: " +
                (autoCollect ? "yes" : "no")), b -> {
                    autoCollect = !autoCollect;
                    rebuildWidgets();
                }).bounds(cx - 120, top + 100, 240, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Name and crew: " + ExpeditionDraft.summary()),
                b -> Minecraft.getInstance().setScreen(new ContinuumExpeditionScreen(this)))
                .bounds(cx - 120, top + 120, 240, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Create schedule"), b -> {
            int interval = INTERVALS[intervalIndex];
            PacketDistributor.sendToServer(C2SScheduleActionPacket.create(pad, destination, type, probes, count,
                    ExpeditionDraft.name, new ArrayList<>(ExpeditionDraft.crew), DELAYS[delayIndex], interval,
                    interval == 0 ? 1 : CAPS[capIndex], autoCollect));
        }).bounds(cx - 120, top + 144, 118, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx + 2, top + 144, 118, 18).build());

        int y = top + 190;
        for (ScheduleService.View view : ContinuumClientState.schedules(pad)) {
            UUIDHolder holder = new UUIDHolder(view);
            addRenderableWidget(Button.builder(Component.literal(view.enabled() ? "Pause" : "Start"),
                    b -> PacketDistributor.sendToServer(C2SScheduleActionPacket.toggle(pad, holder.view.id())))
                    .bounds(cx + 56, y - 2, 40, 14).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"),
                    b -> PacketDistributor.sendToServer(C2SScheduleActionPacket.delete(pad, holder.view.id())))
                    .bounds(cx + 98, y - 2, 40, 14).build());
            y += 26;
        }
    }

    private record UUIDHolder(ScheduleService.View view) {}

    private List<ScheduleService.View> shown = List.of();

    @Override
    public void tick() {
        List<ScheduleService.View> now = ContinuumClientState.schedules(pad);
        if (!now.equals(shown)) {
            shown = now;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = Math.max(8, height / 2 - 120);
        ContinuumBody body = ContinuumData.body(destination);
        g.drawCenteredString(font, "Schedule a launch", cx, top + 6, 0xFFFFFFFF);
        g.drawCenteredString(font, type.label() + " to " + (body != null ? body.name() : destination.getPath()) +
                (count > 1 ? "  x" + count : "") + (probes > 0 ? "  (" + probes + " probes)" : ""), cx, top + 22,
                0xFFb0b8c8);
        g.drawCenteredString(font, "Each run spends a rocket (and its cargo) and the launch energy from this pad.",
                cx, top + 168, 0xFF8890a0);
        g.drawCenteredString(font, "If it cannot launch it waits, and tells you once.", cx, top + 178, 0xFF8890a0);

        int y = top + 190;
        if (shown.isEmpty()) {
            g.drawString(font, "No schedules on this pad yet.", cx - 120, y, 0xFF8890a0);
            return;
        }
        for (ScheduleService.View view : shown) {
            ContinuumBody to = ContinuumData.body(view.destination());
            String what = view.type().label() + " to " + (to != null ? to.name() : view.destination().getPath());
            g.drawString(font, font.plainSubstrByWidth(what, 170), cx - 120, y - 2, 0xFFFFFFFF);
            String when = view.interval() <= 0 ? "single run" : "every " + time((int) (view.interval() / 1000L)) +
                    ", " + view.runs() + (view.cap() > 0 ? "/" + view.cap() : "") + " done";
            String state = !view.enabled() ? "paused" : !view.failure().isEmpty() ? "waiting: " + view.failure() :
                    "next in " + ContinuumMissions.durationText(Math.max(0L, view.nextFire() -
                            ContinuumClientState.now()));
            g.drawString(font, font.plainSubstrByWidth(when + "  -  " + state, 176), cx - 120, y + 8,
                    view.failure().isEmpty() ? 0xFF8890a0 : 0xFFe0b050);
            y += 26;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
