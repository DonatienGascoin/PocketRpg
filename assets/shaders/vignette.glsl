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
uniform float vignetteStrength;

void main() {
    vec4 color = texture(screenTexture, TexCoord);
    vec2 uv = TexCoord - 0.5;

    // Vignette calculation
    // vignetteIntensity controls how far the vignette reaches (lower = only corners)
    // vignetteStrength controls how dark it gets (0.0 = no darkening, 1.0 = full black)
    float vignetteFactor = length(uv) * vignetteIntensity;
    float vignette = 1.0 - (vignetteFactor * vignetteStrength);
    vignette = clamp(vignette, 0.0, 1.0);
    color.rgb *= vignette;

    FragColor = color;
}
