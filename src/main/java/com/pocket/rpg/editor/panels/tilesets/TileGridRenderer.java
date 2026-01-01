package com.pocket.rpg.editor.panels.tilesets;

import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;

import java.util.List;

public class TileGridRenderer {

    private final TileSelectionManager selectionManager;
    private int tileDisplaySize = 32;

    public TileGridRenderer(TileSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    public void render(String tilesetName) {
        if (tilesetName == null) {
            ImGui.textDisabled("Select a tileset above");
            return;
        }

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(tilesetName);
        if (entry == null) {
            ImGui.textDisabled("Tileset not found");
            return;
        }

        SpriteSheet sheet = entry.getSpriteSheet();
        List<Sprite> sprites = entry.getSprites();

        if (sprites == null || sprites.isEmpty()) {
            ImGui.textDisabled("No tiles in spritesheet");
            return;
        }

        int textureId = entry.getTexture().getTextureId();
        int tilesPerRow = sheet.getColumns();
        int tileCount = sheet.getTotalFrames();

        float availableWidth = ImGui.getContentRegionAvailX();
        float tileSlotSize = tileDisplaySize + 2;
        int displayCols = Math.max(1, (int) (availableWidth / tileSlotSize));
        displayCols = Math.min(displayCols, tilesPerRow);

        float gridStartX = ImGui.getCursorScreenPosX();
        float gridStartY = ImGui.getCursorScreenPosY();

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 1, 1);

        for (int i = 0; i < tileCount; i++) {
            Sprite sprite = sprites.get(i);
            if (sprite == null) continue;

            int displayX = i % displayCols;
            if (displayX != 0) {
                ImGui.sameLine();
            }

            renderTileButton(i, sprite, textureId, displayCols, tilesPerRow);
        }

        ImGui.popStyleVar();

        handleDragLogic(gridStartX, gridStartY, tileSlotSize, displayCols, tileCount, tilesetName);

        if (selectionManager.hasSelection()) {
            ImGui.text(selectionManager.getSelectionDebugInfo());
        }
    }

    private void renderTileButton(int tileIndex, Sprite sprite, int textureId, int displayCols, int tilesPerRow) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        boolean isInSelection = selectionManager.isTileInSelection(tileIndex, displayCols);

        if (isInSelection) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
        }

        ImGui.pushID(tileIndex);
        ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize, u0, v1, u1, v0);

        if (ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            int displayX = tileIndex % displayCols;
            int displayY = tileIndex / displayCols;
            selectionManager.startDrag(displayX, displayY, displayCols);
        }

        ImGui.popID();

        if (isInSelection) {
            ImGui.popStyleColor(2);
        }

        if (ImGui.isItemHovered() && !selectionManager.isDragging()) {
            int gridX = tileIndex % tilesPerRow;
            int gridY = tileIndex / tilesPerRow;
            ImGui.beginTooltip();
            ImGui.text("Tile " + tileIndex + " (" + gridX + ", " + gridY + ")");
            ImGui.image(textureId, 64, 64, u0, v1, u1, v0);
            ImGui.endTooltip();
        }
    }

    private void handleDragLogic(float gridStartX, float gridStartY, float tileSlotSize,
                                 int displayCols, int tileCount, String tilesetName) {
        if (selectionManager.isDragging() && ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f)) {
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();

            int hoveredDisplayX = (int) ((mouseX - gridStartX) / tileSlotSize);
            int hoveredDisplayY = (int) ((mouseY - gridStartY) / tileSlotSize);

            hoveredDisplayX = Math.max(0, Math.min(hoveredDisplayX, displayCols - 1));

            int totalRows = (tileCount + displayCols - 1) / displayCols;
            hoveredDisplayY = Math.max(0, Math.min(hoveredDisplayY, totalRows - 1));

            selectionManager.updateDrag(hoveredDisplayX, hoveredDisplayY);
        }

        if (selectionManager.isDragging() && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            selectionManager.endDrag(tilesetName);
        }
    }

    public void renderSizeSlider() {
        int[] displayArr = {tileDisplaySize};
        if (ImGui.sliderInt("Tile Size", displayArr, 16, 64)) {
            tileDisplaySize = displayArr[0];
        }
    }
}