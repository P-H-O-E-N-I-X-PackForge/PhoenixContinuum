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
        var system = net.phoenix.continuum.data.ContinuumData.system(body.system());
        switch (body.type()) {
            case STAR -> paintStar(image, body, system);
            case BLACK_HOLE -> paintHole(image, system);
            default -> paint(image, body.params());
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        ResourceLocation id = PhoenixContinuum.id("surface/" + body.id().getNamespace() + "/" +
                body.id().getPath().replace('/', '_'));
        Minecraft.getInstance().getTextureManager().register(id, texture);
        TEXTURES.put(body.id(), id);
        return id;
    }

    /** Forgets one body picture so it is painted again (after its parameters changed). */
    public static void invalidate(ResourceLocation body) {
        ResourceLocation texture = TEXTURES.remove(body);
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
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

    /** A star seen from above: boiling granulation, darker spots, a bright limb at the edge of the disc. */
    private static void paintStar(NativeImage image, ContinuumBody body,
                                  net.phoenix.continuum.data.@org.jetbrains.annotations.Nullable ContinuumSystem system) {
        int base = system != null ? system.starColor() : 0xfff1cf;
        int dark = mix(base, 0x200800, 0.72f);
        float seed = body.params().seed() * 3.7f;
        for (int y = 0; y < HEIGHT; y++) {
            double lat = (0.5 - (y + 0.5) / HEIGHT) * Math.PI;
            for (int x = 0; x < WIDTH; x++) {
                double lon = (x + 0.5) / WIDTH * Math.PI * 2.0;
                float px = (float) (Math.cos(lat) * Math.cos(lon)) * 3.0f + seed;
                float py = (float) Math.sin(lat) * 3.0f;
                float pz = (float) (Math.cos(lat) * Math.sin(lon)) * 3.0f - seed;
                float cells = fbm(px, py, pz, 5);
                float spots = smooth(0.62f, 0.72f, fbm(px * 0.5f + 40.0f, py * 0.5f, pz * 0.5f, 3));
                float t = clamp(0.35f + cells * 0.85f, 0.0f, 1.0f);
                int color = mix(dark, mix(base, 0xFFFFFF, 0.35f), t);
                color = mix(color, dark, spots * 0.7f);
                put(image, x, y, color);
            }
        }
    }

    /** A black hole face-on: horizon, photon ring, and a spiralling accretion disk in the system's hole colours. */
    private static void paintHole(NativeImage image,
                                  net.phoenix.continuum.data.@org.jetbrains.annotations.Nullable ContinuumSystem system) {
        var style = system != null ? system.hole() : net.phoenix.continuum.data.ContinuumSystem.HoleStyle.DEFAULT;
        int hot = style.hotColor() >= 0 ? style.hotColor() : 0xFFE2A0;
        int cool = style.coolColor() >= 0 ? style.coolColor() : 0xF25214;
        float size = Math.max(0.5f, style.diskSize());
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                float u = (x + 0.5f - WIDTH / 2.0f) / (HEIGHT / 2.0f);
                float v = (y + 0.5f - HEIGHT / 2.0f) / (HEIGHT / 2.0f);
                float r = (float) Math.sqrt(u * u + v * v);
                float angle = (float) Math.atan2(v, u);

                int color = hash((int) (x * 0.7f), y, 5) > 0.9985f ? 0xBBC8FF : 0x02030A;
                float inner = 0.30f;
                float outer = 0.95f * size;
                if (r < inner * 0.86f) {
                    color = 0x000000;
                } else if (r < inner) {
                    color = mix(0x000000, hot, (r - inner * 0.86f) / (inner * 0.14f));
                } else if (r < outer) {
                    float t = (r - inner) / (outer - inner);
                    float swirl = fbm((float) Math.cos(angle + t * 5.0f) * 2.0f, (float) Math.sin(angle + t * 5.0f) * 2.0f,
                            t * 6.0f, 4);
                    float glow = clamp((1.0f - t) * (0.55f + swirl * 0.9f), 0.0f, 1.0f);
                    color = mix(0x02030A, mix(cool, hot, 1.0f - t), glow);
                }
                put(image, x, y, color);
            }
        }
    }

    private static void put(NativeImage image, int x, int y, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
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
