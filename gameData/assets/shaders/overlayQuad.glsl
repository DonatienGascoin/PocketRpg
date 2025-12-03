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
uniform int uShapeType;        // 0=fullscreen, 1=rectangle clip, 2=circle
uniform vec4 uClipBounds;      // For rectangle: (minX, minY, maxX, maxY) in [0, 1]
uniform vec3 uCircleData;      // For circle: (centerX, centerY, radius) - radius in pixels
uniform vec2 uScreenSize;      // Screen size in pixels for circle calculations
uniform bool uInverseCircle;   // If true, draw everything EXCEPT the circle

void main() {
    bool shouldDraw = false;
    
    if (uShapeType == 0) {
        // Fullscreen quad - always draw
        shouldDraw = true;
        
    } else if (uShapeType == 1) {
        // Rectangle clip - only draw inside bounds
        if (fragPos.x >= uClipBounds.x && fragPos.x <= uClipBounds.z &&
            fragPos.y >= uClipBounds.y && fragPos.y <= uClipBounds.w) {
            shouldDraw = true;
        }
        
    } else if (uShapeType == 2) {
        // Circle - calculate distance from center
        vec2 pixelPos = fragPos * uScreenSize;
        vec2 centerPixel = uCircleData.xy * uScreenSize;
        float radius = uCircleData.z;
        
        float dist = distance(pixelPos, centerPixel);
        
        if (uInverseCircle) {
            // Draw everything EXCEPT the circle
            shouldDraw = (dist > radius);
        } else {
            // Draw only the circle
            shouldDraw = (dist <= radius);
        }
    }
    
    if (shouldDraw) {
        FragColor = uColor;
    } else {
        discard;
    }
}
