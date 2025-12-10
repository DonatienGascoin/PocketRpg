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
 * Brush tool for painting tiles on the active layer.
 *
 * Features:
 * - Left click to paint
 * - Right click to erase (convenience)
 * - Adjustable brush size
 * - Preview of affected tiles
 */
public class TileBrushTool implements EditorTool {

    @Setter
    private EditorScene scene;

    /** Currently selected tile index to paint */
    @Getter
    @Setter
    private int selectedTileIndex = 0;

    /** Brush size (1 = single tile, 2 = 2x2, etc.) */
    @Getter
    @Setter
    private int brushSize = 1;

    // Painting state
    private boolean isPainting = false;

    // For overlay rendering - set by SceneViewport
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    public TileBrushTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public String getName() {
        return "Brush";
    }

    @Override
    public String getShortcutKey() {
        return "B";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) { // Left click - paint
            isPainting = true;
            paintAt(tileX, tileY);
        } else if (button == 1) { // Right click - erase
            isPainting = true;
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (!isPainting) return;

        if (button == 0) {
            paintAt(tileX, tileY);
        } else if (button == 1) {
            eraseAt(tileX, tileY);
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        isPainting = false;
        // TODO: Commit undo command here
    }

    /**
     * Paints tiles at the given position using current brush size.
     */
    private void paintAt(int centerX, int centerY) {
        if (scene == null) {
            System.out.println("Cannot paint: scene is null");
            return;
        }

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null) {
            System.out.println("Cannot paint: no active layer");
            return;
        }
        if (layer.isLocked()) {
            System.out.println("Cannot paint: layer is locked");
            return;
        }
        if (layer.getSpriteSheet() == null) {
            System.out.println("Cannot paint: layer has no spritesheet");
            return;
        }

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                layer.setTile(tx, ty, selectedTileIndex);
            }
        }

        // Debug: verify tile was set
        System.out.println("Painted tile at (" + centerX + ", " + centerY + ") with index " + selectedTileIndex);
        System.out.println("  Layer: " + layer.getName() + " (zIndex=" + layer.getZIndex() + ")");
        System.out.println("  Tilemap chunks: " + layer.getTilemap().allChunks().size());

        // Verify the tile is actually in the tilemap
        var tile = layer.getTilemap().get(centerX, centerY);
        if (tile != null) {
            System.out.println("  Tile verified: sprite=" + (tile.sprite() != null ? tile.sprite().getName() : "null"));
        } else {
            System.out.println("  WARNING: Tile not found after setting!");
        }

        scene.markDirty();
    }

    /**
     * Erases tiles at the given position using current brush size.
     */
    private void eraseAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
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

        // Use window draw list with clipping to viewport bounds
        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        int halfSize = brushSize / 2;

        // Draw brush preview (highlight affected tiles)
        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;

                boolean isCenter = (dx == 0 && dy == 0);
                int color = isCenter
                        ? ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.5f)
                        : ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 0.3f);

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
        // Bottom-left of tile in world space is (tileX, tileY)
        // Top-right of tile in world space is (tileX + 1, tileY + 1)
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        // Add viewport offset to get absolute screen position
        float x1 = viewportX + bottomLeft.x;
        float y1 = viewportY + topRight.y;   // topRight.y is smaller (screen Y increases downward)
        float x2 = viewportX + topRight.x;
        float y2 = viewportY + bottomLeft.y; // bottomLeft.y is larger

        // Ensure correct ordering (x1 < x2, y1 < y2)
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.5f);
    }
}