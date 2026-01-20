package com.pocket.rpg.resources;

/**
 * Capabilities that asset loaders can advertise to the editor.
 * Used to dynamically show/hide context menu items and editor tools.
 * <p>
 * Loaders implement {@link AssetLoader#getEditorCapabilities()} to declare
 * which capabilities they support. The editor then uses these to show
 * appropriate context menu items and tool options.
 * <p>
 * Example:
 * <pre>
 * // In SpriteLoader
 * {@literal @}Override
 * public Set&lt;EditorCapability&gt; getEditorCapabilities() {
 *     return Set.of(EditorCapability.PIVOT_EDITING);
 * }
 *
 * // In AssetBrowserPanel context menu
 * if (caps.contains(EditorCapability.PIVOT_EDITING)) {
 *     if (ImGui.menuItem("Edit Pivot...")) {
 *         pivotEditorPanel.open(assetPath);
 *     }
 * }
 * </pre>
 */
public enum EditorCapability {

    /**
     * Asset supports pivot point editing.
     * Enables the "Edit Pivot..." context menu option.
     */
    PIVOT_EDITING,

    /**
     * Asset supports 9-slice configuration.
     * Enables the "Edit 9-Slice..." context menu option.
     * (Future feature)
     */
    NINE_SLICE,

    /**
     * Asset supports physics shape editing.
     * Enables the "Edit Physics Shape..." context menu option.
     * (Future feature)
     */
    PHYSICS_SHAPE,

    /**
     * Asset supports collision mask editing.
     * Enables the "Edit Collision Mask..." context menu option.
     * (Future feature)
     */
    COLLISION_MASK
}
