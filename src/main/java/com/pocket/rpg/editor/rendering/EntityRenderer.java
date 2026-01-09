package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteBatch;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Renders EditorEntity objects using SpriteBatch.
 * Uses {@link EditorGameObject#getCurrentSprite()} for animation support.
 * Supports prefab overrides for all transform and sprite properties.
 */
public class EntityRenderer {

    private static final Vector4f DEFAULT_TINT = new Vector4f(1f, 1f, 1f, 1f);
    private static final String SPRITE_RENDERER_TYPE = "com.pocket.rpg.components.SpriteRenderer";

    public void render(SpriteBatch batch, EditorScene scene) {
        render(batch, scene, DEFAULT_TINT);
    }

    public void render(SpriteBatch batch, EditorScene scene, Vector4f tint) {
        if (scene == null) return;

        List<EditorGameObject> entities = scene.getEntities();
        if (entities.isEmpty()) return;

        for (EditorGameObject entity : entities) {
            renderEntity(batch, entity, tint);
        }
    }

    private void renderEntity(SpriteBatch batch, EditorGameObject entity, Vector4f tint) {
        Sprite sprite = entity.getCurrentSprite();
        if (sprite == null) return;

        Vector3f pos = entity.getPosition();
        Vector3f scale = entity.getScale();

        // Read origin from SpriteRenderer (with prefab override support)
        float originX = getFloatField(entity, SPRITE_RENDERER_TYPE, "originX", 0f);
        float originY = getFloatField(entity, SPRITE_RENDERER_TYPE, "originY", 0f);

        float width = sprite.getWorldWidth() * scale.x;
        float height = sprite.getWorldHeight() * scale.y;

        float drawX = pos.x - (width * originX);
        float drawY = pos.y - (height * originY);

        float zIndex = entity.getZIndex();

        batch.draw(sprite, drawX, drawY, width, height, zIndex, tint);
    }

    /**
     * Gets a float field value with prefab override support.
     * For prefab instances, checks overrides first.
     * For scratch entities, reads from component directly.
     */
    private float getFloatField(EditorGameObject entity, String componentType, String fieldName, float defaultValue) {
        Object value = entity.getFieldValue(componentType, fieldName);

        if (value instanceof Number num) {
            return num.floatValue();
        }

        return defaultValue;
    }
}