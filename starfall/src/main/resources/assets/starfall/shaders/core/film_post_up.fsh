#version 150

// One step up the bloom chain: this level's own light plus the smaller level's, spread with a 3x3 tent, so the
// glow ends up wide around big lights and tight around small ones.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Texel;
uniform float Radius;

in vec2 uv;

out vec4 fragColor;

void main() {
    vec2 t = Texel * Radius;
    vec3 s = texture(Sampler1, uv + vec2(-t.x, -t.y)).rgb + texture(Sampler1, uv + vec2(t.x, -t.y)).rgb
           + texture(Sampler1, uv + vec2(-t.x, t.y)).rgb + texture(Sampler1, uv + vec2(t.x, t.y)).rgb
           + 2.0 * (texture(Sampler1, uv + vec2(0.0, -t.y)).rgb + texture(Sampler1, uv + vec2(0.0, t.y)).rgb
                  + texture(Sampler1, uv + vec2(-t.x, 0.0)).rgb + texture(Sampler1, uv + vec2(t.x, 0.0)).rgb)
           + 4.0 * texture(Sampler1, uv).rgb;
    fragColor = vec4(texture(Sampler0, uv).rgb + s / 16.0, 1.0);
}
