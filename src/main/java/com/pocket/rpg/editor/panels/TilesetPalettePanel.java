package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

/**
 * ImGui panel for selecting tiles from the active layer's spritesheet.
 * 
 * Displays tiles in a grid. Click to select a tile for painting.
 * 
 * Phase 3a: Basic implementation - shows tiles from active layer's spritesheet
 * Phase 3b: Will add multi-tile selection, spritesheet switching, etc.
 */
public class TilesetPalettePanel {
    
    @Setter
    private EditorScene scene;
    
    @Setter
    private TileBrushTool brushTool;
    
    /** Currently selected tile index */
    @Getter
    private int selectedTileIndex = 0;
    
    /** Size of each tile button in the palette (pixels) */
    private float tileButtonSize = 32f;
    
    /** Padding between tile buttons */
    private float tilePadding = 2f;
    
    /**
     * Renders the tileset palette panel.
     */
    public void render() {
        if (ImGui.begin("Tileset Palette")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }
            
            TilemapLayer activeLayer = scene.getActiveLayer();
            if (activeLayer == null) {
                ImGui.textDisabled("No layer selected");
                ImGui.textDisabled("Add a layer in the Layers panel");
                ImGui.end();
                return;
            }
            
            SpriteSheet sheet = activeLayer.getSpriteSheet();
            if (sheet == null) {
                ImGui.textDisabled("Layer has no tileset");
                ImGui.end();
                return;
            }
            
            // Header
            ImGui.text("Layer: " + activeLayer.getName());
            ImGui.text("Tiles: " + sheet.getTotalFrames() + 
                " (" + sheet.getColumns() + "x" + sheet.getRows() + ")");
            
            // Tile size slider
            float[] size = {tileButtonSize};
            if (ImGui.sliderFloat("Tile Size", size, 16f, 64f, "%.0f")) {
                tileButtonSize = size[0];
            }
            
            ImGui.separator();
            
            // Selected tile info
            ImGui.text("Selected: " + selectedTileIndex);
            
            ImGui.separator();
            
            // Tile grid
            renderTileGrid(sheet, activeLayer);
        }
        ImGui.end();
    }
    
    /**
     * Renders the tile selection grid.
     */
    private void renderTileGrid(SpriteSheet sheet, TilemapLayer layer) {
        // Calculate columns based on available width
        float availableWidth = ImGui.getContentRegionAvailX();
        int columns = Math.max(1, (int) (availableWidth / (tileButtonSize + tilePadding)));
        
        int totalTiles = sheet.getTotalFrames();
        
        for (int i = 0; i < totalTiles; i++) {
            Sprite sprite = layer.getSprite(i);
            if (sprite == null) continue;
            
            // Start new row if needed
            if (i % columns != 0) {
                ImGui.sameLine(0, tilePadding);
            }
            
            ImGui.pushID(i);
            
            // Determine if this tile is selected
            boolean isSelected = (i == selectedTileIndex);
            
            // Style selected tile differently
            if (isSelected) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.3f, 0.6f, 1.0f, 1.0f);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.9f, 1.0f);
            }
            
            // Render tile as image button
            int textureId = sprite.getTexture().getTextureId();
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();
            
            // Note: ImGui expects v0 < v1 (top to bottom), but our sprites might have
            // v0 > v1 due to OpenGL's coordinate system. Check and swap if needed.
            float actualV0 = Math.min(v0, v1);
            float actualV1 = Math.max(v0, v1);
            
            if (ImGui.imageButton("tile" + i, textureId, tileButtonSize, tileButtonSize,
                    u0, actualV0, u1, actualV1)) {
                selectedTileIndex = i;
                
                // Update brush tool
                if (brushTool != null) {
                    brushTool.setSelectedTileIndex(i);
                }
            }
            
            if (isSelected) {
                ImGui.popStyleColor(3);
            }
            
            // Tooltip with tile info
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("Tile " + i);
                ImGui.text(String.format("UV: (%.2f, %.2f) - (%.2f, %.2f)", u0, v0, u1, v1));
                ImGui.endTooltip();
            }
            
            ImGui.popID();
        }
    }
    
    /**
     * Sets the selected tile index.
     */
    public void setSelectedTileIndex(int index) {
        this.selectedTileIndex = index;
        if (brushTool != null) {
            brushTool.setSelectedTileIndex(index);
        }
    }
}
