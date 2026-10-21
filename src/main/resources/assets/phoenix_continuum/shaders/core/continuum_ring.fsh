#version 150

// A planetary ring seen as the thin sheet of ice and dust it is: many fine ringlets with a few real gaps, a colour that
// drifts along the radius, slow clumping round the circle, and the lighting of a translucent sheet (bright and chalky when
// the sun and the viewer are on the same face, dim and glowing through its thin parts when they are on opposite faces).
// The planet's shadow falls across it with a soft edge.

uniform float Time;
uniform vec3 SunDir;
uniform vec3 PlanetCenter;
uniform float PlanetRadius;
uniform float Inner;
uniform float Opacity;
uniform float Seed;
uniform vec3 Color1;
uniform vec3 Color2;

in vec2 vPos;
in vec3 vView;

out vec4 fragColor;

float hash1(float x) {
    return fract(sin(x * 127.1 + 311.7) * 43758.5453);
}

float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), f);
}

float valh2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valn2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(valh2(i), valh2(i + vec2(1.0, 0.0)), f.x), mix(valh2(i + vec2(0.0, 1.0)), valh2(i + vec2(1.0, 1.0)), f.x), f.y);
}

// a gap of the given centre and half width, softly edged
float gap(float t, float centre, float halfWidth) {
    return 1.0 - smoothstep(halfWidth * 0.6, halfWidth, abs(t - centre));
}

void main() {
    float r = length(vPos);
    if (r > 1.0 || r < Inner) discard;
    float t = (r - Inner) / (1.0 - Inner);
    float px = max(fwidth(t), 1e-4);                 // how much of the ring one pixel covers

    // ---- ringlets: broad bands, then finer ones, then fine grain that fades out when pixels are bigger than it ----
    float broad = noise1(t * 9.0 + Seed * 13.0);
    float mid = noise1(t * 41.0 + Seed * 5.0);
    float fineAmount = 1.0 - smoothstep(0.05, 0.4, px * 180.0);
    float fine = noise1(t * 180.0 + Seed * 3.0) * fineAmount + 0.5 * (1.0 - fineAmount);
    float grainAmount = 1.0 - smoothstep(0.05, 0.4, px * 520.0);
    float grain = noise1(t * 520.0 + Seed) * grainAmount + 0.5 * (1.0 - grainAmount);
    float bands = broad * 0.45 + mid * 0.30 + fine * 0.18 + grain * 0.07;
    // ringlets are thin: push the contrast so there are clear dense lines and thin spaces
    float density = smoothstep(0.18, 0.78, bands);

    // ---- gaps: one wide division, a few narrow ones, whose places depend on the seed ----
    float wide = 0.50 + 0.12 * (hash1(Seed * 3.3) - 0.5);
    float g = gap(t, wide, 0.045);
    g = max(g, 0.85 * gap(t, 0.22 + 0.1 * hash1(Seed * 7.1), 0.012));
    g = max(g, 0.80 * gap(t, 0.74 + 0.1 * hash1(Seed * 9.7), 0.010));
    g = max(g, 0.65 * gap(t, 0.88 + 0.05 * hash1(Seed * 5.3), 0.008));
    density *= 1.0 - g * 0.96;

    // the inner edge fades in over a short distance, the outer edge ends sharper
    density *= smoothstep(0.0, 0.07, t) * (1.0 - smoothstep(0.965, 1.0, t));

    // slow clumps round the circle: a little denser in places, the way the real thing is
    float ang = atan(vPos.y, vPos.x);
    float clump = 0.82 + 0.18 * valn2(vec2(ang * 3.0 + Seed, t * 14.0));
    density *= clump;
    if (density < 0.015) discard;

    // ---- colour: drifts between the two along the radius, with some variation inside the bands ----
    float drift = smoothstep(0.0, 1.0, t * 0.7 + (broad - 0.5) * 0.7 + 0.15);
    vec3 col = mix(Color1, Color2, clamp(drift, 0.0, 1.0));
    col *= 0.78 + 0.44 * mid;

    // ---- light: the ring is a translucent sheet, so the face matters ----
    vec3 N = normalize(cross(dFdx(vView), dFdy(vView)));
    vec3 L = normalize(SunDir);
    vec3 V = normalize(-vView);
    float sunSide = dot(N, L);
    float eyeSide = dot(N, V);
    float sameFace = smoothstep(-0.08, 0.08, sunSide * eyeSide);
    float lit = abs(sunSide);
    // reflecting: bright, strongest when the sun is high above the sheet
    float reflected = 0.35 + 0.85 * smoothstep(0.0, 0.6, lit);
    // transmitting: dim, but thin places let the light through, so gaps and fine ringlets glow
    float scatter = pow(max(dot(-V, L), 0.0), 5.0);
    float transmitted = (0.08 + 0.55 * (1.0 - density)) * (0.4 + 1.4 * scatter) * smoothstep(0.0, 0.35, lit + 0.12);
    float light = mix(transmitted, reflected, sameFace);

    // the planet's shadow: a soft-edged cylinder along the sun's direction
    vec3 toPlanet = PlanetCenter - vView;
    float ahead = dot(toPlanet, L);
    float perp = length(toPlanet - L * ahead);
    float shadow = ahead > 0.0 ? smoothstep(PlanetRadius * 0.92, PlanetRadius * 1.04, perp) : 1.0;

    vec3 outColor = col * light * (0.04 + 0.96 * shadow) * 1.15;
    // seen edge-on the sheet thins to a line instead of a flat smear
    float face = smoothstep(0.0, 0.3, abs(eyeSide));
    float alpha = clamp(density * Opacity * mix(0.45, 1.0, face), 0.0, 1.0);
    fragColor = vec4(outColor, alpha);
}
