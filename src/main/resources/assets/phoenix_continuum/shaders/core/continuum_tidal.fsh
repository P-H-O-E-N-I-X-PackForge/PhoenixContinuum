#version 150

// Tidal disruption event: a star torn apart as it falls into a black hole. The star itself is a bright blob stretched along
// its orbit; a thin stream of gas peels off it and winds inwards in a spiral, getting hotter (red to blue-white) as it
// falls, with knots of brighter gas drifting along it; and as the debris arrives the accretion ring round the hole flares.
//
// The billboard is centred on the hole and the scene lies in the system's orbital plane (DiskNormal). StarPos is where the
// star is, StarNext where it will be a moment later (so the spiral winds the way the star is going), both in hole radii on
// the billboard. Progress runs 0..1 over the event and fades it in and out.
// Additive, loop-free, no lensing: this draws straight to the screen.

uniform float Time;
uniform float Extent;       // billboard half-size, in hole radii
uniform float Voxel;        // unused
uniform float Core;         // radius of the hole body in billboard units
uniform float Detail;       // 2 (low) - 4 (high)
uniform vec3 Tint;          // the gas colour
uniform vec3 DiskNormal;    // orbital plane normal, view space
uniform float Progress;
uniform vec2 StarPos;
uniform vec2 StarNext;

in vec2 vLocal;
out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 q = vLocal * Extent;
    if (length(q) / Core < 0.98) {
        fragColor = vec4(0.0);   // the hole covers this
        return;
    }

    // plane coordinates, as the protostar disk does it
    vec3 n = normalize(DiskNormal);
    float cosI = clamp(abs(n.z), 0.16, 1.0);
    vec2 mn = length(n.xy) > 1e-3 ? normalize(n.xy) : vec2(0.0, 1.0);
    vec2 mj = vec2(-mn.y, mn.x);

    vec2 e = vec2(dot(q, mj), dot(q, mn) / cosI);
    vec2 eS = vec2(dot(StarPos, mj), dot(StarPos, mn) / cosI);
    vec2 eN = vec2(dot(StarNext, mj), dot(StarNext, mn) / cosI);

    float r = length(e);
    float rS = max(length(eS), 0.8);
    float phi = atan(e.y, e.x);
    float phiS = atan(eS.y, eS.x);
    vec2 move = eN - eS;
    float turn = eS.x * move.y - eS.y * move.x;
    float dir = turn >= 0.0 ? 1.0 : -1.0;                // which way round the star is going

    // ---- the stream: a spiral from the star in towards the hole ---------------------------------------------------
    float fall = log(rS / max(r, 0.25));                  // 0 at the star, growing inwards
    float phiCurve = phiS + dir * 1.25 * fall;
    float dphi = mod(phi - phiCurve + PI, 2.0 * PI) - PI;
    float dist = abs(dphi) * r;
    float width = 0.15 + 0.06 * r;
    float inside = smoothstep(rS * 1.05, rS * 0.93, r) * smoothstep(0.0, 0.25, fall + 0.1);

    float line = exp(-(dist * dist) / (width * width));
    float sheet = exp(-(dist * dist) / (width * width * 10.0)) * 0.32;
    float knots = 0.55 + 0.45 * vnoise(vec2(fall * 4.5 - Time * 0.9, phi * 0.0 + 3.0));
    float heat = smoothstep(0.0, 2.4, fall);
    vec3 cool = vec3(1.0, 0.42, 0.14);
    vec3 hotc = mix(Tint, vec3(0.80, 0.90, 1.0), 0.65);
    vec3 gas = mix(cool, hotc, heat);

    vec3 col = vec3(0.0);
    col += gas * (line * knots * (0.8 + 2.2 * heat) + sheet) * inside;

    // a fainter second arm, flung out the other side of the star
    float dphi2 = mod(phi - (phiCurve + 0.55) + PI, 2.0 * PI) - PI;
    float line2 = exp(-(dphi2 * dphi2 * r * r) / (width * width * 1.6));
    col += gas * line2 * (0.25 + 0.6 * heat) * inside * (0.6 + 0.4 * knots);

    // ---- the star coming apart: a blob stretched along its path ---------------------------------------------------
    vec2 d = e - eS;
    vec2 tang = move;
    tang = length(tang) > 1e-4 ? normalize(tang) : vec2(1.0, 0.0);
    float along = dot(d, tang);
    float across = dot(d, vec2(-tang.y, tang.x));
    float stretch = 0.55 + 1.6 * Progress;
    float blob = exp(-(along * along) / (0.5 * stretch * 0.5 * stretch) - (across * across) / (0.26 * 0.26));
    col += vec3(1.0, 0.86, 0.58) * blob * (1.5 - 0.9 * Progress);
    // a pale halo of the star's outer layers
    col += vec3(1.0, 0.7, 0.4) * exp(-dot(d, d) / 0.9) * 0.22 * (1.0 - 0.6 * Progress);

    // ---- the debris arriving: the ring round the hole flares ------------------------------------------------------
    float arrive = smoothstep(0.15, 0.85, Progress);
    float ringShape = exp(-pow((r - 1.8) / 0.5, 2.0));
    float swirl = 0.7 + 0.3 * vnoise(vec2(phi * 3.0 + Time * 0.6, r * 2.0));
    col += hotc * ringShape * swirl * arrive * 1.3;

    // ---- the event comes and goes ---------------------------------------------------------------------------------
    float envelope = smoothstep(0.0, 0.07, Progress) * (1.0 - smoothstep(0.86, 1.0, Progress));
    col *= envelope;
    col = 1.0 - exp(-col * 1.3);

    float edge = 1.0 - smoothstep(0.7, 1.0, max(abs(vLocal.x), abs(vLocal.y)));
    fragColor = vec4(col * edge, 0.0);
}
