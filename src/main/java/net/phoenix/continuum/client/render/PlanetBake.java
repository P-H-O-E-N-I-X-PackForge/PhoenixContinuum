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

public final class PlanetBake {

    private static final int SIZE = 512;
    
    private static final int MAX_TERRAIN = 12;
    private static final int MAX_CLOUDS = 16;
    
    private static final long BAKE_INTERVAL = 8_000_000L;
    
    private static final long IN_USE = 1_000_000_000L;

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

    private static final class Pending {
        final int tex;
        int face;

        Pending(int tex) {
            this.tex = tex;
        }
    }

    private static final Map<Object, Pending> pending = new LinkedHashMap<>();
    private static boolean paused;
    private static boolean burst;
    private static int denied;

    public static void burst(boolean value) {
        burst = value;
    }

    public static int takeDenied() {
        int count = denied;
        denied = 0;
        return count;
    }

    public static void pause(boolean value) {
        paused = value;
    }

    private static final Map<TerrainKey, Entry> terrain = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<Float, Entry> clouds = new LinkedHashMap<>(16, 0.75f, true);
    private static long lastBake;

    private PlanetBake() {}

    public static int terrain(float seed, float scale, int octaves) {
        ShaderInstance shader = ContinuumShaders.PLANET_BAKE;
        if (shader == null) return 0;
        return lookup(terrain, new TerrainKey(seed, scale, octaves), MAX_TERRAIN, shader, 0.0f, seed, scale, octaves,
                34842, 6408);
    }

    public static int clouds(float seed) {
        ShaderInstance shader = ContinuumShaders.PLANET_BAKE;
        if (shader == null) return 0;
        return lookup(clouds, seed, MAX_CLOUDS, shader, 1.0f, seed, 1.0f, 6, 33325, 6403);
    }

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

    private static <K> int lookup(Map<K, Entry> cache, K key, int max, ShaderInstance shader, float mode, float seed,
                                  float scale, int octaves, int internalFormat, int format) {
        long now = System.nanoTime();
        Entry have = cache.get(key);
        if (have != null) {
            have.lastUse = now;
            return have.tex;
        }
        if (paused || (!burst && now - lastBake < BAKE_INTERVAL)) {
            denied++;
            return 0;
        }

        Pending bake = pending.get(key);
        if (bake == null) {
            if (cache.size() >= max) {
                Iterator<Entry> oldestFirst = cache.values().iterator();
                Entry oldest = oldestFirst.next();
                if (now - oldest.lastUse < IN_USE) return 0;
                GL11.glDeleteTextures(oldest.tex);
                oldestFirst.remove();
            }
            
            while (pending.size() >= 16) {
                Iterator<Pending> stale = pending.values().iterator();
                GL11.glDeleteTextures(stale.next().tex);
                stale.remove();
            }
            bake = new Pending(allocate(internalFormat, format));
            pending.put(key, bake);
        }

        bakeFace(shader, bake.tex, mode, seed, scale, octaves, bake.face);
        bake.face++;
        lastBake = System.nanoTime();
        if (bake.face < 6) {
            denied++;
            return 0;
        }

        pending.remove(key);
        cache.put(key, new Entry(bake.tex, lastBake));
        return bake.tex;
    }

    private static int allocate(int internalFormat, int format) {
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
        GL11.glTexParameteri(CUBE_MAP, 32882, 33071);
        GL11.glEnable(34895);
        RenderSystem.activeTexture(33984);
        return tex;
    }

    private static void bakeFace(ShaderInstance shader, int tex, float mode, float seed, float scale, int octaves,
                                 int face) {
        int previousFbo = GL11.glGetInteger(36006);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

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
        GL30.glFramebufferTexture2D(36160, 36064, FACE_0 + face, tex, 0);
        shader.safeGetUniform("Face").set((float) face);
        SceneRenderer.drawFullQuad(shader);

        GL30.glDeleteFramebuffers(fbo);
        if (cull) RenderSystem.enableCull();
        else RenderSystem.disableCull();
        RenderSystem.depthMask(depthMask);
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        if (blend) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
        GlStateManager._glBindFramebuffer(36160, previousFbo);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
