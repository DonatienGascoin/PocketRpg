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
    
    private final EditorScene scene;
    
    /** Eraser size (1 = single tile, 2 = 2x2, etc.) */
    @Getter
    @Setter
    private int eraserSize = 1;
    
    // Erasing state
    private boolean isErasing = false;
    
    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    
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
        if (hoveredTileX == Integer.MIN_VALUE) return;
        
        ImDrawList drawList = ImGui.getBackgroundDrawList();
        
        int halfSize = eraserSize / 2;
        
        // Draw eraser preview (red highlight for affected tiles)
        for (int dy = -halfSize; dy < eraserSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < eraserSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;
                
                drawTileHighlight(drawList, camera, tx, ty,
                    ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.3f, 0.4f));
            }
        }
        
        // Draw center tile with stronger highlight
        drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY,
            ImGui.colorConvertFloat4ToU32(1.0f, 0.4f, 0.4f, 0.6f));
    }
    
    /**
     * Draws a highlight rectangle for a tile.
     */
    private void drawTileHighlight(ImDrawList drawList, EditorCamera camera,
                                   int tileX, int tileY, int color) {
        // Convert tile corners to screen coordinates
        Vector2f topLeft = camera.worldToScreen(tileX, tileY + 1);
        Vector2f bottomRight = camera.worldToScreen(tileX + 1, tileY);
        
        float x1 = viewportX + topLeft.x;
        float y1 = viewportY + topLeft.y;
        float x2 = viewportX + bottomRight.x;
        float y2 = viewportY + bottomRight.y;
        
        drawList.addRectFilled(x1, y1, x2, y2, color);
        drawList.addRect(x1, y1, x2, y2,
            ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.5f, 0.8f), 0, 0, 1.0f);
    }
}
