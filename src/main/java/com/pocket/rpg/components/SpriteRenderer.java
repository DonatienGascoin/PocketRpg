package com.pocket.rpg.components;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import lombok.Getter;
import lombok.Setter;

/**
 * Component that renders a sprite at the GameObject's Transform position.
 * Holds the sprite reference and rendering properties (origin/pivot).
 *
 * No longer needs update() - the Renderer reads Transform directly.
 */
public class SpriteRenderer extends Component {
    @Getter
    @Setter
    private Sprite sprite;

    // Rotation/scale origin (0-1, relative to sprite size)
    // (0.5, 0.5) = center, (0, 0) = top-left, (1, 1) = bottom-right
    @Getter
    private float originX = 0.5f;
    @Getter
    private float originY = 0.5f;

    /**
     * Creates a SpriteRenderer with a sprite.
     *
     * @param sprite The sprite to render
     */
    public SpriteRenderer(Sprite sprite) {
        this.sprite = sprite;
    }

    /**
     * Creates a SpriteRenderer from a texture (creates a sprite automatically).
     *
     * @param texture The texture to render
     */
    public SpriteRenderer(Texture texture) {
        this.sprite = new Sprite(texture);
    }

    /**
     * Creates a SpriteRenderer from a texture with custom size.
     *
     * @param texture The texture to render
     * @param width   Sprite width
     * @param height  Sprite height
     */
    public SpriteRenderer(Texture texture, float width, float height) {
        this.sprite = new Sprite(texture, width, height);
    }

    /**
     * Sets the rotation/scale origin point.
     *
     * @param originX X origin (0-1, where 0.5 is center)
     * @param originY Y origin (0-1, where 0.5 is center)
     */
    public void setOrigin(float originX, float originY) {
        this.originX = originX;
        this.originY = originY;
    }

    /**
     * Sets the origin to top-left corner.
     */
    public void setOriginTopLeft() {
        setOrigin(0f, 0f);
    }

    /**
     * Sets the origin to center (default).
     */
    public void setOriginCenter() {
        setOrigin(0.5f, 0.5f);
    }

    /**
     * Sets the origin to bottom-left corner.
     */
    public void setOriginBottomLeft() {
        setOrigin(0f, 1f);
    }

    /**
     * Sets the origin to top-right corner.
     */
    public void setOriginTopRight() {
        setOrigin(1f, 0f);
    }

    /**
     * Sets the origin to bottom-right corner.
     */
    public void setOriginBottomRight() {
        setOrigin(1f, 1f);
    }

    @Override
    public void destroy() {
        // Note: We don't destroy the sprite's texture here as it might be shared
        // Texture cleanup should be handled by a resource manager
        sprite = null;
    }

    @Override
    public String toString() {
        return String.format("SpriteRenderer[sprite=%s, origin=(%.2f,%.2f)]",
                sprite != null ? sprite.getName() : "null", originX, originY);
    }
}