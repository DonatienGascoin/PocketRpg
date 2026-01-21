package com.pocket.rpg.resources.loaders;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.EditorPanel;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.EditorCapability;
import com.pocket.rpg.resources.SpriteMetadata;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.Set;

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
            Sprite sprite = new Sprite(texture, path);

            // Apply metadata if it exists (pivot, ppu override, 9-slice)
            String relativePath = Assets.getRelativePath(path);
            if (relativePath != null) {
                SpriteMetadata meta = AssetMetadata.load(relativePath, SpriteMetadata.class);
                if (meta != null) {
                    if (meta.hasPivot()) {
                        sprite.setPivot(meta.pivotX, meta.pivotY);
                    }
                    if (meta.pixelsPerUnitOverride != null) {
                        sprite.setPixelsPerUnitOverride(meta.pixelsPerUnitOverride);
                    }
                    if (meta.hasNineSlice()) {
                        sprite.setNineSliceData(meta.nineSlice.copy());
                    }
                }
            }

            return sprite;
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
    public EditorGameObject instantiate(Sprite asset, String assetPath, Vector3f position) {
        // Extract entity name from filename
        String entityName = extractEntityName(assetPath);

        // Create scratch entity
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        // Add SpriteRenderer component with actual Sprite object
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(asset);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Sprite asset) {
        return asset; // Sprite is its own preview
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Image;
    }

    @Override
    public Set<EditorCapability> getEditorCapabilities() {
        return Set.of(EditorCapability.PIVOT_EDITING);
    }

    @Override
    public EditorPanel getEditorPanel() {
        return EditorPanel.SPRITE_EDITOR;
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
