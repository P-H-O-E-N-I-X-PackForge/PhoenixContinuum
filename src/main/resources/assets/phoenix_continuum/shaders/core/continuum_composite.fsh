#version 150

// Lays an offscreen picture (premultiplied alpha) over the scene. Empty pixels are discarded, so when the picture
// writes depth it does so only where something is drawn.

uniform sampler2D DiffuseSampler;

in vec2 uv;
in vec4 tint;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, uv) * tint;
    if (c.a < 0.004 && c.r + c.g + c.b < 0.004) discard;
    fragColor = c;
}
