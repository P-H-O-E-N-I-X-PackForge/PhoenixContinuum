package net.phoenix.continuum.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;

public final class ContinuumShaders {

    private ContinuumShaders() {}

    private static final Logger LOGGER = LogManager.getLogger();

    public static ShaderInstance PLANET;
    public static ShaderInstance ATMOSPHERE;
    public static ShaderInstance GLOW;
    public static ShaderInstance BACKDROP;
    public static ShaderInstance BLACKHOLE;
    public static ShaderInstance SKY;
    public static ShaderInstance RING;
    public static ShaderInstance NEBULA;
    public static ShaderInstance WOLFRAYET;
    public static ShaderInstance PULSAR;
    public static ShaderInstance PLANETARY;
    public static ShaderInstance PROTOSTAR;
    public static ShaderInstance GALAXY;
    public static ShaderInstance MEGASTRUCTURE;
    public static ShaderInstance DYSON;
    public static ShaderInstance NEBULA_BAKE;
    public static ShaderInstance PLANET_BAKE;
    public static ShaderInstance COMET;
    public static ShaderInstance REMNANT;
    public static ShaderInstance TIDAL;

    public static ShaderInstance STARFIELD;
    public static ShaderInstance COMPOSITE;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, "phoenix_continuum:continuum_planet", DefaultVertexFormat.POSITION, s -> PLANET = s);
        register(event, "phoenix_continuum:continuum_atmosphere", DefaultVertexFormat.POSITION, s -> ATMOSPHERE = s);
        register(event, "phoenix_continuum:continuum_glow", DefaultVertexFormat.POSITION_TEX_COLOR, s -> GLOW = s);
        register(event, "phoenix_continuum:continuum_backdrop", DefaultVertexFormat.POSITION, s -> BACKDROP = s);
        register(event, "phoenix_continuum:continuum_blackhole", DefaultVertexFormat.POSITION, s -> BLACKHOLE = s);
        register(event, "phoenix_continuum:continuum_sky", DefaultVertexFormat.POSITION, s -> SKY = s);
        register(event, "phoenix_continuum:continuum_ring", DefaultVertexFormat.POSITION, s -> RING = s);
        register(event, "phoenix_continuum:continuum_nebula", DefaultVertexFormat.POSITION, s -> NEBULA = s);
        register(event, "phoenix_continuum:continuum_wolfrayet", DefaultVertexFormat.POSITION, s -> WOLFRAYET = s);
        register(event, "phoenix_continuum:continuum_pulsar", DefaultVertexFormat.POSITION, s -> PULSAR = s);
        register(event, "phoenix_continuum:continuum_planetary", DefaultVertexFormat.POSITION, s -> PLANETARY = s);
        register(event, "phoenix_continuum:continuum_protostar", DefaultVertexFormat.POSITION, s -> PROTOSTAR = s);
        register(event, "phoenix_continuum:continuum_galaxy", DefaultVertexFormat.POSITION, s -> GALAXY = s);
        register(event, "phoenix_continuum:continuum_megastructure", DefaultVertexFormat.POSITION,
                s -> MEGASTRUCTURE = s);
        register(event, "phoenix_continuum:continuum_remnant", DefaultVertexFormat.POSITION, s -> REMNANT = s);
        register(event, "phoenix_continuum:continuum_comet", DefaultVertexFormat.POSITION, s -> COMET = s);
        register(event, "phoenix_continuum:continuum_planet_bake", DefaultVertexFormat.POSITION, s -> PLANET_BAKE = s);
        register(event, "phoenix_continuum:continuum_nebula_bake", DefaultVertexFormat.POSITION, s -> NEBULA_BAKE = s);
        register(event, "phoenix_continuum:continuum_dyson", DefaultVertexFormat.POSITION, s -> DYSON = s);
        register(event, "phoenix_continuum:continuum_tidal", DefaultVertexFormat.POSITION, s -> TIDAL = s);
        register(event, "phoenix_continuum:continuum_starfield", DefaultVertexFormat.POSITION, s -> STARFIELD = s);
        register(event, "phoenix_continuum:continuum_composite", DefaultVertexFormat.POSITION_TEX_COLOR,
                s -> COMPOSITE = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format,
                                 Consumer<ShaderInstance> onLoad) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), name, format), onLoad);
        } catch (IOException e) {
            LOGGER.error("[PhoenixCore/Continuum] Failed to register shader '{}': {}", name, e.getMessage());
        }
    }

    public static boolean ready() {
        return PLANET != null && ATMOSPHERE != null && GLOW != null;
    }
}
