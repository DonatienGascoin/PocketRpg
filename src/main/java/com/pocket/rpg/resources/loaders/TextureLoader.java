package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;

/**
 * Loader for texture assets.
 * Supports PNG, JPG, BMP, TGA formats via STB Image.
 */
public class TextureLoader implements AssetLoader<Texture> {
    
    private static Texture placeholderTexture = null;
    
    @Override
    public Texture load(String path) throws IOException {
        try {
            return new Texture(path);
        } catch (RuntimeException e) {
            // Texture constructor throws RuntimeException on failure
            throw new IOException("Failed to load texture: " + path, e);
        }
    }
    
    @Override
    public void save(Texture texture, String path) throws IOException {
        throw new UnsupportedOperationException("Texture saving not supported");
    }
    
    @Override
    public Texture getPlaceholder() {
        if (placeholderTexture == null) {
            placeholderTexture = createPlaceholderTexture();
        }
        return placeholderTexture;
    }
    
    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".png", ".jpg", ".jpeg", ".bmp", ".tga"};
    }
    
    @Override
    public boolean supportsHotReload() {
        return true;
    }
    
    @Override
    public Texture reload(Texture existing, String path) throws IOException {
        // Destroy old texture
        if (existing != null) {
            existing.destroy();
        }
        // Load new texture
        return load(path);
    }
    
    /**
     * Creates a 1x1 magenta placeholder texture.
     * Magenta is highly visible and indicates missing texture.
     */
    private Texture createPlaceholderTexture() {
        // TODO: Create actual 1x1 magenta texture when needed
        // For now, return null and let the system handle it
        return null;
    }
}
