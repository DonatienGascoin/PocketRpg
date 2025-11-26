package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.ResourceHandle;

import java.io.IOException;

/**
 * Loader for sprite assets.
 * Creates sprites by automatically loading their textures through AssetManager.
 * 
 * This loader treats image files as sprites by:
 * 1. Loading the texture via TextureLoader
 * 2. Creating a sprite that uses the full texture
 * 
 * The texture is loaded through AssetManager, so it benefits from caching
 * and will be shared if loaded elsewhere.
 * 
 * Usage:
 * <pre>
 * AssetManager manager = AssetManager.getInstance();
 * manager.registerLoader("sprite", new SpriteLoader());
 * 
 * // Loads texture automatically and creates sprite
 * ResourceHandle&lt;Sprite&gt; sprite = manager.load("player.png", "sprite");
 * </pre>
 */
public class SpriteLoader implements AssetLoader<Sprite> {

    /**
     * Loads a sprite from an image file.
     * Automatically loads the texture via AssetManager.
     *
     * @param path Path to the image file
     * @return Sprite using the loaded texture
     * @throws IOException if texture loading fails
     */
    @Override
    public Sprite load(String path) throws IOException {
        // Load texture through AssetManager (benefits from caching)
        ResourceHandle<Texture> textureHandle = AssetManager.getInstance().load(path, "texture");

        if (!textureHandle.isReady()) {
            throw new IOException("Failed to load texture for sprite: " + path);
        }

        Texture texture = textureHandle.get();
        if (texture == null) {
            throw new IOException("Texture is null for sprite: " + path);
        }

        // Create sprite from texture
        return new Sprite(texture, path);
    }

    /**
     * Unloads a sprite.
     * Note: The texture is NOT unloaded here since it's managed by AssetManager
     * and may be used elsewhere.
     *
     * @param sprite The sprite to unload
     */
    @Override
    public void unload(Sprite sprite) {
        // Sprite doesn't own GPU resources - texture is managed separately
        // Just let it be garbage collected
    }

    /**
     * Returns supported image file extensions (same as TextureLoader).
     *
     * @return Array of supported extensions
     */
    @Override
    public String[] getSupportedExtensions() {
        return new String[]{
                ".png",
                ".jpg", ".jpeg",
                ".bmp",
                ".tga"
        };
    }

    /**
     * Returns a placeholder sprite.
     * Uses the texture placeholder if available.
     *
     * @return Placeholder sprite, or null
     */
    @Override
    public Sprite getPlaceholder() {
        // Could create a sprite from texture placeholder
        // For now, return null
        return null;
    }

    /**
     * Sprites support hot reloading via their textures.
     *
     * @return true
     */
    @Override
    public boolean supportsHotReload() {
        return true;
    }

    /**
     * Reloads a sprite by reloading its texture.
     *
     * @param existing The existing sprite
     * @param path     Path to reload from
     * @return Updated sprite
     * @throws IOException if reload fails
     */
    @Override
    public Sprite reload(Sprite existing, String path) throws IOException {
        // Simply load a fresh sprite - texture reload is handled by TextureLoader
        return load(path);
    }

    /**
     * Estimates sprite memory usage.
     * Sprites themselves are lightweight - just references to textures.
     *
     * @param sprite The sprite to measure
     * @return Estimated size in bytes (very small)
     */
    @Override
    public long estimateSize(Sprite sprite) {
        if (sprite == null) return 0;

        // Sprite is just metadata - ~100 bytes
        // Texture size is counted separately
        return 100;
    }

    /**
     * Gets the loader type name.
     *
     * @return "sprite"
     */
    @Override
    public String getTypeName() {
        return "sprite";
    }
}
