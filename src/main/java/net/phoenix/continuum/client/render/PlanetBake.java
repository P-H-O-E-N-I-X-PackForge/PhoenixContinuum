package net.phoenix.continuum.client.render;

import net.minecraft.client.renderer.ShaderInstance;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A planet's terrain and clouds drawn once into cube maps, so a planet that fills a lot of the screen looks them up instead
 * of running the noise for every pixel every frame. The terrain map holds the height and its gradient (what the shader
 * works out live, so the landmasses are exactly the same); the cloud map holds the cloud density, and the shader turns the
 * lookup direction to make them drift. Each one depends only on the planet's seed (and scale and detail), so they are
 * shared by everything that draws that planet and kept in a small least-recently-used cache.
 */
public final class PlanetBake {

    /** Texels along a cube face; the finest terrain detail is about four texels wide at this size. */
    private static final int SIZE = 512;
    private static final int MAX_TERRAIN = 8;
    private static final int MAX_CLOUDS = 8;
    /** At most one new bake per this many nanoseconds, so a system full of planets does not stall one frame. */
    private static final long BAKE_INTERVAL = 8_000_000L;
    /** A map used within this long is in use: it is never evicted for a new one. */
    private static final long IN_USE = 1_000_000_000L;

    // the texture units the planet shader reads them from (see continuum_planet.json)
    public static final int TERRAIN_UNIT = 5;
    public static final int CLOUD_UNIT = 6;

    private static final int CUBE_MAP = 34067;
    private static final int FACE_0 = 34069;

    private record TerrainKey(float seed, float scale, int octaves) {}

    private static final class Entry {
        final int tex;
        long lastUse;

        Entry(int tex, long now) {
            this.tex = tex;
            this.lastUse = now;
        }
    }

    private static final Map<TerrainKey, Entry> terrain = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Float, Entry> clouds = new LinkedHashMap<>(16, 0.75f, true);
    private static long lastBake;

    private PlanetBake() {}

    /** The terrain cube map for these settings (made on first use), or 0 if it is not available. */
    public static int terrain(float seed, float scale, int octaves) {
        ShaderInstance shader = ContinuumShaders.PLANET_BAKE;
        if (shader == null) return 0;
        return lookup(terrain, new TerrainKey(seed, scale, octaves), MAX_TERRAIN,
                () -> bake(shader, 0.0f, seed, scale, octaves, 34842, 6408));   // RGBA16F
    }

    /** The cloud cube map for this seed, or 0 if it is not available. */
    public static int clouds(float seed) {
        ShaderInstance shader = ContinuumShaders.PLANET_BAKE;
        if (shader == null) return 0;
        return lookup(clouds, seed, MAX_CLOUDS, () -> bake(shader, 1.0f, seed, 1.0f, 6, 33325, 6403));   // R16F
    }

    /** Binds the maps to the units the planet shader reads, then restores the active unit. Pass 0 for "none". */
    public static void bind(int terrainTex, int cloudTex) {
        if (terrainTex != 0) {
            RenderSystem.activeTexture(33984 + TERRAIN_UNIT);
            GL11.glBindTexture(CUBE_MAP, terrainTex);
        }
        if (cloudTex != 0) {
            RenderSystem.activeTexture(33984 + CLOUD_UNIT);
            GL11.glBindTexture(CUBE_MAP, cloudTex);
        }
        RenderSystem.activeTexture(33984);
    }

    /**
     * The cached map for a key, or a new one. Returns 0 (so the planet is drawn live for now) when a new map is not
     * allowed yet: another was made a moment ago, or the cache is full of maps in use.
     */
    private static <K> int lookup(Map<K, Entry> cache, K key, int max, java.util.function.IntSupplier make) {
        long now = System.nanoTime();
        Entry have = cache.get(key);
        if (have != null) {
            have.lastUse = now;
            return have.tex;
        }
        if (now - lastBake < BAKE_INTERVAL) return 0;
        if (cache.size() >= max) {
            Iterator<Entry> oldestFirst = cache.values().iterator();   // least recently used first
            Entry oldest = oldestFirst.next();
            if (now - oldest.lastUse < IN_USE) return 0;
            GL11.glDeleteTextures(oldest.tex);
            oldestFirst.remove();
        }
        int tex = make.getAsInt();
        lastBake = System.nanoTime();
        cache.put(key, new Entry(tex, lastBake));
        return tex;
    }

    private static int bake(ShaderInstance shader, float mode, float seed, float scale, int octaves, int internalFormat,
                            int format) {
        int previousFbo = GL11.glGetInteger(36006);   // GL_DRAW_FRAMEBUFFER_BINDING
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        RenderSystem.activeTexture(33984 + TERRAIN_UNIT);
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(CUBE_MAP, tex);
        for (int face = 0; face < 6; face++) {
            GL11.glTexImage2D(FACE_0 + face, 0, internalFormat, SIZE, SIZE, 0, format, GL11.GL_FLOAT, 0L);
        }
        GL11.glTexParameteri(CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, 33071);
        GL11.glTexParameteri(CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, 33071);
        GL11.glTexParameteri(CUBE_MAP, 32882, 33071);   // wrap R
        GL11.glEnable(34895);                           // seamless filtering across the cube's edges
        RenderSystem.activeTexture(33984);

        int fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(36160, fbo);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GlStateManager._viewport(0, 0, SIZE, SIZE);

        shader.safeGetUniform("Mode").set(mode);
        shader.safeGetUniform("Seed").set(seed);
        shader.safeGetUniform("Scale").set(scale);
        shader.safeGetUniform("Octaves").set((float) octaves);
        for (int face = 0; face < 6; face++) {
            GL30.glFramebufferTexture2D(36160, 36064, FACE_0 + face, tex, 0);
            shader.safeGetUniform("Face").set((float) face);
            SceneRenderer.drawFullQuad(shader);
        }

        GL30.glDeleteFramebuffers(fbo);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        GlStateManager._glBindFramebuffer(36160, previousFbo);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        return tex;
    }
}
