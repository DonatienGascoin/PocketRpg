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

uniform sampler2D mainTexture;
uniform sampler2D emissiveTexture;
uniform float bloomIntensity;
uniform vec2 texelSize;

// Gaussian blur weights for simple blur
const float weight[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

vec3 blurEmissive(vec2 uv) {
    // Simple 2-pass blur approximation (combined horizontal + vertical)
    vec3 result = texture(emissiveTexture, uv).rgb * weight[0];
    
    // Horizontal and vertical samples combined
    for(int i = 1; i < 5; i++) {
        float offset = float(i);
        
        // Horizontal
        result += texture(emissiveTexture, uv + vec2(texelSize.x * offset, 0.0)).rgb * weight[i] * 0.5;
        result += texture(emissiveTexture, uv - vec2(texelSize.x * offset, 0.0)).rgb * weight[i] * 0.5;
        
        // Vertical
        result += texture(emissiveTexture, uv + vec2(0.0, texelSize.y * offset)).rgb * weight[i] * 0.5;
        result += texture(emissiveTexture, uv - vec2(0.0, texelSize.y * offset)).rgb * weight[i] * 0.5;
    }
    
    return result;
}

void main() {
    // Sample main scene
    vec3 mainColor = texture(mainTexture, TexCoord).rgb;
    
    // Sample and blur emissive
    vec3 emissiveGlow = blurEmissive(TexCoord);
    
    // Add bloom to main color
    vec3 finalColor = mainColor + (emissiveGlow * bloomIntensity);
    
    FragColor = vec4(finalColor, 1.0);
}
