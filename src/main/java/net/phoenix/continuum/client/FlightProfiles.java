package net.phoenix.continuum.client;

import net.phoenix.continuum.data.ContinuumBody;

import org.jetbrains.annotations.Nullable;

public final class FlightProfiles {

    private FlightProfiles() {}

    public enum Ascent {

        PLANET(7.0f, new String[] { ContinuumText.str("phoenix_continuum.flight_profiles.lift_off"), ContinuumText.str("phoenix_continuum.flight_profiles.max_q"), ContinuumText.str("phoenix_continuum.flight_profiles.stage_separation"), ContinuumText.str("phoenix_continuum.flight_profiles.orbit_insertion") }, 0xfff0c0, 0xff9a40,
                10.0f,
                13.0f, 0, 0.0f, false),
        GAS_GIANT(8.5f, new String[] { ContinuumText.str("phoenix_continuum.flight_profiles.lift_off"), ContinuumText.str("phoenix_continuum.flight_profiles.max_q"), ContinuumText.str("phoenix_continuum.flight_profiles.stage_separation"), ContinuumText.str("phoenix_continuum.flight_profiles.slingshot_burn") }, 0xfff0c0, 0xff7030,
                8.0f, 14.0f, 0, 0.0f, false),
        MOON(6.0f, new String[] { ContinuumText.str("phoenix_continuum.flight_profiles.lift_off"), ContinuumText.str("phoenix_continuum.flight_profiles.max_q"), ContinuumText.str("phoenix_continuum.flight_profiles.stage_separation"), ContinuumText.str("phoenix_continuum.flight_profiles.transfer_burn") }, 0xd0ecff, 0x80b8ff, 18.0f,
                12.0f, 0, 0.0f, false),
        STAR(8.0f, new String[] { ContinuumText.str("phoenix_continuum.flight_profiles.lift_off"), ContinuumText.str("phoenix_continuum.flight_profiles.max_q"), ContinuumText.str("phoenix_continuum.flight_profiles.stage_separation"), ContinuumText.str("phoenix_continuum.flight_profiles.sun_shield_deploy") }, 0xffffe0, 0xffd060,
                5.0f,
                14.0f, 0xffc070, 0.18f, false),
        BLACK_HOLE(9.0f, new String[] { ContinuumText.str("phoenix_continuum.flight_profiles.lift_off"), ContinuumText.str("phoenix_continuum.flight_profiles.max_q"), ContinuumText.str("phoenix_continuum.flight_profiles.stage_separation"), ContinuumText.str("phoenix_continuum.flight_profiles.shield_check") }, 0xd0e0ff, 0x6a50ff,
                22.0f, 16.0f, 0x100828, 0.35f, true);

        public final float seconds;
        public final String[] stages;
        public final int plumeCore;
        public final int plumeOuter;
        public final float endPitch;
        public final float endDistance;
        public final int tint;
        public final float tintAlpha;
        public final boolean vignette;

        Ascent(float seconds, String[] stages, int plumeCore, int plumeOuter, float endPitch, float endDistance,
               int tint,
               float tintAlpha, boolean vignette) {
            this.seconds = seconds;
            this.stages = stages;
            this.plumeCore = plumeCore;
            this.plumeOuter = plumeOuter;
            this.endPitch = endPitch;
            this.endDistance = endDistance;
            this.tint = tint;
            this.tintAlpha = tintAlpha;
            this.vignette = vignette;
        }

        public static Ascent of(@Nullable ContinuumBody destination) {
            if (destination == null) return PLANET;
            return switch (destination.type()) {
                case BLACK_HOLE -> BLACK_HOLE;
                case STAR -> STAR;
                case MOON -> MOON;
                default -> destination.params().gasGiant() ? GAS_GIANT : PLANET;
            };
        }
    }

    public enum Landing {

        ENTRY(ContinuumText.str("phoenix_continuum.flight_profiles.descent"), ContinuumText.str("phoenix_continuum.flight_profiles.touchdown"), 1.0f, 0.3f, 0xc8a070, false, 0, 0.0f, false, Touch.HARD),

        POWERED(ContinuumText.str("phoenix_continuum.flight_profiles.powered_descent"), ContinuumText.str("phoenix_continuum.flight_profiles.touchdown"), 0.0f, 1.0f, 0x9a9a9a, false, 0, 0.0f, false, Touch.DUST),

        LUNAR(ContinuumText.str("phoenix_continuum.flight_profiles.low_gravity_landing"), ContinuumText.str("phoenix_continuum.flight_profiles.touchdown"), 0.0f, 0.8f, 0xb0b0b8, false, 0, 0.0f, false, Touch.DUST),

        SPLASHDOWN(ContinuumText.str("phoenix_continuum.flight_profiles.descent"), ContinuumText.str("phoenix_continuum.flight_profiles.splashdown"), 0.8f, 0.5f, 0xcfe8ff, true, 0, 0.0f, false, Touch.SPLASH),

        ICE(ContinuumText.str("phoenix_continuum.flight_profiles.descent"), ContinuumText.str("phoenix_continuum.flight_profiles.touchdown"), 0.8f, 0.5f, 0xeaf6ff, false, 0, 0.0f, false, Touch.ICE),

        AEROCAPTURE(ContinuumText.str("phoenix_continuum.flight_profiles.aerobraking"), ContinuumText.str("phoenix_continuum.flight_profiles.aerocapture_complete"), 1.2f, 0.0f, 0, false, 0x6090ff, 0.10f, false, Touch.NONE),

        SOLAR(ContinuumText.str("phoenix_continuum.flight_profiles.solar_approach"), ContinuumText.str("phoenix_continuum.flight_profiles.orbit_achieved"), 0.0f, 0.0f, 0, false, 0xffb060, 0.18f, false, Touch.NONE),

        HORIZON(ContinuumText.str("phoenix_continuum.flight_profiles.horizon_approach"), ContinuumText.str("phoenix_continuum.flight_profiles.orbit_achieved"), 0.0f, 0.0f, 0, false, 0x100828, 0.30f, true, Touch.NONE);

        public enum Touch {
            NONE,
            HARD,
            DUST,
            SPLASH,
            ICE
        }

        public final String title;
        public final String success;
        public final float heat;
        public final float retro;
        public final int dust;
        public final boolean spray;
        public final int tint;
        public final float tintAlpha;
        public final boolean vignette;
        public final Touch touch;

        Landing(String title, String success, float heat, float retro, int dust, boolean spray, int tint,
                float tintAlpha, boolean vignette, Touch touch) {
            this.title = title;
            this.success = success;
            this.heat = heat;
            this.retro = retro;
            this.dust = dust;
            this.spray = spray;
            this.tint = tint;
            this.tintAlpha = tintAlpha;
            this.vignette = vignette;
            this.touch = touch;
        }

        public static Landing of(@Nullable ContinuumBody destination) {
            if (destination == null) return ENTRY;
            switch (destination.type()) {
                case BLACK_HOLE:
                    return HORIZON;
                case STAR:
                    return SOLAR;
                default:
                    break;
            }
            var params = destination.params();
            if (params.gasGiant()) return AEROCAPTURE;
            if (destination.isMoon()) return LUNAR;
            if (params.atmoDensity() < 0.15f) return POWERED;
            if (params.oceanLevel() >= 0.6f) return SPLASHDOWN;
            if (params.polarIce() >= 0.5f) return ICE;
            return ENTRY;
        }
    }
}
