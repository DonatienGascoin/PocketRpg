package com.pocket.rpg.rendering;

import com.pocket.rpg.components.Camera;
import com.pocket.rpg.components.SpriteRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Batched renderer that extends the original Renderer.
 * Uses SpriteBatch internally for efficient rendering.
 */
public class BatchRenderer extends Renderer {

    private SpriteBatch batch;
    private Shader batchShader;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    @Override
    public void init(int gameWidth, int gameHeight) {
        // Create batch
        batch = new SpriteBatch();

        // Create shader
        batchShader = new Shader("assets/shaders/batch_sprite.glsl");
        batchShader.compileAndLink();

        // Initialize matrices
        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();

        setProjection(gameWidth, gameHeight);

        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        System.out.println("BatchRenderer initialized with game resolution: " + gameWidth + "x" + gameHeight);
    }

    @Override
    public void setProjection(int width, int height) {
        if (width <= 0 || height <= 0) {
            System.err.println("WARNING: Invalid projection dimensions: " + width + "x" + height);
            return;
        }

        projectionMatrix.identity().ortho(0, width, height, 0, -1, 1);
        projectionDirty = true;
    }

    @Override
    public void beginWithMatrices(Matrix4f projection, Matrix4f view, Vector4f clearColor) {
        if (projection == null || view == null) {
            System.err.println("ERROR: Cannot begin rendering with null matrices");
            return;
        }

        this.projectionMatrix = new Matrix4f(projection);
        this.viewMatrix = new Matrix4f(view);
        this.projectionDirty = true;
        this.viewDirty = true;

        begin();
    }

    @Override
    public void begin() {
        batchShader.use();

        // Upload projection/view matrices once per frame
        if (projectionDirty) {
            batchShader.uploadMat4f("projection", projectionMatrix);
            projectionDirty = false;
        }

        if (viewDirty) {
            batchShader.uploadMat4f("view", viewMatrix);
            viewDirty = false;
        }

        batchShader.uploadInt("textureSampler", 0);

        // Start batching
        batch.begin();
    }

    @Override
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return;
        }

        // Submit to batch (no immediate rendering!)
        batch.submit(spriteRenderer);
    }

    @Override
    public void end() {
        // Flush batch (renders everything)
        batch.end();

        batchShader.detach();
    }

    @Override
    public void destroy() {
        if (batch != null) {
            batch.destroy();
        }
        if (batchShader != null) {
            batchShader.delete();
        }
    }

    /**
     * Gets the underlying SpriteBatch for configuration.
     */
    public SpriteBatch getBatch() {
        return batch;
    }

    /**
     * Prints batch statistics.
     */
    public void printBatchStats() {
        System.out.printf("Batch Stats: %d sprites in %d draw calls (%.1f sprites/call)%n",
                batch.getTotalSprites(), batch.getDrawCalls(),
                batch.getTotalSprites() / (float) Math.max(1, batch.getDrawCalls()));
        System.out.printf("  Static: %d, Dynamic: %d%n",
                batch.getStaticSprites(), batch.getDynamicSprites());
    }
}