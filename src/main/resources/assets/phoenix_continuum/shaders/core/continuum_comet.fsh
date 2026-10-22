#version 150

// A comet seen against the sky or the map: a bright coma round the nucleus, a long straight blue ion tail pushed directly
// away from the star, and a broader, curved, warmer dust tail trailing behind it. Both tails thin out and fade with
// distance, are streaked along their length, and grow stronger the closer the comet is to the star (Activity).
// Additive and loop-free. TailDir is the direction away from the star on the billboard.

uniform float Time;
uniform float Extent;       // billboard half-size, in nucleus radii
uniform float Voxel;        // unused
uniform float Core;         // radius of the nucleus in billboard units
uniform float Detail;       // 2 (low) - 4 (high)
uniform vec3 Tint;          // the dust colour
uniform vec2 TailDir;       // away from the star, on the billboard
uniform float Activity;     // 0..1

in vec2 vLocal;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 q = vLocal * Extent;
    float r = length(q) / Core;
    if (r < 0.9) {
        fragColor = vec4(0.0);     // the nucleus mesh covers this
        return;
    }
    vec2 t = normalize(TailDir);
    vec2 side = vec2(-t.y, t.x);
    float along = dot(q, t);
    float across = dot(q, side);
    float reach = Extent * (0.35 + 0.55 * Activity);                  // how long the tails run

    vec3 ionCol = vec3(0.45, 0.72, 1.0);
    vec3 dustCol = mix(Tint, vec3(1.0, 0.86, 0.6), 0.5);
    vec3 col = vec3(0.0);

    // ---- coma: a soft glow round the nucleus, bigger when active -----------------------------------------------------
    float coma = exp(-(r - 1.0) / (1.6 + 2.2 * Activity));
    col += mix(ionCol, vec3(1.0), 0.45) * coma * (0.35 + 0.55 * Activity);

    if (along > 0.0) {
        float a = along / reach;
        // ---- ion tail: straight, narrow, blue, streaked and rippling ------------------------------------------------
        float w = 0.45 + 0.05 * along;
        float streak = 0.55 + 0.45 * vnoise(vec2(across * 2.6 / max(w, 0.2), along * 0.35 - Time * 1.6));
        float ion = exp(-(across * across) / (w * w)) * exp(-a * 2.2) * streak;
        col += ionCol * ion * (0.6 + 2.2 * Activity) * smoothstep(0.0, 0.08, a);

        // ---- dust tail: wider, curved back along the orbit, yellow-white ---------------------------------------------
        float bend = 0.06 * along * a * (1.0 + 0.4 * Activity);        // the curve
        float dw = 0.9 + 0.22 * along;
        float dx = across - bend;
        float grain = 0.7 + 0.3 * vnoise(vec2(dx * 1.4 / max(dw, 0.4), along * 0.18 - Time * 0.3));
        float dust = exp(-(dx * dx) / (dw * dw)) * exp(-a * 1.7) * grain;
        col += dustCol * dust * (0.25 + 0.9 * Activity) * smoothstep(0.0, 0.12, a);
    }

    col = 1.0 - exp(-col * 1.2);
    float edge = 1.0 - smoothstep(0.7, 1.0, max(abs(vLocal.x), abs(vLocal.y)));
    fragColor = vec4(col * edge, 0.0);
}
