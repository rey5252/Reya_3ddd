#version 150

// The railgun's and the accelerator's metal: positions and normals come already in view space.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vPos;
out vec3 vNormal;
out vec2 vUV;
out vec4 vColor;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    vPos = pos.xyz;
    vNormal = mat3(ModelViewMat) * Normal;
    vUV = UV0;
    vColor = Color;
}
