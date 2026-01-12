package com.pocket.rpg.rendering.overlay;

import com.pocket.rpg.rendering.resources.Shader;
import lombok.Getter;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL implementation of OverlayRenderer.
 * Uses shader-based clipping for optimal performance.
 * <p>
 * Features:
 * - Zero VBO updates for wipes (shader-based clipping)
 * - True circular wipes with fragment shader
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

    // Screen size for circle calculations
    private int screenWidth = 1920;  // Default, will be updated
    private int screenHeight = 1080;

    // Shape types (must match shader)
    private static final int SHAPE_FULLSCREEN = 0;
    private static final int SHAPE_RECTANGLE = 1;
    private static final int SHAPE_CIRCLE = 2;

    @Override
    public void init() {
        if (initialized) {
            System.err.println("WARNING: OpenGLOverlayRenderer already initialized");
            return;
        }

        // Create shader with clipping support
        shader = new Shader("gameData/assets/shaders/overlayQuad.glsl");
        shader.compileAndLink();

        // Create fullscreen quad (static, never changes!)
        createFullscreenQuad();

        initialized = true;
        System.out.println("OpenGLOverlayRenderer initialized with shader-based rendering");
    }

    /**
     * Updates screen size for circle calculations.
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

    // ======================================================================
    // WIPE EFFECTS - Shader-based clipping (no VBO updates!)
    // ======================================================================

    @Override
    public void drawWipeLeft(Vector4f color, float progress) {
        progress = clampProgress(progress);
        if (progress <= 0.0f) return;

        // Clip bounds: (minX, minY, maxX, maxY) in [0, 1]
        drawRectangleClip(color, 0.0f, 0.0f, progress, 1.0f);
    }

    @Override
    public void drawWipeRight(Vector4f color, float progress) {
        progress = clampProgress(progress);
        if (progress <= 0.0f) return;

        drawRectangleClip(color, 1.0f - progress, 0.0f, 1.0f, 1.0f);
    }

    @Override
    public void drawWipeUp(Vector4f color, float progress) {
        progress = clampProgress(progress);
        if (progress <= 0.0f) return;

        drawRectangleClip(color, 0.0f, 0.0f, 1.0f, progress);
    }

    @Override
    public void drawWipeDown(Vector4f color, float progress) {
        progress = clampProgress(progress);
        if (progress <= 0.0f) return;

        drawRectangleClip(color, 0.0f, 1.0f - progress, 1.0f, 1.0f);
    }

    @Override
    public void drawCircleWipe(Vector4f color, float progress, boolean expanding) {
        progress = clampProgress(progress);
        if (progress <= 0.0f) return;

        // Calculate maximum radius to cover entire screen (diagonal)
        float maxRadius = (float) Math.sqrt(
                screenWidth * screenWidth + screenHeight * screenHeight
        ) / 2.0f;

        float radius = maxRadius * progress;
        float centerX = 0.5f;  // Center in normalized [0, 1] space
        float centerY = 0.5f;

        if (expanding) {
            // Circle grows from center
            drawCircle(color, centerX, centerY, radius, false);
        } else {
            // Draw everything except the circle (inverse)
            drawCircle(color, centerX, centerY, radius, true);
        }
    }

    /**
     * Draws with rectangle clipping (shader-based, no VBO update).
     *
     * @param color RGBA color
     * @param minX  Left edge in normalized [0, 1]
     * @param minY  Top edge in normalized [0, 1]
     * @param maxX  Right edge in normalized [0, 1]
     * @param maxY  Bottom edge in normalized [0, 1]
     */
    private void drawRectangleClip(Vector4f color, float minX, float minY, float maxX, float maxY) {
        if (!initialized) {
            System.err.println("ERROR: OpenGLOverlayRenderer not initialized");
            return;
        }

        shader.use();
        shader.uploadVec4f("uColor", color);
        shader.uploadInt("uShapeType", SHAPE_RECTANGLE);
        shader.uploadVec4f("uClipBounds", new Vector4f(minX, minY, maxX, maxY));

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.detach();
    }

    /**
     * Draws a circle (shader-based).
     *
     * @param color   RGBA color
     * @param centerX Center X in normalized [0, 1]
     * @param centerY Center Y in normalized [0, 1]
     * @param radius  Radius in pixels
     * @param inverse If true, draw everything EXCEPT the circle
     */
    private void drawCircle(Vector4f color, float centerX, float centerY, float radius, boolean inverse) {
        if (!initialized) {
            System.err.println("ERROR: OpenGLOverlayRenderer not initialized");
            return;
        }

        shader.use();
        shader.uploadVec4f("uColor", color);
        shader.uploadInt("uShapeType", SHAPE_CIRCLE);

        // Upload circle data: (centerX, centerY, radius)
        shader.uploadVec3f("uCircleData", new org.joml.Vector3f(centerX, centerY, radius));
        shader.uploadVec2f("uScreenSize", new Vector2f(screenWidth, screenHeight));
        shader.uploadBoolean("uInverseCircle", inverse);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.detach();
    }

    /**
     * Clamps progress to [0, 1] range.
     */
    private float clampProgress(float progress) {
        return Math.max(0.0f, Math.min(1.0f, progress));
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