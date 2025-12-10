package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import lombok.Setter;

/**
 * Panel for selecting tiles from the active layer's spritesheet.
 *
 * Features:
 * - Displays tiles as a clickable grid
 * - Highlights selected tile
 * - Shows tile info on hover
 * - Adjustable tile display size
 */
public class TilesetPalettePanel {

    @Setter
    private EditorScene scene;

    @Setter
    private TileBrushTool brushTool;

    /** Size of tile display in pixels */
    private int tileDisplaySize = 32;

    /** Number of columns in the grid (0 = auto-fit) */
    private int columns = 0;

    public void render() {
        if (ImGui.begin("Tileset")) {
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
                ImGui.textDisabled("Layer has no spritesheet");
                ImGui.end();
                return;
            }

            // Layer info
            ImGui.text("Layer: " + activeLayer.getName());
            ImGui.text("Tiles: " + activeLayer.getTileCount());

            ImGui.separator();

            // Tile size slider
            int[] sizeArr = {tileDisplaySize};
            if (ImGui.sliderInt("Tile Size", sizeArr, 16, 64)) {
                tileDisplaySize = sizeArr[0];
            }

            ImGui.separator();

            // Current selection info
            int selectedIndex = brushTool != null ? brushTool.getSelectedTileIndex() : 0;
            ImGui.text("Selected: " + selectedIndex);

            ImGui.separator();

            // Render tile grid
            renderTileGrid(activeLayer, selectedIndex);
        }
        ImGui.end();
    }

    /**
     * Renders the tile selection grid.
     */
    private void renderTileGrid(TilemapLayer layer, int selectedIndex) {
        int tileCount = layer.getTileCount();
        if (tileCount == 0) {
            ImGui.textDisabled("No tiles in spritesheet");
            return;
        }

        // Calculate number of columns based on panel width
        float availableWidth = ImGui.getContentRegionAvailX();
        float tileSlotSize = tileDisplaySize + 4; // tile + padding
        int cols = columns > 0 ? columns : Math.max(1, (int)(availableWidth / tileSlotSize));

        // Reduce item spacing for tight grid
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2, 2);

        var drawList = ImGui.getWindowDrawList();

        for (int i = 0; i < tileCount; i++) {
            Sprite sprite = layer.getSprite(i);
            if (sprite == null || sprite.getTexture() == null) continue;

            // Start new row if needed
            if (i % cols != 0) {
                ImGui.sameLine();
            }

            boolean isSelected = (i == selectedIndex);

            // Get texture and UV coordinates
            int textureId = sprite.getTexture().getTextureId();
            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();

            // Draw tile button
            ImGui.pushID(i);

            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
            }

            // Use ImageButton - returns true when clicked
            if (ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize,
                    u0, v0, u1, v1)) {
                // Tile clicked - select it
                if (brushTool != null) {
                    brushTool.setSelectedTileIndex(i);
                }
            }

            // Draw selection highlight AFTER the button (so we have correct position)
            if (isSelected) {
                ImGui.popStyleColor();
//                ImVec2 itemMin = ImGui.getItemRectMin();
//                ImVec2 itemMax = ImGui.getItemRectMax();
//
//                // Bright orange selection border
//                int selectColor = ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f);
//                float thickness = 3.0f;
//                drawList.addRect(
//                        itemMin.x - thickness,
//                        itemMin.y - thickness,
//                        itemMax.x + thickness,
//                        itemMax.y + thickness,
//                        selectColor,
//                        2.0f, // rounded corners
//                        0,    // flags
//                        thickness
//                );
            }

            ImGui.popID();

            // Tooltip on hover
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("Tile " + i);

                // Show larger preview
                ImGui.image(textureId, 64, 64, u0, v0, u1, v1);
                ImGui.endTooltip();
            }
        }

        ImGui.popStyleVar();
    }
}