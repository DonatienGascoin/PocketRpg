package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.SceneData;
import com.pocket.rpg.utils.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Loader for scene
 */
public class SceneDataLoader implements AssetLoader<SceneData> {

    @Override
    public SceneData load(String path) throws IOException {
        try {
            // Load scene directly (path is already fully resolved by AssetManager)
            return FileUtils.readFileAndDeserialize(new File(path), SceneData.class);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load texture for sprite: " + path, e);
        }
    }

    @Override
    public void save(SceneData resource, String path) throws IOException {
        FileUtils.serializeAndWriteToFile(new File(path), resource);
    }

    @Override
    public SceneData getPlaceholder() {
        return new SceneData("Placeholder sceneData");
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".scene"};
    }
}
