package com.pocket.rpg.resources;

import java.io.IOException;

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
}
