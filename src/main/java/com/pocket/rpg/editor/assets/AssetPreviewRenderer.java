package com.pocket.rpg.editor.assets;

/**
 * Interface for rendering asset previews in the editor.
 * <p>
 * Implementations handle ImGui rendering for specific asset types.
 * Most assets use the default {@link SpriteBasedPreviewRenderer},
 * while special cases (like Font) have custom renderers.
 *
 * @param <T> The asset type this renderer handles
 */
public interface AssetPreviewRenderer<T> {

    /**
     * Renders a preview of the asset using ImGui.
     *
     * @param asset   The asset to preview
     * @param maxSize Maximum size (width/height) for the preview
     */
    void render(T asset, float maxSize);

    /**
     * Returns the asset type this renderer handles.
     *
     * @return Asset class
     */
    Class<T> getAssetType();
}
