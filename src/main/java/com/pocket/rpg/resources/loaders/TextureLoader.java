package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.serialization.ComponentData;
import org.joml.Vector3f;

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

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorEntity instantiate(Texture asset, String assetPath, Vector3f position) {
        // Extract entity name from filename
        String entityName = extractEntityName(assetPath);

        // Create scratch entity
        EditorEntity entity = new EditorEntity(entityName, position, false);

        // Create Sprite from Texture
        Sprite sprite = new Sprite(asset, assetPath);

        // Add SpriteRenderer component with Sprite object
        ComponentData spriteRenderer = new ComponentData("com.pocket.rpg.components.SpriteRenderer");
        spriteRenderer.getFields().put("sprite", sprite);
        spriteRenderer.getFields().put("zIndex", 0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Texture asset) {
        // Create sprite wrapper for preview
        if (asset != null) {
            return new Sprite(asset);
        }
        return null;
    }

    @Override
    public String getIconCodepoint() {
        return FontAwesomeIcons.Image;
    }

    /**
     * Extracts entity name from asset path.
     * Example: "textures/player.png" -> "player"
     */
    private String extractEntityName(String assetPath) {
        // Get filename
        int lastSlash = Math.max(assetPath.lastIndexOf('/'), assetPath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? assetPath.substring(lastSlash + 1) : assetPath;

        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        return lastDot >= 0 ? filename.substring(0, lastDot) : filename;
    }
}
