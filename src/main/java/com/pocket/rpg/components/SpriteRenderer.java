package com.pocket.rpg.components;

import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
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
 * The origin point for positioning and rotation is defined by the sprite's pivot.
 * Edit the pivot in the Sprite Editor (Pivot tab) to change how the sprite is positioned.
 */
@ComponentMeta(category = "Rendering")
public class SpriteRenderer extends Component implements Renderable {

    @Getter
    @Setter
    private Sprite sprite;

    @Getter
    @Setter
    private Vector4f tintColor = new Vector4f(1f, 1f, 1f, 1f);

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
    // ORIGIN ACCESS (from sprite pivot)
    // ========================================================================

    /**
     * Gets the origin X from the sprite's pivot.
     * Returns 0.5 (center) if no sprite is assigned.
     *
     * @return Origin X (0-1)
     */
    public float getEffectiveOriginX() {
        if (sprite != null) {
            return sprite.getPivotX();
        }
        return 0.5f;
    }

    /**
     * Gets the origin Y from the sprite's pivot.
     * Returns 0.5 (center) if no sprite is assigned.
     *
     * @return Origin Y (0-1)
     */
    public float getEffectiveOriginY() {
        if (sprite != null) {
            return sprite.getPivotY();
        }
        return 0.5f;
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
        return String.format("SpriteRenderer[sprite=%s, pivot=(%.2f,%.2f), zIndex=%d]",
                sprite != null ? sprite.getName() : "null",
                getEffectiveOriginX(), getEffectiveOriginY(),
                zIndex);
    }
}
