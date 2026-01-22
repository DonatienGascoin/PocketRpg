package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Picker tool (eyedropper) for selecting tiles from the canvas.
 *
 * Features:
 * - Left click: Pick single tile and update palette
 * - Shift + drag: Pick multi-tile pattern
 * - Visual selection rectangle during drag
 */
public class TilePickerTool implements EditorTool {

    @Setter
    private EditorScene scene;

    /** Callback to update brush tool's selection */
    @Setter
    private TileSelectionCallback onTilesPicked;

    // Pattern picking state
    private boolean isPickingPattern = false;
    private int startX, startY;
    private int endX, endY;

    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    public TilePickerTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public String getName() {
        return "Picker";
    }

    @Override
    public String getShortcutKey() {
        return "I";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) { // Left click
            if (ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift)) {
                // Start pattern picking
                isPickingPattern = true;
                startX = tileX;
                startY = tileY;
                endX = tileX;
                endY = tileY;
            } else {
                // Pick single tile
                pickSingleTile(tileX, tileY);
            }
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        if (isPickingPattern && button == 0) {
            // Update end position
            endX = tileX;
            endY = tileY;
        }
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        if (isPickingPattern && button == 0) {
            // Pick the pattern
            pickPattern();
            isPickingPattern = false;
        }
    }

    /**
     * Picks a single tile at the given position.
     */
    private void pickSingleTile(int tileX, int tileY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null) {
            System.out.println("Cannot pick: no active layer");
            return;
        }

        TilemapRenderer tilemap = layer.getTilemap();
        TilemapRenderer.Tile tile = tilemap.get(tileX, tileY);

        if (tile == null || tile.sprite() == null) {
            System.out.println("Cannot pick: no tile at position");
            return;
        }

        Sprite sprite = tile.sprite();

        // Find the tileset and sprite index for this sprite
        // We need to search through all tilesets to find which one contains this sprite
        String tilesetName = findTilesetForSprite(sprite);
        if (tilesetName == null) {
            System.out.println("Cannot pick: sprite not found in any tileset");
            return;
        }

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(tilesetName);
        if (entry == null) return;

        // Find sprite index in tileset
        int spriteIndex = entry.getSprites().indexOf(sprite);
        if (spriteIndex < 0) {
            System.out.println("Cannot pick: sprite not found in tileset");
            return;
        }

        // Create single-tile selection
        TileSelection selection = new TileSelection(
                tilesetName,
                entry.getSpriteWidth(),
                entry.getSpriteHeight(),
                spriteIndex,
                sprite
        );

        // Notify callback
        if (onTilesPicked != null) {
            onTilesPicked.onTilesPicked(selection);
        }

        System.out.println("Picked single tile from (" + tileX + ", " + tileY + ")");
    }

    /**
     * Finds which tileset contains the given sprite.
     */
    private String findTilesetForSprite(Sprite sprite) {
        for (String tilesetName : TilesetRegistry.getInstance().getTilesetNames()) {
            TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(tilesetName);
            if (entry != null && entry.getSprites().contains(sprite)) {
                return tilesetName;
            }
        }
        return null;
    }

    /**
     * Picks a multi-tile pattern from the defined rectangle.
     */
    private void pickPattern() {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null) return;

        // Calculate bounds
        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minY = Math.min(startY, endY);
        int maxY = Math.max(startY, endY);

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;

        TilemapRenderer tilemap = layer.getTilemap();

        // First pass: collect all sprites and find which tileset they belong to
        String tilesetName = null;
        TilesetRegistry.TilesetEntry entry = null;

        // We need to find a common tileset for all sprites
        // For simplicity, use the tileset of the first non-null sprite
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int worldX = minX + dx;
                int worldY = maxY - dy; // Flip Y

                TilemapRenderer.Tile tile = tilemap.get(worldX, worldY);
                if (tile != null && tile.sprite() != null) {
                    tilesetName = findTilesetForSprite(tile.sprite());
                    if (tilesetName != null) {
                        entry = TilesetRegistry.getInstance().getTileset(tilesetName);
                        break;
                    }
                }
            }
            if (tilesetName != null) break;
        }

        if (tilesetName == null || entry == null) {
            System.out.println("Cannot pick pattern: no valid tiles in selection");
            return;
        }

        // Create arrays for the pattern
        int[] tileIndices = new int[width * height];
        Sprite[] sprites = new Sprite[width * height];

        // Fill the arrays
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int worldX = minX + dx;
                int worldY = maxY - dy; // Flip Y

                TilemapRenderer.Tile tile = tilemap.get(worldX, worldY);
                int arrayIndex = dy * width + dx;

                if (tile != null && tile.sprite() != null) {
                    Sprite sprite = tile.sprite();
                    sprites[arrayIndex] = sprite;

                    // Find sprite index in the tileset
                    int spriteIndex = entry.getSprites().indexOf(sprite);
                    tileIndices[arrayIndex] = spriteIndex >= 0 ? spriteIndex : -1;
                } else {
                    sprites[arrayIndex] = null;
                    tileIndices[arrayIndex] = -1;
                }
            }
        }

        // Create pattern selection
        TileSelection selection = new TileSelection(
                tilesetName,
                entry.getSpriteWidth(),
                entry.getSpriteHeight(),
                width,
                height,
                tileIndices,
                sprites
        );

        // Notify callback
        if (onTilesPicked != null) {
            onTilesPicked.onTilesPicked(selection);
        }

        System.out.println("Picked pattern " + width + "x" + height + " from (" + minX + ", " + minY + ")");
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        if (isPickingPattern) {
            // Draw pattern selection rectangle
            int minX = Math.min(startX, endX);
            int maxX = Math.max(startX, endX);
            int minY = Math.min(startY, endY);
            int maxY = Math.max(startY, endY);

            // Fill
            int fillColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 0.3f);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    drawTileHighlight(drawList, camera, x, y, fillColor, false);
                }
            }

            // Border
            int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.9f, 0.4f, 0.9f);
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
            // Check if shift is pressed for pattern-pick mode indicator
            boolean shiftPressed = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

            if (shiftPressed) {
                // Pattern-pick mode: magenta/purple color
                int fillColor = ImGui.colorConvertFloat4ToU32(0.9f, 0.4f, 0.9f, 0.4f);
                int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 1.0f, 0.9f);
                drawTileHighlightWithBorder(drawList, camera, hoveredTileX, hoveredTileY, fillColor, borderColor, 2.0f);
            } else {
                // Normal picker cursor
                int color = ImGui.colorConvertFloat4ToU32(1.0f, 0.8f, 0.3f, 0.6f);
                drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color, true);
            }
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
            int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.9f, 0.4f, 0.8f);
            drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.5f);
        }
    }

    /**
     * Draws a highlight rectangle for a tile with custom border color and thickness.
     */
    private void drawTileHighlightWithBorder(ImDrawList drawList, EditorCamera camera,
                                              int tileX, int tileY, int fillColor,
                                              int borderColor, float borderThickness) {
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
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, borderThickness);
    }

    /**
     * Callback interface for notifying when tiles are picked.
     */
    public interface TileSelectionCallback {
        void onTilesPicked(TileSelection selection);
    }
}