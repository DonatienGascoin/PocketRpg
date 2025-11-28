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
uniform float aberrationStrength;

void main() {
    // Calculate offset from center
    vec2 offset = (TexCoord - 0.5) * aberrationStrength;
    
    // Sample each color channel with slight offset
    float r = texture(screenTexture, TexCoord + offset).r;
    float g = texture(screenTexture, TexCoord).g;
    float b = texture(screenTexture, TexCoord - offset).b;
    
    FragColor = vec4(r, g, b, 1.0);
}
