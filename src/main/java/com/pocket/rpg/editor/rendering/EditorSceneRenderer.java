package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerUtils;
import com.pocket.rpg.editor.scene.LayerUtils.LayerRenderInfo;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.scene.LayerVisibilityMode;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.pipeline.RenderDispatcher;
import com.pocket.rpg.rendering.batch.SpriteBatch;
import com.pocket.rpg.rendering.batch.BatchRenderer;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders EditorScene content to the editor framebuffer.
 * <p>
 * Uses {@link RenderDispatcher} for unified rendering with per-layer tinting support.
 * <p>
 * Handles:
 * <ul>
 *   <li>Rendering tilemap layers with visibility mode support</li>
 *   <li>Layer opacity based on visibility mode (dim inactive layers)</li>
 *   <li>Entity rendering with layer-aware tinting</li>
 *   <li>Frustum culling via RenderDispatcher's CullingSystem</li>
 * </ul>
 */
public class EditorSceneRenderer {

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);

    private final EditorFramebuffer framebuffer;
    private final RenderingConfig renderingConfig;

    private BatchRenderer batchRenderer;
    private RenderDispatcher dispatcher;
    private boolean initialized = false;

    public EditorSceneRenderer(EditorFramebuffer framebuffer, RenderingConfig renderingConfig) {
        this.framebuffer = framebuffer;
        this.renderingConfig = renderingConfig;
    }

    public void init() {
        if (initialized) return;

        batchRenderer = new BatchRenderer(renderingConfig);
        batchRenderer.init(framebuffer.getWidth(), framebuffer.getHeight());

        dispatcher = new RenderDispatcher();

        initialized = true;
        System.out.println("[EditorSceneRenderer] Initialized");
    }

    /**
     * Renders the editor scene to the framebuffer.
     *
     * @param scene  The editor scene to render
     * @param camera The editor camera
     */
    public void render(EditorScene scene, EditorCamera camera) {
        if (!initialized) {
            init();
        }

        framebuffer.bind();
        framebuffer.clear(0.15f, 0.15f, 0.15f, 1.0f);

        if (scene != null && camera != null) {
            renderScene(scene, camera);
        }

        framebuffer.unbind();
    }

    private void renderScene(EditorScene scene, EditorCamera camera) {
        // Wrap EditorCamera as RenderCamera
        RenderCamera renderCamera = new EditorCameraAdapter(camera);

        // Update culling system with current camera
        dispatcher.beginFrame(renderCamera);

        // Setup GL state
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Begin batch with camera matrices
        batchRenderer.beginWithMatrices(
                camera.getProjectionMatrix(),
                camera.getViewMatrix(),
                null
        );

        SpriteBatch batch = batchRenderer.getBatch();

        // Render tilemap layers with per-layer tinting
        renderTilemapLayers(scene, batch, renderCamera);

        // Render entities with layer-aware tinting
        renderEntities(scene, batch, renderCamera);

        // Flush batch
        batchRenderer.end();
    }

    /**
     * Renders tilemap layers with visibility mode support.
     * Inactive layers are dimmed based on the current visibility mode.
     */
    private void renderTilemapLayers(EditorScene scene, SpriteBatch batch, RenderCamera camera) {
        for (LayerRenderInfo info : LayerUtils.getLayersForEditorRendering(scene)) {
            TilemapLayer layer = info.layer();

            // Calculate tint based on layer opacity
            Vector4f tint = createLayerTint(info.opacity());

            // Submit tilemap via dispatcher (handles culling internally)
            dispatcher.submit(layer.getTilemap(), batch, camera, tint);
        }
    }

    /**
     * Renders entities with visibility mode support.
     * Entities may be hidden, dimmed, or fully visible based on the current mode.
     */
    private void renderEntities(EditorScene scene, SpriteBatch batch, RenderCamera camera) {
        TilemapLayer activeLayer = scene.getActiveLayer();

        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.isRenderVisible()) continue;

            // Determine tint based on visibility mode (null = skip rendering)
            Vector4f tint = getEntityTint(entity, activeLayer, scene);
            if (tint == null) continue;

            // Submit entity via dispatcher
            dispatcher.submit(entity, batch, camera, tint);
        }
    }

    /**
     * Creates a tint color based on layer opacity.
     *
     * @param opacity Layer opacity (0.0 to 1.0)
     * @return Tint color
     */
    private Vector4f createLayerTint(float opacity) {
        if (opacity >= 1f) {
            return WHITE;
        }
        // Dim the layer: reduce RGB slightly and apply alpha
        return new Vector4f(0.8f, 0.8f, 0.8f, opacity);
    }

    /**
     * Determines the tint for an entity based on visibility mode.
     * - SELECTED_ONLY: Entities are hidden (only the active tilemap layer is visible)
     * - SELECTED_DIMMED: Entities are dimmed so only the active tilemap layer is prominent
     * - ALL: Entities are fully visible
     *
     * @param entity      The entity to tint
     * @param activeLayer The currently active tilemap layer
     * @param scene       The editor scene
     * @return Tint color for the entity, or null to skip rendering
     */
    private Vector4f getEntityTint(EditorGameObject entity, TilemapLayer activeLayer, EditorScene scene) {
        if (activeLayer == null) {
            return WHITE;
        }

        LayerVisibilityMode mode = scene.getVisibilityMode();

        // In selected-only mode, hide all entities
        if (mode == LayerVisibilityMode.SELECTED_ONLY) {
            return null;
        }

        // In dimmed mode, all entities are dimmed so the active tilemap layer stands out
        if (mode == LayerVisibilityMode.SELECTED_DIMMED) {
            float opacity = scene.getDimmedOpacity();
            return new Vector4f(0.8f, 0.8f, 0.8f, opacity);
        }

        return WHITE;
    }

    public void onResize(int width, int height) {
        if (batchRenderer != null && width > 0 && height > 0) {
            batchRenderer.setProjection(width, height);
        }
    }

    public void destroy() {
        if (batchRenderer != null) {
            batchRenderer.destroy();
            batchRenderer = null;
        }
        dispatcher = null;
        initialized = false;
        System.out.println("[EditorSceneRenderer] Destroyed");
    }
}