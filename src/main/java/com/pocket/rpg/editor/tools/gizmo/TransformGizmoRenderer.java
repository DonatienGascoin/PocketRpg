package com.pocket.rpg.editor.tools.gizmo;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Shared rendering utilities for transform gizmos (Move, Rotate, Scale).
 * Provides screen-space drawing and hit testing for gizmo elements.
 */
public final class TransformGizmoRenderer {

    private TransformGizmoRenderer() {
        // Utility class
    }

    // ========================================================================
    // ARROW DRAWING
    // ========================================================================

    /**
     * Draws an axis arrow in screen space.
     *
     * @param drawList ImGui draw list
     * @param startX   Start X in screen space
     * @param startY   Start Y in screen space
     * @param endX     End X in screen space
     * @param endY     End Y in screen space
     * @param color    Arrow color
     * @param thickness Line thickness
     */
    public static void drawArrow(ImDrawList drawList, float startX, float startY,
                                   float endX, float endY, int color, float thickness) {
        // Draw the main line
        drawList.addLine(startX, startY, endX, endY, color, thickness);

        // Calculate direction
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;

        // Perpendicular
        float px = -ny;
        float py = nx;

        // Arrow head size
        float headSize = 10f;

        // Arrow head points
        float ax = endX - nx * headSize + px * headSize * 0.4f;
        float ay = endY - ny * headSize + py * headSize * 0.4f;
        float bx = endX - nx * headSize - px * headSize * 0.4f;
        float by = endY - ny * headSize - py * headSize * 0.4f;

        // Draw filled arrow head
        drawList.addTriangleFilled(endX, endY, ax, ay, bx, by, color);
    }

    /**
     * Draws axis arrows for the move gizmo at a screen position.
     *
     * @param drawList     ImGui draw list
     * @param centerX      Center X in screen space
     * @param centerY      Center Y in screen space
     * @param arrowLength  Length of each arrow in pixels
     * @param hoveredAxis  Currently hovered axis (0=none, 1=X, 2=Y, 3=free)
     * @param isDragging   Whether currently dragging
     * @param isYUp        Whether Y is up (affects arrow direction in screen space)
     */
    public static void drawMoveGizmo(ImDrawList drawList, float centerX, float centerY,
                                       float arrowLength, int hoveredAxis, boolean isDragging,
                                       boolean isYUp) {
        float thickness = 3f;

        // X-axis arrow (pointing right)
        int xColor = (hoveredAxis == 1) ? GizmoColors.AXIS_X_HOVER : GizmoColors.AXIS_X;
        if (isDragging && hoveredAxis == 1) xColor = GizmoColors.ACTIVE;
        drawArrow(drawList, centerX, centerY, centerX + arrowLength, centerY, xColor, thickness);

        // Y-axis arrow (pointing up in screen space means Y- in ImGui coords)
        int yColor = (hoveredAxis == 2) ? GizmoColors.AXIS_Y_HOVER : GizmoColors.AXIS_Y;
        if (isDragging && hoveredAxis == 2) yColor = GizmoColors.ACTIVE;
        float yEndY = isYUp ? centerY - arrowLength : centerY + arrowLength;
        drawArrow(drawList, centerX, centerY, centerX, yEndY, yColor, thickness);

        // Free movement center square
        float squareSize = 12f;
        int freeColor = (hoveredAxis == 3) ? GizmoColors.FREE_MOVE_HOVER : GizmoColors.FREE_MOVE;
        if (isDragging && hoveredAxis == 3) freeColor = GizmoColors.ACTIVE;
        drawList.addRectFilled(
                centerX - squareSize / 2, centerY - squareSize / 2,
                centerX + squareSize / 2, centerY + squareSize / 2,
                freeColor
        );
    }

    // ========================================================================
    // ROTATION RING DRAWING
    // ========================================================================

    /**
     * Draws a rotation ring for the rotate gizmo.
     *
     * @param drawList     ImGui draw list
     * @param centerX      Center X in screen space
     * @param centerY      Center Y in screen space
     * @param radius       Radius in pixels
     * @param isHovered    Whether the ring is hovered
     * @param isDragging   Whether currently dragging
     * @param currentAngle Current rotation angle in radians (for handle position)
     */
    public static void drawRotateGizmo(ImDrawList drawList, float centerX, float centerY,
                                         float radius, boolean isHovered, boolean isDragging,
                                         float currentAngle) {
        int color = isDragging ? GizmoColors.ACTIVE :
                (isHovered ? GizmoColors.ROTATION_HOVER : GizmoColors.ROTATION);
        float thickness = isDragging ? 3f : 2f;

        // Draw the ring
        drawList.addCircle(centerX, centerY, radius, color, 64, thickness);

        // Draw a small handle at the current rotation angle
        float handleX = centerX + (float) Math.cos(currentAngle) * radius;
        float handleY = centerY - (float) Math.sin(currentAngle) * radius; // Y inverted for screen
        float handleSize = 6f;

        int handleColor = isDragging ? GizmoColors.ACTIVE : GizmoColors.WHITE;
        drawList.addCircleFilled(handleX, handleY, handleSize, handleColor);

        // Draw line from center to handle (shows rotation)
        drawList.addLine(centerX, centerY, handleX, handleY, color, 1.5f);
    }

    // ========================================================================
    // SCALE HANDLES DRAWING
    // ========================================================================

    /**
     * Scale handle position identifiers.
     */
    public enum ScaleHandle {
        NONE,
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, RIGHT,
        BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
    }

    /**
     * Draws scale handles around a bounding box.
     *
     * @param drawList      ImGui draw list
     * @param corners       4 corners [topLeft, topRight, bottomRight, bottomLeft] in screen space
     * @param hoveredHandle Currently hovered handle
     * @param isDragging    Whether currently dragging
     */
    public static void drawScaleGizmo(ImDrawList drawList, Vector2f[] corners,
                                        ScaleHandle hoveredHandle, boolean isDragging) {
        if (corners == null || corners.length != 4) return;

        float handleSize = 8f;

        // Calculate edge midpoints
        Vector2f topMid = midpoint(corners[0], corners[1]);
        Vector2f rightMid = midpoint(corners[1], corners[2]);
        Vector2f bottomMid = midpoint(corners[2], corners[3]);
        Vector2f leftMid = midpoint(corners[3], corners[0]);

        // Draw corner handles (uniform scale)
        drawScaleHandle(drawList, corners[0].x, corners[0].y, handleSize, hoveredHandle == ScaleHandle.TOP_LEFT, isDragging);
        drawScaleHandle(drawList, corners[1].x, corners[1].y, handleSize, hoveredHandle == ScaleHandle.TOP_RIGHT, isDragging);
        drawScaleHandle(drawList, corners[2].x, corners[2].y, handleSize, hoveredHandle == ScaleHandle.BOTTOM_RIGHT, isDragging);
        drawScaleHandle(drawList, corners[3].x, corners[3].y, handleSize, hoveredHandle == ScaleHandle.BOTTOM_LEFT, isDragging);

        // Draw edge handles (non-uniform scale)
        drawScaleHandle(drawList, topMid.x, topMid.y, handleSize * 0.8f, hoveredHandle == ScaleHandle.TOP, isDragging);
        drawScaleHandle(drawList, rightMid.x, rightMid.y, handleSize * 0.8f, hoveredHandle == ScaleHandle.RIGHT, isDragging);
        drawScaleHandle(drawList, bottomMid.x, bottomMid.y, handleSize * 0.8f, hoveredHandle == ScaleHandle.BOTTOM, isDragging);
        drawScaleHandle(drawList, leftMid.x, leftMid.y, handleSize * 0.8f, hoveredHandle == ScaleHandle.LEFT, isDragging);

        // Draw bounding box outline
        int outlineColor = GizmoColors.withAlpha(GizmoColors.SCALE, 0.5f);
        drawList.addQuad(
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y,
                outlineColor, 1.5f
        );
    }

    private static void drawScaleHandle(ImDrawList drawList, float x, float y, float size,
                                          boolean isHovered, boolean isDragging) {
        int color = isDragging && isHovered ? GizmoColors.ACTIVE :
                (isHovered ? GizmoColors.SCALE_HOVER : GizmoColors.SCALE);
        drawList.addRectFilled(x - size / 2, y - size / 2, x + size / 2, y + size / 2, color);
    }

    // ========================================================================
    // HIT TESTING
    // ========================================================================

    /**
     * Tests if a point is near a line segment.
     *
     * @param px, py   Point to test
     * @param x1, y1   Line start
     * @param x2, y2   Line end
     * @param threshold Distance threshold in pixels
     * @return true if point is within threshold distance of the line
     */
    public static boolean isPointNearLine(float px, float py, float x1, float y1,
                                            float x2, float y2, float threshold) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSq = dx * dx + dy * dy;

        if (lengthSq < 0.001f) {
            // Line is a point
            float dist = distance(px, py, x1, y1);
            return dist <= threshold;
        }

        // Project point onto line
        float t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSq));
        float projX = x1 + t * dx;
        float projY = y1 + t * dy;

        float dist = distance(px, py, projX, projY);
        return dist <= threshold;
    }

    /**
     * Tests if a point is inside a circle.
     */
    public static boolean isPointInCircle(float px, float py, float cx, float cy, float radius) {
        return distance(px, py, cx, cy) <= radius;
    }

    /**
     * Tests if a point is near a circle's ring (not inside).
     *
     * @param ringThickness Thickness of the ring in pixels
     */
    public static boolean isPointNearRing(float px, float py, float cx, float cy,
                                            float radius, float ringThickness) {
        float dist = distance(px, py, cx, cy);
        return dist >= radius - ringThickness && dist <= radius + ringThickness;
    }

    /**
     * Tests if a point is inside a rectangle.
     */
    public static boolean isPointInRect(float px, float py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Calculates the angle from a center point to a point.
     *
     * @return Angle in radians
     */
    public static float angleFromCenter(float px, float py, float cx, float cy) {
        return (float) Math.atan2(cy - py, px - cx); // Note: Y inverted for screen coords
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    /**
     * Converts world position to screen position within a viewport.
     */
    public static Vector2f worldToScreen(EditorCamera camera, float worldX, float worldY,
                                           float viewportX, float viewportY) {
        Vector2f screen = camera.worldToScreen(worldX, worldY);
        return new Vector2f(viewportX + screen.x, viewportY + screen.y);
    }

    /**
     * Converts screen position (within viewport) to world position.
     */
    public static Vector2f screenToWorld(EditorCamera camera, float screenX, float screenY,
                                           float viewportX, float viewportY) {
        float relX = screenX - viewportX;
        float relY = screenY - viewportY;
        org.joml.Vector3f world3d = camera.screenToWorld(relX, relY);
        return new Vector2f(world3d.x, world3d.y);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static Vector2f midpoint(Vector2f a, Vector2f b) {
        return new Vector2f((a.x + b.x) / 2, (a.y + b.y) / 2);
    }

    /**
     * Draws delta text near a position (e.g., "+2.5, -1.0").
     */
    public static void drawDeltaText(ImDrawList drawList, float x, float y, String text) {
        int bgColor = ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.7f);
        int textColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);

        float padding = 4f;
        float textWidth = ImGui.calcTextSize(text).x;
        float textHeight = ImGui.calcTextSize(text).y;

        // Draw background
        drawList.addRectFilled(
                x - padding, y - padding,
                x + textWidth + padding, y + textHeight + padding,
                bgColor, 3f
        );

        // Draw text
        drawList.addText(x, y, textColor, text);
    }

    // ========================================================================
    // ENTITY HOVER HIGHLIGHT
    // ========================================================================

    /**
     * Draws a hover highlight (cyan semi-transparent border) around an entity.
     * Used by transform tools to show which entity would be selected on click.
     *
     * @param drawList   ImGui draw list
     * @param entity     Entity to highlight
     * @param camera     Editor camera for coordinate conversion
     * @param viewportX  Viewport X offset
     * @param viewportY  Viewport Y offset
     */
    public static void drawEntityHoverHighlight(ImDrawList drawList, EditorGameObject entity,
                                                  EditorCamera camera, float viewportX, float viewportY) {
        Vector2f[] corners = getEntityScreenCorners(entity, camera, viewportX, viewportY);
        if (corners == null) return;

        // Hover color (cyan, semi-transparent)
        int hoverColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 1.0f, 0.6f);

        // Draw quad outline
        drawList.addQuad(
                corners[0].x, corners[0].y,
                corners[1].x, corners[1].y,
                corners[2].x, corners[2].y,
                corners[3].x, corners[3].y,
                hoverColor, 1.5f
        );
    }

    /**
     * Gets the four screen-space corners of an entity's bounding box,
     * accounting for pivot, scale, and rotation.
     *
     * @return Array of 4 corners [topLeft, topRight, bottomRight, bottomLeft] in screen space,
     *         or null if entity has no size
     */
    public static Vector2f[] getEntityScreenCorners(EditorGameObject entity, EditorCamera camera,
                                                      float viewportX, float viewportY) {
        Vector3f pos = entity.getPosition();
        Vector3f scale = entity.getScale();
        Vector3f rotation = entity.getRotation();
        Vector2f size = entity.getCurrentSize();

        if (size == null) {
            size = new Vector2f(1f, 1f);
        }

        // Get pivot from sprite (default to center if no sprite)
        float pivotX = 0.5f;
        float pivotY = 0.5f;
        SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
        if (sr != null) {
            Sprite sprite = sr.getSprite();
            if (sprite != null) {
                pivotX = sprite.getPivotX();
                pivotY = sprite.getPivotY();
            }
        }

        // Calculate scaled size
        float width = size.x * scale.x;
        float height = size.y * scale.y;

        // Calculate corner offsets from position (based on pivot)
        float left = -pivotX * width;
        float right = (1f - pivotX) * width;
        float bottom = -pivotY * height;
        float top = (1f - pivotY) * height;

        // Local corners (before rotation)
        float[][] localCorners = {
                {left, top},      // top-left
                {right, top},     // top-right
                {right, bottom},  // bottom-right
                {left, bottom}    // bottom-left
        };

        // Apply rotation
        float rotZ = (float) Math.toRadians(rotation.z);
        float cos = (float) Math.cos(rotZ);
        float sin = (float) Math.sin(rotZ);

        Vector2f[] screenCorners = new Vector2f[4];
        for (int i = 0; i < 4; i++) {
            float lx = localCorners[i][0];
            float ly = localCorners[i][1];

            // Rotate around origin (which is at entity position)
            float worldX = pos.x + lx * cos - ly * sin;
            float worldY = pos.y + lx * sin + ly * cos;

            // Convert to screen
            Vector2f screen = camera.worldToScreen(worldX, worldY);
            screenCorners[i] = new Vector2f(viewportX + screen.x, viewportY + screen.y);
        }

        return screenCorners;
    }
}
