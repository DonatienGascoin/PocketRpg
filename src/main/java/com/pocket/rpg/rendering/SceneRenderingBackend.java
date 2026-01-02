package com.pocket.rpg.rendering;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.culling.CullingSystem;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Shared rendering backend for all scene rendering contexts.
 * <p>
 * Owns the core rendering resources (BatchRenderer, CullingSystem) and provides
 * a unified API for rendering tilemaps and sprites. Used by:
 * <ul>
 *   <li>EditorSceneRenderer - Scene panel editing</li>
 *   <li>GamePreviewRenderer - Game panel preview</li>
 *   <li>RenderPipeline - Runtime game rendering</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * backend.begin(camera, projection, view);
 * backend.renderTilemap(tilemap, tint);
 * backend.getSpriteBatch().draw(...);
 * backend.end();
 * </pre>
 */
@Getter
public class SceneRenderingBackend {

    private final BatchRenderer batchRenderer;

    private final CullingSystem cullingSystem;

    private final RenderingConfig config;

    /**
     * -- GETTER --
     *  Checks if initialized.
     */
    private boolean initialized = false;
    /**
     * -- GETTER --
     *  Checks if currently in a render pass.
     */
    private boolean rendering = false;

    public SceneRenderingBackend(RenderingConfig config) {
        this.config = config;
        this.batchRenderer = new BatchRenderer(config);
        this.cullingSystem = new CullingSystem();
    }

    /**
     * Initializes rendering resources.
     *
     * @param width  Viewport width
     * @param height Viewport height
     */
    public void init(int width, int height) {
        if (initialized) return;

        batchRenderer.init(width, height);
        initialized = true;
    }

    /**
     * Updates projection for viewport resize.
     */
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            batchRenderer.setProjection(width, height);
        }
    }

    /**
     * Begins a render pass with the given camera.
     *
     * @param camera     Camera for culling (implements RenderCamera)
     * @param projection Projection matrix
     * @param view       View matrix
     */
    public void begin(RenderCamera camera, Matrix4f projection, Matrix4f view) {
        if (rendering) {
            throw new IllegalStateException("Already rendering! Call end() first.");
        }

        if (!initialized) {
            throw new IllegalStateException("Backend not initialized! Call init() first.");
        }

        // Update culling
        cullingSystem.updateFrame(camera);

        // Setup GL state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Begin batch
        batchRenderer.beginWithMatrices(projection, view, null);

        rendering = true;
    }

    /**
     * Begins a render pass using camera matrices directly.
     */
    public void begin(RenderCamera camera) {
        begin(camera, camera.getProjectionMatrix(), camera.getViewMatrix());
    }

    /**
     * Renders a tilemap with automatic frustum culling.
     *
     * @param tilemap Tilemap to render
     * @param tint    Tint color
     */
    public void renderTilemap(TilemapRenderer tilemap, Vector4f tint) {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        if (tilemap == null || tilemap.allChunks().isEmpty()) {
            return;
        }

        List<long[]> visibleChunks = cullingSystem.getVisibleChunks(tilemap);

        if (!visibleChunks.isEmpty()) {
            batchRenderer.drawTilemap(tilemap, visibleChunks, tint);
        }
    }

    /**
     * Renders a tilemap with white tint.
     */
    public void renderTilemap(TilemapRenderer tilemap) {
        renderTilemap(tilemap, new Vector4f(1f, 1f, 1f, 1f));
    }

    /**
     * Gets the SpriteBatch for direct sprite rendering.
     * <p>
     * Use for rendering entities or custom sprites:
     * <pre>
     * backend.getSpriteBatch().draw(sprite, x, y, w, h, z, tint);
     * </pre>
     */
    public SpriteBatch getSpriteBatch() {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }
        return batchRenderer.getBatch();
    }

    /**
     * Ends the render pass and flushes all batched sprites.
     */
    public void end() {
        if (!rendering) {
            throw new IllegalStateException("Not rendering! Call begin() first.");
        }

        batchRenderer.end();
        rendering = false;
    }

    /**
     * Destroys rendering resources.
     */
    public void destroy() {
        batchRenderer.destroy();
        initialized = false;
        rendering = false;
    }
}