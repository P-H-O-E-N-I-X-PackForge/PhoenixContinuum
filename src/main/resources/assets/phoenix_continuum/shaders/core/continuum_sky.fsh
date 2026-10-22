#version 150

// The sky of a personal dimension, looked at from inside, reconstructed from the camera's inverse view and projection
// so it turns with the player. At night, and always on a world with no air, it is an opaque nebula and star field.
// On a world with an atmosphere it also has a daytime sky in the atmosphere's own colour, a twilight glow round the
// star at sunrise and sunset, and the stars and nebula fading out as the star climbs (and back in as it sets).

uniform mat4 InvViewMat;
uniform mat4 InvProjMat;
uniform float Time;
uniform float Octaves;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 SunDir;        // direction to the star, world space
uniform float Atmo;         // 0 = no atmosphere (space all day), 1 = thick
uniform vec3 ZenithColor;   // daytime sky straight up
uniform vec3 HorizonColor;  // daytime sky at the horizon
uniform vec3 DuskColor;     // twilight glow
uniform vec3 SunColor;      // the star's colour
uniform float Eclipse;      // 0..1: how much of the star a world is covering
uniform float Ground;       // 1 when standing on a world: below the horizon is dark ground, not sky
uniform float Aurora;       // 0..1: curtains of light toward the north
uniform vec3 AuroraColor;
uniform float Storm;        // 0..1: lightning
uniform float Lens;         // radians: Einstein radius of a black hole at SunDir (0 = none)

// the nebula comes from the baked picture (see continuum_nebula_bake) when it is there, else it is worked out here
uniform sampler2D NebulaTex;
uniform float UseBake;

vec3 bakedNebula(vec3 dir) {
    float u = atan(dir.z, dir.x) / 6.2831853 + 0.5 + Time * 0.00002;
    float v = asin(clamp(dir.y, -1.0, 1.0)) / 3.14159265 + 0.5;
    return textureLod(NebulaTex, vec2(u, v), 0.0).rgb * 0.5;
}

in vec2 ndc;
out vec4 fragColor;

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
    vec4 viewPos = InvProjMat * vec4(ndc, 1.0, 1.0);
    viewPos /= viewPos.w;
    vec3 dir = normalize((InvViewMat * vec4(viewPos.xyz, 0.0)).xyz);
    vec3 viewDirWorld = dir;                                          // the real direction, for the atmosphere

    // ---- a black hole bends the sky round it: look up where each ray really came from (a thin lens) ---------------
    float ring = 0.0;
    if (Lens > 0.0) {
        vec3 hole = normalize(SunDir);
        float c = clamp(dot(dir, hole), -1.0, 1.0);
        float theta = acos(c);
        if (theta < 1.3) {
            vec3 t = dir - hole * c;
            float tl = length(t);
            if (tl > 1e-4) {
                float source = theta - Lens * Lens / max(theta, 0.02);
                dir = normalize(hole * cos(source) + (t / tl) * sin(source));
            }
        }
        // the thin bright ring where the sky behind is smeared round the hole
        ring = exp(-pow((theta - Lens) / 0.010, 2.0)) * 0.22 + exp(-pow((theta - Lens * 1.45) / 0.030, 2.0)) * 0.05;
    }

    vec3 nebulaLight;
    if (UseBake > 0.5) {
        nebulaLight = bakedNebula(dir);
    } else {
        vec3 q = dir * 1.6 + Time * 0.0004;
        float warp = fbm(q + 1.4 * vec3(fbm(q + 3.1, 3.0), fbm(q + 7.7, 3.0), fbm(q + 1.3, 3.0)));
        float haze = 0.30 + 0.70 * smoothstep(0.15, 0.85, warp);
        float filament = pow(smoothstep(0.42, 0.78, warp), 2.0);
        vec3 nebula = mix(SecondaryColor, PrimaryColor, smoothstep(0.25, 0.8, warp));
        nebulaLight = nebula * (0.11 * haze + 0.42 * filament);
    }

    vec3 col = vec3(0.012, 0.014, 0.034) + nebulaLight + SunColor * ring;

    vec3 sp = dir * 90.0;
    vec3 cell = floor(sp);
    float h = hash(cell);
    if (h > 0.965) {
        vec3 centre = cell + 0.5 + (vec3(hash(cell + 1.7), hash(cell + 4.1), hash(cell + 9.3)) - 0.5) * 0.6;
        float d = length(sp - centre);
        float twinkle = 0.75 + 0.25 * sin(Time * (1.0 + h * 3.0) + h * 40.0);
        col += vec3(1.0, 0.96, 0.9) * smoothstep(0.42, 0.0, d) * (0.35 + 0.65 * (h - 0.965) / 0.035) * twinkle;
    }

    // ---- the atmosphere ---------------------------------------------------------------------------------------------
    dir = viewDirWorld;
    float sunE = SunDir.y;
    // a world crossing the star takes the light away (more than its share: the eye reads it as nearly night)
    float sunLeft = 1.0 - 0.96 * pow(Eclipse, 1.35);
    float dayF = smoothstep(-0.14, 0.18, sunE) * Atmo * sunLeft;     // how much daylight there is
    float twi = exp(-pow(sunE / 0.20, 2.0)) * Atmo * sunLeft;        // twilight: strongest with the star on the horizon
    float up = dir.y;
    float hz = pow(1.0 - clamp(up, 0.0, 1.0), 2.5);                  // 0 overhead, 1 at the horizon
    float cosS = clamp(dot(dir, SunDir), -1.0, 1.0);

    vec3 daySky = mix(ZenithColor, HorizonColor, hz);
    float bloomKeep = 1.0 - 0.75 * twi;                              // at twilight the dusk glow takes over from the bloom
    daySky += SunColor * pow(max(cosS, 0.0), 6.0) * 0.28 * bloomKeep;       // a broad bloom round the star
    daySky += mix(SunColor, vec3(1.0), 0.5) * pow(max(cosS, 0.0), 60.0) * 0.55 * bloomKeep;

    float towardSun = pow(max(cosS, 0.0), 2.0);
    vec3 twilight = DuskColor * twi * 1.1 * (0.20 + 0.80 * towardSun) * smoothstep(0.80, 0.0, up);

    vec3 sky = daySky * dayF + twilight;
    sky *= mix(0.35, 1.0, smoothstep(-0.25, 0.05, up));              // below the horizon the sky darkens

    // stars and nebula are washed out by daylight and a little by twilight
    float spaceVis = 1.0 - clamp(dayF * 1.2 + twi * 0.35, 0.0, 1.0);
    col = col * spaceVis + sky;

    // ---- an eclipse: the corona round the covered star, and a sunset glow all the way round the horizon -------------
    if (Eclipse > 0.02) {
        float total = smoothstep(0.55, 1.0, Eclipse);
        float ang = acos(cosS);
        float corona = 0.55 * exp(-ang / 0.045) + 0.22 * exp(-ang / 0.16) + 0.06 * exp(-ang / 0.5);
        // streamers: the corona is not round
        float az = atan(dot(dir, normalize(cross(SunDir, vec3(0.0, 1.0, 0.01)))), dot(dir, normalize(cross(vec3(0.0, 1.0, 0.01), cross(SunDir, vec3(0.0, 1.0, 0.01))))));
        corona *= 0.75 + 0.5 * noise(vec3(az * 3.0, ang * 6.0, Time * 0.05));
        col += mix(SunColor, vec3(1.0), 0.6) * corona * total * 1.3;
        col += DuskColor * 0.55 * exp(-pow(up / 0.22, 2.0)) * Eclipse * Eclipse * Atmo;
    }

    // ---- an aurora: curtains of light over the northern horizon, at night ----------------------------------------------
    if (Aurora > 0.01 && spaceVis > 0.02) {
        float az = atan(dir.x, dir.z);                               // 0 = north
        float elev = dir.y;
        float band = smoothstep(-0.03, 0.14, elev) * (1.0 - smoothstep(0.45, 0.85, elev));
        float facing = smoothstep(-0.3, 0.7, cos(az));
        float sway = noise(vec3(az * 3.2 + Time * 0.04, elev * 2.0, Time * 0.07));
        float curtain = smoothstep(0.25, 0.85, sway);
        float rays = pow(noise(vec3(az * 38.0, elev * 0.8, Time * 0.25)), 2.2);
        float a = band * facing * curtain * (0.35 + 0.9 * rays) * Aurora;
        vec3 ac = mix(AuroraColor, AuroraColor.bgr, smoothstep(0.18, 0.6, elev));
        col += ac * a * 0.9 * spaceVis;
    }

    // ---- lightning in a stormy sky -------------------------------------------------------------------------------------
    if (Storm > 0.01) {
        float slot = floor(Time * 1.4);
        float chance = hash(vec3(slot, 1.0, 2.0));
        float since = fract(Time * 1.4);
        float flash = step(0.72, chance) * exp(-since * 8.0) * (0.55 + 0.45 * sin(since * 70.0));
        float spin = hash(vec3(slot, 3.0, 4.0)) * 6.2831853;
        vec3 flashDir = normalize(vec3(cos(spin), 0.12 + 0.45 * hash(vec3(slot, 5.0, 6.0)), sin(spin)));
        float local = pow(max(dot(dir, flashDir), 0.0), 10.0);
        col += vec3(0.72, 0.8, 1.0) * Storm * flash * (0.2 + 1.3 * local) * (1.0 - 0.5 * dayF);
    }

    // the world under your feet: a crisp horizon, and nothing of the sky below it
    if (Ground > 0.5) col *= mix(0.05, 1.0, smoothstep(-0.010, 0.010, dir.y));

    fragColor = vec4(col, 1.0);
}
