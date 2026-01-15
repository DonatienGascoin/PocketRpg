package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector2f;

/**
 * Coordinate conversion and bounds calculation for UI Designer.
 * Uses direct typed component access (no reflection).
 */
public class UIDesignerCoordinates {

    private final UIDesignerState state;

    public UIDesignerCoordinates(UIDesignerState state) {
        this.state = state;
    }

    // ========================================================================
    // COORDINATE CONVERSION
    // ========================================================================

    /**
     * Converts canvas coordinates to screen (viewport) coordinates.
     */
    public Vector2f canvasToScreen(float canvasX, float canvasY) {
        float centerX = state.getViewportWidth() / 2;
        float centerY = state.getViewportHeight() / 2;

        float screenX = centerX + (canvasX - state.getCameraX()) * state.getZoom();
        float screenY = centerY + (canvasY - state.getCameraY()) * state.getZoom();

        return new Vector2f(screenX, screenY);
    }

    /**
     * Converts screen (viewport) coordinates to canvas coordinates.
     */
    public Vector2f screenToCanvas(float screenX, float screenY) {
        float centerX = state.getViewportWidth() / 2;
        float centerY = state.getViewportHeight() / 2;

        float canvasX = (screenX - centerX) / state.getZoom() + state.getCameraX();
        float canvasY = (screenY - centerY) / state.getZoom() + state.getCameraY();

        return new Vector2f(canvasX, canvasY);
    }

    /**
     * Converts a size in canvas units to screen units.
     */
    public float canvasSizeToScreen(float size) {
        return size * state.getZoom();
    }

    /**
     * Converts a size in screen units to canvas units.
     */
    public float screenSizeToCanvas(float size) {
        return size / state.getZoom();
    }

    // ========================================================================
    // BOUNDS CALCULATION (delegates to UITransform matrix-based calculation)
    // ========================================================================

    /**
     * Calculates the bounds of a UI element in canvas coordinates.
     * Uses UITransform's matrix-based calculation for consistency with runtime rendering.
     * <p>
     * The matrix-based approach handles hierarchy correctly:
     * - Children automatically inherit parent transforms
     * - Rotation is applied via matrix composition, not manual calculation
     * - Arbitrary nesting depth is supported uniformly
     *
     * @param entity The entity to calculate bounds for
     * @return float[4]: {x, y, scaledWidth, scaledHeight} or null if no UITransform
     */
    public float[] calculateElementBounds(EditorGameObject entity) {
        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return null;

        // Set canvas bounds on transform (needed for MATCH_PARENT and anchor calculations)
        transform.setScreenBounds(state.getCanvasWidth(), state.getCanvasHeight());

        // Use effective width/height (handles MATCH_PARENT mode)
        float width = transform.getEffectiveWidth();
        float height = transform.getEffectiveHeight();

        // Get world scale from matrix (properly accumulates parent scale)
        Vector2f worldScale = transform.getComputedWorldScale2D();
        float scaledWidth = width * worldScale.x;
        float scaledHeight = height * worldScale.y;

        // Get world pivot position from matrix (properly transforms through hierarchy)
        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f pivot = transform.getEffectivePivot();  // Use effective pivot for MATCH_PARENT

        // Calculate top-left from pivot position
        float x = pivotWorld.x - pivot.x * scaledWidth;
        float y = pivotWorld.y - pivot.y * scaledHeight;

        return new float[]{x, y, scaledWidth, scaledHeight};
    }

    /**
     * Finds the nearest ancestor with a UITransform (stops at UICanvas).
     */
    public EditorGameObject findParentWithUITransform(EditorGameObject entity) {
        EditorGameObject parent = entity.getParent();
        while (parent != null) {
            if (parent.hasComponent(UITransform.class) || parent.hasComponent(UICanvas.class)) {
                if (parent.hasComponent(UITransform.class)) {
                    return parent;
                }
                return null; // Reached UICanvas without UITransform
            }
            parent = parent.getParent();
        }
        return null;
    }

    // ========================================================================
    // ENTITY TYPE CHECKS
    // ========================================================================

    /**
     * Checks if the entity is a UI element.
     */
    public boolean isUIEntity(EditorGameObject entity) {
        return entity.hasComponent(UICanvas.class) ||
                entity.hasComponent(UITransform.class) ||
                entity.hasComponent(UIPanel.class) ||
                entity.hasComponent(UIImage.class) ||
                entity.hasComponent(UIButton.class) ||
                entity.hasComponent(UIText.class);
    }

    /**
     * Checks if the entity has visual content (not just UICanvas or UITransform).
     */
    public boolean hasVisualContent(EditorGameObject entity) {
        return entity.hasComponent(UIPanel.class) ||
                entity.hasComponent(UIImage.class) ||
                entity.hasComponent(UIButton.class) ||
                entity.hasComponent(UIText.class);
    }

    // ========================================================================
    // HIT TESTING
    // ========================================================================

    /**
     * Checks if a canvas point is inside the element bounds.
     */
    public boolean isPointInElement(EditorGameObject entity, float canvasX, float canvasY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return false;

        return canvasX >= bounds[0] && canvasX <= bounds[0] + bounds[2] &&
                canvasY >= bounds[1] && canvasY <= bounds[1] + bounds[3];
    }

    /**
     * Checks if a screen point is inside the element bounds.
     */
    public boolean isScreenPointInElement(EditorGameObject entity, float screenX, float screenY) {
        Vector2f canvas = screenToCanvas(screenX, screenY);
        return isPointInElement(entity, canvas.x, canvas.y);
    }

    /**
     * Gets the resize handle at the given screen position, if any.
     * Supports rotated elements by using rotated corner positions.
     *
     * @param entity  The entity to check handles for
     * @param screenX Screen X position
     * @param screenY Screen Y position
     * @return The ResizeHandle at position, or null if none
     */
    public UIDesignerState.ResizeHandle getHandleAtPosition(EditorGameObject entity, float screenX, float screenY) {
        float[] corners = calculateRotatedScreenCorners(entity);
        if (corners == null) return null;

        float hitSize = UIDesignerState.HANDLE_HIT_SIZE;

        // Corner handles (TL, TR, BR, BL)
        if (isInHandle(screenX, screenY, corners[0], corners[1], hitSize)) return UIDesignerState.ResizeHandle.TOP_LEFT;
        if (isInHandle(screenX, screenY, corners[2], corners[3], hitSize)) return UIDesignerState.ResizeHandle.TOP_RIGHT;
        if (isInHandle(screenX, screenY, corners[4], corners[5], hitSize)) return UIDesignerState.ResizeHandle.BOTTOM_RIGHT;
        if (isInHandle(screenX, screenY, corners[6], corners[7], hitSize)) return UIDesignerState.ResizeHandle.BOTTOM_LEFT;

        // Edge midpoint handles
        float topMidX = (corners[0] + corners[2]) / 2, topMidY = (corners[1] + corners[3]) / 2;
        float rightMidX = (corners[2] + corners[4]) / 2, rightMidY = (corners[3] + corners[5]) / 2;
        float bottomMidX = (corners[4] + corners[6]) / 2, bottomMidY = (corners[5] + corners[7]) / 2;
        float leftMidX = (corners[6] + corners[0]) / 2, leftMidY = (corners[7] + corners[1]) / 2;

        if (isInHandle(screenX, screenY, topMidX, topMidY, hitSize)) return UIDesignerState.ResizeHandle.TOP;
        if (isInHandle(screenX, screenY, rightMidX, rightMidY, hitSize)) return UIDesignerState.ResizeHandle.RIGHT;
        if (isInHandle(screenX, screenY, bottomMidX, bottomMidY, hitSize)) return UIDesignerState.ResizeHandle.BOTTOM;
        if (isInHandle(screenX, screenY, leftMidX, leftMidY, hitSize)) return UIDesignerState.ResizeHandle.LEFT;

        return null;
    }

    /**
     * Calculates the rotated corners of an element in screen coordinates.
     * Returns [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y] or null if invalid.
     */
    private float[] calculateRotatedScreenCorners(EditorGameObject entity) {
        UITransform transform = entity.getComponent(UITransform.class);
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return null;

        // Negate rotation to convert to screen space (Y-down)
        // Use computed world rotation from matrix for correct hierarchy handling
        float rotation = transform != null ? -transform.getComputedWorldRotation2D() : 0;

        float x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];

        // Convert bounds to screen space
        Vector2f screenTL = canvasToScreen(x, y);
        Vector2f screenBR = canvasToScreen(x + w, y + h);

        float left = screenTL.x;
        float top = screenTL.y;
        float right = screenBR.x;
        float bottom = screenBR.y;

        // If no rotation, return axis-aligned corners
        if (Math.abs(rotation) < 0.001f) {
            return new float[]{left, top, right, top, right, bottom, left, bottom};
        }

        // Calculate pivot in screen space (use effective pivot for MATCH_PARENT)
        Vector2f pivot = transform != null ? transform.getEffectivePivot() : new Vector2f(0, 0);
        float screenWidth = right - left;
        float screenHeight = bottom - top;
        float pivotScreenX = left + pivot.x * screenWidth;
        float pivotScreenY = top + pivot.y * screenHeight;

        // Calculate rotation
        float cos = (float) Math.cos(Math.toRadians(rotation));
        float sin = (float) Math.sin(Math.toRadians(rotation));

        float[] corners = new float[8];
        rotatePoint(left - pivotScreenX, top - pivotScreenY, cos, sin, pivotScreenX, pivotScreenY, corners, 0);    // TL
        rotatePoint(right - pivotScreenX, top - pivotScreenY, cos, sin, pivotScreenX, pivotScreenY, corners, 2);   // TR
        rotatePoint(right - pivotScreenX, bottom - pivotScreenY, cos, sin, pivotScreenX, pivotScreenY, corners, 4); // BR
        rotatePoint(left - pivotScreenX, bottom - pivotScreenY, cos, sin, pivotScreenX, pivotScreenY, corners, 6);  // BL

        return corners;
    }

    /**
     * Rotates a point around a center and stores result in output array.
     */
    private void rotatePoint(float dx, float dy, float cos, float sin, float cx, float cy, float[] out, int idx) {
        out[idx] = cx + dx * cos - dy * sin;
        out[idx + 1] = cy + dx * sin + dy * cos;
    }

    private boolean isInHandle(float x, float y, float handleX, float handleY, float size) {
        float halfSize = size / 2;
        return x >= handleX - halfSize && x <= handleX + halfSize &&
                y >= handleY - halfSize && y <= handleY + halfSize;
    }

    /**
     * Checks if a screen point is near the anchor point.
     */
    public boolean isNearAnchor(EditorGameObject entity, float screenX, float screenY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return false;

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        Vector2f anchor = transform.getAnchor();

        // Calculate anchor position in parent space
        float parentWidth = state.getCanvasWidth();
        float parentHeight = state.getCanvasHeight();
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = findParentWithUITransform(entity);
        if (parent != null) {
            float[] parentBounds = calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        float anchorCanvasX = parentX + anchor.x * parentWidth;
        float anchorCanvasY = parentY + anchor.y * parentHeight;

        Vector2f anchorScreen = canvasToScreen(anchorCanvasX, anchorCanvasY);

        float distance = (float) Math.sqrt(
                Math.pow(screenX - anchorScreen.x, 2) +
                        Math.pow(screenY - anchorScreen.y, 2)
        );

        return distance <= UIDesignerState.HANDLE_HIT_SIZE;
    }

    /**
     * Checks if a screen point is near the pivot point.
     */
    public boolean isNearPivot(EditorGameObject entity, float screenX, float screenY) {
        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        // Set canvas bounds (needed for matrix calculation)
        transform.setScreenBounds(state.getCanvasWidth(), state.getCanvasHeight());

        // Get world pivot position directly from matrix
        Vector2f pivotCanvas = transform.getWorldPivotPosition2D();
        Vector2f pivotScreen = canvasToScreen(pivotCanvas.x, pivotCanvas.y);

        float distance = (float) Math.sqrt(
                Math.pow(screenX - pivotScreen.x, 2) +
                        Math.pow(screenY - pivotScreen.y, 2)
        );

        return distance <= UIDesignerState.HANDLE_HIT_SIZE;
    }

    // ========================================================================
    // CANVAS BOUNDS
    // ========================================================================

    /**
     * Gets the canvas bounds in screen coordinates.
     *
     * @return float[4]: {left, top, right, bottom}
     */
    public float[] getCanvasScreenBounds() {
        Vector2f topLeft = canvasToScreen(0, 0);
        Vector2f bottomRight = canvasToScreen(state.getCanvasWidth(), state.getCanvasHeight());
        return new float[]{topLeft.x, topLeft.y, bottomRight.x, bottomRight.y};
    }
}
