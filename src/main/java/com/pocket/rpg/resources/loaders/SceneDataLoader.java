package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Loader for scene files.
 * Handles both v3 (legacy) and v4 (current) scene formats.
 */
public class SceneDataLoader implements AssetLoader<SceneData> {

    @Override
    public SceneData load(String path) throws IOException {
        String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
        SceneData data = Serializer.fromJson(jsonContent, SceneData.class);

        if (data == null) {
            throw new IOException("Failed to parse scene: " + path);
        }

        // Migrate v3 scenes to v4 format
        if (data.needsMigration()) {
            System.out.println("Migrating scene from v" + data.getVersion() + " to v4: " + path);
            data.migrateToV4();
        }

        return data;
    }

    @Override
    public void save(SceneData data, String path) throws IOException {
        // Always save as v4
        data.setVersion(4);
        String jsonContent = Serializer.toPrettyJson(data);
        Files.write(Paths.get(path), jsonContent.getBytes());
    }

    @Override
    public SceneData getPlaceholder() {
        return new SceneData("Empty Scene");
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".scene", ".scene.json"};
    }

    @Override
    public boolean supportsHotReload() {
        return false;  // Scenes shouldn't hot-reload while editing
    }
}