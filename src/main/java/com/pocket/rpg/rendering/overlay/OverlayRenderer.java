package com.pocket.rpg.rendering.overlay;

import com.pocket.rpg.rendering.resources.Shader;
import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL implementation of OverlayRenderer.
 * Uses shader-based rendering for optimal performance.
 * <p>
 * Features:
 * - Fullscreen color overlay
 * - Luma wipe transitions using grayscale textures
 * - No per-frame allocations
 * <p>
 * Performance: Single fullscreen quad, all effects done in shader.
 */
public class OverlayRenderer implements com.pocket.rpg.rendering.core.OverlayRenderer {

    private Shader shader;
    private int vao;
    private int vbo;
    @Getter
    private boolean initialized = false;

    // Screen size (kept for potential future use)
    private int screenWidth = 1920;
    private int screenHeight = 1080;

    // Shape types (must match shader)
    private static final int SHAPE_FULLSCREEN = 0;
    private static final int SHAPE_LUMA = 3;

    @Override
    public void init() {
        if (initialized) {
            System.err.println("WARNING: OpenGLOverlayRenderer already initialized");
            return;
        }

        // Create shader with luma wipe support
        shader = new Shader("gameData/assets/shaders/overlayQuad.glsl");
        shader.compileAndLink();

        // Create fullscreen quad (static, never changes!)
        createFullscreenQuad();

        initialized = true;
        System.out.println("OpenGLOverlayRenderer initialized with shader-based rendering");
    }

    /**
     * Updates screen size.
     * Call this when window resizes.
     *
     * @param width  screen width in pixels
     * @param height screen height in pixels
     */
    public void setScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    @Override
    public void drawFullscreenQuad(Vector4f color) {
        if (!initialized) {
            System.err.println("ERROR: OpenGLOverlayRenderer not initialized");
            return;
        }

        if (color == null) {
            System.err.println("ERROR: Cannot draw fullscreen quad with null color");
            return;
        }

        shader.use();
        shader.uploadVec4f("uColor", color);
        shader.uploadInt("uShapeType", SHAPE_FULLSCREEN);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.detach();
    }

    @Override
    public void drawLumaWipe(Vector4f color, float cutoff, int textureId) {
        if (!initialized) {
            System.err.println("ERROR: OpenGLOverlayRenderer not initialized");
            return;
        }

        shader.use();

        // Bind the luma texture to texture slot 0
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        shader.uploadInt("uLumaTexture", 0);
        shader.uploadFloat("uCutoff", cutoff);
        shader.uploadVec4f("uColor", color);
        shader.uploadInt("uShapeType", SHAPE_LUMA);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);

        shader.detach();
    }

    /**
     * Creates a fullscreen quad in normalized device coordinates.
     * This is created ONCE and never modified (static geometry).
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
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW); // STATIC - never changes!

        // Position attribute (location = 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);

        glBindVertexArray(0);
    }

    @Override
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
        System.out.println("OpenGLOverlayRenderer destroyed");
    }
}
