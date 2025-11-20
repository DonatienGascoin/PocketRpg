package com.pocket.rpg.rendering;

import lombok.Getter;
import org.joml.*;
import org.lwjgl.BufferUtils;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform1iv;

public class Shader implements Comparable<Shader> {

    private int shaderProgramId;

    private String vertexSource, fragmentSource;
    @Getter
    private final String filePath;

    public Shader(String filepath) {
        this.filePath = filepath;
        try {
            String source = new String(Files.readAllBytes(new File(filepath).toPath()));
            String[] splitString = source.split("(#type)( )+([a-zA-Z]+)");

            // Find the first pattern after #type
            int index = source.indexOf("#type") + 6;
            int eol = source.indexOf("\n"/*System.lineSeparator()*/, index);
            String firstPattern = source.substring(index, eol).trim();

            // Find the second pattern after #type
            index = source.indexOf("#type", eol) + 6;
            eol = source.indexOf("\n"/*System.lineSeparator()*/, index);
            String secondPattern = source.substring(index, eol).trim();

            if (firstPattern.equals("vertex")) {
                vertexSource = splitString[1];
            } else if (firstPattern.equals("fragment")) {
                fragmentSource = splitString[1];
            } else {
                throw new IOException(String.format("Unexpected token '%s'", firstPattern));
            }

            if (secondPattern.equals("vertex")) {
                vertexSource = splitString[2];
            } else if (secondPattern.equals("fragment")) {
                fragmentSource = splitString[2];
            } else {
                throw new IOException(String.format("Unexpected token '%s'", secondPattern));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error: could not open shader file: " + filepath, e);
        }
    }

    public void compileAndLink() {
        int vertexId, fragmentId;

        // =========================
        // Compile shader
        // =========================

        // First load and compile vertex shader
        vertexId = glCreateShader(GL_VERTEX_SHADER);
        // Pass the shader source to the GPU
        glShaderSource(vertexId, vertexSource);
        glCompileShader(vertexId);

        // Check for errors in compilation
        int success = glGetShaderi(vertexId, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            int len = glGetShaderi(vertexId, GL_INFO_LOG_LENGTH);
            System.err.printf("Error: '%s'\n\tVertex shader compilation failed.", filePath);
            System.out.println(glGetShaderInfoLog(vertexId, len));
            assert false : "";
        }

        // First load and compile fragment shader
        fragmentId = glCreateShader(GL_FRAGMENT_SHADER);
        // Pass the shader source to the GPU
        glShaderSource(fragmentId, fragmentSource);
        glCompileShader(fragmentId);

        // Check for errors in compilation
        success = glGetShaderi(fragmentId, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            int len = glGetShaderi(fragmentId, GL_INFO_LOG_LENGTH);
            System.err.printf("Error: '%s'\n\tFragment shader compilation failed.%n", filePath);
            System.out.println(glGetShaderInfoLog(fragmentId, len));
            assert false : "";
        }

        // ======================
        // Link shader
        // ======================

        shaderProgramId = glCreateProgram();
        glAttachShader(shaderProgramId, vertexId);
        glAttachShader(shaderProgramId, fragmentId);
        glLinkProgram(shaderProgramId);

        // Check for linking error
        success = glGetProgrami(shaderProgramId, GL_LINK_STATUS);
        if (success == GL_FALSE) {
            int len = glGetProgrami(shaderProgramId, GL_INFO_LOG_LENGTH);
            System.err.printf("Error: '%s'\n\tLinking of shaders failed.", filePath);
            System.out.println(glGetProgramInfoLog(shaderProgramId, len));
            assert false : "";
        }
    }

    public void use() {
        // Bind shader program
        glUseProgram(shaderProgramId);
    }

    public void detach() {
        glUseProgram(0);
    }

    public void uploadMat4f(String varName, Matrix4f mat4) {
        int varLocation = glGetUniformLocation(shaderProgramId, varName);
        FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
        mat4.get(matBuffer);

        glUniformMatrix4fv(varLocation, false, matBuffer);
    }

    public void uploadFloatArray(String varName, float[] array) {
        int varLocation = glGetUniformLocation(shaderProgramId, varName);

        glUniformMatrix4fv(varLocation, false, array);
    }

    public void uploadMat3f(String varName, Matrix3f mat3) {
        int varLocation = getVarLocation(varName);
        FloatBuffer matBuffer = BufferUtils.createFloatBuffer(9);
        mat3.get(matBuffer);
        glUniformMatrix3fv(varLocation, false, matBuffer);
    }

    public void uploadVec4f(String varName, Vector4f vec) {
        int varLocation = getVarLocation(varName);
        glUniform4f(varLocation, vec.x, vec.y, vec.z, vec.w);
    }

    public void uploadVec3f(String varName, Vector3f vec) {
        int varLocation = getVarLocation(varName);
        glUniform3f(varLocation, vec.x, vec.y, vec.z);
    }

    public void uploadVec2f(String varName, Vector2f vec) {
        int varLocation = getVarLocation(varName);
        glUniform2f(varLocation, vec.x, vec.y);
    }

    public void uploadVec2f(String varName, float val1, float val2) {
        int varLocation = getVarLocation(varName);
        glUniform2f(varLocation, val1, val2);
    }

    public void uploadFloat(String varName, float val) {
        int varLocation = getVarLocation(varName);
        glUniform1f(varLocation, val);
    }

    public void uploadInt(String varName, int val) {
        int varLocation = getVarLocation(varName);
        glUniform1i(varLocation, val);
    }

    public void uploadTexture(String varName, int slot) {
        int varLocation = getVarLocation(varName);
        glUniform1i(varLocation, slot);
    }

    public void uploadIntArray(String varName, int[] array) {
        int varLocation = getVarLocation(varName);
        glUniform1iv(varLocation, array);
    }

    private int getVarLocation(String varName) {
        int varLocation = glGetUniformLocation(shaderProgramId, varName);
//        use();
        return varLocation;
    }

    public void delete() {
        glDeleteProgram(shaderProgramId);
    }

    @Override
    public int compareTo(Shader o) {
        return filePath.compareTo(o.getFilePath());
    }
}