package com.pocket.rpg.resources;

import com.pocket.rpg.editor.EditorPanel;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Set;

/**
 * Interface for loading and saving assets.
 * Implementations handle specific asset types (textures, shaders, etc.).
 *
 * @param <T> The type of asset this loader handles
 */
public interface AssetLoader<T> {

    /**
     * Loads a resource from the given path.
     *
     * @param path Path to the resource file
     * @return Loaded resource
     * @throws IOException if loading fails
     */
    T load(String path) throws IOException;

    /**
     * Saves a resource back to disk.
     *
     * @param resource The resource to save
     * @param path The path to save to
     * @throws IOException if saving fails
     * @throws UnsupportedOperationException if this loader doesn't support saving
     */
    void save(T resource, String path) throws IOException;

    /**
     * Returns a placeholder for this type (used on load failure).
     * The placeholder allows the game to continue running with a visible indicator.
     *
     * @return Placeholder resource (e.g., magenta texture for textures)
     */
    T getPlaceholder();

    /**
     * Returns supported file extensions (e.g., [".png", ".jpg"]).
     * This is used for automatic type registration.
     * Extensions should include the dot and be lowercase.
     *
     * @return Array of supported extensions
     */
    String[] getSupportedExtensions();

    /**
     * Hot reload support (future feature).
     *
     * @return true if this loader supports reloading
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * Reloads a resource (future feature).
     * Default implementation just calls load().
     *
     * @param existing The existing resource
     * @param path Path to reload from
     * @return Reloaded resource
     * @throws IOException if reload fails
     */
    default T reload(T existing, String path) throws IOException {
        return load(path);
    }

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    /**
     * Returns whether this asset type can be instantiated as an entity in the editor.
     * Override to return true for assets that can be dragged into scenes.
     *
     * @return true if this asset can create entities
     */
    default boolean canInstantiate() {
        return false;
    }

    /**
     * Creates an EditorEntity from this asset.
     * Called when the asset is dropped into the scene viewport or hierarchy.
     *
     * @param asset The loaded asset
     * @param assetPath Relative path to the asset (for component configuration)
     * @param position World position to place the entity
     * @return New EditorEntity with appropriate components, or null if not supported
     */
    default EditorGameObject instantiate(T asset, String assetPath, Vector3f position) {
        return null;
    }

    /**
     * Gets a preview sprite for displaying in the asset browser.
     * Return null to use the default icon instead.
     *
     * @param asset The loaded asset
     * @return Preview sprite, or null for icon fallback
     */
    default Sprite getPreviewSprite(T asset) {
        return null;
    }

    /**
     * Gets the Material icon codepoint for this asset type.
     * Used when no preview sprite is available.
     *
     * @return Material icon string (e.g., MaterialIcons.InsertDriveFile)
     */
    default String getIconCodepoint() {
        return MaterialIcons.InsertDriveFile;
    }

    // ========================================================================
    // EDITOR PANEL SUPPORT
    // ========================================================================

    /**
     * Returns the editor panel to open when this asset is double-clicked.
     * Return null if no dedicated editor exists for this asset type.
     *
     * @return EditorPanel to open, or null for no action
     */
    default EditorPanel getEditorPanel() {
        return null;
    }

    /**
     * Returns the editor capabilities this asset type supports.
     * Used to dynamically show context menu items and editor panels.
     * <p>
     * Example:
     * <pre>
     * {@literal @}Override
     * public Set&lt;EditorCapability&gt; getEditorCapabilities() {
     *     return Set.of(EditorCapability.PIVOT_EDITING);
     * }
     * </pre>
     *
     * @return Set of supported capabilities (empty by default)
     * @see EditorCapability
     */
    default Set<EditorCapability> getEditorCapabilities() {
        return Set.of();
    }

    // ========================================================================
    // SUB-ASSET SUPPORT
    // ========================================================================

    /**
     * Extracts a sub-asset from a parent asset.
     * Only override for assets that contain addressable sub-assets (e.g., SpriteSheet).
     * <p>
     * Example: SpriteSheetLoader overrides this to return individual sprites by index.
     *
     * @param parent  The parent asset
     * @param subId   The sub-asset identifier (e.g., "3" for sprite index)
     * @param subType The expected sub-asset type
     * @param <S>     The sub-asset type
     * @return The sub-asset
     * @throws UnsupportedOperationException if this loader doesn't support sub-assets
     * @throws IllegalArgumentException if subId is invalid or subType is not supported
     */
    default <S> S getSubAsset(T parent, String subId, Class<S> subType) {
        throw new UnsupportedOperationException(
                "Asset type " + parent.getClass().getSimpleName() + " does not support sub-assets"
        );
    }
}
