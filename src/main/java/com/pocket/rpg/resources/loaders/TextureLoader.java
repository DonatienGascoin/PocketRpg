package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;

/**
 * Loader for texture assets.
 * Wraps the existing Texture class and provides resource management integration.
 * 
 * Supports common image formats: PNG, JPG, BMP, TGA via STB Image.
 * 
 * Usage:
 * <pre>
 * AssetManager manager = AssetManager.getInstance();
 * manager.registerLoader("texture", new TextureLoader());
 * 
 * ResourceHandle&lt;Texture&gt; texture = manager.load("player.png");
 * </pre>
 */
public class TextureLoader implements AssetLoader<Texture> {

    // Placeholder texture (1x1 magenta pixel) for loading states
    private static Texture placeholder = null;

    /**
     * Loads a texture from the specified file path.
     *
     * @param path Path to the texture file
     * @return Loaded texture
     * @throws IOException if texture loading fails
     */
    @Override
    public Texture load(String path) throws IOException {
        try {
            return new Texture(path);
        } catch (RuntimeException e) {
            // Texture constructor throws RuntimeException on failure
            throw new IOException("Failed to load texture: " + path, e);
        }
    }

    /**
     * Unloads a texture and releases GPU resources.
     *
     * @param texture The texture to unload
     */
    @Override
    public void unload(Texture texture) {
        if (texture != null) {
            texture.destroy();
        }
    }

    /**
     * Returns supported texture file extensions.
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
     * Returns a placeholder texture to use while actual texture loads.
     * Creates a 1x1 magenta texture as a visual indicator.
     *
     * @return Placeholder texture
     */
    @Override
    public Texture getPlaceholder() {
        if (placeholder == null) {
            placeholder = createPlaceholderTexture();
        }
        return placeholder;
    }

    /**
     * Textures support hot reloading.
     *
     * @return true
     */
    @Override
    public boolean supportsHotReload() {
        return true;
    }

    /**
     * Reloads a texture from disk.
     * The old texture is destroyed and a new one is created.
     *
     * @param existing The existing texture (will be destroyed)
     * @param path     Path to reload from
     * @return New texture instance
     * @throws IOException if reload fails
     */
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
     * Estimates texture memory usage.
     * Calculation: width × height × channels × bytes per channel
     *
     * @param texture The texture to measure
     * @return Estimated size in bytes
     */
    @Override
    public long estimateSize(Texture texture) {
        if (texture == null) return 0;

        // width * height * channels * bytes per channel (1 byte for UNSIGNED_BYTE)
        return (long) texture.getWidth() * texture.getHeight() * texture.getChannels();
    }

    /**
     * Creates a 1x1 magenta placeholder texture.
     * Magenta is chosen because it's highly visible and indicates missing/loading texture.
     *
     * @return Placeholder texture
     */
    private Texture createPlaceholderTexture() {
        // Note: In a real implementation, you'd create a 1x1 magenta texture
        // For now, we'll just return null and let the system handle it
        // TODO: Implement actual placeholder creation when needed
        return null;
    }

    /**
     * Gets the loader type name.
     *
     * @return "texture"
     */
    @Override
    public String getTypeName() {
        return "texture";
    }
}
