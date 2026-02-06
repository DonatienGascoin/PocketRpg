package com.pocket.rpg.resources;

import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteGrid;
import com.pocket.rpg.resources.loaders.SpriteLoader;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
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
     * Loads a resource from the given path.
     * Type is inferred from the return value.
     *
     * @param path Resource path (relative to asset root)
     * @param loadOptions loading options (no cache, raw path)
     * @param <T>  Resource type
     * @return Loaded resource (from cache if already loaded)
     */
    public static <T> T load(String path, LoadOptions loadOptions) {
        return getContext().load(path, loadOptions);
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
     * Loads a resource with explicit type.
     * Useful when type inference fails or when loading composite types.
     *
     * @param path Resource path (relative to asset root)
     * @param loadOptions loading options (no cache, raw path)
     * @param type Resource type class
     * @param <T>  Resource type
     * @return Loaded resource (from cache if already loaded)
     */
    public static <T> T load(String path, LoadOptions loadOptions, Class<T> type) {
        return getContext().load(path, loadOptions, type);
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
     * Gets the path for a loaded resource.
     * Returns null if the resource was not loaded through Assets.
     * <p>
     * Used by serialization to determine if an object should be
     * serialized as a path reference or as a full object.
     *
     * @param resource The resource object
     * @return The normalized path, or null if not tracked
     */
    public static String getPathForResource(Object resource) {
        return getContext().getPathForResource(resource);
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
     * Saves a resource to a specific path with options.
     *
     * @param resource Resource to save
     * @param path     Path to save to
     * @param options  Load options (e.g., raw() to skip asset root)
     */
    public static void persist(Object resource, String path, LoadOptions options) {
        getContext().persist(resource, path, options);
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
     * Scans a specific directory for assets of a given type.
     * Returns paths for files with extensions matching the loader for that type.
     * Does NOT load the assets, just returns their paths.
     * <p>
     * Example:
     * <pre>
     * // Find all scenes in the scenes folder
     * List&lt;String&gt; scenes = Assets.scanByType(SceneData.class, "gameData/scenes");
     * // Returns: ["TestScene.scene", "Village.scene", ...]
     * </pre>
     *
     * @param type Asset type class (e.g., SceneData.class)
     * @param directory Directory to scan (e.g., "gameData/scenes")
     * @return List of paths to assets of that type in the directory
     */
    public static List<String> scanByType(Class<?> type, String directory) {
        return getContext().scanByType(type, directory);
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

    /**
     * Scans a specific directory for all loadable assets.
     * Returns paths for all files that any registered loader can handle.
     * Does NOT load the assets, just returns their paths.
     * <p>
     * Example:
     * <pre>
     * // Find all scenes
     * List&lt;String&gt; scenes = Assets.scanAll("gameData/scenes");
     * // Returns: ["TestScene.scene", "Village.scene", ...]
     * </pre>
     *
     * @param directory Directory to scan (absolute or relative to working directory)
     * @return List of paths to all loadable assets in that directory
     */
    public static List<String> scanAll(String directory) {
        return getContext().scanAll(directory);
    }


    public static String getRelativePath(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        return context.getRelativePath(fullPath);
    }

    /**
     * Gets a preview sprite for an asset using its loader.
     *
     * @param path Asset path
     * @param type Asset type class
     * @return Preview sprite, or null if not available
     */
    public static Sprite getPreviewSprite(String path, Class<?> type) {
        return getContext().getPreviewSprite(path, type);
    }

    /**
     * Gets the asset type for a path based on registered loaders.
     *
     * @param path Asset path
     * @return Asset type class, or null if no loader handles this extension
     */
    public static Class<?> getTypeForPath(String path) {
        return getContext().getTypeForPath(path);
    }

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
    public static void registerResource(Object resource, String path) {
        if (context != null) {
            context.registerResource(resource, path);
        }
    }

    /**
     * Checks if a type has a registered loader.
     * @param type Class to check
     * @return true if this type can be loaded through Assets
     */
    public static boolean isAssetType(Class<?> type) {
        return getContext().isAssetType(type);
    }

    /**
     * Checks if assets of a given type can be instantiated as entities in the editor.
     * Delegates to the loader's canInstantiate() method.
     *
     * @param type Asset type class
     * @return true if this asset type can create entities
     */
    public static boolean canInstantiate(Class<?> type) {
        return getContext().canInstantiate(type);
    }

    /**
     * Creates an EditorGameObject from an asset at the given path.
     * Loads the asset and delegates to the loader's instantiate() method.
     *
     * @param path     Asset path
     * @param type     Asset type class
     * @param position World position for the entity
     * @return New EditorGameObject, or null if instantiation is not supported
     */
    public static EditorGameObject instantiate(String path, Class<?> type, Vector3f position) {
        return getContext().instantiate(path, type, position);
    }

    /**
     * Gets the editor panel that should open when an asset of this type is double-clicked.
     * Delegates to the loader's getEditorPanelType() method.
     *
     * @param type Asset type class
     * @return EditorPanelType to open, or null if no dedicated editor exists
     */
    public static EditorPanelType getEditorPanelType(Class<?> type) {
        return getContext().getEditorPanelType(type);
    }

    public static String getAssetRoot() {
        return context.getAssetRoot();
    }

    /**
     * Gets the editor capabilities for an asset type.
     * Delegates to the loader's getEditorCapabilities() method.
     *
     * @param type Asset type class
     * @return Set of capabilities, or empty set if no loader or none declared
     */
    public static Set<EditorCapability> getEditorCapabilities(Class<?> type) {
        return getContext().getEditorCapabilities(type);
    }

    /**
     * Gets the icon codepoint for an asset type.
     * Delegates to the loader's getIconCodepoint() method.
     *
     * @param type Asset type class
     * @return Icon codepoint string (MaterialIcons constant)
     */
    public static String getIconCodepoint(Class<?> type) {
        return getContext().getIconCodepoint(type);
    }

    // ========================================================================
    // SPRITE GRID HELPERS
    // ========================================================================

    /**
     * Loads a SpriteGrid from a multiple-mode sprite texture.
     * <p>
     * This is a convenience method for loading spritesheets as a grid.
     * The texture must have metadata with {@code spriteMode: MULTIPLE}.
     * <p>
     * Example:
     * <pre>
     * SpriteGrid grid = Assets.loadSpriteGrid("spritesheets/player.png");
     * Sprite frame0 = grid.getSprite(0);
     * Sprite frame5 = grid.getSprite(5);
     * </pre>
     *
     * @param path Path to the texture (must have multiple-mode metadata)
     * @return SpriteGrid for the texture
     * @throws IllegalArgumentException if the sprite is not in multiple mode
     */
    public static SpriteGrid loadSpriteGrid(String path) {
        Sprite parent = load(path, Sprite.class);
        return getSpriteGrid(parent);
    }

    /**
     * Gets the SpriteGrid for an already-loaded multiple-mode sprite.
     * <p>
     * Use this when you already have the parent sprite loaded.
     *
     * @param parent The parent sprite (must be in multiple mode)
     * @return SpriteGrid for the sprite
     * @throws IllegalArgumentException if the sprite is not in multiple mode
     */
    public static SpriteGrid getSpriteGrid(Sprite parent) {
        if (parent == null) {
            throw new IllegalArgumentException("Parent sprite cannot be null");
        }

        // Get the SpriteLoader to access its grid functionality
        AssetContext ctx = getContext();
        if (ctx instanceof AssetManager manager) {
            // Access the loader directly to get the SpriteGrid
            SpriteLoader loader = getSpriteLoader(manager);
            if (loader != null) {
                SpriteGrid grid = loader.getSpriteGrid(parent);
                if (grid != null) {
                    return grid;
                }
            }
        }

        throw new IllegalArgumentException(
                "Sprite is not in MULTIPLE mode or was not loaded through Assets: " +
                        getPathForResource(parent)
        );
    }

    /**
     * Checks if a sprite is in multiple (spritesheet) mode.
     *
     * @param sprite The sprite to check
     * @return true if the sprite is in multiple mode
     */
    public static boolean isMultipleMode(Sprite sprite) {
        if (sprite == null) {
            return false;
        }

        AssetContext ctx = getContext();
        if (ctx instanceof AssetManager manager) {
            SpriteLoader loader = getSpriteLoader(manager);
            if (loader != null) {
                return loader.isMultipleMode(sprite);
            }
        }
        return false;
    }

    /**
     * Gets the total sprite count for a multiple-mode sprite.
     *
     * @param sprite The sprite to check
     * @return Number of sprites in the grid, or 1 if single mode
     */
    public static int getSpriteCount(Sprite sprite) {
        if (sprite == null) {
            return 0;
        }

        AssetContext ctx = getContext();
        if (ctx instanceof AssetManager manager) {
            SpriteLoader loader = getSpriteLoader(manager);
            if (loader != null) {
                return loader.getSpriteCount(sprite);
            }
        }
        return 1;
    }

    /**
     * Gets the SpriteLoader instance from the AssetManager.
     * Helper method for accessing sprite-specific functionality.
     */
    private static SpriteLoader getSpriteLoader(AssetManager manager) {
        AssetLoader<Sprite> loader = manager.getLoader(Sprite.class);
        return (loader instanceof SpriteLoader) ? (SpriteLoader) loader : null;
    }

    // ========================================================================
    // HOT-RELOAD API
    // ========================================================================

    /**
     * Reloads an asset from disk, mutating the cached instance in place.
     * All existing references remain valid.
     * <p>
     * For sub-asset paths (e.g., "sprites/player.png#3"), the parent asset is reloaded.
     *
     * @param path Asset path (e.g., "sprites/player.png")
     * @return true if reloaded, false if not cached or reload not supported
     */
    public static boolean reload(String path) {
        AssetContext ctx = getContext();
        if (!(ctx instanceof AssetManager manager)) {
            return false;
        }

        // Skip sub-asset paths - reload parent instead
        if (path.contains("#")) {
            path = path.substring(0, path.indexOf('#'));
        }

        Object cached = manager.getCache().get(path);
        if (cached == null) {
            return false; // Not loaded, nothing to reload
        }

        Class<?> type = manager.getCachedType(path);
        if (type == null) {
            return false;
        }

        AssetLoader<?> loader = manager.getLoader(type);
        if (loader == null || !loader.supportsHotReload()) {
            return false;
        }

        try {
            String fullPath = manager.resolveFullPath(path);
            reloadWithLoader(loader, cached, fullPath, path);
            return true;
        } catch (Exception e) {
            Log.error("Assets", "Failed to reload " + path + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to invoke reload with proper type casting and contract validation.
     */
    @SuppressWarnings("unchecked")
    private static <T> void reloadWithLoader(AssetLoader<T> loader, Object asset, String fullPath, String path)
            throws IOException {
        T existing = (T) asset;
        T result = loader.reload(existing, fullPath);

        // Guard: ensure loader mutated in place rather than creating new reference
        if (result != existing) {
            Log.error("Assets",
                    "Hot-reload contract violation for " + existing.getClass().getSimpleName() +
                    ": " + loader.getClass().getSimpleName() + ".reload() returned new reference " +
                    "instead of mutating existing. Path: " + path + ". " +
                    "Fix: Update the loader's reload() method to mutate the existing instance in place " +
                    "and return it, rather than creating a new object. Keeping old asset to prevent broken references.");
            // Don't use the new reference - keep existing to avoid breaking external references
        }
    }

    /**
     * Reloads all cached assets that support hot-reload.
     *
     * @return Number of assets successfully reloaded
     */
    public static int reloadAll() {
        AssetContext ctx = getContext();
        if (!(ctx instanceof AssetManager manager)) {
            return 0;
        }

        int count = 0;
        for (String path : new ArrayList<>(manager.getCache().getPaths())) {
            // Skip sub-assets (they share parent's texture)
            if (path.contains("#")) {
                continue;
            }
            if (reload(path)) {
                count++;
            }
        }
        return count;
    }
}