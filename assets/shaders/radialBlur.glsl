#type vertex
#version 310 es
precision highp float;

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;

out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos.x, aPos.y, 0.0, 1.0);
    TexCoord = aTexCoord;
}

#type fragment
#version 310 es
precision highp float;

out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D screenTexture;
uniform vec2 blurCenter;
uniform float blurStrength;
uniform int samples;

void main() {
    vec2 dir = TexCoord - blurCenter;
    float dist = length(dir);
    dir = normalize(dir);
    
    vec4 color = vec4(0.0);
    
    // Sample along radial direction
    for(int i = 0; i < samples; i++) {
        float offset = (float(i) / float(samples - 1) - 0.5) * blurStrength * dist;
        vec2 sampleCoord = TexCoord - dir * offset;
        color += texture(screenTexture, sampleCoord);
    }
    
    color /= float(samples);
    
    FragColor = color;
}
