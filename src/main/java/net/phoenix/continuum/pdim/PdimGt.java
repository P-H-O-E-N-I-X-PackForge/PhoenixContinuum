package net.phoenix.continuum.pdim;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.PhoenixContinuum;

import com.gregtechceu.gtceu.api.registry.GTRegistries;

import java.util.HashSet;

/**
 * GregTech's ore veins, bedrock fluids and bedrock ores belong to dimensions by a filter on each definition. This adds
 * a personal dimension to the filter of every definition that lists the dimension it is meant to be like, so the
 * veins generate there as they do in that dimension. Touches GregTech's classes: only call with GregTech loaded.
 */
final class PdimGt {

    private PdimGt() {}

    static void mirror(MinecraftServer server, ResourceKey<Level> source, ResourceKey<Level> target) {
        int veins = 0;
        int fluids = 0;
        int bedrockOres = 0;
        var access = server.registryAccess();
        try {
            for (var vein : access.registryOrThrow(GTRegistries.Keys.ORE_VEIN)) {
                var filter = vein.dimensionFilter();
                if (filter != null && filter.contains(source) && !filter.contains(target)) {
                    var widened = new HashSet<>(filter);
                    widened.add(target);
                    vein.dimensions(widened);
                    veins++;
                }
            }
            for (var fluid : access.registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID)) {
                if (fluid.dimensionFilter != null && fluid.dimensionFilter.contains(source) &&
                        !fluid.dimensionFilter.contains(target)) {
                    var widened = new HashSet<>(fluid.dimensionFilter);
                    widened.add(target);
                    fluid.dimensionFilter = widened;
                    fluids++;
                }
            }
            for (var ore : access.registryOrThrow(GTRegistries.Keys.BEDROCK_ORE)) {
                if (ore.dimensionFilter != null && ore.dimensionFilter.contains(source) &&
                        !ore.dimensionFilter.contains(target)) {
                    var widened = new HashSet<>(ore.dimensionFilter);
                    widened.add(target);
                    ore.dimensionFilter = widened;
                    bedrockOres++;
                }
            }
        } catch (RuntimeException e) {
            PhoenixContinuum.LOGGER.warn("Could not give {} the ores of {}: {}", target.location(), source.location(),
                    e.getMessage());
            return;
        }
        if (veins + fluids + bedrockOres > 0) {
            PhoenixContinuum.LOGGER.info("{} now has {} ore vein(s), {} bedrock fluid(s) and {} bedrock ore(s) like {}",
                    target.location(), veins, fluids, bedrockOres, source.location());
        }
    }
}
