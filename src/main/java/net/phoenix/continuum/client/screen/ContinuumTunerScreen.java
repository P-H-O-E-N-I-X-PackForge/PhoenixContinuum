package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import net.phoenix.continuum.client.render.SurfaceMap;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumTuner;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class ContinuumTunerScreen extends Screen {

    private enum Kind {
        NUMBER,
        COLOR,
        BOOL,
        TEXT,
        CHOICE,
        HEADER,
        BUTTON
    }

    private record Field(String path, String label, Kind kind, double min, double max, double step, Object fallback,
                         boolean server, List<String> choices) {

        Field(String path, String label, Kind kind, double min, double max, double step, Object fallback) {
            this(path, label, kind, min, max, step, fallback, false, List.of());
        }

        static Field server(String path, String label, Kind kind, double min, double max, double step,
                            Object fallback) {
            return new Field(path, label, kind, min, max, step, fallback, true, List.of());
        }

        static Field choice(String path, String label, String fallback, String... options) {
            return new Field(path, label, Kind.CHOICE, 0, 0, 0, fallback, true, List.of(options));
        }

        static Field header(String label) {
            return new Field("", label, Kind.HEADER, 0, 0, 0, "", false, List.of());
        }

        static Field button(String id, String label) {
            return new Field(id, label, Kind.BUTTON, 0, 0, 0, "", false, List.of());
        }
    }

    private static final int ROW = 15;
    private static final int LIST_W = 330;

    /** Text fields that are sentences rather than ids: these open the roomy editor. */
    private static final java.util.Set<String> LONG_TEXT = java.util.Set.of("name", "description", "lore.detected",
            "lore.surveyed");

    private static final int SV_SIZE = 100;
    private static final int HUE_W = 14;
    private static final int HUE_GAP = 6;
    private static final int PICKER_PAD = 8;

    private Field pickerField;
    private float pickerHue;
    private float pickerSat = 1.0f;
    private float pickerVal = 1.0f;
    private int pickerX;
    private int pickerY;
    private boolean draggingSV;
    private boolean draggingHue;

    private final Screen parent;
    private final List<ContinuumBody> bodies = new ArrayList<>();
    private int bodyIndex;
    private final List<Field> fields = new ArrayList<>();
    private int scroll;
    private EditBox editor;
    private Field editing;
    private String status = "";
    private long previewDueAt = -1;
    private final ScenePreview preview = new ScenePreview();
    private final List<Object[]> hits = new ArrayList<>();

    public ContinuumTunerScreen(Screen parent, ResourceLocation start) {
        super(Component.literal("Continuum tuner"));
        this.parent = parent;
        for (ContinuumBody body : ContinuumData.allBodies()) {
            if (body.type() == ContinuumBody.Type.STAR) continue;
            bodies.add(body);
        }
        bodies.sort(Comparator.comparing((ContinuumBody b) -> b.system().toString()).thenComparing(ContinuumBody::name));
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i).id().equals(start)) bodyIndex = i;
        }
        rebuildFields();
    }

    public static boolean allowed() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.hasSingleplayerServer() || mc.player.hasPermissions(2));
    }

    private ContinuumBody body() {
        ContinuumBody listed = bodies.get(Math.floorMod(bodyIndex, bodies.size()));
        
        ContinuumBody current = ContinuumData.body(listed.id());
        return current != null ? current : listed;
    }

    private void rebuildFields() {
        fields.clear();
        scroll = 0;
        editing = null;
        if (bodies.isEmpty()) return;
        ContinuumBody body = body();
        JsonObject json = ContinuumTuner.merged(body.id());

        if (body.type() == ContinuumBody.Type.BLACK_HOLE) {
            fields.add(new Field("hole_tilt", "Disk tilt (map view)  0 edge-on, 1 face-on", Kind.NUMBER, 0, 1, 0.1, 0.075));
            fields.add(new Field("sky_hole_inclination", "Disk inclination (sky), degrees", Kind.NUMBER, 0, 90, 5, 45.0));
            fields.add(new Field("sky_hole_lean", "Disk lean (sky), degrees", Kind.NUMBER, -90, 90, 5, 15.0));
            return;
        }

        fields.add(new Field("radius", "Radius (Earth = 1)", Kind.NUMBER, 0.1, 200, 0.1, 1.0));
        fields.add(new Field("gas_giant", "Gas giant", Kind.BOOL, 0, 1, 1, false));
        fields.add(new Field("axial_tilt", "Axial tilt", Kind.NUMBER, -180, 180, 5, 0.0));
        fields.add(new Field("spin_deg_per_sec", "Spin", Kind.NUMBER, -360, 360, 1, 4.0));
        fields.add(new Field("cloud_spin_deg_per_sec", "Cloud spin", Kind.NUMBER, -360, 360, 1, 6.0));
        fields.add(new Field("surface.seed", "Seed", Kind.NUMBER, 0, 999999, 1, 1.0));
        fields.add(new Field("surface.scale", "Terrain scale", Kind.NUMBER, 0.1, 60, 0.25, 2.5));
        fields.add(new Field("surface.bump", "Bumpiness", Kind.NUMBER, 0, 6, 0.1, 0.7));
        fields.add(new Field("surface.ocean_level", "Ocean level", Kind.NUMBER, 0, 1, 0.05, 0.0));
        fields.add(new Field("surface.polar_ice", "Polar ice", Kind.NUMBER, 0, 1, 0.05, 0.0));
        fields.add(new Field("surface.cloud_cover", "Cloud cover", Kind.NUMBER, 0, 1, 0.05, 0.0));
        fields.add(new Field("surface.low", "Low ground colour", Kind.COLOR, 0, 0, 0, "#444444"));
        fields.add(new Field("surface.mid", "Mid ground colour", Kind.COLOR, 0, 0, 0, "#888888"));
        fields.add(new Field("surface.high", "High ground colour", Kind.COLOR, 0, 0, 0, "#cccccc"));
        fields.add(new Field("surface.ocean_deep", "Deep ocean colour", Kind.COLOR, 0, 0, 0, "#03123a"));
        fields.add(new Field("surface.ocean_shallow", "Shallow ocean colour", Kind.COLOR, 0, 0, 0, "#1a6f8f"));
        fields.add(new Field("atmosphere.color", "Atmosphere colour", Kind.COLOR, 0, 0, 0, "#6aa8ff"));
        fields.add(new Field("atmosphere.density", "Atmosphere density", Kind.NUMBER, 0, 4, 0.05, 0.0));
        if (json.has("rings")) {
            fields.add(new Field("rings.inner", "Ring inner radius", Kind.NUMBER, 1, 30, 0.1, 1.4));
            fields.add(new Field("rings.outer", "Ring outer radius", Kind.NUMBER, 1, 60, 0.1, 2.4));
            fields.add(new Field("rings.tilt", "Ring tilt", Kind.NUMBER, -90, 90, 5, 0.0));
            fields.add(new Field("rings.opacity", "Ring opacity", Kind.NUMBER, 0, 1, 0.1, 0.85));
            fields.add(new Field("rings.color", "Ring colour", Kind.COLOR, 0, 0, 0, "#d8c8a0"));
            fields.add(new Field("rings.color2", "Ring colour 2", Kind.COLOR, 0, 0, 0, "#8a7a60"));
        }
        addServerFields(json);
    }

    private void addServerFields(JsonObject json) {
        fields.add(Field.header("Visual edits above are local. Push them to apply for everyone."));
        fields.add(Field.button("push", "Push visual edits to the server"));
        fields.add(Field.button("newbody", "New body in this system..."));
        fields.add(Field.button("tunesystem", "Tune this system..."));
        fields.add(Field.button("newsystem", "New system..."));
        fields.add(Field.header("Server (applies for everyone, saved in config/phoenix_continuum/tuning)"));
        fields.add(Field.server("name", "Name", Kind.TEXT, 0, 0, 0, ""));
        fields.add(Field.server("description", "Description", Kind.TEXT, 0, 0, 0, ""));
        fields.add(Field.server("lore.detected", "Lore (detected)", Kind.TEXT, 0, 0, 0, ""));
        fields.add(Field.server("lore.surveyed", "Lore (surveyed)", Kind.TEXT, 0, 0, 0, ""));
        fields.add(Field.server("trip_minutes", "Trip minutes", Kind.NUMBER, 5, 10000, 5, 10.0));
        fields.add(Field.choice("initial_stage", "Starting stage", "unknown", "unknown", "detected", "surveyed"));
        fields.add(Field.choice("gate.min_tier", "Rocket tier needed", "start", "start", "mid", "late", "endgame"));
        fields.add(Field.server("orbit.radius_au", "Orbit radius (AU)", Kind.NUMBER, 0, 1000, 0.1, 1.0));
        fields.add(Field.server("orbit.period_days", "Orbit period (days)", Kind.NUMBER, 1, 200000, 5, 100.0));
        fields.add(Field.server("orbit.phase", "Orbit phase", Kind.NUMBER, 0, 1, 0.05, 0.0));
        if (json.has("pdim_cost")) {
            fields.add(Field.server("pdim_cost.item", "Personal dimension cost item", Kind.TEXT, 0, 0, 0,
                    "minecraft:ender_pearl"));
            fields.add(Field.server("pdim_cost.count", "Personal dimension cost count", Kind.NUMBER, 0, 4096, 1, 2.0));
        }
        fields.add(Field.header("Yields"));
        int count = json.has("yields") && json.get("yields").isJsonArray() ? json.getAsJsonArray("yields").size() : 0;
        for (int i = 0; i < count; i++) {
            fields.add(Field.server("yields." + i + ".item", "  #" + (i + 1) + " item", Kind.TEXT, 0, 0, 0,
                    "minecraft:stone"));
            fields.add(Field.server("yields." + i + ".min", "  #" + (i + 1) + " min", Kind.NUMBER, 1, 4096, 1, 1.0));
            fields.add(Field.server("yields." + i + ".max", "  #" + (i + 1) + " max", Kind.NUMBER, 1, 4096, 1, 1.0));
            fields.add(Field.server("yields." + i + ".chance", "  #" + (i + 1) + " chance", Kind.NUMBER, 0, 1, 0.1,
                    1.0));
        }
        fields.add(Field.button("yield+", "Add a yield"));
        if (count > 0) fields.add(Field.button("yield-", "Remove the last yield"));
    }

    private JsonElement read(Field field) {
        JsonElement cursor = ContinuumTuner.merged(body().id());
        for (String part : field.path().split("[.]")) {
            if (cursor == null) return null;
            if (cursor.isJsonObject()) {
                cursor = cursor.getAsJsonObject().get(part);
            } else if (cursor.isJsonArray()) {
                try {
                    int index = Integer.parseInt(part);
                    cursor = index >= 0 && index < cursor.getAsJsonArray().size() ?
                            cursor.getAsJsonArray().get(index) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cursor;
    }

    private String text(Field field) {
        JsonElement value = read(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : String.valueOf(field.fallback());
    }

    private double number(Field field) {
        JsonElement value = read(field);
        if (value != null && value.isJsonPrimitive()) {
            try {
                return value.getAsDouble();
            } catch (RuntimeException ignored) {}
        }
        return ((Number) field.fallback()).doubleValue();
    }

    private String color(Field field) {
        JsonElement value = read(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : (String) field.fallback();
    }

    private boolean bool(Field field) {
        JsonElement value = read(field);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : (Boolean) field.fallback();
    }

    private void set(Field field, JsonElement value) {
        if (field.server()) {
            sendServer(field, value);
            return;
        }
        ContinuumTuner.set(body().id(), field.path(), value);
        previewDueAt = System.currentTimeMillis() + 120;
    }

    private void send(String path, JsonElement value) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new net.phoenix.continuum.network.C2STunePacket(body().id(), path,
                        value == null ? "" : value.toString()));
        status = "Sent to the server.";
    }

    private void sendServer(Field field, JsonElement value) {
        String[] parts = field.path().split("[.]");
        if (parts[0].equals("yields") && parts.length == 3) {
            JsonObject json = ContinuumTuner.merged(body().id());
            com.google.gson.JsonArray array = json.has("yields") && json.get("yields").isJsonArray() ?
                    json.getAsJsonArray("yields").deepCopy() : new com.google.gson.JsonArray();
            int index = Integer.parseInt(parts[1]);
            if (index < array.size() && array.get(index).isJsonObject()) {
                array.get(index).getAsJsonObject().add(parts[2], value);
                send("yields", array);
            }
            return;
        }
        send(field.path(), value);
    }

    private void editYields(boolean add) {
        JsonObject json = ContinuumTuner.merged(body().id());
        com.google.gson.JsonArray array = json.has("yields") && json.get("yields").isJsonArray() ?
                json.getAsJsonArray("yields").deepCopy() : new com.google.gson.JsonArray();
        if (add) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", "minecraft:iron_ingot");
            entry.addProperty("min", 1);
            entry.addProperty("max", 4);
            entry.addProperty("chance", 1.0);
            array.add(entry);
        } else if (array.size() > 0) {
            array.remove(array.size() - 1);
        }
        send("yields", array);
    }

    private void pushVisual() {
        var leaves = ContinuumTuner.leaves(body().id());
        if (leaves.isEmpty()) {
            status = "No visual edits to push.";
            return;
        }
        leaves.forEach(this::send);
        ContinuumTuner.reset(body().id());
        SurfaceMap.invalidate(body().id());
        status = "Pushed " + leaves.size() + " edit(s).";
    }

    private void setNumber(Field field, double value) {
        double clamped = Math.max(field.min(), Math.min(field.max(), value));
        clamped = field.min() + Math.round((clamped - field.min()) / field.step()) * field.step();
        clamped = Math.max(field.min(), Math.min(field.max(), Math.round(clamped * 10000.0) / 10000.0));
        set(field, new JsonPrimitive(field.step() >= 1 ? (Number) (long) Math.round(clamped) : (Number) clamped));
    }

    private int left() {
        return Math.max(8, (width - LIST_W - 210) / 2);
    }

    private int top() {
        return 44;
    }

    private int rows() {
        return Math.max(4, (height - top() - 40) / ROW);
    }

    @Override
    protected void init() {
        editor = new EditBox(font, 0, 0, 90, 13, Component.empty());
        editor.setBordered(true);
        editor.setVisible(false);
        editor.setMaxLength(12);
        addRenderableWidget(editor);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xF00A0E14);
        hits.clear();
        if (bodies.isEmpty()) {
            g.drawCenteredString(font, "No bodies loaded.", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        if (previewDueAt > 0 && System.currentTimeMillis() >= previewDueAt) {
            previewDueAt = -1;
            SurfaceMap.invalidate(body().id());
        }

        ContinuumBody body = body();
        int x = left();
        g.drawString(font, "Continuum tuner", x, 8, 0xFFFFFFFF, false);
        String title = "<  " + body.name() + "  (" + body.id() + ")  >";
        g.drawString(font, title, x, 22, 0xFFC8D4E8, false);
        hit(g, mx, my, x, 20, font.width(title) + 4, 12, "body", false);
        if (ContinuumTuner.isTuned(body.id())) g.drawString(font, "tuned", x + LIST_W - 30, 8, 0xFFFFB040, false);

        int rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, fields.size() - rows)));
        for (int i = 0; i < rows && i + scroll < fields.size(); i++) {
            Field field = fields.get(i + scroll);
            int y = top() + i * ROW;
            boolean hover = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW;
            if (hover) g.fill(x, y, x + LIST_W, y + ROW, 0x20FFFFFF);
            if (field.kind() == Kind.HEADER) {
                g.fill(x, y, x + LIST_W, y + ROW, 0x30406080);
                g.drawString(font, font.plainSubstrByWidth(field.label(), LIST_W - 8), x + 4, y + 4, 0xFFFFD080, false);
                continue;
            }
            if (field.kind() == Kind.BUTTON) {
                button(g, mx, my, x + 4, y + 1, LIST_W - 8, field.label(), field, "button");
                continue;
            }
            g.drawString(font, font.plainSubstrByWidth(field.label(), 150), x + 4, y + 4,
                    field.server() ? 0xFFD8C890 : 0xFFAEBBCF, false);

            int vx = x + 160;
            switch (field.kind()) {
                case TEXT -> {
                    String value = text(field);
                    g.drawString(font, font.plainSubstrByWidth(value, LIST_W - 170), vx, y + 4, 0xFFFFFFFF, false);
                    hit(g, mx, my, vx, y + 1, LIST_W - 164, 13, field, "edit");
                }
                case CHOICE -> {
                    g.fill(vx, y + 2, vx + 90, y + 13, 0xFF263040);
                    g.drawCenteredString(font, text(field), vx + 45, y + 4, 0xFFFFFFFF);
                    hit(g, mx, my, vx, y + 1, 90, 13, field, "cycle");
                }
                default -> {}
            }
            switch (field.kind()) {
                case NUMBER -> {
                    double value = number(field);
                    String text = field.step() >= 1 ? String.valueOf(Math.round(value)) :
                            String.format(Locale.ROOT, "%.3f", value);
                    button(g, mx, my, vx, y + 1, 12, "-", field, "dec");
                    g.drawCenteredString(font, text, vx + 12 + 36, y + 4, 0xFFFFFFFF);
                    hit(g, mx, my, vx + 12, y + 1, 72, 13, field, "edit");
                    button(g, mx, my, vx + 84, y + 1, 12, "+", field, "inc");
                    int barX = vx + 100;
                    int barW = LIST_W - 10 - (barX - x);
                    g.fill(barX, y + 6, barX + barW, y + 9, 0xFF2A3340);
                    double t = (value - field.min()) / (field.max() - field.min());
                    g.fill(barX, y + 6, barX + (int) (barW * Math.max(0, Math.min(1, t))), y + 9, 0xFF4F8FCF);
                    hit(g, mx, my, barX, y + 1, barW, 13, field, "bar");
                }
                case COLOR -> {
                    String hex = color(field);
                    int argb = parseColor(hex);
                    g.fill(vx, y + 2, vx + 22, y + 13, 0xFF000000 | argb);
                    g.renderOutline(vx, y + 2, 22, 11, 0xFF3A4452);
                    g.drawString(font, hex, vx + 28, y + 4, 0xFFFFFFFF, false);
                    hit(g, mx, my, vx, y + 1, 100, 13, field, "edit");
                    hit(g, mx, my, vx, y + 1, 22, 13, field, "swatch");
                }
                case BOOL -> {
                    boolean on = bool(field);
                    g.fill(vx, y + 2, vx + 28, y + 13, on ? 0xFF2F8F5F : 0xFF3A4452);
                    g.drawCenteredString(font, on ? "on" : "off", vx + 14, y + 4, 0xFFFFFFFF);
                    hit(g, mx, my, vx, y + 1, 28, 13, field, "toggle");
                }
            }
        }

        int px = x + LIST_W + 12;
        g.drawString(font, "Preview", px, top() - 12, 0xFFAEBBCF, false);
        if (body.type() != ContinuumBody.Type.BLACK_HOLE) {
            preview.drawPlanet(g, body.params(), px, top(), 200);
            g.drawString(font, "drag to turn, scroll to zoom", px, top() + 204, 0xFF7F8A99, false);
        } else {
            g.fill(px, top(), px + 200, top() + 200, 0xFF05070C);
            g.drawCenteredString(font, "tune black holes in the", px + 100, top() + 92, 0xFF7F8A99);
            g.drawCenteredString(font, "system screen", px + 100, top() + 104, 0xFF7F8A99);
        }
        int ay = top() + 218;
        action(g, mx, my, px, ay, 200, "Export definition", "export");
        action(g, mx, my, px, ay + 20, 200, "Reset local edits", "reset");
        action(g, mx, my, px, ay + 40, 200, "Reset server edits", "resetServer");
        action(g, mx, my, px, ay + 60, 200, "Close", "close");
        g.drawString(font, "Visual edits: this client only", px, ay + 84, 0xFF7F8A99, false);
        g.drawString(font, "until pushed. Gold rows go to the", px, ay + 94, 0xFF7F8A99, false);
        g.drawString(font, "server and are saved as patches.", px, ay + 104, 0xFF7F8A99, false);
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, 200), px, ay + 120, 0xFF66DDFF, false);

        super.render(g, mx, my, partialTick);
        if (pickerField != null) renderPicker(g);
    }

    private void openPicker(Field field, int x, int y) {
        commitEdit();
        pickerField = field;
        int rgb = parseColor(color(field));
        float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];
        int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
        int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
        pickerX = Math.max(4, Math.min(x, width - totalW - 4));
        pickerY = Math.max(4, Math.min(y + 16, height - totalH - 4));
    }

    private void closePicker() {
        pickerField = null;
        draggingSV = false;
        draggingHue = false;
    }

    private void applyPicker() {
        if (pickerField == null) return;
        int rgb = java.awt.Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0xFFFFFF;
        set(pickerField, new JsonPrimitive(String.format(Locale.ROOT, "#%06x", rgb)));
    }

    /** The same picker PhoenixWiki's theme editor has: a saturation/value square, a hue strip and a hex readout. */
    private void renderPicker(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        g.flush();

        int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
        int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
        g.fill(pickerX, pickerY, pickerX + totalW, pickerY + totalH, 0xF00A0A0E);
        g.renderOutline(pickerX, pickerY, totalW, totalH, 0xFF3A4452);

        int svX = pickerX + PICKER_PAD;
        int svY = pickerY + PICKER_PAD;
        int cell = 2;
        for (int px = 0; px < SV_SIZE; px += cell) {
            float sat = px / (float) (SV_SIZE - 1);
            for (int py = 0; py < SV_SIZE; py += cell) {
                float val = 1f - py / (float) (SV_SIZE - 1);
                int rgb = java.awt.Color.HSBtoRGB(pickerHue, sat, val);
                g.fill(svX + px, svY + py, svX + px + cell, svY + py + cell, 0xFF000000 | (rgb & 0xFFFFFF));
            }
        }
        g.renderOutline(svX, svY, SV_SIZE, SV_SIZE, 0xFF3A4452);
        int cursorX = svX + Math.round(pickerSat * (SV_SIZE - 1));
        int cursorY = svY + Math.round((1f - pickerVal) * (SV_SIZE - 1));
        g.renderOutline(cursorX - 3, cursorY - 3, 6, 6, pickerVal > 0.5f ? 0xFF000000 : 0xFFFFFFFF);

        int hueX = svX + SV_SIZE + HUE_GAP;
        for (int py = 0; py < SV_SIZE; py++) {
            int rgb = java.awt.Color.HSBtoRGB(py / (float) (SV_SIZE - 1), 1f, 1f);
            g.fill(hueX, svY + py, hueX + HUE_W, svY + py + 1, 0xFF000000 | (rgb & 0xFFFFFF));
        }
        g.renderOutline(hueX, svY, HUE_W, SV_SIZE, 0xFF3A4452);
        int markerY = svY + Math.round(pickerHue * (SV_SIZE - 1));
        g.fill(hueX - 2, markerY - 1, hueX + HUE_W + 2, markerY, 0xFFFFFFFF);
        g.fill(hueX - 2, markerY, hueX + HUE_W + 2, markerY + 1, 0xFF000000);

        int previewY = svY + SV_SIZE + 4;
        int rgbNow = java.awt.Color.HSBtoRGB(pickerHue, pickerSat, pickerVal);
        int previewW = totalW - PICKER_PAD * 2;
        g.fill(svX, previewY, svX + previewW, previewY + 12, 0xFF000000 | (rgbNow & 0xFFFFFF));
        g.renderOutline(svX, previewY, previewW, 12, 0xFF3A4452);
        int textColor = (0.299f * ((rgbNow >> 16) & 0xFF) + 0.587f * ((rgbNow >> 8) & 0xFF) +
                0.114f * (rgbNow & 0xFF)) > 140 ? 0xFF000000 : 0xFFFFFFFF;
        g.drawCenteredString(font, String.format(Locale.ROOT, "#%06X", rgbNow & 0xFFFFFF), svX + previewW / 2,
                previewY + 2, textColor);

        g.flush();
        g.pose().popPose();
    }

    private boolean clickPicker(double mx, double my) {
        int totalW = SV_SIZE + HUE_GAP + HUE_W + PICKER_PAD * 2;
        int totalH = SV_SIZE + PICKER_PAD * 2 + 20;
        if (mx < pickerX || mx >= pickerX + totalW || my < pickerY || my >= pickerY + totalH) {
            closePicker();
            return true;
        }
        int svX = pickerX + PICKER_PAD;
        int svY = pickerY + PICKER_PAD;
        int hueX = svX + SV_SIZE + HUE_GAP;
        if (mx >= svX && mx < svX + SV_SIZE && my >= svY && my < svY + SV_SIZE) {
            draggingSV = true;
            dragPicker(mx, my);
        } else if (mx >= hueX && mx < hueX + HUE_W && my >= svY && my < svY + SV_SIZE) {
            draggingHue = true;
            dragPicker(mx, my);
        }
        return true;
    }

    private void dragPicker(double mx, double my) {
        int svX = pickerX + PICKER_PAD;
        int svY = pickerY + PICKER_PAD;
        if (draggingSV) {
            pickerSat = Math.max(0f, Math.min(1f, (float) (mx - svX) / (SV_SIZE - 1)));
            pickerVal = 1f - Math.max(0f, Math.min(1f, (float) (my - svY) / (SV_SIZE - 1)));
            applyPicker();
        } else if (draggingHue) {
            pickerHue = Math.max(0f, Math.min(1f, (float) (my - svY) / (SV_SIZE - 1)));
            applyPicker();
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, String label, Object target, String act) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, hover ? 0xFF35507A : 0xFF263040);
        g.drawCenteredString(font, label, x + w / 2, y + 3, 0xFFFFFFFF);
        hits.add(new Object[] { target, act, x, y, w, 13 });
    }

    private void action(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String act) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? 0xFF35507A : 0xFF263040);
        g.renderOutline(x, y, w, 16, 0xFF3A4452);
        g.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
        hits.add(new Object[] { act, "action", x, y, w, 16 });
    }

    private void hit(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Object target, Object act) {
        hits.add(new Object[] { target, act, x, y, w, h });
    }

    private static int parseColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseInt(clean, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0x808080;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (pickerField != null) return clickPicker(mx, my);
        if (preview.mouseClicked(mx, my)) return true;
        if (editor.isVisible() && editor.mouseClicked(mx, my, button)) return true;
        commitEdit();

        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            int x = (int) h[2];
            int y = (int) h[3];
            if (mx < x || mx >= x + (int) h[4] || my < y || my >= y + (int) h[5]) continue;
            Object target = h[0];
            Object act = h[1];

            if (act.equals("action")) {
                runAction((String) target);
                return true;
            }
            if (target.equals("body")) {
                bodyIndex += mx < x + font.width("<  ") + 6 ? -1 : 1;
                rebuildFields();
                return true;
            }
            Field field = (Field) target;
            switch ((String) act) {
                case "dec" -> setNumber(field, number(field) - field.step() * (hasShiftDown() ? 10 : 1));
                case "inc" -> setNumber(field, number(field) + field.step() * (hasShiftDown() ? 10 : 1));
                case "bar" -> setNumber(field, field.min() + (mx - x) / (double) (int) h[4] * (field.max() - field.min()));
                case "toggle" -> set(field, new JsonPrimitive(!bool(field)));
                case "cycle" -> {
                    int next = (field.choices().indexOf(text(field)) + 1) % field.choices().size();
                    set(field, new JsonPrimitive(field.choices().get(next)));
                }
                case "button" -> {
                    switch (field.path()) {
                        case "push" -> pushVisual();
                        case "newbody" -> minecraft.setScreen(new ContinuumCreateScreen(this, body().system()));
                        case "newsystem" -> minecraft.setScreen(new ContinuumSystemScreen(this, null));
                        case "tunesystem" -> minecraft.setScreen(new ContinuumSystemScreen(this, body().system()));
                        case "yield+" -> editYields(true);
                        case "yield-" -> editYields(false);
                        default -> {}
                    }
                }
                case "swatch" -> openPicker(field, x, y);
                case "edit" -> {
                    if (field.kind() == Kind.TEXT && LONG_TEXT.contains(field.path())) {
                        Field textField = field;
                        minecraft.setScreen(new ContinuumTextScreen(this, field.label().trim(), text(field),
                                value -> set(textField, new JsonPrimitive(value))));
                    } else {
                        beginEdit(field, x, y);
                    }
                }
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSV || draggingHue) {
            dragPicker(mx, my);
            return true;
        }
        if (preview.mouseDragged(dx, dy)) return true;
        
        for (Object[] h : hits) {
            if (!"bar".equals(h[1])) continue;
            int x = (int) h[2];
            int y = (int) h[3];
            if (my >= y && my < y + (int) h[5] && mx >= x - 4 && mx <= x + (int) h[4] + 4) {
                Field field = (Field) h[0];
                setNumber(field, field.min() + (mx - x) / (double) (int) h[4] * (field.max() - field.min()));
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSV = false;
        draggingHue = false;
        preview.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void removed() {
        preview.close();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (preview.mouseScrolled(mx, my, scrollY)) return true;
        scroll -= (int) Math.signum(scrollY) * 2;
        return true;
    }

    private void beginEdit(Field field, int x, int y) {
        editing = field;
        editor.setX(x);
        editor.setY(y);
        editor.setMaxLength(field.kind() == Kind.TEXT ? 120 : 12);
        editor.setWidth(field.kind() == Kind.TEXT ? LIST_W - 164 : 90);
        editor.setValue(field.kind() == Kind.COLOR ? color(field) : field.kind() == Kind.TEXT ? text(field) :
                String.valueOf(number(field)));
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
            } else if (editing.kind() == Kind.COLOR) {
                String clean = text.startsWith("#") ? text.substring(1) : text;
                if (clean.length() == 6) {
                    Integer.parseInt(clean, 16);
                    set(editing, new JsonPrimitive("#" + clean.toLowerCase(Locale.ROOT)));
                }
            } else {
                setNumber(editing, Double.parseDouble(text));
            }
        } catch (NumberFormatException ignored) {
            
        }
        editor.setVisible(false);
        editing = null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (pickerField != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closePicker();
            return true;
        }
        if (editor.isVisible()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editor.setVisible(false);
                editing = null;
                return true;
            }
            return editor.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void runAction(String action) {
        switch (action) {
            case "close" -> onClose();
            case "reset" -> {
                ContinuumTuner.reset(body().id());
                SurfaceMap.invalidate(body().id());
                rebuildFields();
                status = "Reset.";
            }
            case "export" -> export();
            case "resetServer" -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        net.phoenix.continuum.network.C2STunePacket.reset(body().id()));
                status = "Asked the server to remove its edits.";
            }
            default -> {}
        }
    }

    private void export() {
        ResourceLocation id = body().id();
        JsonObject json = ContinuumTuner.merged(id);
        String text = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        Path file = FMLPaths.CONFIGDIR.get().resolve("phoenix_continuum").resolve("tuner_export")
                .resolve(id.getNamespace()).resolve(id.getPath() + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
            minecraft.keyboardHandler.setClipboard(text);
            status = "Saved and copied: " + id.getPath() + ".json";
        } catch (IOException e) {
            status = "Could not write the file: " + e.getMessage();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
