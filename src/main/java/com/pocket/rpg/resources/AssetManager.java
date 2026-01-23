package com.pocket.rpg.resources;

import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;
import org.reflections.Reflections;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.reflections.scanners.Scanners.SubTypes;
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
     * Registers default loaders automatically by scanning for AssetLoader implementations.
     * <p>
     * Loaders can be defined in any package under com.pocket.rpg.
     * The asset type is extracted from the generic type parameter (e.g., AssetLoader&lt;Texture&gt;).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerDefaultLoaders() {
        try {
            // Scan all packages under com.pocket.rpg for AssetLoader implementations
            Reflections reflections = new Reflections("com.pocket.rpg");
            Set<Class<?>> loaderClasses = reflections.get(SubTypes.of(AssetLoader.class).asClass());

            int registered = 0;
            for (Class<?> clazz : loaderClasses) {
                // Skip interfaces and abstract classes
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }

                try {
                    // Extract asset type from generic parameter
                    Class<?> assetClass = extractAssetType(clazz);
                    if (assetClass == null) {
                        System.err.println("Could not determine asset type for loader: " + clazz.getName());
                        continue;
                    }

                    Constructor<?> constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    AssetLoader<?> loader = (AssetLoader<?>) constructor.newInstance();

                    registerLoader(assetClass, (AssetLoader) loader);
                    registered++;

                } catch (NoSuchMethodException e) {
                    System.err.println("AssetLoader must have a no-arg constructor: " + clazz.getName());
                } catch (Exception e) {
                    System.err.println("Failed to instantiate loader: " + clazz.getName() + " - " + e.getMessage());
                }
            }

            System.out.println("Loader scanning complete. Registered " + registered + " asset loaders.");

        } catch (Exception e) {
            System.err.println("Error scanning for loaders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts the asset type from a loader class's generic type parameter.
     * For example, TextureLoader implements AssetLoader&lt;Texture&gt;, so this returns Texture.class.
     *
     * @param loaderClass The loader class to inspect
     * @return The asset class, or null if it cannot be determined
     */
    private Class<?> extractAssetType(Class<?> loaderClass) {
        // Check generic interfaces
        for (Type iface : loaderClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pType) {
                if (pType.getRawType() == AssetLoader.class) {
                    Type typeArg = pType.getActualTypeArguments()[0];
                    if (typeArg instanceof Class<?>) {
                        return (Class<?>) typeArg;
                    }
                }
            }
        }

        // Check superclass chain (for loaders that extend an abstract base)
        Class<?> superclass = loaderClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return extractAssetType(superclass);
        }

        return null;
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

    // ========================================================================
    // PUBLIC LOAD METHODS - all delegate to loadInternal
    // ========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path) {
        String normalizedPath = normalizePath(path);
        Class<?> type = getTypeFromPath(normalizedPath);
        return (T) loadInternal(normalizedPath, type, LoadOptions.defaults());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path, LoadOptions options) {
        String normalizedPath = normalizePath(path);
        Class<?> type = getTypeFromPath(normalizedPath);
        return (T) loadInternal(normalizedPath, type, options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path, Class<T> type) {
        String normalizedPath = normalizePath(path);
        return (T) loadInternal(normalizedPath, type, LoadOptions.defaults());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(String path, LoadOptions options, Class<T> type) {
        String normalizedPath = normalizePath(path);
        return (T) loadInternal(normalizedPath, type, options);
    }

    // ========================================================================
    // INTERNAL LOAD IMPLEMENTATION
    // ========================================================================

    /**
     * Core load implementation - all public methods delegate here.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadInternal(String normalizedPath, Class<?> type, LoadOptions options) {
        // Cache check
        if (options.isUseCache()) {
            T cached = cache.get(normalizedPath);
            if (cached != null) {
                return cached;
            }
        }

        if (statisticsEnabled) {
            cache.getStats().recordLoad();
        }

        // Sub-asset handling
        int hashIndex = normalizedPath.indexOf(SUB_ASSET_SEPARATOR);
        if (hashIndex != -1) {
            return loadSubAsset(normalizedPath, hashIndex, type, options);
        }

        // Type inference if needed
        if (type == null || type == Object.class) {
            type = getTypeFromExtension(normalizedPath);
            if (type == null) {
                throw new IllegalArgumentException("Unknown file extension for: " + normalizedPath);
            }
        }

        return loadWithType(normalizedPath, type, options);
    }

    /**
     * Loads a sub-asset from a parent asset.
     * Path format: "path/to/parent.ext#subId"
     *
     * @param fullPath  Full path including sub-asset identifier
     * @param hashIndex Index of the '#' separator
     * @param subType   Expected sub-asset type class
     * @param options   Load options
     * @param <T>       Expected sub-asset type
     * @return The loaded sub-asset
     */
    @SuppressWarnings("unchecked")
    private <T> T loadSubAsset(String fullPath, int hashIndex, Class<?> subType, LoadOptions options) {
        String basePath = fullPath.substring(0, hashIndex);
        String subId = fullPath.substring(hashIndex + 1);

        // Determine parent type from extension
        Class<?> parentType = getTypeFromExtension(basePath);
        if (parentType == null) {
            throw new IllegalArgumentException("Unknown parent asset type for: " + basePath);
        }

        // Load parent asset (inherits options)
        Object parent = loadInternal(basePath, parentType, options);

        // Get loader and extract sub-asset
        AssetLoader<?> loader = loaders.get(parentType);
        if (loader == null) {
            throw new IllegalArgumentException("No loader for parent type: " + parentType.getSimpleName());
        }

        Object subAsset = ((AssetLoader<Object>) loader).getSubAsset(parent, subId, Object.class);

        if (options.isUseCache()) {
            cache.put(fullPath, subAsset);
        }
        resourcePaths.put(subAsset, fullPath);

        return (T) subAsset;
    }

    /**
     * Internal load method with type and options.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadWithType(String normalizedPath, Class<?> type, LoadOptions options) {
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(type);
        if (loader == null) {
            throw new IllegalArgumentException("No loader registered for type: " + type.getSimpleName());
        }

        String fullPath = resolvePath(normalizedPath, options);

        try {
            T resource = loader.load(fullPath);

            if (options.isUseCache()) {
                cache.put(normalizedPath, resource);
            }
            resourcePaths.put(resource, normalizedPath);

            return resource;

        } catch (IOException e) {
            if (errorMode == ErrorMode.THROW_EXCEPTION) {
                throw new RuntimeException("Failed to load: " + normalizedPath, e);
            } else {
                System.err.println("WARNING: Failed to load '" + normalizedPath + "', using placeholder. Error: " + e.getMessage());
                T placeholder = loader.getPlaceholder();

                if (placeholder != null && options.isUseCache()) {
                    cache.put(normalizedPath, placeholder);
                    resourcePaths.put(placeholder, normalizedPath);
                }

                return placeholder;
            }
        }
    }

    // ========================================================================
    // PATH RESOLUTION
    // ========================================================================

    /**
     * Resolves path with options controlling asset root usage.
     */
    private String resolvePath(String path, LoadOptions options) {
        // Skip asset root if disabled or path is absolute
        if (!options.isUseAssetRoot() || path.startsWith("/") || path.contains(":")) {
            return path;
        }

        String root = assetRoot;
        if (!root.endsWith("/")) {
            root += "/";
        }
        return root + path;
    }

    /**
     * Helper to extract type from path (handles sub-assets).
     */
    private Class<?> getTypeFromPath(String normalizedPath) {
        int hashIndex = normalizedPath.indexOf(SUB_ASSET_SEPARATOR);
        String pathForType = (hashIndex != -1) ? normalizedPath.substring(0, hashIndex) : normalizedPath;
        return getTypeFromExtension(pathForType);
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
     * Determines type from file extension.
     */
    private Class<?> getTypeFromExtension(String path) {
        String lowerPath = path.toLowerCase();

        // Check for compound extensions (e.g., ".prefab.json", ".scene.json")
        // by testing all registered extensions against the path ending
        for (Map.Entry<String, Class<?>> entry : extensionMap.entrySet()) {
            if (lowerPath.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    // ========================================================================
    // GET / QUERY METHODS
    // ========================================================================

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

    @Override
    public boolean canInstantiate(Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        return loader != null && loader.canInstantiate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public EditorGameObject instantiate(String path, Class<?> type, Vector3f position) {
        AssetLoader<?> loader = loaders.get(type);
        if (loader == null || !loader.canInstantiate()) {
            return null;
        }

        try {
            Object asset = load(path, type);
            if (asset == null) {
                return null;
            }
            return ((AssetLoader<Object>) loader).instantiate(asset, path, position);
        } catch (Exception e) {
            System.err.println("Failed to instantiate asset: " + path + " - " + e.getMessage());
            return null;
        }
    }

    @Override
    public EditorPanelType getEditorPanelType(Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        return loader != null ? loader.getEditorPanelType() : null;
    }

    @Override
    public Set<EditorCapability> getEditorCapabilities(Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        return loader != null ? loader.getEditorCapabilities() : Set.of();
    }

    @Override
    public String getIconCodepoint(Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        return loader != null ? loader.getIconCodepoint() : MaterialIcons.InsertDriveFile;
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

    // ========================================================================
    // PERSISTENCE
    // ========================================================================

    @Override
    public void persist(Object resource) {
        String path = resourcePaths.get(resource);
        if (path == null) {
            throw new IllegalArgumentException("Resource has no associated path. Use persist(resource, path) instead.");
        }
        persist(resource, path);
    }

    @Override
    public void persist(Object resource, String path) {
        persist(resource, path, LoadOptions.defaults());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void persist(Object resource, String path, LoadOptions options) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot persist null resource");
        }

        Class<?> type = resource.getClass();
        AssetLoader loader = loaders.get(type);

        if (loader == null) {
            throw new IllegalArgumentException("No loader registered for type: " + type.getSimpleName());
        }

        String normalizedPath = normalizePath(path);
        String fullPath = resolvePath(normalizedPath, options);

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

    // ========================================================================
    // SCANNING
    // ========================================================================

    @Override
    public List<String> scanByType(Class<?> type) {
        AssetLoader<?> loader = loaders.get(type);
        if (loader == null) {
            return new ArrayList<>();
        }

        String[] extensions = loader.getSupportedExtensions();
        return scanDirectory(assetRoot, extensions);
    }

    @Override
    public List<String> scanAll() {
        return scanAll(assetRoot);
    }

    @Override
    public List<String> scanAll(String directory) {
        Set<String> allExtensions = new HashSet<>();
        for (AssetLoader<?> loader : loaders.values()) {
            for (String ext : loader.getSupportedExtensions()) {
                allExtensions.add(ext.toLowerCase());
            }
        }

        return scanDirectory(directory, allExtensions.toArray(new String[0]));
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
                return results;
            }

            Set<String> extSet = new HashSet<>();
            for (String ext : extensions) {
                extSet.add(ext.toLowerCase());
            }

            Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();

                        for (String ext : extSet) {
                            if (fileName.toLowerCase().endsWith(ext)) {
                                String relativePath = dirPath.relativize(path).toString();
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

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    @Override
    public AssetsConfiguration configure() {
        return new AssetsConfiguration(this);
    }

    @Override
    public CacheStats getStats() {
        return cache.getStats();
    }

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