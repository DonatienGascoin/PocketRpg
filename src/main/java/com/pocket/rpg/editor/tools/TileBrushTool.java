package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.rendering.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Brush tool for painting tiles on the active layer.
 *
 * Features:
 * - Paint single tiles or multi-tile patterns
 * - Left click to paint
 * - Right click to erase (convenience)
 * - Adjustable brush size (for single tile mode)
 * - Preview of affected tiles
 */
public class TileBrushTool implements EditorTool {

    @Setter
    private EditorScene scene;

    /** Current tile selection (single or pattern) */
    @Getter
    @Setter
    private TileSelection selection;

    /** Brush size for single-tile mode (1 = single tile, 2 = 2x2, etc.) */
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

    /**
     * Gets the selected tile index (for single-tile selections).
     * @deprecated Use getSelection() instead
     */
    @Deprecated
    public int getSelectedTileIndex() {
        return selection != null ? selection.getFirstTileIndex() : 0;
    }

    /**
     * Sets a single tile selection by index.
     * @deprecated Use setSelection() instead
     */
    @Deprecated
    public void setSelectedTileIndex(int index) {
        // This is now handled by TilesetPalettePanel creating a TileSelection
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
     * Paints tiles at the given position.
     * Uses pattern if multi-tile selection, otherwise uses brush size.
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

        if (selection == null) {
            System.out.println("Cannot paint: no tile selected");
            return;
        }System.out.println("paintAt: selection=" + selection +
                ", isPattern=" + (selection != null ? selection.isPattern() : "null"));

        if (selection.isPattern()) {
            System.out.println("Calling paintPattern");
            // Paint pattern
            paintPattern(layer, centerX, centerY);
        } else {
            // Paint single tile with brush size
            paintSingleWithSize(layer, centerX, centerY);
        }

        scene.markDirty();
    }

    /**
     * Paints a multi-tile pattern at the given position.
     * The centerX/centerY becomes the top-left of the pattern.
     */
    private void paintPattern(TilemapLayer layer, int startX, int startY) {
        for (int py = 0; py < selection.getHeight(); py++) {
            for (int px = 0; px < selection.getWidth(); px++) {
                Sprite sprite = selection.getSprite(px, py);
                if (sprite != null) {
                    int tileX = startX + px;
                    int tileY = startY + (selection.getHeight() - 1 - py); // Flip Y for world coords

                    // Set tile directly with sprite
                    layer.getTilemap().set(tileX, tileY, new TilemapRenderer.Tile(sprite));
                }
            }
        }
    }

    /**
     * Paints a single tile with brush size.
     */
    private void paintSingleWithSize(TilemapLayer layer, int centerX, int centerY) {
        Sprite sprite = selection.getFirstSprite();
        if (sprite == null) return;

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                layer.getTilemap().set(tx, ty, new TilemapRenderer.Tile(sprite));
            }
        }
    }

    /**
     * Erases tiles at the given position using current brush/pattern size.
     */
    private void eraseAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        int eraseWidth = (selection != null && selection.isPattern()) ? selection.getWidth() : brushSize;
        int eraseHeight = (selection != null && selection.isPattern()) ? selection.getHeight() : brushSize;

        int halfW = eraseWidth / 2;
        int halfH = eraseHeight / 2;

        // For patterns, erase from top-left like painting
        if (selection != null && selection.isPattern()) {
            for (int dy = 0; dy < eraseHeight; dy++) {
                for (int dx = 0; dx < eraseWidth; dx++) {
                    int tx = centerX + dx;
                    int ty = centerY + (eraseHeight - 1 - dy);
                    layer.getTilemap().clear(tx, ty);
                }
            }
        } else {
            // Single tile with brush size
            for (int dy = -halfH; dy < eraseHeight - halfH; dy++) {
                for (int dx = -halfW; dx < eraseWidth - halfW; dx++) {
                    int tx = centerX + dx;
                    int ty = centerY + dy;
                    layer.getTilemap().clear(tx, ty);
                }
            }
        }

        scene.markDirty();
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Determine overlay size
        int overlayWidth, overlayHeight;
        if (selection != null && selection.isPattern()) {
            overlayWidth = selection.getWidth();
            overlayHeight = selection.getHeight();
        } else {
            overlayWidth = brushSize;
            overlayHeight = brushSize;
        }

        // Draw preview
        if (selection != null && selection.isPattern()) {
            // Pattern preview - from top-left
            for (int py = 0; py < overlayHeight; py++) {
                for (int px = 0; px < overlayWidth; px++) {
                    int tx = hoveredTileX + px;
                    int ty = hoveredTileY + (overlayHeight - 1 - py);

                    boolean hasTile = selection.getSprite(px, py) != null;
                    int color = hasTile
                            ? ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.4f)
                            : ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.6f, 0.2f);

                    drawTileHighlight(drawList, camera, tx, ty, color);
                }
            }
        } else {
            // Single tile with brush size - centered
            int halfSize = brushSize / 2;
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
        }

        drawList.popClipRect();
    }

    /**
     * Draws a highlight rectangle for a tile.
     */
    private void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                   int tileX, int tileY, int fillColor) {
        Vector2f bottomLeft = camera.worldToScreen(tileX, tileY);
        Vector2f topRight = camera.worldToScreen(tileX + 1, tileY + 1);

        float x1 = viewportX + bottomLeft.x;
        float y1 = viewportY + topRight.y;
        float x2 = viewportX + topRight.x;
        float y2 = viewportY + bottomLeft.y;

        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        float minY = Math.min(y1, y2);
        float maxY = Math.max(y1, y2);

        drawList.addRectFilled(minX, minY, maxX, maxY, fillColor);

        int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }
}