#version 150

// The strikes' halo, added over the world: the blurred glow of their light.

uniform sampler2D Sampler0;
uniform float Strength;

in vec2 uv;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, uv).rgb * Strength;
    fragColor = vec4(c, 1.0);
}
