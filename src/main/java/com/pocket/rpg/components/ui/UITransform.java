package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.ui.AnchorPreset;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Transform component for UI elements.
 * Extends Transform to inherit rotation and scale, while adding anchor-based positioning.
 *
 * <h2>Coordinate System</h2>
 * <ul>
 *   <li>Origin (0,0) = TOP-LEFT of screen</li>
 *   <li>Anchor (1,1) = BOTTOM-RIGHT of parent</li>
 *   <li>Positive X offset = move RIGHT</li>
 *   <li>Positive Y offset = move DOWN</li>
 * </ul>
 *
 * <h2>Offset vs LocalPosition</h2>
 * UITransform uses localPosition.x/y as the offset from the anchor point.
 * The {@link #getOffset()} and {@link #setOffset(float, float)} methods are
 * convenience wrappers around localPosition for backward compatibility.
 *
 * <h2>Rotation and Scale</h2>
 * UITransform inherits rotation and scale from Transform:
 * <ul>
 *   <li>localRotation.z - 2D rotation around pivot point</li>
 *   <li>localScale.x/y - Visual scaling of the element</li>
 * </ul>
 *
 * <h2>Hierarchy Support</h2>
 * UITransform automatically inherits bounds from parent UITransform:
 * <ul>
 *   <li>If parent has UITransform: uses parent's screen position and size as bounds</li>
 *   <li>If no parent UITransform: uses screen bounds (set by renderer)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * MANDATORY for all UI components (UIImage, UIPanel, UIText, UIButton, etc.)
 */
public class UITransform extends Transform {

    // Anchor point relative to parent (0-1)
    // (0,0) = top-left, (1,1) = bottom-right
    @Getter
    private final Vector2f anchor = new Vector2f(0, 0);

    // Pivot point for this element (0-1 relative to own size)
    // (0,0) = top-left, (0.5,0.5) = center, (1,1) = bottom-right
    @Getter
    private final Vector2f pivot = new Vector2f(0, 0);

    // Size in pixels
    @Getter
    @Setter
    private float width = 100;

    @Getter
    @Setter
    private float height = 100;

    // Cached calculated position (top-left corner of element in SCREEN coordinates)
    private final Vector2f calculatedPosition = new Vector2f();
    private boolean positionDirty = true;

    // Screen bounds (set by renderer for root elements)
    private float screenWidth;
    private float screenHeight;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public UITransform() {
        super();
    }

    public UITransform(float width, float height) {
        super();
        this.width = width;
        this.height = height;
    }

    // ========================================================================
    // ANCHOR
    // ========================================================================

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

    // ========================================================================
    // OFFSET (uses localPosition.x/y from Transform)
    // ========================================================================

    /**
     * Gets offset from anchor point.
     * This is a convenience wrapper around localPosition.x/y.
     *
     * @return Offset as Vector2f (new instance)
     */
    public Vector2f getOffset() {
        return new Vector2f(localPosition.x, localPosition.y);
    }

    /**
     * Sets offset from anchor point.
     * Positive X = right, Positive Y = down.
     * This sets localPosition.x/y.
     *
     * @param x X offset in pixels
     * @param y Y offset in pixels
     */
    public void setOffset(float x, float y) {
        if (localPosition.x == x && localPosition.y == y) {
            return;
        }
        localPosition.set(x, y, localPosition.z);
        positionDirty = true;
        markDirtyAndNotify();
    }

    /**
     * Sets offset from anchor point.
     * This sets localPosition.x/y.
     *
     * @param offset Offset vector
     */
    public void setOffset(Vector2f offset) {
        setOffset(offset.x, offset.y);
    }

    // ========================================================================
    // PIVOT
    // ========================================================================

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

    // ========================================================================
    // SIZE
    // ========================================================================

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        positionDirty = true;
    }

    public void setSize(Vector2f size) {
        setSize(size.x, size.y);
    }

    // ========================================================================
    // ROTATION (convenience methods for Z rotation)
    // ========================================================================

    /**
     * Gets the 2D rotation angle in degrees.
     * This is the Z rotation from Transform.
     *
     * @return Rotation angle in degrees
     */
    public float getRotation2D() {
        return localRotation.z;
    }

    /**
     * Sets the 2D rotation angle in degrees.
     * This sets the Z rotation in Transform.
     *
     * @param degrees Rotation angle in degrees
     */
    public void setRotation2D(float degrees) {
        setLocalRotation(0, 0, degrees);
        positionDirty = true;
    }

    // ========================================================================
    // SCALE (convenience methods for 2D scale)
    // ========================================================================

    /**
     * Gets the 2D scale as Vector2f.
     *
     * @return Scale (x, y)
     */
    public Vector2f getScale2D() {
        return new Vector2f(localScale.x, localScale.y);
    }

    /**
     * Sets the 2D scale.
     *
     * @param x X scale
     * @param y Y scale
     */
    public void setScale2D(float x, float y) {
        setLocalScale(x, y, 1);
        positionDirty = true;
    }

    /**
     * Sets uniform 2D scale.
     *
     * @param scale Uniform scale value
     */
    public void setScale2D(float scale) {
        setScale2D(scale, scale);
    }

    // ========================================================================
    // SCREEN BOUNDS (set by renderer for root elements)
    // ========================================================================

    /**
     * Sets the screen bounds. Called by UIRenderer for root-level elements.
     * Elements with a parent UITransform will ignore this and use parent bounds instead.
     *
     * @param screenWidth  Screen width in pixels
     * @param screenHeight Screen height in pixels
     */
    public void setScreenBounds(float screenWidth, float screenHeight) {
        if (this.screenWidth != screenWidth || this.screenHeight != screenHeight) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            positionDirty = true;
        }
    }

    // ========================================================================
    // PARENT BOUNDS (from hierarchy)
    // ========================================================================

    /**
     * Gets the parent UITransform, if any.
     *
     * @return Parent UITransform or null if no parent with UITransform
     */
    private UITransform getParentUITransform() {
        if (gameObject == null) {
            return null;
        }

        GameObject parent = gameObject.getParent();
        if (parent == null) {
            return null;
        }

        return parent.getComponent(UITransform.class);
    }

    /**
     * Gets the effective parent bounds for position calculation.
     * Uses parent UITransform if available, otherwise screen bounds.
     *
     * @return float[4]: {parentX, parentY, parentWidth, parentHeight}
     */
    private float[] getParentBounds() {
        UITransform parentTransform = getParentUITransform();

        if (parentTransform != null) {
            // Use parent UITransform's position and size
            Vector2f parentPos = parentTransform.getScreenPosition();
            return new float[]{
                    parentPos.x,
                    parentPos.y,
                    parentTransform.getWidth(),
                    parentTransform.getHeight()
            };
        } else {
            // Use screen bounds
            return new float[]{0, 0, screenWidth, screenHeight};
        }
    }

    /**
     * Gets the parent's width for layout calculations.
     *
     * @return Parent width in pixels
     */
    public float getParentWidth() {
        UITransform parentTransform = getParentUITransform();
        return parentTransform != null ? parentTransform.getWidth() : screenWidth;
    }

    /**
     * Gets the parent's height for layout calculations.
     *
     * @return Parent height in pixels
     */
    public float getParentHeight() {
        UITransform parentTransform = getParentUITransform();
        return parentTransform != null ? parentTransform.getHeight() : screenHeight;
    }

    // ========================================================================
    // POSITION CALCULATION
    // ========================================================================

    /**
     * Gets the calculated screen position (top-left corner of this element).
     * This is an ABSOLUTE screen position, not relative to parent.
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
        float[] parentBounds = getParentBounds();
        float parentX = parentBounds[0];
        float parentY = parentBounds[1];
        float parentWidth = parentBounds[2];
        float parentHeight = parentBounds[3];

        // Anchor position in parent's local space
        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        // Apply offset from localPosition (positive Y = down)
        float localX = anchorX + localPosition.x;
        float localY = anchorY + localPosition.y;

        // Apply pivot (shift so pivot point is at anchor+offset)
        localX -= pivot.x * width;
        localY -= pivot.y * height;

        // Convert to absolute screen position by adding parent's position
        calculatedPosition.set(parentX + localX, parentY + localY);
        positionDirty = false;
    }

    /**
     * Marks position as needing recalculation.
     * Call when parent bounds change or when this element's properties change.
     */
    public void markDirty() {
        positionDirty = true;
    }

    /**
     * Recursively marks this transform and all child UITransforms as dirty.
     * Call when screen bounds change or when parent position changes.
     */
    public void markDirtyRecursive() {
        positionDirty = true;

        if (gameObject == null) {
            return;
        }

        for (GameObject child : gameObject.getChildren()) {
            UITransform childTransform = child.getComponent(UITransform.class);
            if (childTransform != null) {
                childTransform.markDirtyRecursive();
            }
        }
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

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
        return new float[]{pos.x, pos.y, width, height};
    }

    /**
     * Checks if a point (in screen coordinates) is inside this element.
     *
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     * @return true if point is inside bounds
     */
    public boolean containsPoint(float x, float y) {
        Vector2f pos = getScreenPosition();
        return x >= pos.x && x <= pos.x + width &&
                y >= pos.y && y <= pos.y + height;
    }

    // ========================================================================
    // LEGACY SUPPORT
    // ========================================================================

    /**
     * @deprecated Use {@link #setScreenBounds(float, float)} instead.
     * This method is kept for backwards compatibility.
     */
    @Deprecated
    public void setParentBounds(float parentWidth, float parentHeight) {
        setScreenBounds(parentWidth, parentHeight);
    }

    /**
     * @deprecated Parent bounds are now automatically derived from parent UITransform.
     * Use {@link #setScreenBounds(float, float)} for root elements only.
     */
    @Deprecated
    public void setParentBounds(float parentX, float parentY, float parentWidth, float parentHeight) {
        // Only use screen bounds - parent position comes from hierarchy
        setScreenBounds(parentWidth, parentHeight);
    }

    @Override
    public String toString() {
        return String.format("UITransform[anchor=(%.2f,%.2f), offset=(%.0f,%.0f), size=%.0fx%.0f, pivot=(%.2f,%.2f), rot=%.1f, scale=(%.2f,%.2f)]",
                anchor.x, anchor.y, localPosition.x, localPosition.y, width, height, pivot.x, pivot.y,
                localRotation.z, localScale.x, localScale.y);
    }
}
