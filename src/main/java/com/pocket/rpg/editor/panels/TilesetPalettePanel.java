package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.panels.tilesets.TileGridRenderer;
import com.pocket.rpg.editor.panels.tilesets.TileSelectionManager;
import com.pocket.rpg.editor.panels.tilesets.TilesetSelector;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tools.TileBrushTool;
import com.pocket.rpg.editor.tools.TileEraserTool;
import com.pocket.rpg.editor.tools.TileFillTool;
import com.pocket.rpg.editor.tools.TileRectangleTool;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;

public class TilesetPalettePanel {
    private final ToolManager toolManager;

    @Setter
    private TileBrushTool brushTool;

    @Setter
    private TileEraserTool eraserTool;

    @Setter
    private TileFillTool fillTool;

    @Setter
    private TileRectangleTool rectangleTool;

    private final TilesetSelector tilesetSelector;
    private final TileSelectionManager selectionManager;
    private final TileGridRenderer gridRenderer;
    private boolean isHorizontalLayout = false;

    public TilesetPalettePanel(ToolManager toolManager) {
        this.toolManager = toolManager;
        this.tilesetSelector = new TilesetSelector();
        this.selectionManager = new TileSelectionManager();
        this.gridRenderer = new TileGridRenderer(selectionManager);

        tilesetSelector.setOnTilesetChanged(tileset -> selectionManager.clearSelection());
        selectionManager.setOnSelectionCreated(this::onSelectionCreated);
    }

    public void render() {
        if (ImGui.begin("Tileset")) {
            if (ImGui.isWindowFocused() && ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                selectionManager.clearSelection();
                clearToolSelection();
            }

            syncSelectionWithTool();

            if (isHorizontalLayout) {
                renderHorizontal();
            } else {
                renderVertical();
            }
        }
        ImGui.end();

        tilesetSelector.renderDialogs();
    }

    private void renderVertical() {
        tilesetSelector.renderSelector();
        ImGui.separator();

        float reservedHeight = ImGui.getTextLineHeightWithSpacing() * 3 +
                ImGui.getStyle().getItemSpacingY() * 3 +
                ImGui.getFrameHeightWithSpacing() * 2 + 30;

        float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;

        ImGui.beginChild("tileGridChild", 0, availableHeight, false,
                imgui.flag.ImGuiWindowFlags.HorizontalScrollbar);
        gridRenderer.render(tilesetSelector.getSelectedTileset());
        ImGui.endChild();

        ImGui.separator();
        renderSelectionInfo();
        renderToolSizeSlider();
        ImGui.separator();
        gridRenderer.renderSizeSlider();
    }

    private void renderHorizontal() {
        if (ImGui.beginTable("tilesetTable", 2, ImGuiTableFlags.BordersInnerV)) {

            ImGui.tableSetupColumn(
                    "Left",
                    ImGuiTableColumnFlags.WidthFixed,
                    ImGui.getWindowWidth() * 0.33f
            );
            ImGui.tableSetupColumn("Right");

            // ===== LEFT COLUMN =====
            ImGui.tableNextColumn();

            tilesetSelector.renderSelector();
            ImGui.separator();
            renderSelectionInfo();
            renderToolSizeSlider();
            ImGui.separator();
            gridRenderer.renderSizeSlider();

            // ===== RIGHT COLUMN =====
            ImGui.tableNextColumn();

            // Full-height scrollable grid ONLY
            ImGui.beginChild(
                    "TileGridScroll",
                    0,
                    0,
                    false,
                    ImGuiWindowFlags.HorizontalScrollbar
            );
            gridRenderer.render(tilesetSelector.getSelectedTileset());
            ImGui.endChild();

            ImGui.endTable();
        }
    }


    private void syncSelectionWithTool() {
        if (!selectionManager.isDragging() && brushTool != null && brushTool.getSelection() == null) {
            selectionManager.clearSelection();
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

    private void renderToolSizeSlider() {
        if (brushTool == null) return;

        // Don't show size for pattern selections
        TileSelection selection = brushTool.getSelection();
        if (selection != null && selection.isPattern()) return;

        int[] size = {brushTool.getBrushSize()};
        if (ImGui.sliderInt("Tool Size", size, 1, 10)) {
            brushTool.setBrushSize(size[0]);
            if (eraserTool != null) {
                eraserTool.setEraserSize(size[0]);
            }
        }
    }

    private void onSelectionCreated(TileSelection selection) {
        if (brushTool != null) brushTool.setSelection(selection);
        if (fillTool != null) fillTool.setSelection(selection);
        if (rectangleTool != null) rectangleTool.setSelection(selection);

        var activeTool = toolManager.getActiveTool();
        if (activeTool != rectangleTool && activeTool != brushTool) {
            toolManager.setActiveTool(brushTool);
        }
    }

    private void clearToolSelection() {
        if (brushTool != null) brushTool.setSelection(null);
        if (fillTool != null) fillTool.setSelection(null);
        if (rectangleTool != null) rectangleTool.setSelection(null);
    }

    public void clearSelection() {
        selectionManager.clearSelection();
        clearToolSelection();
    }

    public void setExternalSelection(TileSelection selection) {
        if (selection == null) {
            clearSelection();
            return;
        }

        if (!selection.getTilesetName().equals(tilesetSelector.getSelectedTileset())) {
            tilesetSelector.setSelectedTileset(selection.getTilesetName());
        }

        selectionManager.setExternalSelection(selection);
    }

    public void setHorizontalLayout(boolean horizontal) {
        this.isHorizontalLayout = horizontal;
    }

    public boolean isHorizontalLayout() {
        return isHorizontalLayout;
    }
}