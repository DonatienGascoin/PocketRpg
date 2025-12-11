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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Fill tool for flood-filling tiles on the active layer.
 *
 * Features:
 * - Left click to flood fill matching tiles
 * - Safety limit of 2000 tiles
 * - Preview of hovered tile
 */
public class TileFillTool implements EditorTool {

    private static final int MAX_FILL_TILES = 2000;

    @Setter
    private EditorScene scene;

    @Setter
    private TileSelection selection;

    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    @Setter
    private float viewportWidth, viewportHeight;

    public TileFillTool(EditorScene scene) {
        this.scene = scene;
    }

    @Override
    public String getName() {
        return "Fill";
    }

    @Override
    public String getShortcutKey() {
        return "F";
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) { // Left click - flood fill
            floodFill(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
        // No drag behavior for fill tool
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
        // TODO: Commit undo command here
    }

    /**
     * Performs flood fill starting at the given tile position.
     */
    private void floodFill(int startX, int startY) {
        if (scene == null) {
            System.out.println("Cannot fill: scene is null");
            return;
        }

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null) {
            System.out.println("Cannot fill: no active layer");
            return;
        }
        if (layer.isLocked()) {
            System.out.println("Cannot fill: layer is locked");
            return;
        }

        if (selection == null || selection.getFirstSprite() == null) {
            System.out.println("Cannot fill: no sprite selected");
            return;
        }

        Sprite fillSprite = selection.getFirstSprite();
        TilemapRenderer tilemap = layer.getTilemap();

        // Get the sprite at start position to match
        TilemapRenderer.Tile startTile = tilemap.get(startX, startY);
        Sprite targetSprite = (startTile != null) ? startTile.sprite() : null;

        // If clicking on same sprite, do nothing
        if (targetSprite == fillSprite) {
            return;
        }

        // Flood fill using BFS
        Queue<TilePos> queue = new LinkedList<>();
        Set<TilePos> visited = new HashSet<>();

        queue.add(new TilePos(startX, startY));
        visited.add(new TilePos(startX, startY));

        int fillCount = 0;

        while (!queue.isEmpty() && fillCount < MAX_FILL_TILES) {
            TilePos pos = queue.poll();

            // Get current tile sprite
            TilemapRenderer.Tile currentTile = tilemap.get(pos.x, pos.y);
            Sprite currentSprite = (currentTile != null) ? currentTile.sprite() : null;

            // Check if matches target
            if (currentSprite != targetSprite) {
                continue;
            }

            // Fill this tile
            tilemap.set(pos.x, pos.y, new TilemapRenderer.Tile(fillSprite));
            fillCount++;

            // Add neighbors
            addNeighbor(queue, visited, pos.x + 1, pos.y);
            addNeighbor(queue, visited, pos.x - 1, pos.y);
            addNeighbor(queue, visited, pos.x, pos.y + 1);
            addNeighbor(queue, visited, pos.x, pos.y - 1);
        }

        if (fillCount >= MAX_FILL_TILES) {
            System.out.println("Fill stopped at safety limit: " + MAX_FILL_TILES + " tiles");
        }

        scene.markDirty();
    }

    /**
     * Adds a neighbor tile to the queue if not visited.
     */
    private void addNeighbor(Queue<TilePos> queue, Set<TilePos> visited, int x, int y) {
        TilePos pos = new TilePos(x, y);
        if (!visited.contains(pos)) {
            queue.add(pos);
            visited.add(pos);
        }
    }

    @Override
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (hoveredTileX == Integer.MIN_VALUE || hoveredTileY == Integer.MIN_VALUE) return;
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        ImDrawList drawList = ImGui.getForegroundDrawList();
        drawList.pushClipRect(viewportX, viewportY, viewportX + viewportWidth, viewportY + viewportHeight, true);

        // Draw fill preview (bucket icon would be better, but simple highlight for now)
        int color = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.3f, 0.5f);
        drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color);

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

        int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.4f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.5f);
    }

    /**
     * Simple tile position class for flood fill.
     */
    private static class TilePos {
        final int x, y;

        TilePos(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TilePos)) return false;
            TilePos tilePos = (TilePos) o;
            return x == tilePos.x && y == tilePos.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }
}