package com.pocket.rpg.components.ui;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

/**
 * Renders a sprite/texture in screen space with various rendering modes.
 * Requires UICanvas ancestor and UITransform on same GameObject.
 * <p>
 * Supports Unity-style image types:
 * <ul>
 *   <li><b>SIMPLE</b> - Regular sprite rendering</li>
 *   <li><b>SLICED</b> - 9-slice rendering for scalable UI elements</li>
 *   <li><b>TILED</b> - Repeats the sprite to fill the area</li>
 *   <li><b>FILLED</b> - Progress bar / cooldown style partial rendering</li>
 * </ul>
 */
public class UIImage extends UIComponent {

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * How the sprite is rendered.
     */
    public enum ImageType {
        /** Regular sprite rendering */
        SIMPLE,
        /** 9-slice rendering - corners fixed, edges stretch */
        SLICED,
        /** Tiles the sprite to fill the area */
        TILED,
        /** Partial rendering for progress bars and cooldowns */
        FILLED
    }

    /**
     * Fill method for FILLED image type.
     */
    public enum FillMethod {
        /** Fill horizontally */
        HORIZONTAL,
        /** Fill vertically */
        VERTICAL,
        /** Fill radially in a 90-degree arc */
        RADIAL_90,
        /** Fill radially in a 180-degree arc */
        RADIAL_180,
        /** Fill radially in a full 360-degree circle */
        RADIAL_360
    }

    /**
     * Origin point for fill direction.
     */
    public enum FillOrigin {
        // Horizontal origins
        LEFT,
        RIGHT,
        // Vertical origins
        BOTTOM,
        TOP,
        // Radial origins (corner or edge to start from)
        BOTTOM_LEFT,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    // ========================================================================
    // FIELDS
    // ========================================================================

    @Getter @Setter
    private Sprite sprite;

    @Getter
    private final Vector4f color = new Vector4f(1, 1, 1, 1);  // RGBA tint

    /** How the sprite is rendered */
    @Getter @Setter
    private ImageType imageType = ImageType.SIMPLE;

    /** For SLICED type: whether to render the center region */
    @Getter @Setter
    private boolean fillCenter = true;

    /** For FILLED type: the fill method */
    @Getter @Setter
    private FillMethod fillMethod = FillMethod.HORIZONTAL;

    /** For FILLED type: where the fill starts from */
    @Getter @Setter
    private FillOrigin fillOrigin = FillOrigin.LEFT;

    /** For FILLED type: fill amount (0 = empty, 1 = full) */
    @Getter @Setter
    private float fillAmount = 1.0f;

    /** For FILLED type with radial: fill direction */
    @Getter @Setter
    private boolean fillClockwise = true;

    /** For TILED type: pixels per unit for tiling calculation */
    @Getter @Setter
    private float pixelsPerUnit = 100f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public UIImage() {
    }

    public UIImage(Sprite sprite) {
        this.sprite = sprite;
    }

    public UIImage(Texture texture) {
        this.sprite = new Sprite(texture);
    }

    // ========================================================================
    // COLOR METHODS
    // ========================================================================

    public void setColor(float r, float g, float b, float a) {
        color.set(r, g, b, a);
    }

    public void setColor(Vector4f color) {
        this.color.set(color);
    }

    public void setAlpha(float alpha) {
        color.w = alpha;
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Convenience method to set size on UITransform.
     */
    public void setSize(float width, float height) {
        UITransform t = getUITransform();
        if (t != null) {
            t.setSize(width, height);
        }
    }

    /**
     * Returns true if the sprite has 9-slice data configured.
     */
    public boolean hasNineSlice() {
        return sprite != null && sprite.hasNineSlice();
    }

    /**
     * Gets the appropriate fill origin for the current fill method.
     * Returns a valid origin or the default for the method.
     */
    public FillOrigin getEffectiveFillOrigin() {
        return switch (fillMethod) {
            case HORIZONTAL -> (fillOrigin == FillOrigin.LEFT || fillOrigin == FillOrigin.RIGHT)
                    ? fillOrigin : FillOrigin.LEFT;
            case VERTICAL -> (fillOrigin == FillOrigin.BOTTOM || fillOrigin == FillOrigin.TOP)
                    ? fillOrigin : FillOrigin.BOTTOM;
            case RADIAL_90, RADIAL_180, RADIAL_360 ->
                    (fillOrigin == FillOrigin.BOTTOM_LEFT || fillOrigin == FillOrigin.TOP_LEFT ||
                     fillOrigin == FillOrigin.TOP_RIGHT || fillOrigin == FillOrigin.BOTTOM_RIGHT)
                    ? fillOrigin : FillOrigin.BOTTOM_LEFT;
        };
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void render(UIRendererBackend backend) {
        RenderBounds bounds = computeRenderBounds();
        if (bounds == null) return;

        switch (imageType) {
            case SIMPLE -> renderSimple(backend, bounds);
            case SLICED -> renderSliced(backend, bounds);
            case TILED -> renderTiled(backend, bounds);
            case FILLED -> renderFilled(backend, bounds);
        }
    }

    private void renderSimple(UIRendererBackend backend, RenderBounds bounds) {
        backend.drawSprite(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(), sprite, color);
    }

    private void renderSliced(UIRendererBackend backend, RenderBounds bounds) {
        if (sprite == null || !sprite.hasNineSlice()) {
            // Fallback to simple if no 9-slice data
            renderSimple(backend, bounds);
            return;
        }

        backend.drawNineSlice(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                sprite, color, fillCenter);
    }

    private void renderTiled(UIRendererBackend backend, RenderBounds bounds) {
        if (sprite == null) {
            renderSimple(backend, bounds);
            return;
        }

        backend.drawTiled(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                sprite, color, pixelsPerUnit);
    }

    private void renderFilled(UIRendererBackend backend, RenderBounds bounds) {
        if (sprite == null || fillAmount <= 0) {
            return; // Nothing to draw
        }

        // For horizontal, vertical, and radial 360, full fill = full sprite
        // For radial 90/180, full fill only shows that portion of the sprite
        if (fillAmount >= 1.0f && fillMethod != FillMethod.RADIAL_90
                && fillMethod != FillMethod.RADIAL_180) {
            renderSimple(backend, bounds);
            return;
        }

        backend.drawFilled(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.rotation(), bounds.pivotX(), bounds.pivotY(),
                sprite, color, fillMethod, getEffectiveFillOrigin(), fillAmount, fillClockwise);
    }

    // ========================================================================
    // DEBUG
    // ========================================================================

    @Override
    public String toString() {
        return String.format("UIImage[sprite=%s, type=%s]",
                sprite != null ? sprite.getName() : "null", imageType);
    }
}
