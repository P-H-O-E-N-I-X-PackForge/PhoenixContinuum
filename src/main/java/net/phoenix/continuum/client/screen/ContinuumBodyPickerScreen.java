package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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
 * a search box that looks across every system at once.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumBodyPickerScreen extends Screen {

    private static final int ROW = 13;
    private static final int SYSTEM_W = 190;

    private final Screen parent;
    private final Consumer<ResourceLocation> onPick;
    private final Map<ResourceLocation, List<ContinuumBody>> bySystem = new LinkedHashMap<>();
    private final List<ResourceLocation> systemOrder = new ArrayList<>();
    private ResourceLocation selectedSystem;
    private final ResourceLocation current;
    private int systemScroll;
    private int bodyScroll;
    private EditBox search;
    private final List<Object[]> hits = new ArrayList<>();

    public ContinuumBodyPickerScreen(Screen parent, List<ContinuumBody> candidates, ResourceLocation current,
                                     Consumer<ResourceLocation> onPick) {
        super(Component.literal("Pick a body"));
        this.parent = parent;
        this.onPick = onPick;
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
        ContinuumBody currentBody = ContinuumData.body(current);
        selectedSystem = currentBody != null && bySystem.containsKey(currentBody.system()) ? currentBody.system() :
                systemOrder.isEmpty() ? null : systemOrder.get(0);
    }

    private static String systemName(ResourceLocation id) {
        ContinuumSystem system = ContinuumData.system(id);
        return system == null ? id.toString() : system.name();
    }

    @Override
    protected void init() {
        search = new EditBox(font, 16, 22, Math.min(width - 32, 360), 14, Component.literal("Search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Search every system and body..."));
        search.setResponder(text -> bodyScroll = 0);
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {}

    private int top() {
        // the search box ends at 36, and each column has a heading line above its rows
        return 56;
    }

    private int rows() {
        return Math.max(3, (height - top() - 24) / ROW);
    }

    /** The bodies shown on the right: the chosen system's, or every match of the search across all systems. */
    private List<Object[]> bodyRows() {
        List<Object[]> out = new ArrayList<>();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            if (selectedSystem == null) return out;
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

        int rows = rows();
        int top = top();
        int bodiesX = 16 + SYSTEM_W + 10;

        // systems
        g.drawString(font, "Systems (" + systemOrder.size() + ")", 16, top - 11, 0xFFFFD080, false);
        systemScroll = Math.max(0, Math.min(systemScroll, Math.max(0, systemOrder.size() - rows)));
        for (int i = 0; i < rows && i + systemScroll < systemOrder.size(); i++) {
            ResourceLocation id = systemOrder.get(i + systemScroll);
            int y = top + i * ROW;
            boolean selected = id.equals(selectedSystem);
            boolean hover = mx >= 16 && mx < 16 + SYSTEM_W && my >= y && my < y + ROW;
            if (selected) g.fill(16, y, 16 + SYSTEM_W, y + ROW, 0x40406080);
            else if (hover) g.fill(16, y, 16 + SYSTEM_W, y + ROW, 0x20FFFFFF);
            String label = systemName(id) + "  (" + bySystem.get(id).size() + ")";
            g.drawString(font, font.plainSubstrByWidth(label, SYSTEM_W - 6), 19, y + 3,
                    selected ? 0xFFFFFFFF : 0xFFAEBBCF, false);
            hits.add(new Object[] { "system", id, 16, y, SYSTEM_W, ROW });
        }

        // bodies
        List<Object[]> bodyRows = bodyRows();
        boolean searching = search != null && !search.getValue().trim().isEmpty();
        String heading = searching ? "Matches (" + bodyRows.size() + ")" :
                selectedSystem == null ? "" : systemName(selectedSystem);
        g.drawString(font, heading, bodiesX, top - 11, 0xFFFFD080, false);
        bodyScroll = Math.max(0, Math.min(bodyScroll, Math.max(0, bodyRows.size() - rows)));
        int bodiesW = width - bodiesX - 16;
        for (int i = 0; i < rows && i + bodyScroll < bodyRows.size(); i++) {
            Object[] row = bodyRows.get(i + bodyScroll);
            ContinuumBody body = (ContinuumBody) row[0];
            int indent = (int) row[1] * 14;
            int y = top + i * ROW;
            boolean isCurrent = body.id().equals(current);
            boolean hover = mx >= bodiesX && mx < bodiesX + bodiesW && my >= y && my < y + ROW;
            if (isCurrent) g.fill(bodiesX, y, bodiesX + bodiesW, y + ROW, 0x40406080);
            else if (hover) g.fill(bodiesX, y, bodiesX + bodiesW, y + ROW, 0x20FFFFFF);

            int swatch = 0xFF000000 | body.params().mid();
            g.fill(bodiesX + 3 + indent, y + 3, bodiesX + 9 + indent, y + 9, swatch);
            String name = body.name() + (ContinuumTuner.isTuned(body.id()) ? "  *" : "");
            g.drawString(font, font.plainSubstrByWidth(name, Math.max(40, bodiesW - 150 - indent)),
                    bodiesX + 14 + indent, y + 3, isCurrent ? 0xFFFFFFFF : 0xFFC8D4E8, false);
            String detail = (searching ? systemName(body.system()) + "  " : "") + body.type().label();
            g.drawString(font, font.plainSubstrByWidth(detail, 140), bodiesX + bodiesW - 140, y + 3, 0xFF7F8A99, false);
            hits.add(new Object[] { "body", body.id(), bodiesX, y, bodiesW, ROW });
        }

        g.drawString(font, "click a body to tune it   scroll: more   Esc: back", 16, height - 14, 0xFF7F8A99, false);
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
            } else {
                onPick.accept((ResourceLocation) h[1]);
                minecraft.setScreen(parent);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
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
