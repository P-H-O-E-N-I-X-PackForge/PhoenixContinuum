#version 150

// One puff of a nebula from its baked picture (see SceneRenderer.drawNebulas): a puff is a fixed cloud, so it is worked
// out once by continuum_nebula and after that is a single texture read. Premultiplied, like the shader it stands in for.

uniform sampler2D Puff;

in vec2 vLocal;
out vec4 fragColor;

void main() {
    if (length(vLocal) >= 1.0) discard;
    fragColor = texture(Puff, vLocal * 0.5 + 0.5);
}
