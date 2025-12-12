package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;

import java.io.IOException;

/**
 * Loader for scene
 */
public class SceneLoader implements AssetLoader<Scene> {

    private final AssetManager assetManager;

    public SceneLoader(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    @Override
    public Scene load(String path) throws IOException {
        try {
            // Load scene directly (path is already fully resolved by AssetManager)
            return assetManager.load(path);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load texture for sprite: " + path, e);
        }
    }

    @Override
    public void save(Scene resource, String path) throws IOException {
        assetManager.persist(resource, path);
    }

    @Override
    public Scene getPlaceholder() {
        return new RuntimeScene("Placeholder scene");
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[0];
    }
}
