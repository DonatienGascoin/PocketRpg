package com.pocket.rpg.resources;

import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.JsonPrefabLoader;
import com.pocket.rpg.rendering.Shader;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.loaders.*;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.ui.text.Font;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of AssetContext.
 * Manages asset loading, caching, and persistence.
 * <p>
 * Supports sub-asset references using the format "path/to/asset.ext#subId".
 * For example: "sheets/player.spritesheet#3" loads sprite index 3 from the sheet.
 */
public class AssetManager implements AssetContext {

    /** Separator for sub-asset references (e.g., "sheet.spritesheet#3") */
    public static final char SUB_ASSET_SEPARATOR = '#';

    /**
     * Stores loaded assets keyed by normalized path.
     * <p>
     * Enables {@link #get(String)} for fast lookups and prevents duplicate loading
     * of the same asset. All assets loaded via {@link #load(String, Class)} are cached here.
     */
    @Getter
    private final ResourceCache cache;

    /**
     * Maps asset type (Class) → AssetLoader.
     * <p>
     * Determines how each type is loaded, saved, and provides metadata like supported
     * extensions and preview sprites. Register custom loaders via {@link #registerLoader(Class, AssetLoader)}.
     */
    private final Map<Class<?>, AssetLoader<?>> loaders;

    /**
     * Maps file extension (".png", ".spritesheet") → asset type (Texture.class, SpriteSheet.class).
     * <p>
     * Used by {@link #load(String)} to infer the asset type from path when not explicitly specified.
     * Extensions are registered automatically when calling {@link #registerLoader(Class, AssetLoader)}.
     */
    private final Map<String, Class<?>> extensionMap;

    /**
     * Reverse mapping: asset object → path.
     * <p>
     * <b>Single source of truth for serialization.</b> Used by {@link #getPathForResource(Object)}
     * to resolve how to serialize an asset reference. Includes #index suffix for sub-assets
     * (e.g., spritesheet sprites are stored as "sheet.spritesheet#3").
     * <p>
     * Populated automatically when assets are loaded via {@link #load(String, Class)}.
     */
    private final Map<Object, String> resourcePaths;

    @Getter
    private String assetRoot = "gameData/assets/";
    @Getter
    private ErrorMode errorMode = ErrorMode.USE_PLACEHOLDER;
    @Setter
    @Getter
    private boolean statisticsEnabled = true;

    /**
     * Creates a new AssetManager with default loaders pre-registered.
     */
    public AssetManager() {
        this.cache = new ResourceCache();
        this.loaders = new ConcurrentHashMap<>();
        this.extensionMap = new ConcurrentHashMap<>();
        this.resourcePaths = new ConcurrentHashMap<>();

        // Auto-register default loaders
        registerDefaultLoaders();
    }

    /**
     * Registers default loaders automatically.
     */
    private void registerDefaultLoaders() {
        registerLoader(Texture.class, new TextureLoader());
        registerLoader(Shader.class, new ShaderLoader());
        registerLoader(Sprite.class, new SpriteLoader());
        registerLoader(SpriteSheet.class, new SpriteSheetLoader());
        registerLoader(SceneData.class, new SceneDataLoader());
        registerLoader(JsonPrefab.class, new JsonPrefabLoader());
        registerLoader(Font.class, new FontLoader());

    }

    /**
     * Registers a loader for a type.
     * Automatically registers extensions from getSupportedExtensions().
     *
     * @param type   Resource type class
     * @param loader Loader for that type
     * @param <T>    Resource type
     */
    public <T> void registerLoader(Class<T> type, AssetLoader<T> loader) {
        loaders.put(type, loader);

        // Auto-register extensions
        for (String ext : loader.getSupportedExtensions()) {
            extensionMap.put(ext.toLowerCase(), type);
        }

        System.out.println("Registered loader: " + type.getSimpleName() +
                " (" + String.join(", ", loader.getSupportedExtensions()) + ")");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path) {
        // Normalize path
        String normalizedPath = normalizePath(path);

        // Check cache first (including sub-assets)
        T cached = cache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }

        if (statisticsEnabled) {
            cache.getStats().recordLoad();
        }

        // Check for sub-asset reference
        int hashIndex = normalizedPath.indexOf(SUB_ASSET_SEPARATOR);
        if (hashIndex != -1) {
            // Use Object.class as wildcard - loader returns appropriate type
            return (T) loadSubAsset(normalizedPath, hashIndex, Object.class);
        }

        // Determine type from extension
        Class<?> type = getTypeFromExtension(normalizedPath);
        if (type == null) {
            throw new IllegalArgumentException("Unknown file extension for: " + path);
        }

        return (T) loadWithType(normalizedPath, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path, Class<T> type) {
        // Normalize path
        String normalizedPath = normalizePath(path);

        // Check cache first (including sub-assets)
        T cached = cache.get(normalizedPath);
        if (cached != null) {
            return cached;
        }

        if (statisticsEnabled) {
            cache.getStats().recordLoad();
        }

        // Check for sub-asset reference
        int hashIndex = normalizedPath.indexOf(SUB_ASSET_SEPARATOR);
        if (hashIndex != -1) {
            return loadSubAsset(normalizedPath, hashIndex, type);
        }

        return (T) loadWithType(normalizedPath, type);
    }

    /**
     * Loads a sub-asset from a parent asset.
     * Path format: "path/to/parent.ext#subId"
     *
     * @param fullPath  Full path including sub-asset identifier
     * @param hashIndex Index of the '#' separator
     * @param subType   Expected sub-asset type class
     * @param <T>       Expected sub-asset type
     * @return The loaded sub-asset
     */
    @SuppressWarnings("unchecked")
    private <T> T loadSubAsset(String fullPath, int hashIndex, Class<T> subType) {
        String basePath = fullPath.substring(0, hashIndex);
        String subId = fullPath.substring(hashIndex + 1);

        // Determine parent type from extension
        Class<?> parentType = getTypeFromExtension(basePath);
        if (parentType == null) {
            throw new IllegalArgumentException("Unknown parent asset type for: " + basePath);
        }

        // Load parent asset (will be cached)
        Object parent = load(basePath, parentType);

        // Get loader and extract sub-asset
        AssetLoader<?> loader = loaders.get(parentType);
        if (loader == null) {
            throw new IllegalArgumentException("No loader for parent type: " + parentType.getSimpleName());
        }

        T subAsset = ((AssetLoader<Object>) loader).getSubAsset(parent, subId, subType);

        // Cache and register with full reference path
        cache.put(fullPath, subAsset);
        resourcePaths.put(subAsset, fullPath);

        return subAsset;
    }

    /**
     * Internal load method with type specified.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadWithType(String normalizedPath, Class<T> type) {
        // Get loader for type
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(type);
        if (loader == null) {
            throw new IllegalArgumentException("No loader registered for type: " + type.getSimpleName());
        }

        // Resolve full path for actual file system access
        String fullPath = resolvePath(normalizedPath);

        try {
            // Load resource - pass FULL PATH to loader
            T resource = loader.load(fullPath);

            // Cache it with NORMALIZED PATH as key
            cache.put(normalizedPath, resource);

            // Track normalized path for persistence
            resourcePaths.put(resource, normalizedPath);

            return resource;

        } catch (IOException e) {
            // Handle error based on mode
            if (errorMode == ErrorMode.THROW_EXCEPTION) {
                throw new RuntimeException("Failed to load: " + normalizedPath, e);
            } else {
                // Use placeholder
                System.err.println("WARNING: Failed to load '" + normalizedPath + "', using placeholder. Error: " + e.getMessage());
                T placeholder = loader.getPlaceholder();

                if (placeholder != null) {
                    cache.put(normalizedPath, placeholder);
                    resourcePaths.put(placeholder, normalizedPath);
                }

                return placeholder;
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        String normalizedPath = normalizePath(path);
        return cache.get(normalizedPath);
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        return cache.getAllOfType(type);
    }

    @Override
    public boolean isLoaded(String path) {
        String normalizedPath = normalizePath(path);
        return cache.contains(normalizedPath);
    }

    @Override
    public Set<String> getLoadedPaths() {
        return cache.getPaths();
    }

    @Override
    public void persist(Object resource) {
        String path = resourcePaths.get(resource);
        if (path == null) {
            throw new IllegalArgumentException("Resource has no associated path. Use persist(resource, path) instead.");
        }
        persist(resource, path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void persist(Object resource, String path) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot persist null resource");
        }

        Class<?> type = resource.getClass();
        AssetLoader loader = loaders.get(type);

        if (loader == null) {
            throw new IllegalArgumentException("No loader registered for type: " + type.getSimpleName());
        }

        String normalizedPath = normalizePath(path);
        String fullPath = resolvePath(normalizedPath);

        try {
            loader.save(resource, fullPath);
            System.out.println("Saved: " + normalizedPath);

            // Update cache and path tracking
            cache.put(normalizedPath, resource);
            resourcePaths.put(resource, normalizedPath);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save: " + normalizedPath, e);
        }
    }

    @Override
    public AssetsConfiguration configure() {
        return new AssetsConfiguration(this);
    }

    @Override
    public CacheStats getStats() {
        return cache.getStats();
    }

    @Override
    public List<String> scanByType(Class<?> type) {
        // Get loader for type
        AssetLoader<?> loader = loaders.get(type);
        if (loader == null) {
            return new ArrayList<>();  // No loader, return empty
        }

        // Get extensions from loader
        String[] extensions = loader.getSupportedExtensions();

        // Scan for files with those extensions
        return scanDirectory(assetRoot, extensions);
    }

    @Override
    public List<String> scanAll() {
        // Collect all extensions from all loaders
        Set<String> allExtensions = new HashSet<>();
        for (AssetLoader<?> loader : loaders.values()) {
            for (String ext : loader.getSupportedExtensions()) {
                allExtensions.add(ext.toLowerCase());
            }
        }

        // Scan for all extensions
        return scanDirectory(assetRoot, allExtensions.toArray(new String[0]));
    }

    /**
     * Recursively scans a directory for files with specified extensions.
     * Returns paths relative to asset root.
     */
    private List<String> scanDirectory(String directory, String[] extensions) {
        List<String> results = new ArrayList<>();

        try {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return results;  // Directory doesn't exist
            }

            // Convert extensions to lowercase set for fast lookup
            Set<String> extSet = new HashSet<>();
            for (String ext : extensions) {
                extSet.add(ext.toLowerCase());
            }

            // Walk directory tree
            Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();

                        // Check if file has matching extension
                        for (String ext : extSet) {
                            if (fileName.toLowerCase().endsWith(ext)) {
                                // Convert to relative path from asset root
                                String relativePath = dirPath.relativize(path).toString();
                                // Normalize path separators to forward slashes
                                relativePath = relativePath.replace('\\', '/');
                                results.add(relativePath);
                                break;
                            }
                        }
                    });

        } catch (IOException e) {
            System.err.println("Error scanning directory: " + directory + " - " + e.getMessage());
        }

        return results;
    }

    /**
     * Normalizes a path (removes ./ and ../, converts backslashes).
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Convert backslashes to forward slashes
        path = path.replace('\\', '/');

        // Remove leading ./
        if (path.startsWith("./")) {
            path = path.substring(2);
        }

        return path;
    }

    /**
     * Resolves a path relative to asset root.
     * If path is absolute or starts with /, uses it directly.
     * Otherwise, prepends asset root.
     */
    private String resolvePath(String path) {
        // Check if absolute path (starts with / or contains :)
        if (path.startsWith("/") || path.contains(":")) {
            return path;
        }

        // Relative path - prepend asset root
        String root = assetRoot;
        if (!root.endsWith("/")) {
            root += "/";
        }

        return root + path;
    }

    /**
     * Determines type from file extension.
     */
    private Class<?> getTypeFromExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        }

        String extension = path.substring(lastDot).toLowerCase();
        return extensionMap.get(extension);
    }

    // Getters and setters for configuration

    public void setAssetRoot(String assetRoot) {
        if (assetRoot == null || assetRoot.isEmpty()) {
            throw new IllegalArgumentException("Asset root cannot be null or empty");
        }
        this.assetRoot = assetRoot;
        System.out.println("Asset root set to: " + assetRoot);
    }

    public void setErrorMode(ErrorMode errorMode) {
        if (errorMode == null) {
            throw new IllegalArgumentException("Error mode cannot be null");
        }
        this.errorMode = errorMode;
    }

    @Override
    public String getRelativePath(String fullPath) {
        try {
            return fullPath.substring(assetRoot.length());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw e;
        }
    }

    @Override
    public String getPathForResource(Object resource) {
        return resourcePaths.get(resource);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Sprite getPreviewSprite(String path, Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        if (loader == null) {
            return null;
        }

        try {
            Object asset = load(path, type);
            return ((AssetLoader<Object>) loader).getPreviewSprite(asset);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Class<?> getTypeForPath(String path) {
        return getTypeFromExtension(path);
    }

    @Override
    public boolean isAssetType(Class<?> type) {
        return loaders.containsKey(type);
    }

    /**
     * Manually registers a resource with a path.
     * Useful for programmatically created assets that need path tracking.
     *
     * @param resource The resource to register
     * @param path     The path to associate with it
     */
    public void registerResource(Object resource, String path) {
        if (resource == null || path == null) {
            return;
        }
        String normalizedPath = normalizePath(path);
        resourcePaths.put(resource, normalizedPath);
        cache.put(normalizedPath, resource);
    }

    @Override
    public String toString() {
        return String.format("AssetManager[root=%s, errorMode=%s, %s]",
                assetRoot, errorMode, cache);
    }
}