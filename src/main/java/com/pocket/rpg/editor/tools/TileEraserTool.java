package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BatchTileCommand;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Eraser tool for removing tiles from the active layer.
 * Supports undo/redo.
 */
public class TileEraserTool implements EditorTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private int eraserSize = 1;

    private boolean isErasing = false;
    private BatchTileCommand currentCommand = null;

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
        if (button == 0) {
            if (scene == null) return;
            TilemapLayer layer = scene.getActiveLayer();
            if (layer == null || layer.isLocked()) return;

            isErasing = true;
            currentCommand = new BatchTileCommand(layer, "Erase");
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
        if (isErasing && currentCommand != null && currentCommand.hasChanges()) {
            UndoManager.getInstance().execute(currentCommand);
        }
        isErasing = false;
        currentCommand = null;
    }

    private void eraseAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        int halfSize = eraserSize / 2;

        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                
                if (currentCommand != null) {
                    currentCommand.recordChange(tx, ty, null);
                }
                layer.clearTile(tx, ty);
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

        int halfSize = eraserSize / 2;

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

        int borderColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.5f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.5f);
    }
}
