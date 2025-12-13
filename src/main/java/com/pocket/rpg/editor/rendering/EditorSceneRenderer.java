package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders EditorScene content to the editor framebuffer.
 * <p>
 * Handles:
 * - Rendering tilemap layers with visibility mode support
 * - Layer opacity based on visibility mode (All/Selected/Dimmed)
 * - Sprite rendering for entities
 * - Frustum culling for tilemaps
 */
public class EditorSceneRenderer {

    private final EditorFramebuffer framebuffer;
    private final RenderingConfig renderingConfig;

    private BatchRenderer batchRenderer;
    private boolean initialized = false;

    /**
     * Creates an EditorSceneRenderer.
     *
     * @param framebuffer Framebuffer to render to
     * @param renderingConfig Rendering configuration
     */
    public EditorSceneRenderer(EditorFramebuffer framebuffer, RenderingConfig renderingConfig) {
        this.framebuffer = framebuffer;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Initializes rendering resources.
     */
    public void init() {
        if (initialized) return;

        // Initialize batch renderer with config
        batchRenderer = new BatchRenderer(renderingConfig);
        batchRenderer.init(framebuffer.getWidth(), framebuffer.getHeight());

        initialized = true;
        System.out.println("EditorSceneRenderer initialized");
    }

    /**
     * Renders the editor scene to the framebuffer.
     *
     * @param scene EditorScene to render
     * @param camera Editor camera for view/projection matrices
     */
    public void render(EditorScene scene, EditorCamera camera) {
        if (!initialized) {
            init();
        }

        // Bind framebuffer
        framebuffer.bind();

        // Clear with dark gray (editor background)
        // Using a fixed color instead of game config for clarity
        framebuffer.clear(0.15f, 0.15f, 0.15f, 1.0f);

        // Enable blending for sprites
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Get camera matrices
        Matrix4f projectionMatrix = camera.getProjectionMatrix();
        Matrix4f viewMatrix = camera.getViewMatrix();

        // Render scene content
        if (scene != null) {
            renderSceneContent(scene, projectionMatrix, viewMatrix, camera);
        }

        // Unbind framebuffer
        framebuffer.unbind();
    }

    /**
     * Renders scene layers.
     */
    private void renderSceneContent(EditorScene scene, Matrix4f projection, Matrix4f view, EditorCamera camera) {
        int layerCount = scene.getLayerCount();
        if (layerCount == 0) return;

        // Build list of (layerIndex, zIndex) pairs for sorting
        List<int[]> layerOrder = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer != null) {
                layerOrder.add(new int[]{i, layer.getZIndex()});
            }
        }

        // Sort by zIndex (lower zIndex renders first = behind)
        layerOrder.sort(Comparator.comparingInt(a -> a[1]));

        // Begin batch with camera matrices
        batchRenderer.beginWithMatrices(projection, view, null);

        // Render each layer in zIndex order
        for (int[] pair : layerOrder) {
            int layerIndex = pair[0];

            // Check visibility using ORIGINAL index
            if (!scene.isLayerVisible(layerIndex)) {
                continue;
            }

            TilemapLayer layer = scene.getLayer(layerIndex);
            if (layer == null) continue;

            // Get opacity based on visibility mode
            float opacity = scene.getLayerOpacity(layerIndex);
            // Render tilemap
            renderTilemap(layer.getTilemap(), camera, opacity);
        }

        // End batch
        batchRenderer.end();
    }

    /**
     * Renders a tilemap with frustum culling.
     *
     * @param tilemap TilemapRenderer to render
     * @param camera Editor camera
     * @param opacity Layer opacity (0.0 - 1.0)
     */
    private void renderTilemap(TilemapRenderer tilemap, EditorCamera camera, float opacity) {
        if (tilemap == null) return;

        // Check if tilemap has any chunks
        if (tilemap.allChunks().isEmpty()) {
            return;
        }

        // Get visible bounds
        float[] bounds = camera.getWorldBounds();
        float left = bounds[0];
        float bottom = bounds[1];
        float right = bounds[2];
        float top = bounds[3];

        // Calculate visible chunks
        float tileSize = tilemap.getTileSize();
        int chunkSize = TilemapRenderer.TileChunk.CHUNK_SIZE;
        float chunkWorldSize = chunkSize * tileSize;

        int startCX = (int) Math.floor(left / chunkWorldSize);
        int startCY = (int) Math.floor(bottom / chunkWorldSize);
        int endCX = (int) Math.ceil(right / chunkWorldSize);
        int endCY = (int) Math.ceil(top / chunkWorldSize);

        // Collect visible chunks
        List<long[]> visibleChunks = new ArrayList<>();
        for (int cy = startCY; cy <= endCY; cy++) {
            for (int cx = startCX; cx <= endCX; cx++) {
                if (tilemap.hasChunk(cx, cy)) {
                    visibleChunks.add(new long[]{cx, cy});
                }
            }
        }

        // Render visible chunks
        if (!visibleChunks.isEmpty()) {
            if (opacity == 1f) {
                batchRenderer.drawTilemap(tilemap, visibleChunks, new Vector4f(1f, 1f, 1f, 1f));
            } else {
                batchRenderer.drawTilemap(tilemap, visibleChunks, new Vector4f(.8f, .8f, .8f, (1 - opacity)));

            }
        }
    }

    /**
     * Called when framebuffer is resized.
     */
    public void onResize(int width, int height) {
        if (batchRenderer != null && width > 0 && height > 0) {
            batchRenderer.setProjection(width, height);
        }
    }

    /**
     * Destroys rendering resources.
     */
    public void destroy() {
        if (batchRenderer != null) {
            batchRenderer.destroy();
            batchRenderer = null;
        }
        initialized = false;
    }
}