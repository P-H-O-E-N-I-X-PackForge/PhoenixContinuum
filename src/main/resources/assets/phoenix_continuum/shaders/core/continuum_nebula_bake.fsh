#version 150

// Draws the nebula (haze and filaments, no stars) of the sky and map backdrop for the whole sphere of directions into a
// longitude / latitude picture. It is run once (a few rows a frame) when the colours change, and the backdrop shaders
// then just look it up, which is far cheaper than evaluating the noise for every pixel every frame.
// Stored at twice its value so the dark range keeps its precision in an 8-bit texture.

uniform float Octaves;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;

in vec2 vLocal;
out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

float fbm(vec3 p, float limit) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        if (float(i) >= min(Octaves, limit)) break;
        v += a * noise(p);
        p = p * 2.03 + vec3(7.3, 5.9, 11.1);
        a *= 0.5;
    }
    return v;
}

float fbm(vec3 p) { return fbm(p, 6.0); }

void main() {
    vec2 uv = vLocal * 0.5 + 0.5;
    float lon = (uv.x - 0.5) * 2.0 * PI;
    float lat = (uv.y - 0.5) * PI;
    vec3 dir = vec3(cos(lat) * cos(lon), sin(lat), cos(lat) * sin(lon));

    vec3 q = dir * 1.6;
    float warp = fbm(q + 1.4 * vec3(fbm(q + 3.1, 3.0), fbm(q + 7.7, 3.0), fbm(q + 1.3, 3.0)));
    float haze = 0.30 + 0.70 * smoothstep(0.15, 0.85, warp);
    float filament = pow(smoothstep(0.42, 0.78, warp), 2.0);
    vec3 nebula = mix(SecondaryColor, PrimaryColor, smoothstep(0.25, 0.8, warp));
    fragColor = vec4(nebula * (0.11 * haze + 0.42 * filament) * 2.0, 1.0);
}
