package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.ui.AnchorPreset;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

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
    // Transient - not serialized, recalculated at runtime
    private transient final Vector2f calculatedPosition = new Vector2f();
    private transient boolean positionDirty = true;

    // Screen bounds (set by renderer for root elements)
    // Transient - not serialized, set by UIRenderer at runtime
    private transient float screenWidth;
    private transient float screenHeight;

    // ========================================================================
    // MATRIX-BASED TRANSFORM (unified world position calculation)
    // ========================================================================

    /**
     * UI world transformation matrix.
     * Combines anchor, offset, pivot, rotation, and scale into a single matrix.
     * The matrix origin is at the pivot point for correct rotation behavior.
     */
    private transient final Matrix4f uiWorldMatrix = new Matrix4f();

    /**
     * Cached world pivot position extracted from the matrix.
     * This is the point where the element's pivot is in world coordinates.
     */
    private transient final Vector2f worldPivotPosition = new Vector2f();

    /**
     * Cached world rotation extracted from the matrix.
     */
    private transient float cachedWorldRotation = 0;

    /**
     * Cached world scale extracted from the matrix.
     */
    private transient final Vector2f cachedWorldScale = new Vector2f(1, 1);

    /**
     * Dirty flag for the UI matrix. When true, matrix needs recalculation.
     */
    private transient boolean uiMatrixDirty = true;

    // ========================================================================
    // STRETCH MODE (Match Parent Size)
    // ========================================================================

    /**
     * Stretch mode for matching parent bounds.
     */
    public enum StretchMode {
        /** Use explicit width/height values */
        NONE,
        /** Stretch to fill parent bounds (like Unity's stretch anchor) */
        MATCH_PARENT,
        /** Stretch width to match parent, keep explicit height */
        MATCH_PARENT_WIDTH,
        /** Stretch height to match parent, keep explicit width */
        MATCH_PARENT_HEIGHT
    }

    @Getter
    private StretchMode stretchMode = StretchMode.NONE;

    /**
     * Sets the stretch mode. Resets anchor, pivot, and offset on the matching axis/axes
     * so the element fills the parent appropriately.
     */
    public void setStretchMode(StretchMode stretchMode) {
        this.stretchMode = stretchMode;
        switch (stretchMode) {
            case MATCH_PARENT -> {
                anchor.set(0, 0);
                pivot.set(0, 0);
                localPosition.set(0, 0, localPosition.z);
            }
            case MATCH_PARENT_WIDTH -> {
                anchor.x = 0;
                pivot.x = 0;
                localPosition.x = 0;
            }
            case MATCH_PARENT_HEIGHT -> {
                anchor.y = 0;
                pivot.y = 0;
                localPosition.y = 0;
            }
            case NONE -> { /* no reset */ }
        }
        markDirty();
    }

    // ========================================================================
    // MATCH PARENT ROTATION/SCALE
    // ========================================================================

    /**
     * When true, this element's rotation equals the parent's world rotation.
     * The element will visually rotate with its parent.
     */
    @Getter
    @Setter
    private boolean matchParentRotation = false;

    /**
     * When true, this element's scale equals the parent's world scale.
     * The element will visually scale with its parent.
     */
    @Getter
    @Setter
    private boolean matchParentScale = false;

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
        uiMatrixDirty = true;
    }

    public void setAnchor(Vector2f anchor) {
        this.anchor.set(anchor);
        positionDirty = true;
        uiMatrixDirty = true;
    }

    public void setAnchor(AnchorPreset preset) {
        anchor.set(preset.getX(), preset.getY());
        positionDirty = true;
        uiMatrixDirty = true;
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
        uiMatrixDirty = true;
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
        uiMatrixDirty = true;
    }

    public void setPivot(Vector2f pivot) {
        this.pivot.set(pivot);
        positionDirty = true;
        uiMatrixDirty = true;
    }

    /**
     * Sets pivot to center (0.5, 0.5).
     * Useful for centered elements.
     */
    public void setPivotCenter() {
        setPivot(0.5f, 0.5f);
    }

    /**
     * Gets the effective pivot for rendering.
     * For MATCH_PARENT elements, returns the parent's pivot so rotation
     * happens around the correct point (parent's center, not top-left).
     * Otherwise returns this element's pivot.
     *
     * @return Effective pivot ratio (0-1)
     */
    public Vector2f getEffectivePivot() {
        if (stretchMode == StretchMode.MATCH_PARENT) {
            UITransform parentTransform = getParentUITransform();
            if (parentTransform != null) {
                return parentTransform.getPivot();
            }
        } else if (stretchMode == StretchMode.MATCH_PARENT_WIDTH) {
            UITransform parentTransform = getParentUITransform();
            if (parentTransform != null) {
                return new Vector2f(parentTransform.getPivot().x, pivot.y);
            }
        } else if (stretchMode == StretchMode.MATCH_PARENT_HEIGHT) {
            UITransform parentTransform = getParentUITransform();
            if (parentTransform != null) {
                return new Vector2f(pivot.x, parentTransform.getPivot().y);
            }
        }
        return pivot;
    }

    // ========================================================================
    // SIZE
    // ========================================================================

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        positionDirty = true;
        uiMatrixDirty = true;
    }

    public void setSize(Vector2f size) {
        setSize(size.x, size.y);
    }

    /**
     * Gets the effective width, considering stretch mode.
     * In MATCH_PARENT mode, returns parent width.
     *
     * @return Effective width in pixels
     */
    public float getEffectiveWidth() {
        if (stretchMode == StretchMode.MATCH_PARENT || stretchMode == StretchMode.MATCH_PARENT_WIDTH) {
            return getParentWidth();
        }
        return width;
    }

    /**
     * Gets the effective height, considering stretch mode.
     * In MATCH_PARENT mode, returns parent height.
     *
     * @return Effective height in pixels
     */
    public float getEffectiveHeight() {
        if (stretchMode == StretchMode.MATCH_PARENT || stretchMode == StretchMode.MATCH_PARENT_HEIGHT) {
            return getParentHeight();
        }
        return height;
    }

    /**
     * Checks if this transform is in any match parent mode.
     *
     * @return true if stretchMode is not NONE
     */
    public boolean isMatchingParent() {
        return stretchMode != StretchMode.NONE;
    }

    /**
     * Checks if width is matching parent (MATCH_PARENT or MATCH_PARENT_WIDTH).
     */
    public boolean isMatchingParentWidth() {
        return stretchMode == StretchMode.MATCH_PARENT || stretchMode == StretchMode.MATCH_PARENT_WIDTH;
    }

    /**
     * Checks if height is matching parent (MATCH_PARENT or MATCH_PARENT_HEIGHT).
     */
    public boolean isMatchingParentHeight() {
        return stretchMode == StretchMode.MATCH_PARENT || stretchMode == StretchMode.MATCH_PARENT_HEIGHT;
    }

    // ========================================================================
    // ROTATION (convenience methods for Z rotation)
    // ========================================================================

    /**
     * Gets the 2D rotation angle in degrees.
     * If matchParentRotation is true, returns the parent's world rotation.
     * Otherwise returns the local Z rotation from Transform.
     *
     * @return Rotation angle in degrees (effective value considering matchParentRotation)
     */
    public float getRotation2D() {
        if (matchParentRotation) {
            UITransform parentTransform = getParentUITransform();
            if (parentTransform != null) {
                return parentTransform.getWorldRotation2D();
            }
        }
        return localRotation.z;
    }

    /**
     * Gets the local 2D rotation angle in degrees.
     * Always returns the stored localRotation.z value, ignoring matchParentRotation.
     * Use this for undo/redo operations to capture the actual stored value.
     *
     * @return Local rotation angle in degrees (raw stored value)
     */
    public float getLocalRotation2D() {
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
        markUIMatrixDirty();  // Propagates to children
    }

    // ========================================================================
    // SCALE (convenience methods for 2D scale)
    // ========================================================================

    /**
     * Gets the 2D scale as Vector2f.
     * If matchParentScale is true, returns the parent's world scale.
     * Otherwise returns the local scale from Transform.
     *
     * @return Scale (x, y) (effective value considering matchParentScale)
     */
    public Vector2f getScale2D() {
        if (matchParentScale) {
            UITransform parentTransform = getParentUITransform();
            if (parentTransform != null) {
                return parentTransform.getWorldScale2D();
            }
        }
        return new Vector2f(localScale.x, localScale.y);
    }

    /**
     * Gets the local 2D scale as Vector2f.
     * Always returns the stored localScale value, ignoring matchParentScale.
     * Use this for undo/redo operations to capture the actual stored value.
     *
     * @return Local scale (x, y) (raw stored value)
     */
    public Vector2f getLocalScale2D() {
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
        markUIMatrixDirty();  // Propagates to children
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
    // WORLD ROTATION AND SCALE (inherited from parent hierarchy)
    // ========================================================================

    /**
     * Gets the world rotation (accumulated from all parents).
     * Use this for rendering to include parent rotation.
     *
     * @return Total rotation in degrees (this + all parents)
     */
    public float getWorldRotation2D() {
        UITransform parentTransform = getParentUITransform();
        if (parentTransform != null) {
            float parentWorldRot = parentTransform.getWorldRotation2D();
            if (matchParentRotation) {
                // Match parent: use parent's world rotation (ignore local rotation)
                return parentWorldRot;
            } else {
                // Normal composition: add local rotation to parent's world rotation
                return localRotation.z + parentWorldRot;
            }
        }
        // No parent - use local rotation
        return localRotation.z;
    }

    /**
     * Gets the world scale (multiplied from all parents).
     * Use this for rendering to include parent scale.
     *
     * @return Combined scale (this * all parents)
     */
    public Vector2f getWorldScale2D() {
        UITransform parentTransform = getParentUITransform();
        if (parentTransform != null) {
            Vector2f parentScale = parentTransform.getWorldScale2D();
            if (matchParentScale) {
                // Match parent: use parent's world scale (ignore local scale)
                return new Vector2f(parentScale);
            } else {
                // Normal composition: multiply local scale by parent's world scale
                return new Vector2f(localScale.x * parentScale.x, localScale.y * parentScale.y);
            }
        }
        // No parent - use local scale
        return new Vector2f(localScale.x, localScale.y);
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
            uiMatrixDirty = true;
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

    /**
     * Temporarily overrides the calculated screen position.
     * This is used by the editor to set the position for rendering when
     * the normal hierarchy-based calculation doesn't apply.
     * <p>
     * Note: This directly sets the cached position without marking dirty.
     * The position will be recalculated on the next getScreenPosition() call
     * if any property changes mark the position dirty.
     *
     * @param x Screen X position in pixels
     * @param y Screen Y position in pixels
     */
    public void setCalculatedPosition(float x, float y) {
        calculatedPosition.set(x, y);
        positionDirty = false;
    }

    private void calculatePosition() {
        float[] parentBounds = getParentBounds();
        float parentX = parentBounds[0];
        float parentY = parentBounds[1];
        float parentWidth = parentBounds[2];
        float parentHeight = parentBounds[3];

        // Get parent transform for rotation handling
        UITransform parentTransform = getParentUITransform();

        // In MATCH_PARENT mode, element fills parent entirely
        if (stretchMode == StretchMode.MATCH_PARENT) {
            calculatedPosition.set(parentX, parentY);
            positionDirty = false;
            return;
        }

        // Anchor position in parent's local space
        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        // Apply offset from localPosition (positive Y = down)
        float localX = anchorX + localPosition.x;
        float localY = anchorY + localPosition.y;

        // Apply world scale to dimensions for pivot calculation
        // This ensures pivot offset accounts for scaled visual size
        Vector2f worldScale = getWorldScale2D();
        float scaledWidth = width * worldScale.x;
        float scaledHeight = height * worldScale.y;

        // Apply pivot (shift so pivot point is at anchor+offset)
        localX -= pivot.x * scaledWidth;
        localY -= pivot.y * scaledHeight;

        // Apply parent rotation if present
        // Child position should rotate around parent's pivot point
        if (parentTransform != null) {
            float parentRotation = parentTransform.getWorldRotation2D();
            if (Math.abs(parentRotation) > 0.001f) {
                // Get parent's pivot point in world coordinates
                Vector2f parentPivot = parentTransform.getPivot();
                float parentPivotX = parentX + parentPivot.x * parentWidth;
                float parentPivotY = parentY + parentPivot.y * parentHeight;

                // Calculate cos/sin for rotation
                float cos = (float) Math.cos(Math.toRadians(parentRotation));
                float sin = (float) Math.sin(Math.toRadians(parentRotation));

                // Position relative to parent's pivot (not parent's top-left)
                float relX = parentX + localX - parentPivotX;
                float relY = parentY + localY - parentPivotY;

                // Rotate around parent pivot
                float rotatedX = relX * cos - relY * sin;
                float rotatedY = relX * sin + relY * cos;

                // Final position
                calculatedPosition.set(parentPivotX + rotatedX, parentPivotY + rotatedY);
                positionDirty = false;
                return;
            }
        }

        // No parent rotation - simple addition
        calculatedPosition.set(parentX + localX, parentY + localY);
        positionDirty = false;
    }

    /**
     * Marks position and matrix as needing recalculation.
     * Call when parent bounds change or when this element's properties change.
     */
    public void markDirty() {
        positionDirty = true;
        uiMatrixDirty = true;
    }

    /**
     * Recursively marks this transform and all child UITransforms as dirty.
     * Call when screen bounds change or when parent position changes.
     */
    public void markDirtyRecursive() {
        positionDirty = true;
        uiMatrixDirty = true;

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
     * Gets the scaled bounds of this element as [x, y, scaledWidth, scaledHeight].
     * Applies world scale to dimensions. Useful for renderers.
     */
    public float[] getScaledBounds() {
        Vector2f pos = getScreenPosition();
        Vector2f scale = getWorldScale2D();
        return new float[]{pos.x, pos.y, width * scale.x, height * scale.y};
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
    // MATRIX-BASED WORLD TRANSFORM (Single source of truth)
    // ========================================================================

    /**
     * Gets the UI world transformation matrix.
     * This matrix combines anchor, offset, pivot, rotation, and scale,
     * properly composed with parent transformations.
     * <p>
     * The matrix origin is at the pivot point, so:
     * - Translation component = pivot position in world space
     * - Rotation component = total rotation (this + all parents)
     * - Scale component = total scale (this * all parents)
     *
     * @return The world transformation matrix (do not modify)
     */
    public Matrix4f getUIWorldMatrix() {
        if (uiMatrixDirty) {
            buildUIWorldMatrix();
        }
        return uiWorldMatrix;
    }

    /**
     * Gets the pivot position in world coordinates.
     * This is the point where the element rotates around.
     * <p>
     * For rendering, use: position = pivotPosition - pivot * scaledSize
     *
     * @return Pivot position in world coordinates
     */
    public Vector2f getWorldPivotPosition2D() {
        if (uiMatrixDirty) {
            buildUIWorldMatrix();
        }
        return new Vector2f(worldPivotPosition);
    }

    /**
     * Gets the world rotation computed via matrix composition.
     * This properly accumulates all parent rotations.
     *
     * @return World rotation in degrees
     */
    public float getComputedWorldRotation2D() {
        if (uiMatrixDirty) {
            buildUIWorldMatrix();
        }
        return cachedWorldRotation;
    }

    /**
     * Gets the world scale computed via matrix composition.
     * This properly multiplies all parent scales.
     *
     * @return World scale (x, y)
     */
    public Vector2f getComputedWorldScale2D() {
        if (uiMatrixDirty) {
            buildUIWorldMatrix();
        }
        return new Vector2f(cachedWorldScale);
    }

    /**
     * Builds the UI world transformation matrix.
     * <p>
     * Algorithm:
     * 1. Get parent's world matrix (or identity if no parent)
     * 2. Calculate this element's PIVOT position in parent's local space:
     *    - pivotLocalX = anchor.x * parentWidth + offset.x
     *    - pivotLocalY = anchor.y * parentHeight + offset.y
     * 3. Transform this local pivot position by parent's world matrix
     * 4. Build this element's world matrix at the resulting position
     * <p>
     * This ensures children automatically inherit parent transforms through
     * matrix multiplication, handling arbitrary nesting depth uniformly.
     */
    private void buildUIWorldMatrix() {
        // Get parent bounds for anchor calculation
        float parentWidth = screenWidth;
        float parentHeight = screenHeight;
        float parentX = 0;
        float parentY = 0;

        UITransform parentTransform = getParentUITransform();

        // In MATCH_PARENT mode, child fills parent completely and rotates with it
        if (stretchMode == StretchMode.MATCH_PARENT) {
            if (parentTransform != null) {
                // Ensure parent has screen bounds set (propagate up the chain)
                parentTransform.setScreenBounds(screenWidth, screenHeight);

                // Use parent's pivot position - child will use same pivot ratio via getEffectivePivot()
                Vector2f parentPivotWorld = parentTransform.getWorldPivotPosition2D();
                parentX = parentPivotWorld.x;
                parentY = parentPivotWorld.y;
                parentWidth = parentTransform.getEffectiveWidth();
                parentHeight = parentTransform.getEffectiveHeight();
            }

            worldPivotPosition.set(parentX, parentY);
            cachedWorldRotation = parentTransform != null ? parentTransform.getComputedWorldRotation2D() : 0;
            cachedWorldScale.set(parentTransform != null ? parentTransform.getComputedWorldScale2D() : new Vector2f(1, 1));

            // Build identity-ish matrix at parent position
            uiWorldMatrix.identity();
            uiWorldMatrix.translate(parentX, parentY, 0);
            if (Math.abs(cachedWorldRotation) > 0.001f) {
                uiWorldMatrix.rotateZ((float) Math.toRadians(cachedWorldRotation));
            }
            uiWorldMatrix.scale(cachedWorldScale.x, cachedWorldScale.y, 1);

            uiMatrixDirty = false;
            return;
        }

        // Get this element's RAW local rotation and scale
        // (don't use getRotation2D()/getScale2D() which return parent values when match modes are enabled)
        float localRot = localRotation.z;
        Vector2f localScaleVec = new Vector2f(localScale.x, localScale.y);

        // Transform pivot position by parent's world matrix
        float pivotWorldX, pivotWorldY;
        float worldRot, worldScaleX, worldScaleY;

        if (parentTransform != null) {
            // Ensure parent has screen bounds set (propagate up the chain)
            parentTransform.setScreenBounds(screenWidth, screenHeight);

            // Get parent's world pivot and transform info
            Vector2f parentWorldPivot = parentTransform.getWorldPivotPosition2D();
            Vector2f parentScale = parentTransform.getComputedWorldScale2D();
            Vector2f parentPivotRatio = parentTransform.getPivot();
            float parentWorldRot = parentTransform.getComputedWorldRotation2D();

            // Use parent's effective dimensions for anchor calculation (not scaled)
            parentWidth = parentTransform.getEffectiveWidth();
            parentHeight = parentTransform.getEffectiveHeight();

            // Calculate this element's position in parent's LOCAL space (from parent's top-left)
            float childLocalX = anchor.x * parentWidth + localPosition.x;
            float childLocalY = anchor.y * parentHeight + localPosition.y;

            // Calculate parent's pivot position in parent's LOCAL space (from parent's top-left)
            float parentPivotLocalX = parentPivotRatio.x * parentWidth;
            float parentPivotLocalY = parentPivotRatio.y * parentHeight;

            // Calculate child's position RELATIVE to parent's pivot (this is what rotates)
            float relX = childLocalX - parentPivotLocalX;
            float relY = childLocalY - parentPivotLocalY;

            // Scale the relative position by parent's scale
            float scaledRelX = relX * parentScale.x;
            float scaledRelY = relY * parentScale.y;

            // Rotate the relative position by parent's WORLD rotation
            // (child offset must be rotated to match parent's rotated coordinate system)
            if (Math.abs(parentWorldRot) > 0.001f) {
                // Negate rotation for Y-down screen coordinates
                float cos = (float) Math.cos(Math.toRadians(-parentWorldRot));
                float sin = (float) Math.sin(Math.toRadians(-parentWorldRot));

                float rotatedRelX = scaledRelX * cos - scaledRelY * sin;
                float rotatedRelY = scaledRelX * sin + scaledRelY * cos;

                // Add to parent's WORLD pivot position
                pivotWorldX = parentWorldPivot.x + rotatedRelX;
                pivotWorldY = parentWorldPivot.y + rotatedRelY;
            } else {
                // No parent rotation - just add scaled offset to parent's world pivot
                pivotWorldX = parentWorldPivot.x + scaledRelX;
                pivotWorldY = parentWorldPivot.y + scaledRelY;
            }

            // Compose rotation and scale, handling match modes
            if (matchParentRotation) {
                // Match parent: use parent's world rotation (ignore local rotation)
                worldRot = parentWorldRot;
            } else {
                // Normal composition: add local rotation to parent's world rotation
                worldRot = localRot + parentWorldRot;
            }

            if (matchParentScale) {
                // Match parent: use parent's world scale (ignore local scale)
                worldScaleX = parentScale.x;
                worldScaleY = parentScale.y;
            } else {
                // Normal composition: multiply local scale by parent's world scale
                worldScaleX = localScaleVec.x * parentScale.x;
                worldScaleY = localScaleVec.y * parentScale.y;
            }
        } else {
            // No parent - local space equals world space
            // parentWidth/parentHeight are already set to screenWidth/screenHeight
            float pivotLocalX = anchor.x * parentWidth + localPosition.x;
            float pivotLocalY = anchor.y * parentHeight + localPosition.y;

            pivotWorldX = pivotLocalX;
            pivotWorldY = pivotLocalY;
            worldRot = localRot;
            worldScaleX = localScaleVec.x;
            worldScaleY = localScaleVec.y;
        }

        // Store cached values
        worldPivotPosition.set(pivotWorldX, pivotWorldY);
        cachedWorldRotation = worldRot;
        cachedWorldScale.set(worldScaleX, worldScaleY);

        // Build the world matrix with origin at pivot
        uiWorldMatrix.identity();
        uiWorldMatrix.translate(pivotWorldX, pivotWorldY, 0);
        if (Math.abs(worldRot) > 0.001f) {
            uiWorldMatrix.rotateZ((float) Math.toRadians(worldRot));
        }
        uiWorldMatrix.scale(worldScaleX, worldScaleY, 1);

        uiMatrixDirty = false;
    }

    /**
     * Marks the UI matrix as dirty, requiring recalculation.
     * Also propagates to all children.
     */
    public void markUIMatrixDirty() {
        uiMatrixDirty = true;
        markChildrenUIMatrixDirty();
    }

    /**
     * Marks all children's UI matrices as dirty.
     */
    private void markChildrenUIMatrixDirty() {
        if (gameObject == null) return;

        for (GameObject child : gameObject.getChildren()) {
            UITransform childTransform = child.getComponent(UITransform.class);
            if (childTransform != null) {
                childTransform.markUIMatrixDirty();
            }
        }
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
