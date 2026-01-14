package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.resources.Shader;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.resources.Texture;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL33.*;

/**
 * Rendering backend for UI Designer preview.
 * <p>
 * Renders UI elements to a texture using SpriteBatch for consistent rendering
 * with runtime. The texture can then be displayed in ImGui via ImGui.image().
 * <p>
 * Uses screen-space coordinates (origin top-left, Y-down) to match UI system.
 *
 * <h2>Usage</h2>
 * <pre>
 * backend.begin(canvasWidth, canvasHeight);
 * backend.drawSprite(sprite, x, y, width, height, tint);
 * backend.drawRect(x, y, width, height, color);
 * backend.end();
 * int textureId = backend.getOutputTexture();
 * </pre>
 */
public class UIRenderingBackend {

    private final RenderingConfig config;

    @Getter
    private EditorFramebuffer framebuffer;
    private SpriteBatch spriteBatch;
    private Shader batchShader;

    private Matrix4f projectionMatrix;

    private int canvasWidth;
    private int canvasHeight;

    @Getter
    private boolean initialized = false;
    private boolean rendering = false;

    // Solid color sprite for rectangles
    private Sprite whitePixel;

    public UIRenderingBackend(RenderingConfig config) {
        this.config = config;
    }

    /**
     * Initializes rendering resources.
     *
     * @param width  Initial canvas width
     * @param height Initial canvas height
     */
    public void init(int width, int height) {
        if (initialized) return;

        this.canvasWidth = width;
        this.canvasHeight = height;

        framebuffer = new EditorFramebuffer(width, height);
        framebuffer.init();

        spriteBatch = new SpriteBatch(config);

        batchShader = new Shader("gameData/assets/shaders/batch_sprite.glsl");
        batchShader.compileAndLink();

        projectionMatrix = new Matrix4f();
        updateProjection();

        createWhitePixelTexture();

        initialized = true;
        System.out.println("UIRenderingBackend initialized (" + width + "x" + height + ")");
    }

    /**
     * Resizes the canvas.
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (width == canvasWidth && height == canvasHeight) return;

        this.canvasWidth = width;
        this.canvasHeight = height;

        if (framebuffer != null) {
            framebuffer.destroy();
        }
        framebuffer = new EditorFramebuffer(width, height);
        framebuffer.init();

        updateProjection();
    }

    /**
     * Updates the orthographic projection for screen-space rendering.
     * Origin at top-left, Y increases downward.
     */
    private void updateProjection() {
        // Screen-space: origin top-left, Y-down
        projectionMatrix.identity().ortho(
                0, canvasWidth,      // left, right
                canvasHeight, 0,     // bottom, top (flipped for Y-down)
                -1000f, 1000f        // near, far
        );
    }

    /**
     * Creates a 1x1 white texture for drawing solid rectangles.
     * 
     * NOTE: Requires Texture constructor: Texture(int textureId, int width, int height)
     * If your Texture class doesn't have this, add it or adjust accordingly.
     */
    private void createWhitePixelTexture() {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create 1x1 white pixel (RGBA bytes)
            java.nio.ByteBuffer pixels = stack.bytes((byte)255, (byte)255, (byte)255, (byte)255);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        }

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Create a Texture wrapper, then a Sprite
        Texture whiteTex = Texture.wrap(texId, 1, 1);
        whitePixel = new Sprite(whiteTex, 1, 1);
    }

    // ========================================================================
    // RENDER PASS
    // ========================================================================

    /**
     * Begins a UI render pass.
     *
     * @param width  Canvas width (will resize if different)
     * @param height Canvas height (will resize if different)
     */
    public void begin(int width, int height) {
        if (!initialized) {
            init(width, height);
        }

        if (width != canvasWidth || height != canvasHeight) {
            resize(width, height);
        }

        if (rendering) {
            throw new IllegalStateException("Already rendering! Call end() first.");
        }

        framebuffer.bind();
        glViewport(0, 0, canvasWidth, canvasHeight);

        // Clear with transparent
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Setup GL state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        // Setup shader
        batchShader.use();
        batchShader.uploadMat4f("projection", projectionMatrix);
        batchShader.uploadMat4f("view", new Matrix4f()); // Identity view
        batchShader.uploadInt("textureSampler", 0);

        spriteBatch.begin();
        rendering = true;
    }

    /**
     * Begins with default canvas size.
     */
    public void begin() {
        begin(canvasWidth, canvasHeight);
    }

    /**
     * Draws a sprite at the given screen position.
     *
     * @param sprite Sprite to draw
     * @param x      X position (screen-space, from left)
     * @param y      Y position (screen-space, from top)
     * @param width  Width in pixels
     * @param height Height in pixels
     * @param zIndex Z-order (higher = on top)
     * @param tint   Tint color
     */
    public void drawSprite(Sprite sprite, float x, float y, float width, float height,
                           float zIndex, Vector4f tint) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (sprite == null) return;

        spriteBatch.submit(sprite, x, y, width, height, zIndex, tint);
    }

    /**
     * Draws a sprite with default white tint.
     */
    public void drawSprite(Sprite sprite, float x, float y, float width, float height, float zIndex) {
        drawSprite(sprite, x, y, width, height, zIndex, new Vector4f(1f, 1f, 1f, 1f));
    }

    /**
     * Draws a sprite with rotation around a specified origin point.
     *
     * @param sprite   Sprite to draw
     * @param x        X position (screen-space, from left)
     * @param y        Y position (screen-space, from top)
     * @param width    Width in pixels
     * @param height   Height in pixels
     * @param rotation Rotation angle in degrees
     * @param originX  Rotation origin X offset from sprite position
     * @param originY  Rotation origin Y offset from sprite position
     * @param zIndex   Z-order (higher = on top)
     * @param tint     Tint color
     */
    public void drawSprite(Sprite sprite, float x, float y, float width, float height,
                           float rotation, float originX, float originY, float zIndex, Vector4f tint) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (sprite == null) return;

        spriteBatch.submit(sprite, x, y, width, height, rotation, originX, originY, zIndex, tint);
    }

    /**
     * Draws a solid colored rectangle.
     *
     * @param x      X position
     * @param y      Y position
     * @param width  Width
     * @param height Height
     * @param color  Fill color (RGBA)
     * @param zIndex Z-order
     */
    public void drawRect(float x, float y, float width, float height, Vector4f color, float zIndex) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (whitePixel != null) {
            spriteBatch.submit(whitePixel, x, y, width, height, zIndex, color);
        }
    }

    /**
     * Draws a solid colored rectangle with rotation.
     *
     * @param x        X position
     * @param y        Y position
     * @param width    Width
     * @param height   Height
     * @param rotation Rotation angle in degrees
     * @param originX  Rotation origin X offset from rect position
     * @param originY  Rotation origin Y offset from rect position
     * @param color    Fill color (RGBA)
     * @param zIndex   Z-order
     */
    public void drawRect(float x, float y, float width, float height,
                         float rotation, float originX, float originY, Vector4f color, float zIndex) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (whitePixel != null) {
            spriteBatch.submit(whitePixel, x, y, width, height, rotation, originX, originY, zIndex, color);
        }
    }

    /**
     * Draws a rectangle outline.
     *
     * @param x         X position
     * @param y         Y position
     * @param width     Width
     * @param height    Height
     * @param color     Line color
     * @param thickness Line thickness
     * @param zIndex    Z-order
     */
    public void drawRectOutline(float x, float y, float width, float height,
                                Vector4f color, float thickness, float zIndex) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (whitePixel == null) return;

        // Top
        spriteBatch.submit(whitePixel, x, y, width, thickness, zIndex, color);
        // Bottom
        spriteBatch.submit(whitePixel, x, y + height - thickness, width, thickness, zIndex, color);
        // Left
        spriteBatch.submit(whitePixel, x, y, thickness, height, zIndex, color);
        // Right
        spriteBatch.submit(whitePixel, x + width - thickness, y, thickness, height, zIndex, color);
    }

    /**
     * Ends the render pass and flushes to texture.
     */
    public void end() {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        spriteBatch.end();
        batchShader.detach();
        framebuffer.unbind();

        rendering = false;
    }

    // ========================================================================
    // OUTPUT
    // ========================================================================

    /**
     * Gets the rendered texture ID for display in ImGui.
     */
    public int getOutputTexture() {
        return framebuffer != null ? framebuffer.getTextureId() : 0;
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        if (spriteBatch != null) {
            spriteBatch.destroy();
            spriteBatch = null;
        }

        if (batchShader != null) {
            batchShader.delete();
            batchShader = null;
        }

        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }

        initialized = false;
        rendering = false;

        System.out.println("UIRenderingBackend destroyed");
    }
}
