package net.phoenix.continuum.client.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.phoenix.continuum.client.pdim.PdimSkyBodies;
import net.phoenix.continuum.client.pdim.PdimSkyEffects;
import net.phoenix.continuum.client.pdim.PdimSkyModel;
import net.phoenix.continuum.client.pdim.PdimSkyView;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.client.render.SceneTarget;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.ContinuumSystem;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class ContinuumSkyScreen extends Screen {

    private static final int PANEL_W = 236;
    private static final float TICKS_PER_DAY = 24000.0f;
    private static final float[] SPEEDS = { 0.0f, 20.0f, 200.0f, 2000.0f };
    private static final String[] SPEED_LABELS = { "paused", "1x", "10x", "100x" };

    private final Screen parent;
    private final @org.jetbrains.annotations.Nullable ContinuumSystem systemOverride;
    private final List<ContinuumBody> bodies = new ArrayList<>();
    private int index;
    private final SceneTarget target = new SceneTarget();
    private final List<Object[]> hits = new ArrayList<>();

    private float yaw;
    private float pitch = 20.0f;
    private float fov = 70.0f;
    private long day;
    private float timeOfDay = 6000.0f;
    private int speed = 1;
    private boolean orbit;
    private boolean fixed;
    private boolean showAll = true;
    private boolean panelOpen = true;
    private boolean draggingSky;
    private boolean draggingTime;
    private long lastMs = Util.getMillis();
    private String message = "";

    public ContinuumSkyScreen(Screen parent, ContinuumBody start) {
        this(parent, start, null);
    }

    public ContinuumSkyScreen(Screen parent, ContinuumBody start,
                              @org.jetbrains.annotations.Nullable ContinuumSystem systemOverride) {
        super(Component.literal("Sky view"));
        this.parent = parent;
        this.systemOverride = systemOverride;
        for (ContinuumBody b : ContinuumData.bodiesOf(start.system())) {
            if (b.isCentral() || b.transientInfo() != null) continue;
            bodies.add(b);
        }
        if (bodies.isEmpty()) bodies.add(start);
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i).id().equals(start.id())) index = i;
        }
        var level = Minecraft.getInstance().level;
        if (level != null) day = level.getGameTime() / 24000L;
    }

    private @org.jetbrains.annotations.Nullable ContinuumSystem systemOf(ContinuumBody body) {
        return systemOverride != null && systemOverride.id().equals(body.system()) ? systemOverride :
                ContinuumData.system(body.system());
    }

    private ContinuumBody body() {
        ContinuumBody listed = bodies.get(Math.floorMod(index, bodies.size()));
        ContinuumBody current = ContinuumData.body(listed.id());
        return current != null ? current : listed;
    }

    private double gameTicks() {
        return day * (double) TICKS_PER_DAY + timeOfDay;
    }

    private PdimSkyView sky() {
        double ticks = gameTicks();
        return new PdimSkyView(ticks, ticks, -1.0f, 1.0f, orbit, fixed, showAll);
    }

    private int panelX() {
        return panelOpen ? width - PANEL_W - 8 : width + 8;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min((now - lastMs) / 1000.0f, 0.1f);
        lastMs = now;
        if (SPEEDS[speed] > 0.0f && !draggingTime) {
            timeOfDay += dt * SPEEDS[speed];
            while (timeOfDay >= TICKS_PER_DAY) {
                timeOfDay -= TICKS_PER_DAY;
                day++;
            }
        }

        hits.clear();
        ContinuumBody body = body();
        ContinuumSystem system = systemOf(body);
        PdimSkyView view0 = sky();

        PdimSkyModel.Sample sample = null;
        boolean drawn = false;
        if (ContinuumShaders.ready() && system != null) {
            try {
                RenderTarget rt = target.ensure();
                if (rt != null) {
                    SceneRenderer.begin(rt, yaw);
                    Matrix4f projection = SceneRenderer.projection(rt.width, rt.height, fov);
                    Matrix4f view = new Matrix4f().rotateX((float) Math.toRadians(-pitch))
                            .rotateY((float) Math.toRadians(yaw));
                    float time = (float) (System.currentTimeMillis() % 10000000L) / 1000.0f;
                    sample = PdimSkyEffects.drawScene(view, projection, view0, body, system, time);
                    drawn = true;
                }
            } catch (RuntimeException e) {
                message = "The sky could not be drawn: " + e.getMessage();
            } finally {
                SceneRenderer.end();
            }
        }

        if (drawn) {
            target.blit(g, width, height);
        } else {
            g.fill(0, 0, width, height, 0xFF05060F);
            g.drawCenteredString(font, ContinuumShaders.ready() ? "The sky is unavailable" : "Shaders not loaded",
                    width / 2, height / 2, 0xFFFF6B6B);
        }

        if (panelOpen) {
            drawPanel(g, mx, my, body, system, view0, sample);
        } else {
            drawFoldedPanel(g, mx, my, body, view0, sample);
        }
        super.render(g, mx, my, partialTick);
    }

    private void drawPanel(GuiGraphics g, int mx, int my, ContinuumBody body, ContinuumSystem system, PdimSkyView view0,
                           PdimSkyModel.Sample sample) {
        int x = panelX();
        int y = 8;
        int bottom = height - 8;
        g.fill(x - 4, y - 4, x + PANEL_W + 4, bottom + 4, 0xC00A0E14);
        g.renderOutline(x - 4, y - 4, PANEL_W + 8, bottom - y + 8, 0xFF3A4452);

        g.drawString(font, "Sky view", x, y, 0xFFFFFFFF, false);
        button(g, mx, my, x + PANEL_W - 54, y - 2, 54, "Hide  H", "panel");
        y += 12;
        String title = "<  " + font.plainSubstrByWidth(body.name(), PANEL_W - 40) + "  >";
        g.drawString(font, title, x, y, 0xFFC8D4E8, false);
        hit(x, y - 2, font.width("<  ") + 6, 12, "prev", null);
        hit(x + PANEL_W - 14, y - 2, 18, 12, "next", null);
        y += 14;
        g.drawString(font, font.plainSubstrByWidth(system == null ? "" : system.name(), PANEL_W), x, y, 0xFF7F8A99, false);
        y += 14;

        g.drawString(font, String.format(Locale.ROOT, "Time of day  %02d:%02d", (int) (timeOfDay / 1000.0f * 1.0f + 6) % 24,
                (int) ((timeOfDay % 1000.0f) / 1000.0f * 60.0f)), x, y, 0xFFAEBBCF, false);
        y += 12;
        int barW = PANEL_W;
        g.fill(x, y + 3, x + barW, y + 7, 0xFF2A3340);
        g.fill(x, y + 3, x + (int) (barW * timeOfDay / TICKS_PER_DAY), y + 7, 0xFF4F8FCF);
        int knob = x + (int) (barW * timeOfDay / TICKS_PER_DAY);
        g.fill(knob - 2, y, knob + 2, y + 10, 0xFFFFFFFF);
        hit(x, y - 2, barW, 14, "timebar", null);
        y += 16;

        button(g, mx, my, x, y, 76, "Speed: " + SPEED_LABELS[speed], "speed");
        button(g, mx, my, x + 82, y, 12, "-", "daydec");
        g.drawCenteredString(font, "Day " + day, x + 82 + 12 + 34, y + 3, 0xFFFFFFFF);
        button(g, mx, my, x + 82 + 12 + 68, y, 12, "+", "dayinc");
        y += 18;

        button(g, mx, my, x, y, 114, orbit ? "From orbit" : "From the surface", "orbit");
        button(g, mx, my, x + 120, y, 116, fixed ? "Fixed sky: on" : "Fixed sky: off", "fixed");
        y += 16;
        button(g, mx, my, x, y, 114, showAll ? "Undiscovered: shown" : "Undiscovered: hidden", "showall");
        var eclipse = eclipseTarget(body);
        button(g, mx, my, x + 120, y, 116, eclipse == null ? "No eclipse soon" : "Next eclipse", "eclipse");
        y += 18;

        g.fill(x, y, x + PANEL_W, y + 1, 0xFF404040);
        y += 5;
        g.drawString(font, "In the sky", x, y, 0xFFFFD080, false);
        y += 12;
        if (sample != null) {
            float sunElevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, sample.sunDir().y))));
            float sunAzimuth = (float) Math.toDegrees(Math.atan2(sample.sunDir().x, -sample.sunDir().z));
            if (sunAzimuth < 0.0f) sunAzimuth += 360.0f;
            if (sample.hasSun()) {
                String sun = (system != null ? system.name() : "Star") + "  " + angles(sunElevation, sunAzimuth, 0.0f);
                g.drawString(font, font.plainSubstrByWidth(sun, PANEL_W), x, y, system == null ? 0xFFFFFFFF :
                        0xFF000000 | system.starColor(), false);
                hit(x, y - 1, PANEL_W, 10, "look", new float[] { sunAzimuth, sunElevation });
                y += 11;
            } else {
                g.drawString(font, "No star in this sky", x, y, 0xFF7F8A99, false);
                y += 11;
            }
            for (var item : PdimSkyBodies.describe(view0, body, sample)) {
                String line = (item.moon() ? "moon " : "") + item.body().name() + "  " +
                        angles(item.elevationDeg(), item.azimuthDeg(), item.apparentDeg());
                g.drawString(font, font.plainSubstrByWidth(line, PANEL_W), x, y,
                        0xFF000000 | item.body().params().mid(), false);
                hit(x, y - 1, PANEL_W, 10, "look", new float[] { item.azimuthDeg(), item.elevationDeg() });
                y += 11;
                if (y > bottom - 190) break;
            }
            if (sample.eclipse() > 0.01f) {
                g.drawString(font, String.format(Locale.ROOT, "Eclipse: %d%% of the star covered",
                        Math.round(sample.eclipse() * 100.0f)), x, y, 0xFFFFB040, false);
                y += 11;
            }
        }

        drawDiagram(g, x, Math.max(y + 8, bottom - 170), PANEL_W, 160, body, system);

        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, PANEL_W), x, bottom - 8, 0xFFFF6B6B, false);
        }
        button(g, mx, my, x, bottom - 16, 60, "Close", "close");
        g.drawString(font, "drag: look   scroll: zoom", x + 66, bottom - 12, 0xFF7F8A99, false);
    }

    private void drawFoldedPanel(GuiGraphics g, int mx, int my, ContinuumBody body, PdimSkyView view0,
                                 PdimSkyModel.Sample sample) {
        String text = body.name() + "   Day " + day;
        int w = font.width(text) + 70;
        int x = width - w - 8;
        g.fill(x, 8, x + w, 24, 0xC00A0E14);
        g.renderOutline(x, 8, w, 16, 0xFF3A4452);
        g.drawString(font, text, x + 6, 12, 0xFFC8D4E8, false);
        button(g, mx, my, x + w - 58, 9, 54, "Info  H", "panel");
        if (!message.isEmpty()) g.drawString(font, font.plainSubstrByWidth(message, width - 20), 10, height - 14, 0xFFFF6B6B, false);
    }

    private static String angles(float elevation, float azimuth, float apparent) {
        String base = String.format(Locale.ROOT, "%+.0f°  az %.0f°", elevation, azimuth);
        return apparent > 0.0f ? base + String.format(Locale.ROOT, "  %.1f° wide", apparent) : base;
    }

    private void drawDiagram(GuiGraphics g, int x, int y, int w, int h, ContinuumBody viewer, ContinuumSystem system) {
        g.fill(x, y, x + w, y + h, 0xFF05070C);
        g.renderOutline(x, y, w, h, 0xFF3A4452);
        if (system == null) return;

        int cx = x + w / 2;
        int cy = y + h / 2;
        double radius = Math.min(w, h) / 2.0 - 8.0;
        double maxAu = 0.1;
        List<ContinuumBody> planets = new ArrayList<>();
        for (ContinuumBody b : ContinuumData.bodiesOf(system.id())) {
            if (b.isMoon() || b.isCentral() || b.transientInfo() != null) continue;
            planets.add(b);
            maxAu = Math.max(maxAu, b.orbitAu());
        }
        ContinuumBody home = viewer.isMoon() && viewer.parent() != null ? ContinuumData.body(viewer.parent()) : viewer;
        double days = gameTicks() / TICKS_PER_DAY;

        for (ContinuumBody b : planets) {
            double r = radius * Math.sqrt(b.orbitAu() / maxAu);
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2.0 * i / 48.0;
                g.fill(cx + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r),
                        cx + (int) Math.round(Math.cos(a) * r) + 1, cy + (int) Math.round(Math.sin(a) * r) + 1,
                        0x44FFFFFF);
            }
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF000000 | system.starColor());
        for (ContinuumBody b : planets) {
            double[] p = PdimSkyBodies.orbitPosition(b, days);
            double angle = Math.atan2(p[1], p[0]);
            double r = radius * Math.sqrt(b.orbitAu() / maxAu);
            int px = cx + (int) Math.round(Math.cos(angle) * r);
            int py = cy + (int) Math.round(Math.sin(angle) * r);
            boolean viewing = home != null && b.id().equals(home.id());
            int size = viewing ? 3 : 2;
            g.fill(px - size, py - size, px + size + 1, py + size + 1, 0xFF000000 | b.params().mid());
            if (viewing) g.renderOutline(px - 4, py - 4, 9, 9, 0xFFFFFFFF);
        }
    }

    private @org.jetbrains.annotations.Nullable ObservatoryReadings.Eclipse eclipseTarget(ContinuumBody body) {
        ContinuumBody world = body.isMoon() && body.parent() != null ? ContinuumData.body(body.parent()) : body;
        return world == null ? null : ObservatoryReadings.nextEclipse(world, gameTicks() / TICKS_PER_DAY, 400.0);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, String label, String act) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, hover ? 0xFF35507A : 0xFF263040);
        g.drawCenteredString(font, label, x + w / 2, y + 3, 0xFFFFFFFF);
        hits.add(new Object[] { act, null, x, y, w, 13 });
    }

    private void hit(int x, int y, int w, int h, String act, Object arg) {
        hits.add(new Object[] { act, arg, x, y, w, h });
    }

    private void setTimeFromMouse(double mx) {
        int x = panelX();
        timeOfDay = (float) Math.max(0.0, Math.min(TICKS_PER_DAY - 1.0, (mx - x) / PANEL_W * TICKS_PER_DAY));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            int x = (int) h[2];
            int y = (int) h[3];
            if (mx < x || mx >= x + (int) h[4] || my < y || my >= y + (int) h[5]) continue;
            switch ((String) h[0]) {
                case "prev" -> index--;
                case "next" -> index++;
                case "timebar" -> {
                    draggingTime = true;
                    setTimeFromMouse(mx);
                }
                case "speed" -> speed = (speed + 1) % SPEEDS.length;
                case "daydec" -> day = Math.max(0L, day - (hasShiftDown() ? 10 : 1));
                case "dayinc" -> day += hasShiftDown() ? 10 : 1;
                case "orbit" -> orbit = !orbit;
                case "fixed" -> fixed = !fixed;
                case "showall" -> showAll = !showAll;
                case "eclipse" -> jumpToEclipse();
                case "look" -> {
                    float[] at = (float[]) h[1];
                    yaw = at[0];
                    pitch = Math.max(-89.0f, Math.min(89.0f, at[1]));
                }
                case "panel" -> panelOpen = !panelOpen;
                case "close" -> onClose();
                default -> {}
            }
            message = "";
            return true;
        }
        if (mx < panelX() - 4) {
            draggingSky = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void jumpToEclipse() {
        ContinuumBody body = body();
        var eclipse = eclipseTarget(body);
        if (eclipse == null) {
            message = "No eclipse in the next 400 days.";
            return;
        }
        double target = gameTicks() / TICKS_PER_DAY + eclipse.inDays();
        day = (long) Math.floor(target);
        timeOfDay = (float) ((target - Math.floor(target)) * TICKS_PER_DAY);
        speed = 0;

        ContinuumSystem system = systemOf(body);
        if (system != null) {
            var sample = PdimSkyModel.sample(sky(), body, system);
            yaw = (float) Math.toDegrees(Math.atan2(sample.sunDir().x, -sample.sunDir().z));
            pitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, sample.sunDir().y))));
        }
        message = (eclipse.total() ? "Total eclipse by " : "Eclipse by ") + eclipse.moon();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingTime) {
            setTimeFromMouse(mx);
            return true;
        }
        if (draggingSky) {
            float scale = fov / 70.0f * 0.3f;
            yaw += (float) dx * scale;
            pitch = Math.max(-89.0f, Math.min(89.0f, pitch + (float) dy * scale));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingSky = false;
        draggingTime = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        fov = Math.max(20.0f, Math.min(110.0f, fov - (float) scrollY * 4.0f));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_H) {
            panelOpen = !panelOpen;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        target.close();
        super.removed();
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
