package net.phoenix.continuum.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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

@OnlyIn(Dist.CLIENT)
public final class ContinuumBodyPickerScreen extends Screen {

    private static final int ROW = 13;
    private static final int SYSTEM_W = 190;
    private static final int LINE = 10;
    private static final String FOOTER = "click a body to tune it   scroll: more   Esc: back";

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
        search = new EditBox(font, 16, 22, Math.min(width - 32, 360), 14, Component.literal("Search"));
        search.setMaxLength(48);
        search.setHint(Component.literal("Search every system and body..."));
        search.setResponder(text -> bodyScroll = 0);
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private int top() {
        
        return 56;
    }

    private List<FormattedCharSequence> footerLines() {
        return font.split(Component.literal(FOOTER), Math.max(60, width - 32));
    }

    private int rows() {
        int footer = footerLines().size() * LINE + 8;
        return Math.max(3, (height - top() - footer) / ROW);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private List<Object[]> bodyRows() {
        List<Object[]> out = new ArrayList<>();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            if (selectedSystem == null) return out;
            out.add(new Object[] { selectedSystem, 0 });
            List<ContinuumBody> list = bySystem.get(selectedSystem);
            
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

        int rows = rows();
        int top = top();
        int bodiesX = 16 + SYSTEM_W + 10;

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
            g.drawString(font, fit(label, SYSTEM_W - 6), 19, y + 3,
                    selected ? 0xFFFFFFFF : 0xFFAEBBCF, false);
            hits.add(new Object[] { "system", id, 16, y, SYSTEM_W, ROW });
        }

        List<Object[]> bodyRows = bodyRows();
        boolean searching = search != null && !search.getValue().trim().isEmpty();
        String heading = searching ? "Matches (" + bodyRows.size() + ")" :
                selectedSystem == null ? "" : systemName(selectedSystem);
        g.drawString(font, fit(heading, width - bodiesX - 16), bodiesX, top - 11, 0xFFFFD080, false);
        bodyScroll = Math.max(0, Math.min(bodyScroll, Math.max(0, bodyRows.size() - rows)));
        int bodiesW = width - bodiesX - 16;
        for (int i = 0; i < rows && i + bodyScroll < bodyRows.size(); i++) {
            Object[] row = bodyRows.get(i + bodyScroll);
            int y = top + i * ROW;
            if (row[0] instanceof ResourceLocation systemId) {
                
                boolean hover = mx >= bodiesX && mx < bodiesX + bodiesW && my >= y && my < y + ROW;
                if (hover) g.fill(bodiesX, y, bodiesX + bodiesW, y + ROW, 0x20FFFFFF);
                ContinuumSystem system = ContinuumData.system(systemId);
                int starColor = system == null ? 0xFFD9A0 : system.starColor();
                g.fill(bodiesX + 3, y + 3, bodiesX + 9, y + 9, 0xFF000000 | starColor);
                g.drawString(font, fit(systemName(systemId) + " (the star)", Math.max(40, bodiesW - 150)),
                        bodiesX + 14, y + 3, 0xFFFFE8B0, false);
                g.drawString(font, "System", bodiesX + bodiesW - 140, y + 3, 0xFF7F8A99, false);
                hits.add(new Object[] { "star", systemId, bodiesX, y, bodiesW, ROW });
                continue;
            }
            ContinuumBody body = (ContinuumBody) row[0];
            int indent = (int) row[1] * 14;
            boolean isCurrent = body.id().equals(current);
            boolean hover = mx >= bodiesX && mx < bodiesX + bodiesW && my >= y && my < y + ROW;
            if (isCurrent) g.fill(bodiesX, y, bodiesX + bodiesW, y + ROW, 0x40406080);
            else if (hover) g.fill(bodiesX, y, bodiesX + bodiesW, y + ROW, 0x20FFFFFF);

            int swatch = 0xFF000000 | body.params().mid();
            g.fill(bodiesX + 3 + indent, y + 3, bodiesX + 9 + indent, y + 9, swatch);
            String name = body.name() + (ContinuumTuner.isTuned(body.id()) ? "  *" : "");
            g.drawString(font, fit(name, Math.max(40, bodiesW - 150 - indent)),
                    bodiesX + 14 + indent, y + 3, isCurrent ? 0xFFFFFFFF : 0xFFC8D4E8, false);
            String detail = (searching ? systemName(body.system()) + "  " : "") + body.type().label();
            g.drawString(font, fit(detail, 136), bodiesX + bodiesW - 140, y + 3, 0xFF7F8A99, false);
            hits.add(new Object[] { "body", body.id(), bodiesX, y, bodiesW, ROW });
        }

        List<FormattedCharSequence> footer = footerLines();
        int footerY = height - 4 - footer.size() * LINE;
        for (int i = 0; i < footer.size(); i++) {
            g.drawString(font, footer.get(i), 16, footerY + i * LINE, 0xFF7F8A99, false);
        }
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = -(int) Math.signum(delta) * 3;
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