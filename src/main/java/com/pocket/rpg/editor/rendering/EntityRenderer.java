package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.serialization.ComponentData;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Renders entities in the editor viewport.
 * <p>
 * Uses SpriteBatch for efficient batched rendering.
 * Entities are rendered after tilemap layers but before overlays.
 */
public class EntityRenderer {

    // Default z-index for entities (above most tilemap layers)
    private static final float DEFAULT_ENTITY_Z_INDEX = 100f;
    private static final Vector4f DEFAULT_TINT = new Vector4f(1f, 1f, 1f, 1f);

    /**
     * Renders all entities in the scene.
     * <p>
     * Call this between batch.begin() and batch.end(), after tilemap layers.
     *
     * @param batch  SpriteBatch currently in batching mode
     * @param scene  EditorScene containing entities
     */
    public void render(SpriteBatch batch, EditorScene scene) {
        if (scene == null) return;

        List<EditorEntity> entities = scene.getEntities();
        if (entities.isEmpty()) return;

        for (EditorEntity entity : entities) {
            renderEntity(batch, entity, DEFAULT_TINT);
        }
    }

    /**
     * Renders all entities with a custom tint.
     */
    public void render(SpriteBatch batch, EditorScene scene, Vector4f tint) {
        if (scene == null) return;

        List<EditorEntity> entities = scene.getEntities();
        if (entities.isEmpty()) return;

        for (EditorEntity entity : entities) {
            renderEntity(batch, entity, tint);
        }
    }

    /**
     * Renders a single entity.
     */
    private void renderEntity(SpriteBatch batch, EditorEntity entity, Vector4f tint) {
        Sprite sprite = entity.getPreviewSprite();
        if (sprite == null) return;

        Vector3f pos = entity.getPositionRef();
        Vector2f size = entity.getPreviewSize();
        if (size == null) size = new Vector2f(1f, 1f);

        // Get origin from SpriteRenderer component (default 0,0 = bottom-left)
        float originX = 0f;
        float originY = 0f;
        ComponentData spriteRenderer = entity.getComponentByType("SpriteRenderer");
        if (spriteRenderer != null) {
            Object ox = spriteRenderer.getFields().get("originX");
            Object oy = spriteRenderer.getFields().get("originY");
            if (ox instanceof Number) originX = ((Number)ox).floatValue();
            if (oy instanceof Number) originY = ((Number)oy).floatValue();
        }

        float drawX = pos.x - (size.x * originX);
        float drawY = pos.y - (size.y * originY);
        float zIndex = pos.z != 0 ? pos.z : DEFAULT_ENTITY_Z_INDEX;

        batch.draw(sprite, drawX, drawY, size.x, size.y, zIndex, tint);
    }
}