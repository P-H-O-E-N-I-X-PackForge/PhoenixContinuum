package net.phoenix.continuum.client.render;

/**
 * The tables the black hole shader looks up instead of working them out for every pixel, built once:
 * <ul>
 * <li>the orbit table: for a ray that passes the hole at impact parameter rho (the pixel's distance from the middle, in
 * shadow radii) it holds u = 1/r and du/dphi at every angle phi round the orbit, so "where is this ray when it is phi
 * round" is one lookup instead of a few dozen integration steps;</li>
 * <li>the disk noise: the turbulence of the accretion disk on a polar grid. The disk turns at a different rate at every
 * radius, which only shifts this picture sideways by an amount that depends on r, so the noise itself never changes.</li>
 * </ul>
 */
final class HoleBake {

    public static final float RS = 0.3849f;
    public static final int ORBIT_W = 512;
    public static final int ORBIT_H = 1024;
    public static final float PHI_MAX = (float) (3.0 * Math.PI + 0.3);
    public static final float RHO_MAX = 16.0f;

    public static final int NOISE_W = 512;
    public static final int NOISE_H = 2048;
    public static final float NOISE_R0 = 1.0f;
    public static final float NOISE_R1 = 13.5f;

    private HoleBake() {}

    /** Two floats (u, du/dphi) per texel, row after row; row j is the impact parameter RHO_MAX * ((j + 0.5) / H)^2. */
    static float[] orbit() {
        float[] out = new float[ORBIT_W * ORBIT_H * 2];
        final int sub = 8;
        double dphi = (double) PHI_MAX / ORBIT_W;
        double h = dphi / sub;
        double horizon = 1.0 / RS;
        for (int j = 0; j < ORBIT_H; j++) {
            double y = (j + 0.5) / ORBIT_H;
            double rho = Math.max(RHO_MAX * y * y, 0.01);
            double u = 0.0;
            double v = 1.0 / rho;
            int state = 0; // 0 flying, 1 fell in, 2 escaped
            for (int i = 0; i < ORBIT_W; i++) {
                // integrate to the middle of the cell, which is where this texel is sampled, record, then on to the next
                for (int half = 0; half < 2; half++) {
                    if (half == 1) {
                        int o = (j * ORBIT_W + i) * 2;
                        if (state == 1) {
                            out[o] = (float) (horizon * 1.2);
                            out[o + 1] = 0.0f;
                        } else if (state == 2) {
                            out[o] = 0.0f;
                            out[o + 1] = -1.0f;
                        } else {
                            out[o] = (float) u;
                            out[o + 1] = (float) v;
                        }
                    }
                    for (int s = 0; s < sub / 2 && state == 0; s++) {
                        double k1u = v, k1v = -u + 1.5 * RS * u * u;
                        double u2 = u + 0.5 * h * k1u, v2 = v + 0.5 * h * k1v;
                        double k2u = v2, k2v = -u2 + 1.5 * RS * u2 * u2;
                        double u3 = u + 0.5 * h * k2u, v3 = v + 0.5 * h * k2v;
                        double k3u = v3, k3v = -u3 + 1.5 * RS * u3 * u3;
                        double u4 = u + h * k3u, v4 = v + h * k3v;
                        double k4u = v4, k4v = -u4 + 1.5 * RS * u4 * u4;
                        u += h / 6.0 * (k1u + 2 * k2u + 2 * k3u + k4u);
                        v += h / 6.0 * (k1v + 2 * k2v + 2 * k3v + k4v);
                        if (u > horizon) state = 1;
                        else if (u < 0.0) state = 2;
                    }
                }
            }
        }
        return out;
    }

    // -- the disk noise, the same functions the shader used -------------------------------------------------------

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    private static float hash(float x, float y) {
        float px = fract(x * 123.34f);
        float py = fract(y * 456.21f);
        float d = px * (px + 45.32f) + py * (py + 45.32f);
        px += d;
        py += d;
        return fract(px * py);
    }

    private static float mod(float x, float p) {
        return x - p * (float) Math.floor(x / p);
    }

    private static float pnoise(float x, float y, float period) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3.0f - 2.0f * fx);
        fy = fy * fy * (3.0f - 2.0f * fy);
        float x0 = mod(ix, period);
        float x1 = mod(ix + 1.0f, period);
        float a = hash(x0, iy) + (hash(x1, iy) - hash(x0, iy)) * fx;
        float b = hash(x0, iy + 1.0f) + (hash(x1, iy + 1.0f) - hash(x0, iy + 1.0f)) * fx;
        return a + (b - a) * fy;
    }

    private static float pfbm(float ang, float r, float period) {
        float px = ang * period / (float) (2.0 * Math.PI);
        float py = r;
        float v = 0.0f, a = 0.5f, total = 0.0f;
        for (int i = 0; i < 4; i++) {
            v += a * pnoise(px, py, period);
            total += a;
            px *= 2.0f;
            py = py * 2.0f + 11.7f;
            period *= 2.0f;
            a *= 0.5f;
        }
        return v / total * 0.9375f;
    }

    /** RGB bytes per texel: coarse clumps, filaments and fine grain, on a polar grid (x round the disk, y the radius). */
    static byte[] diskNoise() {
        byte[] out = new byte[NOISE_W * NOISE_H * 4];
        float twoPi = (float) (2.0 * Math.PI);
        for (int j = 0; j < NOISE_H; j++) {
            float r = NOISE_R0 + (NOISE_R1 - NOISE_R0) * (j + 0.5f) / NOISE_H;
            for (int i = 0; i < NOISE_W; i++) {
                float ang = twoPi * (i + 0.5f) / NOISE_W;
                float coarse = pfbm(ang, r * 2.6f, 5.0f);
                float filaments = pnoise(ang * 13.0f / twoPi, r * 24.0f, 13.0f);
                float fine = pnoise(ang * 37.0f / twoPi, r * 58.0f, 37.0f);
                int o = (j * NOISE_W + i) * 4;
                out[o] = (byte) Math.round(Math.max(0, Math.min(1, coarse)) * 255.0f);
                out[o + 1] = (byte) Math.round(Math.max(0, Math.min(1, filaments)) * 255.0f);
                out[o + 2] = (byte) Math.round(Math.max(0, Math.min(1, fine)) * 255.0f);
                out[o + 3] = (byte) 255;
            }
        }
        return out;
    }
}
