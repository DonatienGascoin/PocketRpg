package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * // Load-only (simple)
 * manager.registerLoader(
 *     Animation.class,
 *     new GenericJSONLoader<>(
 *         Animation::fromJSON,
 *         new String[]{".anim"}
 *     )
 * );
 *
 * // Load + Save (full support)
 * manager.registerLoader(
 *     Quest.class,
 *     new GenericJSONLoader<>(
 *         Quest::fromJSON,     // Load function
 *         Quest::toJSON,       // Save function
 *         new String[]{".quest.json"}
 *     )
 * );
 *
 * // Use it
 * Animation anim = Assets.load("player_walk.anim");
 * </pre>
 *
 * @param <T> The type of asset this loader creates
 */
public class GenericJSONLoader<T> implements AssetLoader<T> {

    private final Function<JsonObject, T> constructor;
    private final Function<T, JsonObject> serializer;  // Optional for saving
    private final String[] extensions;
    private final Gson gson;

    /**
     * Creates a generic JSON loader (load-only).
     *
     * @param constructor Function that creates T from JsonObject
     * @param extensions  Supported file extensions
     */
    public GenericJSONLoader(Function<JsonObject, T> constructor, String[] extensions) {
        this(constructor, null, extensions);
    }

    /**
     * Creates a generic JSON loader with save support.
     *
     * @param constructor Function that creates T from JsonObject
     * @param serializer  Function that converts T to JsonObject (for saving)
     * @param extensions  Supported file extensions
     */
    public GenericJSONLoader(Function<JsonObject, T> constructor,
                             Function<T, JsonObject> serializer,
                             String[] extensions) {
        if (constructor == null) {
            throw new IllegalArgumentException("Constructor function cannot be null");
        }
        if (extensions == null || extensions.length == 0) {
            throw new IllegalArgumentException("Must provide at least one extension");
        }

        this.constructor = constructor;
        this.serializer = serializer;
        this.extensions = extensions;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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
     * Saves an asset to a JSON file.
     * Requires serializer function provided in constructor.
     *
     * @param resource The asset to save
     * @param path Path to save to
     * @throws IOException if saving fails
     * @throws UnsupportedOperationException if no serializer was provided
     */
    @Override
    public void save(T resource, String path) throws IOException {
        if (serializer == null) {
            throw new UnsupportedOperationException(
                    "Saving not supported - no serializer function provided");
        }

        try {
            // Convert resource to JSON
            JsonObject json = serializer.apply(resource);

            if (json == null) {
                throw new IOException("Serializer returned null for: " + path);
            }

            // Write to file
            String jsonString = gson.toJson(json);
            Files.write(Paths.get(path), jsonString.getBytes());

        } catch (Exception e) {
            throw new IOException("Failed to save JSON asset: " + path, e);
        }
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
     * Returns supported file extensions.
     *
     * @return Array of extensions
     */
    @Override
    public String[] getSupportedExtensions() {
        return extensions;
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
}