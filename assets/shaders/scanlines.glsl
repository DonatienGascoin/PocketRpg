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
uniform float scanlineIntensity;
uniform float scanlineCount;

void main() {
    vec4 color = texture(screenTexture, TexCoord);
    
    // Create scanline pattern
    float scanline = sin(TexCoord.y * scanlineCount * 3.14159 * 2.0) * 0.5 + 0.5;
    scanline = mix(1.0, scanline, scanlineIntensity);
    
    color.rgb *= scanline;
    
    FragColor = color;
}
