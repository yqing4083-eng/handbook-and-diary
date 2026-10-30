#version 450
layout(location=0) in vec3 position;
layout(location=1) in vec2 uv;
layout(set=0,binding=0,std140) uniform Draw {
    mat4 mvp; mat4 model; vec4 color; vec4 params; vec4 extra;
} d;
layout(location=0) out vec2 vUv;
layout(location=1) out float vLight;
layout(location=2) out vec3 vSurfacePosition;
void main() {
    vec3 p=position;
    // Half-panels stay planar. Fold angles and the real camera produce the V
    // outline and perspective height change, including for side walls/shadows.
    vec3 n=normalize((d.model*vec4(0.,0.,1.,0.)).xyz);
    vLight=d.extra.x<0. ? 1. : .67+.33*abs(dot(n,normalize(vec3(-.35,.55,1.))));
    vSurfacePosition=(d.model*vec4(p,1.)).xyz;
    vUv=uv;
    vec4 clip=d.mvp*vec4(p,1.);
    // Preserve shared GL-style scene/picking math; Vulkan clip depth is [0, w].
    gl_Position=vec4(clip.x,-clip.y,(clip.z+clip.w)*.5,clip.w);
}
