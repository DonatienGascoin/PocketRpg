package com.pocket.rpg.components;

import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * Component that renders a sprite at the GameObject's Transform position.
 * Holds the sprite reference and rendering properties.
 * <p>
 * Z-ordering uses {@link #zIndex} (not Transform.position.z):
 * - Lower zIndex renders first (background)
 * - Higher zIndex renders on top (foreground)
 * - Default is 0
 *
 * <h2>Origin/Pivot</h2>
 * The origin point for positioning and rotation can be set in two ways:
 * <ul>
 *   <li>Use sprite's pivot: Set {@link #useSpritePivot} to true (default)</li>
 *   <li>Override manually: Set {@link #useSpritePivot} to false and use {@link #setOrigin(float, float)}</li>
 * </ul>
 */
public class SpriteRenderer extends Component implements Renderable {

    @Getter
    @Setter
    private Sprite sprite;

    @Getter
    @Setter
    private Vector4f tintColor = new Vector4f(1f, 1f, 1f, 1f);

    /**
     * If true, uses the sprite's pivot point. If false, uses originX/originY.
     * Default is true.
     */
    @Getter
    @Setter
    private boolean useSpritePivot = true;

    /**
     * Rotation/scale origin X (0-1, relative to sprite size).
     * 0 = left edge, 0.5 = center, 1 = right edge.
     * Only used when {@link #useSpritePivot} is false.
     */
    @Getter
    private float originX = 0.5f;

    /**
     * Rotation/scale origin Y (0-1, relative to sprite size).
     * 0 = bottom edge, 0.5 = center, 1 = top edge.
     * Only used when {@link #useSpritePivot} is false.
     */
    @Getter
    private float originY = 0f;

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

    public SpriteRenderer() {

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

    // ========================================================================
    // RENDERABLE IMPLEMENTATION
    // ========================================================================

    /**
     * {@inheritDoc}
     * <p>
     * Checks component enabled state, GameObject enabled state, and sprite validity.
     */
    @Override
    public boolean isRenderVisible() {
        if (!isEnabled()) {
            return false;
        }
        if (gameObject == null || !gameObject.isEnabled()) {
            return false;
        }
        return sprite != null;
    }

    // ========================================================================
    // EFFECTIVE ORIGIN (resolves sprite pivot vs override)
    // ========================================================================

    /**
     * Gets the effective origin X, considering sprite pivot and override.
     *
     * @return Origin X (0-1)
     */
    public float getEffectiveOriginX() {
        if (useSpritePivot && sprite != null) {
            return sprite.getPivotX();
        }
        return originX;
    }

    /**
     * Gets the effective origin Y, considering sprite pivot and override.
     *
     * @return Origin Y (0-1)
     */
    public float getEffectiveOriginY() {
        if (useSpritePivot && sprite != null) {
            return sprite.getPivotY();
        }
        return originY;
    }

    // ========================================================================
    // ORIGIN CONFIGURATION
    // ========================================================================

    /**
     * Sets the rotation/scale origin point and disables sprite pivot.
     *
     * @param originX X origin (0-1, where 0.5 is center)
     * @param originY Y origin (0-1, where 0.5 is center)
     */
    public void setOrigin(float originX, float originY) {
        this.originX = originX;
        this.originY = originY;
        this.useSpritePivot = false;
    }

    /**
     * Sets the origin to top-left corner.
     */
    public void setOriginTopLeft() {
        setOrigin(0f, 1f);
    }

    /**
     * Sets the origin to center.
     */
    public void setOriginCenter() {
        setOrigin(0.5f, 0.5f);
    }

    /**
     * Sets the origin to bottom-left corner.
     */
    public void setOriginBottomLeft() {
        setOrigin(0f, 0f);
    }

    /**
     * Sets the origin to bottom-center (good for characters on tiles).
     */
    public void setOriginBottomCenter() {
        setOrigin(0.5f, 0f);
    }

    /**
     * Sets the origin to top-right corner.
     */
    public void setOriginTopRight() {
        setOrigin(1f, 1f);
    }

    /**
     * Sets the origin to bottom-right corner.
     */
    public void setOriginBottomRight() {
        setOrigin(1f, 0f);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void onDestroy() {
        sprite = null;
    }

    @Override
    public String toString() {
        return String.format("SpriteRenderer[sprite=%s, origin=(%.2f,%.2f), useSpritePivot=%b, zIndex=%d]",
                sprite != null ? sprite.getName() : "null",
                getEffectiveOriginX(), getEffectiveOriginY(),
                useSpritePivot, zIndex);
    }
}