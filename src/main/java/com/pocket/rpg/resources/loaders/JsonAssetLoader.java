package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Abstract base class for JSON-backed asset loaders.
 * <p>
 * Provides standard implementations for file I/O, placeholder caching,
 * hot-reload, and editor integration. Subclasses only define the
 * asset-specific parts: parsing, serialization, and placeholder creation.
 * <p>
 * Example usage:
 * <pre>
 * public class QuestLogLoader extends JsonAssetLoader&lt;QuestLog&gt; {
 *     protected QuestLog fromJson(JsonObject json, String path) { ... }
 *     protected JsonObject toJson(QuestLog asset) { ... }
 *     protected QuestLog createPlaceholder() { return new QuestLog(); }
 *     protected String[] extensions() { return new String[]{".questlog.json"}; }
 *     protected String iconCodepoint() { return MaterialIcons.MenuBook; }
 *     protected void copyInto(QuestLog existing, QuestLog fresh) { existing.copyFrom(fresh); }
 * }
 * </pre>
 *
 * @param <T> The asset type this loader handles
 */
public abstract class JsonAssetLoader<T> implements AssetLoader<T> {

    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private T placeholder;

    // ========================================================================
    // ABSTRACT METHODS — subclasses define these
    // ========================================================================

    /**
     * Deserializes a JSON object into the asset type.
     *
     * @param json The parsed JSON object
     * @param path The full file path (available for error messages or path-derived data)
     * @return The deserialized asset
     * @throws IOException if deserialization fails
     */
    protected abstract T fromJson(JsonObject json, String path) throws IOException;

    /**
     * Serializes the asset into a JSON object.
     *
     * @param asset The asset to serialize
     * @return The JSON representation
     */
    protected abstract JsonObject toJson(T asset);

    /**
     * Creates a new placeholder instance for load failures.
     *
     * @return A minimal valid asset instance
     */
    protected abstract T createPlaceholder();

    /**
     * Returns supported file extensions (e.g., {".dialogue.json"}).
     * Extensions should include the dot and be lowercase.
     *
     * @return Array of supported extensions
     */
    protected abstract String[] extensions();

    /**
     * Returns the Material icon codepoint for this asset type.
     *
     * @return Material icon string
     */
    protected abstract String iconCodepoint();

    /**
     * Copies all state from a freshly loaded asset into the existing instance.
     * Used during hot-reload to mutate the cached instance in place.
     *
     * @param existing The cached instance to update
     * @param fresh    The freshly loaded instance with new data
     */
    protected abstract void copyInto(T existing, T fresh);

    // ========================================================================
    // OPTIONAL HOOKS — subclasses can override
    // ========================================================================

    /**
     * Called after {@link #fromJson}, before the asset is returned from {@link #load}.
     * Use this for post-processing like deriving a name from the file path.
     *
     * @param asset The loaded asset
     * @param path  The full file path
     */
    protected void afterLoad(T asset, String path) {}

    /**
     * Called before {@link #copyInto} during hot-reload.
     * Use this to invalidate caches or release resources before the copy.
     *
     * @param existing The cached instance about to be updated
     */
    protected void beforeReloadCopy(T existing) {}

    /**
     * Returns the editor panel type for this asset. Default: {@link EditorPanelType#ASSET_EDITOR}.
     *
     * @return The editor panel type
     */
    protected EditorPanelType editorPanelType() {
        return EditorPanelType.ASSET_EDITOR;
    }

    // ========================================================================
    // AssetLoader IMPLEMENTATION
    // ========================================================================

    @Override
    public final T load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            T asset = fromJson(json, path);
            afterLoad(asset, path);
            return asset;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load " + path, e);
        }
    }

    @Override
    public final void save(T resource, String path) throws IOException {
        try {
            JsonObject json = toJson(resource);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to save " + path, e);
        }
    }

    @Override
    public final T getPlaceholder() {
        if (placeholder == null) {
            placeholder = createPlaceholder();
        }
        return placeholder;
    }

    @Override
    public final String[] getSupportedExtensions() {
        return extensions();
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public T reload(T existing, String path) throws IOException {
        beforeReloadCopy(existing);
        T fresh = load(path);
        copyInto(existing, fresh);
        return existing;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public EditorPanelType getEditorPanelType() {
        return editorPanelType();
    }

    @Override
    public String getIconCodepoint() {
        return iconCodepoint();
    }
}
