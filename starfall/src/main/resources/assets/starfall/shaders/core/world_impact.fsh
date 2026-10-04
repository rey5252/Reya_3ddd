#version 150

// The world's own picture while a strike lands: it smears outwards from where the strike is, the colours part,
// it drains towards the strike's colour (bright stays bright), and it shakes.

uniform sampler2D Sampler0;
uniform vec2 Centre;
uniform float Zoom;
uniform float Aberration;
uniform vec4 Tint;
uniform float Desaturate;
uniform vec2 Shake;
uniform float Time;

in vec2 uv;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 tap(vec2 p) {
    vec2 d = (p - Centre) * Aberration;
    return vec3(texture(Sampler0, p + d).r, texture(Sampler0, p).g, texture(Sampler0, p - d).b);
}

void main() {
    vec2 p = uv + Shake;
    vec3 col;
    if (Zoom > 0.002) {
        col = vec3(0.0);
        float ws = 0.0;
        float j = hash12(gl_FragCoord.xy + fract(Time * 7.3) * 97.0);
        for (int i = 0; i < 16; i++) {
            float t = (float(i) + j) / 16.0;
            vec2 q = Centre + (p - Centre) * (1.0 - Zoom * t);
            float w = 1.0 - t * 0.7;
            col += tap(q) * w;
            ws += w;
        }
        col /= ws;
    } else {
        col = tap(p);
    }
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(l), Desaturate);
    col = mix(col, Tint.rgb * (0.2 + 1.3 * l), clamp(Tint.a, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
