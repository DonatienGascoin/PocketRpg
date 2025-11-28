#type vertex
#version 330 core

layout (location = 0) in vec2 aPos;      // Already in world space!
layout (location = 1) in vec2 aTexCoord;

out vec2 TexCoord;

uniform mat4 projection;
uniform mat4 view;
// No model matrix - vertices are pre-transformed!

void main()
{
    gl_Position = projection * view * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
}

#type fragment
#version 330 core

in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D textureSampler;

void main()
{
    FragColor = texture(textureSampler, TexCoord);
}
