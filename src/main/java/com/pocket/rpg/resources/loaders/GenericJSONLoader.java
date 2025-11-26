package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Generic loader for JSON-based assets.
 * Allows creating custom asset types without writing a full loader implementation.
 * 
 * This is the most flexible loader - you can use it to load any JSON-based asset
 * by just providing a constructor function.
 * 
 * Example - Animation System:
 * <pre>
 * // Define your data class
 * class Animation {
 *     String name;
 *     float duration;
 *     List&lt;Keyframe&gt; keyframes;
 *     
 *     static Animation fromJSON(JsonObject json) {
 *         // Parse JSON into Animation
 *         return new Animation(json);
 *     }
 * }
 * 
 * // Register with AssetManager
 * AssetManager manager = AssetManager.getInstance();
 * manager.registerLoader("animation",
 *     new GenericJSONLoader&lt;&gt;(
 *         Animation::fromJSON,
 *         new String[]{".anim", ".animation.json"}
 *     )
 * );
 * 
 * // Use it
 * ResourceHandle&lt;Animation&gt; anim = manager.load("player_walk.anim");
 * </pre>
 * 
 * @param <T> The type of asset this loader creates
 */
public class GenericJSONLoader<T> implements AssetLoader<T> {

    private final Function<JsonObject, T> constructor;
    private final String[] extensions;
    private final String typeName;

    /**
     * Creates a generic JSON loader.
     *
     * @param constructor Function that creates T from JsonObject
     * @param extensions  Supported file extensions
     */
    public GenericJSONLoader(Function<JsonObject, T> constructor, String[] extensions) {
        this(constructor, extensions, null);
    }

    /**
     * Creates a generic JSON loader with explicit type name.
     *
     * @param constructor Function that creates T from JsonObject
     * @param extensions  Supported file extensions
     * @param typeName    Type name for registration (e.g., "animation")
     */
    public GenericJSONLoader(Function<JsonObject, T> constructor, String[] extensions, String typeName) {
        if (constructor == null) {
            throw new IllegalArgumentException("Constructor function cannot be null");
        }
        if (extensions == null || extensions.length == 0) {
            throw new IllegalArgumentException("Must provide at least one extension");
        }

        this.constructor = constructor;
        this.extensions = extensions;
        this.typeName = typeName;
    }

    /**
     * Loads an asset from a JSON file.
     *
     * @param path Path to the JSON file
     * @return Loaded asset
     * @throws IOException if loading or parsing fails
     */
    @Override
    public T load(String path) throws IOException {
        try {
            // Read JSON file
            String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

            // Use constructor function to create asset
            T asset = constructor.apply(json);

            if (asset == null) {
                throw new IOException("Constructor returned null for: " + path);
            }

            return asset;

        } catch (Exception e) {
            throw new IOException("Failed to load JSON asset: " + path, e);
        }
    }

    /**
     * Unloads an asset.
     * Default implementation does nothing - override if cleanup is needed.
     *
     * @param resource The asset to unload
     */
    @Override
    public void unload(T resource) {
        // Generic unload - nothing to clean up for most JSON assets
        // If your asset needs cleanup, use a custom loader instead
    }

    /**
     * Returns supported file extensions.
     *
     * @return Array of extensions
     */
    @Override
    public String[] getSupportedExtensions() {
        return extensions;
    }

    /**
     * Returns placeholder asset.
     * Generic loader doesn't know how to create placeholders.
     *
     * @return null
     */
    @Override
    public T getPlaceholder() {
        return null;
    }

    /**
     * JSON assets support hot reloading by default.
     *
     * @return true
     */
    @Override
    public boolean supportsHotReload() {
        return true;
    }

    /**
     * Reloads an asset from JSON.
     * Simply loads fresh - no special handling needed for most JSON assets.
     *
     * @param existing The existing asset (ignored)
     * @param path     Path to reload from
     * @return New asset instance
     * @throws IOException if reload fails
     */
    @Override
    public T reload(T existing, String path) throws IOException {
        // Just load fresh - most JSON assets are lightweight
        return load(path);
    }

    /**
     * Estimates asset memory usage.
     * Generic estimate - most JSON assets are small.
     *
     * @param resource The asset to measure
     * @return Estimated size in bytes
     */
    @Override
    public long estimateSize(T resource) {
        // Generic estimate: 1KB for most JSON assets
        return 1024;
    }

    /**
     * Gets the loader type name.
     *
     * @return Type name, or generated from extensions
     */
    @Override
    public String getTypeName() {
        if (typeName != null) {
            return typeName;
        }

        // Generate from first extension
        if (extensions.length > 0) {
            String ext = extensions[0];
            return ext.replace(".", "").toLowerCase();
        }

        return "json";
    }
}
