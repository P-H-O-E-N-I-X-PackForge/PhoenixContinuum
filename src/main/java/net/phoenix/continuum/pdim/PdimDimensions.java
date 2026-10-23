package net.phoenix.continuum.pdim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumBody;

import org.jetbrains.annotations.Nullable;

public final class PdimDimensions {

    private PdimDimensions() {}

    public static final String PREFIX = "pdim/";

    public static final int PLOT_SPACING = 1024;
    public static final int PLATFORM_Y = 64;

    public static final float MIN_GRAVITY = 0.02f;
    public static final float MAX_GRAVITY = 6.0f;

    public static ResourceKey<Level> keyFor(ResourceLocation body) {
        return ResourceKey.create(Registries.DIMENSION, PhoenixContinuum.id(PREFIX + body.getPath()));
    }

    private static java.util.Map<String, ResourceLocation> byPath = java.util.Map.of();
    private static int byPathVersion = -1;

    public static @Nullable ResourceLocation bodyOf(ResourceKey<Level> dimension) {
        ResourceLocation id = dimension.location();
        if (!PhoenixContinuum.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith(PREFIX)) return null;
        String path = id.getPath().substring(PREFIX.length());
        if (byPathVersion != net.phoenix.continuum.data.ContinuumData.version()) {
            java.util.Map<String, ResourceLocation> found = new java.util.HashMap<>();
            for (ContinuumBody body : net.phoenix.continuum.data.ContinuumData.allBodies()) {
                
                ResourceLocation old = found.get(body.id().getPath());
                if (old == null || PhoenixContinuum.MOD_ID.equals(body.id().getNamespace())) {
                    found.put(body.id().getPath(), body.id());
                }
            }
            byPath = found;
            byPathVersion = net.phoenix.continuum.data.ContinuumData.version();
        }
        ResourceLocation body = byPath.get(path);
        return body != null ? body : PhoenixContinuum.id(path);
    }

    public static boolean isPersonal(ResourceKey<Level> dimension) {
        return bodyOf(dimension) != null;
    }

    public static BlockPos plotOrigin(int plot) {
        return new BlockPos((plot % 32) * PLOT_SPACING, PLATFORM_Y, (plot / 32) * PLOT_SPACING);
    }

    public static final float MIN_DAY_SPEED = 0.05f;
    public static final float MAX_DAY_SPEED = 20.0f;

    public static float clampDaySpeed(float speed) {
        return Math.max(MIN_DAY_SPEED, Math.min(MAX_DAY_SPEED, speed));
    }

    public static float clampGravity(float gravity) {
        return Math.max(MIN_GRAVITY, Math.min(MAX_GRAVITY, gravity));
    }

    public static float naturalGravity(ContinuumBody body) {
        return switch (body.type()) {
            case STAR -> 2.0f;
            case BLACK_HOLE -> 3.0f;
            default -> clampGravity((float) Math.pow(Math.max(0.05, body.radius()), 0.9));
        };
    }
}
