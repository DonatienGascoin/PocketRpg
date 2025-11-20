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
uniform float vignetteIntensity;
uniform float desaturationAmount;

void main() {
    vec4 color = texture(screenTexture, TexCoord);
    vec2 uv = TexCoord - 0.5;

    // Vignette calculation
    float vignette = 1.0 - length(uv) * vignetteIntensity;
    color.rgb *= vignette;

    // Desaturation/color correction
    float avg = (color.r + color.g + color.b) / 3.0;
    color.rgb = mix(color.rgb, vec3(avg), desaturationAmount);

    FragColor = color;
}