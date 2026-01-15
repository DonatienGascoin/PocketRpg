package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Vector2f;

/**
 * Draws selection gizmos, resize handles, anchor/pivot points, and snap guides
 * for the UI Designer panel.
 */
public class UIDesignerGizmoDrawer {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;

    public UIDesignerGizmoDrawer(UIDesignerState state, UIDesignerCoordinates coords) {
        this.state = state;
        this.coords = coords;
    }

    // ========================================================================
    // CANVAS BOUNDS
    // ========================================================================

    /**
     * Draws the canvas bounds rectangle with label.
     */
    public void drawCanvasBounds(ImDrawList drawList) {
        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Background
        drawList.addRectFilled(left, top, right, bottom,
                ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1.0f));

        // Border
        drawList.addRect(left, top, right, bottom, UIDesignerState.COLOR_CANVAS_BORDER, 0, 0, 2.0f);

        // Size label
        String label = state.getCanvasWidth() + " x " + state.getCanvasHeight();
        drawList.addText(left + 5, top + 5, UIDesignerState.COLOR_CANVAS_LABEL, label);
    }

    // ========================================================================
    // SELECTION BORDERS
    // ========================================================================

    /**
     * Draws borders around all UI elements, highlighting selected ones.
     * Supports rotated elements by drawing 4 lines forming a rotated rectangle.
     */
    public void drawSelectionBorders(ImDrawList drawList, EditorScene scene) {
        if (scene == null) return;

        for (EditorGameObject entity : scene.getEntities()) {
            if (!coords.isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

            UITransform transform = entity.getComponent(UITransform.class);
            // Negate rotation to convert to screen space (Y-down)
            // Use matrix-based method for correct hierarchy handling
            float rotation = transform != null ? -transform.getComputedWorldRotation2D() : 0;

            float[] corners = calculateRotatedScreenCorners(entity, transform, rotation);
            if (corners == null) continue;

            boolean selected = scene.isSelected(entity);
            int borderColor = selected ? UIDesignerState.COLOR_SELECTION
                    : ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.8f);
            float thickness = selected ? 2.0f : 1.0f;

            // Draw 4 lines forming rotated rectangle
            drawList.addLine(corners[0], corners[1], corners[2], corners[3], borderColor, thickness); // TL->TR
            drawList.addLine(corners[2], corners[3], corners[4], corners[5], borderColor, thickness); // TR->BR
            drawList.addLine(corners[4], corners[5], corners[6], corners[7], borderColor, thickness); // BR->BL
            drawList.addLine(corners[6], corners[7], corners[0], corners[1], borderColor, thickness); // BL->TL
        }
    }

    /**
     * Calculates the rotated corners of an element in screen coordinates.
     * Returns [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y] or null if invalid.
     */
    private float[] calculateRotatedScreenCorners(EditorGameObject entity, UITransform transform, float rotation) {
        float[] bounds = coords.calculateElementBounds(entity);
        if (bounds == null) return null;

        float x = bounds[0], y = bounds[1], w = bounds[2], h = bounds[3];

        // If no rotation, use simple axis-aligned calculation
        if (Math.abs(rotation) < 0.001f) {
            Vector2f screenTL = coords.canvasToScreen(x, y);
            Vector2f screenBR = coords.canvasToScreen(x + w, y + h);
            float left = state.getViewportX() + screenTL.x;
            float top = state.getViewportY() + screenTL.y;
            float right = state.getViewportX() + screenBR.x;
            float bottom = state.getViewportY() + screenBR.y;
            return new float[]{left, top, right, top, right, bottom, left, bottom};
        }

        // Get pivot position in canvas space (use effective pivot for MATCH_PARENT)
        Vector2f pivot = transform != null ? transform.getEffectivePivot() : new Vector2f(0.5f, 0.5f);
        float pivotCanvasX = x + pivot.x * w;
        float pivotCanvasY = y + pivot.y * h;

        // Convert pivot to screen coords
        Vector2f screenPivot = coords.canvasToScreen(pivotCanvasX, pivotCanvasY);
        float spx = state.getViewportX() + screenPivot.x;
        float spy = state.getViewportY() + screenPivot.y;

        // Half dimensions in screen space
        float hw = (w * state.getZoom()) / 2;
        float hh = (h * state.getZoom()) / 2;

        // Offset from pivot to center (in screen space)
        float cx = (0.5f - pivot.x) * w * state.getZoom();
        float cy = (0.5f - pivot.y) * h * state.getZoom();

        // Rotation math
        float cos = (float) Math.cos(Math.toRadians(rotation));
        float sin = (float) Math.sin(Math.toRadians(rotation));

        // Rotate center offset around pivot
        float rcx = cx * cos - cy * sin;
        float rcy = cx * sin + cy * cos;

        // Center position in screen coords
        float centerX = spx + rcx;
        float centerY = spy + rcy;

        // Calculate rotated corners around center
        float[] corners = new float[8];
        rotatePoint(-hw, -hh, cos, sin, centerX, centerY, corners, 0);  // TL
        rotatePoint(hw, -hh, cos, sin, centerX, centerY, corners, 2);   // TR
        rotatePoint(hw, hh, cos, sin, centerX, centerY, corners, 4);    // BR
        rotatePoint(-hw, hh, cos, sin, centerX, centerY, corners, 6);   // BL
        return corners;
    }

    /**
     * Rotates a point around a center and stores result in output array.
     */
    private void rotatePoint(float dx, float dy, float cos, float sin,
                             float centerX, float centerY, float[] out, int index) {
        out[index] = centerX + dx * cos - dy * sin;
        out[index + 1] = centerY + dx * sin + dy * cos;
    }

    // ========================================================================
    // SELECTION GIZMOS (handles, anchor, pivot)
    // ========================================================================

    /**
     * Draws gizmos for all selected entities and updates hover state.
     */
    public void drawSelectionGizmos(ImDrawList drawList, EditorScene scene) {
        if (scene == null) return;

        state.clearHoverState();

        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();

        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!coords.isUIEntity(entity)) continue;
            if (entity.hasComponent(UICanvas.class)) continue;

            // Update hover state if not dragging
            if (state.isHovered() && !state.isAnyDragActive()) {
                UIDesignerState.ResizeHandle handle = coords.getHandleAtPosition(entity, localX, localY);
                if (handle != null) {
                    state.setHoveredHandle(handle);
                    state.setHoveredHandleEntity(entity);
                }
            }

            drawEntityGizmos(drawList, entity);
        }
    }

    /**
     * Draws all gizmos for a single entity.
     */
    private void drawEntityGizmos(ImDrawList drawList, EditorGameObject entity) {
        float[] bounds = coords.calculateElementBounds(entity);
        if (bounds == null) return;

        float x = bounds[0];
        float y = bounds[1];
        float width = bounds[2];
        float height = bounds[3];

        UITransform transform = entity.getComponent(UITransform.class);
        // Negate rotation to convert to screen space (Y-down)
        // Use matrix-based method for correct hierarchy handling
        float rotation = transform != null ? -transform.getComputedWorldRotation2D() : 0;
        float[] corners = calculateRotatedScreenCorners(entity, transform, rotation);

        if (corners != null) {
            drawResizeHandles(drawList, entity, corners);
        }

        drawAnchorPoint(drawList, entity, x, y, width, height);
        drawPivotPoint(drawList, entity, x, y, width, height);
    }

    // ========================================================================
    // RESIZE HANDLES
    // ========================================================================

    /**
     * Draws resize handles at rotated corner and edge positions.
     * corners array: [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y]
     */
    private void drawResizeHandles(ImDrawList drawList, EditorGameObject entity, float[] corners) {
        // Extract corner positions
        float tlX = corners[0], tlY = corners[1];
        float trX = corners[2], trY = corners[3];
        float brX = corners[4], brY = corners[5];
        float blX = corners[6], blY = corners[7];

        // Calculate edge midpoints
        float topMidX = (tlX + trX) / 2, topMidY = (tlY + trY) / 2;
        float bottomMidX = (blX + brX) / 2, bottomMidY = (blY + brY) / 2;
        float leftMidX = (tlX + blX) / 2, leftMidY = (tlY + blY) / 2;
        float rightMidX = (trX + brX) / 2, rightMidY = (trY + brY) / 2;

        boolean isThisEntity = entity == state.getHoveredHandleEntity();

        // Corners
        drawHandle(drawList, tlX, tlY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.TOP_LEFT);
        drawHandle(drawList, trX, trY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.TOP_RIGHT);
        drawHandle(drawList, blX, blY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.BOTTOM_LEFT);
        drawHandle(drawList, brX, brY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.BOTTOM_RIGHT);

        // Edges
        drawHandle(drawList, topMidX, topMidY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.TOP);
        drawHandle(drawList, bottomMidX, bottomMidY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.BOTTOM);
        drawHandle(drawList, leftMidX, leftMidY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.LEFT);
        drawHandle(drawList, rightMidX, rightMidY, isThisEntity && state.getHoveredHandle() == UIDesignerState.ResizeHandle.RIGHT);
    }

    private void drawHandle(ImDrawList drawList, float x, float y, boolean hovered) {
        float half = (hovered ? UIDesignerState.HANDLE_SIZE + 2 : UIDesignerState.HANDLE_SIZE) / 2;
        int fillColor = hovered ? UIDesignerState.COLOR_HANDLE_HOVERED : UIDesignerState.COLOR_HANDLE;
        drawList.addRectFilled(x - half, y - half, x + half, y + half, fillColor);
        drawList.addRect(x - half, y - half, x + half, y + half, UIDesignerState.COLOR_HANDLE_BORDER);
    }

    // ========================================================================
    // ANCHOR POINT
    // ========================================================================

    private void drawAnchorPoint(ImDrawList drawList, EditorGameObject entity,
                                 float elemX, float elemY, float elemWidth, float elemHeight) {
        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return;

        Vector2f anchor = transform.getAnchor();

        // Get parent bounds
        float parentWidth = state.getCanvasWidth();
        float parentHeight = state.getCanvasHeight();
        float parentX = 0;
        float parentY = 0;

        EditorGameObject parent = coords.findParentWithUITransform(entity);
        if (parent != null) {
            float[] parentBounds = coords.calculateElementBounds(parent);
            if (parentBounds != null) {
                parentX = parentBounds[0];
                parentY = parentBounds[1];
                parentWidth = parentBounds[2];
                parentHeight = parentBounds[3];
            }
        }

        // Calculate anchor screen position
        float anchorX = parentX + anchor.x * parentWidth;
        float anchorY = parentY + anchor.y * parentHeight;

        Vector2f screenAnchor = coords.canvasToScreen(anchorX, anchorY);
        float sx = state.getViewportX() + screenAnchor.x;
        float sy = state.getViewportY() + screenAnchor.y;

        // Check hover
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();
        boolean hovered = Math.abs(localX - screenAnchor.x) < 12 && Math.abs(localY - screenAnchor.y) < 12;

        float size = hovered ? 8 : 6;
        int color = hovered ? ImGui.colorConvertFloat4ToU32(0.4f, 1f, 0.4f, 1f) : UIDesignerState.COLOR_ANCHOR;

        // Draw diamond shape
        drawList.addQuadFilled(
                sx, sy - size,
                sx + size, sy,
                sx, sy + size,
                sx - size, sy,
                color
        );
        drawList.addQuad(
                sx, sy - size,
                sx + size, sy,
                sx, sy + size,
                sx - size, sy,
                UIDesignerState.COLOR_HANDLE_BORDER
        );

        // Draw anchor line to element center
        if (state.isShowAnchorLines()) {
            float elemCenterX = elemX + elemWidth / 2;
            float elemCenterY = elemY + elemHeight / 2;
            Vector2f screenCenter = coords.canvasToScreen(elemCenterX, elemCenterY);
            drawList.addLine(sx, sy,
                    state.getViewportX() + screenCenter.x,
                    state.getViewportY() + screenCenter.y,
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 0.2f, 0.3f), 1f);
        }

        // Draw label on hover or during drag
        if (hovered || state.isDraggingAnchor()) {
            String label = String.format("Anchor (%.2f, %.2f)", anchor.x, anchor.y);
            drawList.addText(sx + 10, sy - 10, UIDesignerState.COLOR_ANCHOR, label);
        }
    }

    // ========================================================================
    // PIVOT POINT
    // ========================================================================

    private void drawPivotPoint(ImDrawList drawList, EditorGameObject entity,
                                float elemX, float elemY, float elemWidth, float elemHeight) {
        UITransform transform = entity.getComponent(UITransform.class);
        if (transform == null) return;

        Vector2f pivot = transform.getEffectivePivot();  // Use effective pivot for MATCH_PARENT

        // Calculate pivot screen position
        float pivotX = elemX + pivot.x * elemWidth;
        float pivotY = elemY + pivot.y * elemHeight;

        Vector2f screenPivot = coords.canvasToScreen(pivotX, pivotY);
        float sx = state.getViewportX() + screenPivot.x;
        float sy = state.getViewportY() + screenPivot.y;

        // Check hover
        ImVec2 mousePos = ImGui.getMousePos();
        float localX = mousePos.x - state.getViewportX();
        float localY = mousePos.y - state.getViewportY();
        boolean hovered = Math.abs(localX - screenPivot.x) < 10 && Math.abs(localY - screenPivot.y) < 10;

        float radius = hovered ? 7 : 5;
        int color = hovered ? ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1f, 1f) : UIDesignerState.COLOR_PIVOT;

        // Draw circle with crosshair
        drawList.addCircleFilled(sx, sy, radius, color);
        drawList.addCircle(sx, sy, radius, UIDesignerState.COLOR_HANDLE_BORDER);
        drawList.addLine(sx - radius - 2, sy, sx + radius + 2, sy, color, 1f);
        drawList.addLine(sx, sy - radius - 2, sx, sy + radius + 2, color, 1f);

        // Draw label on hover or during drag
        if (hovered || state.isDraggingPivot()) {
            String label = String.format("Pivot (%.2f, %.2f)", pivot.x, pivot.y);
            drawList.addText(sx + 10, sy + 5, UIDesignerState.COLOR_PIVOT, label);
        }
    }

    // ========================================================================
    // SNAP GUIDES
    // ========================================================================

    /**
     * Draws snap guides when dragging near canvas edges.
     */
    public void drawSnapGuides(ImDrawList drawList) {
        if (!state.isSnapEnabled()) return;

        EditorGameObject draggedEntity = state.getDraggedEntity();
        if (draggedEntity == null) return;

        float[] bounds = coords.calculateElementBounds(draggedEntity);
        if (bounds == null) return;

        float canvasWidth = state.getCanvasWidth();
        float canvasHeight = state.getCanvasHeight();

        float elemLeft = bounds[0];
        float elemTop = bounds[1];
        float elemRight = elemLeft + bounds[2];
        float elemBottom = elemTop + bounds[3];

        float threshold = UIDesignerState.SNAP_THRESHOLD / state.getZoom();

        // Left edge
        if (Math.abs(elemLeft) < threshold || Math.abs(elemRight) < threshold) {
            Vector2f top = coords.canvasToScreen(0, 0);
            Vector2f bottom = coords.canvasToScreen(0, canvasHeight);
            drawList.addLine(
                    state.getViewportX() + top.x, state.getViewportY() + top.y,
                    state.getViewportX() + bottom.x, state.getViewportY() + bottom.y,
                    UIDesignerState.COLOR_SNAP_GUIDE, 1f);
        }

        // Right edge
        if (Math.abs(elemLeft - canvasWidth) < threshold || Math.abs(elemRight - canvasWidth) < threshold) {
            Vector2f top = coords.canvasToScreen(canvasWidth, 0);
            Vector2f bottom = coords.canvasToScreen(canvasWidth, canvasHeight);
            drawList.addLine(
                    state.getViewportX() + top.x, state.getViewportY() + top.y,
                    state.getViewportX() + bottom.x, state.getViewportY() + bottom.y,
                    UIDesignerState.COLOR_SNAP_GUIDE, 1f);
        }

        // Top edge
        if (Math.abs(elemTop) < threshold || Math.abs(elemBottom) < threshold) {
            Vector2f left = coords.canvasToScreen(0, 0);
            Vector2f right = coords.canvasToScreen(canvasWidth, 0);
            drawList.addLine(
                    state.getViewportX() + left.x, state.getViewportY() + left.y,
                    state.getViewportX() + right.x, state.getViewportY() + right.y,
                    UIDesignerState.COLOR_SNAP_GUIDE, 1f);
        }

        // Bottom edge
        if (Math.abs(elemTop - canvasHeight) < threshold || Math.abs(elemBottom - canvasHeight) < threshold) {
            Vector2f left = coords.canvasToScreen(0, canvasHeight);
            Vector2f right = coords.canvasToScreen(canvasWidth, canvasHeight);
            drawList.addLine(
                    state.getViewportX() + left.x, state.getViewportY() + left.y,
                    state.getViewportX() + right.x, state.getViewportY() + right.y,
                    UIDesignerState.COLOR_SNAP_GUIDE, 1f);
        }
    }
}
