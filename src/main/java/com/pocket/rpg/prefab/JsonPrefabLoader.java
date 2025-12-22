package com.pocket.rpg.prefab;

import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.Serializer;

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
        String jsonContent = Serializer.toJson(prefab, true);

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
}