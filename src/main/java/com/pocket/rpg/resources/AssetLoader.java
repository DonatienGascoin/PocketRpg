package com.pocket.rpg.resources;

import java.io.IOException;

/**
 * Interface for loading specific types of resources.
 * Implementations handle the actual loading, unloading, and placeholder logic
 * for different asset types (textures, sprites, shaders, etc.).
 *
 * @param <T> The type of resource this loader handles
 */
public interface AssetLoader<T> {

    /**
     * Loads a resource from the specified path.
     * This method should be blocking - async loading is handled by AssetManager.
     *
     * @param path Path to the resource file (relative or absolute)
     * @return The loaded resource
     * @throws IOException if loading fails
     */
    T load(String path) throws IOException;

    /**
     * Unloads and cleans up a resource.
     * Should release any system resources (GPU memory, file handles, etc.).
     *
     * @param resource The resource to unload
     */
    void unload(T resource);

    /**
     * Returns file extensions this loader supports.
     * Used for automatic loader selection based on file extension.
     * Example: [".png", ".jpg", ".bmp"] for texture loader
     *
     * @return Array of supported extensions (including the dot)
     */
    String[] getSupportedExtensions();

    /**
     * Returns a placeholder resource to use while the actual resource loads.
     * This allows seamless async loading without null checks everywhere.
     * Optional - can return null if no placeholder is appropriate.
     *
     * @return A placeholder resource, or null
     */
    default T getPlaceholder() {
        return null;
    }

    /**
     * Returns the type name for this loader.
     * Used for registration and debugging.
     * Example: "texture", "sprite", "shader"
     *
     * @return Type name
     */
    default String getTypeName() {
        return getClass().getSimpleName().replace("Loader", "").toLowerCase();
    }

    /**
     * Checks if this loader can reload resources.
     * If true, the loader should support updating an existing resource
     * with new data (for hot reloading).
     *
     * @return true if hot reload is supported
     */
    default boolean supportsHotReload() {
        return false;
    }

    /**
     * Reloads an existing resource with fresh data from disk.
     * Only called if supportsHotReload() returns true.
     * Default implementation just loads a new resource.
     *
     * @param existing The existing resource to update
     * @param path     Path to reload from
     * @return The updated resource (may be the same instance or new instance)
     * @throws IOException if reloading fails
     */
    default T reload(T existing, String path) throws IOException {
        // Default: just load fresh and let caller handle swap
        return load(path);
    }

    /**
     * Estimates the memory size of a resource in bytes.
     * Used for cache management and statistics.
     * Optional - can return 0 if unknown.
     *
     * @param resource The resource to measure
     * @return Estimated size in bytes
     */
    default long estimateSize(T resource) {
        return 0;
    }
}