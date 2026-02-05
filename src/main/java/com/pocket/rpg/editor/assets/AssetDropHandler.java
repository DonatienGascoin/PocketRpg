package com.pocket.rpg.editor.assets;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;
import org.joml.Vector3f;

/**
 * Handles asset drop operations in the editor.
 * <p>
 * Converts dropped assets to EditorEntity instances using the appropriate
 * AssetLoader's instantiation logic.
 */
public class AssetDropHandler {

    /**
     * Creates an EditorEntity from a dropped asset.
     *
     * @param payload  The drag-drop payload
     * @param position World position for the entity
     * @return New EditorEntity, or null if asset cannot be instantiated
     */
    public static EditorGameObject handleDrop(AssetDragPayload payload, Vector3f position) {
        if (payload == null || payload.path() == null) {
            return null;
        }

        try {
            // Handle sub-asset paths (spritesheet#index) - load as Sprite directly
            if (payload.isSubAsset()) {
                return createSpriteEntity(payload.path(), position);
            }

            // Delegate to the loader's instantiate method via Assets facade
            return Assets.instantiate(payload.path(), payload.type(), position);

        } catch (Exception e) {
            System.err.println("Error handling asset drop: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Creates an entity with a sprite from a path (handles both direct and #index format).
     */
    private static EditorGameObject createSpriteEntity(String path, Vector3f position) {
        String entityName = extractEntityName(path);
        
        // Append sub-asset ID to name if present
        String subId = SpriteReference.getSubAssetId(path);
        if (subId != null) {
            entityName = entityName + "_" + subId;
        }
        
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        // Load the sprite
        Sprite sprite = null;
        try {
            sprite = Assets.load(path, Sprite.class);
        } catch (Exception e) {
            System.err.println("Failed to load sprite: " + path);
        }

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprite);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    /**
     * Extracts entity name from asset path.
     * Handles both direct paths and #index format.
     */
    private static String extractEntityName(String assetPath) {
        // Get base path (without #index)
        String basePath = SpriteReference.getBasePath(assetPath);
        if (basePath == null) basePath = assetPath;
        
        int lastSlash = Math.max(basePath.lastIndexOf('/'), basePath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? basePath.substring(lastSlash + 1) : basePath;

        int firstDot = filename.indexOf('.');
        return firstDot >= 0 ? filename.substring(0, firstDot) : filename;
    }

    /**
     * Checks if a payload can be instantiated.
     * Delegates to the loader's canInstantiate() method via Assets facade.
     */
    public static boolean canInstantiate(AssetDragPayload payload) {
        if (payload == null) return false;

        // Sub-assets (spritesheet#index) can always be instantiated as sprites
        if (payload.isSubAsset()) return true;

        return Assets.canInstantiate(payload.type());
    }
}
