package net.phoenix.continuum.client.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenix.continuum.common.SwarmControlView;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.network.C2SSwarmControlPacket;

import java.util.Locale;

/**
 * Swarm Control: the target star or black hole, drawn with the swarm or shell round it as built, with laser
 * beams from its emitters for each part of the way there, and the controls to send more.
 */
public class ContinuumSwarmControlScreen extends Screen {

    private static final int PANEL_W = 196;
    private static final int NODES = 24;

    private SwarmControlView view;
    private final ScenePreview preview = new ScenePreview();
    private final long openedAt = Util.getMillis();
    private long lastRefresh = openedAt;
    private float shownProgress;
    private int renderX;
    private int renderY;
    private int renderSize;

    public ContinuumSwarmControlScreen(SwarmControlView view) {
        super(Component.literal("Swarm Control"));
        this.view = view;
    }

    public BlockPos at() {
        return view.control();
    }

    public void update(SwarmControlView next) {
        boolean retarget = !next.target().equals(view.target());
        this.view = next;
        if (retarget) shownProgress = 0.0f;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(byte action, ResourceLocation target) {
        PacketDistributor.sendToServer(new C2SSwarmControlPacket(action, view.control(), target));
    }

    private int panelX() {
        return width - PANEL_W - 12;
    }

    private boolean blackHoles;
    private boolean sectionChosen;
    private boolean framed;

    private java.util.List<SwarmControlView.Candidate> section() {
        java.util.List<SwarmControlView.Candidate> list = new java.util.ArrayList<>();
        for (var c : view.candidates()) {
            if (c.penrose() == blackHoles) list.add(c);
        }
        return list;
    }

    private void cycle(int by) {
        var list = section();
        if (list.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).body().equals(view.target())) index = i;
        }
        send(C2SSwarmControlPacket.SELECT, list.get(Math.floorMod(index + by, list.size())).body());
    }

    private void pickSection(boolean holes) {
        blackHoles = holes;
        sectionChosen = true;
        var list = section();
        var current = view.current();
        if (!list.isEmpty() && (current == null || current.penrose() != holes)) {
            send(C2SSwarmControlPacket.SELECT, list.get(0).body());
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = panelX();
        var current = view.current();
        if (!sectionChosen && current != null) blackHoles = current.penrose();
        else if (current != null && current.penrose() != blackHoles && section().stream()
                .noneMatch(c -> c.body().equals(view.target()))) {
            // the target moved to the other section (another screen changed it): follow it
            blackHoles = current.penrose();
        }
        int stars = (int) view.candidates().stream().filter(c -> !c.penrose()).count();
        int holes = view.candidates().size() - stars;
        int half = (PANEL_W - 22) / 2;
        addRenderableWidget(MapButton.of(x + 8, 32, half, 16, "Stars (" + stars + ")", b -> pickSection(false))
                .selected(!blackHoles).enabled(stars > 0));
        addRenderableWidget(MapButton.of(x + 14 + half, 32, half, 16, "Black holes (" + holes + ")",
                b -> pickSection(true)).selected(blackHoles).enabled(holes > 0));
        addRenderableWidget(MapButton.of(x + 8, 52, 18, 16, "<", b -> cycle(-1)));
        addRenderableWidget(MapButton.of(x + PANEL_W - 26, 52, 18, 16, ">", b -> cycle(1)));

        boolean ready = current != null && view.padFound() && view.online();
        addRenderableWidget(MapButton.of(x + 8, height - 88, 22, 16, "-", b -> send(C2SSwarmControlPacket.SMALLER,
                view.target())));
        addRenderableWidget(MapButton.of(x + PANEL_W - 30, height - 88, 22, 16, "+", b -> send(
                C2SSwarmControlPacket.BIGGER, view.target())));
        addRenderableWidget(MapButton.of(x + 8, height - 64, PANEL_W - 16, 18, "Send swarm pods",
                b -> send(C2SSwarmControlPacket.BUILD_SWARM, view.target()))
                .enabled(ready && current.swarm() < current.swarmMax() && current.shell() == 0));
        addRenderableWidget(MapButton.of(x + 8, height - 42, PANEL_W - 16, 18, "Send shell segments",
                b -> send(C2SSwarmControlPacket.BUILD_SHELL, view.target()))
                .enabled(ready && current.shell() < current.shellMax() && current.swarm() == 0));
        addRenderableWidget(MapButton.of(x + 8, height - 20, PANEL_W - 16, 14, "Close", b -> onClose()));
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (now - lastRefresh > 2000L) {
            lastRefresh = now;
            send(C2SSwarmControlPacket.REFRESH, view.target());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, MapUi.SCREEN_BG);
    }

    /** Completion of what is built, 0 to 1. */
    private float progress(SwarmControlView.Candidate c) {
        if (c == null) return 0.0f;
        if (c.shell() > 0) return Math.min(1.0f, c.shell() / (float) Math.max(1, c.shellMax()));
        return Math.min(1.0f, c.swarm() / (float) Math.max(1, c.swarmMax()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        renderBackground(g, mouseX, mouseY, partialTick);

        var current = view.current();
        ContinuumBody central = current == null ? null : ContinuumData.body(current.body());
        var system = central == null ? null : ContinuumData.system(central.system());

        renderSize = Math.max(64, Math.min(height - 24, panelX() - 24));
        renderX = 12;
        renderY = (height - renderSize) / 2;
        if (!framed && current != null) {
            framed = true;
            // far enough out to see the whole swarm or shell
            preview.frame(current.swarm() > 0 || current.shell() > 0 ? 9.0f : 4.6f);
        }
        preview.drawTarget(g, central, system, current == null ? 0 : current.swarm(),
                current == null ? 0 : current.shell(), renderX, renderY, renderSize);
        if (current != null) {
            shownProgress += (progress(current) - shownProgress) * Math.min(1.0f, 0.05f + partialTick * 0.1f);
            drawBeams(g, system == null ? 0xFFD9A0 : system.starColor());
        }

        int x = panelX();
        MapUi.panel(g, x, 8, PANEL_W, height - 16);
        g.drawString(font, "SWARM CONTROL", x + 8, 14, MapUi.TITLE);
        String status = view.online() ? "ONLINE" : "OFFLINE";
        g.drawString(font, status, x + PANEL_W - 8 - font.width(status), 14, view.online() ? MapUi.GOOD : MapUi.BAD);
        if (current == null) {
            g.drawString(font, "No target", x + 8, 76, MapUi.DIM);
            for (var line : font.split(Component.literal(view.note()), PANEL_W - 16)) {
                g.drawString(font, line, x + 8, 90, MapUi.DIM, false);
            }
            for (var renderable : this.renderables) renderable.render(g, mouseX, mouseY, partialTick);
            return;
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(current.name(), PANEL_W - 64), x + PANEL_W / 2, 58,
                MapUi.TITLE);
        g.drawCenteredString(font, current.penrose() ? "Black hole  -  Penrose swarm" : "Star  -  Dyson swarm",
                x + PANEL_W / 2, 72, MapUi.DIM);

        g.drawCenteredString(font, String.format(Locale.ROOT, "Hologram size %.2gx", view.scale()), x + PANEL_W / 2,
                height - 84, MapUi.TEXT);
        int y = 88;
        y = stage(g, x + 8, y, current);
        y = meter(g, x + 8, y, "Swarm", current.swarm(), current.swarmMax(), view.swarmInFlight(), MapUi.GOOD);
        y = meter(g, x + 8, y, current.penrose() ? "Penrose sphere" : "Shell", current.shell(), current.shellMax(),
                view.shellInFlight(), MapUi.WARN);

        g.drawString(font, "Output now", x + 8, y + 2, MapUi.DIM);
        String now = String.format(Locale.ROOT, "%,d %s/t", view.powerNow(), view.unit());
        g.drawString(font, now, x + PANEL_W - 8 - font.width(now), y + 2,
                view.powerNow() > 0 ? MapUi.GOOD : MapUi.WARN);
        y += 12;
        g.drawString(font, "Finished shell", x + 8, y + 2, MapUi.DIM);
        String done = String.format(Locale.ROOT, "%,d %s/t", view.powerDone(), view.unit());
        g.drawString(font, done, x + PANEL_W - 8 - font.width(done), y + 2, MapUi.TEXT);
        y += 18;

        g.drawString(font, "Launch pad", x + 8, y, MapUi.TITLE);
        y += 11;
        if (!view.padFound()) {
            for (var line : font.split(Component.literal(view.note()), PANEL_W - 16)) {
                g.drawString(font, line, x + 8, y, MapUi.WARN, false);
                y += 10;
            }
        } else {
            g.drawString(font, font.plainSubstrByWidth(view.padName(), PANEL_W - 16), x + 8, y, MapUi.TEXT);
            y += 11;
            boolean enough = view.energyStored() >= view.launchEnergy();
            g.drawString(font, String.format(Locale.ROOT, "Energy %,d / %,d %s", view.energyStored(),
                    view.launchEnergy(), view.unit()), x + 8, y, enough ? MapUi.GOOD : MapUi.WARN);
            y += 11;
            g.drawString(font, "Rockets " + view.rockets() + "   Pods " + view.collectors() + "   Segments " +
                    view.segments(), x + 8, y, MapUi.TEXT);
        }
        for (var renderable : this.renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }

    private int stage(GuiGraphics g, int x, int y, SwarmControlView.Candidate c) {
        String text;
        int color = MapUi.TEXT;
        if (c.shell() > 0) {
            boolean finished = c.shell() >= c.shellMax();
            text = finished ? "Stage: shell complete" : "Stage: building the shell";
            color = finished ? MapUi.GOOD : MapUi.WARN;
        } else if (c.swarm() > 0) {
            boolean finished = c.swarm() >= c.swarmMax();
            text = finished ? "Stage: swarm complete" : "Stage: building the swarm";
            color = finished ? MapUi.GOOD : MapUi.WARN;
        } else if (view.swarmInFlight() + view.shellInFlight() > 0) {
            text = "Stage: first parts on their way";
        } else {
            text = "Stage: nothing built yet";
        }
        g.drawString(font, text, x, y, color);
        String percent = Math.round(progress(c) * 100.0f) + "%";
        g.drawString(font, percent, x + PANEL_W - 16 - font.width(percent), y, MapUi.TITLE);
        return y + 14;
    }

    private int meter(GuiGraphics g, int x, int y, String label, int built, int max, int flying, int color) {
        g.drawString(font, label, x, y, MapUi.DIM);
        String count = built + " / " + max + (flying > 0 ? "  (+" + flying + " flying)" : "");
        g.drawString(font, count, x + PANEL_W - 16 - font.width(count), y, MapUi.TEXT);
        MapUi.bar(g, x, y + 11, PANEL_W - 16, 4, built / (float) Math.max(1, max), color);
        return y + 22;
    }

    /**
     * Emitter nodes round the picture; a node lights as its share of the build is done, and the next one flickers
     * while parts are in flight. Each lit node fires a beam into the star.
     */
    private void drawBeams(GuiGraphics g, int color) {
        int flying = view.swarmInFlight() + view.shellInFlight();
        int lit = Math.round(shownProgress * NODES);
        float time = (Util.getMillis() - openedAt) / 1000.0f;
        float cx = renderX + renderSize / 2.0f;
        float cy = renderY + renderSize / 2.0f;
        float radius = renderSize * 0.46f;
        int rgb = color & 0x00ffffff;
        for (int i = 0; i < NODES; i++) {
            boolean on = i < lit;
            boolean next = i == lit && flying > 0;
            double angle = Math.PI * 2.0 * i / NODES + time * 0.05;
            float nx = cx + (float) Math.cos(angle) * radius;
            float ny = cy + (float) Math.sin(angle) * radius;
            if (!on && !next) {
                g.fill((int) nx - 1, (int) ny - 1, (int) nx + 2, (int) ny + 2, 0x55808090);
                continue;
            }
            float pulse = next ? 0.35f + 0.35f * (float) Math.sin(time * 9.0 + i) :
                    0.65f + 0.2f * (float) Math.sin(time * 3.0 + i * 1.7);
            int alpha = Math.max(20, Math.min(255, (int) (pulse * 255.0f)));
            float length = (float) Math.hypot(cx - nx, cy - ny) * 0.82f;
            float theta = (float) Math.atan2(cy - ny, cx - nx);
            g.pose().pushPose();
            g.pose().translate(nx, ny, 0.0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(theta));
            g.fill(0, -2, (int) length, 2, ((alpha / 4) << 24) | rgb);
            g.fill(0, -1, (int) length, 1, (alpha << 24) | 0x00ffffff);
            g.pose().popPose();
            g.fill((int) nx - 2, (int) ny - 2, (int) nx + 3, (int) ny + 3, (alpha << 24) | 0x00ffffff);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (preview.mouseClicked(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (preview.mouseDragged(dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (preview.mouseScrolled(mx, my, scrollY)) return true;
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public void removed() {
        preview.close();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
