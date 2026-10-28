package net.phoenix.continuum.pdim;

import net.phoenix.continuum.api.PdimWorldgen;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.data.ContinuumBody;

/**
 * Worldgen worked out from a body's own data, so a world looks like what the map shows with nothing to script: a body
 * with no ground (gas giant, star, black hole) stays void; a rocky one gets terrain in a biome that follows its
 * distance from its star, its ice and its oceans.
 */
public final class PdimAuto {

    private PdimAuto() {}

    public static void apply(PdimWorldgen.Spec spec, ContinuumBody body) {
        PlanetParams p = body.params();
        if (body.type() == ContinuumBody.Type.STAR || body.type() == ContinuumBody.Type.BLACK_HOLE ||
                (p != null && p.gasGiant())) {
            spec.voidWorld();
            return;
        }
        float ocean = p == null ? 0.0f : p.oceanLevel();
        float ice = p == null ? 0.0f : p.polarIce();
        float au = body.orbitAu();
        String biome;
        if (ice > 0.55f || au > 2.2f) biome = "minecraft:snowy_plains";
        else if (au < 0.55f) biome = "minecraft:desert";
        else if (au < 0.8f) biome = "minecraft:badlands";
        else if (ocean > 0.45f) biome = "minecraft:ocean";
        else if (au > 1.7f) biome = "minecraft:taiga";
        else biome = "minecraft:plains";
        // a moon with no air is bare rock
        if (body.type() == ContinuumBody.Type.MOON && (p == null || p.atmoDensity() < 0.05f)) {
            biome = "minecraft:stony_peaks";
        }
        if (ocean > 0.45f && au >= 0.8f && au <= 1.7f) {
            spec.overworldLike();
        } else {
            spec.noise("minecraft:overworld", biome);
        }
        spec.arriveOnSurface();
    }
}
