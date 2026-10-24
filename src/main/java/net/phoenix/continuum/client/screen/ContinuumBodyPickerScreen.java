package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.ContinuumTuner;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pick a body to tune: systems down the left, the chosen system's worlds (moons under their planet) on the right, and
 * a search box that looks across every system at once. Long names wrap onto extra lines and the row grows to fit.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumBodyPickerScreen extends Screen {

    /** Height of a one-line row; wrapped rows add LINE for every extra line. */
    private static final int ROW = 13;
    private static final int LINE = 10;
    private static final int SYSTEM_W = 190;
    private static final int INDENT = 14;

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    private final Consumer<ResourceLocation> onPickSystem;
    private final Map<ResourceLocation, List<ContinuumBody>> bySystem = new LinkedHashMap<>();
    private final List<ResourceLocation> systemOrder = new ArrayList<>();
    private ResourceLocation selectedSystem;
    private final ResourceLocation current;
    private int systemScroll;
    private int bodyScroll;
    private EditBox search;
    private final List<Object[]> hits = new ArrayList<>();

    public ContinuumBodyPickerScreen(Screen parent, List<ContinuumBody> candidates, ResourceLocation current,
                                     Consumer<ResourceLocation> onPick, Consumer<ResourceLocation> onPickSystem) {
        super(Component.literal("Pick a body"));
        this.parent = parent;
        this.onPick = onPick;
        this.onPickSystem = onPickSystem;
        this.current = current;

        for (ContinuumBody body : candidates) {
            bySystem.computeIfAbsent(body.system(), k -> new ArrayList<>()).add(body);
        }
        systemOrder.addAll(bySystem.keySet());
        systemOrder.sort(Comparator.comparing((ResourceLocation id) -> systemName(id).toLowerCase(Locale.ROOT))
                .thenComparing(ResourceLocation::toString));
        for (List<ContinuumBody> list : bySystem.values()) {
            // central body first, then each planet followed by its moons
            list.sort(Comparator.comparing((ContinuumBody b) -> !b.isCentral()).thenComparing(ContinuumBody::orbitAu));
        }
        ContinuumBody currentBody = current == null ? null : ContinuumData.body(current);
        selectedSystem = currentBody != null && bySystem.containsKey(currentBody.system()) ? currentBody.system() :
                current != null && bySystem.containsKey(current) ? current :
                systemOrder.isEmpty() ? null : systemOrder.get(0);
    }

    private static String systemName(ResourceLocation id) {
        ContinuumSystem system = ContinuumData.system(id);
        return system == null ? id.toString() : system.name();
    }

    @Override
    protected void init() {
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, 16, 22, Math.min(width - 32, 360), 14, Component.literal("Search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Search every system and body..."));
        search.setResponder(text -> bodyScroll = 0);
        search.setValue(previous);
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    private int top() {
        // the search box ends at 36, and each column has a heading line above its rows
        return 56;
    }

    // ------------------------------------------------------------------ wrapping

    /** Splits text into lines that fit the width (breaking inside a long unbroken word if it has to). */
    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(text, Math.max(8, width), Style.EMPTY)) {
            out.add(line.getString());
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static int heightOf(int lines) {
        return Math.max(ROW, lines * LINE + 3);
    }

    /** The right-hand column that holds the body type (and the system's name while searching). */
    private static int detailWidth(int bodiesW) {
        return Math.min(140, Math.max(40, bodiesW / 3));
    }

    private static int nameWidth(int bodiesW, int indent) {
        // 14 for the swatch, 6 of air before the detail column
        return Math.max(40, bodiesW - detailWidth(bodiesW) - INDENT - 6 - indent);
    }

    private static String systemLabel(ResourceLocation id, int count) {
        return systemName(id) + "  (" + count + ")";
    }

    private static String bodyLabel(ContinuumBody body) {
        return body.name() + (ContinuumTuner.isTuned(body.id()) ? "  *" : "");
    }

    private static String detailLabel(ContinuumBody body, boolean searching) {
        return (searching ? systemName(body.system()) + "  " : "") + body.type().label();
    }

    private int rowHeight(Object[] row, int bodiesW, boolean searching) {
        if (row[0] instanceof ResourceLocation systemId) {
            return heightOf(wrap(systemName(systemId) + " (the star)", nameWidth(bodiesW, 0)).size());
        }
        ContinuumBody body = (ContinuumBody) row[0];
        int indent = (int) row[1] * INDENT;
        int nameLines = wrap(bodyLabel(body), nameWidth(bodiesW, indent)).size();
        int detailLines = wrap(detailLabel(body, searching), detailWidth(bodiesW)).size();
        return heightOf(Math.max(nameLines, detailLines));
    }

    /** The first row to show at the top so that the end of the list still fills the space. */
    private static int maxScroll(int[] heights, int avail) {
        int used = 0;
        int first = heights.length;
        while (first > 0) {
            if (used + heights[first - 1] > avail) break;
            used += heights[first - 1];
            first--;
        }
        return first;
    }

    private void drawLines(GuiGraphics g, List<String> lines, int x, int y, int color) {
        for (int k = 0; k < lines.size(); k++) {
            g.drawString(font, lines.get(k), x, y + k * LINE, color, false);
        }
    }

    // ------------------------------------------------------------------ rows

    /** The bodies shown on the right: the chosen system's, or every match of the search across all systems. */
    private List<Object[]> bodyRows() {
        List<Object[]> out = new ArrayList<>();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            if (selectedSystem == null) return out;
            out.add(new Object[] { selectedSystem, 0 });
            List<ContinuumBody> list = bySystem.get(selectedSystem);
            // a moon goes under its planet
            List<ContinuumBody> planets = new ArrayList<>();
            for (ContinuumBody b : list) if (!b.isMoon()) planets.add(b);
            for (ContinuumBody planet : planets) {
                out.add(new Object[] { planet, 0 });
                for (ContinuumBody moon : ContinuumData.moonsOf(planet.id())) {
                    if (list.contains(moon)) out.add(new Object[] { moon, 1 });
                }
            }
            for (ContinuumBody b : list) {
                if (b.isMoon() && (b.parent() == null || !bySystem.get(selectedSystem).stream()
                        .anyMatch(p -> p.id().equals(b.parent())))) {
                    out.add(new Object[] { b, 1 });
                }
            }
            return out;
        }
        for (ResourceLocation system : systemOrder) {
            if ((systemName(system) + " " + system + " star").toLowerCase(Locale.ROOT).contains(query)) {
                out.add(new Object[] { system, 0 });
            }
            for (ContinuumBody b : bySystem.get(system)) {
                String hay = (b.name() + " " + b.id() + " " + systemName(system)).toLowerCase(Locale.ROOT);
                if (hay.contains(query)) out.add(new Object[] { b, 0 });
            }
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        hits.clear();
        g.drawString(font, "Pick a body to tune", 16, 10, 0xFFFFFFFF, false);

        int top = top();
        int bodiesX = 16 + SYSTEM_W + 10;

        // the hint at the bottom wraps too, and the lists stop above it
        List<String> hint = wrap("click a body to tune it   scroll: more   Esc: back", Math.max(80, width - 32));
        int hintY = height - 6 - hint.size() * LINE;
        int bottom = hintY - 6;
        int avail = bottom - top;

        // systems
        g.drawString(font, "Systems (" + systemOrder.size() + ")", 16, top - 11, 0xFFFFD080, false);
        int[] systemHeights = new int[systemOrder.size()];
        List<List<String>> systemLines = new ArrayList<>();
        for (int i = 0; i < systemOrder.size(); i++) {
            ResourceLocation id = systemOrder.get(i);
            List<String> lines = wrap(systemLabel(id, bySystem.get(id).size()), SYSTEM_W - 6);
            systemLines.add(lines);
            systemHeights[i] = heightOf(lines.size());
        }
        systemScroll = Math.max(0, Math.min(systemScroll, maxScroll(systemHeights, avail)));
        int cursor = top;
        for (int i = systemScroll; i < systemOrder.size(); i++) {
            int h = systemHeights[i];
            if (i > systemScroll && cursor + h > bottom) break;
            ResourceLocation id = systemOrder.get(i);
            int y = cursor;
            cursor += h;
            boolean selected = id.equals(selectedSystem);
            boolean hover = mx >= 16 && mx < 16 + SYSTEM_W && my >= y && my < y + h;
            if (selected) g.fill(16, y, 16 + SYSTEM_W, y + h, 0x40406080);
            else if (hover) g.fill(16, y, 16 + SYSTEM_W, y + h, 0x20FFFFFF);
            drawLines(g, systemLines.get(i), 19, y + 3, selected ? 0xFFFFFFFF : 0xFFAEBBCF);
            hits.add(new Object[] { "system", id, 16, y, SYSTEM_W, h });
        }

        // bodies
        List<Object[]> bodyRows = bodyRows();
        boolean searching = search != null && !search.getValue().trim().isEmpty();
        int bodiesW = width - bodiesX - 16;
        String heading = searching ? "Matches (" + bodyRows.size() + ")" :
                selectedSystem == null ? "" : systemName(selectedSystem);
        g.drawString(font, font.plainSubstrByWidth(heading, Math.max(40, bodiesW)), bodiesX, top - 11, 0xFFFFD080,
                false);
        int[] bodyHeights = new int[bodyRows.size()];
        for (int i = 0; i < bodyRows.size(); i++) {
            bodyHeights[i] = rowHeight(bodyRows.get(i), bodiesW, searching);
        }
        bodyScroll = Math.max(0, Math.min(bodyScroll, maxScroll(bodyHeights, avail)));
        int detailW = detailWidth(bodiesW);
        cursor = top;
        for (int i = bodyScroll; i < bodyRows.size(); i++) {
            int h = bodyHeights[i];
            if (i > bodyScroll && cursor + h > bottom) break;
            Object[] row = bodyRows.get(i);
            int y = cursor;
            cursor += h;
            boolean hover = mx >= bodiesX && mx < bodiesX + bodiesW && my >= y && my < y + h;
            if (row[0] instanceof ResourceLocation systemId) {
                // the system's own star (or black hole): tuned on the system screen
                if (hover) g.fill(bodiesX, y, bodiesX + bodiesW, y + h, 0x20FFFFFF);
                ContinuumSystem system = ContinuumData.system(systemId);
                int starColor = system == null ? 0xFFD9A0 : system.starColor();
                g.fill(bodiesX + 3, y + 3, bodiesX + 9, y + 9, 0xFF000000 | starColor);
                drawLines(g, wrap(systemName(systemId) + " (the star)", nameWidth(bodiesW, 0)), bodiesX + INDENT,
                        y + 3, 0xFFFFE8B0);
                g.drawString(font, "System", bodiesX + bodiesW - detailW, y + 3, 0xFF7F8A99, false);
                hits.add(new Object[] { "star", systemId, bodiesX, y, bodiesW, h });
                continue;
            }
            ContinuumBody body = (ContinuumBody) row[0];
            int indent = (int) row[1] * INDENT;
            boolean isCurrent = body.id().equals(current);
            if (isCurrent) g.fill(bodiesX, y, bodiesX + bodiesW, y + h, 0x40406080);
            else if (hover) g.fill(bodiesX, y, bodiesX + bodiesW, y + h, 0x20FFFFFF);

            int swatch = 0xFF000000 | body.params().mid();
            g.fill(bodiesX + 3 + indent, y + 3, bodiesX + 9 + indent, y + 9, swatch);
            drawLines(g, wrap(bodyLabel(body), nameWidth(bodiesW, indent)), bodiesX + INDENT + indent, y + 3,
                    isCurrent ? 0xFFFFFFFF : 0xFFC8D4E8);
            drawLines(g, wrap(detailLabel(body, searching), detailW), bodiesX + bodiesW - detailW, y + 3,
                    0xFF7F8A99);
            hits.add(new Object[] { "body", body.id(), bodiesX, y, bodiesW, h });
        }

        drawLines(g, hint, 16, hintY, 0xFF7F8A99);
        super.render(g, mx, my, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            int x = (int) h[2];
            int y = (int) h[3];
            if (mx < x || mx >= x + (int) h[4] || my < y || my >= y + (int) h[5]) continue;
            if (h[0].equals("system")) {
                selectedSystem = (ResourceLocation) h[1];
                bodyScroll = 0;
                search.setValue("");
            } else if (h[0].equals("star")) {
                // back first, so whatever the pick opens is what the player is left looking at
                minecraft.setScreen(parent);
                onPickSystem.accept((ResourceLocation) h[1]);
            } else {
                minecraft.setScreen(parent);
                onPick.accept((ResourceLocation) h[1]);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        int step = -(int) Math.signum(scrollY) * 3;
        if (mx < 16 + SYSTEM_W + 5) systemScroll += step;
        else bodyScroll += step;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
