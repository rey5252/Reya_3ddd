#version 150

// UV0 within the unit disc is a point of a soft shape: sprites use the whole disc, ribbons only (0, -1..1)
// across their width. Adding 4 to u picks the glow profile (a hot core with a long halo) over the plain soft one.
// Output is premultiplied, so the same pass can add light or, with the usual blend, lay down soft colour.

uniform vec4 ColorModulator;

in vec2 uv;
in vec4 tint;

out vec4 fragColor;

void main() {
    float glow = step(2.0, uv.x);
    vec2 q = vec2(uv.x - glow * 4.0, uv.y);
    float d2 = dot(q, q);
    if (d2 >= 1.0) {
        discard;
    }
    float soft = (exp(-d2 * 3.5) - 0.0302) / 0.9698;
    float core = (1.0 / (1.0 + d2 * 90.0) - 1.0 / 91.0) * 1.0111;
    float k = mix(soft, core * 0.7 + soft * 0.3, glow);
    float a = tint.a * k;
    fragColor = vec4(tint.rgb * a, a) * ColorModulator;
}
