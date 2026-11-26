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
                case PLANET -> net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.type_planet");
                case MOON -> net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.type_moon");
                case BLACK_HOLE -> net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.type_black_hole");
                case STAR -> net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.type_star");
            };
        }
    }

    public ContinuumBody withType(Type newType) {
        return new ContinuumBody(id, system, parent, newType, name, description, orbitAu, periodDays, phase, radius,
                tripMinutes, minTier, detectedResearch, surveyedResearch, params, yields, initialStage, loreDetected,
                loreSurveyed, discipline, pdimCost, transientInfo, holeTilt, skyHoleInclination, skyHoleLean,
                visibility, anomalies);
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

    public String kindLabel() {
        if (transientInfo != null) {
            String kind = visitorLabel();
            return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
        }
        if (type == Type.PLANET && params.gasGiant()) return net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.gas_giant");
        return type.label();
    }

    public ContinuumBody withRadius(float newRadius) {
        return new ContinuumBody(id, system, parent, type, name, description, orbitAu, periodDays, phase, newRadius,
                tripMinutes, minTier, detectedResearch, surveyedResearch, params, yields, initialStage, loreDetected,
                loreSurveyed, discipline, pdimCost, transientInfo, holeTilt, skyHoleInclination, skyHoleLean,
                visibility, anomalies);
    }

    /** The visitor kind as it is shown to a player, in their language. */
    public String visitorLabel() {
        return net.phoenix.continuum.common.ContinuumLang.text("phoenix_continuum.continuum_body.visitor_" +
                visitorKind().replace(' ', '_'));
    }

    public String visitorKind() {
        return isRogue() ? "rogue planet" : isRemnant() ? "supernova remnant" :
                params.style() == PlanetParams.STYLE_TIDAL ? "stellar collision" : "comet";
    }
}
