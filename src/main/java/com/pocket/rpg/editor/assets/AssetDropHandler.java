package com.pocket.rpg.editor.assets;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.Assets;
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
    public static EditorEntity handleDrop(AssetDragPayload payload, Vector3f position) {
        if (payload == null || payload.path() == null) {
            return null;
        }

        try {
            // Load the asset
            Object asset = Assets.load(payload.path(), payload.type());
            if (asset == null) {
                System.err.println("Failed to load asset for drop: " + payload.path());
                return null;
            }

            // Get the asset manager and loader
            AssetManager manager = (AssetManager) Assets.getContext();

            // Handle SpriteSheet with specific sprite index
            if (payload.isSpriteSheetSprite() && asset instanceof SpriteSheet sheet) {
                return instantiateSpriteSheetSprite(sheet, payload.path(), position, payload.spriteIndex());
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
     * Instantiates a specific sprite from a spritesheet.
     */
    private static EditorEntity instantiateSpriteSheetSprite(SpriteSheet sheet, String path,
                                                             Vector3f position, int spriteIndex) {
        // Use the SpriteSheetLoader's specialized method
        SpriteSheetLoader loader = new SpriteSheetLoader();
        return loader.instantiateWithIndex(sheet, path, position, spriteIndex);
    }

    /**
     * Instantiates an entity using the appropriate loader.
     */
    @SuppressWarnings("unchecked")
    private static EditorEntity instantiateFromLoader(Object asset, String path,
                                                      Vector3f position, Class<?> type) {
        // Get loader for type
        AssetManager manager = (AssetManager) Assets.getContext();

        // Handle known types with their loaders
        if (asset instanceof Sprite sprite) {
            return createSpriteEntity(sprite, path, position);
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

    private static EditorEntity createSpriteEntity(Sprite sprite, String path, Vector3f position) {
        String entityName = extractEntityName(path);
        EditorEntity entity = new EditorEntity(entityName, position, false);

        com.pocket.rpg.serialization.ComponentData spriteRenderer =
                new com.pocket.rpg.serialization.ComponentData("com.pocket.rpg.components.SpriteRenderer");
        spriteRenderer.getFields().put("sprite", sprite);
        spriteRenderer.getFields().put("zIndex", 0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    private static EditorEntity createTextureEntity(Texture texture, String path, Vector3f position) {
        String entityName = extractEntityName(path);
        EditorEntity entity = new EditorEntity(entityName, position, false);

        // Create Sprite from Texture
        com.pocket.rpg.rendering.Sprite sprite = new com.pocket.rpg.rendering.Sprite(texture, path);

        com.pocket.rpg.serialization.ComponentData spriteRenderer =
                new com.pocket.rpg.serialization.ComponentData("com.pocket.rpg.components.SpriteRenderer");
        spriteRenderer.getFields().put("sprite", sprite);
        spriteRenderer.getFields().put("zIndex", 0);
        entity.addComponent(spriteRenderer);

        return entity;
    }

    private static EditorEntity createPrefabEntity(JsonPrefab prefab, String path, Vector3f position) {
        // Create prefab instance
        EditorEntity entity = new EditorEntity(prefab.getId(), position);

        String displayName = prefab.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            entity.setName(displayName + "_" + entity.getId().substring(0, 4));
        }

        return entity;
    }

    /**
     * Extracts entity name from asset path.
     */
    private static String extractEntityName(String assetPath) {
        int lastSlash = Math.max(assetPath.lastIndexOf('/'), assetPath.lastIndexOf('\\'));
        String filename = lastSlash >= 0 ? assetPath.substring(lastSlash + 1) : assetPath;

        int firstDot = filename.indexOf('.');
        return firstDot >= 0 ? filename.substring(0, firstDot) : filename;
    }

    /**
     * Checks if a payload can be instantiated.
     */
    public static boolean canInstantiate(AssetDragPayload payload) {
        if (payload == null) return false;

        Class<?> type = payload.type();
        return type == Sprite.class ||
                type == Texture.class ||
                type == SpriteSheet.class ||
                type == JsonPrefab.class;
    }
}
