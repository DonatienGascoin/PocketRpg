package com.pocket.rpg.ui;

import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Transform component for UI elements.
 * Uses anchor-based positioning relative to parent bounds.
 *
 * Coordinate system:
 * - Anchor (0,0) = bottom-left of parent
 * - Anchor (1,1) = top-right of parent
 * - Offset is in pixels from anchor point
 *
 * MANDATORY for all UI components (UIImage, UIPanel, etc.)
 */
public class UITransform extends Component {

    // Anchor point relative to parent (0-1)
    @Getter
    private final Vector2f anchor = new Vector2f(0, 0);

    // Offset from anchor point in pixels
    @Getter
    private final Vector2f offset = new Vector2f(0, 0);

    // Pivot point for this element (0-1 relative to own size)
    // (0,0) = bottom-left, (0.5,0.5) = center, (1,1) = top-right
    @Getter
    private final Vector2f pivot = new Vector2f(0, 0);

    // Size in pixels
    @Getter @Setter
    private float width = 100;

    @Getter @Setter
    private float height = 100;

    // Cached calculated position
    private final Vector2f calculatedPosition = new Vector2f();
    private boolean positionDirty = true;

    // Parent bounds cache (set by renderer before calculating)
    private float parentWidth;
    private float parentHeight;

    public UITransform() {
    }

    public UITransform(float width, float height) {
        this.width = width;
        this.height = height;
    }

    // ========================================
    // Anchor
    // ========================================

    public void setAnchor(float x, float y) {
        anchor.set(x, y);
        positionDirty = true;
    }

    public void setAnchor(Vector2f anchor) {
        this.anchor.set(anchor);
        positionDirty = true;
    }

    public void setAnchor(AnchorPreset preset) {
        anchor.set(preset.getX(), preset.getY());
        positionDirty = true;
    }

    // ========================================
    // Offset
    // ========================================

    public void setOffset(float x, float y) {
        offset.set(x, y);
        positionDirty = true;
    }

    public void setOffset(Vector2f offset) {
        this.offset.set(offset);
        positionDirty = true;
    }

    // ========================================
    // Pivot
    // ========================================

    public void setPivot(float x, float y) {
        pivot.set(x, y);
        positionDirty = true;
    }

    public void setPivot(Vector2f pivot) {
        this.pivot.set(pivot);
        positionDirty = true;
    }

    /**
     * Sets pivot to center (0.5, 0.5).
     * Useful for centered elements.
     */
    public void setPivotCenter() {
        setPivot(0.5f, 0.5f);
    }

    // ========================================
    // Size
    // ========================================

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        positionDirty = true;
    }

    public void setSize(Vector2f size) {
        this.width = size.x;
        this.height = size.y;
        positionDirty = true;
    }

    // ========================================
    // Position Calculation
    // ========================================

    /**
     * Updates parent bounds. Called by renderer before getScreenPosition().
     */
    public void setParentBounds(float parentWidth, float parentHeight) {
        if (this.parentWidth != parentWidth || this.parentHeight != parentHeight) {
            this.parentWidth = parentWidth;
            this.parentHeight = parentHeight;
            positionDirty = true;
        }
    }

    /**
     * Gets the calculated screen position (bottom-left corner of this element).
     * Call setParentBounds() first.
     *
     * @return Screen position in pixels, origin at bottom-left
     */
    public Vector2f getScreenPosition() {
        if (positionDirty) {
            calculatePosition();
        }
        return calculatedPosition;
    }

    private void calculatePosition() {
        // Anchor position in parent space
        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        // Apply offset
        float posX = anchorX + offset.x;
        float posY = anchorY + offset.y;

        // Apply pivot (shift so pivot point is at anchor+offset)
        posX -= pivot.x * width;
        posY -= pivot.y * height;

        calculatedPosition.set(posX, posY);
        positionDirty = false;
    }

    /**
     * Marks position as needing recalculation.
     */
    public void markDirty() {
        positionDirty = true;
    }

    // ========================================
    // Convenience Methods
    // ========================================

    /**
     * Sets anchor and offset in one call.
     */
    public void setAnchorAndOffset(AnchorPreset preset, float offsetX, float offsetY) {
        setAnchor(preset);
        setOffset(offsetX, offsetY);
    }

    /**
     * Sets anchor, offset, and size in one call.
     */
    public void set(AnchorPreset preset, float offsetX, float offsetY, float width, float height) {
        setAnchor(preset);
        setOffset(offsetX, offsetY);
        setSize(width, height);
    }

    @Override
    public String toString() {
        return String.format("UITransform[anchor=(%.2f,%.2f), offset=(%.0f,%.0f), size=%.0fx%.0f]",
                anchor.x, anchor.y, offset.x, offset.y, width, height);
    }
}