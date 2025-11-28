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
uniform float grainIntensity;
uniform float time;

// Pseudo-random function
float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 color = texture(screenTexture, TexCoord);
    
    // Generate noise based on position and time
    float noise = random(TexCoord * time) * 2.0 - 1.0;
    
    // Apply grain
    color.rgb += noise * grainIntensity;
    
    FragColor = color;
}
