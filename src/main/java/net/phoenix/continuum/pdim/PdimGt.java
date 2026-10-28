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
 * veins generate there as they do in that dimension. Written against GregTech 7 (static registries); with GregTech 8 the
 * classes are not found and nothing is changed. Touches GregTech's classes: only call with GregTech loaded.
 */
final class PdimGt {

    private PdimGt() {}

    static void mirror(MinecraftServer server, ResourceKey<Level> source, ResourceKey<Level> target) {
        int veins = 0;
        int fluids = 0;
        int bedrockOres = 0;
        try {
            for (var vein : GTRegistries.ORE_VEINS) {
                var filter = vein.dimensionFilter();
                if (filter != null && filter.contains(source) && !filter.contains(target)) {
                    var widened = new HashSet<>(filter);
                    widened.add(target);
                    vein.dimensionFilter(widened);
                    veins++;
                }
            }
            for (var fluid : GTRegistries.BEDROCK_FLUID_DEFINITIONS) {
                var filter = fluid.getDimensionFilter();
                if (filter != null && filter.contains(source) && !filter.contains(target)) {
                    var widened = new HashSet<>(filter);
                    widened.add(target);
                    fluid.setDimensionFilter(widened);
                    fluids++;
                }
            }
            for (var ore : GTRegistries.BEDROCK_ORE_DEFINITIONS) {
                var filter = ore.dimensionFilter();
                if (filter != null && filter.contains(source) && !filter.contains(target)) {
                    var widened = new HashSet<>(filter);
                    widened.add(target);
                    ore.dimensionFilter(widened);
                    bedrockOres++;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            PhoenixContinuum.LOGGER.warn("Could not give {} the ores of {} (this GregTech version is not supported): {}",
                    target.location(), source.location(), e.toString());
            return;
        }
        if (veins + fluids + bedrockOres > 0) {
            PhoenixContinuum.LOGGER.info("{} now has {} ore vein(s), {} bedrock fluid(s) and {} bedrock ore(s) like {}",
                    target.location(), veins, fluids, bedrockOres, source.location());
        }
    }
}
