package com.pocket.rpg.resources;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * Static facade for asset loading.
 * Uses context pattern similar to Input class.
 * <p>
 * Example usage:
 * <pre>
 * // Initialize once at startup
 * Assets.initialize();
 *
 * // Load assets
 * Texture texture = Assets.load("player.png");
 * Sprite sprite = Assets.load("enemy.png", Sprite.class);
 *
 * // Query assets
 * List&lt;Texture&gt; textures = Assets.getAll(Texture.class);
 *
 * // Save assets
 * Assets.persist(modifiedSheet);
 * </pre>
 */
public final class Assets {

    @Setter
    @Getter
    private static AssetContext context;

    private Assets() {
        // Private constructor - static utility class
    }

    /**
     * Gets the current asset context.
     *
     * @return Asset context
     * @throws IllegalStateException if not initialized
     */
    public static AssetContext getContext() {
        if (context == null) {
            throw new IllegalStateException(
                    "Assets not initialized. Call Assets.initialize() first.");
        }
        return context;
    }

    /**
     * Initializes the asset system with the given context.
     *
     * @param assetContext Asset context to use
     * @throws IllegalArgumentException if context is null
     */
    public static void initialize(AssetContext assetContext) {
        if (assetContext == null) {
            throw new IllegalArgumentException("Asset context cannot be null");
        }
        context = assetContext;
        System.out.println("Assets initialized");
    }

    /**
     * Initializes the asset system with default AssetManager.
     * Default loaders are automatically registered:
     * - TextureLoader (.png, .jpg, .jpeg, .bmp, .tga)
     * - ShaderLoader (.glsl, .shader)
     * - SpriteLoader (Sprite.class for image files)
     * - SpriteSheetLoader (.spritesheet, .spritesheet.json)
     */
    public static void initialize() {
        initialize(new AssetManager());
    }

    /**
     * Loads a resource from the given path.
     * Type is inferred from the return value.
     *
     * @param path Resource path (relative to asset root)
     * @param <T>  Resource type
     * @return Loaded resource (from cache if already loaded)
     */
    public static <T> T load(String path) {
        return getContext().load(path);
    }

    /**
     * Loads a resource with explicit type.
     * Useful when type inference fails or when loading composite types.
     *
     * @param path Resource path (relative to asset root)
     * @param type Resource type class
     * @param <T>  Resource type
     * @return Loaded resource (from cache if already loaded)
     */
    public static <T> T load(String path, Class<T> type) {
        return getContext().load(path, type);
    }

    /**
     * Gets a cached resource without loading.
     * Returns null if the resource is not loaded.
     *
     * @param path Resource path
     * @param <T>  Resource type
     * @return Cached resource, or null if not loaded
     */
    public static <T> T get(String path) {
        return getContext().get(path);
    }

    /**
     * Gets all loaded resources of a specific type.
     *
     * @param type Resource type class
     * @param <T>  Resource type
     * @return List of all loaded resources of that type
     */
    public static <T> List<T> getAll(Class<T> type) {
        return getContext().getAll(type);
    }

    /**
     * Checks if a resource is loaded.
     *
     * @param path Resource path
     * @return true if resource is cached
     */
    public static boolean isLoaded(String path) {
        return getContext().isLoaded(path);
    }

    /**
     * Gets all loaded resource paths.
     *
     * @return Set of all cached paths
     */
    public static Set<String> getLoadedPaths() {
        return getContext().getLoadedPaths();
    }

    /**
     * Saves a resource back to its original path.
     * Resource must have been loaded through Assets to have a tracked path.
     *
     * @param resource Resource to save
     * @throws IllegalArgumentException if resource has no tracked path
     */
    public static void persist(Object resource) {
        getContext().persist(resource);
    }

    /**
     * Saves a resource to a specific path.
     *
     * @param resource Resource to save
     * @param path     Path to save to
     */
    public static void persist(Object resource, String path) {
        getContext().persist(resource, path);
    }

    /**
     * Returns a configuration builder for the asset system.
     * <p>
     * Example:
     * <pre>
     * Assets.configure()
     *     .setAssetRoot("assets/")
     *     .setErrorMode(ErrorMode.USE_PLACEHOLDER)
     *     .apply();
     * </pre>
     *
     * @return Configuration builder
     */
    public static AssetsConfiguration configure() {
        return getContext().configure();
    }

    /**
     * Gets cache statistics.
     * Useful for monitoring cache performance.
     *
     * @return Cache statistics
     */
    public static CacheStats getStats() {
        return getContext().getStats();
    }

    /**
     * Scans the asset directory for files loadable by a specific type's loader.
     * Does NOT load the assets, just returns their paths.
     * <p>
     * Example:
     * <pre>
     * // Find all textures
     * List&lt;String&gt; textures = Assets.scanByType(Texture.class);
     * // Returns: ["sprites/player.png", "sprites/enemy.jpg", ...]
     * </pre>
     *
     * @param type Asset type class (e.g., Texture.class)
     * @return List of relative paths to assets of that type
     */
    public static List<String> scanByType(Class<?> type) {
        return getContext().scanByType(type);
    }

    /**
     * Scans the asset directory for all loadable assets.
     * Returns paths for all files that any registered loader can handle.
     * Does NOT load the assets, just returns their paths.
     * <p>
     * Example:
     * <pre>
     * // Find all assets
     * List&lt;String&gt; allAssets = Assets.scanAll();
     * // Returns: ["sprites/player.png", "shaders/sprite.glsl", "sheets/player.spritesheet", ...]
     * </pre>
     *
     * @return List of relative paths to all loadable assets
     */
    public static List<String> scanAll() {
        return getContext().scanAll();
    }


    public static String getRelativePath(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        return context.getRelativePath(fullPath);
    }
}