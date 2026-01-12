package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerUtils;
import com.pocket.rpg.editor.scene.LayerUtils.LayerRenderInfo;
import com.pocket.rpg.rendering.core.Renderable;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts EditorScene data into Renderables for unified rendering.
 * <p>
 * Provides two conversion modes:
 * <ul>
 *   <li>{@link #toRenderables} - For editor scene view (respects visibility mode, dims inactive layers)</li>
 *   <li>{@link #toRenderablesForPreview} - For game preview (ignores visibility mode, shows final result)</li>
 * </ul>
 * <p>
 * The returned list is reused internally to avoid per-frame allocations.
 * Do not store references to the returned list.
 */
public class EditorSceneAdapter {

    // Reuse list to avoid per-frame allocation
    private final List<Renderable> renderables = new ArrayList<>();

    // Tint colors for visibility modes
    private static final Vector4f FULL_OPACITY = new Vector4f(1f, 1f, 1f, 1f);
    private static final float DIMMED_RGB = 0.6f;

    /**
     * Builds renderable list for editor scene view.
     * <p>
     * Respects visibility mode:
     * <ul>
     *   <li>Active layer: full opacity</li>
     *   <li>Inactive layers: dimmed (reduced opacity and saturation)</li>
     *   <li>Hidden layers: not included</li>
     * </ul>
     *
     * @param scene The editor scene (may be null)
     * @return List of renderables (reused internally - do not store)
     */
    public List<Renderable> toRenderables(EditorScene scene) {
        renderables.clear();

        if (scene == null) {
            return renderables;
        }

        // Add tilemap layers with visibility mode support
        for (LayerRenderInfo info : LayerUtils.getLayersForEditorRendering(scene)) {
            Vector4f tint;
            if (info.opacity() >= 1f) {
                tint = FULL_OPACITY;
            } else {
                // Dim inactive layers: reduce brightness and apply opacity
                tint = new Vector4f(DIMMED_RGB, DIMMED_RGB, DIMMED_RGB, info.opacity());
            }
            renderables.add(new TilemapLayerRenderable(info.layer(), tint));
        }

        // Add entities (EditorEntity implements Renderable)
        for (EditorGameObject entity : scene.getEntities()) {
            if (entity.isRenderVisible()) {
                renderables.add(entity);
            }
        }

        return renderables;
    }

    /**
     * Builds renderable list for preview/play mode.
     * <p>
     * Ignores visibility mode - shows the scene as it will appear in-game.
     * Only respects layer visibility flags (not editor-specific dimming).
     *
     * @param scene The editor scene (may be null)
     * @return List of renderables (reused internally - do not store)
     */
    public List<Renderable> toRenderablesForPreview(EditorScene scene) {
        renderables.clear();

        if (scene == null) {
            return renderables;
        }

        // Add tilemap layers without visibility mode dimming
        for (LayerRenderInfo info : LayerUtils.getLayersForPreviewRendering(scene)) {
            renderables.add(new TilemapLayerRenderable(info.layer(), FULL_OPACITY));
        }

        // Add entities
        for (EditorGameObject entity : scene.getEntities()) {
            if (entity.isRenderVisible()) {
                renderables.add(entity);
            }
        }

        return renderables;
    }

    /**
     * Clears the internal list.
     * Called automatically by toRenderables/toRenderablesForPreview.
     */
    public void clear() {
        renderables.clear();
    }
}
