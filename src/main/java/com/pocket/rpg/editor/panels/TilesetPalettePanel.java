package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tileset.CreateSpritesheetDialog;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.editor.tools.TileFillTool;
import com.pocket.rpg.editor.tools.TileRectangleTool;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;

import java.util.List;

/**
 * Panel for selecting tiles from available spritesheets.
 * <p>
 * VISUAL SELECTION - selects tiles as they appear on screen, not by spritesheet coordinates
 */
public class TilesetPalettePanel {

    @Setter
    private EditorScene scene;

    @Setter
    private TileBrushTool brushTool;

    @Setter
    private TileFillTool fillTool;

    @Setter
    private TileRectangleTool rectangleTool;

    private String selectedTileset = null;
    private int tileDisplaySize = 32;

    // Visual selection - rectangle in display coordinates
    private boolean isDragging = false;
    private int dragStartDisplayX = -1;
    private int dragStartDisplayY = -1;
    private int dragEndDisplayX = -1;
    private int dragEndDisplayY = -1;

    // Current selection bounds in display coords
    private int selectionMinDisplayX = -1;
    private int selectionMinDisplayY = -1;
    private int selectionMaxDisplayX = -1;
    private int selectionMaxDisplayY = -1;

    // Cache display layout for selection
    private int cachedDisplayCols = -1;

    private final CreateSpritesheetDialog createDialog = new CreateSpritesheetDialog();

    public TilesetPalettePanel() {
        createDialog.setOnCreated(() -> {
            List<String> names = TilesetRegistry.getInstance().getTilesetNames();
            if (!names.isEmpty()) {
                selectedTileset = names.get(names.size() - 1);
            }
        });
    }

    public void render() {
        if (ImGui.begin("Tileset")) {
            // Top section - tileset selector (fixed)
            renderTilesetSelector();
            ImGui.separator();

            // Calculate available height for the scrollable grid
            // Reserve space for: selection info (2 lines) + separator + slider + separator + padding
            float reservedHeight = ImGui.getTextLineHeightWithSpacing() * 2 +
                    ImGui.getStyle().getItemSpacingY() * 2 +
                    ImGui.getFrameHeightWithSpacing() +
                    20; // padding

            float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;

            // Middle section - scrollable tile grid
            ImGui.beginChild("tileGridChild", 0, availableHeight, false,
                    imgui.flag.ImGuiWindowFlags.HorizontalScrollbar);
            renderTileGrid();
            ImGui.endChild();

            ImGui.separator();
            // Bottom section - always visible (no scroll needed)
            renderSelectionInfo();
            ImGui.separator();

            int[] displayArr = {tileDisplaySize};
            if (ImGui.sliderInt("Tile Size", displayArr, 16, 64)) {
                tileDisplaySize = displayArr[0];
            }
        }
        ImGui.end();

        createDialog.render();
    }

    private void renderTilesetSelector() {
        List<String> tilesetNames = TilesetRegistry.getInstance().getTilesetNames();

        if (tilesetNames.isEmpty()) {
            ImGui.textDisabled("No spritesheets found");
            ImGui.textDisabled("Create one or add .spritesheet files");
        }

        if (selectedTileset == null && !tilesetNames.isEmpty()) {
            selectedTileset = tilesetNames.get(0);
        }

        if (selectedTileset != null && !tilesetNames.contains(selectedTileset)) {
            selectedTileset = tilesetNames.isEmpty() ? null : tilesetNames.get(0);
        }

        ImGui.text("Tileset:");

        String displayName = selectedTileset != null ? selectedTileset : "Select...";
        if (ImGui.beginCombo("##tileset", displayName)) {
            for (String name : tilesetNames) {
                boolean isSelected = name.equals(selectedTileset);
                if (ImGui.selectable(name, isSelected)) {
                    selectedTileset = name;
                    clearSelection();
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }

                if (ImGui.isItemHovered()) {
                    TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(name);
                    if (entry != null) {
                        ImGui.setTooltip(entry.getSpriteWidth() + "x" + entry.getSpriteHeight() +
                                " sprites, " + entry.getSpriteSheet().getTotalFrames() + " tiles");
                    }
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        if (ImGui.smallButton("↻")) {
            TilesetRegistry.getInstance().reload();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reload spritesheets");
        }

        ImGui.sameLine();
        if (ImGui.smallButton("+")) {
            createDialog.open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new spritesheet");
        }

        if (selectedTileset != null) {
            TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
            if (entry != null) {
                ImGui.textDisabled(entry.getSpriteWidth() + "x" + entry.getSpriteHeight() +
                        " px, " + entry.getSpriteSheet().getTotalFrames() + " tiles");
            }
        }
    }

    private void renderSelectionInfo() {
        if (brushTool == null) {
            ImGui.textDisabled("No brush tool");
            return;
        }

        TileSelection selection = brushTool.getSelection();
        if (selection == null) {
            ImGui.text("Selection: None");
            ImGui.textDisabled("Click a tile to select");
        } else if (selection.isSingleTile()) {
            ImGui.text("Selection: Tile " + selection.getFirstTileIndex());
        } else {
            ImGui.text("Selection: Pattern " + selection.getWidth() + "x" + selection.getHeight());
            ImGui.textDisabled("Click to stamp pattern");
        }
    }

    private void renderTileGrid() {
        if (selectedTileset == null) {
            ImGui.textDisabled("Select a tileset above");
            return;
        }

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
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

            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();

            boolean isInSelection = isTileInSelection(i, displayCols);

            if (isInSelection) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
            }

            ImGui.pushID(i);

            ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize,
                    u0, v1, u1, v0);

            if (ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                displayX = i % displayCols;
                int displayY = i / displayCols;

                isDragging = true;
                dragStartDisplayX = displayX;
                dragStartDisplayY = displayY;
                dragEndDisplayX = displayX;
                dragEndDisplayY = displayY;

                selectionMinDisplayX = displayX;
                selectionMinDisplayY = displayY;
                selectionMaxDisplayX = displayX;
                selectionMaxDisplayY = displayY;

                cachedDisplayCols = displayCols;
            }

            ImGui.popID();

            if (isInSelection) {
                ImGui.popStyleColor();
                ImGui.popStyleColor();
            }

            if (ImGui.isItemHovered() && !isDragging) {
                int gridX = i % tilesPerRow;
                int gridY = i / tilesPerRow;
                ImGui.beginTooltip();
                ImGui.text("Tile " + i + " (" + gridX + ", " + gridY + ")");
                ImGui.image(textureId, 64, 64, u0, v1, u1, v0);
                ImGui.endTooltip();
            }
        }

        ImGui.popStyleVar();

        if (isDragging && ImGui.isMouseDragging(ImGuiMouseButton.Left, 1.0f)) {
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();

            int hoveredDisplayX = (int) ((mouseX - gridStartX) / tileSlotSize);
            int hoveredDisplayY = (int) ((mouseY - gridStartY) / tileSlotSize);

            hoveredDisplayX = Math.max(0, Math.min(hoveredDisplayX, displayCols - 1));

            int totalRows = (tileCount + displayCols - 1) / displayCols;
            hoveredDisplayY = Math.max(0, Math.min(hoveredDisplayY, totalRows - 1));

            dragEndDisplayX = hoveredDisplayX;
            dragEndDisplayY = hoveredDisplayY;

            // Update selection rectangle in display space
            selectionMinDisplayX = Math.min(dragStartDisplayX, dragEndDisplayX);
            selectionMaxDisplayX = Math.max(dragStartDisplayX, dragEndDisplayX);
            selectionMinDisplayY = Math.min(dragStartDisplayY, dragEndDisplayY);
            selectionMaxDisplayY = Math.max(dragStartDisplayY, dragEndDisplayY);

            cachedDisplayCols = displayCols;
        }

        if (isDragging && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            isDragging = false;
            createSelection();
        }

        if (selectionMinDisplayX >= 0) {
            ImGui.text("Selection: (" + selectionMinDisplayX + "," + selectionMinDisplayY +
                    ") to (" + selectionMaxDisplayX + "," + selectionMaxDisplayY + ") in display");
        }
    }

    private boolean isTileInSelection(int tileIndex, int displayCols) {
        if (selectionMinDisplayX < 0 || cachedDisplayCols < 0) return false;

        // Convert tile index to display coordinates
        int displayX = tileIndex % displayCols;
        int displayY = tileIndex / displayCols;

        return displayX >= selectionMinDisplayX && displayX <= selectionMaxDisplayX &&
                displayY >= selectionMinDisplayY && displayY <= selectionMaxDisplayY;
    }

    private void createSelection() {
        if (brushTool == null || selectedTileset == null) return;
        if (selectionMinDisplayX < 0 || cachedDisplayCols < 0) return;

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
        if (entry == null) return;

        SpriteSheet sheet = entry.getSpriteSheet();
        List<Sprite> allSprites = entry.getSprites();
        int tilesPerRow = sheet.getColumns();

        // Calculate width and height of selection in display space
        int selectionWidth = selectionMaxDisplayX - selectionMinDisplayX + 1;
        int selectionHeight = selectionMaxDisplayY - selectionMinDisplayY + 1;

        // Build arrays to hold the selected tiles
        int[] indices = new int[selectionWidth * selectionHeight];
        Sprite[] selectionSprites = new Sprite[selectionWidth * selectionHeight];

        // Fill in the selection from display rectangle
        for (int displayY = selectionMinDisplayY; displayY <= selectionMaxDisplayY; displayY++) {
            for (int displayX = selectionMinDisplayX; displayX <= selectionMaxDisplayX; displayX++) {
                // Convert display coords to tile index
                int tileIndex = displayY * cachedDisplayCols + displayX;

                // Calculate position in selection array
                int localX = displayX - selectionMinDisplayX;
                int localY = displayY - selectionMinDisplayY;
                int arrayIndex = localY * selectionWidth + localX;

                // Bounds check
                if (tileIndex < allSprites.size()) {
                    indices[arrayIndex] = tileIndex;
                    selectionSprites[arrayIndex] = allSprites.get(tileIndex);
                } else {
                    indices[arrayIndex] = -1;
                    selectionSprites[arrayIndex] = null;
                }
            }
        }

        TileSelection selection = new TileSelection(
                selectedTileset,
                entry.getSpriteWidth(),
                entry.getSpriteHeight(),
                selectionWidth,
                selectionHeight,
                indices,
                selectionSprites
        );

        brushTool.setSelection(selection);
        if (fillTool != null) {
            fillTool.setSelection(selection);
        }
        if (rectangleTool != null) {
            rectangleTool.setSelection(selection);
        }
    }

    private void clearSelection() {
        selectionMinDisplayX = -1;
        selectionMinDisplayY = -1;
        selectionMaxDisplayX = -1;
        selectionMaxDisplayY = -1;
        cachedDisplayCols = -1;

        if (brushTool != null) {
            brushTool.setSelection(null);
        }
    }

    public void setExternalSelection(TileSelection selection) {
        if (selection == null) {
            clearSelection();
            return;
        }

        if (!selection.getTilesetName().equals(selectedTileset)) {
            selectedTileset = selection.getTilesetName();
        }

        // For single tiles from picker, try to highlight in display
        if (selection.isSingleTile() && cachedDisplayCols > 0) {
            int tileIndex = selection.getFirstTileIndex();
            int displayX = tileIndex % cachedDisplayCols;
            int displayY = tileIndex / cachedDisplayCols;

            selectionMinDisplayX = displayX;
            selectionMaxDisplayX = displayX;
            selectionMinDisplayY = displayY;
            selectionMaxDisplayY = displayY;
        } else {
            // For patterns, we can't reliably map back
            clearSelection();
        }
    }
}