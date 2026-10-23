#version 150

// Dyson sphere: a star shut inside a shell of plates. The shell is dark metal on the outside, in a grid of plates with
// seams that glow where the star's light leaks out, a few great rifts where it has not been finished and the star shows
// through, and a lit belt of docks round the equator. It is opaque, so it hides the star; the star's glare rings its edge.
// Output is premultiplied (the blend is one, one-minus-source-alpha).
//
// DiskNormal is the system's orbital plane normal in view space: the belt lies in that plane.

uniform float Time;
uniform float Extent;       // billboard half-size, in star radii
uniform float Voxel;        // unused
uniform float Core;         // radius of the star body in billboard units
uniform float Detail;       // 2 (low) - 4 (high)
uniform vec3 Tint;          // star colour
uniform vec3 DiskNormal;    // orbital plane normal, view space
uniform float Completion;   // 0 - 1: how much of the shell is built (1 is a finished sphere)
uniform float Overlay;      // 1 when drawn over a star that is already drawn: no glare of its own, gaps show the star

in vec2 vLocal;
out vec4 fragColor;

const float PI = 3.14159265;
const float SHELL = 1.5;    // shell radius, in star radii

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash3(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    vec2 q = vLocal * Extent;
    float rr = length(q);                    // radius in star radii
    vec3 hot = mix(Tint, vec3(1.0), 0.5);
    float edge = 1.0 - smoothstep(0.7, 1.0, max(abs(vLocal.x), abs(vLocal.y)));

    vec3 n = normalize(DiskNormal);
    vec3 u = normalize(cross(n, abs(n.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
    vec3 w = cross(n, u);

    vec3 col = vec3(0.0);
    float alpha = 0.0;

    if (rr < SHELL) {
        vec3 p = vec3(q, sqrt(SHELL * SHELL - rr * rr));            // the point on the near side of the shell
        float nz = p.z / SHELL;                                      // facing the viewer
        vec3 nrm = p / SHELL;

        // coordinates on the shell, turning slowly about the orbital axis
        float spin = Time * 0.004;
        float lat = asin(clamp(dot(nrm, n), -1.0, 1.0));
        float lon = atan(dot(nrm, w), dot(nrm, u)) + spin;

        // ---- plates: an equal-area grid of cells ----------------------------------------------------------------
        float cellSize = 0.085;
        float rows = floor(PI * SHELL / cellSize);
        float rowF = (lat + 0.5 * PI) / PI * rows;
        float row = floor(rowF);
        float latc = (row + 0.5) / rows * PI - 0.5 * PI;
        float cols = max(3.0, floor(2.0 * PI * SHELL * cos(latc) / cellSize));
        float colF = (lon / (2.0 * PI) + 0.5) * cols;
        float cid = floor(colF);
        float h = hash(vec2(cid + row * 173.0, 3.0));
        vec2 fr = vec2(fract(colF), fract(rowF));
        vec2 e = min(fr, 1.0 - fr);
        float seam = 1.0 - smoothstep(0.03, 0.10, min(e.x, e.y));

        // a half-built shell: plates go up scattered, the belt first. A missing plate is just the scaffold's lattice
        float order = mix(hash(vec2(cid * 1.7 + row * 61.0, 9.0)), abs(lat) / (0.5 * PI), 0.35);
        if (order > Completion) {
            float lattice = seam * 0.85;
            vec3 strut = vec3(0.10, 0.11, 0.15) + hot * 0.12;
            fragColor = vec4(strut * lattice * edge, lattice * edge);
            return;
        }

        // plates differ a little in tone, with slow large-scale weathering
        vec3 sp = vec3(dot(nrm, u), dot(nrm, n), dot(nrm, w));
        float weather = vnoise3(sp * 5.0 + vec3(0.0, 0.0, 3.7));
        vec3 metal = vec3(0.055, 0.058, 0.075) * (0.7 + 0.6 * h) * (0.75 + 0.5 * weather);

        // ---- rifts: places where the shell is unfinished and the star shows through ------------------------------
        float riftNoise = vnoise3(sp * 2.1 + vec3(7.0));
        float rift = smoothstep(0.64, 0.72, riftNoise);
        float riftEdge = smoothstep(0.58, 0.66, riftNoise) - rift;

        // ---- the belt: a lit band of docks round the equator ----------------------------------------------------
        float belt = 1.0 - smoothstep(0.045, 0.075, abs(lat));
        float dock = step(0.62, hash(vec2(cid, row + 31.0)));
        float lights = belt * dock * (0.6 + 0.4 * sin(Time * 1.2 + h * 40.0));

        // the star's glare reaches round the limb, as a rim
        float rim = pow(1.0 - nz, 2.6);
        float lightFromStar = 0.07 + 0.05 * (0.5 + 0.5 * dot(nrm, normalize(vec3(-0.4, 0.5, 0.7))));

        col = metal * (1.0 + 2.0 * lightFromStar)
            + hot * rim * 0.9
            + vec3(0.45, 0.6, 1.0) * lightFromStar * 0.4
            + hot * seam * (0.07 + 0.55 * step(0.78, h)) * 0.9                 // light leaking through the seams
            + hot * riftEdge * 0.9
            + vec3(1.0, 0.85, 0.55) * lights * 0.85;
        // a rift is the star itself, seen through the hole
        vec3 starFace = hot * (1.4 + 0.5 * vnoise3(sp * 9.0 + vec3(Time * 0.05)));
        col = mix(col, starFace, rift);
        alpha = 1.0;
    } else if (Overlay > 0.5) {
        // the star under it already has its own glare
        fragColor = vec4(0.0);
        return;
    } else {
        // ---- outside the shell: the glare that gets past it, a faint corona, and slow shafts -------------------------
        float d = rr - SHELL;
        col += hot * (0.75 * exp(-d * 5.0) + 0.18 * exp(-d * 1.1) + 0.05 / (1.0 + d * d * 0.8));
        float ang = atan(q.y, q.x) / (2.0 * PI) + 0.5 + Time * 0.002;
        float idx = ang * 40.0;
        float rayH = hash(vec2(floor(idx), 17.0));
        float across = abs(fract(idx) - 0.5);
        float ray = step(0.6, rayH) * (1.0 - smoothstep(0.04, 0.38, across));
        col += hot * ray * exp(-d * 0.55) * 0.35 * smoothstep(0.0, 0.5, d);
    }

    col = 1.0 - exp(-col * 1.3);
    fragColor = vec4(col * edge, alpha * edge);
}
