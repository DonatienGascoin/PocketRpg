package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteBatch;
import com.pocket.rpg.serialization.ComponentData;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Renders EditorEntity objects using SpriteBatch.
 * Uses {@link EditorEntity#getCurrentSprite()} for animation support.
 */
public class EntityRenderer {

    private static final Vector4f DEFAULT_TINT = new Vector4f(1f, 1f, 1f, 1f);

    public void render(SpriteBatch batch, EditorScene scene) {
        render(batch, scene, DEFAULT_TINT);
    }

    public void render(SpriteBatch batch, EditorScene scene, Vector4f tint) {
        if (scene == null) return;

        List<EditorEntity> entities = scene.getEntities();
        if (entities.isEmpty()) return;

        for (EditorEntity entity : entities) {
            renderEntity(batch, entity, tint);
        }
    }

    private void renderEntity(SpriteBatch batch, EditorEntity entity, Vector4f tint) {
        Sprite sprite = entity.getCurrentSprite();
        if (sprite == null) return;

        Vector3f pos = entity.getPositionRef();

        // Read origin and scale from SpriteRenderer component
        float originX = 0f;
        float originY = 0f;
        float scaleX = 1f;
        float scaleY = 1f;

        ComponentData sr = entity.getComponentByType("SpriteRenderer");
        if (sr != null) {
            Object ox = sr.getFields().get("originX");
            Object oy = sr.getFields().get("originY");
            Object sx = sr.getFields().get("scaleX");
            Object sy = sr.getFields().get("scaleY");

            if (ox instanceof Number) originX = ((Number) ox).floatValue();
            if (oy instanceof Number) originY = ((Number) oy).floatValue();
            if (sx instanceof Number) scaleX = ((Number) sx).floatValue();
            if (sy instanceof Number) scaleY = ((Number) sy).floatValue();
        }

        float width = sprite.getWorldWidth() * scaleX;
        float height = sprite.getWorldHeight() * scaleY;

        float drawX = pos.x - (width * originX);
        float drawY = pos.y - (height * originY);

        float zIndex = entity.getZIndex();

        batch.draw(sprite, drawX, drawY, width, height, zIndex, tint);
    }
}