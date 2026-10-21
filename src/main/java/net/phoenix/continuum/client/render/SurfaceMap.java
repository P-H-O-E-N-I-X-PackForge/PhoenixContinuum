package net.phoenix.continuum.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.phoenix.continuum.PhoenixContinuum;
import net.phoenix.continuum.data.ContinuumBody;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.HashMap;
import java.util.Map;

/**
 * A flat, top-down picture of a world, painted on the CPU from the same parameters the 3D renderer uses (palette,
 * ocean level, polar ice, cloud cover, gas bands). It is an equirectangular map: the left and right edges are the same
 * meridian. Generated once per body and kept as a texture.
 */
public final class SurfaceMap {

    public static final int WIDTH = 512;
    public static final int HEIGHT = 256;

    private static final Map<ResourceLocation, ResourceLocation> TEXTURES = new HashMap<>();

    private SurfaceMap() {}

    /** The texture for a body, generating it on first use. Call from the render thread. */
    public static ResourceLocation texture(ContinuumBody body) {
        ResourceLocation existing = TEXTURES.get(body.id());
        if (existing != null) return existing;

        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        paint(image, body.params());
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        ResourceLocation id = PhoenixContinuum.id("surface/" + body.id().getNamespace() + "/" +
                body.id().getPath().replace('/', '_'));
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(body.id(), id);
        return id;
    }

    /** Drops the cached textures, for example when the body data is reloaded. */
    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(manager::release);
        TEXTURES.clear();
    }

    private static void paint(NativeImage image, PlanetParams p) {
        float seedOffset = p.seed() * 7.13f;
        float freq = Math.max(0.6f, p.scale());

        for (int y = 0; y < HEIGHT; y++) {
            double lat = (0.5 - (y + 0.5) / HEIGHT) * Math.PI;
            double cosLat = Math.cos(lat);
            double sinLat = Math.sin(lat);
            for (int x = 0; x < WIDTH; x++) {
                double lon = (x + 0.5) / WIDTH * Math.PI * 2.0;
                float px = (float) (cosLat * Math.cos(lon)) + seedOffset;
                float py = (float) sinLat + seedOffset * 0.37f;
                float pz = (float) (cosLat * Math.sin(lon)) + seedOffset * 0.71f;

                int rgb = p.gasGiant() ? gas(p, px, py, pz, (float) lat, freq) : rocky(p, px, py, pz, (float) lat, freq);
                // NativeImage wants ABGR
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
    }

    private static int rocky(PlanetParams p, float x, float y, float z, float lat, float freq) {
        float h = fbm(x * freq, y * freq, z * freq, 5);
        int color;
        if (h < p.oceanLevel()) {
            float t = p.oceanLevel() <= 0 ? 1.0f : h / p.oceanLevel();
            color = mix(p.oceanDeep(), p.oceanShallow(), t * t);
        } else {
            float t = (h - p.oceanLevel()) / Math.max(0.001f, 1.0f - p.oceanLevel());
            // fine grain so large land masses are not flat colour
            float grain = fbm(x * freq * 6.0f + 13.0f, y * freq * 6.0f, z * freq * 6.0f, 3) - 0.5f;
            t = clamp(t + grain * 0.22f * p.bump(), 0.0f, 1.0f);
            color = t < 0.5f ? mix(p.low(), p.mid(), t * 2.0f) : mix(p.mid(), p.high(), (t - 0.5f) * 2.0f);
        }

        if (p.polarIce() > 0.0f) {
            float edge = 1.0f - p.polarIce();
            float wobble = (fbm(x * 3.0f, y * 3.0f, z * 3.0f, 3) - 0.5f) * 0.18f;
            float cap = smooth(edge - 0.04f, edge + 0.06f, Math.abs(lat) / 1.5708f + wobble);
            color = mix(color, 0xF1F5FF, cap);
        }

        if (p.cloudCover() > 0.0f) {
            float c = fbm(x * freq * 1.7f + 91.0f, y * freq * 1.7f, z * freq * 1.7f + 17.0f, 4);
            float cloud = smooth(1.0f - p.cloudCover() * 0.85f - 0.1f, 1.0f - p.cloudCover() * 0.85f + 0.2f, c);
            color = mix(color, 0xFFFFFF, cloud * 0.8f);
        }
        return color;
    }

    private static int gas(PlanetParams p, float x, float y, float z, float lat, float freq) {
        float turbulence = fbm(x * freq * 2.0f, y * freq * 0.6f, z * freq * 2.0f, 4) - 0.5f;
        float bands = 0.5f + 0.5f * (float) Math.sin(lat * (6.0f + freq * 2.0f) + turbulence * 5.0f);
        float detail = fbm(x * freq * 5.0f, y * freq * 1.2f, z * freq * 5.0f, 3) - 0.5f;
        float t = clamp(bands + detail * 0.35f, 0.0f, 1.0f);
        return t < 0.5f ? mix(p.low(), p.mid(), t * 2.0f) : mix(p.mid(), p.high(), (t - 0.5f) * 2.0f);
    }

    // ------------------------------------------------------------------ noise

    private static float fbm(float x, float y, float z, int octaves) {
        float sum = 0.0f;
        float amp = 0.5f;
        float norm = 0.0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, z);
            norm += amp;
            x *= 2.03f;
            y *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static float noise(float x, float y, float z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float fx = x - xi;
        float fy = y - yi;
        float fz = z - zi;
        float u = fx * fx * (3.0f - 2.0f * fx);
        float v = fy * fy * (3.0f - 2.0f * fy);
        float w = fz * fz * (3.0f - 2.0f * fz);

        float c000 = hash(xi, yi, zi);
        float c100 = hash(xi + 1, yi, zi);
        float c010 = hash(xi, yi + 1, zi);
        float c110 = hash(xi + 1, yi + 1, zi);
        float c001 = hash(xi, yi, zi + 1);
        float c101 = hash(xi + 1, yi, zi + 1);
        float c011 = hash(xi, yi + 1, zi + 1);
        float c111 = hash(xi + 1, yi + 1, zi + 1);

        float x00 = c000 + (c100 - c000) * u;
        float x10 = c010 + (c110 - c010) * u;
        float x01 = c001 + (c101 - c001) * u;
        float x11 = c011 + (c111 - c011) * u;
        float y0 = x00 + (x10 - x00) * v;
        float y1 = x01 + (x11 - x01) * v;
        return y0 + (y1 - y0) * w;
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    // ------------------------------------------------------------------ colour

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float smooth(float a, float b, float x) {
        float t = clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static int mix(int a, int b, float t) {
        t = clamp(t, 0.0f, 1.0f);
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
