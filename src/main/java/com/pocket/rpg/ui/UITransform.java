package com.pocket.rpg.ui;

import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Transform component for UI elements.
 * Uses anchor-based positioning relative to parent bounds.
 * <p>
 * Coordinate system (matches Camera):
 * - Origin (0,0) = TOP-LEFT of screen
 * - Anchor (1,1) = BOTTOM-RIGHT of parent
 * - Positive X offset = move RIGHT
 * - Positive Y offset = move DOWN
 * <p>
 * MANDATORY for all UI components (UIImage, UIPanel, UIText, etc.)
 */
public class UITransform extends Component {

    // Anchor point relative to parent (0-1)
    // (0,0) = top-left, (1,1) = bottom-right
    @Getter
    private final Vector2f anchor = new Vector2f(0, 0);

    // Offset from anchor point in pixels
    // Positive X = right, Positive Y = down
    @Getter
    private final Vector2f offset = new Vector2f(0, 0);

    // Pivot point for this element (0-1 relative to own size)
    // (0,0) = top-left, (0.5,0.5) = center, (1,1) = bottom-right
    @Getter
    private final Vector2f pivot = new Vector2f(0, 0);

    // Size in pixels (ignored if fillParent is true)
    @Setter
    private float width = 100;

    @Setter
    private float height = 100;

    // When true, this element uses parent's size instead of its own
    @Getter
    private boolean fillParent = false;

    // Cached calculated position (top-left corner of element in SCREEN coordinates)
    private final Vector2f calculatedPosition = new Vector2f();
    private boolean positionDirty = true;

    // Parent bounds cache (set by renderer before calculating)
    private float parentX;      // Parent's absolute X position on screen
    private float parentY;      // Parent's absolute Y position on screen
    private float parentWidth;  // Parent's width
    private float parentHeight; // Parent's height

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

    /**
     * Sets offset from anchor point.
     * Positive X = right, Positive Y = down.
     */
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

    /**
     * Sets pivot point (0-1 relative to own size).
     * (0,0) = top-left, (0.5,0.5) = center, (1,1) = bottom-right.
     */
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
        this.fillParent = false;  // Explicit size disables fillParent
        positionDirty = true;
    }

    public void setSize(Vector2f size) {
        setSize(size.x, size.y);
    }

    /**
     * Gets the effective width of this element.
     * Returns parent's width if fillParent is true, otherwise returns own width.
     */
    public float getWidth() {
        return fillParent ? parentWidth : width;
    }

    /**
     * Gets the effective height of this element.
     * Returns parent's height if fillParent is true, otherwise returns own height.
     */
    public float getHeight() {
        return fillParent ? parentHeight : height;
    }

    // ========================================
    // Position Calculation
    // ========================================

    /**
     * Updates parent bounds and position. Called by renderer before getScreenPosition().
     *
     * @param parentX      Parent's absolute X position on screen
     * @param parentY      Parent's absolute Y position on screen
     * @param parentWidth  Parent's width in pixels
     * @param parentHeight Parent's height in pixels
     */
    public void setParentBounds(float parentX, float parentY, float parentWidth, float parentHeight) {
        if (this.parentX != parentX || this.parentY != parentY ||
                this.parentWidth != parentWidth || this.parentHeight != parentHeight) {
            this.parentX = parentX;
            this.parentY = parentY;
            this.parentWidth = parentWidth;
            this.parentHeight = parentHeight;
            positionDirty = true;
        }
    }

    /**
     * Legacy method for backwards compatibility.
     * Sets parent position to (0,0).
     */
    public void setParentBounds(float parentWidth, float parentHeight) {
        setParentBounds(0, 0, parentWidth, parentHeight);
    }

    /**
     * Gets the parent's width. Useful for components that need parent dimensions.
     */
    public float getParentWidth() {
        return parentWidth;
    }

    /**
     * Gets the parent's height. Useful for components that need parent dimensions.
     */
    public float getParentHeight() {
        return parentHeight;
    }

    /**
     * Gets the calculated screen position (top-left corner of this element).
     * This is an ABSOLUTE screen position, not relative to parent.
     * Call setParentBounds() first.
     *
     * @return Screen position in pixels, origin at top-left of screen
     */
    public Vector2f getScreenPosition() {
        if (positionDirty) {
            calculatePosition();
        }
        return new Vector2f(calculatedPosition);
    }

    private void calculatePosition() {
        float effectiveWidth = getWidth();
        float effectiveHeight = getHeight();

        // Anchor position in parent's local space
        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        // Apply offset (positive Y = down)
        float localX = anchorX + offset.x;
        float localY = anchorY + offset.y;

        // Apply pivot (shift so pivot point is at anchor+offset)
        localX -= pivot.x * effectiveWidth;
        localY -= pivot.y * effectiveHeight;

        // Convert to absolute screen position by adding parent's position
        calculatedPosition.set(parentX + localX, parentY + localY);
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

    /**
     * Gets the bounds of this element as [x, y, width, height].
     * Useful for hit testing.
     */
    public float[] getBounds() {
        Vector2f pos = getScreenPosition();
        return new float[]{pos.x, pos.y, getWidth(), getHeight()};
    }

    @Override
    public String toString() {
        if (fillParent) {
            return String.format("UITransform[fillParent, anchor=(%.2f,%.2f), offset=(%.0f,%.0f)]",
                    anchor.x, anchor.y, offset.x, offset.y);
        }
        return String.format("UITransform[anchor=(%.2f,%.2f), offset=(%.0f,%.0f), size=%.0fx%.0f]",
                anchor.x, anchor.y, offset.x, offset.y, width, height);
    }
}