package net.phoenix.continuum.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenix.continuum.client.ContinuumSounds;
import net.phoenix.continuum.client.pdim.PdimClientState;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.network.C2SPdimPacket;
import net.phoenix.continuum.pdim.PdimCosts;
import net.phoenix.continuum.pdim.PdimData;
import net.phoenix.continuum.pdim.PdimDimensions;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makes or edits a personal dimension. Every setting is here whether the dimension exists yet or not: gravity (a preset
 * or any value), how fast the sky clock runs, a fixed time of day, and whether the sky is the view from the surface of
 * the world or from orbit. Before the dimension exists the choices are held on the screen and sent with "Create and
 * enter"; afterwards each click is applied straight away.
 */
public class ContinuumPdimScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private static final int W = 320;
    private static final int H = 300;

    private record Preset(String label, float gravity) {}

    private final Screen parent;
    private final ContinuumBody body;
    private final List<Preset> presets = new ArrayList<>();
    private final List<Object[]> rects = new ArrayList<>();
    private float scale = 1.0f;

    // what the player is choosing, for a dimension that does not exist yet
    private float pendGravity;
    private float pendSpeed = 1.0f;
    private float pendLocked = -1.0f;
    private boolean pendOrbit;

    public ContinuumPdimScreen(Screen parent, ContinuumBody body) {
        super(Component.literal("Personal dimension"));
        this.parent = parent;
        this.body = body;

        float natural = PdimDimensions.naturalGravity(body);
        pendGravity = natural;
        presets.add(new Preset("Natural", natural));
        presets.add(new Preset("Moon", 0.16f));
        presets.add(new Preset("Low", 0.5f));
        presets.add(new Preset("Standard", 1.0f));
        presets.add(new Preset("Heavy", 1.5f));
        presets.add(new Preset("Crushing", 2.5f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PdimData.Entry existing() {
        return PdimClientState.entry(body.id());
    }

    private float gravity() {
        var e = existing();
        return e != null ? e.gravity() : pendGravity;
    }

    private float speed() {
        var e = existing();
        return e != null ? e.daySpeed() : pendSpeed;
    }

    private float locked() {
        var e = existing();
        return e != null ? e.lockedTime() : pendLocked;
    }

    private boolean orbit() {
        var e = existing();
        return e != null ? e.orbitView() : pendOrbit;
    }

    private static String clock(float tick) {
        int t = Math.round(tick);
        return switch (t) {
            case 0 -> "dawn";
            case 6000 -> "noon";
            case 12000 -> "dusk";
            case 18000 -> "midnight";
            default -> "tick " + t;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        g.fill(0, 0, width, height, 0xCC000000);

        scale = Math.max(0.4f, Math.min(1.0f, Math.min((width - 40.0f) / W, (height - 40.0f) / H)));
        int mx = Math.round(mouseX / scale);
        int my = Math.round(mouseY / scale);
        int vw = Math.round(width / scale);
        int vh = Math.round(height / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);

        int x = (vw - W) / 2;
        int y = (vh - H) / 2;
        g.fill(x, y, x + W, y + H, MapUi.PANEL_SOLID);
        g.renderOutline(x, y, W, H, MapUi.PANEL_LINE);
        rects.clear();

        boolean exists = existing() != null;
        g.drawString(font, "Personal dimension  -  " + body.name(), x + 10, y + 9, MapUi.TITLE);
        int ty = y + 24;
        ty = wrapped(g, "An empty void with an obsidian platform, under the sky of " + body.name() +
                ". Falling out of the bottom puts you back at the top." +
                (exists ? " Changes apply at once; the sky only changes what you see." : ""), x + 10, ty, W - 20,
                MapUi.DIM) + 4;

        var cost = PdimClientState.cost(body.id());
        boolean charged = cost != null && !cost.free() && (!exists || PdimClientState.chargesEveryVisit());
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        int have = cost == null ? 0 : PdimCosts.count(Minecraft.getInstance().player, cost);
        boolean canPay = !charged || creative || have >= cost.count();

        // gravity: any value, plus presets
        ty = adjustRow(g, x, ty, String.format(Locale.ROOT, "Gravity  x%.2f", gravity()), "g", mx, my);
        int bw = (W - 20 - 10) / 3;
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            int bx = x + 10 + (i % 3) * (bw + 5);
            int by = ty + (i / 3) * 21;
            button(g, bx, by, bw, p.label() + String.format(Locale.ROOT, " x%.2f", p.gravity()), "preset" + i, true,
                    Math.abs(gravity() - p.gravity()) < 0.005f, mx, my);
        }
        ty += 44;

        // day and night
        float lockedNow = locked();
        ty = adjustRow(g, x, ty, lockedNow >= 0 ? "Day  locked at " + clock(lockedNow) :
                String.format(Locale.ROOT, "Day speed  x%.2f", speed()), "s", mx, my);
        int quarter = (W - 20 - 15) / 4;
        String[] names = {"dawn", "noon", "dusk", "midnight"};
        float[] ticks = {0.0f, 6000.0f, 12000.0f, 18000.0f};
        for (int i = 0; i < 4; i++) {
            button(g, x + 10 + i * (quarter + 5), ty, quarter, names[i], "lock" + i, true,
                    lockedNow >= 0 && Math.round(lockedNow) == (int) ticks[i], mx, my);
        }
        ty += 21;
        button(g, x + 10, ty, W - 20, lockedNow >= 0 ? "Let the sky clock run again" : "Sky clock running  (lock a time above)",
                "unlock", lockedNow >= 0, false, mx, my);
        ty += 23;

        // where the sky is seen from
        button(g, x + 10, ty, W - 20, orbit() ? "Sky: seen from orbit  (click for the surface)" :
                "Sky: as if you stand on it  (click for orbit)", "sky", true, false, mx, my);

        costLine(g, x + 10, y + H - 80, charged, cost, have, canPay);
        button(g, x + 10, y + H - 66, W - 20, exists ? "Enter" : "Create and enter", exists ? "enter" : "create", canPay,
                false, mx, my);

        boolean inside = PdimClientState.inside();
        int half = (W - 25) / 2;
        if (inside) {
            button(g, x + 10, y + H - 44, half, "Leave this dimension", "leave", true, false, mx, my);
            button(g, x + 15 + half, y + H - 44, half, "Close", "close", true, false, mx, my);
        } else {
            button(g, x + 10, y + H - 44, W - 20, "Close", "close", true, false, mx, my);
        }
        if (exists && !inside) {
            button(g, x + 10, y + H - 22, W - 20, "Forget this dimension", "forget", true, false, mx, my);
        }

        g.pose().popPose();
    }

    private int adjustRow(GuiGraphics g, int x, int y, String label, String id, int mx, int my) {
        g.drawString(font, label, x + 10, y + 5, MapUi.TEXT);
        button(g, x + W - 10 - 22 * 2 - 4, y, 22, "-", id + "-", true, false, mx, my);
        button(g, x + W - 10 - 22, y, 22, "+", id + "+", true, false, mx, my);
        return y + 22;
    }

    private void costLine(GuiGraphics g, int x, int y, boolean charged, PdimCosts.Cost cost, int have, boolean canPay) {
        if (!charged) {
            g.drawString(font, "This trip is free.", x, y, MapUi.GOOD);
            return;
        }
        g.drawString(font, "Cost: " + cost.describe().getString() + "   (you have " + have + ")", x, y,
                canPay ? MapUi.TEXT : MapUi.BAD);
    }

    private int wrapped(GuiGraphics g, String text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(FormattedText.of(text), w)) {
            g.drawString(font, line, x, y, color);
            y += 10;
        }
        return y;
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, String id, boolean enabled, boolean selected,
                        int mx, int my) {
        boolean hover = enabled && MapUi.inside(mx, my, x, y, w, 18);
        g.fill(x, y, x + w, y + 18,
                !enabled ? MapUi.BUTTON_OFF : selected ? MapUi.BUTTON_ON : hover ? MapUi.BUTTON_HOVER : MapUi.BUTTON);
        g.renderOutline(x, y, w, 18, selected ? MapUi.FRAME : MapUi.PANEL_LINE);
        g.drawCenteredString(font, label, x + w / 2, y + 5, enabled ? MapUi.TITLE : MapUi.DIM);
        if (enabled) rects.add(new Object[] { id, x, y, w });
    }

    /** Applies a change: held on the screen until the dimension exists, sent to the server once it does. */
    private void apply(float gravity, float speed, float locked, boolean orbit) {
        gravity = PdimDimensions.clampGravity(Math.round(gravity * 100f) / 100f);
        speed = PdimDimensions.clampDaySpeed(speed);
        if (existing() == null) {
            pendGravity = gravity;
            pendSpeed = speed;
            pendLocked = locked;
            pendOrbit = orbit;
        } else {
            PacketDistributor.sendToServer(
                    new C2SPdimPacket(C2SPdimPacket.Action.ADJUST, body.id(), gravity, speed, locked, orbit));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX / scale;
        double my = mouseY / scale;
        if (button == 0) {
            for (Object[] r : rects) {
                if (!MapUi.inside(mx, my, (int) r[1], (int) r[2], (int) r[3], 18)) continue;
                ContinuumSounds.click();
                String id = (String) r[0];
                float gravity = gravity();
                float speed = speed();
                float locked = locked();
                boolean orbit = orbit();
                float step = hasShiftDown() ? 0.5f : hasControlDown() ? 0.01f : 0.1f;
                float factor = hasShiftDown() ? 2.0f : 1.25f;
                switch (id) {
                    case "g-" -> apply(gravity - step, speed, locked, orbit);
                    case "g+" -> apply(gravity + step, speed, locked, orbit);
                    case "s-" -> apply(gravity, speed / factor, -1.0f, orbit);
                    case "s+" -> apply(gravity, speed * factor, -1.0f, orbit);
                    case "lock0" -> apply(gravity, speed, 0.0f, orbit);
                    case "lock1" -> apply(gravity, speed, 6000.0f, orbit);
                    case "lock2" -> apply(gravity, speed, 12000.0f, orbit);
                    case "lock3" -> apply(gravity, speed, 18000.0f, orbit);
                    case "unlock" -> apply(gravity, speed, -1.0f, orbit);
                    case "sky" -> apply(gravity, speed, locked, !orbit);
                    case "create", "enter" -> {
                        PacketDistributor.sendToServer(new C2SPdimPacket(C2SPdimPacket.Action.ENTER, body.id(), gravity,
                                speed, locked, orbit));
                        Minecraft.getInstance().setScreen(null);
                    }
                    case "leave" -> {
                        PacketDistributor.sendToServer(
                                new C2SPdimPacket(C2SPdimPacket.Action.LEAVE, body.id(), 1.0f, false));
                        Minecraft.getInstance().setScreen(null);
                    }
                    case "forget" -> PacketDistributor.sendToServer(
                            new C2SPdimPacket(C2SPdimPacket.Action.DELETE, body.id(), 1.0f, false));
                    case "close" -> onClose();
                    default -> {
                        if (id.startsWith("preset")) {
                            apply(presets.get(Integer.parseInt(id.substring(6))).gravity(), speed, locked, orbit);
                        } else {
                            onClose();
                        }
                    }
                }
                return true;
            }
        } else if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        Minecraft.getInstance().setScreen(parent);
    }
}
