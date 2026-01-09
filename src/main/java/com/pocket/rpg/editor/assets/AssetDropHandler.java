package com.pocket.rpg.editor.assets;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;
import com.pocket.rpg.resources.loaders.SpriteSheetLoader;
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

            // Load the asset
            Object asset = Assets.load(payload.path(), payload.type());
            if (asset == null) {
                System.err.println("Failed to load asset for drop: " + payload.path());
                return null;
            }

            // Use loader's instantiate method
            return instantiateFromLoader(asset, payload.path(), position, payload.type());

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
     * Instantiates an entity using the appropriate loader.
     */
    @SuppressWarnings("unchecked")
    private static EditorGameObject instantiateFromLoader(Object asset, String path,
                                                          Vector3f position, Class<?> type) {
        // Handle known types with their loaders
        if (asset instanceof Sprite sprite) {
            return createSpriteEntityFromSprite(sprite, path, position);
        }
        if (asset instanceof Texture texture) {
            return createTextureEntity(texture, path, position);
        }
        if (asset instanceof SpriteSheet sheet) {
            // Default to first sprite
            SpriteSheetLoader loader = new SpriteSheetLoader();
            return loader.instantiateWithIndex(sheet, path, position, 0);
        }
        if (asset instanceof JsonPrefab prefab) {
            return createPrefabEntity(prefab, path, position);
        }

        System.err.println("No instantiation handler for type: " + type.getSimpleName());
        return null;
    }

    private static EditorGameObject createSpriteEntityFromSprite(Sprite sprite, String path, Vector3f position) {
        String entityName = extractEntityName(path);
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprite);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    private static EditorGameObject createTextureEntity(Texture texture, String path, Vector3f position) {
        String entityName = extractEntityName(path);
        EditorGameObject entity = new EditorGameObject(entityName, position, false);

        // Create Sprite from Texture
        Sprite sprite = new Sprite(texture, path);

        SpriteRenderer spriteRenderer = new SpriteRenderer();
        spriteRenderer.setSprite(sprite);
        spriteRenderer.setZIndex(0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    private static EditorGameObject createPrefabEntity(JsonPrefab prefab, String path, Vector3f position) {
        // Create prefab instance
        EditorGameObject entity = new EditorGameObject(prefab.getId(), position);

        String displayName = prefab.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            entity.setName(displayName + "_" + entity.getId().substring(0, 4));
        }

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
     */
    public static boolean canInstantiate(AssetDragPayload payload) {
        if (payload == null) return false;

        // Sub-assets (spritesheet#index) can always be instantiated as sprites
        if (payload.isSubAsset()) return true;

        Class<?> type = payload.type();
        return type == Sprite.class ||
                type == Texture.class ||
                type == SpriteSheet.class ||
                type == JsonPrefab.class;
    }
}
