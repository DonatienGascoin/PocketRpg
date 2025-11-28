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
uniform float pixelSize;

void main() {
    // Calculate pixelated coordinates
    vec2 pixelatedCoord = floor(TexCoord / pixelSize) * pixelSize;
    
    vec4 color = texture(screenTexture, pixelatedCoord);
    
    FragColor = color;
}
