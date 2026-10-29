package net.phoenix.continuum.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * The two tables the faster black hole shader reads (see {@link HoleBake}). They are worked out on another thread the first
 * time a black hole is wanted and uploaded when they are ready; until then, and if anything goes wrong, the black hole is
 * drawn by the classic shader, which needs no tables.
 */
final class BlackHoleTables {

    /** A texture unit nothing else uses while the tables are uploaded, so the cached binding of unit 0 is left alone. */
    private static final int UPLOAD_UNIT = 33984 + 13;

    private record Data(float[] orbit, byte[] noise) {}

    private static CompletableFuture<Data> pending;
    private static int orbit;
    private static int noise;
    private static boolean failed;

    private BlackHoleTables() {}

    /** Whether the tables are on the GPU; starts building them the first time it is asked. */
    static boolean ready() {
        if (failed) return false;
        if (orbit != 0) return true;
        if (pending == null) {
            pending = CompletableFuture.supplyAsync(() -> new Data(HoleBake.orbit(), HoleBake.diskNoise()));
            return false;
        }
        if (!pending.isDone()) return false;
        try {
            upload(pending.join());
        } catch (RuntimeException | LinkageError e) {
            failed = true;
        }
        pending = null;
        return orbit != 0;
    }

    static int orbit() {
        return orbit;
    }

    static int noise() {
        return noise;
    }

    private static void upload(Data data) {
        RenderSystem.activeTexture(UPLOAD_UNIT);
        GL11.glGetError();
        try {
            orbit = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, orbit);
            FloatBuffer floats = BufferUtils.createFloatBuffer(data.orbit().length);
            floats.put(data.orbit()).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG32F, HoleBake.ORBIT_W, HoleBake.ORBIT_H, 0, GL30.GL_RG,
                    GL11.GL_FLOAT, floats);
            parameters(33071);

            noise = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, noise);
            ByteBuffer bytes = BufferUtils.createByteBuffer(data.noise().length);
            bytes.put(data.noise()).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, HoleBake.NOISE_W, HoleBake.NOISE_H, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, bytes);
            parameters(10497);

            if (GL11.glGetError() != 0) {
                GL11.glDeleteTextures(orbit);
                GL11.glDeleteTextures(noise);
                orbit = 0;
                noise = 0;
                failed = true;
            }
        } finally {
            RenderSystem.activeTexture(33984);
        }
    }

    /** Linear filtering; the first table clamps at its edges, the second wraps round the disk (x) and clamps in radius. */
    private static void parameters(int wrapS) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
    }
}
