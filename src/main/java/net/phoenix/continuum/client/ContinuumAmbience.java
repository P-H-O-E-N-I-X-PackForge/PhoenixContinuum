package net.phoenix.continuum.client;

import net.minecraft.sounds.SoundEvent;
import net.phoenix.continuum.config.ContinuumConfigs;
import net.phoenix.continuum.client.render.PlanetParams;
import net.phoenix.continuum.data.ContinuumBody;
import net.phoenix.continuum.data.ContinuumSystem;
import net.phoenix.continuum.data.DiscoveryStage;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ContinuumAmbience {

    private final Map<SoundEvent, ContinuumSounds.Loop> loops = new HashMap<>();

    public void update(@Nullable ContinuumSystem system, @Nullable ContinuumBody body, float duck) {
        Map<SoundEvent, Float> targets = new HashMap<>();
        float master = (float) ContinuumConfigs.INSTANCE.ambienceVolume * duck;

        add(targets, HostSounds.void_cosmic_drone(),
                body != null ? 0.16f : system != null ? 0.26f : 0.32f);

        if (body != null) {
            DiscoveryStage stage = ContinuumClientState.stage(body.id());
            float known = stage == DiscoveryStage.SURVEYED ? 1.0f : 0.45f;
            PlanetParams params = body.params();
            switch (body.type()) {
                case STAR -> add(targets, HostSounds.phoenix_volcanic_rumble(), 0.4f);
                case BLACK_HOLE -> {
                    add(targets, HostSounds.sealed_b_inverted_hum(), 0.5f);
                    add(targets, HostSounds.void_cosmic_drone(), 0.3f);
                }
                case MOON -> add(targets, HostSounds.void_ethereal_wind(), 0.12f * known);
                default -> {
                    add(targets, HostSounds.phoenix_volcanic_wind(),
                            Math.min(1.0f, params.atmoDensity()) * (params.gasGiant() ? 0.6f : 0.42f) * known);
                    if (params.oceanLevel() >= 0.5f) {
                        add(targets, HostSounds.sculk_deep_cave(), 0.34f * known);
                    }
                    if (params.emissiveAmount() >= 0.3f) {
                        add(targets, HostSounds.sealed_a_neon_hum(), 0.28f * known);
                    }
                }
            }
        } else if (system != null) {
            if (system.hasHole()) {
                add(targets, HostSounds.sealed_b_inverted_hum(),
                        system.isQuasar() ? 0.42f : 0.3f);
                add(targets, HostSounds.void_ethereal_wind(), 0.14f);
            } else {
                add(targets, HostSounds.phoenix_volcanic_rumble(), 0.14f);
            }
        }

        Iterator<Map.Entry<SoundEvent, ContinuumSounds.Loop>> it = loops.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Float want = targets.get(entry.getKey());
            if (want == null || want * master < 0.005f) {
                entry.getValue().fadeOut();
                it.remove();
            }
        }
        targets.forEach((event, volume) -> {
            if (volume * master < 0.005f) return;
            ContinuumSounds.Loop loop = loops.computeIfAbsent(event, ContinuumSounds::ambientLoop);
            loop.setTarget(Math.min(1.0f, volume * master));
        });
    }

    private static void add(Map<SoundEvent, Float> targets, SoundEvent event, float volume) {
        targets.merge(event, volume, Float::sum);
    }

    public void stop() {
        loops.values().forEach(ContinuumSounds.Loop::fadeOut);
        loops.clear();
    }
}
