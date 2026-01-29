#type vertex
#version 330 core

layout (location = 0) in vec2 aPos;

out vec2 fragPos;  // Position in normalized [0, 1] space

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);

    // Convert from NDC [-1, 1] to normalized [0, 1] for fragment shader
    fragPos = (aPos + 1.0) * 0.5;
}

#type fragment
#version 330 core

in vec2 fragPos;  // Position in [0, 1] space
out vec4 FragColor;

// Uniforms
uniform vec4 uColor;           // RGBA color
uniform int uShapeType;        // 0=fullscreen, 3=luma wipe
uniform sampler2D uLumaTexture; // Grayscale luma texture for wipe pattern
uniform float uCutoff;          // Luma cutoff threshold (0.0 to 1.0)

void main() {
    bool shouldDraw = false;

    if (uShapeType == 0) {
        // Fullscreen quad - always draw
        shouldDraw = true;

    } else if (uShapeType == 3) {
        // Luma wipe - sample grayscale texture and compare against cutoff
        float luma = texture(uLumaTexture, fragPos).r;
        shouldDraw = (luma < uCutoff);
    }

    if (shouldDraw) {
        FragColor = uColor;
    } else {
        discard;
    }
}
