package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;

/**
 * Shared preview renderer for sprite editing with zoom and pan support.
 * <p>
 * Handles:
 * <ul>
 *   <li>Scroll wheel zoom (toward mouse position)</li>
 *   <li>Middle-click pan</li>
 *   <li>Checkerboard background</li>
 *   <li>Sprite rendering</li>
 *   <li>Optional grid overlay</li>
 *   <li>Clip rect management</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * renderer.beginPreview(sprite, availWidth, availHeight);
 * // Draw custom overlays using renderer.getDrawX(), getDrawY(), etc.
 * renderer.endPreview();
 * </pre>
 */
public class SpritePreviewRenderer {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    public static final float MIN_ZOOM = 0.5f;
    public static final float MAX_ZOOM = 8.0f;
    public static final float DEFAULT_ZOOM = 2.0f;
    private static final int GRID_SUBDIVISIONS = 4;

    // ========================================================================
    // STATE
    // ========================================================================

    private float zoom = DEFAULT_ZOOM;
    private float panX = 0;
    private float panY = 0;

    private boolean isPanning = false;
    private float panStartX = 0;
    private float panStartY = 0;

    // Current render state (valid between beginPreview/endPreview)
    private float drawX;
    private float drawY;
    private float displayWidth;
    private float displayHeight;
    private float areaWidth;
    private float areaHeight;
    private float spriteWidth;
    private float spriteHeight;
    private ImDrawList drawList;
    private boolean isHovered;

    // Options
    private boolean showGrid = true;

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Gets current zoom level.
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Sets zoom level (clamped to MIN_ZOOM..MAX_ZOOM).
     */
    public void setZoom(float zoom) {
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    /**
     * Resets zoom to default and centers the view.
     */
    public void reset() {
        zoom = DEFAULT_ZOOM;
        panX = 0;
        panY = 0;
    }

    /**
     * Fits the sprite to the preview area.
     *
     * @param sprite The sprite to fit
     * @param areaWidth Available width
     * @param areaHeight Available height
     */
    public void fit(Sprite sprite, float areaWidth, float areaHeight) {
        if (sprite == null) return;
        float fitZoomX = (areaWidth - 20) / sprite.getWidth();
        float fitZoomY = (areaHeight - 20) / sprite.getHeight();
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(fitZoomX, fitZoomY)));
        panX = 0;
        panY = 0;
    }

    /**
     * Resets pan position only.
     */
    public void resetPan() {
        panX = 0;
        panY = 0;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    // ========================================================================
    // PREVIEW RENDERING
    // ========================================================================

    /**
     * Begins rendering the preview area.
     * Creates an invisible button for input capture, handles zoom/pan input,
     * draws background and sprite.
     *
     * @param sprite The sprite to preview (can be null)
     * @param availWidth Available width for the preview
     * @param availHeight Available height for the preview
     * @return true if sprite is valid and preview is active, false otherwise
     */
    public boolean beginPreview(Sprite sprite, float availWidth, float availHeight) {
        this.areaWidth = availWidth;
        this.areaHeight = availHeight;

        if (sprite == null || sprite.getTexture() == null) {
            ImGui.textDisabled("No sprite to preview");
            return false;
        }

        this.spriteWidth = sprite.getWidth();
        this.spriteHeight = sprite.getHeight();
        this.displayWidth = spriteWidth * zoom;
        this.displayHeight = spriteHeight * zoom;

        // Get base cursor position
        ImVec2 cursorPos = ImGui.getCursorScreenPos();

        // Create invisible button for input capture
        ImGui.invisibleButton("##PreviewArea", availWidth, availHeight);
        isHovered = ImGui.isItemHovered();

        // Handle input
        handleZoomInput(cursorPos, availWidth, availHeight);
        handlePanInput(cursorPos);

        // Calculate draw position with pan offset (centered by default)
        float centerOffsetX = (availWidth - displayWidth) / 2;
        float centerOffsetY = (availHeight - displayHeight) / 2;
        drawX = cursorPos.x + centerOffsetX + panX;
        drawY = cursorPos.y + centerOffsetY + panY;

        drawList = ImGui.getWindowDrawList();

        // Clip to preview area
        drawList.pushClipRect(cursorPos.x, cursorPos.y,
                cursorPos.x + availWidth, cursorPos.y + availHeight, true);

        // Draw background
        drawCheckerboard(drawX, drawY, displayWidth, displayHeight);

        // Draw sprite
        int texId = sprite.getTexture().getTextureId();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        drawList.addImage(texId, drawX, drawY, drawX + displayWidth, drawY + displayHeight,
                u0, v1, u1, v0);  // V flipped for OpenGL

        // Draw grid if enabled
        if (showGrid) {
            drawGrid(drawX, drawY, displayWidth, displayHeight);
        }

        return true;
    }

    /**
     * Ends the preview rendering (pops clip rect).
     * Call this after drawing any custom overlays.
     */
    public void endPreview() {
        if (drawList != null) {
            drawList.popClipRect();
        }
    }

    // ========================================================================
    // ACCESSORS (valid between beginPreview/endPreview)
    // ========================================================================

    /**
     * Gets the X coordinate where the sprite is drawn.
     */
    public float getDrawX() {
        return drawX;
    }

    /**
     * Gets the Y coordinate where the sprite is drawn.
     */
    public float getDrawY() {
        return drawY;
    }

    /**
     * Gets the display width of the sprite (sprite width * zoom).
     */
    public float getDisplayWidth() {
        return displayWidth;
    }

    /**
     * Gets the display height of the sprite (sprite height * zoom).
     */
    public float getDisplayHeight() {
        return displayHeight;
    }

    /**
     * Gets the original sprite width in pixels.
     */
    public float getSpriteWidth() {
        return spriteWidth;
    }

    /**
     * Gets the original sprite height in pixels.
     */
    public float getSpriteHeight() {
        return spriteHeight;
    }

    /**
     * Gets the available area width.
     */
    public float getAreaWidth() {
        return areaWidth;
    }

    /**
     * Gets the available area height.
     */
    public float getAreaHeight() {
        return areaHeight;
    }

    /**
     * Gets the ImGui draw list for custom drawing.
     */
    public ImDrawList getDrawList() {
        return drawList;
    }

    /**
     * Returns true if the preview area is hovered.
     */
    public boolean isHovered() {
        return isHovered;
    }

    /**
     * Returns true if currently panning.
     */
    public boolean isPanning() {
        return isPanning;
    }

    // ========================================================================
    // COORDINATE CONVERSION
    // ========================================================================

    /**
     * Converts screen X coordinate to normalized sprite coordinate (0-1).
     */
    public float screenToNormalizedX(float screenX) {
        return (screenX - drawX) / displayWidth;
    }

    /**
     * Converts screen Y coordinate to normalized sprite coordinate (0-1).
     * Note: Y is flipped (0 = bottom, 1 = top).
     */
    public float screenToNormalizedY(float screenY) {
        return 1.0f - (screenY - drawY) / displayHeight;
    }

    /**
     * Converts screen X coordinate to pixel coordinate in sprite.
     */
    public float screenToPixelX(float screenX) {
        return ((screenX - drawX) / displayWidth) * spriteWidth;
    }

    /**
     * Converts screen Y coordinate to pixel coordinate in sprite.
     */
    public float screenToPixelY(float screenY) {
        return ((screenY - drawY) / displayHeight) * spriteHeight;
    }

    /**
     * Converts normalized X (0-1) to screen X.
     */
    public float normalizedToScreenX(float normalizedX) {
        return drawX + normalizedX * displayWidth;
    }

    /**
     * Converts normalized Y (0-1, bottom=0) to screen Y.
     */
    public float normalizedToScreenY(float normalizedY) {
        return drawY + (1.0f - normalizedY) * displayHeight;
    }

    // ========================================================================
    // INPUT HANDLING
    // ========================================================================

    private void handleZoomInput(ImVec2 cursorPos, float availWidth, float availHeight) {
        if (!isHovered) return;

        float scroll = ImGui.getIO().getMouseWheel();
        if (scroll == 0) return;

        float oldZoom = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + scroll * 0.5f));

        // Zoom toward mouse position
        if (zoom != oldZoom) {
            ImVec2 mousePos = ImGui.getMousePos();
            float mouseRelX = mousePos.x - cursorPos.x - availWidth / 2 - panX;
            float mouseRelY = mousePos.y - cursorPos.y - availHeight / 2 - panY;
            float zoomRatio = zoom / oldZoom;
            panX -= mouseRelX * (zoomRatio - 1);
            panY -= mouseRelY * (zoomRatio - 1);
        }
    }

    private void handlePanInput(ImVec2 cursorPos) {
        // Start pan
        if (isHovered && ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
            isPanning = true;
            ImVec2 mousePos = ImGui.getMousePos();
            panStartX = mousePos.x - panX;
            panStartY = mousePos.y - panY;
        }

        // Continue pan
        if (isPanning) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Middle)) {
                ImVec2 mousePos = ImGui.getMousePos();
                panX = mousePos.x - panStartX;
                panY = mousePos.y - panStartY;
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeAll);
            } else {
                isPanning = false;
            }
        }
    }

    // ========================================================================
    // DRAWING UTILITIES
    // ========================================================================

    private void drawCheckerboard(float x, float y, float width, float height) {
        int lightColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f);
        int darkColor = ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f);
        float cellSize = 8;

        int cols = (int) Math.ceil(width / cellSize);
        int rows = (int) Math.ceil(height / cellSize);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean isLight = (row + col) % 2 == 0;
                int color = isLight ? lightColor : darkColor;

                float cellX = x + col * cellSize;
                float cellY = y + row * cellSize;
                float cellW = Math.min(cellSize, x + width - cellX);
                float cellH = Math.min(cellSize, y + height - cellY);

                drawList.addRectFilled(cellX, cellY, cellX + cellW, cellY + cellH, color);
            }
        }
    }

    private void drawGrid(float x, float y, float width, float height) {
        int gridColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.2f);

        // Draw border
        drawList.addRect(x, y, x + width, y + height, gridColor);

        // Draw subdivisions
        for (int i = 1; i < GRID_SUBDIVISIONS; i++) {
            float t = (float) i / GRID_SUBDIVISIONS;
            // Vertical lines
            drawList.addLine(x + t * width, y, x + t * width, y + height, gridColor);
            // Horizontal lines
            drawList.addLine(x, y + t * height, x + width, y + t * height, gridColor);
        }
    }

    /**
     * Draws a sprite boundary rectangle.
     */
    public void drawBoundary() {
        int boundaryColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.3f);
        drawList.addRect(drawX, drawY, drawX + displayWidth, drawY + displayHeight, boundaryColor);
    }
}
