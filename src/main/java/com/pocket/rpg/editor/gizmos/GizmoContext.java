package com.pocket.rpg.editor.gizmos;

import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.camera.EditorCamera;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Provides drawing primitives for gizmo rendering.
 * All coordinates are in world space - the context handles conversion to screen space.
 * <p>
 * Usage:
 * <pre>
 * public void onDrawGizmosSelected(GizmoContext ctx) {
 *     ctx.setColor(GizmoColors.BOUNDS);
 *     ctx.drawRect(0, 0, 1, 1);  // World coordinates
 *
 *     ctx.setColor(GizmoColors.PIVOT);
 *     ctx.drawCircleFilled(0.5f, 0.5f, 0.1f);
 * }
 * </pre>
 */
public class GizmoContext {

    private final ImDrawList drawList;
    private final EditorCamera camera;
    private final float viewportX;
    private final float viewportY;

    @Getter
    @Setter
    private Transform transform;

    private int currentColor = GizmoColors.DEFAULT;
    private float currentThickness = 1.0f;

    /**
     * Creates a new GizmoContext.
     *
     * @param drawList  ImGui draw list for rendering
     * @param camera    Editor camera for coordinate conversion
     * @param viewportX Viewport X offset in screen space
     * @param viewportY Viewport Y offset in screen space
     */
    public GizmoContext(ImDrawList drawList, EditorCamera camera, float viewportX, float viewportY) {
        this.drawList = drawList;
        this.camera = camera;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
    }

    // ========================================================================
    // STYLE
    // ========================================================================

    /**
     * Sets the current drawing color using an ImGui color value.
     *
     * @param color ImGui color (use GizmoColors constants or ImGui.colorConvertFloat4ToU32)
     */
    public void setColor(int color) {
        this.currentColor = color;
    }

    /**
     * Sets the current drawing color using RGBA floats.
     *
     * @param r Red (0-1)
     * @param g Green (0-1)
     * @param b Blue (0-1)
     * @param a Alpha (0-1)
     */
    public void setColor(float r, float g, float b, float a) {
        this.currentColor = GizmoColors.fromRGBA(r, g, b, a);
    }

    /**
     * Sets the line thickness for drawing operations.
     *
     * @param thickness Line thickness in pixels
     */
    public void setThickness(float thickness) {
        this.currentThickness = thickness;
    }

    /**
     * Gets the current camera zoom level.
     * Useful for scaling gizmo sizes to remain consistent on screen.
     */
    public float getZoom() {
        return camera.getZoom();
    }

    /**
     * Returns a world-space size that appears as constant screen size regardless of zoom.
     * Similar to Unity's HandleUtility.GetHandleSize().
     * <p>
     * Use this for markers, pivots, and handles that should remain visually consistent.
     *
     * <h3>Example</h3>
     * <pre>
     * // Draw a pivot that always appears ~10 pixels on screen
     * float size = ctx.getHandleSize(10);
     * ctx.drawCircle(x, y, size);
     * </pre>
     *
     * @param desiredScreenPixels The desired size in screen pixels
     * @return World-space size that will appear as the desired screen size
     */
    public float getHandleSize(float desiredScreenPixels) {
        // Calculate how many screen pixels = 1 world unit at current zoom
        float pixelsPerWorldUnit = worldSizeToScreen(1.0f);
        if (pixelsPerWorldUnit < 0.001f) {
            return desiredScreenPixels; // Fallback
        }
        return desiredScreenPixels / pixelsPerWorldUnit;
    }

    // ========================================================================
    // COORDINATE CONVERSION
    // ========================================================================

    /**
     * Converts world coordinates to screen coordinates.
     */
    private Vector2f worldToScreen(float worldX, float worldY) {
        Vector2f screen = camera.worldToScreen(worldX, worldY);
        return new Vector2f(viewportX + screen.x, viewportY + screen.y);
    }

    /**
     * Converts a world-space size to screen-space size.
     */
    private float worldSizeToScreen(float worldSize) {
        // Use the camera's zoom and pixels per unit
        // We can calculate this by converting two points and measuring distance
        Vector2f p1 = camera.worldToScreen(0, 0);
        Vector2f p2 = camera.worldToScreen(worldSize, 0);
        return Math.abs(p2.x - p1.x);
    }

    // ========================================================================
    // LINE PRIMITIVES
    // ========================================================================

    /**
     * Draws a line between two world-space points.
     */
    public void drawLine(float x1, float y1, float x2, float y2) {
        Vector2f p1 = worldToScreen(x1, y1);
        Vector2f p2 = worldToScreen(x2, y2);
        drawList.addLine(p1.x, p1.y, p2.x, p2.y, currentColor, currentThickness);
    }

    /**
     * Draws a dashed line between two world-space points.
     *
     * @param dashLength Length of each dash in world units
     * @param gapLength  Length of gaps between dashes in world units
     */
    public void drawDashedLine(float x1, float y1, float x2, float y2, float dashLength, float gapLength) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;

        float pos = 0;
        boolean drawing = true;
        while (pos < length) {
            float segmentLength = drawing ? dashLength : gapLength;
            float endPos = Math.min(pos + segmentLength, length);

            if (drawing) {
                float sx = x1 + nx * pos;
                float sy = y1 + ny * pos;
                float ex = x1 + nx * endPos;
                float ey = y1 + ny * endPos;
                drawLine(sx, sy, ex, ey);
            }

            pos = endPos;
            drawing = !drawing;
        }
    }

    // ========================================================================
    // RECTANGLE PRIMITIVES
    // ========================================================================

    /**
     * Draws a rectangle outline in world space.
     *
     * @param x      Left edge X
     * @param y      Bottom edge Y (world space, Y-up)
     * @param width  Rectangle width
     * @param height Rectangle height
     */
    public void drawRect(float x, float y, float width, float height) {
        Vector2f tl = worldToScreen(x, y + height);
        Vector2f br = worldToScreen(x + width, y);
        drawList.addRect(tl.x, tl.y, br.x, br.y, currentColor, 0, 0, currentThickness);
    }

    /**
     * Draws a filled rectangle in world space.
     */
    public void drawRectFilled(float x, float y, float width, float height) {
        Vector2f tl = worldToScreen(x, y + height);
        Vector2f br = worldToScreen(x + width, y);
        drawList.addRectFilled(tl.x, tl.y, br.x, br.y, currentColor);
    }

    /**
     * Draws a rectangle centered on a point.
     *
     * @param centerX Center X
     * @param centerY Center Y
     * @param width   Full width
     * @param height  Full height
     */
    public void drawRectCentered(float centerX, float centerY, float width, float height) {
        drawRect(centerX - width / 2, centerY - height / 2, width, height);
    }

    /**
     * Draws a filled rectangle centered on a point.
     */
    public void drawRectCenteredFilled(float centerX, float centerY, float width, float height) {
        drawRectFilled(centerX - width / 2, centerY - height / 2, width, height);
    }

    // ========================================================================
    // CIRCLE PRIMITIVES
    // ========================================================================

    /**
     * Draws a circle outline in world space.
     *
     * @param x      Center X
     * @param y      Center Y
     * @param radius Radius in world units
     */
    public void drawCircle(float x, float y, float radius) {
        Vector2f center = worldToScreen(x, y);
        float screenRadius = worldSizeToScreen(radius);
        drawList.addCircle(center.x, center.y, screenRadius, currentColor, 0, currentThickness);
    }

    /**
     * Draws a filled circle in world space.
     */
    public void drawCircleFilled(float x, float y, float radius) {
        Vector2f center = worldToScreen(x, y);
        float screenRadius = worldSizeToScreen(radius);
        drawList.addCircleFilled(center.x, center.y, screenRadius, currentColor);
    }

    // ========================================================================
    // SPECIAL SHAPES
    // ========================================================================

    /**
     * Draws a crosshair (plus sign) at a world position.
     *
     * @param x    Center X
     * @param y    Center Y
     * @param size Half-size of each arm in world units
     */
    public void drawCrossHair(float x, float y, float size) {
        drawLine(x - size, y, x + size, y);
        drawLine(x, y - size, x, y + size);
    }

    /**
     * Draws a crosshair with a fixed screen size (doesn't scale with zoom).
     *
     * @param x          Center X in world space
     * @param y          Center Y in world space
     * @param screenSize Size in pixels
     */
    public void drawCrossHairScreenSize(float x, float y, float screenSize) {
        Vector2f center = worldToScreen(x, y);
        drawList.addLine(center.x - screenSize, center.y, center.x + screenSize, center.y, currentColor, currentThickness);
        drawList.addLine(center.x, center.y - screenSize, center.x, center.y + screenSize, currentColor, currentThickness);
    }

    /**
     * Draws a diamond shape at a world position.
     *
     * @param x    Center X
     * @param y    Center Y
     * @param size Distance from center to each point
     */
    public void drawDiamond(float x, float y, float size) {
        Vector2f top = worldToScreen(x, y + size);
        Vector2f right = worldToScreen(x + size, y);
        Vector2f bottom = worldToScreen(x, y - size);
        Vector2f left = worldToScreen(x - size, y);

        drawList.addQuad(top.x, top.y, right.x, right.y, bottom.x, bottom.y, left.x, left.y, currentColor, currentThickness);
    }

    /**
     * Draws a filled diamond shape.
     */
    public void drawDiamondFilled(float x, float y, float size) {
        Vector2f top = worldToScreen(x, y + size);
        Vector2f right = worldToScreen(x + size, y);
        Vector2f bottom = worldToScreen(x, y - size);
        Vector2f left = worldToScreen(x - size, y);

        drawList.addQuadFilled(top.x, top.y, right.x, right.y, bottom.x, bottom.y, left.x, left.y, currentColor);
    }

    /**
     * Draws a diamond with fixed screen size.
     */
    public void drawDiamondScreenSize(float x, float y, float screenSize) {
        Vector2f center = worldToScreen(x, y);

        drawList.addQuad(
                center.x, center.y - screenSize,
                center.x + screenSize, center.y,
                center.x, center.y + screenSize,
                center.x - screenSize, center.y,
                currentColor, currentThickness
        );
    }

    /**
     * Draws a filled diamond with fixed screen size.
     */
    public void drawDiamondFilledScreenSize(float x, float y, float screenSize) {
        Vector2f center = worldToScreen(x, y);

        drawList.addQuadFilled(
                center.x, center.y - screenSize,
                center.x + screenSize, center.y,
                center.x, center.y + screenSize,
                center.x - screenSize, center.y,
                currentColor
        );
    }

    /**
     * Draws an arrow from one point to another.
     *
     * @param x1       Start X
     * @param y1       Start Y
     * @param x2       End X (arrow tip)
     * @param y2       End Y (arrow tip)
     * @param headSize Size of the arrow head in world units
     */
    public void drawArrow(float x1, float y1, float x2, float y2, float headSize) {
        drawLine(x1, y1, x2, y2);

        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) return;

        float nx = dx / length;
        float ny = dy / length;

        // Perpendicular
        float px = -ny;
        float py = nx;

        // Arrow head points
        float ax = x2 - nx * headSize + px * headSize * 0.5f;
        float ay = y2 - ny * headSize + py * headSize * 0.5f;
        float bx = x2 - nx * headSize - px * headSize * 0.5f;
        float by = y2 - ny * headSize - py * headSize * 0.5f;

        drawLine(x2, y2, ax, ay);
        drawLine(x2, y2, bx, by);
    }

    // ========================================================================
    // TILE PRIMITIVES
    // ========================================================================

    /**
     * Highlights a tile at the given grid coordinates.
     *
     * @param tileX    Tile X coordinate
     * @param tileY    Tile Y coordinate
     * @param tileSize Size of each tile in world units
     */
    public void drawTileHighlight(int tileX, int tileY, float tileSize) {
        float worldX = tileX * tileSize;
        float worldY = tileY * tileSize;
        drawRectFilled(worldX, worldY, tileSize, tileSize);
    }

    /**
     * Draws a tile outline at the given grid coordinates.
     */
    public void drawTileOutline(int tileX, int tileY, float tileSize) {
        float worldX = tileX * tileSize;
        float worldY = tileY * tileSize;
        drawRect(worldX, worldY, tileSize, tileSize);
    }

    // ========================================================================
    // TEXT
    // ========================================================================

    /**
     * Draws text at a world position.
     * Note: Text does not scale with zoom.
     */
    public void drawText(float x, float y, String text) {
        Vector2f screen = worldToScreen(x, y);
        drawList.addText(screen.x, screen.y, currentColor, text);
    }

    /**
     * Draws text with an offset from a world position.
     *
     * @param x             World X
     * @param y             World Y
     * @param text          Text to draw
     * @param screenOffsetX Screen-space X offset
     * @param screenOffsetY Screen-space Y offset
     */
    public void drawText(float x, float y, String text, float screenOffsetX, float screenOffsetY) {
        Vector2f screen = worldToScreen(x, y);
        drawList.addText(screen.x + screenOffsetX, screen.y + screenOffsetY, currentColor, text);
    }

    /**
     * Draws text at a world position with a font size that scales with zoom.
     * The text grows and shrinks with the world, like other world-space gizmos.
     *
     * @param x              World X
     * @param y              World Y
     * @param text           Text to draw
     * @param worldFontSize  Base font size in world units
     * @param screenOffsetY  Screen-space Y offset (for positioning below icons)
     */
    public void drawTextScaled(float x, float y, String text, float worldFontSize, float screenOffsetY) {
        int fontSize = (int) worldSizeToScreen(worldFontSize);
        if (fontSize < 4) return; // Too small to read
        if (fontSize > 64) fontSize = 64; // Clamp to avoid huge text

        Vector2f screen = worldToScreen(x, y);
        drawList.addText(ImGui.getFont(), fontSize, screen.x, screen.y + screenOffsetY, currentColor, text);
    }

    // ========================================================================
    // AXIS / TRANSFORM GIZMOS
    // ========================================================================

    /**
     * Draws X and Y axis lines from a point.
     *
     * @param x      Origin X
     * @param y      Origin Y
     * @param length Length of each axis in world units
     */
    public void drawAxes(float x, float y, float length) {
        // X axis (red)
        setColor(GizmoColors.AXIS_X);
        drawLine(x, y, x + length, y);
        drawArrow(x, y, x + length, y, length * 0.15f);

        // Y axis (green)
        setColor(GizmoColors.AXIS_Y);
        drawLine(x, y, x, y + length);
        drawArrow(x, y, x, y + length, length * 0.15f);
    }

    /**
     * Draws a pivot/origin point indicator with crosshair and circle.
     *
     * @param x         Center X in world space
     * @param y         Center Y in world space
     * @param worldSize Radius of the circle (crosshair extends slightly beyond)
     */
    public void drawPivotPoint(float x, float y, float worldSize) {
        drawCircle(x, y, worldSize);
        drawCrossHair(x, y, worldSize * 0.7f);
    }

    /**
     * Draws a pivot point with fixed screen size.
     */
    public void drawPivotPointScreenSize(float x, float y, float screenSize) {
        Vector2f center = worldToScreen(x, y);
        drawList.addCircle(center.x, center.y, screenSize, currentColor, 0, currentThickness);
        drawList.addLine(center.x - screenSize * 1.5f, center.y, center.x + screenSize * 1.5f, center.y, currentColor, currentThickness);
        drawList.addLine(center.x, center.y - screenSize * 1.5f, center.x, center.y + screenSize * 1.5f, currentColor, currentThickness);
    }
}
