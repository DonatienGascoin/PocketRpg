#type vertex
#version 330 core

layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec4 aColor;     // Carries encoded entity ID

out vec2 TexCoord;
flat out vec4 Color;                      // flat = no interpolation (entity ID data)

uniform mat4 projection;
uniform mat4 view;

void main()
{
    gl_Position = projection * view * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
    Color = aColor;
}

#type fragment
#version 330 core

in vec2 TexCoord;
flat in vec4 Color;       // Entity ID encoded in RGB (flat = no interpolation)
out vec4 FragColor;

uniform sampler2D textureSampler;

void main()
{
    float alpha = texture(textureSampler, TexCoord).a;
    if (alpha < 0.5) discard;             // Transparent pixel = not pickable
    FragColor = vec4(Color.rgb, 1.0);     // Output entity ID as flat color
}
