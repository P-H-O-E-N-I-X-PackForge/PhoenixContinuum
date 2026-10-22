package net.phoenix.continuum.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.block.LaunchPadBlockEntity;
import net.phoenix.continuum.integration.gtceu.ContinuumGt;
import net.phoenix.continuum.integration.gtceu.GregTech;

import org.jetbrains.annotations.Nullable;

/** Finds the launch complex at a position: the GregTech multiblock when GregTech is installed, the powered Launch Pad when not. */
public final class LaunchSites {

    private LaunchSites() {}

    public static @Nullable LaunchComplex at(Level level, BlockPos pos) {
        if (GregTech.loaded()) return ContinuumGt.complexAt(level, pos);
        return level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad ? pad : null;
    }
}
