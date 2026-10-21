#version 150

// Megastructure around a star: a swarm of reflective panels in tilted rings, a bright rim where they face the star, and
// shafts of the star's light leaking out through the gaps where panels are missing.
//
// The first ring lies in the system's orbital plane (DiskNormal is that plane's normal in view space); the others are
// tipped away from it so the swarm reads as a loose shell rather than a single hoop. Panels are cells of an angular grid
// with a few rows; a hash decides which are missing, which glint, and the shafts follow the missing ones.
// Additive and loop-free apart from three fixed ring layers, so it is cheap enough to draw straight to the screen.

uniform float Time;
uniform float Extent;       // billboard half-size, in star radii
uniform float Voxel;        // unused: the swarm is drawn smooth in every mode
uniform float Core;         // radius of the star body in billboard units
uniform float Detail;       // 2 (low) - 4 (high)
uniform vec3 Tint;          // star colour
uniform vec3 DiskNormal;    // orbital plane normal, view space

in vec2 vLocal;
out vec4 fragColor;

const float PI = 3.14159265;
const float PANELS = 96.0;     // panels around a ring
const float ROWS = 3.0;        // rows of panels across a ring's width
const float MISSING = 0.18;    // fraction of panels that are not there

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float pnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    return mix(mix(hash(vec2(x0, i.y)), hash(vec2(x1, i.y)), f.x),
               mix(hash(vec2(x0, i.y + 1.0)), hash(vec2(x1, i.y + 1.0)), f.x), f.y);
}

// the plane of a ring as screen axes: minor axis, major axis, and how squashed it looks
void planeAxes(vec3 n, out vec2 mn, out vec2 mj, out float cosI) {
    cosI = clamp(abs(n.z), 0.16, 1.0);
    mn = length(n.xy) > 1e-3 ? normalize(n.xy) : vec2(0.0, 1.0);
    mj = vec2(-mn.y, mn.x);
}

// One ring of panels. q is the position in star radii on the billboard; the ring occupies r0..r1 in its own plane.
// Returns the light it gives off; `gap` is 1 where a panel is missing at this angle.
vec3 ring(vec2 q, vec3 n, float r0, float r1, float seed, float spin, out float gap) {
    vec2 mn, mj;
    float cosI;
    planeAxes(n, mn, mj, cosI);

    vec2 e = vec2(dot(q, mj), dot(q, mn) / cosI);
    float re = length(e);
    gap = 0.0;
    if (re < r0 - 0.15 || re > r1 + 0.15) return vec3(0.0);

    float ang = atan(e.y, e.x) / (2.0 * PI) + 0.5 + spin;     // 0..1 round the ring
    float t = clamp((re - r0) / (r1 - r0), 0.0, 0.999);
    float row = floor(t * ROWS);
    float rowFrac = fract(t * ROWS);
    float cell = floor(ang * PANELS);
    float cellFrac = fract(ang * PANELS);

    float h = hash(vec2(cell + row * 131.0, seed));
    float present = step(MISSING, h);
    gap = 1.0 - present;

    float inBand = smoothstep(r0 - 0.12, r0, re) * (1.0 - smoothstep(r1, r1 + 0.12, re));
    float seamX = smoothstep(0.0, 0.07, min(cellFrac, 1.0 - cellFrac));
    float seamY = smoothstep(0.0, 0.10, min(rowFrac, 1.0 - rowFrac));
    float body = present * inBand * seamX * seamY;

    // panels turn slowly, so each one catches the star for a moment and flashes
    float glint = pow(max(0.0, cos(h * 40.0 + Time * (0.5 + 0.6 * h))), 18.0);
    float inner = 1.0 - t;                       // the inner edge faces the star
    vec3 hot = mix(Tint, vec3(1.0), 0.55);
    vec3 col = vec3(0.07, 0.08, 0.12)
            + hot * (0.30 * inner * inner + 1.1 * glint)
            + vec3(0.9, 0.95, 1.0) * pow(inner, 7.0) * 0.8;      // bright rim light on the star side
    return col * body;
}

void main() {
    vec2 q = vLocal * Extent;
    float rs = length(q) / Core;
    if (rs < 0.98) {
        fragColor = vec4(0.0);   // the star mesh covers this
        return;
    }

    vec3 hot = mix(Tint, vec3(1.0), 0.5);
    vec3 col = vec3(0.0);

    // ---- the star's glare ----------------------------------------------------------------------------------------
    float d = max(rs - 1.0, 0.0);
    col += hot * (0.85 * exp(-d * 1.5) + 0.26 / (1.0 + d * d * 1.5));

    // ---- panels -------------------------------------------------------------------------------------------------
    vec3 n = normalize(DiskNormal);
    vec3 n2 = normalize(vec3(0.55 * n.y + 0.45, -0.7 * n.x, 0.5 * n.z + 0.55));
    vec3 n3 = normalize(vec3(-0.6 * n.x - 0.35, 0.85, 0.6 * n.z));

    float gapA;
    float gapB;
    float gapC = 0.0;
    float rA0 = 3.3;
    float rA1 = 4.2;
    col += ring(q, n, rA0, rA1, 1.0, Time * 0.004, gapA);
    col += ring(q, n2, 4.8, 5.5, 2.0, -Time * 0.003, gapB);
    if (Detail > 2.5) col += ring(q, n3, 2.6, 3.0, 3.0, Time * 0.006, gapC);

    // ---- shafts: the star's light finding its way out between the panels ----------------------------------------
    vec2 mn, mj;
    float cosI;
    planeAxes(n, mn, mj, cosI);
    vec2 e = vec2(dot(q, mj), dot(q, mn) / cosI);
    float re = length(e);
    float angA = atan(e.y, e.x) / (2.0 * PI) + 0.5 + Time * 0.004;
    float cell = floor(angA * PANELS);
    // a shaft leaves wherever the middle row of the first ring has a panel missing, and a wider soft one where two are
    float missMid = 1.0 - step(MISSING, hash(vec2(cell + 1.0 * 131.0, 1.0)));
    float missNext = 1.0 - step(MISSING, hash(vec2(cell + 1.0 + 131.0, 1.0)));
    float beam = missMid * (0.7 + 0.3 * missNext);
    float across = abs(fract(angA * PANELS) - 0.5);
    float beamShape = 1.0 - smoothstep(0.04, 0.34, across);
    float reach = smoothstep(rA1, rA1 + 0.5, re) * exp(-(re - rA1) * 0.42);
    float flicker = 0.75 + 0.25 * pnoise(vec2(angA * 40.0, Time * 0.3), 40.0);
    col += hot * beam * beamShape * reach * flicker * 1.15;
    // a faint haze where the swarm is thick, so the rings read as one structure
    col += hot * 0.05 * exp(-pow((re - 3.75) / 0.9, 2.0));

    col = 1.0 - exp(-col * 1.25);

    // fade to nothing before the billboard edge so no rectangle shows
    float edge = 1.0 - smoothstep(0.7, 1.0, max(abs(vLocal.x), abs(vLocal.y)));
    fragColor = vec4(col * edge, 0.0);   // alpha 0: purely additive
}
