package com.pocket.rpg.rendering.renderers;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.Shader;
import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.rendering.stats.BatchStatistics;
import com.pocket.rpg.rendering.stats.StatisticsReporter;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Batched renderer that extends the original Renderer.
 * Uses SpriteBatch internally for efficient rendering.
 *
 * <h2>Supported Renderables</h2>
 * <ul>
 *   <li>{@link SpriteRenderer} - Individual sprites</li>
 *   <li>{@link TilemapRenderer} - Tile-based maps (rendered via chunk submission)</li>
 * </ul>
 */
public class BatchRenderer extends Renderer {

    @Getter
    private SpriteBatch batch;
    private Shader batchShader;
    private StatisticsReporter statisticsReporter;
    private int statisticsInterval;
    private int frameCounter = 0;

    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private boolean projectionDirty = true;
    private boolean viewDirty = true;

    public BatchRenderer(RenderingConfig config) {
        this.config = config;
        if (config.isEnableStatistics()) {
            this.statisticsReporter = config.getReporter();
        }
    }

    @Override
    public void init(int gameWidth, int gameHeight) {
        // Create batch
        batch = new SpriteBatch(config);

        // Create shader
        batchShader = new Shader("gameData/assets/shaders/batch_sprite.glsl");
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

    public void setStatisticsReporter(StatisticsReporter reporter, int intervalFrames) {
        this.statisticsReporter = reporter;
        this.statisticsInterval = intervalFrames;
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
        glDisable(GL_DEPTH_TEST);
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
    public void drawSpriteRenderer(SpriteRenderer spriteRenderer, Vector4f globalTintColor) {
        if (spriteRenderer == null || spriteRenderer.getSprite() == null) {
            return;
        }

        // Submit to batch (no immediate rendering!)
        batch.submit(spriteRenderer, globalTintColor);
    }

    // ========================================================================
    // TILEMAP RENDERING
    // ========================================================================

    /**
     * Renders visible chunks of a tilemap.
     * Called by RenderPipeline after chunk culling.
     *
     * @param tilemapRenderer The tilemap to render
     * @param visibleChunks   List of visible chunk coordinates [cx, cy]
     */
    public void drawTilemap(TilemapRenderer tilemapRenderer, List<long[]> visibleChunks) {
        drawTilemap(tilemapRenderer, visibleChunks, DEFAULT_TINT_COLOR);
    }

    /**
     * Renders visible chunks of a tilemap.
     * Called by RenderPipeline after chunk culling.
     *
     * @param tilemapRenderer The tilemap to render
     * @param visibleChunks   List of visible chunk coordinates [cx, cy]
     */
    public void drawTilemap(TilemapRenderer tilemapRenderer, List<long[]> visibleChunks, Vector4f globalTintColor) {
        if (tilemapRenderer == null || visibleChunks == null || visibleChunks.isEmpty()) {
            return;
        }

        for (long[] chunkCoord : visibleChunks) {
            int cx = (int) chunkCoord[0];
            int cy = (int) chunkCoord[1];

            // Submit entire chunk to batch
            batch.submitChunk(tilemapRenderer, cx, cy, globalTintColor);

            // Clear dirty flag for this chunk (if using static batching)
            if (tilemapRenderer.isStatic()) {
                tilemapRenderer.clearChunkDirty(cx, cy);
            }
        }
    }

    @Override
    public void end() {
        // Flush batch (renders everything)
        batch.end();

        batchShader.detach();

        // Report statistics if configured
        reportStatistics();
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
     * Reports batch statistics using the configured reporter.
     */
    private void reportStatistics() {
        if (statisticsReporter == null) {
            return;
        }

        frameCounter++;
        if (frameCounter >= statisticsInterval) {
            // Create statistics object
            BatchStatistics stats = new BatchStatistics(
                    batch.getTotalSprites(),
                    batch.getStaticSpritesRendered(),
                    batch.getDynamicSpritesRendered(),
                    batch.getDrawCalls(),
                    batch.getSortingStrategy()
            );

            statisticsReporter.report(stats);
            frameCounter = 0;
        }
    }
}