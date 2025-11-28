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
uniform bool horizontal;
uniform float bloomThreshold;
uniform float bloomIntensity;
uniform vec2 texelSize;

// Gaussian blur weights for 5-tap kernel
const float weight[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec3 color = texture(screenTexture, TexCoord).rgb;
    
    // Extract bright areas (first pass only)
    if (!horizontal) {
        float brightness = dot(color, vec3(0.2126, 0.7152, 0.0722));
        if (brightness < bloomThreshold) {
            color = vec3(0.0);
        }
    }
    
    // Apply Gaussian blur
    vec3 result = color * weight[0];
    
    if (horizontal) {
        for(int i = 1; i < 5; ++i) {
            result += texture(screenTexture, TexCoord + vec2(texelSize.x * float(i), 0.0)).rgb * weight[i];
            result += texture(screenTexture, TexCoord - vec2(texelSize.x * float(i), 0.0)).rgb * weight[i];
        }
    } else {
        for(int i = 1; i < 5; ++i) {
            result += texture(screenTexture, TexCoord + vec2(0.0, texelSize.y * float(i))).rgb * weight[i];
            result += texture(screenTexture, TexCoord - vec2(0.0, texelSize.y * float(i))).rgb * weight[i];
        }
    }
    
    FragColor = vec4(result * bloomIntensity, 1.0);
}
