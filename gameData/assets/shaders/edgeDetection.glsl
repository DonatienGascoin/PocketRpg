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
uniform float edgeThreshold;
uniform vec3 edgeColor;
uniform vec2 texelSize;

void main() {
    // Sobel operator kernels
    float sobel_x[9] = float[](
        -1.0, 0.0, 1.0,
        -2.0, 0.0, 2.0,
        -1.0, 0.0, 1.0
    );
    
    float sobel_y[9] = float[](
        -1.0, -2.0, -1.0,
         0.0,  0.0,  0.0,
         1.0,  2.0,  1.0
    );
    
    // Sample 3x3 neighborhood
    float gx = 0.0;
    float gy = 0.0;
    
    for(int i = 0; i < 3; i++) {
        for(int j = 0; j < 3; j++) {
            vec2 offset = vec2(float(i - 1), float(j - 1)) * texelSize;
            float luminance = dot(texture(screenTexture, TexCoord + offset).rgb, vec3(0.299, 0.587, 0.114));
            
            int index = i + j * 3;
            gx += luminance * sobel_x[index];
            gy += luminance * sobel_y[index];
        }
    }
    
    // Calculate edge magnitude
    float edge = sqrt(gx * gx + gy * gy);
    
    // Threshold and combine with original
    vec4 originalColor = texture(screenTexture, TexCoord);
    
    if(edge > edgeThreshold) {
        FragColor = vec4(mix(originalColor.rgb, edgeColor, 0.8), 1.0);
    } else {
        FragColor = originalColor;
    }
}
