#version 150

// A supernova remnant: the shell of gas thrown off by a star that has exploded, expanding and thinning over the visit. The
// bright shell is ragged (fingers of denser gas push out ahead of it) and layered by colour, hydrogen red at the shock,
// oxygen green-teal just behind it, and a hot blue-white glow filling the middle; the neutron star that is all that is left
// of the star is a bright point at the centre. It begins with a flash and fades out at the end of the visit.
// Additive and loop-free. Progress runs 0..1 over the visit.

uniform float Time;
uniform float Extent;       // unused: the billboard is already sized to the shell
uniform float Voxel;        // unused
uniform float Core;         // unused
uniform float Detail;       // 2 (low) - 4 (high)
uniform vec3 Tint;          // tints the shell
uniform float Progress;

in vec2 vLocal;
out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// value noise, periodic in x so an angle can be fed in with no seam
float pnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, period);
    float x1 = mod(i.x + 1.0, period);
    return mix(mix(hash(vec2(x0, i.y)), hash(vec2(x1, i.y)), f.x),
               mix(hash(vec2(x0, i.y + 1.0)), hash(vec2(x1, i.y + 1.0)), f.x), f.y);
}

void main() {
    vec2 p = vLocal;
    float r = length(p);
    float ang = atan(p.y, p.x);
    float a01 = ang / (2.0 * PI);

    float grow = pow(Progress, 0.55);                       // fast at first, then coasting
    float radius = mix(0.12, 0.72, grow);
    float thick = 0.030 + 0.075 * grow;

    // ragged shell: slow lumps, finer fingers, and fine grain (the grain is dropped on low detail)
    float lumps = pnoise(vec2(a01 * 7.0 + Progress * 0.6, Progress * 2.0), 7.0);
    float fingers = pnoise(vec2(a01 * 23.0, Progress * 3.0 + 5.0), 23.0);
    float grain = Detail > 2.5 ? pnoise(vec2(a01 * 61.0 + Time * 0.02, r * 40.0), 61.0) : 0.5;
    float rr = radius * (1.0 + 0.14 * (lumps - 0.5) + 0.07 * (fingers - 0.5));

    float shell = exp(-pow((r - rr) / thick, 2.0)) * (0.30 + 1.5 * pow(fingers, 1.8)) * (0.75 + 0.5 * grain);
    // a second, fainter shell inside the first: the reverse shock
    float inner = exp(-pow((r - rr * 0.72) / (thick * 1.6), 2.0)) * (0.25 + 0.9 * pow(lumps, 1.6));
    // gas filling the bubble, patchy and fading towards the middle
    float fill = smoothstep(rr, rr * 0.05, r) * (0.10 + 0.30 * pnoise(vec2(a01 * 9.0, r * 5.0 - Time * 0.02), 9.0));
    // fingers reaching in from the shell, streaks along the radius
    float streak = pow(pnoise(vec2(a01 * 31.0, r * 3.0), 31.0), 3.0) * smoothstep(rr * 1.02, rr * 0.5, r) *
                   smoothstep(0.0, rr * 0.3, r);

    vec3 shock = mix(vec3(1.0, 0.34, 0.24), Tint, 0.25);     // hydrogen at the shock front
    vec3 oxygen = vec3(0.22, 1.0, 0.72);
    vec3 hot = vec3(0.55, 0.72, 1.0);

    vec3 col = shock * shell * 1.3;
    col += oxygen * inner * 0.6;
    col += hot * fill * 0.9;
    col += mix(oxygen, hot, 0.5) * streak * 0.35;

    // the explosion's flash, and the neutron star left at the centre
    float flash = exp(-Progress * 16.0) * exp(-r * 5.0) * 2.4;
    col += vec3(1.0, 0.95, 0.9) * flash;
    float star = exp(-r * r / 0.00035) * 1.6 + exp(-r * r / 0.004) * 0.35;
    col += vec3(0.75, 0.88, 1.0) * star * (0.7 + 0.3 * sin(Time * 9.0));

    float envelope = smoothstep(0.0, 0.04, Progress) * (1.0 - smoothstep(0.82, 1.0, Progress));
    col = col * mix(1.0, envelope, 0.9);
    col = 1.0 - exp(-col * 1.25);

    float edge = 1.0 - smoothstep(0.82, 1.0, max(abs(vLocal.x), abs(vLocal.y)));
    fragColor = vec4(col * edge, 0.0);
}
