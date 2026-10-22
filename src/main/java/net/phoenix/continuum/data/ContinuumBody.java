package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.client.render.PlanetParams;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ContinuumBody(
                            ResourceLocation id,
                            ResourceLocation system,
                            @Nullable ResourceLocation parent,
                            Type type,
                            String name,
                            String description,
                            float orbitAu,
                            float periodDays,
                            float phase,
                            float radius,
                            float tripMinutes,
                            String minTier,
                            @Nullable ResourceLocation detectedResearch,
                            @Nullable ResourceLocation surveyedResearch,
                            PlanetParams params,
                            List<Yield> yields,
                            DiscoveryStage initialStage,
                            String loreDetected,
                            String loreSurveyed,
                            @Nullable String discipline,
                            @Nullable PdimCost pdimCost,
                            @Nullable Transient transientInfo,
                            @Nullable Float holeTilt,
                            @Nullable Float skyHoleInclination,
                            @Nullable Float skyHoleLean,
                            @Nullable com.google.gson.JsonObject visibility,
                            List<Anomaly> anomalies) {

    public record Anomaly(String id, String name, String description, DiscoveryStage reveal, List<Yield> rewards,
                          @Nullable ResourceLocation research, String message, @Nullable PdimCost cost) {}

    public record Transient(int durationMinutes, int cooldownMinutes, float chance, float innerAu, float outerAu) {}

    public record PdimCost(ResourceLocation item, int count) {}

    public record Yield(ResourceLocation item, int min, int max, float chance) {}

    public enum Type {

        PLANET,
        MOON,
        BLACK_HOLE,
        STAR;

        public String label() {
            return switch (this) {
                case PLANET -> "Planet";
                case MOON -> "Moon";
                case BLACK_HOLE -> "Black hole";
                case STAR -> "Star";
            };
        }
    }

    public boolean isCentral() {
        return orbitAu <= 0.0f && parent == null;
    }

    public String lore(DiscoveryStage stage) {
        return switch (stage) {
            case DETECTED -> loreDetected;
            case SURVEYED -> loreSurveyed;
            default -> "";
        };
    }

    public boolean isMoon() {
        return parent != null;
    }

    public boolean isComet() {
        return transientInfo != null && params.style() != PlanetParams.STYLE_TIDAL &&
                params.style() != PlanetParams.STYLE_ROGUE && params.style() != PlanetParams.STYLE_REMNANT;
    }

    public boolean isRogue() {
        return transientInfo != null && params.style() == PlanetParams.STYLE_ROGUE;
    }

    public boolean isRemnant() {
        return transientInfo != null && params.style() == PlanetParams.STYLE_REMNANT;
    }

    public String visitorKind() {
        return isRogue() ? "rogue planet" : isRemnant() ? "supernova remnant" :
                params.style() == PlanetParams.STYLE_TIDAL ? "stellar collision" : "comet";
    }
}
