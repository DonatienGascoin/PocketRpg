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
    
    private final EditorScene scene;
    
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
    
    // For overlay rendering
    @Setter
    private float viewportX, viewportY;
    
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
        TilemapLayer layer = scene.getActiveLayer();
        if (layer == null || layer.isLocked()) return;
        if (layer.getSpriteSheet() == null) return;
        
        int halfSize = brushSize / 2;
        
        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = centerX + dx;
                int ty = centerY + dy;
                layer.setTile(tx, ty, selectedTileIndex);
            }
        }
        
        scene.markDirty();
    }
    
    /**
     * Erases tiles at the given position using current brush size.
     */
    private void eraseAt(int centerX, int centerY) {
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
        if (hoveredTileX == Integer.MIN_VALUE) return;
        
        ImDrawList drawList = ImGui.getBackgroundDrawList();
        
        int halfSize = brushSize / 2;
        
        // Draw brush preview (highlight affected tiles)
        for (int dy = -halfSize; dy < brushSize - halfSize; dy++) {
            for (int dx = -halfSize; dx < brushSize - halfSize; dx++) {
                int tx = hoveredTileX + dx;
                int ty = hoveredTileY + dy;
                
                drawTileHighlight(drawList, camera, tx, ty, 
                    ImGui.colorConvertFloat4ToU32(0.2f, 0.6f, 1.0f, 0.4f));
            }
        }
        
        // Draw center tile with stronger highlight
        drawTileHighlight(drawList, camera, hoveredTileX, hoveredTileY,
            ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.6f));
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
            ImGui.colorConvertFloat4ToU32(0.4f, 0.8f, 1.0f, 0.8f), 0, 0, 1.0f);
    }
}
