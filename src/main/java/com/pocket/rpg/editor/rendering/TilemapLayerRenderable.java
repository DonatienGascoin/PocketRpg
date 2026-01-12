package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.rendering.core.Renderable;
import org.joml.Vector4f;

/**
 * Adapts TilemapLayer to the Renderable interface.
 * <p>
 * This is a data-only wrapper - actual rendering logic is in RenderDispatcher.
 * Supports tinting for visibility mode (dimming inactive layers in editor).
 * <p>
 * Usage:
 * <pre>
 * // Full opacity
 * renderables.add(new TilemapLayerRenderable(layer));
 *
 * // Dimmed (inactive layer)
 * renderables.add(new TilemapLayerRenderable(layer, new Vector4f(0.5f, 0.5f, 0.5f, 0.5f)));
 * </pre>
 */
public record TilemapLayerRenderable(
        TilemapLayer layer,
        Vector4f tint
) implements Renderable {

    /**
     * Creates a renderable with default white tint (no color modification).
     *
     * @param layer The tilemap layer to render
     */
    public TilemapLayerRenderable(TilemapLayer layer) {
        this(layer, new Vector4f(1, 1, 1, 1));
    }

    /**
     * Creates a renderable with specified tint.
     *
     * @param layer The tilemap layer to render
     * @param tint  RGBA tint color (multiplied with tile colors)
     */
    public TilemapLayerRenderable {
        if (layer == null) {
            throw new IllegalArgumentException("Layer cannot be null");
        }
        if (tint == null) {
            tint = new Vector4f(1, 1, 1, 1);
        }
    }

    @Override
    public int getZIndex() {
        return layer.getZIndex();
    }

    @Override
    public boolean isRenderVisible() {
        return layer.isVisible() && layer.getTilemap() != null;
    }
}
