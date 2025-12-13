#type vertex
#version 330 core

layout (location = 0) in vec2 aPos;      // Already in world space!
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec4 aColor;

out vec2 TexCoord;
out vec4 Color;

uniform mat4 projection;
uniform mat4 view;
// No model matrix - vertices are pre-transformed!

void main()
{
    gl_Position = projection * view * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
    Color = aColor;
}

#type fragment
#version 330 core

in vec2 TexCoord;
in vec4 Color;
out vec4 FragColor;

uniform sampler2D textureSampler;

void main()
{
    vec4 texColor = texture(textureSampler, TexCoord);
    FragColor = texColor * Color;  // Multiply by vertex color
}
