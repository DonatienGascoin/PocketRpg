package com.pocket.rpg.prefab;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.Serializer;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loader for JSON prefab files.
 * <p>
 * Registered with AssetManager to enable:
 * JsonPrefab prefab = Assets.load("prefabs/chest.prefab.json");
 */
public class JsonPrefabLoader implements AssetLoader<JsonPrefab> {

    @Override
    public JsonPrefab load(String path) throws IOException {
        String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
        JsonPrefab prefab = Serializer.fromJson(jsonContent, JsonPrefab.class);

        if (prefab == null) {
            throw new IOException("Failed to parse prefab: " + path);
        }

        // Store the source path for later saving
        prefab.setSourcePath(path);

        // Validate required fields
        if (prefab.getId() == null || prefab.getId().isEmpty()) {
            throw new IOException("Prefab missing required 'id' field: " + path);
        }

        return prefab;
    }

    @Override
    public void save(JsonPrefab prefab, String path) throws IOException {
        String jsonContent = Serializer.toPrettyJson(prefab);

        Path filePath = Paths.get(path);

        // Create parent directories if needed
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.write(filePath, jsonContent.getBytes());

        // Update source path
        prefab.setSourcePath(path);
    }

    @Override
    public JsonPrefab getPlaceholder() {
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".prefab.json", ".prefab"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorGameObject instantiate(JsonPrefab asset, String assetPath, Vector3f position) {
        if (asset == null) {
            return null;
        }

        // Create prefab instance (not scratch entity)
        EditorGameObject entity = new EditorGameObject(asset.getId(), position);

        // Name from prefab display name
        String displayName = asset.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            entity.setName(displayName + "_" + entity.getId().substring(0, 4));
        }

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(JsonPrefab asset) {
        if (asset != null) {
            return asset.getPreviewSprite();
        }
        return null;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Inventory2;
    }
}