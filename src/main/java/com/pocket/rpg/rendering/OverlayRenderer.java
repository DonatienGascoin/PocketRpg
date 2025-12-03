package com.pocket.rpg.rendering;

import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders fullscreen colored overlays.
 * Used for transition effects, fade effects, and other screen overlays.
 * <p>
 * Separated from Renderer to keep concerns focused:
 * - Renderer: Sprite rendering
 * - OverlayRenderer: Screen overlays
 */
public class OverlayRenderer {

    private Shader shader;
    private int vao;
    private int vbo;
    @Getter
    private boolean initialized = false;

    /**
     * Initializes the overlay renderer.
     * Must be called before rendering.
     */
    public void init() {
        if (initialized) {
            System.err.println("WARNING: OverlayRenderer already initialized");
            return;
        }

        // Create shader for colored fullscreen quads
        shader = new Shader("gameData/assets/shaders/fullscreenQuad.glsl");
        shader.compileAndLink();

        // Create fullscreen quad mesh (in normalized device coordinates)
        createFullscreenQuad();

        initialized = true;
        System.out.println("OverlayRenderer initialized");
    }

    /**
     * Draws a fullscreen quad with the specified color.
     * Alpha channel controls transparency.
     *
     * @param color RGBA color (use w component for alpha: 0=transparent, 1=opaque)
     */
    public void drawFullscreenQuad(Vector4f color) {
        if (!initialized) {
            System.err.println("ERROR: OverlayRenderer not initialized");
            return;
        }

        if (color == null) {
            System.err.println("ERROR: Cannot draw fullscreen quad with null color");
            return;
        }

        // Use the shader
        shader.use();
        shader.uploadVec4f("color", color);

        // Draw the fullscreen quad
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.detach();
    }

    /**
     * Creates a fullscreen quad in normalized device coordinates.
     * Covers the entire screen from (-1, -1) to (1, 1).
     */
    private void createFullscreenQuad() {
        // Fullscreen quad vertices in NDC (Normalized Device Coordinates)
        // Two triangles covering the entire screen
        float[] vertices = {
                // Triangle 1
                -1.0f, 1.0f,  // Top-left
                -1.0f, -1.0f,  // Bottom-left
                1.0f, -1.0f,  // Bottom-right

                // Triangle 2
                -1.0f, 1.0f,  // Top-left
                1.0f, -1.0f,  // Bottom-right
                1.0f, 1.0f   // Top-right
        };

        // Create VAO and VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        // Position attribute (location = 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);

        glBindVertexArray(0);
    }

    /**
     * Cleans up OpenGL resources.
     * Must be called when the overlay renderer is no longer needed.
     */
    public void destroy() {
        if (!initialized) {
            return;
        }

        if (shader != null) {
            shader.delete();
            shader = null;
        }

        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }

        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }

        initialized = false;
        System.out.println("OverlayRenderer destroyed");
    }
}