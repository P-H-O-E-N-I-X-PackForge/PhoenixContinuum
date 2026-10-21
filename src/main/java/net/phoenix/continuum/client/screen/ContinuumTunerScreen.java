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

/**
 * Live tuning of how a world looks: edit a body's visual parameters and see the map change at once. It only changes
 * this client; nothing is sent to the server. Export writes the full definition as a file (and to the clipboard) to
 * drop into the pack folder. Open with {@code /continuum tune [body]}. Operators (or the single-player owner) only.
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuumTunerScreen extends Screen {

    private enum Kind {
        NUMBER,
        COLOR,
        BOOL
    }

    private record Field(String path, String label, Kind kind, double min, double max, double step, Object fallback) {}

    private static final int ROW = 15;
    private static final int LIST_W = 330;

    private final Screen parent;
    private final List<ContinuumBody> bodies = new ArrayList<>();
    private int bodyIndex;
    private final List<Field> fields = new ArrayList<>();
    private int scroll;
    private EditBox editor;
    private Field editing;
    private String status = "";
    private long previewDueAt = -1;
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
        return bodies.get(Math.floorMod(bodyIndex, bodies.size()));
    }

    private void rebuildFields() {
        fields.clear();
        scroll = 0;
        editing = null;
        if (bodies.isEmpty()) return;
        ContinuumBody body = body();
        JsonObject json = ContinuumTuner.merged(body.id());

        if (body.type() == ContinuumBody.Type.BLACK_HOLE) {
            fields.add(new Field("hole_tilt", "Disk tilt (map view)  0 edge-on, 1 face-on", Kind.NUMBER, 0, 1, 0.05, 0.075));
            fields.add(new Field("sky_hole_inclination", "Disk inclination (sky), degrees", Kind.NUMBER, 0, 90, 1, 45.0));
            fields.add(new Field("sky_hole_lean", "Disk lean (sky), degrees", Kind.NUMBER, -90, 90, 1, 15.0));
            return;
        }

        fields.add(new Field("radius", "Radius", Kind.NUMBER, 0.1, 12, 0.1, 1.0));
        fields.add(new Field("gas_giant", "Gas giant", Kind.BOOL, 0, 1, 1, false));
        fields.add(new Field("axial_tilt", "Axial tilt", Kind.NUMBER, -90, 90, 1, 0.0));
        fields.add(new Field("spin_deg_per_sec", "Spin", Kind.NUMBER, 0, 60, 0.5, 4.0));
        fields.add(new Field("cloud_spin_deg_per_sec", "Cloud spin", Kind.NUMBER, 0, 60, 0.5, 6.0));
        fields.add(new Field("surface.seed", "Seed", Kind.NUMBER, 0, 9999, 1, 1.0));
        fields.add(new Field("surface.scale", "Terrain scale", Kind.NUMBER, 0.5, 10, 0.1, 2.5));
        fields.add(new Field("surface.bump", "Bumpiness", Kind.NUMBER, 0, 2, 0.05, 0.7));
        fields.add(new Field("surface.ocean_level", "Ocean level", Kind.NUMBER, 0, 1, 0.02, 0.0));
        fields.add(new Field("surface.polar_ice", "Polar ice", Kind.NUMBER, 0, 1, 0.02, 0.0));
        fields.add(new Field("surface.cloud_cover", "Cloud cover", Kind.NUMBER, 0, 1, 0.02, 0.0));
        fields.add(new Field("surface.low", "Low ground colour", Kind.COLOR, 0, 0, 0, "#444444"));
        fields.add(new Field("surface.mid", "Mid ground colour", Kind.COLOR, 0, 0, 0, "#888888"));
        fields.add(new Field("surface.high", "High ground colour", Kind.COLOR, 0, 0, 0, "#cccccc"));
        fields.add(new Field("surface.ocean_deep", "Deep ocean colour", Kind.COLOR, 0, 0, 0, "#03123a"));
        fields.add(new Field("surface.ocean_shallow", "Shallow ocean colour", Kind.COLOR, 0, 0, 0, "#1a6f8f"));
        fields.add(new Field("atmosphere.color", "Atmosphere colour", Kind.COLOR, 0, 0, 0, "#6aa8ff"));
        fields.add(new Field("atmosphere.density", "Atmosphere density", Kind.NUMBER, 0, 1.5, 0.02, 0.0));
        if (json.has("rings")) {
            fields.add(new Field("rings.inner", "Ring inner radius", Kind.NUMBER, 1, 6, 0.05, 1.4));
            fields.add(new Field("rings.outer", "Ring outer radius", Kind.NUMBER, 1, 8, 0.05, 2.4));
            fields.add(new Field("rings.tilt", "Ring tilt", Kind.NUMBER, -90, 90, 1, 0.0));
            fields.add(new Field("rings.opacity", "Ring opacity", Kind.NUMBER, 0, 1, 0.05, 0.85));
            fields.add(new Field("rings.color", "Ring colour", Kind.COLOR, 0, 0, 0, "#d8c8a0"));
            fields.add(new Field("rings.color2", "Ring colour 2", Kind.COLOR, 0, 0, 0, "#8a7a60"));
        }
    }

    // ------------------------------------------------------------------ values

    private JsonElement read(Field field) {
        JsonElement cursor = ContinuumTuner.merged(body().id());
        for (String part : field.path().split("[.]")) {
            if (cursor == null || !cursor.isJsonObject()) return null;
            cursor = cursor.getAsJsonObject().get(part);
        }
        return cursor;
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
        ContinuumTuner.set(body().id(), field.path(), value);
        previewDueAt = System.currentTimeMillis() + 120;
    }

    private void setNumber(Field field, double value) {
        double clamped = Math.max(field.min(), Math.min(field.max(), value));
        clamped = Math.round(clamped / Math.min(field.step(), 0.001) ) * Math.min(field.step(), 0.001);
        set(field, new JsonPrimitive(field.step() >= 1 ? (Number) (long) Math.round(clamped) : (Number) clamped));
    }

    // ------------------------------------------------------------------ layout

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
            g.drawString(font, font.plainSubstrByWidth(field.label(), 150), x + 4, y + 4, 0xFFAEBBCF, false);

            int vx = x + 160;
            switch (field.kind()) {
                case NUMBER -> {
                    double value = number(field);
                    String text = field.step() >= 1 ? String.valueOf(Math.round(value)) :
                            String.format(Locale.ROOT, "%.3f", value);
                    button(g, mx, my, vx, y + 1, 12, "-", field, "dec");
                    g.drawCenteredString(font, text, vx + 12 + 36, y + 4, 0xFFFFFFFF);
                    hit(g, mx, my, vx + 12, y + 1, 72, 13, field, "edit");
                    button(g, mx, my, vx + 84, y + 1, 12, "+", field, "inc");
                    int barX = vx + 104;
                    int barW = 110;
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
                }
                case BOOL -> {
                    boolean on = bool(field);
                    g.fill(vx, y + 2, vx + 28, y + 13, on ? 0xFF2F8F5F : 0xFF3A4452);
                    g.drawCenteredString(font, on ? "on" : "off", vx + 14, y + 4, 0xFFFFFFFF);
                    hit(g, mx, my, vx, y + 1, 28, 13, field, "toggle");
                }
            }
        }

        // preview of the surface, and the actions
        int px = x + LIST_W + 12;
        g.drawString(font, "Preview", px, top() - 12, 0xFFAEBBCF, false);
        if (body.type() != ContinuumBody.Type.BLACK_HOLE) {
            g.blit(SurfaceMap.texture(body), px, top(), 0, 0, 200, 100, 200, 100);
        } else {
            g.fill(px, top(), px + 200, top() + 100, 0xFF05070C);
            g.drawCenteredString(font, "see the Continuum map", px + 100, top() + 46, 0xFF7F8A99);
        }
        action(g, mx, my, px, top() + 112, 200, "Export definition", "export");
        action(g, mx, my, px, top() + 132, 200, "Reset this body", "reset");
        action(g, mx, my, px, top() + 152, 200, "Close", "close");
        g.drawString(font, "Only this client changes.", px, top() + 176, 0xFF7F8A99, false);
        g.drawString(font, "Export, then put the file in", px, top() + 188, 0xFF7F8A99, false);
        g.drawString(font, "config/phoenix_continuum/pack/", px, top() + 198, 0xFF7F8A99, false);
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, 200), px, top() + 214, 0xFF66DDFF, false);

        super.render(g, mx, my, partialTick);
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
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
                case "edit" -> beginEdit(field, x, y);
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        // dragging along a bar scrubs the value
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
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 2;
        return true;
    }

    private void beginEdit(Field field, int x, int y) {
        editing = field;
        editor.setX(x);
        editor.setY(y);
        editor.setValue(field.kind() == Kind.COLOR ? color(field) : String.valueOf(number(field)));
        editor.setVisible(true);
        setFocused(editor);
        editor.setFocused(true);
    }

    private void commitEdit() {
        if (editing == null || !editor.isVisible()) return;
        String text = editor.getValue().trim();
        try {
            if (editing.kind() == Kind.COLOR) {
                String clean = text.startsWith("#") ? text.substring(1) : text;
                if (clean.length() == 6) {
                    Integer.parseInt(clean, 16);
                    set(editing, new JsonPrimitive("#" + clean.toLowerCase(Locale.ROOT)));
                }
            } else {
                setNumber(editing, Double.parseDouble(text));
            }
        } catch (NumberFormatException ignored) {
            // leave the value as it was
        }
        editor.setVisible(false);
        editing = null;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
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
