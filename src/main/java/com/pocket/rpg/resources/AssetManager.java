package com.pocket.rpg.resources;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Central manager for all game assets.
 * Provides unified API for loading, caching, and managing resources.
 * <p>
 * Features:
 * - Automatic caching with LRU eviction
 * - Async loading support
 * - Reference counting for lifecycle management
 * - Pluggable loader system for extensibility
 * - Thread-safe operations
 * <p>
 * Usage:
 * <pre>
 * // Initialize once at startup
 * AssetManager.initialize();
 *
 * // Register loaders
 * AssetManager.getInstance().registerLoader("texture", new TextureLoader());
 *
 * // Load resources
 * ResourceHandle&lt;Texture&gt; texture = AssetManager.load("player.png");
 *
 * // Update each frame
 * AssetManager.getInstance().update(deltaTime);
 * </pre>
 */
public class AssetManager {

    private static volatile AssetManager instance = null;
    private static final Object LOCK = new Object();

    // Core components
    private final ResourceCache cache;
    private final Map<String, AssetLoader<?>> loadersByType;
    private final Map<String, String> extensionToType;

    // Async loading
    private final ExecutorService loadQueue;
    private final Set<String> currentlyLoading;

    // Statistics
    private long totalLoadsStarted;
    private long totalLoadsCompleted;
    private long totalLoadsFailed;

    // Configuration
    private static final int DEFAULT_CACHE_SIZE = 1000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 4;
    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 5000; // 5 seconds
    private static final long DEFAULT_EVICTION_THRESHOLD_MS = 60000; // 1 minute

    private long lastCleanupTime;

    /**
     * Private constructor for singleton pattern.
     */
    private AssetManager() {
        this.cache = new ResourceCache(DEFAULT_CACHE_SIZE);
        this.loadersByType = new ConcurrentHashMap<>();
        this.extensionToType = new ConcurrentHashMap<>();
        this.loadQueue = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE, r -> {
            Thread thread = new Thread(r, "AssetManager-Loader");
            thread.setDaemon(true);
            return thread;
        });
        this.currentlyLoading = Collections.synchronizedSet(new HashSet<>());
        this.lastCleanupTime = System.currentTimeMillis();
        this.totalLoadsStarted = 0;
        this.totalLoadsCompleted = 0;
        this.totalLoadsFailed = 0;
    }

    /**
     * Initializes the AssetManager singleton.
     * Must be called before any other AssetManager operations.
     */
    public static void initialize() {
        if (instance != null) {
            System.err.println("WARNING: AssetManager already initialized");
            return;
        }

        synchronized (LOCK) {
            if (instance == null) {
                instance = new AssetManager();
                System.out.println("AssetManager initialized");
            }
        }
    }

    /**
     * Gets the AssetManager singleton instance.
     *
     * @return The AssetManager instance
     * @throws IllegalStateException if not initialized
     */
    public static AssetManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AssetManager not initialized. Call AssetManager.initialize() first."
            );
        }
        return instance;
    }

    /**
     * Loads a resource synchronously.
     * If already cached, returns cached handle immediately.
     *
     * @param path Path to the resource
     * @param <T>  Resource type
     * @return Handle to the resource
     */
    public <T> ResourceHandle<T> load(String path) {
        return load(path, null);
    }

    /**
     * Loads a resource with explicit type.
     * Useful when file extension doesn't match or is ambiguous.
     *
     * @param path     Path to the resource
     * @param typeName Explicit type name (e.g., "texture", "sprite")
     * @param <T>      Resource type
     * @return Handle to the resource
     */
    @SuppressWarnings("unchecked")
    public <T> ResourceHandle<T> load(String path, String typeName) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }

        // Normalize path
        path = normalizePath(path);
        String resourceId = generateResourceId(path, typeName);

        // Check cache first
        ResourceHandle<T> cachedHandle = cache.get(resourceId);
        if (cachedHandle != null) {
            cachedHandle.retain();
            return cachedHandle;
        }

        // Determine type if not specified
        if (typeName == null) {
            typeName = getTypeFromPath(path);
            if (typeName == null) {
                throw new IllegalArgumentException(
                        "Cannot determine asset type for: " + path +
                                ". Use load(path, typeName) to specify explicitly."
                );
            }
        }

        // Get loader
        AssetLoader<T> loader = (AssetLoader<T>) loadersByType.get(typeName);
        if (loader == null) {
            throw new IllegalArgumentException(
                    "No loader registered for type: " + typeName
            );
        }

        // Create handle with placeholder
        ResourceHandle<T> handle = new ResourceHandle<>(resourceId, ResourceState.LOADING);
        T placeholder = loader.getPlaceholder();
        if (placeholder != null) {
            handle.setData(placeholder);
        }

        // Store in cache
        cache.store(resourceId, handle);
        handle.retain();

        // Load synchronously
        totalLoadsStarted++;
        try {
            T resource = loader.load(path);
            handle.setData(resource);
            totalLoadsCompleted++;
        } catch (Exception e) {
            handle.setError(e);
            totalLoadsFailed++;
            System.err.println("Failed to load resource: " + path);
            e.printStackTrace();
        }

        return handle;
    }

    /**
     * Loads a resource asynchronously.
     * Handle is returned immediately in LOADING state.
     *
     * @param path     Path to the resource
     * @param callback Optional callback when loading completes
     * @param <T>      Resource type
     * @return Handle to the resource (may not be ready yet)
     */
    public <T> ResourceHandle<T> loadAsync(String path, Consumer<ResourceHandle<T>> callback) {
        return loadAsync(path, null, callback);
    }

    /**
     * Loads a resource asynchronously with explicit type.
     *
     * @param path     Path to the resource
     * @param typeName Explicit type name
     * @param callback Optional callback when loading completes
     * @param <T>      Resource type
     * @return Handle to the resource (may not be ready yet)
     */
    @SuppressWarnings("unchecked")
    public <T> ResourceHandle<T> loadAsync(String path, String typeName, Consumer<ResourceHandle<T>> callback) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }

        // Normalize path
        path = normalizePath(path);
        String resourceId = generateResourceId(path, typeName);

        // Check cache first
        ResourceHandle<T> cachedHandle = cache.get(resourceId);
        if (cachedHandle != null) {
            cachedHandle.retain();

            // If already ready, invoke callback immediately
            if (callback != null && cachedHandle.isReady()) {
                callback.accept(cachedHandle);
            } else if (callback != null) {
                cachedHandle.onReady(callback);
            }

            return cachedHandle;
        }

        // Check if already loading
        if (currentlyLoading.contains(resourceId)) {
            // Return the handle that's being loaded
            cachedHandle = cache.get(resourceId);
            if (cachedHandle != null) {
                cachedHandle.retain();
                if (callback != null) {
                    cachedHandle.onReady(callback);
                }
                return cachedHandle;
            }
        }

        // Determine type if not specified
        if (typeName == null) {
            typeName = getTypeFromPath(path);
            if (typeName == null) {
                throw new IllegalArgumentException(
                        "Cannot determine asset type for: " + path
                );
            }
        }

        // Get loader
        AssetLoader<T> loader = (AssetLoader<T>) loadersByType.get(typeName);
        if (loader == null) {
            throw new IllegalArgumentException(
                    "No loader registered for type: " + typeName
            );
        }

        // Create handle with placeholder
        ResourceHandle<T> handle = new ResourceHandle<>(resourceId, ResourceState.LOADING);
        T placeholder = loader.getPlaceholder();
        if (placeholder != null) {
            handle.setData(placeholder);
            handle.setState(ResourceState.LOADING); // Reset state after placeholder
        }

        // Store in cache
        cache.store(resourceId, handle);
        handle.retain();

        // Register callback if provided
        if (callback != null) {
            handle.onReady(callback);
        }

        // Mark as loading
        currentlyLoading.add(resourceId);

        // Load asynchronously
        final String finalPath = path;
        final String finalType = typeName;
        totalLoadsStarted++;

        loadQueue.submit(() -> {
            try {
                T resource = loader.load(finalPath);
                handle.setData(resource);
                totalLoadsCompleted++;
            } catch (Exception e) {
                handle.setError(e);
                totalLoadsFailed++;
                System.err.println("Failed to load resource asynchronously: " + finalPath);
                e.printStackTrace();
            } finally {
                currentlyLoading.remove(resourceId);
            }
        });

        return handle;
    }

    /**
     * Unloads a resource from the cache.
     *
     * @param path Path to the resource
     */
    public void unload(String path) {
        unload(path, null);
    }

    /**
     * Unloads a resource with explicit type.
     *
     * @param path     Path to the resource
     * @param typeName Type name
     */
    public void unload(String path, String typeName) {
        path = normalizePath(path);
        String resourceId = generateResourceId(path, typeName);
        cache.remove(resourceId);
    }

    /**
     * Marks a handle as retained (never auto-evicted).
     *
     * @param handle Handle to retain
     */
    public void retain(ResourceHandle<?> handle) {
        if (handle == null) return;
        handle.markRetained();
        cache.markRetained(handle.getResourceId());
    }

    /**
     * Unmarks a handle as retained.
     *
     * @param handle Handle to release from retention
     */
    public void release(ResourceHandle<?> handle) {
        if (handle == null) return;
        handle.unmarkRetained();
        cache.unmarkRetained(handle.getResourceId());
    }

    /**
     * Registers an asset loader.
     *
     * @param typeName Type name (e.g., "texture", "sprite")
     * @param loader   The loader implementation
     */
    public void registerLoader(String typeName, AssetLoader<?> loader) {
        if (typeName == null || loader == null) {
            throw new IllegalArgumentException("Type name and loader cannot be null");
        }

        loadersByType.put(typeName.toLowerCase(), loader);

        // Register extensions
        for (String extension : loader.getSupportedExtensions()) {
            extensionToType.put(extension.toLowerCase(), typeName.toLowerCase());
        }

        System.out.println("Registered loader: " + typeName + " (" +
                String.join(", ", loader.getSupportedExtensions()) + ")");
    }

    /**
     * Updates the asset manager.
     * Should be called once per frame to handle cleanup and maintenance.
     *
     * @param deltaTime Time since last update in seconds
     */
    public void update(float deltaTime) {
        long now = System.currentTimeMillis();

        // Periodic cleanup
        if (now - lastCleanupTime > DEFAULT_CLEANUP_INTERVAL_MS) {
            cache.cleanupUnused(DEFAULT_EVICTION_THRESHOLD_MS);
            lastCleanupTime = now;
        }
    }

    /**
     * Gets loading progress for debugging/UI.
     *
     * @return Fraction of loads completed (0.0 to 1.0)
     */
    public float getLoadingProgress() {
        if (totalLoadsStarted == 0) return 1.0f;
        return (float) totalLoadsCompleted / totalLoadsStarted;
    }

    /**
     * Checks if any resources are currently loading.
     *
     * @return true if loads are in progress
     */
    public boolean isLoading() {
        return !currentlyLoading.isEmpty();
    }

    /**
     * Clears all cached resources.
     * Warning: Does not unload resources, just removes from cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Destroys the AssetManager and releases all resources.
     */
    public static void destroy() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
                System.out.println("AssetManager destroyed");
            }
        }
    }

    /**
     * Internal shutdown method.
     */
    private void shutdown() {
        // Shutdown thread pool
        loadQueue.shutdown();
        try {
            if (!loadQueue.awaitTermination(5, TimeUnit.SECONDS)) {
                loadQueue.shutdownNow();
            }
        } catch (InterruptedException e) {
            loadQueue.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear cache
        cache.clear();
        loadersByType.clear();
        extensionToType.clear();
        currentlyLoading.clear();
    }

    // Helper methods

    /**
     * Normalizes a file path (forward slashes, no trailing slashes).
     */
    private String normalizePath(String path) {
        return path.replace('\\', '/').trim();
    }

    /**
     * Generates a unique resource ID from path and type.
     */
    private String generateResourceId(String path, String typeName) {
        if (typeName == null) {
            return path;
        }
        return typeName + ":" + path;
    }

    /**
     * Determines asset type from file extension.
     */
    private String getTypeFromPath(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return null;

        String extension = path.substring(lastDot).toLowerCase();
        return extensionToType.get(extension);
    }

    // Getters for statistics and configuration

    public ResourceCache getCache() {
        return cache;
    }

    public int getCacheSize() {
        return cache.getCurrentSize();
    }

    public int getMaxCacheSize() {
        return cache.getMaxCacheSize();
    }

    public void setMaxCacheSize(int size) {
        cache.setMaxCacheSize(size);
    }

    public long getTotalLoadsStarted() {
        return totalLoadsStarted;
    }

    public long getTotalLoadsCompleted() {
        return totalLoadsCompleted;
    }

    public long getTotalLoadsFailed() {
        return totalLoadsFailed;
    }

    public Set<String> getRegisteredTypes() {
        return new HashSet<>(loadersByType.keySet());
    }

    @Override
    public String toString() {
        return String.format(
                "AssetManager[cache=%s, loaders=%d, loading=%d, stats=(%d started, %d completed, %d failed)]",
                cache.toString(), loadersByType.size(), currentlyLoading.size(),
                totalLoadsStarted, totalLoadsCompleted, totalLoadsFailed
        );
    }
}