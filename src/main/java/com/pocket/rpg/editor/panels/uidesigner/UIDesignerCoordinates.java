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
    // BOUNDS CALCULATION (using direct typed access)
    // ========================================================================

    /**
     * Calculates the bounds of a UI element in canvas coordinates.
     *
     * @param entity The entity to calculate bounds for
     * @return float[4]: {x, y, width, height} or null if no UITransform
     */
    public float[] calculateElementBounds(EditorGameObject entity) {
        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return null;

        float width = transform.getWidth();
        float height = transform.getHeight();
        Vector2f offset = transform.getOffset();
        Vector2f anchor = transform.getAnchor();
        Vector2f pivot = transform.getPivot();

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

        float anchorX = anchor.x * parentWidth;
        float anchorY = anchor.y * parentHeight;

        float x = parentX + anchorX + offset.x - pivot.x * width;
        float y = parentY + anchorY + offset.y - pivot.y * height;

        return new float[]{x, y, width, height};
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
     *
     * @param entity  The entity to check handles for
     * @param screenX Screen X position
     * @param screenY Screen Y position
     * @return The ResizeHandle at position, or null if none
     */
    public UIDesignerState.ResizeHandle getHandleAtPosition(EditorGameObject entity, float screenX, float screenY) {
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return null;

        Vector2f topLeft = canvasToScreen(bounds[0], bounds[1]);
        Vector2f bottomRight = canvasToScreen(bounds[0] + bounds[2], bounds[1] + bounds[3]);

        float left = topLeft.x;
        float top = topLeft.y;
        float right = bottomRight.x;
        float bottom = bottomRight.y;
        float midX = (left + right) / 2;
        float midY = (top + bottom) / 2;

        float hitSize = UIDesignerState.HANDLE_HIT_SIZE;

        // Check corners first (priority)
        if (isInHandle(screenX, screenY, left, top, hitSize)) return UIDesignerState.ResizeHandle.TOP_LEFT;
        if (isInHandle(screenX, screenY, right, top, hitSize)) return UIDesignerState.ResizeHandle.TOP_RIGHT;
        if (isInHandle(screenX, screenY, left, bottom, hitSize)) return UIDesignerState.ResizeHandle.BOTTOM_LEFT;
        if (isInHandle(screenX, screenY, right, bottom, hitSize)) return UIDesignerState.ResizeHandle.BOTTOM_RIGHT;

        // Check edges
        if (isInHandle(screenX, screenY, midX, top, hitSize)) return UIDesignerState.ResizeHandle.TOP;
        if (isInHandle(screenX, screenY, midX, bottom, hitSize)) return UIDesignerState.ResizeHandle.BOTTOM;
        if (isInHandle(screenX, screenY, left, midY, hitSize)) return UIDesignerState.ResizeHandle.LEFT;
        if (isInHandle(screenX, screenY, right, midY, hitSize)) return UIDesignerState.ResizeHandle.RIGHT;

        return null;
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
        float[] bounds = calculateElementBounds(entity);
        if (bounds == null) return false;

        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        Vector2f pivot = transform.getPivot();

        // Calculate pivot position in element space
        float pivotCanvasX = bounds[0] + pivot.x * bounds[2];
        float pivotCanvasY = bounds[1] + pivot.y * bounds[3];

        Vector2f pivotScreen = canvasToScreen(pivotCanvasX, pivotCanvasY);

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
