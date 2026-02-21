package com.pocket.rpg.editor.assets;

/**
 * Interface for rendering asset previews and inspectors in the editor.
 * <p>
 * Implementations handle ImGui rendering for specific asset types.
 * {@link #renderPreview} provides a compact thumbnail (picker popup, browser grid).
 * {@link #renderInspector} provides a detailed inspector panel view (defaults to renderPreview).
 *
 * @param <T> The asset type this renderer handles
 */
public interface AssetPreviewRenderer<T> {

    /**
     * Renders a compact preview/thumbnail of the asset using ImGui.
     *
     * @param asset   The asset to preview
     * @param maxSize Maximum size (width/height) for the preview
     */
    void renderPreview(T asset, float maxSize);

    /**
     * Renders a detailed inspector view of the asset using ImGui.
     * <p>
     * Defaults to {@link #renderPreview}. Override for types that need
     * additional detail in the inspector (e.g., grid overlays, playback controls).
     *
     * @param asset     The asset to inspect
     * @param assetPath Path to the asset (for metadata loading)
     * @param maxSize   Maximum size for the preview area
     */
    default void renderInspector(T asset, String assetPath, float maxSize) {
        renderPreview(asset, maxSize);
    }

    /**
     * Returns the asset type this renderer handles.
     *
     * @return Asset class
     */
    Class<T> getAssetType();
}
