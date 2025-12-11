package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tileset.CreateSpritesheetDialog;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import lombok.Setter;

import java.util.List;

/**
 * Panel for selecting tiles from available spritesheets.
 *
 * Features:
 * - Tileset dropdown (loaded from .spritesheet files)
 * - Create new spritesheet button
 * - Displays tiles as a clickable grid
 * - Single click to select one tile
 * - Drag to select multi-tile pattern
 * - Shows selection info in brush tool
 */
public class TilesetPalettePanel {

    @Setter
    private EditorScene scene;

    @Setter
    private TileBrushTool brushTool;

    /** Currently selected tileset name */
    private String selectedTileset = null;

    /** Size of tile display in pixels */
    private int tileDisplaySize = 32;

    // Multi-select state
    private boolean isDragging = false;
    private int dragStartX = -1;
    private int dragStartY = -1;
    private int dragEndX = -1;
    private int dragEndY = -1;

    // Current selection highlight
    private int selectionStartX = -1;
    private int selectionStartY = -1;
    private int selectionEndX = -1;
    private int selectionEndY = -1;

    // Create spritesheet dialog
    private final CreateSpritesheetDialog createDialog = new CreateSpritesheetDialog();

    public TilesetPalettePanel() {
        // Set callback when new spritesheet is created
        createDialog.setOnCreated(() -> {
            // Select the newly created tileset
            List<String> names = TilesetRegistry.getInstance().getTilesetNames();
            if (!names.isEmpty()) {
                selectedTileset = names.get(names.size() - 1);
            }
        });
    }

    public void render() {
        if (ImGui.begin("Tileset")) {
            // Tileset dropdown and controls
            renderTilesetSelector();

            ImGui.separator();

            // Selection info
            renderSelectionInfo();

            ImGui.separator();

            // Display size slider
            int[] displayArr = {tileDisplaySize};
            if (ImGui.sliderInt("Tile Size", displayArr, 16, 64)) {
                tileDisplaySize = displayArr[0];
            }

            ImGui.separator();

            // Tile grid
            renderTileGrid();
        }
        ImGui.end();

        // Render create dialog (if open)
        createDialog.render();
    }

    /**
     * Renders the tileset dropdown selector.
     */
    private void renderTilesetSelector() {
        List<String> tilesetNames = TilesetRegistry.getInstance().getTilesetNames();

        if (tilesetNames.isEmpty()) {
            ImGui.textDisabled("No spritesheets found");
            ImGui.textDisabled("Create one or add .spritesheet files");
        }

        // Auto-select first tileset if none selected
        if (selectedTileset == null && !tilesetNames.isEmpty()) {
            selectedTileset = tilesetNames.get(0);
        }

        // Validate selection still exists
        if (selectedTileset != null && !tilesetNames.contains(selectedTileset)) {
            selectedTileset = tilesetNames.isEmpty() ? null : tilesetNames.get(0);
        }

        ImGui.text("Tileset:");

        // Dropdown
        String displayName = selectedTileset != null ? selectedTileset : "Select...";
        if (ImGui.beginCombo("##tileset", displayName)) {
            for (String name : tilesetNames) {
                boolean isSelected = name.equals(selectedTileset);
                if (ImGui.selectable(name, isSelected)) {
                    selectedTileset = name;
                    // Clear selection when changing tileset
                    clearSelection();
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }

                // Tooltip with details
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

        // Buttons row
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

        // Show current tileset info
        if (selectedTileset != null) {
            TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
            if (entry != null) {
                ImGui.textDisabled(entry.getSpriteWidth() + "x" + entry.getSpriteHeight() +
                        " px, " + entry.getSpriteSheet().getTotalFrames() + " tiles");
            }
        }
    }

    /**
     * Renders current selection info.
     */
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

    /**
     * Renders the tile selection grid.
     */
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

        // Calculate grid layout
        float availableWidth = ImGui.getContentRegionAvailX();
        float tileSlotSize = tileDisplaySize + 2;
        int displayCols = Math.max(1, (int)(availableWidth / tileSlotSize));
        displayCols = Math.min(displayCols, tilesPerRow);

        // Store grid start position BEFORE rendering tiles
        float gridStartX = ImGui.getCursorScreenPosX();
        float gridStartY = ImGui.getCursorScreenPosY();

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 1, 1);

        for (int i = 0; i < tileCount; i++) {
            Sprite sprite = sprites.get(i);
            if (sprite == null) continue;

            int gridX = i % tilesPerRow;
            int gridY = i / tilesPerRow;

            int displayX = i % displayCols;
            if (displayX != 0) {
                ImGui.sameLine();
            }

            float u0 = sprite.getU0();
            float v0 = sprite.getV0();
            float u1 = sprite.getU1();
            float v1 = sprite.getV1();

            boolean isInSelection = isTileInSelection(gridX, gridY);

            if (isInSelection) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(1.0f, 0.5f, 0.0f, 1.0f));
            }

            ImGui.pushID(i);

            ImGui.imageButton("##tile", textureId, tileDisplaySize, tileDisplaySize,
                    u0, v1, u1, v0);

            // Start drag on click
            if (ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                isDragging = true;
                dragStartX = gridX;
                dragStartY = gridY;
                dragEndX = gridX;
                dragEndY = gridY;
            }

            ImGui.popID();

            if (isInSelection) {
                ImGui.popStyleColor();
            }

            // Tooltip
            if (ImGui.isItemHovered() && !isDragging) {
                ImGui.beginTooltip();
                ImGui.text("Tile " + i + " (" + gridX + ", " + gridY + ")");
                ImGui.image(textureId, 64, 64, u0, v1, u1, v0);
                ImGui.endTooltip();
            }
        }

        ImGui.popStyleVar();

        // Calculate hovered tile from mouse position (works during drag)
        if (isDragging && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            float mouseX = ImGui.getMousePosX();
            float mouseY = ImGui.getMousePosY();

            // Calculate which tile the mouse is over based on position
            int hoveredDisplayX = (int) ((mouseX - gridStartX) / tileSlotSize);
            int hoveredDisplayY = (int) ((mouseY - gridStartY) / tileSlotSize);

            // Clamp to valid range
            hoveredDisplayX = Math.max(0, Math.min(hoveredDisplayX, displayCols - 1));

            int totalRows = (tileCount + displayCols - 1) / displayCols;
            hoveredDisplayY = Math.max(0, Math.min(hoveredDisplayY, totalRows - 1));

            // Convert display position back to grid position in spritesheet
            // Display layout may differ from spritesheet layout if displayCols != tilesPerRow
            int hoveredIndex = hoveredDisplayY * displayCols + hoveredDisplayX;
            hoveredIndex = Math.max(0, Math.min(hoveredIndex, tileCount - 1));

            int hoveredGridX = hoveredIndex % tilesPerRow;
            int hoveredGridY = hoveredIndex / tilesPerRow;

            dragEndX = hoveredGridX;
            dragEndY = hoveredGridY;

            // Update preview during drag
            selectionStartX = Math.min(dragStartX, dragEndX);
            selectionStartY = Math.min(dragStartY, dragEndY);
            selectionEndX = Math.max(dragStartX, dragEndX);
            selectionEndY = Math.max(dragStartY, dragEndY);
        }

        // Handle drag end
        if (isDragging && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            isDragging = false;

            selectionStartX = Math.min(dragStartX, dragEndX);
            selectionStartY = Math.min(dragStartY, dragEndY);
            selectionEndX = Math.max(dragStartX, dragEndX);
            selectionEndY = Math.max(dragStartY, dragEndY);

            createSelection();
        }

        // Debug display
        if (selectionStartX >= 0) {
            ImGui.text("Selection: (" + selectionStartX + "," + selectionStartY +
                    ") to (" + selectionEndX + "," + selectionEndY + ")");
        }
        if (isDragging) {
            ImGui.text("Dragging: (" + dragStartX + "," + dragStartY +
                    ") to (" + dragEndX + "," + dragEndY + ")");
        }
    }

    /**
     * Checks if a tile grid position is within the current selection.
     */
    private boolean isTileInSelection(int gridX, int gridY) {
        if (selectionStartX < 0) return false;

        return gridX >= selectionStartX && gridX <= selectionEndX &&
                gridY >= selectionStartY && gridY <= selectionEndY;
    }

    /**
     * Creates a TileSelection from the current selection and passes to brush.
     */
    private void createSelection() {
        System.out.println("createSelection called: " +
                selectionStartX + "," + selectionStartY + " to " +
                selectionEndX + "," + selectionEndY);
        if (brushTool == null || selectedTileset == null) return;
        if (selectionStartX < 0) return;

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
        if (entry == null) return;

        TileSelection selection = new TileSelection.Builder(
                selectedTileset,
                entry.getSpriteWidth(),
                entry.getSpriteHeight())
                .setRegion(selectionStartX, selectionStartY, selectionEndX, selectionEndY)
                .build();

        if (selection != null) {
            brushTool.setSelection(selection);
        }

        if (selection != null) {
            System.out.println("Created selection: " + selection.getWidth() + "x" + selection.getHeight());
            brushTool.setSelection(selection);
        } else {
            System.out.println("Selection is null!");
        }
    }

    /**
     * Clears the current selection.
     */
    private void clearSelection() {
        selectionStartX = -1;
        selectionStartY = -1;
        selectionEndX = -1;
        selectionEndY = -1;

        if (brushTool != null) {
            brushTool.setSelection(null);
        }
    }
}