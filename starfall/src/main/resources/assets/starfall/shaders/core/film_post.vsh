#version 150

// A pass over the whole of a target: a quad given straight in clip space.

in vec3 Position;
in vec2 UV0;

out vec2 uv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    uv = UV0;
}
