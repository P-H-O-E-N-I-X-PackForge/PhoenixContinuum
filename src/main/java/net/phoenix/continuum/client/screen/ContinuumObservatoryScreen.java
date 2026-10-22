package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.DiscoveryStage;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The observatory: a live 3D look at any body or star the team has found, plus what only the instruments here can tell you
 * (readings the map does not show): surface gravity, an estimate of the temperature, the kind of air, the materials a
 * spectrograph picks out, a forecast of the next eclipse its moons will cast, and a live top-down view of the system's
 * orbits. Pick a body from the list; drag the picture to turn it and scroll over it to zoom.
 */
public class ContinuumObservatoryScreen extends Screen {

    private static final int ROW = 12;

    private final ScenePreview preview = new ScenePreview();
    private final List<Entry> entries = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private int selected;
    private int scroll;

    // worked out once when a body is picked, not every frame
    private int forecastFor = -2;
    private ObservatoryReadings.Eclipse eclipse;

    /** A row in the list: a system heading (body == null) or a body. */
    private record Entry(ContinuumSystem system, ContinuumBody body) {}

    public ContinuumObservatoryScreen() {
        super(Component.literal("Observatory"));
    }

    @Override
    protected void init() {
        entries.clear();
        List<ContinuumSystem> systems = new ArrayList<>(ContinuumData.systems());
        systems.sort(Comparator.comparing(ContinuumSystem::name));
        for (ContinuumSystem system : systems) {
            if (ContinuumClientState.stage(system.id()) == DiscoveryStage.UNKNOWN) continue;
            List<ContinuumBody> found = new ArrayList<>();
            for (ContinuumBody body : ContinuumData.bodiesOf(system.id())) {
                if (ContinuumClientState.stage(body.id()) == DiscoveryStage.UNKNOWN) continue;
                if (body.transientInfo() == null || ContinuumClientState.transientWindow(body.id()) != null) found.add(body);
            }
            if (found.isEmpty()) continue;
            entries.add(new Entry(system, null));
            entries.addAll(found.stream().map(b -> new Entry(system, b)).toList());
        }
        selected = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).body() != null) {
                selected = i;
                break;
            }
        }
        forecastFor = -2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** The layout is drawn at a fixed size and scaled to fit the window, so a small window is smaller rather than squashed. */
    private static final int VW = 470;
    private static final int VH = 300;
    private float scale = 1.0f;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        g.fill(0, 0, width, height, 0xCC000000);

        scale = Math.max(0.4f, Math.min(1.0f, Math.min((width - 16.0f) / VW, (height - 16.0f) / VH)));
        int vw = Math.round(width / scale);
        int vh = Math.round(height / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        draw(g, Math.round(mouseX / scale), Math.round(mouseY / scale), (vw - VW) / 2, (vh - VH) / 2, VW, VH);
        g.pose().popPose();
    }

    private void draw(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, MapUi.PANEL_SOLID);
        g.renderOutline(x, y, w, h, MapUi.PANEL_LINE);
        g.drawString(font, "Observatory", x + 8, y + 7, MapUi.TITLE);
        rowRects.clear();

        if (selected < 0) {
            g.drawString(font, "Nothing observed yet. Detect a signal on the map first.", x + 8, y + 26, MapUi.DIM);
            return;
        }

        // ---- the list ----
        int listW = 132;
        int listTop = y + 22;
        int visible = (h - 30) / ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        // (the scissor box is in window coordinates and is not scaled by the pose, so scale it by hand)
        g.enableScissor(Math.round((x + 6) * scale), Math.round(listTop * scale), Math.round((x + 6 + listW) * scale),
                Math.round((y + h - 6) * scale));
        for (int i = scroll; i < Math.min(entries.size(), scroll + visible); i++) {
            Entry e = entries.get(i);
            int ry = listTop + (i - scroll) * ROW;
            if (e.body() == null) {
                g.drawString(font, font.plainSubstrByWidth(e.system().name(), listW - 4), x + 8, ry + 2, MapUi.TITLE);
                continue;
            }
            boolean sel = i == selected;
            boolean hover = MapUi.inside(mouseX, mouseY, x + 6, ry, listW, ROW);
            if (sel || hover) g.fill(x + 6, ry, x + 6 + listW, ry + ROW, sel ? MapUi.BUTTON_ON : MapUi.BUTTON_HOVER);
            DiscoveryStage stage = ContinuumClientState.stage(e.body().id());
            g.drawString(font, font.plainSubstrByWidth((e.body().isMoon() ? "  " : " ") + e.body().name(), listW - 4),
                    x + 8, ry + 2, stage == DiscoveryStage.SURVEYED ? MapUi.TEXT : MapUi.DIM);
            rowRects.add(new int[] { x + 6, ry, listW, ROW, i });
        }
        g.disableScissor();

        Entry current = entries.get(selected);
        ContinuumBody body = current.body();
        ContinuumSystem system = current.system();
        DiscoveryStage stage = ContinuumClientState.stage(body.id());
        boolean surveyed = stage == DiscoveryStage.SURVEYED;

        if (forecastFor != selected) {
            forecastFor = selected;
            var level = Minecraft.getInstance().level;
            double days = level == null ? 0.0 : level.getGameTime() / 24000.0;
            eclipse = body.isMoon() || body.isCentral() ? null : ObservatoryReadings.nextEclipse(body, days, 800.0);
        }

        // ---- the picture, and the system's orbits under it ----
        int px = x + listW + 16;
        int size = 132;
        if (body.isCentral()) {
            preview.drawSystem(g, system, px, y + 22, size);
        } else {
            var params = surveyed ? body.params() : body.params().ghost();
            preview.drawPlanet(g, params, px, y + 22, size);
        }
        int diagramTop = y + 30 + size;
        int diagram = Math.min(size, y + h - 8 - diagramTop);
        if (diagram >= 64) drawOrbits(g, system, body, px, diagramTop, diagram);

        // ---- what the instruments say ----
        int tx = px + size + 10;
        int tw = x + w - 8 - tx;
        int ty = y + 22;
        g.drawString(font, body.name(), tx, ty, MapUi.TITLE);
        ty += 11;
        g.drawString(font, body.type().label() + "  -  " + (surveyed ? "surveyed" : "detected"), tx, ty,
                surveyed ? MapUi.GOOD : MapUi.WARN);
        ty += 14;

        g.drawString(font, "Readings", tx, ty, MapUi.TITLE);
        ty += 11;
        if (!surveyed) {
            ty = wrapped(g, "Only a scan return so far. Survey it to resolve the instruments.", tx, ty, tw, MapUi.DIM);
        } else {
            ty = line(g, "Gravity", ObservatoryReadings.gravity(body), tx, ty, tw);
            String temperature = ObservatoryReadings.temperature(body, system);
            if (temperature != null) ty = line(g, "Temp", temperature, tx, ty, tw);
            int airY = ty;
            String air = ObservatoryReadings.atmosphere(body);
            ty = line(g, "Air", air, tx, ty, tw);
            if (body.params().atmoDensity() > 0.0f) {
                // the colour of the air, after the word
                int sx = tx + 56 + font.width(air) + 5;
                g.fill(sx, airY, sx + 9, airY + 8, 0xFF000000 | body.params().atmoColor());
                g.renderOutline(sx - 1, airY - 1, 11, 10, MapUi.PANEL_LINE);
            }
        }

        ty += 3;
        g.drawString(font, "Spectrograph", tx, ty, MapUi.TITLE);
        ty += 11;
        List<Item> lines = ObservatoryReadings.spectrum(body);
        if (lines.isEmpty()) {
            g.drawString(font, "no lines", tx, ty, MapUi.DIM);
            ty += 12;
        } else {
            int ix = tx;
            for (Item item : lines) {
                if (ix + 16 > tx + tw) break;
                g.renderItem(new ItemStack(item), ix, ty - 1);
                ix += 17;
            }
            ty += 19;
        }

        if (!body.isMoon() && !body.isCentral() && surveyed) {
            g.drawString(font, "Sky", tx, ty, MapUi.TITLE);
            ty += 11;
            if (ContinuumData.moonsOf(body.id()).isEmpty()) {
                g.drawString(font, "no moons, no eclipses", tx, ty, MapUi.DIM);
                ty += 11;
            } else if (eclipse == null) {
                ty = wrapped(g, "No eclipse in the next 800 days.", tx, ty, tw, MapUi.DIM);
            } else {
                String how = eclipse.total() ? "total" : "annular or partial";
                // a game day is 24000 ticks: 20 real minutes at normal speed
                ty = line(g, "Eclipse", String.format(Locale.ROOT, "%s, in %.1f days (about %s)", how, eclipse.inDays(),
                        MapUi.duration(Math.round(eclipse.inDays() * 1_200_000.0))), tx, ty, tw);
                ty = wrapped(g, "by " + eclipse.moon(), tx, ty, tw, MapUi.DIM);
            }
        }

        // notes: what the observers have written down
        String lore = body.lore(stage);
        if (!lore.isBlank()) {
            ty += 3;
            for (FormattedCharSequence l : font.split(FormattedText.of(lore), tw)) {
                if (ty > y + h - 12) break;
                g.drawString(font, l, tx, ty, MapUi.DIM);
                ty += 10;
            }
        }
    }

    /** A top-down view of the system's orbits with every found world where it is right now. */
    private void drawOrbits(GuiGraphics g, ContinuumSystem system, ContinuumBody selectedBody, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF05060F);
        g.renderOutline(x, y, size, size, MapUi.PANEL_LINE);
        int cx = x + size / 2;
        int cy = y + size / 2;

        List<ContinuumBody> worlds = new ArrayList<>();
        float maxAu = 0.5f;
        for (ContinuumBody b : ContinuumData.bodiesOf(system.id())) {
            if (b.isCentral() || b.isMoon() || b.transientInfo() != null) continue;
            if (ContinuumClientState.stage(b.id()) == DiscoveryStage.UNKNOWN) continue;
            worlds.add(b);
            maxAu = Math.max(maxAu, b.orbitAu());
        }
        double dayMillis = Math.max(0.5, ContinuumConfigs.INSTANCE.launchWindowDayMinutes) * 60_000.0;
        double days = ContinuumClientState.now() / dayMillis;
        int outer = size / 2 - 5;

        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000 | system.starColor());
        for (ContinuumBody b : worlds) {
            int r = 7 + (int) ((outer - 7) * Math.sqrt(b.orbitAu() / maxAu));
            // the orbit
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2.0 / 48.0;
                g.fill(cx + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r),
                        cx + (int) Math.round(Math.cos(a) * r) + 1, cy + (int) Math.round(Math.sin(a) * r) + 1, 0x40FFFFFF);
            }
            double angle = 2.0 * Math.PI * (b.phase() + days / Math.max(0.05, b.periodDays()));
            int bx = cx + (int) Math.round(Math.cos(angle) * r);
            int by = cy + (int) Math.round(Math.sin(angle) * r);
            boolean sel = b == selectedBody || (selectedBody.isMoon() && b.id().equals(selectedBody.parent()));
            int color = 0xFF000000 | b.params().mid();
            g.fill(bx - 1, by - 1, bx + 2, by + 2, color);
            if (sel) g.renderOutline(bx - 3, by - 3, 7, 7, MapUi.TITLE);
        }
    }

    private int line(GuiGraphics g, String label, String value, int x, int y, int width) {
        g.drawString(font, label, x, y, MapUi.DIM);
        int vx = x + 56;
        for (FormattedCharSequence l : font.split(FormattedText.of(value), Math.max(40, width - 56))) {
            g.drawString(font, l, vx, y, MapUi.TEXT);
            y += 10;
        }
        return y + 1;
    }

    private int wrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (FormattedCharSequence l : font.split(FormattedText.of(text), width)) {
            g.drawString(font, l, x, y, color);
            y += 10;
        }
        return y + 1;
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = screenX / scale;
        double mouseY = screenY / scale;
        if (button == 0) {
            for (int[] r : rowRects) {
                if (MapUi.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    selected = r[4];
                    return true;
                }
            }
            if (preview.mouseClicked(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (preview.mouseDragged(dragX / scale, dragY / scale)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (preview.mouseScrolled(mouseX / scale, mouseY / scale, scrollY)) return true;
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        preview.close();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        preview.close();
    }
}
