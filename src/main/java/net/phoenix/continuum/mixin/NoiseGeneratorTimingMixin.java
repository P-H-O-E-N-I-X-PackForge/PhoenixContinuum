package net.phoenix.continuum.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.phoenix.continuum.pdim.WorldgenTimes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Times the terrain pass of every noise generator, per noise settings: see {@link WorldgenTimes}. */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class NoiseGeneratorTimingMixin {

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true, remap = false)
    private void continuum$noiseTimed(Blender blender, RandomState randomState, StructureManager structures,
                                      ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        long start = System.nanoTime();
        String key = ((NoiseBasedChunkGenerator) (Object) this).generatorSettings().unwrapKey()
                .map(k -> k.location().toString()).orElse("inline settings");
        cir.setReturnValue(cir.getReturnValue().whenComplete((done, error) -> WorldgenTimes.record("noise", key,
                System.nanoTime() - start)));
    }
}
