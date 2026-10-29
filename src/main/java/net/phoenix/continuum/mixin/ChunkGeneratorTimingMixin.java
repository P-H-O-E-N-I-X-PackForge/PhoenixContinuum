package net.phoenix.continuum.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.phoenix.continuum.pdim.WorldgenTimes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times the feature pass (trees, ores, lakes) of every chunk, per dimension: see {@link WorldgenTimes}. */
@Mixin(value = ChunkGenerator.class, remap = false)
public abstract class ChunkGeneratorTimingMixin {

    private static final ThreadLocal<long[]> CONTINUUM$START = ThreadLocal.withInitial(() -> new long[1]);

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), remap = false)
    private void continuum$decorationStart(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                           CallbackInfo ci) {
        CONTINUUM$START.get()[0] = System.nanoTime();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"), remap = false)
    private void continuum$decorationEnd(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                         CallbackInfo ci) {
        long start = CONTINUUM$START.get()[0];
        if (start == 0L) return;
        WorldgenTimes.record("decoration", level.getLevel().dimension().location().toString(),
                System.nanoTime() - start);
    }
}
