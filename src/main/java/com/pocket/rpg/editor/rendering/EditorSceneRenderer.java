package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.rendering.SceneRenderingBackend;
import com.pocket.rpg.rendering.SpriteBatch;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders EditorScene content to the editor framebuffer.
 * <p>
 * Handles:
 * - Rendering tilemap layers with visibility mode support
 * - Layer opacity based on visibility mode
 * - Entity rendering
 * - Frustum culling via SceneRenderingBackend
 */
public class EditorSceneRenderer {

    private final EditorFramebuffer framebuffer;
    private final RenderingConfig renderingConfig;

    private SceneRenderingBackend backend;
    private EntityRenderer entityRenderer;
    private boolean initialized = false;

    public EditorSceneRenderer(EditorFramebuffer framebuffer, RenderingConfig renderingConfig) {
        this.framebuffer = framebuffer;
        this.renderingConfig = renderingConfig;
    }

    public void init() {
        if (initialized) return;

        backend = new SceneRenderingBackend(renderingConfig);
        backend.init(framebuffer.getWidth(), framebuffer.getHeight());

        entityRenderer = new EntityRenderer();

        initialized = true;
        System.out.println("EditorSceneRenderer initialized");
    }

    public void render(EditorScene scene, EditorCamera camera) {
        if (!initialized) {
            init();
        }

        framebuffer.bind();
        framebuffer.clear(0.15f, 0.15f, 0.15f, 1.0f);

        if (scene != null) {
            backend.begin(camera);

            renderTilemapLayers(scene);

            SpriteBatch batch = backend.getSpriteBatch();
            entityRenderer.render(batch, scene);

            backend.end();
        }

        framebuffer.unbind();
    }

    private void renderTilemapLayers(EditorScene scene) {
        int layerCount = scene.getLayerCount();
        if (layerCount == 0) return;

        List<int[]> layerOrder = new ArrayList<>();
        for (int i = 0; i < layerCount; i++) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer != null) {
                layerOrder.add(new int[]{i, layer.getZIndex()});
            }
        }

        layerOrder.sort(Comparator.comparingInt(a -> a[1]));

        for (int[] pair : layerOrder) {
            int layerIndex = pair[0];

            if (!scene.isLayerVisible(layerIndex)) {
                continue;
            }

            TilemapLayer layer = scene.getLayer(layerIndex);
            if (layer == null) continue;

            float opacity = scene.getLayerOpacity(layerIndex);
            Vector4f tint = (opacity >= 1f)
                    ? new Vector4f(1f, 1f, 1f, 1f)
                    : new Vector4f(0.8f, 0.8f, 0.8f, opacity);

            backend.renderTilemap(layer.getTilemap(), tint);
        }
    }

    public void onResize(int width, int height) {
        if (backend != null && width > 0 && height > 0) {
            backend.resize(width, height);
        }
    }

    public void destroy() {
        if (backend != null) {
            backend.destroy();
            backend = null;
        }
        entityRenderer = null;
        initialized = false;
    }
}