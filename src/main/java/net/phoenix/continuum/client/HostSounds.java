package net.phoenix.continuum.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;

/**
 * The ambience Continuum plays comes from the pack around it (PhoenixCore registers these under its own id). Each one is
 * looked up when used; a sound nobody registered is simply silent.
 */
public final class HostSounds {

    private HostSounds() {}

    public static final String NAMESPACE = "phoenixcore";

    public static @Nullable SoundEvent void_cosmic_drone() {
        return get("ambient/void/cosmic_drone");
    }

    public static @Nullable SoundEvent void_ethereal_wind() {
        return get("ambient/void/ethereal_wind");
    }

    public static @Nullable SoundEvent phoenix_volcanic_rumble() {
        return get("ambient/phoenix/volcanic_rumble");
    }

    public static @Nullable SoundEvent phoenix_volcanic_wind() {
        return get("ambient/phoenix/volcanic_wind");
    }

    public static @Nullable SoundEvent sculk_deep_cave() {
        return get("ambient/sculk/deep_cave");
    }

    public static @Nullable SoundEvent sealed_a_neon_hum() {
        return get("ambient/sealed_a/neon_hum");
    }

    public static @Nullable SoundEvent sealed_b_inverted_hum() {
        return get("ambient/sealed_b/inverted_hum");
    }

    public static @Nullable SoundEvent get(String path) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(NAMESPACE, path));
    }
}
