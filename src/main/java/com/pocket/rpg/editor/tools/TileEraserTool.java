package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Eraser tool for removing tiles from the active layer.
 *
 * Features:
 * - Left click to erase
 * - Adjustable eraser size
 * - Preview of affected tiles (red highlight)
 */
public class TileEraserTool implements EditorTool {

    @Setter
    private EditorScene scene;

    /** Eraser size (1 = single tile, 2 = 2x2, etc.) */
    @Getter
    @Setter
    private int eraserSize = 1;

    // Erasing state
    private boolean isErasing = false;

    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    public TileEraserTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public String getName() {
        return "Eraser";
    }

    @Override
    public String getShortcutKey() {
        return "E";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) { // Left click
            isErasing = true;
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isErasing) return;

        if (button == 0) {
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        isErasing = false;
        // TODO: Commit undo command here
    }

    /**
     * Erases tiles at the given position using current eraser size.
     */
    private void eraseAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        int halfSize = eraserSize / 2;

        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                layer.clearTile(tx, ty);
            }
        }

        scene.markDirty();
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        // Use foreground draw list with clipping
        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        int halfSize = eraserSize / 2;

        // Draw eraser preview (red highlight for affected tiles)
        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;

                boolean isCenter = (dx == 0 && dy == 0);
                int color = isCenter
                        ? ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.4f, 0.5f)
                        : ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 0.3f);

                drawTileHighlight(drawList, camera, tx, ty, color);
            }
        }

        drawList.popClipRect();
    }

    /**
     * Draws a highlight rectangle for a tile.
     */
    private void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                   int tileX, int tileY, int fillColor) {
        // Convert tile corners to screen coordinates
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        // Add viewport offset to get absolute screen position
        float x1 = viewportX + bottomLeft.x;
        float y1 = viewportY + topRight.y;
        float x2 = viewportX + topRight.x;
        float y2 = viewportY + bottomLeft.y;

        // Ensure correct ordering
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.5f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.5f);
    }
}