#version 450
layout(set=0,binding=0,std140) uniform Draw {
    mat4 mvp; mat4 model; vec4 color; vec4 params; vec4 extra;
} d;
layout(set=0,binding=1) uniform sampler2D artwork;
layout(location=0) in vec2 vUv;
layout(location=1) in float vLight;
layout(location=2) in vec3 vSurfacePosition;
layout(location=0) out vec4 outputColor;
void main() {
    vec2 uv=vec2(mix(d.params.x,d.params.y,vUv.x),vUv.y);
    // Android Bitmap pixels and the transparent Vulkan surface are premultiplied.
    // Opacity must attenuate RGB as well as alpha, including shadow feathering.
    vec4 c=texture(artwork,uv);
    c.rgb*=d.color.rgb*d.color.a;
    c.a*=d.color.a;
    if(d.extra.x<0.) {
        if(d.extra.x> -1.5) {
            // Soft penumbra at the free edges. Keep the hinge covered so the
            // paired leaves cast one continuous shadow rather than two boxes.
            float vertical=min(vUv.y,1.-vUv.y);
            c*=smoothstep(0.,.16,vertical)*smoothstep(0.,.13,1.-vUv.x);
        }
        outputColor=c;return;
    }
    float isRim=mod(d.extra.x,2.);
    float rim=mix(.84,1.,pow(abs(vUv.y*2.-1.),3.));
    float light=vLight;
    if(isRim>.5) {
        // Rigid paper faces use the stable transformed normal from the vertex
        // shader. Only side walls need geometric derivatives; guard tiny edge
        // fragments so grazing angles cannot normalize a near-zero vector.
        vec3 n=cross(dFdx(vSurfacePosition),dFdy(vSurfacePosition));
        float lengthSquared=dot(n,n);
        if(lengthSquared>1e-18) {
            n*=inversesqrt(lengthSquared);
            light=.67+.33*abs(dot(n,normalize(vec3(-.35,.55,1.))));
        }
    }
    // Paper has no painted inner-crease shadow. Keep the existing cover shading.
    float crease=d.extra.x>=2. ? 1. : 1.-.075*exp(-vUv.x*35.);
    outputColor=vec4(c.rgb*light*mix(1.,rim,isRim)*crease,c.a);
}
