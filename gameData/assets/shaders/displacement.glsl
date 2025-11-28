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
uniform float displacementStrength;
uniform float time;
uniform vec2 shakeDirection;

void main() {
    // Create wave-based displacement
    vec2 displacement = vec2(
        sin(TexCoord.y * 10.0 + time * 5.0),
        cos(TexCoord.x * 10.0 + time * 5.0)
    ) * displacementStrength;
    
    // Add directional shake
    displacement += shakeDirection * displacementStrength * 2.0;
    
    vec2 distortedCoord = TexCoord + displacement;
    
    FragColor = texture(screenTexture, distortedCoord);
}
