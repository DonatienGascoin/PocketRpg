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
uniform vec2 texelSize;        // 1.0 / textureSize
uniform vec2 direction;        // (1,0) for horizontal, (0,1) for vertical
uniform float blurStrength;    // Multiplier for blur distance
uniform float nearSampleOffset; // Near sample offset (default: 1.5)
uniform float farSampleOffset;  // Far sample offset (default: 3.0)
uniform float sampleCount;      // Total number of samples (default: 5)

void main() {
    // Center sample
    vec4 color = texture(screenTexture, TexCoord);

    // Near samples (closer to center)
    color += texture(screenTexture, TexCoord + direction * texelSize * nearSampleOffset * blurStrength);
    color += texture(screenTexture, TexCoord - direction * texelSize * nearSampleOffset * blurStrength);

    // Far samples (further from center)
    color += texture(screenTexture, TexCoord + direction * texelSize * farSampleOffset * blurStrength);
    color += texture(screenTexture, TexCoord - direction * texelSize * farSampleOffset * blurStrength);

    // Average all samples
    FragColor = color / sampleCount;
}