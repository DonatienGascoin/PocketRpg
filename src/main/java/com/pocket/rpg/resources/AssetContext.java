package com.pocket.rpg.resources;

import com.pocket.rpg.rendering.Sprite;

import java.util.List;
import java.util.Set;

/**
 * Interface for asset management operations.
 * Allows swapping implementations (e.g., for testing).
 */
public interface AssetContext {

    /**
     * Loads a resource from the given path.
     * Type is inferred from the return value.
     *
     * @param path Resource path
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path);

    /**
     * Loads a resource with explicit type.
     *
     * @param path Resource path
     * @param type Resource type class
     * @param <T>  Resource type
     * @return Loaded resource
     */
    <T> T load(String path, Class<T> type);

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
     * Scans the asset directory for all loadable assets.
     * Returns paths for all files that any registered loader can handle.
     * Does NOT load the assets, just returns their paths.
     *
     * @return List of relative paths to all loadable assets
     */
    List<String> scanAll();

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
}