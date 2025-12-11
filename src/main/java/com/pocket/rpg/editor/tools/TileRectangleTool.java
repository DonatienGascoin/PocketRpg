package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.rendering.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Rectangle tool for filling rectangular regions with tiles.
 *
 * Features:
 * - Click and drag to define rectangle
 * - Visual preview during drag
 * - Fill on release
 */
public class TileRectangleTool implements EditorTool {

    @Setter
    private EditorScene scene;

    @Setter
    private TileSelection selection;

    // Rectangle state
    private boolean isDefiningRect = false;
    private int startX, startY;
    private int endX, endY;

    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    public TileRectangleTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public String getName() {
        return "Rectangle";
    }

    @Override
    public String getShortcutKey() {
        return "R";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) { // Left click - start rectangle
            isDefiningRect = true;
            startX = tileX;
            startY = tileY;
            endX = tileX;
            endY = tileY;
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (isDefiningRect && button == 0) {
            // Update end position
            endX = tileX;
            endY = tileY;
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (isDefiningRect && button == 0) {
            // Fill the rectangle
            fillRectangle();
            isDefiningRect = false;
        }
    }

    /**
     * Fills the defined rectangle with the selected sprite.
     */
    private void fillRectangle() {
        if (scene == null) {
            System.out.println("Cannot fill rectangle: scene is null");
            return;
        }

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null) {
            System.out.println("Cannot fill rectangle: no active layer");
            return;
        }
        if (layer.isLocked()) {
            System.out.println("Cannot fill rectangle: layer is locked");
            return;
        }

        if (selection == null || selection.getFirstSprite() == null) {
            System.out.println("Cannot fill rectangle: no sprite selected");
            return;
        }

        Sprite fillSprite = selection.getFirstSprite();

        // Calculate bounds
        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = Math.min(startY, endY);
        int maxY = Math.max(startY, endY);

        // Fill all tiles in rectangle
        TilemapRenderer tilemap = layer.getTilemap();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                tilemap.set(x, y, new TilemapRenderer.Tile(fillSprite));
            }
        }

        scene.markDirty();
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        if (isDefiningRect) {
            // Draw rectangle preview
            int minX = Math.min(startX, endX);
            int maxX = Math.max(startX, endX);
            int minY = Math.min(startY, endY);
            int maxY = Math.max(startY, endY);

            // Fill
            int fillColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.3f);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    drawTileHighlight(drawList, camera, x, y, fillColor, false);
                }
            }

            // Border
            int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.9f);
            Vector2f bottomLeft = camera.worldToScreen(minX, minY);
            Vector2f topRight = camera.worldToScreen(maxX + 1, maxY + 1);

            float x1 = viewportX + bottomLeft.x;
            float y1 = viewportY + topRight.y;
            float x2 = viewportX + topRight.x;
            float y2 = viewportY + bottomLeft.y;

            float rectMinX = Math.min(x1, x2);
            float rectMaxX = Math.max(x1, x2);
            float rectMinY = Math.min(y1, y2);
            float rectMaxY = Math.max(y1, y2);

            drawList.addRect(rectMinX, rectMinY, rectMaxX, rectMaxY, borderColor, 0, 0, 2.0f);

        } else if (hoveredTileX != Integer.MIN_VALUE && hoveredTileY != Integer.MIN_VALUE) {
            // Draw single tile preview when not dragging
            int color = ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.4f);
            drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color, true);
        }

        drawList.popClipRect();
    }

    /**
     * Draws a highlight rectangle for a tile.
     */
    private void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                   int tileX, int tileY, int fillColor, boolean drawBorder) {
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

        if (drawBorder) {
            int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f);
            drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
        }
    }
}