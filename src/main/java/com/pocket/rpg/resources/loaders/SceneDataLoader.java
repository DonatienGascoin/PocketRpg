package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.serialization.Serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

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

        // Clean up Transform/UITransform coexistence (legacy bug fix)
        cleanupTransformDuplicates(data);

        return data;
    }

    /**
     * Removes duplicate Transform components when UITransform exists.
     * This handles scenes created before the fix where both could coexist.
     */
    private void cleanupTransformDuplicates(SceneData data) {
        if (data.getGameObjects() == null) return;

        for (GameObjectData gameObject : data.getGameObjects()) {
            List<Component> components = gameObject.getComponents();
            if (components == null) continue;

            boolean hasUITransform = false;
            boolean hasTransform = false;

            for (Component comp : components) {
                if (comp.getClass() == UITransform.class) hasUITransform = true;
                else if (comp.getClass() == Transform.class) hasTransform = true;
            }

            // If both exist, remove plain Transform (UITransform takes precedence)
            if (hasUITransform && hasTransform) {
                components.removeIf(c -> c.getClass() == Transform.class);
                System.out.println("Cleaned up duplicate Transform on: " + gameObject.getName());
            }
        }
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