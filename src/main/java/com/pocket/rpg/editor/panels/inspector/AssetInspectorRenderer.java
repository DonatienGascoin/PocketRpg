package com.pocket.rpg.editor.panels.inspector;

/**
 * Interface for rendering type-specific asset inspectors.
 * <p>
 * Unlike {@link com.pocket.rpg.editor.assets.AssetPreviewRenderer} which only renders
 * a preview, this interface provides a full inspector with:
 * <ul>
 *   <li>Preview rendering</li>
 *   <li>Editable property fields</li>
 *   <li>Action buttons (e.g., "Open Editor")</li>
 *   <li>Save functionality</li>
 * </ul>
 *
 * @param <T> The asset type this inspector handles
 */
public interface AssetInspectorRenderer<T> {

    /**
     * Renders the full inspector UI for this asset.
     * <p>
     * Should render:
     * <ul>
     *   <li>Preview section</li>
     *   <li>Property fields (editable if supported)</li>
     *   <li>Action buttons</li>
     * </ul>
     *
     * @param asset The asset to inspect
     * @param assetPath Path to the asset (for saving/opening editor)
     * @param maxPreviewSize Maximum size for the preview area
     * @return true if there are unsaved changes
     */
    boolean render(T asset, String assetPath, float maxPreviewSize);

    /**
     * Returns true if this asset type has editable properties.
     * <p>
     * If true, a Save button will be shown in the inspector.
     */
    default boolean hasEditableProperties() {
        return false;
    }

    /**
     * Saves any pending changes to the asset.
     *
     * @param asset The asset
     * @param assetPath Path to save to
     */
    default void save(T asset, String assetPath) {
        // Default: no-op
    }

    /**
     * Called when the inspector is about to switch to a different asset.
     * <p>
     * Implementations can use this to clean up state, stop previews, etc.
     */
    default void onDeselect() {
        // Default: no-op
    }

    /**
     * Returns true if there are unsaved changes.
     * <p>
     * Used to prompt the user before switching to a different asset.
     */
    default boolean hasUnsavedChanges() {
        return false;
    }

    /**
     * Undoes the last edit. Called by InspectorPanel shortcuts.
     */
    default void undo() {
        // Default: no-op
    }

    /**
     * Redoes the last undone edit. Called by InspectorPanel shortcuts.
     */
    default void redo() {
        // Default: no-op
    }

    /**
     * Returns the asset type this inspector handles.
     */
    Class<T> getAssetType();
}
