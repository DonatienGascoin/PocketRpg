package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BatchTileCommand;
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
 * Supports undo/redo.
 */
public class TileFillTool implements EditorTool {

    private static final int MAX_FILL_TILES = 2000;

    @Setter
    private EditorScene scene;

    @Setter
    private TileSelection selection;

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
        if (button == 0) {
            floodFill(tileX, tileY);
        }
    }

    @Override
    public void onMouseDrag(int tileX, int tileY, int button) {
    }

    @Override
    public void onMouseUp(int tileX, int tileY, int button) {
    }

    private void floodFill(int startX, int startY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        if (selection == null || selection.getFirstSprite() == null) return;

        Sprite fillSprite = selection.getFirstSprite();
        TilemapRenderer tilemap = layer.getTilemap();

        TilemapRenderer.Tile startTile = tilemap.get(startX, startY);
        Sprite targetSprite = (startTile != null) ? startTile.sprite() : null;

        if (targetSprite == fillSprite) return;

        // Create undo command
        BatchTileCommand command = new BatchTileCommand(layer, "Fill");

        Queue<TilePos> queue = new LinkedList<>();
        Set<TilePos> visited = new HashSet<>();

        queue.add(new TilePos(startX, startY));
        visited.add(new TilePos(startX, startY));

        int fillCount = 0;
        TilemapRenderer.Tile newTile = new TilemapRenderer.Tile(fillSprite);

        while (!queue.isEmpty() && fillCount < MAX_FILL_TILES) {
            TilePos pos = queue.poll();

            TilemapRenderer.Tile currentTile = tilemap.get(pos.x, pos.y);
            Sprite currentSprite = (currentTile != null) ? currentTile.sprite() : null;

            if (currentSprite != targetSprite) continue;

            // Record and apply change
            command.recordChange(pos.x, pos.y, newTile);
            tilemap.set(pos.x, pos.y, newTile);
            fillCount++;

            addNeighbor(queue, visited, pos.x + 1, pos.y);
            addNeighbor(queue, visited, pos.x - 1, pos.y);
            addNeighbor(queue, visited, pos.x, pos.y + 1);
            addNeighbor(queue, visited, pos.x, pos.y - 1);
        }

        if (command.hasChanges()) {
            UndoManager.getInstance().execute(command);
        }

        scene.markDirty();
    }

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

        int color = ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.3f, 0.5f);
        drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color);

        drawList.popClipRect();
    }

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
