package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.DrivableBy;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.rendering.ui.FillMethod;
import com.pocket.rpg.rendering.ui.FillOrigin;
import lombok.Getter;
import lombok.Setter;

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
@DrivableBy(UIButton.class)
public class UIImage extends UIVisual {

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

    // ========================================================================
    // FIELDS
    // ========================================================================

    @Getter @Setter
    private Sprite sprite;

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

    /** For SIMPLE and FILLED types: fit image within bounds while preserving sprite aspect ratio */
    @Getter @Setter
    private boolean preserveAspectRatio = false;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public UIImage() {
        super(1, 1, 1, 1);  // White default tint
    }

    public UIImage(Sprite sprite) {
        super(1, 1, 1, 1);  // White default tint
        this.sprite = sprite;
    }

    public UIImage(Texture texture) {
        super(1, 1, 1, 1);  // White default tint
        this.sprite = new Sprite(texture);
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
    // DEBUG
    // ========================================================================

    @Override
    public String toString() {
        return String.format("UIImage[sprite=%s, type=%s]",
                sprite != null ? sprite.getName() : "null", imageType);
    }
}
