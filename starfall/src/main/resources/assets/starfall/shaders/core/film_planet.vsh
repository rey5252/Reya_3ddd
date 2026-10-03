#version 150

// A planet is ray-traced per pixel: the quad covers the part of the screen it can show up in (in clip space),
// and the fragment shader finds the sphere along each pixel's ray.

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
