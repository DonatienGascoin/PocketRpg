package com.pocket.rpg.engine;


import static org.lwjgl.opengl.GL33.*;

/**
 * Utility class for shader compilation and program linking with proper error handling.
 */
public class ShaderUtils {

    /**
     * Compiles and links a vertex and fragment shader into a program.
     *
     * @param vertexSource   GLSL source code for the vertex shader
     * @param fragmentSource GLSL source code for the fragment shader
     * @return The compiled and linked shader program ID
     * @throws RuntimeException if compilation or linking fails
     */
    public static int createShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        // Check for linking errors
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            throw new RuntimeException("Shader program linking failed:\n" + log);
        }

        // Clean up shader objects (no longer needed after linking)
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        return program;
    }

    /**
     * Compiles a single shader from source.
     *
     * @param shaderType GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
     * @param source     GLSL source code
     * @return The compiled shader ID
     * @throws RuntimeException if compilation fails
     */
    private static int compileShader(int shaderType, String source) {
        int shader = glCreateShader(shaderType);
        glShaderSource(shader, source);
        glCompileShader(shader);

        // Check for compilation errors
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String shaderTypeName = (shaderType == GL_VERTEX_SHADER) ? "Vertex" : "Fragment";
            glDeleteShader(shader);
            throw new RuntimeException(shaderTypeName + " shader compilation failed:\n" + log);
        }

        return shader;
    }
}
