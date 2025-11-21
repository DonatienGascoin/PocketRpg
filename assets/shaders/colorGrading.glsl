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
uniform vec3 tintColor;
uniform float tintStrength;

void main() {
    vec4 color = texture(screenTexture, TexCoord);
    
    // Apply color tint
    color.rgb = mix(color.rgb, color.rgb * tintColor, tintStrength);
    
    FragColor = color;
}
