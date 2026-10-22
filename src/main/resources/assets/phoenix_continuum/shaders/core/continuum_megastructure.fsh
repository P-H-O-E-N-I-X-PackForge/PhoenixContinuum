#version 150

// Dyson swarm: thousands of reflective panels on a few tilted, nested shells around a star. It is built as real depth, not
// a flat picture: the billboard is treated as a view straight down on each shell, so there is a near side and a far side.
// The far panels show their inner faces, lit by the star (bright, seen through the gaps in the near ones); the near
// panels show their outer faces, dark metal edged in the glare behind them, and block the star and its shafts of light.
// Layers are composited back to front: far shells, the star's glare and shafts, then near shells.
//
// DiskNormal is the system's orbital plane normal in view space: the first shell is a band in that plane, the others are
// tipped away from it. Output is premultiplied (the blend is one, one-minus-source-alpha), so panels can hide the star.

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

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// One layer of panels on a sphere of radius R, seen at the point p on it (view space, star at the origin). The shell's
// polar axis is `axis`; the panels fill a band of latitudes `band` wide plus a sparse scatter everywhere else.
// Returns premultiplied colour.
vec4 panels(vec3 p, float R, vec3 axis, float band, float scatter, float seed, float spin, float cellSize, vec3 hot) {
    vec3 u = normalize(cross(axis, abs(axis.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 w = cross(axis, u);
    float lat = asin(clamp(dot(p, axis) / R, -1.0, 1.0));
    float lon = atan(dot(p, w), dot(p, u)) + spin;

    float rows = max(4.0, floor(PI * R / cellSize));
    float rowF = (lat + 0.5 * PI) / PI * rows;
    float row = floor(rowF);
    float latc = (row + 0.5) / rows * PI - 0.5 * PI;
    float cols = max(3.0, floor(2.0 * PI * R * cos(latc) / cellSize));
    float colF = (lon / (2.0 * PI) + 0.5) * cols;
    float col = floor(colF);

    float h = hash(vec2(col + row * 173.0, seed));
    float h2 = hash(vec2(row + col * 97.0, seed + 11.0));
    float inBand = 1.0 - smoothstep(band, band + 0.10, abs(lat));
    float prob = inBand * 0.62 + scatter;
    float present = step(h2, prob);
    if (present < 0.5) return vec4(0.0);

    vec2 fr = vec2(fract(colF), fract(rowF));
    vec2 e = min(fr, 1.0 - fr);
    float mask = smoothstep(0.05, 0.16, e.x) * smoothstep(0.05, 0.16, e.y);
    if (mask < 0.01) return vec4(0.0);

    float nz = p.z / R;                                           // > 0: we see the outer face, < 0: the inner face
    float glint = pow(max(0.0, cos(h * 40.0 + Time * (0.5 + 0.6 * h))), 18.0);
    vec3 col3;
    if (nz > 0.0) {
        float fres = pow(1.0 - nz, 3.0);
        col3 = vec3(0.045, 0.05, 0.075) * (0.7 + 0.6 * h)
             + hot * fres * 0.75
             + hot * glint * 1.3 * (0.35 + 0.65 * nz)
             + vec3(0.5, 0.65, 1.0) * 0.05 * (0.5 + 0.5 * h);       // a little starlight from elsewhere on the outer face
    } else {
        float inner = 0.55 + 0.45 * (1.0 - abs(nz));
        col3 = hot * (0.55 + 0.4 * h) * inner * (0.75 + 1.4 * glint)
             + vec3(0.9, 0.95, 1.0) * pow(max(0.0, 1.0 - e.y * 5.0), 3.0) * 0.25 * hot;
    }
    // panel edges catch the light
    col3 += hot * (1.0 - smoothstep(0.05, 0.2, min(e.x, e.y))) * 0.12 * step(0.0, nz);
    return vec4(col3 * mask, mask);
}

vec4 over(vec4 top, vec4 bottom) {
    return vec4(top.rgb + bottom.rgb * (1.0 - top.a), top.a + bottom.a * (1.0 - top.a));
}

void main() {
    vec2 q = vLocal * Extent;
    float rs = length(q) / Core;
    vec3 hot = mix(Tint, vec3(1.0), 0.5);

    const float R0 = 3.4;
    const float R1 = 4.5;
    const float R2 = 5.7;

    vec3 n = normalize(DiskNormal);
    vec3 t = normalize(cross(n, abs(n.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
    vec3 a1 = n;
    vec3 a2 = normalize(n * 0.55 + t * 0.83);
    vec3 a3 = normalize(n * 0.40 - t * 0.55 + cross(n, t) * 0.73);

    float cell = 0.30;
    float edge = 1.0 - smoothstep(0.7, 1.0, max(abs(vLocal.x), abs(vLocal.y)));

    vec4 acc = vec4(0.0);
    float rr = rs * Core;                                         // = length(q): the radius in star radii

    // ---- far side: the inner faces of the shells, lit by the star -------------------------------------------------
    if (rs >= 0.98) {
        if (Detail > 2.5 && rr < R2) acc = over(panels(vec3(q, -sqrt(R2 * R2 - rr * rr)), R2, a3, 0.17, 0.02, 3.0, -Time * 0.004, cell * 1.15, hot), acc);
        if (rr < R1) acc = over(panels(vec3(q, -sqrt(R1 * R1 - rr * rr)), R1, a2, 0.24, 0.025, 2.0, Time * 0.003, cell, hot), acc);
        if (rr < R0) acc = over(panels(vec3(q, -sqrt(R0 * R0 - rr * rr)), R0, a1, 0.20, 0.02, 1.0, -Time * 0.006, cell * 0.9, hot), acc);
    }

    // ---- the star's glare and the shafts of light leaving it ------------------------------------------------------
    vec3 glare = vec3(0.0);
    if (rs >= 0.98) {
        float d = max(rs - 1.0, 0.0);
        glare += hot * (0.85 * exp(-d * 1.5) + 0.26 / (1.0 + d * d * 1.5));
        float ang = atan(q.y, q.x) / (2.0 * PI) + 0.5 + Time * 0.002;
        float idx = ang * 56.0;
        float rayH = hash(vec2(floor(idx), 5.0));
        float across = abs(fract(idx) - 0.5);
        float ray = step(0.45, rayH) * (1.0 - smoothstep(0.05, 0.42, across)) * (0.6 + 0.4 * hash(vec2(floor(idx), 9.0)));
        float flick = 0.8 + 0.2 * sin(Time * 0.7 + rayH * 30.0);
        glare += hot * ray * flick * exp(-(rs - 1.0) * 0.28) * 0.32 * smoothstep(1.0, 1.6, rs);
    }
    acc = vec4(acc.rgb + glare * (1.0 - acc.a * 0.0), acc.a);     // light adds on top of what is behind it

    // ---- near side: the outer faces of the shells, dark against the glare, hiding it ----------------------------
    if (rr < R0) acc = over(panels(vec3(q, sqrt(R0 * R0 - rr * rr)), R0, a1, 0.20, 0.02, 1.0, -Time * 0.006, cell * 0.9, hot), acc);
    if (rr < R1) acc = over(panels(vec3(q, sqrt(R1 * R1 - rr * rr)), R1, a2, 0.24, 0.025, 2.0, Time * 0.003, cell, hot), acc);
    if (Detail > 2.5 && rr < R2) acc = over(panels(vec3(q, sqrt(R2 * R2 - rr * rr)), R2, a3, 0.17, 0.02, 3.0, -Time * 0.004, cell * 1.15, hot), acc);

    acc.rgb = 1.0 - exp(-acc.rgb * 1.25);
    fragColor = vec4(acc.rgb * edge, acc.a * edge);
}
