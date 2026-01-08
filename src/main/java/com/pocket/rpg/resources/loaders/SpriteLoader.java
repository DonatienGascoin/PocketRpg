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
 * Loader for sprite assets.
 * Creates sprites from textures.
 * <p>
 * Note: Path tracking is handled centrally by {@link com.pocket.rpg.resources.AssetManager#getPathForResource(Object)}.
 * Sprites loaded through this loader are automatically registered in the resourcePaths map.
 */
public class SpriteLoader implements AssetLoader<Sprite> {

    @Override
    public Sprite load(String path) throws IOException {
        try {
            // Load texture directly (path is already fully resolved by AssetManager)
            Texture texture = new Texture(path);

            // Create sprite from texture
            // Path tracking is handled by AssetManager.resourcePaths
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

    // ========================================================================
    // EDITOR INSTANTIATION SUPPORT
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorEntity instantiate(Sprite asset, String assetPath, Vector3f position) {
        // Extract entity name from filename
        String entityName = extractEntityName(assetPath);

        // Create scratch entity
        EditorEntity entity = new EditorEntity(entityName, position, false);

        // Add SpriteRenderer component with actual Sprite object
        ComponentData spriteRenderer = new ComponentData("com.pocket.rpg.components.SpriteRenderer");
        spriteRenderer.getFields().put("sprite", asset);
        spriteRenderer.getFields().put("zIndex", 0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Sprite asset) {
        return asset; // Sprite is its own preview
    }

    @Override
    public String getIconCodepoint() {
        return FontAwesomeIcons.Image;
    }

    /**
     * Extracts entity name from asset path.
     * Example: "sprites/player.png" -> "player"
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
