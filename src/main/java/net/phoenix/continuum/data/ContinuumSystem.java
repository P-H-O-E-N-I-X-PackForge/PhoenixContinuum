package net.phoenix.continuum.data;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public record ContinuumSystem(
                              ResourceLocation id,
                              String name,
                              String description,
                              float galaxyX, float galaxyY, float galaxyZ,
                              int starColor,
                              float starRadius,
                              String starKind,
                              DiscoveryStage initialStage,
                              @Nullable ResourceLocation detectedResearch,
                              @Nullable ResourceLocation surveyedResearch,
                              HoleStyle hole,
                              @Nullable com.google.gson.JsonObject visibility,
                              @Nullable Companion companion) {

    /**
     * A second star orbiting the first round their common centre, drawn as its own kind (a plain star, a dyson swarm, a
     * dyson sphere, ...).
     *
     * @param radius     its size relative to the primary
     * @param separation distance between the two, in primary radii
     * @param massRatio  its mass over the primary's (the lighter one orbits further out)
     */
    public record Companion(String kind, int color, float radius, float separation, float massRatio,
                            float periodSeconds) {}

    /** This system with its companion dropped and its star replaced: how each half of a pair is drawn on its own. */
    public ContinuumSystem withStar(String kind, int color) {
        return new ContinuumSystem(id, name, description, galaxyX, galaxyY, galaxyZ, color, starRadius, kind,
                initialStage, detectedResearch, surveyedResearch, hole, visibility, null);
    }


    public record HoleStyle(float diskSize, float diskIntensity, float feeding, float tilt, int hotColor, int coolColor,
                            float separation, float massRatio, float periodSeconds, float skyInclination,
                            float skyLean) {

        public static final HoleStyle DEFAULT = new HoleStyle(1.0f, 1.0f, 0.0f, 0.075f, -1, -1, 3.2f, 0.6f, 24.0f,
                Float.NaN, Float.NaN);

        public HoleStyle withTilt(float newTilt) {
            return new HoleStyle(diskSize, diskIntensity, feeding, newTilt, hotColor, coolColor, separation,
                    massRatio, periodSeconds, skyInclination, skyLean);
        }

        /** Overrides how the disk is tipped when it is drawn in a skybox (NaN keeps the default for that value). */
        public HoleStyle withSky(float inclination, float lean) {
            return new HoleStyle(diskSize, diskIntensity, feeding, tilt, hotColor, coolColor, separation, massRatio,
                    periodSeconds, Float.isNaN(inclination) ? skyInclination : inclination,
                    Float.isNaN(lean) ? skyLean : lean);
        }

        public HoleStyle withDisk(float size, float intensity) {
            return new HoleStyle(size, intensity, feeding, tilt, hotColor, coolColor, separation, massRatio,
                    periodSeconds, skyInclination, skyLean);
        }
    }

    public boolean isBlackHole() {
        return "black_hole".equals(starKind);
    }

    public boolean isQuasar() {
        return "quasar".equals(starKind);
    }

    public boolean isWolfRayet() {
        return "wolf_rayet".equals(starKind);
    }

    public int starStyle() {
        return switch (starKind) {
            case "red_giant" -> 1;
            case "brown_dwarf" -> 2;
            case "rapid_rotator" -> 3;
            default -> 0;
        };
    }

    public boolean isPulsar() {
        return "pulsar".equals(starKind);
    }

    /** A star shut inside a complete shell ("dyson_sphere"). */
    public boolean isDysonSphere() {
        return "dyson_sphere".equals(starKind) || "dyson_shell".equals(starKind);
    }

    public boolean isMegastructure() {
        return "dyson_swarm".equals(starKind) || "megastructure".equals(starKind);
    }

    public boolean isProtostar() {
        return "protostar".equals(starKind);
    }

    public boolean isPlanetaryNebula() {
        return "planetary_nebula".equals(starKind);
    }

    public boolean isBinaryHole() {
        return "binary_black_hole".equals(starKind);
    }

    public boolean hasHole() {
        return isBlackHole() || isQuasar() || isBinaryHole();
    }
}
