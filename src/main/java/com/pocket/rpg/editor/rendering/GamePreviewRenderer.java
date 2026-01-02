package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameCamera;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.rendering.PreviewCamera;
import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.rendering.renderers.BatchRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders EditorScene preview using game camera settings.
 * <p>
 * Used by GameViewPanel to show a static preview of what the game
 * will look like when stopped (similar to Unity's Game view).
 * <p>
 * Differences from EditorSceneRenderer:
 * - Uses game resolution (from GameConfig)
 * - Uses SceneCameraSettings for camera position/size
 * - All visible layers rendered at full opacity (no dimmed mode)
 */
public class GamePreviewRenderer {

    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    private EditorFramebuffer framebuffer;
    private BatchRenderer batchRenderer;
    private EntityRenderer entityRenderer;
    private PreviewCamera previewCamera;
    private ViewportConfig viewportConfig;

    private boolean initialized = false;
    private boolean dirty = true;

    public GamePreviewRenderer(GameConfig gameConfig, RenderingConfig renderingConfig) {
        this.gameConfig = gameConfig;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Initializes rendering resources.
     */
    public void init() {
        if (initialized) return;

        int width = gameConfig.getGameWidth();
        int height = gameConfig.getGameHeight();

        // Create framebuffer at game resolution
        framebuffer = new EditorFramebuffer(width, height);
        framebuffer.init();

        // Create viewport config from game settings
        viewportConfig = new ViewportConfig(gameConfig);

        // Create preview camera (will be configured per-render from scene settings)
        previewCamera = new PreviewCamera(viewportConfig);

        // Create batch renderer
        batchRenderer = new BatchRenderer(renderingConfig);
        batchRenderer.init(width, height);

        // Create entity renderer
        entityRenderer = new EntityRenderer();

        initialized = true;
        System.out.println("GamePreviewRenderer initialized (" + width + "x" + height + ")");
    }

    /**
     * Renders the scene preview.
     *
     * @param scene EditorScene to render
     */
    public void render(EditorScene scene) {
        if (!initialized) {
            init();
        }

        if (scene == null) {
            renderEmpty();
            return;
        }

        // Update camera from scene settings
        SceneCameraSettings settings = scene.getCameraSettings();

        previewCamera.applySceneSettings(settings.getPosition(), settings.getOrthographicSize());

        // Bind framebuffer and set viewport
        framebuffer.bind();
        glViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());

        // Clear with dark background
        Vector4f bgColor = renderingConfig.getClearColor();
        framebuffer.clear(bgColor.x, bgColor.y, bgColor.z, 1.0f);

        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Get camera matrices
        Matrix4f projection = previewCamera.getProjectionMatrix();
        Matrix4f view = previewCamera.getViewMatrix();

        // Single batch pass for proper z-ordering
        batchRenderer.beginWithMatrices(projection, view, null);

        // 1. Render tilemap layers
        renderTilemapLayers(scene);

        // 2. Render entities (uses batch internally)
        SpriteBatch batch = batchRenderer.getBatch();
        entityRenderer.render(batch, scene);

        // End batch (flushes and sorts by zIndex)
        batchRenderer.end();

        // Unbind framebuffer
        framebuffer.unbind();

        dirty = false;
    }

    /**
     * Renders an empty preview (no scene loaded).
     */
    private void renderEmpty() {
        framebuffer.bind();
        framebuffer.clear(0.1f, 0.1f, 0.1f, 1.0f);
        framebuffer.unbind();
        dirty = false;
    }

    /**
     * Renders all visible tilemap layers in z-order.
     * Unlike EditorSceneRenderer, renders all visible layers at full opacity.
     */
    private void renderTilemapLayers(EditorScene scene) {
        int layerCount = scene.getLayerCount();
        if (layerCount == 0) return;

        // Build list of (layerIndex, zIndex) pairs for sorting
        List<int[]> layerOrder = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer != null && layer.isVisible()) {
                layerOrder.add(new int[]{i, layer.getZIndex()});
            }
        }

        // Sort by zIndex (lower renders first = behind)
        layerOrder.sort(Comparator.comparingInt(a -> a[1]));

        // Render each layer
        for (int[] pair : layerOrder) {
            int layerIndex = pair[0];
            TilemapLayer layer = scene.getLayer(layerIndex);
            if (layer == null) continue;

            renderTilemap(layer.getTilemap());
        }
    }

    /**
     * Renders a tilemap with frustum culling.
     */
    private void renderTilemap(TilemapRenderer tilemap) {
        if (tilemap == null) return;
        if (tilemap.allChunks().isEmpty()) return;

        // Get visible bounds from camera
        float[] bounds = previewCamera.getWorldBounds();
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

        // Render visible chunks at full opacity
        if (!visibleChunks.isEmpty()) {
            Vector4f tint = new Vector4f(1f, 1f, 1f, 1f);
            batchRenderer.drawTilemap(tilemap, visibleChunks, tint);
        }
    }

    /**
     * Gets the output texture ID for ImGui display.
     */
    public int getOutputTexture() {
        return framebuffer != null ? framebuffer.getTextureId() : 0;
    }

    /**
     * Gets the game width.
     */
    public int getWidth() {
        return gameConfig.getGameWidth();
    }

    /**
     * Gets the game height.
     */
    public int getHeight() {
        return gameConfig.getGameHeight();
    }

    /**
     * Marks the preview as needing re-render.
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Checks if preview needs re-render.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Checks if initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Destroys rendering resources.
     */
    public void destroy() {
        if (batchRenderer != null) {
            batchRenderer.destroy();
            batchRenderer = null;
        }

        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }

        entityRenderer = null;
        previewCamera = null;
        viewportConfig = null;
        initialized = false;

        System.out.println("GamePreviewRenderer destroyed");
    }
}