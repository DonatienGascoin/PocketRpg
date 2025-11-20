package com.pocket.rpg.components;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import org.joml.Vector3f;

/**
 * Component that renders a sprite at the GameObject's position.
 * Automatically syncs with the Transform component.
 */
public class SpriteRenderer extends Component {
    private Sprite sprite;

    public SpriteRenderer(Sprite sprite) {
        this.sprite = sprite;
    }

    public SpriteRenderer(Texture texture) {
        this.sprite = new Sprite(texture);
    }

    public SpriteRenderer(Texture texture, float width, float height) {
        this.sprite = new Sprite(texture, 0, 0, width, height);
    }

    public Sprite getSprite() {
        return sprite;
    }

    public void setSprite(Sprite sprite) {
        this.sprite = sprite;
    }

    @Override
    public void update(float deltaTime) {
        if (sprite != null && gameObject != null) {
            // Sync sprite with transform
            Transform transform = gameObject.getTransform();
            if (transform != null) {
                Vector3f pos = transform.getPosition();
                Vector3f rot = transform.getRotation();
                Vector3f scale = transform.getScale();

                sprite.setPosition(pos.x, pos.y);
                sprite.setRotation(rot.z); // Use Z rotation for 2D
                sprite.setSize(sprite.getWidth() * scale.x, sprite.getHeight() * scale.y);
            }
        }
    }

    @Override
    public void destroy() {
        // Note: We don't destroy the sprite's texture here as it might be shared
        // Texture cleanup should be handled by a resource manager
        sprite = null;
    }
}