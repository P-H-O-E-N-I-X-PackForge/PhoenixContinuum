#version 150

// Draws one face of a planet's terrain or cloud cube map: for every direction on the sphere, the height and its gradient
// (mode 0, exactly what continuum_planet computes live) or the cloud density (mode 1). Run once per planet; the planet
// shader then looks the result up.

uniform float Face;         // 0..5: +X -X +Y -Y +Z -Z
uniform float Mode;         // 0 = terrain, 1 = clouds
uniform float Seed;
uniform float Scale;
uniform float Octaves;

in vec2 vLocal;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

float fbm(vec3 p, int octaves) {
    float amplitude = 0.5;
    float sum = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += amplitude * noise(p);
        p = p * 2.02 + vec3(13.1, 7.7, 3.3);
        amplitude *= 0.5;
    }
    return sum;
}

// value noise with its analytic gradient (x = value, yzw = d/dp), so a relief normal costs one pass instead of four
vec4 noised(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    vec3 u = f * f * (3.0 - 2.0 * f);
    vec3 du = 6.0 * f * (1.0 - f);
    float a = hash(i + vec3(0, 0, 0));
    float b = hash(i + vec3(1, 0, 0));
    float c = hash(i + vec3(0, 1, 0));
    float d = hash(i + vec3(1, 1, 0));
    float e = hash(i + vec3(0, 0, 1));
    float ff = hash(i + vec3(1, 0, 1));
    float g = hash(i + vec3(0, 1, 1));
    float h = hash(i + vec3(1, 1, 1));
    float k1 = b - a;
    float k2 = c - a;
    float k3 = e - a;
    float k4 = a - b - c + d;
    float k5 = a - c - e + g;
    float k6 = a - b - e + ff;
    float k7 = -a + b + c - d + e - ff - g + h;
    float v = a + k1 * u.x + k2 * u.y + k3 * u.z + k4 * u.x * u.y + k5 * u.y * u.z + k6 * u.z * u.x + k7 * u.x * u.y * u.z;
    vec3 dv = du * vec3(k1 + k4 * u.y + k6 * u.z + k7 * u.y * u.z,
                        k2 + k5 * u.z + k4 * u.x + k7 * u.z * u.x,
                        k3 + k6 * u.x + k5 * u.y + k7 * u.x * u.y);
    return vec4(v, dv);
}

// fbm with its gradient with respect to the starting point
vec4 fbmd(vec3 p, int octaves) {
    float amplitude = 0.5;
    float scale = 1.0;
    vec4 sum = vec4(0.0);
    for (int i = 0; i < octaves; i++) {
        vec4 n = noised(p);
        sum += amplitude * vec4(n.x, n.yzw * scale);
        p = p * 2.02 + vec3(13.1, 7.7, 3.3);
        amplitude *= 0.5;
        scale *= 2.02;
    }
    return sum;
}



vec3 faceDirection(float face, vec2 st) {
    // the standard cube map layout, with st the texel position in -1..1 across the face
    if (face < 0.5) return vec3(1.0, -st.y, -st.x);
    if (face < 1.5) return vec3(-1.0, -st.y, st.x);
    if (face < 2.5) return vec3(st.x, 1.0, st.y);
    if (face < 3.5) return vec3(st.x, -1.0, -st.y);
    if (face < 4.5) return vec3(st.x, -st.y, 1.0);
    return vec3(-st.x, -st.y, -1.0);
}

void main() {
    vec3 dir = normalize(faceDirection(Face, vLocal));
    if (Mode < 0.5) {
        vec4 r = fbmd(dir * Scale + vec3(Seed * 17.0), int(Octaves));
        float t = (r.x - 0.2) / 0.6;
        float inside = step(0.0, t) * step(t, 1.0);
        fragColor = vec4(clamp(t, 0.0, 1.0), r.yzw * (Scale / 0.6) * inside);
    } else {
        fragColor = vec4(fbm(dir * 3.2 + vec3(Seed * 5.0), 6), 0.0, 0.0, 1.0);
    }
}
