package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;

/**
 * Loader for sprite assets.
 * Creates sprites from textures. Note: Textures are NOT cached separately,
 * each sprite loads its own texture instance.
 */
public class SpriteLoader implements AssetLoader<Sprite> {

    @Override
    public Sprite load(String path) throws IOException {
        try {
            // Load texture directly (path is already fully resolved by AssetManager)
            Texture texture = new Texture(path);

            // Create sprite from texture
            return new Sprite(texture, path);
        } catch (RuntimeException e) {
            throw new IOException("Failed to load texture for sprite: " + path, e);
        }
    }

    @Override
    public void save(Sprite sprite, String path) throws IOException {
        throw new UnsupportedOperationException("Sprite saving not supported");
    }

    @Override
    public Sprite getPlaceholder() {
        // Could create a sprite from texture placeholder
        // For now, return null
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        // Same as TextureLoader - sprites are created from images
        return new String[]{".png", ".jpg", ".jpeg", ".bmp", ".tga"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public Sprite reload(Sprite existing, String path) throws IOException {
        // Simply load fresh sprite - texture reload is handled by TextureLoader
        return load(path);
    }
}