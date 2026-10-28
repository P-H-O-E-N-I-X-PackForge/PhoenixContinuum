package net.phoenix.continuum.pdim;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.phoenix.continuum.PhoenixContinuum;

/** Puts a structure template at a plot, for specs that start a personal dimension with a base. */
final class PdimStructures {

    private PdimStructures() {}

    /**
     * Place {@code template} so its floor is at {@code ground} and its middle on the plot.
     *
     * @return false if the template does not exist
     */
    static boolean place(ServerLevel level, ResourceLocation template, BlockPos ground) {
        var found = level.getStructureManager().get(template);
        if (found.isEmpty()) {
            PhoenixContinuum.LOGGER.warn("Plot structure {} does not exist", template);
            return false;
        }
        var structure = found.get();
        var size = structure.getSize();
        BlockPos corner = ground.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        level.getChunk(ground);
        var settings = new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE)
                .setIgnoreEntities(false);
        return structure.placeInWorld(level, corner, corner, settings, level.getRandom(), 2);
    }
}
