package net.phoenix.continuum.pdim;

import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.api.PdimWorldgen;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PhoenixContinuum.MOD_ID)
public final class PdimWeather {

    private PdimWeather() {}

    public enum Mode {
        
        NORMAL,
        CLEAR,
        RAIN,
        STORM,
        
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

    public record Cycle(int clearMin, int clearMax, int rainMin, int rainMax, float stormChance) {

        public static final Cycle DEFAULT = new Cycle(12000, 168000, 12000, 24000, 0.3f);
    }

    private static final class State {

        int timer;
        boolean raining;
        boolean storming;
        float rain;
        float thunder;
        float sentRain = -1.0f;
        float sentThunder = -1.0f;
    }

    private static final Map<ResourceKey<Level>, State> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        advance(level);
    }

    public static boolean advance(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) return false;
        PdimWorldgen.Spec spec = PdimRules.specOf(level);
        if (spec == null || spec.weather() == Mode.NORMAL) {
            STATES.remove(level.dimension());
            return false;
        }

        State state = STATES.computeIfAbsent(level.dimension(), k -> new State());
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

        if (state.sentRain < 0.0f) {
            state.rain = level.getRainLevel(1.0f);
            state.thunder = level.getThunderLevel(1.0f);
        }
        state.thunder = Mth.clamp(state.thunder + (storm ? 0.01f : -0.01f), 0.0f, 1.0f);
        state.rain = Mth.clamp(state.rain + (rain ? 0.01f : -0.01f), 0.0f, 1.0f);

        boolean rainDiffers = level.getRainLevel(1.0f) != state.rain || state.sentRain != state.rain;
        boolean thunderDiffers = level.getThunderLevel(1.0f) != state.thunder || state.sentThunder != state.thunder;
        level.setRainLevel(state.rain);
        level.setThunderLevel(state.thunder);
        if (rainDiffers) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, state.rain),
                    level.dimension());
            state.sentRain = state.rain;
        }
        if (thunderDiffers) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, state.thunder),
                    level.dimension());
            state.sentThunder = state.thunder;
        }
        return true;
    }

    private static int between(ServerLevel level, int min, int max) {
        return max <= min ? Math.max(1, min) : min + level.random.nextInt(max - min + 1);
    }

    public static void reset(ResourceKey<Level> dimension) {
        STATES.remove(dimension);
    }
}
