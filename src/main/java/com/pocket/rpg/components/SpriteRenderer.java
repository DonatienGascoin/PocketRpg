package com.pocket.rpg.components;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import lombok.Getter;
import lombok.Setter;

/**
 * Component that renders a sprite at the GameObject's Transform position.
 * Holds the sprite reference and rendering properties.
 * <p>
 * Z-ordering uses {@link #zIndex} (not Transform.position.z):
 * - Lower zIndex renders first (background)
 * - Higher zIndex renders on top (foreground)
 * - Default is 0
 */
public class SpriteRenderer extends Component {

    @Getter
    @Setter
    private Sprite sprite;

    /**
     * Rotation/scale origin X (0-1, relative to sprite size).
     * 0 = left edge, 0.5 = center, 1 = right edge.
     */
    @Getter
    private float originX = 0.5f;

    /**
     * Rotation/scale origin Y (0-1, relative to sprite size).
     * 0 = top edge, 0.5 = center, 1 = bottom edge.
     */
    @Getter
    private float originY = 0.5f;

    /**
     * Sorting order for rendering. Higher values render on top.
     * This is independent of Transform.position.z.
     * <p>
     * Typical usage:
     * - Background tiles: 0
     * - Ground objects: 10
     * - Characters: 20
     * - Foreground effects: 30
     * - UI elements: 100
     */
    @Getter
    @Setter
    private int zIndex = 0;

    /**
     * If true, this sprite's vertices are cached (not recalculated each frame).
     * Use for sprites that never move, rotate, or scale (background tiles, buildings).
     * <p>
     * WARNING: If you modify a static sprite's transform, call
     * scene.markStaticBatchDirty() to rebuild the cache!
     */
    @Getter
    private boolean isStatic = false;

    /**
     * Creates a SpriteRenderer with a sprite.
     *
     * @param sprite The sprite to render
     */
    public SpriteRenderer(Sprite sprite) {
        this.sprite = sprite;
    }

    /**
     * Creates a SpriteRenderer from a texture.
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

    /**
     * Marks this sprite as static for vertex caching.
     *
     * @param isStatic true to enable caching
     */
    public void setStatic(boolean isStatic) {
        if (this.isStatic != isStatic) {
            this.isStatic = isStatic;

            // Notify scene to rebuild static batch
            if (gameObject != null && gameObject.getScene() != null) {
                gameObject.getScene().markStaticBatchDirty();
            }
        }
    }

    @Override
    public void onDestroy() {
        sprite = null;
    }

    @Override
    public String toString() {
        return String.format("SpriteRenderer[sprite=%s, origin=(%.2f,%.2f), zIndex=%d, static=%b]",
                sprite != null ? sprite.getName() : "null", originX, originY, zIndex, isStatic);
    }
}