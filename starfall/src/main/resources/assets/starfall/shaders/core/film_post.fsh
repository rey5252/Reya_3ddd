#version 150

// The film's last pass, like a camera's lens and film: the colours part a little towards the edges, the picture
// smears outwards when it rushes forward, bright light blooms, the corners darken, a fine grain moves over it,
// and a flash can wash it out.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Texel;
uniform float Bloom;
uniform float Aberration;
uniform float Zoom;
uniform float Vignette;
uniform float Grain;
uniform float Time;
uniform vec4 Flash;

in vec2 uv;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 scene(vec2 p) {
    vec2 d = (p - 0.5) * Aberration;
    return vec3(texture(Sampler0, p + d).r, texture(Sampler0, p).g, texture(Sampler0, p - d).b);
}

void main() {
    vec3 col;
    if (Zoom > 0.002) {
        // a rush: samples back towards the middle, nearer ones counting more
        col = vec3(0.0);
        float wsum = 0.0;
        float jitter = hash12(uv / Texel + Time);
        for (int i = 0; i < 14; i++) {
            float t = (float(i) + jitter) / 14.0;
            vec2 q = 0.5 + (uv - 0.5) * (1.0 - Zoom * t);
            float w = 1.0 - t * 0.6;
            col += scene(q) * w;
            wsum += w;
        }
        col /= wsum;
    } else {
        col = scene(uv);
    }
    col += texture(Sampler1, uv).rgb * Bloom;
    // highlights roll off instead of clipping flat, keeping their hue, and the brightest burn towards white
    float mx = max(col.r, max(col.g, col.b));
    float mt = mx < 0.8 ? mx : 0.8 + 0.2 * (1.0 - exp(-(mx - 0.8) / 0.2));
    col *= mt / max(mx, 1.0e-4);
    col = mix(col, vec3(mt), smoothstep(1.2, 4.0, mx) * 0.6);
    float aspect = Texel.y / Texel.x;
    vec2 v = (uv - 0.5) * vec2(aspect, 1.0);
    float r = length(v) / length(vec2(aspect, 1.0) * 0.5);
    col *= 1.0 - Vignette * smoothstep(0.3, 1.05, r) * (0.7 + 0.3 * r);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col += (hash12(floor(uv / Texel * 0.5) + fract(Time * 7.31) * 913.0) - 0.5) * Grain * (1.0 - 0.6 * luma);
    col = mix(col, Flash.rgb, clamp(Flash.a, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
