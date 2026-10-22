package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.common.LaunchWindows;
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
 * The observatory: a live 3D look at any body or star the team has found, with what is known about it. Pick one from the
 * list; drag the picture to turn it and scroll over it to zoom. A body that is only detected shows as the faint scan return
 * it is; a surveyed one in full.
 */
public class ContinuumObservatoryScreen extends Screen {

    private static final int ROW = 12;

    private final ScenePreview preview = new ScenePreview();
    private final List<Entry> entries = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private int selected;
    private int scroll;

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
                if (ContinuumClientState.stage(body.id()) != DiscoveryStage.UNKNOWN && body.transientInfo() == null) {
                    found.add(body);
                } else if (body.transientInfo() != null && ContinuumClientState.transientWindow(body.id()) != null &&
                        ContinuumClientState.stage(body.id()) != DiscoveryStage.UNKNOWN) {
                    found.add(body);
                }
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        g.fill(0, 0, width, height, 0xCC000000);

        int w = Math.min(width - 16, 440);
        int h = Math.min(height - 16, 270);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        g.fill(x, y, x + w, y + h, MapUi.PANEL_SOLID);
        g.renderOutline(x, y, w, h, MapUi.PANEL_LINE);
        g.drawString(font, "Observatory", x + 8, y + 7, MapUi.TITLE);
        rowRects.clear();

        if (selected < 0) {
            g.drawString(font, "Nothing observed yet. Detect a signal on the map first.", x + 8, y + 26, MapUi.DIM);
            return;
        }

        // ---- the list ----
        int listW = 124;
        int listTop = y + 22;
        int visible = (h - 30) / ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - visible)));
        g.enableScissor(x + 6, listTop, x + 6 + listW, y + h - 6);
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

        // ---- the body ----
        Entry current = entries.get(selected);
        ContinuumBody body = current.body();
        ContinuumSystem system = current.system();
        DiscoveryStage stage = ContinuumClientState.stage(body.id());
        int px = x + listW + 16;
        int size = Math.min(160, w - listW - 24 - 150);
        size = Math.max(96, Math.min(size, h - 40));
        if (body.isCentral()) {
            preview.drawSystem(g, system, px, y + 22, size);
        } else {
            var params = stage == DiscoveryStage.SURVEYED ? body.params() : body.params().ghost();
            preview.drawPlanet(g, params, px, y + 22, size);
        }
        g.drawString(font, "drag to turn, scroll to zoom", px, y + 26 + size, MapUi.DIM);

        int tx = px + size + 10;
        int tw = x + w - 8 - tx;
        int ty = y + 22;
        g.drawString(font, body.name(), tx, ty, MapUi.TITLE);
        ty += 11;
        g.drawString(font, body.type().label() + "  -  " + (stage == DiscoveryStage.SURVEYED ? "surveyed" : "detected"),
                tx, ty, stage == DiscoveryStage.SURVEYED ? MapUi.GOOD : MapUi.WARN);
        ty += 13;
        if (!body.isCentral()) {
            ty = line(g, "Orbit", String.format(Locale.ROOT, "%.2f AU, %.0f days", body.orbitAu(), body.periodDays()), tx, ty, tw);
        }
        if (stage == DiscoveryStage.SURVEYED && body.type() != ContinuumBody.Type.STAR) {
            ty = line(g, "Radius", String.format(Locale.ROOT, "%.2f (Earth = 1)", body.radius()), tx, ty, tw);
            ty = line(g, "Air", body.params().atmoDensity() > 0 ? "yes" : "none", tx, ty, tw);
        }
        var window = LaunchWindows.of(body, ContinuumClientState.now());
        if (window.applies()) {
            ty = line(g, "Window", window.label() + (window.changesInMillis() > 0 ?
                    (window.openNow() ? ", closes in " : ", opens in ") + MapUi.duration(window.changesInMillis()) : ""),
                    tx, ty, tw);
        }
        var passing = body.transientInfo() == null ? null : ContinuumClientState.transientWindow(body.id());
        if (passing != null) {
            ty = line(g, "Passing", "leaves in " + MapUi.duration(passing[1] - ContinuumClientState.now()), tx, ty, tw);
        }
        int found = 0;
        for (ContinuumBody.Anomaly a : body.anomalies()) {
            if (stage.atLeast(a.reveal())) found++;
        }
        if (found > 0) ty = line(g, "Anomalies", String.valueOf(found), tx, ty, tw);
        List<ContinuumBody> moons = ContinuumData.moonsOf(body.id());
        if (!moons.isEmpty()) ty = line(g, "Moons", String.valueOf(moons.size()), tx, ty, tw);

        ty += 4;
        String lore = body.lore(stage);
        if (lore.isBlank()) lore = body.description();
        for (FormattedCharSequence l : font.split(FormattedText.of(lore), tw)) {
            if (ty > y + h - 14) break;
            g.drawString(font, l, tx, ty, MapUi.DIM);
            ty += 10;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] r : rowRects) {
                if (MapUi.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    selected = r[4];
                    return true;
                }
            }
            if (preview.mouseClicked(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (preview.mouseDragged(dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (preview.mouseScrolled(mouseX, mouseY, scrollY)) return true;
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
