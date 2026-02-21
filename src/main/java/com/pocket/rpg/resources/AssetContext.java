package com.pocket.rpg.resources;

import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

/**
 * Interface for asset management operations.
 * Allows swapping implementations (e.g., for testing).
 * <p>
 * Supports sub-asset references using the format "path/to/asset.ext#subId".
 * For example: "sheets/player.spritesheet#3" loads sprite index 3 from the sheet.
 */
public interface AssetContext {

    /**
     * Loads a resource from the given path.
     * Type is inferred from the return value.
     * <p>
     * Supports sub-asset references: "path/to/asset.ext#subId"
     *
     * @param path Resource path
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path);

    /**
     * Loads a resource from the given path with loading option.
     * Type is inferred from the return value.
     * <p>
     * Supports sub-asset references: "path/to/asset.ext#subId"
     *
     * @param path Resource path
     * @param loadOptions Loading options (no cache, raw path)
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path, LoadOptions loadOptions);

    /**
     * Loads a resource with explicit type.
     * <p>
     * Supports sub-asset references: "path/to/asset.ext#subId"
     *
     * @param path Resource path
     * @param type Resource type class
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path, Class<T> type);

    /**
     * Loads a resource with explicit type and loading option.
     * <p>
     * Supports sub-asset references: "path/to/asset.ext#subId"
     *
     * @param path Resource path
     * @param loadOptions Loading options (no cache, raw path)
     * @param type Resource type class
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path, LoadOptions loadOptions, Class<T> type);

    /**
     * Gets a cached resource without loading.
     *
     * @param path Resource path
     * @param <T>  Resource type
     * @return Cached resource, or null if not loaded
     */
    <T> T get(String path);

    /**
     * Gets all loaded resources of a specific type.
     *
     * @param type Resource type class
     * @param <T>  Resource type
     * @return List of all loaded resources of that type
     */
    <T> List<T> getAll(Class<T> type);

    /**
     * Checks if a resource is loaded.
     *
     * @param path Resource path
     * @return true if resource is cached
     */
    boolean isLoaded(String path);

    /**
     * Gets all loaded resource paths.
     *
     * @return Set of all cached paths
     */
    Set<String> getLoadedPaths();

    /**
     * Gets the path for a loaded resource.
     * Returns the full reference path including sub-asset identifiers
     * (e.g., "sheet.spritesheet#3").
     * <p>
     * Returns null if the resource was not loaded through Assets.
     *
     * @param resource The resource object
     * @return The normalized path, or null if not tracked
     */
    String getPathForResource(Object resource);

    /**
     * Saves a resource back to its original path.
     *
     * @param resource Resource to save
     */
    void persist(Object resource);

    /**
     * Saves a resource to a specific path.
     *
     * @param resource Resource to save
     * @param path     Path to save to
     */
    void persist(Object resource, String path);

    /**
     * Saves a resource to a specific path with options.
     *
     * @param resource Resource to save
     * @param path     Path to save to
     * @param options  Load options (e.g., raw() to skip asset root)
     */
    void persist(Object resource, String path, LoadOptions options);

    /**
     * Returns a configuration builder for this context.
     *
     * @return Configuration builder
     */
    AssetsConfiguration configure();

    /**
     * Gets cache statistics.
     *
     * @return Cache statistics
     */
    CacheStats getStats();

    /**
     * Scans the asset directory for files loadable by a specific type's loader.
     * Does NOT load the assets, just returns their paths.
     *
     * @param type Asset type class (e.g., Texture.class)
     * @return List of relative paths to assets of that type
     */
    List<String> scanByType(Class<?> type);

    /**
     * Scans a specific directory for assets of a given type.
     * Returns paths for files with extensions matching the loader for that type.
     * Does NOT load the assets, just returns their paths.
     *
     * @param type Asset type class (e.g., SceneData.class)
     * @param directory Directory to scan (e.g., "gameData/scenes")
     * @return List of paths to assets of that type in the directory
     */
    List<String> scanByType(Class<?> type, String directory);

    /**
     * Scans the asset directory for all loadable assets.
     * Returns paths for all files that any registered loader can handle.
     * Does NOT load the assets, just returns their paths.
     *
     * @return List of relative paths to all loadable assets
     */
    List<String> scanAll();

    /**
     * Scans a specific directory for all loadable assets.
     * Returns paths for all files that any registered loader can handle.
     * Does NOT load the assets, just returns their paths.
     *
     * @param directory Directory to scan (absolute or relative to working directory)
     * @return List of paths to all loadable assets in that directory
     */
    List<String> scanAll(String directory);

    void setAssetRoot(String assetRoot);

    String getAssetRoot();

    ResourceCache getCache();

    void setErrorMode(ErrorMode errorMode);

    void setStatisticsEnabled(boolean enableStatistics);

    String getRelativePath(String fullPath);

    /**
     * Gets a preview sprite for the asset at the given path.
     * Delegates to the appropriate loader's getPreviewSprite method.
     */
    Sprite getPreviewSprite(String path, Class<?> type);

    /**
     * Gets the asset type for a path based on registered loaders.
     *
     * @param path Asset path
     * @return Asset type class, or null if no loader handles this extension
     */
    Class<?> getTypeForPath(String path);

    /**
     * Manually registers a resource with a path.
     * Useful for programmatically created assets that need path tracking.
     * <p>
     * This enables {@link #getPathForResource(Object)} to work for assets
     * not loaded through {@link #load(String, Class)}.
     *
     * @param resource The resource to register
     * @param path     The path to associate with it
     */
    void registerResource(Object resource, String path);

    /**
     * Unregisters a resource from path tracking.
     * The resource remains usable but getPathForResource() will return null.
     *
     * @param resource The resource to unregister
     */
    void unregisterResource(Object resource);

    /**
     * Checks if a type has a registered loader.
     * @param type Class to check
     * @return true if this type can be loaded through Assets
     */
    boolean isAssetType(Class<?> type);

    /**
     * Checks if assets of a given type can be instantiated as entities in the editor.
     * Delegates to the loader's canInstantiate() method.
     *
     * @param type Asset type class
     * @return true if this asset type can create entities
     */
    boolean canInstantiate(Class<?> type);

    /**
     * Creates an EditorGameObject from an asset at the given path.
     * Loads the asset and delegates to the loader's instantiate() method.
     *
     * @param path     Asset path
     * @param type     Asset type class
     * @param position World position for the entity
     * @return New EditorGameObject, or null if instantiation is not supported
     */
    EditorGameObject instantiate(String path, Class<?> type, Vector3f position);

    /**
     * Checks if assets of a given type can be saved back to disk.
     * Delegates to the loader's canSave() method.
     *
     * @param type Asset type class
     * @return true if this asset type supports saving
     */
    boolean canSave(Class<?> type);

    /**
     * Gets the editor panel that should open when an asset of this type is double-clicked.
     * Delegates to the loader's getEditorPanelType() method.
     *
     * @param type Asset type class
     * @return EditorPanelType to open, or null if no dedicated editor exists
     */
    EditorPanelType getEditorPanelType(Class<?> type);

    /**
     * Gets the editor capabilities for an asset type.
     * Delegates to the loader's getEditorCapabilities() method.
     *
     * @param type Asset type class
     * @return Set of capabilities, or empty set if no loader or none declared
     */
    Set<EditorCapability> getEditorCapabilities(Class<?> type);

    /**
     * Gets the icon codepoint for an asset type.
     * Delegates to the loader's getIconCodepoint() method.
     *
     * @param type Asset type class
     * @return Icon codepoint string (MaterialIcons constant)
     */
    String getIconCodepoint(Class<?> type);
}
