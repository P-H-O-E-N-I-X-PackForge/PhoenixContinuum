package net.phoenix.continuum.client.screen;

import net.phoenix.continuum.client.ContinuumText;
import net.phoenix.continuum.common.ContinuumLang;
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

public class ContinuumPdimScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    private static final int W = 320;
    private static final int H = 344;

    private record Preset(String label, float gravity) {}

    private final Screen parent;
    private final ContinuumBody body;
    private final List<Preset> presets = new ArrayList<>();
    private final List<Object[]> rects = new ArrayList<>();
    private float scale = 1.0f;

    private float pendGravity;
    private float pendSpeed = 1.0f;
    private float pendLocked = -1.0f;
    private boolean pendOrbit;
    private boolean pendFixed;

    public ContinuumPdimScreen(Screen parent, ContinuumBody body) {
        super(ContinuumLang.tr("phoenix_continuum.continuum_pdim_screen.personal_dimension"));
        this.parent = parent;
        this.body = body;

        float natural = PdimDimensions.naturalGravity(body);
        pendGravity = natural;
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.natural"), natural));
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.moon"), 0.16f));
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.low"), 0.5f));
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.standard"), 1.0f));
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.heavy"), 1.5f));
        presets.add(new Preset(ContinuumText.str("phoenix_continuum.continuum_pdim_screen.crushing"), 2.5f));
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

    private boolean fixed() {
        var e = existing();
        return e != null ? e.fixedSky() : pendFixed;
    }

    private static String clock(float tick) {
        int t = Math.round(tick);
        return switch (t) {
            case 0 -> ContinuumText.str("phoenix_continuum.continuum_pdim_screen.clock_dawn");
            case 6000 -> ContinuumText.str("phoenix_continuum.continuum_pdim_screen.clock_noon");
            case 12000 -> ContinuumText.str("phoenix_continuum.continuum_pdim_screen.clock_dusk");
            case 18000 -> ContinuumText.str("phoenix_continuum.continuum_pdim_screen.clock_midnight");
            default -> ContinuumText.str("phoenix_continuum.continuum_pdim_screen.clock_tick", t);
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
        g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_pdim.personal_dimension", body.name()), x + 10, y + 9, MapUi.TITLE);
        int ty = y + 24;
        ty = wrapped(g, ContinuumText.str("phoenix_continuum.continuum_pdim_screen.empty_void", body.name()) +
                (exists ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.changes_apply_at_once_the_sky") : ""), x + 10, ty, W - 20,
                MapUi.DIM) + 4;

        var cost = PdimClientState.cost(body.id());
        boolean charged = cost != null && !cost.free() && (!exists || PdimClientState.chargesEveryVisit());
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        int have = cost == null ? 0 : PdimCosts.count(Minecraft.getInstance().player, cost);
        boolean canPay = !charged || creative || have >= cost.count();

        ty = adjustRow(g, x, ty, String.format(Locale.ROOT, ContinuumText.label("phoenix_continuum.continuum_pdim_screen.gravity_x_2f"), gravity()), "g", mx, my);
        int bw = (W - 20 - 10) / 3;
        for (int i = 0; i < presets.size(); i++) {
            Preset p = presets.get(i);
            int bx = x + 10 + (i % 3) * (bw + 5);
            int by = ty + (i / 3) * 21;
            button(g, bx, by, bw, p.label() + String.format(Locale.ROOT, " x%.2f", p.gravity()), "preset" + i, true,
                    Math.abs(gravity() - p.gravity()) < 0.005f, mx, my);
        }
        ty += 44;

        float lockedNow = locked();
        ty = adjustRow(g, x, ty, lockedNow >= 0 ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.day_locked_at", clock(lockedNow)) :
                String.format(Locale.ROOT, ContinuumText.label("phoenix_continuum.continuum_pdim_screen.day_speed_x_2f"), speed()), "s", mx, my);
        int quarter = (W - 20 - 15) / 4;
        String[] names = {"dawn", "noon", "dusk", "midnight"};
        float[] ticks = {0.0f, 6000.0f, 12000.0f, 18000.0f};
        for (int i = 0; i < 4; i++) {
            button(g, x + 10 + i * (quarter + 5), ty, quarter, names[i], "lock" + i, true,
                    lockedNow >= 0 && Math.round(lockedNow) == (int) ticks[i], mx, my);
        }
        ty += 21;
        button(g, x + 10, ty, W - 20, lockedNow >= 0 ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.let_the_sky_clock_run_again") : ContinuumText.str("phoenix_continuum.continuum_pdim_screen.sky_clock_running_lock_a_time"),
                "unlock", lockedNow >= 0, false, mx, my);
        ty += 23;

        button(g, x + 10, ty, W - 20, orbit() ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.sky_seen_from_orbit_click_for") :
                ContinuumText.str("phoenix_continuum.continuum_pdim_screen.sky_as_if_you_stand_on"), "sky", true, false, mx, my);

        ty += 22;
        button(g, x + 10, ty, W - 20, orbit() ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.orbiting_worlds_not_shown_the_sky") :
                fixed() ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.orbiting_worlds_always_in_the_sky") :
                        ContinuumText.str("phoenix_continuum.continuum_pdim_screen.orbiting_worlds_follow_the_day_click"), "fixed", !orbit(), fixed(), mx, my);

        costLine(g, x + 10, y + H - 80, charged, cost, have, canPay);
        button(g, x + 10, y + H - 66, W - 20, exists ? ContinuumText.str("phoenix_continuum.continuum_pdim_screen.enter") : ContinuumText.str("phoenix_continuum.continuum_pdim_screen.create_and_enter"), exists ? "enter" : "create", canPay,
                false, mx, my);

        boolean inside = PdimClientState.inside();
        int half = (W - 25) / 2;
        if (inside) {
            button(g, x + 10, y + H - 44, half, ContinuumText.str("phoenix_continuum.continuum_pdim_screen.leave_this_dimension"), "leave", true, false, mx, my);
            button(g, x + 15 + half, y + H - 44, half, ContinuumText.str("phoenix_continuum.continuum_pdim_screen.close"), "close", true, false, mx, my);
        } else {
            button(g, x + 10, y + H - 44, W - 20, ContinuumText.str("phoenix_continuum.continuum_pdim_screen.close"), "close", true, false, mx, my);
        }
        if (exists && !inside) {
            button(g, x + 10, y + H - 22, W - 20, ContinuumText.str("phoenix_continuum.continuum_pdim_screen.forget_this_dimension"), "forget", true, false, mx, my);
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
            g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_pdim.this_trip_is_free"), x, y, MapUi.GOOD);
            return;
        }
        g.drawString(font, ContinuumText.str("phoenix_continuum.continuum_pdim.cost_you_have", cost.describe().getString(), have), x, y,
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

    private void apply(float gravity, float speed, float locked, boolean orbit, boolean fixed) {
        gravity = PdimDimensions.clampGravity(Math.round(gravity * 100f) / 100f);
        speed = PdimDimensions.clampDaySpeed(speed);
        if (existing() == null) {
            pendGravity = gravity;
            pendSpeed = speed;
            pendLocked = locked;
            pendOrbit = orbit;
            pendFixed = fixed;
        } else {
            PacketDistributor.sendToServer(
                    new C2SPdimPacket(C2SPdimPacket.Action.ADJUST, body.id(), gravity, speed, locked, orbit, fixed));
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
                boolean fixed = fixed();
                float step = hasShiftDown() ? 0.5f : hasControlDown() ? 0.01f : 0.1f;
                float factor = hasShiftDown() ? 2.0f : 1.25f;
                switch (id) {
                    case "g-" -> apply(gravity - step, speed, locked, orbit, fixed);
                    case "g+" -> apply(gravity + step, speed, locked, orbit, fixed);
                    case "s-" -> apply(gravity, speed / factor, -1.0f, orbit, fixed);
                    case "s+" -> apply(gravity, speed * factor, -1.0f, orbit, fixed);
                    case "lock0" -> apply(gravity, speed, 0.0f, orbit, fixed);
                    case "lock1" -> apply(gravity, speed, 6000.0f, orbit, fixed);
                    case "lock2" -> apply(gravity, speed, 12000.0f, orbit, fixed);
                    case "lock3" -> apply(gravity, speed, 18000.0f, orbit, fixed);
                    case "unlock" -> apply(gravity, speed, -1.0f, orbit, fixed);
                    case "sky" -> apply(gravity, speed, locked, !orbit, fixed);
                    case "fixed" -> apply(gravity, speed, locked, orbit, !fixed);
                    case "create", "enter" -> {
                        PacketDistributor.sendToServer(new C2SPdimPacket(C2SPdimPacket.Action.ENTER, body.id(), gravity,
                                speed, locked, orbit, fixed));
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
                            apply(presets.get(Integer.parseInt(id.substring(6))).gravity(), speed, locked, orbit, fixed);
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
