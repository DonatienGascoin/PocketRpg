package com.pocket.rpg.editor.tools;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BatchTileCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Brush tool for painting tiles on the active layer.
 * Supports undo/redo for all paint operations.
 */
public class TileBrushTool implements EditorTool {

    @Setter
    private EditorScene scene;

    @Getter
    @Setter
    private TileSelection selection;

    @Getter
    @Setter
    private int brushSize = 1;

    private boolean isPainting = false;
    private BatchTileCommand currentCommand = null;

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

    @Deprecated
    public int getSelectedTileIndex() {
        return selection != null ? selection.getFirstTileIndex() : 0;
    }

    @Deprecated
    public void setSelectedTileIndex(int index) {
    }

    @Override
    public void onMouseDown(int tileX, int tileY, int button) {
        if (scene == null) return;
        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        if (button == 0) {
            isPainting = true;
            currentCommand = new BatchTileCommand(layer, "Paint");
            paintAt(tileX, tileY);
        } else if (button == 1) {
            isPainting = true;
            currentCommand = new BatchTileCommand(layer, "Erase");
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
        if (isPainting && currentCommand != null && currentCommand.hasChanges()) {
            // Add to undo stack - command knows to skip first execute()
            UndoManager.getInstance().execute(currentCommand);
        }
        isPainting = false;
        currentCommand = null;
    }

    private void paintAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        if (selection == null) return;

        if (selection.isPattern()) {
            paintPattern(layer, centerX, centerY);
        } else {
            paintSingleWithSize(layer, centerX, centerY);
        }

        scene.markDirty();
    }

    private void paintPattern(TilemapLayer layer, int startX, int startY) {
        for (int py = 0; py < selection.getHeight(); py++) {
            for (int px = 0; px < selection.getWidth(); px++) {
                Sprite sprite = selection.getSprite(px, py);
                if (sprite != null) {
                    int tileX = startX + px;
                    int tileY = startY + (selection.getHeight() - 1 - py);

                    TilemapRenderer.Tile newTile = new TilemapRenderer.Tile(sprite);
                    
                    if (currentCommand != null) {
                        currentCommand.recordChange(tileX, tileY, newTile);
                    }
                    layer.getTilemap().set(tileX, tileY, newTile);
                }
            }
        }
    }

    private void paintSingleWithSize(TilemapLayer layer, int centerX, int centerY) {
        Sprite sprite = selection.getFirstSprite();
        if (sprite == null) return;

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                
                TilemapRenderer.Tile newTile = new TilemapRenderer.Tile(sprite);
                
                if (currentCommand != null) {
                    currentCommand.recordChange(tx, ty, newTile);
                }
                layer.getTilemap().set(tx, ty, newTile);
            }
        }
    }

    private void eraseAt(int centerX, int centerY) {
        if (scene == null) return;

        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;

        int eraseWidth = (selection != null && selection.isPattern()) ? selection.getWidth() : brushSize;
        int eraseHeight = (selection != null && selection.isPattern()) ? selection.getHeight() : brushSize;

        int halfW = eraseWidth / 2;
        int halfH = eraseHeight / 2;

        if (selection != null && selection.isPattern()) {
            for (int dy = 0; dy < eraseHeight; dy++) {
                for (int dx = 0; dx < eraseWidth; dx++) {
                    int tx = centerX + dx;
                    int ty = centerY + (eraseHeight - 1 - dy);
                    
                    if (currentCommand != null) {
                        currentCommand.recordChange(tx, ty, null);
                    }
                    layer.getTilemap().clear(tx, ty);
                }
            }
        } else {
            for (int dy = -halfH; dy < eraseHeight - halfH; dy++) {
                for (int dx = -halfW; dx < eraseWidth - halfW; dx++) {
                    int tx = centerX + dx;
                    int ty = centerY + dy;
                    
                    if (currentCommand != null) {
                        currentCommand.recordChange(tx, ty, null);
                    }
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

        if (selection != null && selection.isPattern()) {
            renderPatternGhost(drawList, camera, hoveredTileX, hoveredTileY);
        } else if (selection != null) {
            renderSingleTileGhost(drawList, camera, hoveredTileX, hoveredTileY);
        } else {
            int color = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.3f);
            drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY, color);
        }

        drawList.popClipRect();
    }

    private void renderPatternGhost(ImDrawList drawList, EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        int textureId = getSelectionTextureId();

        for (int py = 0; py < selection.getHeight(); py++) {
            for (int px = 0; px < selection.getWidth(); px++) {
                Sprite sprite = selection.getSprite(px, py);
                int tx = hoveredTileX + px;
                int ty = hoveredTileY + (selection.getHeight() - 1 - py);

                if (sprite != null && textureId > 0) {
                    drawGhostSprite(drawList, camera, tx, ty, sprite, textureId);
                } else {
                    int color = ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.6f, 0.2f);
                    drawTileHighlight(drawList, camera, tx, ty, color);
                }
            }
        }
    }

    private void renderSingleTileGhost(ImDrawList drawList, EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        Sprite sprite = selection.getFirstSprite();
        int textureId = getSelectionTextureId();

        int halfSize = brushSize / 2;

        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;

                if (sprite != null && textureId > 0) {
                    drawGhostSprite(drawList, camera, tx, ty, sprite, textureId);
                }
            }
        }
    }

    private void drawGhostSprite(ImDrawList drawList, EditorCamera camera,
                                 int tileX, int tileY, Sprite sprite, int textureId) {
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

        float u0 = sprite.getU0();
        float v0 = sprite.getV1();
        float u1 = sprite.getU1();
        float v1 = sprite.getV0();

        int ghostColor = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.5f);
        drawList.addImage(textureId, minX, minY, maxX, maxY, u0, v0, u1, v1, ghostColor);

        int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.6f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }

    private int getSelectionTextureId() {
        if (selection == null) return 0;

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selection.getTilesetName());
        if (entry == null || entry.getTexture() == null) return 0;

        return entry.getTexture().getTextureId();
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

        int borderColor = ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f);
        drawList.addRect(minX, minY, maxX, maxY, borderColor, 0, 0, 1.0f);
    }
}
