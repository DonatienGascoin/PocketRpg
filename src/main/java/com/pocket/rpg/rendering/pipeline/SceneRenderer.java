package com.pocket.rpg.rendering.pipeline;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.batch.BatchRenderer;
import lombok.Getter;

/**
 * Renders scene content: sprites, tilemaps, entities.
 * <p>
 * Uses SpriteBatch with deferred batching and global z-sort.
 * All renderables are submitted first, then sorted by z-index and drawn.
 * <p>
 * This ensures correct rendering order regardless of submission order:
 * - Entities interleave correctly with tilemap layers
 * - Transparent objects render back-to-front
 */
public class SceneRenderer {

    private final RenderingConfig config;
    private final BatchRenderer batchRenderer;
    private final RenderDispatcher dispatcher;

    @Getter private boolean initialized;

    public SceneRenderer(RenderingConfig config) {
        this.config = config;
        this.batchRenderer = new BatchRenderer(config);
        this.dispatcher = new RenderDispatcher();
    }

    /**
     * Initializes the renderer with the given dimensions.
     * Must be called before render().
     *
     * @param width  Render width in pixels
     * @param height Render height in pixels
     */
    public void init(int width, int height) {
        if (initialized) return;
        batchRenderer.init(width, height);
        initialized = true;
    }

    /**
     * Renders all renderables to the currently bound target.
     * <p>
     * Renderables are submitted to the batch, then globally sorted by z-index
     * and drawn in a single pass.
     *
     * @param renderables Objects to render (may be null or empty)
     * @param camera      Camera for view/projection matrices and frustum culling
     */
    public void render(Iterable<Renderable> renderables, RenderCamera camera) {
        if (!initialized) {
            System.err.println("[SceneRenderer] Not initialized - call init() first");
            return;
        }

        if (renderables == null || camera == null) {
            return;
        }

        dispatcher.beginFrame(camera);
        // Begin batch with camera matrices (clearColor not used, pass null)
        batchRenderer.beginWithMatrices(
                camera.getProjectionMatrix(),
                camera.getViewMatrix(),
                null
        );


        SpriteBatch batch = batchRenderer.getBatch();

        // Submit all renderables (deferred - not drawn yet)
        for (Renderable renderable : renderables) {
            dispatcher.submit(renderable, batch, camera);
        }

        // End calls batch.end() which sorts by z-index and draws all batches
        batchRenderer.end();
    }

    /**
     * Resizes the internal projection.
     * Note: When using camera matrices via render(), this is not needed
     * as the camera provides projection. This updates the fallback projection.
     *
     * @param width  New width in pixels
     * @param height New height in pixels
     */
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            batchRenderer.setProjection(width, height);
        }
    }

    /**
     * Releases all resources.
     */
    public void destroy() {
        batchRenderer.destroy();
        initialized = false;
    }
}