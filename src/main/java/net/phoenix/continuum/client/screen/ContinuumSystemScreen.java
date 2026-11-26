package net.phoenix.continuum.client.screen;

import net.phoenix.continuum.client.ContinuumText;
import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.TuningPatches;
import net.phoenix.continuum.network.C2SCreatePacket;
import net.phoenix.continuum.network.C2STunePacket;
import net.phoenix.continuum.network.ContinuumNetwork;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
            "pulsar", "protostar", "planetary_nebula", "black_hole", "quasar", "binary_black_hole", "dyson_swarm", "dyson_sphere" };
    private static final String[] STAGES = { "unknown", "detected", "surveyed" };

    private static final int ROW = 15;
    private static final int LIST_W = 340;

    private static final int MIN_W = LIST_W + 230;
    private static final int MIN_H = 430;
    private float uiScale = 1.0f;
    private int vw;
    private int vh;

    private final Screen parent;
    private final List<ContinuumSystem> systems = new ArrayList<>();
    private int dataVersion = ContinuumData.version();
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

    public ContinuumSystemScreen(Screen parent, @Nullable ResourceLocation start) {
        super(ContinuumLang.tr("phoenix_continuum.continuum_system_screen.continuum_systems"));
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

    private void listAgainIfChanged() {
        if (dataVersion == ContinuumData.version()) return;
        dataVersion = ContinuumData.version();
        ResourceLocation current = systems.isEmpty() ? null : systems.get(Math.floorMod(index, systems.size())).id();
        systems.clear();
        systems.addAll(ContinuumData.systems());
        systems.sort(Comparator.comparing(ContinuumSystem::name));
        for (int i = 0; i < systems.size(); i++) {
            if (systems.get(i).id().equals(current)) index = i;
        }
    }

    private void openPicker() {
        ResourceLocation current = systems.isEmpty() ? null : systems.get(Math.floorMod(index, systems.size())).id();
        minecraft.setScreen(new ContinuumBodyPickerScreen(this, new ArrayList<>(ContinuumData.allBodies()), current,
                id -> minecraft.setScreen(new ContinuumTunerScreen(this, id)), this::selectSystem));
    }

    private void selectSystem(ResourceLocation id) {
        for (int i = 0; i < systems.size(); i++) {
            if (systems.get(i).id().equals(id)) {
                index = i;
                beginTune();
                return;
            }
        }
    }

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
        draftId = freshId(ContinuumText.str("phoenix_continuum.continuum_system_screen.new_system"));
        status = "";
        rebuild();
    }

    private ResourceLocation freshId(String display) {
        String slug = display.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) slug = "new_system";
        String candidate = slug;
        int n = 2;
        while (ContinuumData.system(new ResourceLocation("phoenix_continuum", candidate)) != null) {
            candidate = slug + "_" + n++;
        }
        return new ResourceLocation("phoenix_continuum", candidate);
    }

    private void rebuild() {
        rows.clear();
        scroll = 0;
        editing = null;
        previewSystem = null;
        rows.add(Row.header(ContinuumText.str("phoenix_continuum.continuum_system_screen.star")));
        rows.add(Row.text("name", ContinuumText.str("phoenix_continuum.continuum_system_screen.name"), "System"));
        rows.add(Row.text("description", ContinuumText.str("phoenix_continuum.continuum_system_screen.description"), ""));
        rows.add(Row.choice("star_kind", ContinuumText.str("phoenix_continuum.continuum_system_screen.kind"), "star", STAR_KINDS));
        rows.add(Row.color("star_color", ContinuumText.str("phoenix_continuum.continuum_system_screen.colour"), "#ffd9a0"));
        rows.add(Row.number("star_radius", ContinuumText.str("phoenix_continuum.continuum_system_screen.size"), 0.3, 4.0, 0.05, 1.0));
        rows.add(Row.choice("initial_stage", ContinuumText.str("phoenix_continuum.continuum_system_screen.starting_stage"), "unknown", STAGES));
        rows.add(Row.header(ContinuumText.str("phoenix_continuum.continuum_system_screen.position_in_the_galaxy")));
        rows.add(Row.number("galaxy.0", "x", -80, 80, 0.5, 0));
        rows.add(Row.number("galaxy.1", "y (height)", -20, 20, 0.1, 0));
        rows.add(Row.number("galaxy.2", "z", -80, 80, 0.5, 0));
        String kind = kind();
        if (kind.equals("black_hole") || kind.equals("quasar") || kind.equals("binary_black_hole")) {
            rows.add(Row.header(ContinuumText.str("phoenix_continuum.continuum_system_screen.accretion_disk")));
            rows.add(Row.number("hole.disk_size", ContinuumText.str("phoenix_continuum.continuum_system_screen.disk_size"), 0.2, 4, 0.05, 1.0));
            rows.add(Row.number("hole.disk_intensity", ContinuumText.str("phoenix_continuum.continuum_system_screen.disk_brightness"), 0, 4, 0.05, 1.0));
            rows.add(Row.number("hole.feeding", ContinuumText.str("phoenix_continuum.continuum_system_screen.feeding"), 0, 1, 0.02, 0.0));
            rows.add(Row.number("hole.tilt", ContinuumText.str("phoenix_continuum.continuum_system_screen.tilt_map_view"), 0, 1, 0.025, 0.075));
            rows.add(Row.color("hole.hot_color", ContinuumText.str("phoenix_continuum.continuum_system_screen.hot_colour"), "#ffc060"));
            rows.add(Row.color("hole.cool_color", ContinuumText.str("phoenix_continuum.continuum_system_screen.cool_colour"), "#c04020"));
            rows.add(Row.number("hole.sky_inclination", ContinuumText.str("phoenix_continuum.continuum_system_screen.inclination_sky"), 0, 90, 1, 45));
            rows.add(Row.number("hole.sky_lean", ContinuumText.str("phoenix_continuum.continuum_system_screen.lean_sky"), -90, 90, 1, 15));
            if (kind.equals("binary_black_hole")) {
                rows.add(Row.number("hole.separation", ContinuumText.str("phoenix_continuum.continuum_system_screen.separation"), 2.2, 8, 0.1, 3.2));
                rows.add(Row.number("hole.mass_ratio", ContinuumText.str("phoenix_continuum.continuum_system_screen.mass_ratio"), 0.1, 1, 0.05, 0.6));
                rows.add(Row.number("hole.period_seconds", ContinuumText.str("phoenix_continuum.continuum_system_screen.orbit_period_s"), 2, 120, 1, 24));
            }
        }
    }

    private String kind() {
        JsonElement e = draft.get("star_kind");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "star";
    }

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

    private void apply() {
        if (creating) {
            ContinuumNetwork.sendToServer(new C2SCreatePacket(true, draftId, draft.toString()));
            status = ContinuumText.str("phoenix_continuum.continuum_system_screen.asked_to_create", draftId.getPath());
            return;
        }
        int sent = 0;
        for (String key : TuningPatches.EDITABLE_SYSTEM) {
            JsonElement now = draft.get(key);
            JsonElement before = original.get(key);
            if (now == null || now.equals(before)) continue;
            ContinuumNetwork.sendToServer(C2STunePacket.forSystem(draftId, key, now.toString()));
            sent++;
        }
        status = sent == 0 ? ContinuumText.str("phoenix_continuum.continuum_system_screen.nothing_changed") : ContinuumText.str("phoenix_continuum.continuum_system_screen.sent_changes", sent);
        if (sent > 0) original = draft.deepCopy();
    }

    private void resetServer() {
        if (creating) return;
        ContinuumNetwork.sendToServer(C2STunePacket.resetSystem(draftId));
        status = ContinuumText.str("phoenix_continuum.continuum_system_screen.asked_the_server_to_drop_its");
    }

    @Override
    protected void init() {
        editor = new EditBox(font, 0, 0, 90, 13, Component.empty());
        editor.setVisible(false);
        editor.setMaxLength(12);
        addRenderableWidget(editor);
    }

    private int left() {
        return Math.max(8, (vw - LIST_W - 230) / 2);
    }

    private int top() {
        return 44;
    }

    private int visibleRows() {
        return Math.max(4, (vh - top() - 24) / ROW);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float partialTick) {
        listAgainIfChanged();
        uiScale = Math.min(1.0f, Math.min(width / (float) MIN_W, height / (float) MIN_H));
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);
        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1.0f);
        renderContent(g, (int) (rawMx / uiScale), (int) (rawMy / uiScale), partialTick);
        g.pose().popPose();
    }

    private void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, vw, vh, 0xF00A0E14);
        hits.clear();
        int x = left();
        g.drawString(font, creating ? ContinuumText.str("phoenix_continuum.continuum_system.new_system") : ContinuumText.str("phoenix_continuum.continuum_system.tune_a_system"), x, 8, 0xFFFFFFFF, false);

        if (creating) {
            g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_system.will_be_created_as", draftId), x, 22, 0xFFC8D4E8, false);
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
        g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_system.preview"), px, top() - 12, 0xFFAEBBCF, false);
        ContinuumSystem shown = draftSystem();
        if (shown != null) {
            preview.drawSystem(g, shown, px, top(), 200);
        } else {
            g.fill(px, top(), px + 200, top() + 200, 0xFF05070C);
        }
        g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_system.drag_to_turn_scroll_to"), px, top() + 204, 0xFF7F8A99, false);

        int ay = top() + 220;
        if (creating) {
            action(g, mx, my, px, ay, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.create_system"), "apply");
            action(g, mx, my, px, ay + 20, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.free_space_for_it"), "place");
        } else {
            action(g, mx, my, px, ay, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.apply_to_the_server"), "apply");
            action(g, mx, my, px, ay + 20, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.undo_my_changes"), "undo");
            action(g, mx, my, px, ay + 40, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.reset_server_edits"), "resetServer");
        }
        action(g, mx, my, px, ay + 60, 200, creating ? ContinuumText.str("phoenix_continuum.continuum_system_screen.tune_an_existing_system") : ContinuumText.str("phoenix_continuum.continuum_system_screen.new_system_2"), "mode");
        int backY = ay + 80;
        if (!creating) {
            action(g, mx, my, px, ay + 80, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.sky_view"), "sky");
            backY = ay + 100;
        }
        action(g, mx, my, px, backY, 200, ContinuumText.str("phoenix_continuum.continuum_system_screen.back"), "close");
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, 220), px, backY + 24, 0xFF66DDFF, false);

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

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= uiScale;
        my /= uiScale;
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
                    if (mx < x + font.width("<  ") + 6) {
                        index--;
                        beginTune();
                    } else if (mx >= x + (int) h[4] - font.width("  >") - 8) {
                        index++;
                        beginTune();
                    } else {
                        
                        openPicker();
                    }
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

    private void openSky() {
        ContinuumSystem system = draftSystem();
        if (system == null || creating) return;
        ContinuumBody from = null;
        for (ContinuumBody b : ContinuumData.bodiesOf(system.id())) {
            if (b.isCentral() || b.transientInfo() != null || b.isMoon()) continue;
            from = b;
            break;
        }
        if (from == null) {
            status = ContinuumText.str("phoenix_continuum.continuum_system_screen.this_system_has_no_worlds_to");
            return;
        }
        minecraft.setScreen(new ContinuumSkyScreen(this, from, system));
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
            case "sky" -> openSky();
            case "close" -> onClose();
            default -> {}
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= uiScale;
        my /= uiScale;
        dx /= uiScale;
        dy /= uiScale;
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        mx /= uiScale;
        my /= uiScale;
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
