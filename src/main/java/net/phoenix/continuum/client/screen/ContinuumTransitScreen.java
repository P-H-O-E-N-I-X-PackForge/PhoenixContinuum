package net.phoenix.continuum.client.screen;

import net.phoenix.continuum.client.ContinuumText;
import net.phoenix.continuum.common.ContinuumLang;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.phoenix.continuum.client.ContinuumClientState;
import net.phoenix.continuum.client.ContinuumSounds;
import net.phoenix.continuum.client.render.ContinuumShaders;
import net.phoenix.continuum.client.render.GlowRenderer;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.client.render.SceneRenderer;
import net.phoenix.continuum.client.render.SceneTarget;
import net.phoenix.continuum.common.ContinuumStateSnapshot;
import net.phoenix.continuum.common.Mission;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumData;
import net.phoenix.continuum.data.DiscoveryStage;
import net.phoenix.continuum.network.C2SCollectMissionPacket;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.UUID;

public class ContinuumTransitScreen extends Screen {

    @Override
    public void renderBackground(GuiGraphics g) {}

    private static final float START_DISTANCE = 70.0f;
    private static final float END_DISTANCE = 3.9f;

    private static final float[] WAYPOINT_AT = { 0.12f, 0.38f, 0.66f, 0.90f };
    private static final String[] WAYPOINT_TEXT = { ContinuumText.str("phoenix_continuum.continuum_transit_screen.clearing_the_home_system"), ContinuumText.str("phoenix_continuum.continuum_transit_screen.crossing_the_outer_dark"),
            ContinuumText.str("phoenix_continuum.continuum_transit_screen.course_correction_burn"), ContinuumText.str("phoenix_continuum.continuum_transit_screen.final_approach") };

    private final UUID missionId;
    private final @Nullable BlockPos pad;
    private final SceneTarget scene = new SceneTarget();

    private float clock;
    private long lastFrame = Util.getMillis();
    private ContinuumSounds.Loop hum;
    private int waypointsPassed = -1;
    private boolean wasRunning;

    @Override
    protected void init() {
        super.init();
        if (hum == null) {
            hum = ContinuumSounds.humLoop();
            hum.setTarget(0.4f);
        }
    }

    public ContinuumTransitScreen(UUID missionId, @Nullable BlockPos pad) {
        super(ContinuumLang.tr("phoenix_continuum.continuum_transit_screen.transit"));
        this.missionId = missionId;
        this.pad = pad;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapUi.refreshTheme();
        long nowMillis = Util.getMillis();
        float dt = Math.min((nowMillis - lastFrame) / 1000.0f, 0.1f);
        lastFrame = nowMillis;
        clock += dt;

        ContinuumStateSnapshot.MissionView mission = ContinuumClientState.mission(missionId);
        if (mission == null) {
            Minecraft.getInstance().setScreen(new ContinuumMapScreen(pad));
            return;
        }

        long serverNow = ContinuumClientState.now();
        float progress = mission.progress(serverNow);
        ContinuumBody destination = ContinuumData.body(mission.destination());

        if (!mission.state().finished()) {
            wasRunning = true;
        } else if (wasRunning) {
            Minecraft.getInstance().setScreen(new ContinuumArrivalScreen(missionId, pad));
            return;
        }

        int passed = 0;
        for (float waypoint : WAYPOINT_AT) {
            if (progress >= waypoint) passed++;
        }
        if (waypointsPassed >= 0 && passed > waypointsPassed && !mission.state().finished()) {
            ContinuumSounds.waypoint();
        }
        waypointsPassed = passed;

        drawScene(graphics, mission, destination, progress);
        drawOverlay(graphics, mouseX, mouseY, mission, destination, progress, serverNow);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawScene(GuiGraphics graphics, ContinuumStateSnapshot.MissionView mission,
                           @Nullable ContinuumBody destination, float progress) {
        RenderTarget target = scene.ensure();
        if (target == null || !ContinuumShaders.ready() || destination == null) {
            graphics.fill(0, 0, width, height, 0xFF05060f);
            return;
        }

        DiscoveryStage stage = ContinuumClientState.stage(destination.id());
        PlanetParams params = stage == DiscoveryStage.SURVEYED || mission.state() == Mission.State.SUCCESS ?
                destination.params() : destination.params().ghost();

        float approach = 1.0f - (1.0f - progress) * (1.0f - progress);
        float distance = START_DISTANCE + (END_DISTANCE - START_DISTANCE) * approach;

        Matrix4f projection = SceneRenderer.projection(target.width, target.height, 40.0f);
        Matrix4f view = new Matrix4f().translate(0.0f, 0.0f, -distance).rotateX((float) Math.toRadians(9.0));

        SceneRenderer.begin(target, clock * 3.0f);
        if (SceneRenderer.drawCentralBody(destination, view, projection, new Vector3f(), 1.2f,
                net.phoenix.continuum.client.ContinuumVisuals.quality())) {

        } else {
            SceneRenderer.drawPlanet(params, view, projection, new Vector3f(), 1.0f, new Vector3f(-60.0f, 24.0f, 40.0f),
                    clock * params.spinDegPerSec(), clock * params.cloudSpinDegPerSec(),
                    net.phoenix.continuum.client.ContinuumVisuals.quality(), true);
        }
        SceneRenderer.end();
        scene.blit(graphics, width, height);

        if (!mission.state().finished()) {
            float flicker = 0.8f + 0.2f * (float) Math.sin(clock * 38.0);
            GlowRenderer.batch(graphics)
                    .glow(width / 2.0f, height * 0.9f, 22.0f, 0xfff0c0, 0.7f * flicker)
                    .glow(width / 2.0f, height * 0.9f + 14.0f, 38.0f, 0xff9a40, 0.45f * flicker)
                    .draw();
        }
    }

    private int backX() {
        return width - 62;
    }

    private int replayX() {
        return width - 140;
    }

    private int collectX() {
        return width / 2 - 62;
    }

    private int collectY() {
        return height - 58;
    }

    private void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY, ContinuumStateSnapshot.MissionView mission,
                             @Nullable ContinuumBody destination, float progress, long serverNow) {
        String name = destination != null ? destination.name() : mission.destination().getPath();

        graphics.renderOutline(1, 1, width - 2, height - 2, MapUi.FRAME);
        boolean extraction = mission.type() == Mission.Type.EXTRACT;
        boolean payload = extraction || mission.type() == Mission.Type.DEPLOY;
        graphics.drawString(font, mission.type().label().toUpperCase(java.util.Locale.ROOT), 14, 12, MapUi.TITLE);
        graphics.drawString(font, ContinuumText.str("phoenix_continuum.continuum_transit.bound_for", name, (payload ? "  -  " + mission.probes() + " probe" +
                (mission.probes() == 1 ? "" : "s") : "")), 14, 24, MapUi.TEXT);
        graphics.drawString(font, ContinuumText.str("phoenix_continuum.continuum_transit.launched_by", mission.launcherName()), 14, 35, MapUi.DIM);

        int y = 56;
        for (int i = 0; i < WAYPOINT_AT.length; i++) {
            boolean passed = progress >= WAYPOINT_AT[i];
            graphics.drawString(font, (passed ? "[x] " : "[ ] ") + WAYPOINT_TEXT[i], 14, y,
                    passed ? MapUi.TEXT : MapUi.DIM);
            y += 11;
        }

        if (mission.state().finished()) {
            graphics.fill(replayX(), 8, replayX() + 70, 22, MapUi.CHROME);
            graphics.renderOutline(replayX(), 8, 70, 14, MapUi.PANEL_LINE);
            graphics.drawCenteredString(font, ContinuumText.str("phoenix_continuum.continuum_transit.replay_landing"), replayX() + 35, 11, MapUi.TEXT);
        }

        graphics.fill(backX(), 8, backX() + 52, 22, MapUi.CHROME);
        graphics.renderOutline(backX(), 8, 52, 14, MapUi.PANEL_LINE);
        graphics.drawCenteredString(font, ContinuumText.str("phoenix_continuum.continuum_transit.map"), backX() + 26, 11, MapUi.TEXT);

        int barW = Math.min(420, width - 80);
        int barX = (width - barW) / 2;
        int barY = height - 28;
        boolean finished = mission.state().finished();

        MapUi.bar(graphics, barX, barY, barW, 8, progress,
                mission.state() == Mission.State.FAILED ? MapUi.BAD : MapUi.FRAME);
        for (float waypoint : WAYPOINT_AT) {
            int wx = barX + Math.round(barW * waypoint);
            graphics.fill(wx, barY - 3, wx + 1, barY + 11, progress >= waypoint ? 0xFFB8B0D8 : 0xFF4a4468);
        }

        String status;
        int statusColor;
        if (mission.state() == Mission.State.SUCCESS) {
            status = switch (mission.type()) {
                case SURVEY -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_survey_complete");
                case EXTRACT -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_resources_are_ready_to_collect");
                case DEPLOY -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_the_outpost_is_up_and");
                case HAUL -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_the_stockpile_is_ready_to");
                case REPAIR -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_the_outpost_is_repaired_and");
                case RESCUE -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_the_stranded_rocket_is_recovered");
                case STATION -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_the_orbital_station_is_built");
                case SHELL -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.arrived_another_section_of_the_shell");
            };
            statusColor = MapUi.GOOD;
        } else if (mission.stranded()) {
            status = mission.rescuing() ? ContinuumText.str("phoenix_continuum.continuum_transit_screen.distress_signal_a_rescue_run_is") :
                    ContinuumText.str("phoenix_continuum.continuum_transit_screen.distress_fades", net.phoenix.continuum.common.ContinuumMissions
                                    .durationText(mission.distressUntil() - serverNow));
            statusColor = MapUi.WARN;
        } else if (mission.state() == Mission.State.FAILED) {
            status = switch (mission.type()) {
                case SURVEY -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_rocket_came_back");
                case EXTRACT, DEPLOY -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_probes_were_lost");
                case HAUL -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_haul_was_lost");
                case REPAIR -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_repair_kits_were");
                case RESCUE -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_repair_kits_were_2");
                case STATION -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_station_crew_s");
                case SHELL -> ContinuumText.str("phoenix_continuum.continuum_transit_screen.mission_failed_the_shell_crew_s");
            };
            statusColor = MapUi.BAD;
        } else {
            status = ContinuumText.str("phoenix_continuum.continuum_transit_screen.remaining", MapUi.duration(mission.endMillis() - serverNow));
            statusColor = MapUi.TEXT;
        }
        graphics.drawCenteredString(font, status, width / 2, barY - 14, statusColor);

        String wear = String.format(Locale.ROOT, ContinuumText.label("phoenix_continuum.continuum_transit_screen.rocket_wear_after_this_trip_d"),
                Math.round(mission.wearAfter() * 100.0f));
        graphics.drawCenteredString(font, wear, width / 2, barY + 12, MapUi.wearColor(mission.wearAfter()));

        if (finished && mission.stranded() && !mission.rescuing()) {
            boolean hover = MapUi.inside(mouseX, mouseY, collectX(), collectY(), 124, 18);
            graphics.fill(collectX(), collectY(), collectX() + 124, collectY() + 18, hover ? 0xFF4a2c2c : 0xFF3a1d1d);
            graphics.renderOutline(collectX(), collectY(), 124, 18, MapUi.WARN);
            graphics.drawCenteredString(font, ContinuumText.str("phoenix_continuum.continuum_transit.plan_rescue"), collectX() + 62, collectY() + 5, MapUi.TITLE);
        }
        if (finished && !mission.stranded()) {
            boolean hover = MapUi.inside(mouseX, mouseY, collectX(), collectY(), 124, 18);
            graphics.fill(collectX(), collectY(), collectX() + 124, collectY() + 18,
                    hover ? MapUi.BUTTON_HOVER : MapUi.BUTTON);
            graphics.renderOutline(collectX(), collectY(), 124, 18, MapUi.FRAME);
            boolean hasHaul = mission.state() == Mission.State.SUCCESS &&
                    (extraction || mission.type() == Mission.Type.HAUL);
            graphics.drawCenteredString(font, hasHaul ? ContinuumText.str("phoenix_continuum.continuum_transit.collect_rocket_haul") : ContinuumText.str("phoenix_continuum.continuum_transit.collect_rocket"), collectX() + 62,
                    collectY() + 5, MapUi.TITLE);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (MapUi.inside(mouseX, mouseY, backX(), 8, 52, 14)) {
                ContinuumSounds.click();
                leave();
                return true;
            }
            ContinuumStateSnapshot.MissionView mission = ContinuumClientState.mission(missionId);
            if (mission != null && mission.state().finished() && MapUi.inside(mouseX, mouseY, replayX(), 8, 70, 14)) {
                ContinuumSounds.click();
                Minecraft.getInstance().setScreen(new ContinuumArrivalScreen(missionId, pad));
                return true;
            }
            if (mission != null && mission.stranded() && !mission.rescuing() &&
                    MapUi.inside(mouseX, mouseY, collectX(), collectY(), 124, 18)) {
                ContinuumSounds.click();
                ContinuumMapScreen.requestRescue(mission.destination());
                Minecraft.getInstance().setScreen(new ContinuumMapScreen(pad));
                return true;
            }
            if (mission != null && mission.state().finished() && !mission.stranded() &&
                    MapUi.inside(mouseX, mouseY, collectX(), collectY(), 124, 18)) {
                ContinuumSounds.click();
                net.phoenix.continuum.network.ContinuumNetwork.sendToServer(new C2SCollectMissionPacket(missionId));
                leave();
                return true;
            }
        } else if (button == 1) {
            leave();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_BACKSPACE) {
            leave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void leave() {
        Minecraft.getInstance().setScreen(new ContinuumMapScreen(pad));
    }

    @Override
    public void removed() {
        if (hum != null) hum.fadeOut();
        scene.close();
        super.removed();
    }
}
