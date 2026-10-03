#version 150

// The sky behind a film shot: a full-screen quad given straight in clip space.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 ndc;
out vec4 tint;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    ndc = Position.xy + UV0 * 0.0;
    tint = Color;
}
