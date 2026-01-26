package com.pocket.rpg.editor.panels.spriteeditor;

import com.pocket.rpg.rendering.resources.NineSliceData;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import com.pocket.rpg.resources.SpriteMetadata.PivotData;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;

/**
 * Full texture preview renderer for the Sprite Editor V2.
 * <p>
 * Renders the entire texture with optional overlays:
 * <ul>
 *   <li>Grid overlay for multiple mode spritesheets</li>
 *   <li>Cell selection highlighting</li>
 *   <li>Pivot markers (all sprites or selected only)</li>
 *   <li>9-slice border visualization</li>
 * </ul>
 * <p>
 * Supports zoom/pan interaction and coordinate conversion between:
 * <ul>
 *   <li>Screen coordinates (ImGui cursor position)</li>
 *   <li>Texture pixel coordinates</li>
 *   <li>Cell indices (for multiple mode)</li>
 *   <li>Normalized coordinates within a cell (0-1)</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * if (renderer.beginPreview(texture, metadata, width, height)) {
 *     renderer.drawGridOverlay();
 *     renderer.drawSelectionHighlight(selectedIndex);
 *     renderer.drawAllPivotMarkers(metadata);
 *     renderer.endPreview();
 * }
 * </pre>
 */
public class TexturePreviewRenderer {

    // ========================================================================
    // CONSTANTS
    // ========================================================================

    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 16.0f;
    public static final float DEFAULT_ZOOM = 1.0f;

    // Colors (ABGR format for ImGui)
    private static final int COLOR_GRID = 0x8000FF00;           // Green, 50% alpha
    private static final int COLOR_GRID_DIMMED = 0x4000FF00;    // Green, 25% alpha
    private static final int COLOR_SELECTION = 0xC000FFFF;      // Yellow, 75% alpha
    private static final int COLOR_PIVOT = 0xFFFFFFFF;          // White
    private static final int COLOR_PIVOT_DIMMED = 0x80FFFFFF;   // White, 50% alpha
    private static final int COLOR_NINE_SLICE = 0xC0FF8800;     // Orange, 75% alpha
    private static final int COLOR_CELL_NUMBER = 0xC0FFFFFF;    // White, 75% alpha
    private static final int COLOR_CELL_NUMBER_BG = 0x80000000; // Black, 50% alpha

    private static final float PIVOT_MARKER_SIZE = 12f;
    private static final float PIVOT_MARKER_THICKNESS = 2f;

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
    private Texture texture;
    private SpriteMetadata metadata;
    private float drawX;
    private float drawY;
    private float displayWidth;
    private float displayHeight;
    private float areaX;
    private float areaY;
    private float areaWidth;
    private float areaHeight;
    private ImDrawList drawList;
    private boolean isHovered;

    // Cached grid calculations
    private int gridColumns;
    private int gridRows;
    private int totalCells;

    // ========================================================================
    // PUBLIC API - ZOOM/PAN
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
     * Fits the texture to the preview area.
     *
     * @param texture   The texture to fit
     * @param areaWidth Available width
     * @param areaHeight Available height
     */
    public void fit(Texture texture, float areaWidth, float areaHeight) {
        if (texture == null) return;
        float fitZoomX = (areaWidth - 20) / texture.getWidth();
        float fitZoomY = (areaHeight - 20) / texture.getHeight();
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

    // ========================================================================
    // PREVIEW LIFECYCLE
    // ========================================================================

    /**
     * Begins rendering the preview area.
     * Creates an invisible button for input capture, handles zoom/pan input,
     * draws background and texture.
     *
     * @param texture    The texture to preview (can be null)
     * @param metadata   Sprite metadata (can be null for single mode defaults)
     * @param availWidth Available width for the preview
     * @param availHeight Available height for the preview
     * @return true if texture is valid and preview is active, false otherwise
     */
    public boolean beginPreview(Texture texture, SpriteMetadata metadata, float availWidth, float availHeight) {
        this.texture = texture;
        this.metadata = metadata;
        this.areaWidth = availWidth;
        this.areaHeight = availHeight;

        if (texture == null) {
            ImGui.textDisabled("No texture to preview");
            return false;
        }

        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        this.displayWidth = textureWidth * zoom;
        this.displayHeight = textureHeight * zoom;

        // Calculate grid dimensions
        calculateGridDimensions(textureWidth, textureHeight);

        // Get base cursor position
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        this.areaX = cursorPos.x;
        this.areaY = cursorPos.y;

        // Create invisible button for input capture
        ImGui.invisibleButton("##TexturePreviewArea", availWidth, availHeight);
        isHovered = ImGui.isItemHovered();

        // Handle input
        handleZoomInput(cursorPos, availWidth, availHeight);
        handlePanInput();

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

        // Draw texture (V flipped for OpenGL)
        drawList.addImage(texture.getTextureId(),
                drawX, drawY, drawX + displayWidth, drawY + displayHeight,
                0, 1, 1, 0);

        return true;
    }

    /**
     * Ends the preview rendering (pops clip rect).
     * Call this after drawing any custom overlays.
     */
    public void endPreview() {
        if (drawList != null) {
            drawList.popClipRect();
            drawList = null;
        }
        texture = null;
    }

    // ========================================================================
    // GRID OVERLAY
    // ========================================================================

    /**
     * Draws the grid overlay for multiple mode.
     * Uses grid settings from the metadata.
     */
    public void drawGridOverlay() {
        if (metadata == null || !metadata.isMultiple() || metadata.grid == null) {
            return;
        }
        drawGridOverlay(metadata.grid);
    }

    /**
     * Draws the grid overlay with specified settings.
     *
     * @param grid Grid settings defining sprite boundaries
     */
    public void drawGridOverlay(GridSettings grid) {
        if (grid == null || texture == null) return;

        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();

        float cellW = grid.spriteWidth * zoom;
        float cellH = grid.spriteHeight * zoom;
        float spacingX = grid.spacingX * zoom;
        float spacingY = grid.spacingY * zoom;
        float offsetX = grid.offsetX * zoom;
        float offsetY = grid.offsetY * zoom;

        // Draw vertical lines
        float x = drawX + offsetX;
        for (int col = 0; col <= gridColumns; col++) {
            float lineX = x + col * (cellW + spacingX);
            if (col < gridColumns) {
                // Left edge of cell
                drawList.addLine(lineX, drawY + offsetY, lineX, drawY + offsetY + gridRows * (cellH + spacingY) - spacingY, COLOR_GRID, 1.0f);
                // Right edge of cell
                drawList.addLine(lineX + cellW, drawY + offsetY, lineX + cellW, drawY + offsetY + gridRows * (cellH + spacingY) - spacingY, COLOR_GRID, 1.0f);
            }
        }

        // Draw horizontal lines
        float y = drawY + offsetY;
        for (int row = 0; row <= gridRows; row++) {
            float lineY = y + row * (cellH + spacingY);
            if (row < gridRows) {
                // Top edge of cell
                drawList.addLine(drawX + offsetX, lineY, drawX + offsetX + gridColumns * (cellW + spacingX) - spacingX, lineY, COLOR_GRID, 1.0f);
                // Bottom edge of cell
                drawList.addLine(drawX + offsetX, lineY + cellH, drawX + offsetX + gridColumns * (cellW + spacingX) - spacingX, lineY + cellH, COLOR_GRID, 1.0f);
            }
        }
    }

    /**
     * Draws cell index numbers on the grid.
     */
    public void drawCellNumbers() {
        if (metadata == null || !metadata.isMultiple() || metadata.grid == null) {
            return;
        }
        drawCellNumbers(metadata.grid);
    }

    /**
     * Draws cell index numbers with specified grid settings.
     *
     * @param grid Grid settings
     */
    public void drawCellNumbers(GridSettings grid) {
        if (grid == null || texture == null) return;

        float cellW = grid.spriteWidth * zoom;
        float cellH = grid.spriteHeight * zoom;
        float spacingX = grid.spacingX * zoom;
        float spacingY = grid.spacingY * zoom;
        float offsetX = grid.offsetX * zoom;
        float offsetY = grid.offsetY * zoom;

        // Only draw numbers if cells are large enough
        if (cellW < 20 || cellH < 16) return;

        int index = 0;
        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridColumns; col++) {
                float cellX = drawX + offsetX + col * (cellW + spacingX);
                float cellY = drawY + offsetY + row * (cellH + spacingY);

                String text = String.valueOf(index);
                ImVec2 textSize = new ImVec2();
                ImGui.calcTextSize(textSize, text);

                // Draw background for readability
                float padding = 2;
                drawList.addRectFilled(
                        cellX + padding, cellY + padding,
                        cellX + textSize.x + padding * 3, cellY + textSize.y + padding * 2,
                        COLOR_CELL_NUMBER_BG, 2f);

                // Draw text
                drawList.addText(cellX + padding * 2, cellY + padding, COLOR_CELL_NUMBER, text);

                index++;
            }
        }
    }

    // ========================================================================
    // SELECTION
    // ========================================================================

    /**
     * Hit tests to find which cell is under the given screen coordinates.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return Cell index (0-based), or -1 if no cell hit
     */
    public int hitTestCell(float screenX, float screenY) {
        if (metadata == null || !metadata.isMultiple() || metadata.grid == null) {
            // Single mode - return 0 if within texture bounds
            if (screenX >= drawX && screenX < drawX + displayWidth &&
                    screenY >= drawY && screenY < drawY + displayHeight) {
                return 0;
            }
            return -1;
        }

        GridSettings grid = metadata.grid;
        float cellW = grid.spriteWidth * zoom;
        float cellH = grid.spriteHeight * zoom;
        float spacingX = grid.spacingX * zoom;
        float spacingY = grid.spacingY * zoom;
        float offsetX = grid.offsetX * zoom;
        float offsetY = grid.offsetY * zoom;

        // Convert to local coordinates
        float localX = screenX - drawX - offsetX;
        float localY = screenY - drawY - offsetY;

        if (localX < 0 || localY < 0) return -1;

        // Calculate cell (accounting for spacing)
        float cellPlusSpacingX = cellW + spacingX;
        float cellPlusSpacingY = cellH + spacingY;

        int col = (int) (localX / cellPlusSpacingX);
        int row = (int) (localY / cellPlusSpacingY);

        // Check if within cell bounds (not in spacing)
        float inCellX = localX - col * cellPlusSpacingX;
        float inCellY = localY - row * cellPlusSpacingY;

        if (inCellX > cellW || inCellY > cellH) {
            return -1; // In spacing area
        }

        if (col >= gridColumns || row >= gridRows) {
            return -1;
        }

        int index = row * gridColumns + col;
        return (index < totalCells) ? index : -1;
    }

    /**
     * Draws selection highlight on the specified cell.
     *
     * @param cellIndex Cell index to highlight
     */
    public void drawSelectionHighlight(int cellIndex) {
        if (cellIndex < 0) return;

        float[] rect = cellToScreenRect(cellIndex);
        if (rect == null) return;

        drawList.addRect(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3],
                COLOR_SELECTION, 0, 0, 2.0f);
    }

    // ========================================================================
    // PIVOT OVERLAY
    // ========================================================================

    /**
     * Draws pivot markers for all sprites using metadata.
     *
     * @param selectedIndex Currently selected sprite index (-1 for none)
     */
    public void drawAllPivotMarkers(int selectedIndex) {
        if (metadata == null) return;

        if (metadata.isMultiple() && metadata.grid != null) {
            // Multiple mode - draw all pivots
            for (int i = 0; i < totalCells; i++) {
                PivotData pivot = metadata.getEffectivePivot(i);
                boolean isSelected = (i == selectedIndex);
                drawPivotMarker(i, pivot.x, pivot.y, isSelected);
            }
        } else {
            // Single mode - draw single pivot
            float pivotX = metadata.getPivotXOrDefault();
            float pivotY = metadata.getPivotYOrDefault();
            drawPivotMarker(0, pivotX, pivotY, true);
        }
    }

    /**
     * Draws a pivot marker at the specified position within a cell.
     * Uses same visual style as V1: red circle with white plus sign.
     *
     * @param cellIndex  Cell index
     * @param pivotX     Pivot X (0-1, 0=left, 1=right)
     * @param pivotY     Pivot Y (0-1, 0=bottom, 1=top)
     * @param isSelected Whether this is the selected sprite
     */
    public void drawPivotMarker(int cellIndex, float pivotX, float pivotY, boolean isSelected) {
        float[] screenPos = normalizedToScreen(cellIndex, pivotX, pivotY);
        if (screenPos == null) return;

        float x = screenPos[0];
        float y = screenPos[1];

        if (isSelected) {
            // Selected pivot: full-size red circle with plus sign (V1 style)
            int blackColor = ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f);
            int redColor = ImGui.colorConvertFloat4ToU32(1f, 0.2f, 0.2f, 1f);
            int whiteColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f);

            // Outer black circle (filled)
            drawList.addCircleFilled(x, y, 8, blackColor);
            // Inner red circle (filled)
            drawList.addCircleFilled(x, y, 6, redColor);
            // Black outline
            drawList.addCircle(x, y, 8, blackColor, 0, 2);
            // White plus sign
            drawList.addLine(x - 4, y, x + 4, y, whiteColor, 2);
            drawList.addLine(x, y - 4, x, y + 4, whiteColor, 2);
        } else {
            // Non-selected pivot: smaller, dimmed version
            int blackDimmed = ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.5f);
            int redDimmed = ImGui.colorConvertFloat4ToU32(1f, 0.2f, 0.2f, 0.5f);
            int whiteDimmed = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.5f);

            drawList.addCircleFilled(x, y, 5, blackDimmed);
            drawList.addCircleFilled(x, y, 4, redDimmed);
            drawList.addLine(x - 3, y, x + 3, y, whiteDimmed, 1.5f);
            drawList.addLine(x, y - 3, x, y + 3, whiteDimmed, 1.5f);
        }
    }

    // ========================================================================
    // 9-SLICE OVERLAY
    // ========================================================================

    /**
     * Draws 9-slice borders on the specified cell.
     *
     * @param cellIndex Cell index
     * @param data      9-slice border data
     */
    public void drawNineSliceBorders(int cellIndex, NineSliceData data) {
        if (data == null || !data.hasSlicing()) return;

        float[] rect = cellToScreenRect(cellIndex);
        if (rect == null) return;

        float cellX = rect[0];
        float cellY = rect[1];
        float cellW = rect[2];
        float cellH = rect[3];

        // Scale borders by zoom
        float left = data.left * zoom;
        float right = data.right * zoom;
        float top = data.top * zoom;
        float bottom = data.bottom * zoom;

        // Draw vertical lines (left and right borders)
        if (left > 0) {
            drawList.addLine(cellX + left, cellY, cellX + left, cellY + cellH, COLOR_NINE_SLICE, 1.5f);
        }
        if (right > 0) {
            drawList.addLine(cellX + cellW - right, cellY, cellX + cellW - right, cellY + cellH, COLOR_NINE_SLICE, 1.5f);
        }

        // Draw horizontal lines (top and bottom borders)
        if (top > 0) {
            drawList.addLine(cellX, cellY + top, cellX + cellW, cellY + top, COLOR_NINE_SLICE, 1.5f);
        }
        if (bottom > 0) {
            drawList.addLine(cellX, cellY + cellH - bottom, cellX + cellW, cellY + cellH - bottom, COLOR_NINE_SLICE, 1.5f);
        }
    }

    // ========================================================================
    // COORDINATE CONVERSION
    // ========================================================================

    /**
     * Gets the screen rectangle for a cell.
     *
     * @param cellIndex Cell index
     * @return [x, y, width, height] in screen coordinates, or null if invalid
     */
    public float[] cellToScreenRect(int cellIndex) {
        if (cellIndex < 0) return null;

        if (metadata == null || !metadata.isMultiple() || metadata.grid == null) {
            // Single mode - return full texture rect
            return new float[]{drawX, drawY, displayWidth, displayHeight};
        }

        GridSettings grid = metadata.grid;
        int col = cellIndex % gridColumns;
        int row = cellIndex / gridColumns;

        if (row >= gridRows) return null;

        float cellW = grid.spriteWidth * zoom;
        float cellH = grid.spriteHeight * zoom;
        float spacingX = grid.spacingX * zoom;
        float spacingY = grid.spacingY * zoom;
        float offsetX = grid.offsetX * zoom;
        float offsetY = grid.offsetY * zoom;

        float x = drawX + offsetX + col * (cellW + spacingX);
        float y = drawY + offsetY + row * (cellH + spacingY);

        return new float[]{x, y, cellW, cellH};
    }

    /**
     * Converts screen coordinates to normalized coordinates within a cell.
     *
     * @param screenX   Screen X coordinate
     * @param screenY   Screen Y coordinate
     * @param cellIndex Cell index (determines which cell's bounds to use)
     * @return [normalizedX, normalizedY] where 0,0 is bottom-left and 1,1 is top-right, or null if invalid
     */
    public float[] screenToNormalized(float screenX, float screenY, int cellIndex) {
        float[] rect = cellToScreenRect(cellIndex);
        if (rect == null) return null;

        float normalizedX = (screenX - rect[0]) / rect[2];
        float normalizedY = 1.0f - (screenY - rect[1]) / rect[3]; // Y flipped

        return new float[]{normalizedX, normalizedY};
    }

    /**
     * Converts normalized coordinates within a cell to screen coordinates.
     *
     * @param cellIndex   Cell index
     * @param normalizedX Normalized X (0=left, 1=right)
     * @param normalizedY Normalized Y (0=bottom, 1=top)
     * @return [screenX, screenY], or null if invalid
     */
    public float[] normalizedToScreen(int cellIndex, float normalizedX, float normalizedY) {
        float[] rect = cellToScreenRect(cellIndex);
        if (rect == null) return null;

        float screenX = rect[0] + normalizedX * rect[2];
        float screenY = rect[1] + (1.0f - normalizedY) * rect[3]; // Y flipped

        return new float[]{screenX, screenY};
    }

    /**
     * Converts screen coordinates to texture pixel coordinates.
     *
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @return [pixelX, pixelY] in texture coordinates, or null if outside texture
     */
    public float[] screenToPixel(float screenX, float screenY) {
        if (texture == null) return null;

        float pixelX = (screenX - drawX) / zoom;
        float pixelY = (screenY - drawY) / zoom;

        if (pixelX < 0 || pixelX >= texture.getWidth() ||
                pixelY < 0 || pixelY >= texture.getHeight()) {
            return null;
        }

        return new float[]{pixelX, pixelY};
    }

    /**
     * Converts texture pixel coordinates to screen coordinates.
     *
     * @param pixelX Pixel X in texture
     * @param pixelY Pixel Y in texture
     * @return [screenX, screenY]
     */
    public float[] pixelToScreen(float pixelX, float pixelY) {
        return new float[]{
                drawX + pixelX * zoom,
                drawY + pixelY * zoom
        };
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

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

    /**
     * Gets the ImGui draw list for custom drawing.
     * Only valid between beginPreview/endPreview.
     */
    public ImDrawList getDrawList() {
        return drawList;
    }

    /**
     * Gets the X coordinate where the texture is drawn.
     */
    public float getDrawX() {
        return drawX;
    }

    /**
     * Gets the Y coordinate where the texture is drawn.
     */
    public float getDrawY() {
        return drawY;
    }

    /**
     * Gets the display width of the texture (texture width * zoom).
     */
    public float getDisplayWidth() {
        return displayWidth;
    }

    /**
     * Gets the display height of the texture (texture height * zoom).
     */
    public float getDisplayHeight() {
        return displayHeight;
    }

    /**
     * Gets the number of grid columns (1 for single mode).
     */
    public int getGridColumns() {
        return gridColumns;
    }

    /**
     * Gets the number of grid rows (1 for single mode).
     */
    public int getGridRows() {
        return gridRows;
    }

    /**
     * Gets the total number of cells (1 for single mode).
     */
    public int getTotalCells() {
        return totalCells;
    }

    /**
     * Gets the preview area X coordinate.
     */
    public float getAreaX() {
        return areaX;
    }

    /**
     * Gets the preview area Y coordinate.
     */
    public float getAreaY() {
        return areaY;
    }

    /**
     * Gets the preview area width.
     */
    public float getAreaWidth() {
        return areaWidth;
    }

    /**
     * Gets the preview area height.
     */
    public float getAreaHeight() {
        return areaHeight;
    }

    // ========================================================================
    // INTERNAL METHODS
    // ========================================================================

    private void calculateGridDimensions(int textureWidth, int textureHeight) {
        if (metadata != null && metadata.isMultiple() && metadata.grid != null) {
            GridSettings grid = metadata.grid;
            gridColumns = grid.calculateColumns(textureWidth);
            gridRows = grid.calculateRows(textureHeight);
            totalCells = gridColumns * gridRows;
        } else {
            gridColumns = 1;
            gridRows = 1;
            totalCells = 1;
        }
    }

    private void handleZoomInput(ImVec2 cursorPos, float availWidth, float availHeight) {
        if (!isHovered) return;

        float scroll = ImGui.getIO().getMouseWheel();
        if (scroll == 0) return;

        float oldZoom = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (1 + scroll * 0.15f)));

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

    private void handlePanInput() {
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
}
