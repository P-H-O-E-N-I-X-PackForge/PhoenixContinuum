package net.phoenix.continuum.pdim;

import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.api.PdimWorldgen;

import java.util.HashMap;
import java.util.Map;

/**
 * The weather of a personal dimension whose spec has its own: always clear, always raining, always storming, or a cycle
 * of its own. Called in place of the game's weather step (see the mixin); anything else keeps the overworld's weather.
 */
public final class PdimWeather {

    private PdimWeather() {}

    /** How the weather of a dimension is decided. */
    public enum Mode {
        /** As the overworld's. */
        NORMAL,
        CLEAR,
        RAIN,
        STORM,
        /** Its own spells of clear and rain, set by {@link PdimWorldgen.Spec#weatherCycle}. */
        CYCLE;

        public static Mode of(String text) {
            return switch (text.toLowerCase(java.util.Locale.ROOT)) {
                case "clear", "none", "off" -> CLEAR;
                case "rain", "raining" -> RAIN;
                case "storm", "thunder", "thunderstorm" -> STORM;
                case "cycle" -> CYCLE;
                default -> NORMAL;
            };
        }
    }

    /** The spells of a {@link Mode#CYCLE}: lengths in ticks, and the share of rainy spells that are thunderstorms. */
    public record Cycle(int clearMin, int clearMax, int rainMin, int rainMax, float stormChance) {

        public static final Cycle DEFAULT = new Cycle(12000, 168000, 12000, 24000, 0.3f);
    }

    private static final class State {

        int timer;
        boolean raining;
        boolean storming;
    }

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    /** @return true when this dimension has weather of its own and the game's step is not to run. */
    public static boolean advance(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) return false;
        PdimWorldgen.Spec spec = PdimRules.specOf(level);
        if (spec == null || spec.weather() == Mode.NORMAL) return false;

        boolean rain;
        boolean storm;
        switch (spec.weather()) {
            case CLEAR -> {
                rain = false;
                storm = false;
            }
            case RAIN -> {
                rain = true;
                storm = false;
            }
            case STORM -> {
                rain = true;
                storm = true;
            }
            default -> {
                State state = STATES.computeIfAbsent(level.dimension(), k -> new State());
                Cycle cycle = spec.weatherCycle();
                if (state.timer-- <= 0) {
                    if (state.raining) {
                        state.raining = false;
                        state.storming = false;
                        state.timer = between(level, cycle.clearMin(), cycle.clearMax());
                    } else {
                        state.raining = true;
                        state.storming = level.random.nextFloat() < cycle.stormChance();
                        state.timer = between(level, cycle.rainMin(), cycle.rainMax());
                    }
                }
                rain = state.raining;
                storm = state.storming;
            }
        }

        level.oThunderLevel = level.thunderLevel;
        level.thunderLevel = Mth.clamp(level.thunderLevel + (storm ? 0.01f : -0.01f), 0.0f, 1.0f);
        level.oRainLevel = level.rainLevel;
        level.rainLevel = Mth.clamp(level.rainLevel + (rain ? 0.01f : -0.01f), 0.0f, 1.0f);

        if (level.oRainLevel != level.rainLevel) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel),
                    level.dimension());
        }
        if (level.oThunderLevel != level.thunderLevel) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel),
                    level.dimension());
        }
        return true;
    }

    private static int between(ServerLevel level, int min, int max) {
        return max <= min ? Math.max(1, min) : min + level.random.nextInt(max - min + 1);
    }

    /** Forget the running cycle of a dimension (it starts again from clear). */
    public static void reset(ResourceKey<Level> dimension) {
        STATES.remove(dimension);
    }
}
