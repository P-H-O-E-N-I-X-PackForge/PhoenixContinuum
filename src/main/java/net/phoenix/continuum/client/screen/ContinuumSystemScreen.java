package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.TuningPatches;
import net.phoenix.continuum.network.C2SCreatePacket;
import net.phoenix.continuum.network.C2STunePacket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Systems, on a screen of their own: tune one system at a time with a live 3D view of its star, or start a new one. Edits
 * are made on a working copy; "Apply" sends the changes to the server (saved as a patch for everyone) and "Create" writes
 * a new system into the pack folder.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumSystemScreen extends Screen {

    private enum Kind {
        NUMBER,
        COLOR,
        TEXT,
        CHOICE,
        HEADER
    }

    private record Row(String path, String label, Kind kind, double min, double max, double step, Object fallback,
                       List<String> choices) {

        static Row number(String path, String label, double min, double max, double step, double fallback) {
            return new Row(path, label, Kind.NUMBER, min, max, step, fallback, List.of());
        }

        static Row color(String path, String label, String fallback) {
            return new Row(path, label, Kind.COLOR, 0, 0, 0, fallback, List.of());
        }

        static Row text(String path, String label, String fallback) {
            return new Row(path, label, Kind.TEXT, 0, 0, 0, fallback, List.of());
        }

        static Row choice(String path, String label, String fallback, String... options) {
            return new Row(path, label, Kind.CHOICE, 0, 0, 0, fallback, List.of(options));
        }

        static Row header(String label) {
            return new Row("", label, Kind.HEADER, 0, 0, 0, "", List.of());
        }
    }

    private static final String[] STAR_KINDS = { "star", "red_giant", "brown_dwarf", "rapid_rotator", "wolf_rayet",
            "pulsar", "protostar", "planetary_nebula", "black_hole", "quasar", "binary_black_hole" };
    private static final String[] STAGES = { "unknown", "detected", "surveyed" };

    private static final int ROW = 15;
    private static final int LIST_W = 340;

    private final Screen parent;
    private final List<ContinuumSystem> systems = new ArrayList<>();
    private int index;
    private boolean creating;
    private ResourceLocation draftId;
    private JsonObject draft = new JsonObject();
    private JsonObject original = new JsonObject();
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private EditBox editor;
    private Row editing;
    private String status = "";
    private final ScenePreview preview = new ScenePreview();
    private @Nullable ContinuumSystem previewSystem;
    private final List<Object[]> hits = new ArrayList<>();

    /** @param start the system to tune first, or null to start with a new one */
    public ContinuumSystemScreen(Screen parent, @Nullable ResourceLocation start) {
        super(Component.literal("Continuum systems"));
        this.parent = parent;
        systems.addAll(ContinuumData.systems());
        systems.sort(Comparator.comparing(ContinuumSystem::name));
        if (start == null || systems.isEmpty()) {
            beginCreate();
        } else {
            for (int i = 0; i < systems.size(); i++) {
                if (systems.get(i).id().equals(start)) index = i;
            }
            beginTune();
        }
    }

    // ------------------------------------------------------------------ the working copy

    private void beginTune() {
        creating = false;
        ContinuumSystem system = systems.get(Math.floorMod(index, systems.size()));
        JsonObject raw = ContinuumData.rawSystem(system.id());
        original = raw == null ? new JsonObject() : raw.deepCopy();
        draft = original.deepCopy();
        draftId = system.id();
        status = "";
        rebuild();
    }

    private void beginCreate() {
        creating = true;
        draft = new JsonObject();
        draft.addProperty("format", 1);
        draft.addProperty("name", "New system");
        draft.addProperty("description", "Created in game.");
        double[] at = ContinuumCreateScreen.freeSpot();
        JsonArray galaxy = new JsonArray();
        for (double v : at) galaxy.add(Math.round(v * 10.0) / 10.0);
        draft.add("galaxy", galaxy);
        draft.addProperty("star_color", "#ffe9b0");
        draft.addProperty("star_radius", 1.0);
        draft.addProperty("star_kind", "star");
        draft.addProperty("initial_stage", "detected");
        original = new JsonObject();
        draftId = freshId("New system");
        status = "";
        rebuild();
    }

    private ResourceLocation freshId(String display) {
        String slug = display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) slug = "new_system";
        String candidate = slug;
        int n = 2;
        while (ContinuumData.system(ResourceLocation.fromNamespaceAndPath("phoenix_continuum", candidate)) != null) {
            candidate = slug + "_" + n++;
        }
        return ResourceLocation.fromNamespaceAndPath("phoenix_continuum", candidate);
    }

    private void rebuild() {
        rows.clear();
        scroll = 0;
        editing = null;
        previewSystem = null;
        rows.add(Row.header("Star"));
        rows.add(Row.text("name", "Name", "System"));
        rows.add(Row.text("description", "Description", ""));
        rows.add(Row.choice("star_kind", "Kind", "star", STAR_KINDS));
        rows.add(Row.color("star_color", "Colour", "#ffd9a0"));
        rows.add(Row.number("star_radius", "Size", 0.3, 4.0, 0.05, 1.0));
        rows.add(Row.choice("initial_stage", "Starting stage", "unknown", STAGES));
        rows.add(Row.header("Position in the galaxy"));
        rows.add(Row.number("galaxy.0", "x", -80, 80, 0.5, 0));
        rows.add(Row.number("galaxy.1", "y (height)", -20, 20, 0.1, 0));
        rows.add(Row.number("galaxy.2", "z", -80, 80, 0.5, 0));
        String kind = kind();
        if (kind.equals("black_hole") || kind.equals("quasar") || kind.equals("binary_black_hole")) {
            rows.add(Row.header("Accretion disk"));
            rows.add(Row.number("hole.disk_size", "Disk size", 0.2, 4, 0.05, 1.0));
            rows.add(Row.number("hole.disk_intensity", "Disk brightness", 0, 4, 0.05, 1.0));
            rows.add(Row.number("hole.feeding", "Feeding", 0, 1, 0.02, 0.0));
            rows.add(Row.number("hole.tilt", "Tilt (map view)", 0, 1, 0.025, 0.075));
            rows.add(Row.color("hole.hot_color", "Hot colour", "#ffc060"));
            rows.add(Row.color("hole.cool_color", "Cool colour", "#c04020"));
            rows.add(Row.number("hole.sky_inclination", "Inclination (sky)", 0, 90, 1, 45));
            rows.add(Row.number("hole.sky_lean", "Lean (sky)", -90, 90, 1, 15));
            if (kind.equals("binary_black_hole")) {
                rows.add(Row.number("hole.separation", "Separation", 2.2, 8, 0.1, 3.2));
                rows.add(Row.number("hole.mass_ratio", "Mass ratio", 0.1, 1, 0.05, 0.6));
                rows.add(Row.number("hole.period_seconds", "Orbit period (s)", 2, 120, 1, 24));
            }
        }
    }

    private String kind() {
        JsonElement e = draft.get("star_kind");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "star";
    }

    // ------------------------------------------------------------------ values

    private @Nullable JsonElement read(Row row) {
        JsonElement cursor = draft;
        for (String part : row.path().split("[.]")) {
            if (cursor == null) return null;
            if (cursor.isJsonObject()) {
                cursor = cursor.getAsJsonObject().get(part);
            } else if (cursor.isJsonArray()) {
                try {
                    int i = Integer.parseInt(part);
                    cursor = i >= 0 && i < cursor.getAsJsonArray().size() ? cursor.getAsJsonArray().get(i) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cursor;
    }

    private String text(Row row) {
        JsonElement v = read(row);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : String.valueOf(row.fallback());
    }

    private double number(Row row) {
        JsonElement v = read(row);
        if (v != null && v.isJsonPrimitive()) {
            try {
                return v.getAsDouble();
            } catch (RuntimeException ignored) {}
        }
        return ((Number) row.fallback()).doubleValue();
    }

    private void set(Row row, JsonElement value) {
        String[] parts = row.path().split("[.]");
        JsonObject cursor = draft;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.equals("galaxy")) {
                JsonArray galaxy = cursor.has("galaxy") && cursor.get("galaxy").isJsonArray() ?
                        cursor.getAsJsonArray("galaxy") : new JsonArray();
                while (galaxy.size() < 3) galaxy.add(0);
                galaxy.set(Integer.parseInt(parts[i + 1]), value);
                cursor.add("galaxy", galaxy);
                afterEdit(row);
                return;
            }
            JsonElement next = cursor.get(part);
            if (next == null || !next.isJsonObject()) {
                next = new JsonObject();
                cursor.add(part, next);
            }
            cursor = next.getAsJsonObject();
        }
        cursor.add(parts[parts.length - 1], value);
        afterEdit(row);
    }

    private void afterEdit(Row row) {
        previewSystem = null;
        if (row.path().equals("star_kind")) {
            int keep = scroll;
            rebuild();
            scroll = keep;
        }
    }

    private void setNumber(Row row, double value) {
        double clamped = Math.max(row.min(), Math.min(row.max(), value));
        clamped = Math.round(clamped / row.step()) * row.step();
        clamped = Math.round(clamped * 10000.0) / 10000.0;
        set(row, new JsonPrimitive(clamped));
    }

    private @Nullable ContinuumSystem draftSystem() {
        if (previewSystem == null) {
            try {
                previewSystem = ContinuumData.parseSystem(draftId, draft);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return previewSystem;
    }

    // ------------------------------------------------------------------ actions

    private void apply() {
        if (creating) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new C2SCreatePacket(true, draftId, draft.toString()));
            status = "Asked the server to create " + draftId.getPath() + ".";
            return;
        }
        int sent = 0;
        for (String key : TuningPatches.EDITABLE_SYSTEM) {
            JsonElement now = draft.get(key);
            JsonElement before = original.get(key);
            if (now == null || now.equals(before)) continue;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(C2STunePacket.forSystem(draftId, key, now.toString()));
            sent++;
        }
        status = sent == 0 ? "Nothing changed." : "Sent " + sent + " change(s) to the server.";
        if (sent > 0) original = draft.deepCopy();
    }

    private void resetServer() {
        if (creating) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(C2STunePacket.resetSystem(draftId));
        status = "Asked the server to drop its edits of this system.";
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void init() {
        editor = new EditBox(font, 0, 0, 90, 13, Component.empty());
        editor.setVisible(false);
        editor.setMaxLength(12);
        addRenderableWidget(editor);
    }

    private int left() {
        return Math.max(8, (width - LIST_W - 230) / 2);
    }

    private int top() {
        return 44;
    }

    private int visibleRows() {
        return Math.max(4, (height - top() - 24) / ROW);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        hits.clear();
        int x = left();
        g.drawString(font, creating ? "New system" : "Tune a system", x, 8, 0xFFFFFFFF, false);

        if (creating) {
            g.drawString(font, "will be created as " + draftId, x, 22, 0xFFC8D4E8, false);
        } else {
            ContinuumSystem system = systems.get(Math.floorMod(index, systems.size()));
            String title = "<  " + system.name() + "  (" + system.id() + ")  >";
            g.drawString(font, title, x, 22, 0xFFC8D4E8, false);
            hit(x, 20, font.width(title) + 4, 12, "system", null);
        }

        int rowsShown = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - rowsShown)));
        for (int i = 0; i < rowsShown && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int y = top() + i * ROW;
            if (row.kind() == Kind.HEADER) {
                g.fill(x, y, x + LIST_W, y + ROW, 0x30406080);
                g.drawString(font, row.label(), x + 4, y + 4, 0xFFFFD080, false);
                continue;
            }
            if (mx >= x && mx < x + LIST_W && my >= y && my < y + ROW) g.fill(x, y, x + LIST_W, y + ROW, 0x20FFFFFF);
            g.drawString(font, font.plainSubstrByWidth(row.label(), 150), x + 4, y + 4, 0xFFAEBBCF, false);
            int vx = x + 160;
            switch (row.kind()) {
                case TEXT -> {
                    g.drawString(font, font.plainSubstrByWidth(text(row), LIST_W - 170), vx, y + 4, 0xFFFFFFFF, false);
                    hit(vx, y + 1, LIST_W - 164, 13, "edit", row);
                }
                case CHOICE -> {
                    g.fill(vx, y + 2, vx + 110, y + 13, 0xFF263040);
                    g.drawCenteredString(font, text(row), vx + 55, y + 4, 0xFFFFFFFF);
                    hit(vx, y + 1, 110, 13, "cycle", row);
                }
                case COLOR -> {
                    String hex = text(row);
                    g.fill(vx, y + 2, vx + 22, y + 13, 0xFF000000 | parseColor(hex));
                    g.renderOutline(vx, y + 2, 22, 11, 0xFF3A4452);
                    g.drawString(font, hex, vx + 28, y + 4, 0xFFFFFFFF, false);
                    hit(vx, y + 1, 100, 13, "edit", row);
                }
                case NUMBER -> {
                    double value = number(row);
                    button(g, mx, my, vx, y + 1, 12, "-", "dec", row);
                    g.drawCenteredString(font, String.format(Locale.ROOT, "%.2f", value), vx + 48, y + 4, 0xFFFFFFFF);
                    hit(vx + 12, y + 1, 72, 13, "edit", row);
                    button(g, mx, my, vx + 84, y + 1, 12, "+", "inc", row);
                    int barX = vx + 104;
                    int barW = 76;
                    g.fill(barX, y + 6, barX + barW, y + 9, 0xFF2A3340);
                    double t = (value - row.min()) / (row.max() - row.min());
                    g.fill(barX, y + 6, barX + (int) (barW * Math.max(0, Math.min(1, t))), y + 9, 0xFF4F8FCF);
                    hit(barX, y + 1, barW, 13, "bar", row);
                }
                default -> {}
            }
        }

        int px = x + LIST_W + 14;
        g.drawString(font, "Preview", px, top() - 12, 0xFFAEBBCF, false);
        ContinuumSystem shown = draftSystem();
        if (shown != null) {
            preview.drawSystem(g, shown, px, top(), 200);
        } else {
            g.fill(px, top(), px + 200, top() + 200, 0xFF05070C);
        }
        g.drawString(font, "drag to turn, scroll to zoom", px, top() + 204, 0xFF7F8A99, false);

        int ay = top() + 220;
        if (creating) {
            action(g, mx, my, px, ay, 200, "Create system", "apply");
            action(g, mx, my, px, ay + 20, 200, "Free space for it", "place");
        } else {
            action(g, mx, my, px, ay, 200, "Apply to the server", "apply");
            action(g, mx, my, px, ay + 20, 200, "Undo my changes", "undo");
            action(g, mx, my, px, ay + 40, 200, "Reset server edits", "resetServer");
        }
        action(g, mx, my, px, ay + 60, 200, creating ? "Tune an existing system" : "New system...", "mode");
        action(g, mx, my, px, ay + 80, 200, "Back", "close");
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, 220), px, ay + 104, 0xFF66DDFF, false);

        super.render(g, mx, my, partialTick);
    }

    private static int parseColor(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x888888;
        }
    }

    private void hit(int x, int y, int w, int h, String act, @Nullable Row row) {
        hits.add(new Object[] { act, row, x, y, w, h });
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String act, Row row) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, over ? 0xFF3A4A60 : 0xFF263040);
        g.drawCenteredString(font, label, x + w / 2, y + 3, 0xFFFFFFFF);
        hit(x, y, w, 13, act, row);
    }

    private void action(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String act) {
        boolean over = mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, over ? 0xFF3A4A60 : 0xFF263040);
        g.renderOutline(x, y, w, 16, 0xFF3A4452);
        g.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
        hits.add(new Object[] { "action:" + act, null, x, y, w, 16 });
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (preview.mouseClicked(mx, my)) return true;
        if (editor.isVisible() && editor.mouseClicked(mx, my, button)) return true;
        commitEdit();

        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            int x = (int) h[2];
            int y = (int) h[3];
            if (mx < x || mx >= x + (int) h[4] || my < y || my >= y + (int) h[5]) continue;
            String act = (String) h[0];
            Row row = (Row) h[1];

            if (act.startsWith("action:")) {
                runAction(act.substring("action:".length()));
                return true;
            }
            switch (act) {
                case "system" -> {
                    index += mx < x + font.width("<  ") + 6 ? -1 : 1;
                    beginTune();
                }
                case "dec" -> setNumber(row, number(row) - row.step() * (hasShiftDown() ? 10 : 1));
                case "inc" -> setNumber(row, number(row) + row.step() * (hasShiftDown() ? 10 : 1));
                case "bar" -> setNumber(row, row.min() + (mx - x) / (double) (int) h[4] * (row.max() - row.min()));
                case "cycle" -> {
                    int next = (row.choices().indexOf(text(row)) + 1) % row.choices().size();
                    set(row, new JsonPrimitive(row.choices().get(next)));
                }
                case "edit" -> beginEdit(row, x, y);
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void runAction(String id) {
        switch (id) {
            case "apply" -> apply();
            case "undo" -> beginTune();
            case "resetServer" -> resetServer();
            case "place" -> {
                double[] at = ContinuumCreateScreen.freeSpot();
                JsonArray galaxy = new JsonArray();
                for (double v : at) galaxy.add(Math.round(v * 10.0) / 10.0);
                draft.add("galaxy", galaxy);
                previewSystem = null;
            }
            case "mode" -> {
                if (creating && !systems.isEmpty()) beginTune();
                else beginCreate();
            }
            case "close" -> onClose();
            default -> {}
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (preview.mouseDragged(dx, dy)) return true;
        for (Object[] h : hits) {
            if (!"bar".equals(h[0])) continue;
            int x = (int) h[2];
            int y = (int) h[3];
            if (my >= y && my < y + (int) h[5] && mx >= x - 4 && mx <= x + (int) h[4] + 4) {
                Row row = (Row) h[1];
                setNumber(row, row.min() + (mx - x) / (double) (int) h[4] * (row.max() - row.min()));
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        preview.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (preview.mouseScrolled(mx, my, delta)) return true;
        scroll -= (int) Math.signum(delta) * 2;
        return true;
    }

    private void beginEdit(Row row, int x, int y) {
        editing = row;
        editor.setX(x);
        editor.setY(y);
        editor.setMaxLength(row.kind() == Kind.TEXT ? 120 : 12);
        editor.setWidth(row.kind() == Kind.TEXT ? LIST_W - 164 : 90);
        editor.setValue(row.kind() == Kind.NUMBER ? String.valueOf(number(row)) : text(row));
        editor.setVisible(true);
        setFocused(editor);
        editor.setFocused(true);
    }

    private void commitEdit() {
        if (editing == null || !editor.isVisible()) return;
        String text = editor.getValue().trim();
        try {
            if (editing.kind() == Kind.TEXT) {
                set(editing, new JsonPrimitive(text));
                if (creating && editing.path().equals("name")) draftId = freshId(text);
            } else if (editing.kind() == Kind.COLOR) {
                String clean = text.startsWith("#") ? text.substring(1) : text;
                if (clean.length() == 6) {
                    Integer.parseInt(clean, 16);
                    set(editing, new JsonPrimitive("#" + clean.toLowerCase(Locale.ROOT)));
                }
            } else {
                setNumber(editing, Double.parseDouble(text));
            }
        } catch (NumberFormatException ignored) {}
        editor.setVisible(false);
        editor.setFocused(false);
        editing = null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editor.isVisible()) {
            if (key == 257 || key == 335) {
                commitEdit();
                return true;
            }
            if (key == 256) {
                editor.setVisible(false);
                editing = null;
                return true;
            }
            return editor.keyPressed(key, scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (editor.isVisible() && editor.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        preview.close();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
