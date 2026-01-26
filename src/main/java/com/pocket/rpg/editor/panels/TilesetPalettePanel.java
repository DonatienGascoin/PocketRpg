package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.SelectionChangedEvent;
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
import com.pocket.rpg.editor.scene.TilemapLayer;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;

import java.util.List;

/**
 * Panel for selecting and managing tilesets and tile selections.
 * When open, tile painting tools become available.
 */
public class TilesetPalettePanel extends EditorPanel {

    private static final String PANEL_ID = "tilesetPalette";

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

    // Scene and selection manager for layer selector and bidirectional sync
    @Setter
    private EditorScene scene;

    private EditorSelectionManager editorSelectionManager;

    public TilesetPalettePanel(ToolManager toolManager) {
        super(PANEL_ID, false); // Default closed - painting panel
        this.toolManager = toolManager;
        this.tilesetSelector = new TilesetSelector();
        this.selectionManager = new TileSelectionManager();
        this.gridRenderer = new TileGridRenderer(selectionManager);

        tilesetSelector.setOnTilesetChanged(tileset -> selectionManager.clearSelection());
        selectionManager.setOnSelectionCreated(this::onSelectionCreated);
    }

    @Override
    public void render() {
        if (!isOpen()) {
            setContentVisible(false);
            return;
        }

        boolean visible = ImGui.begin("Tileset Palette");
        setContentVisible(visible);

        if (visible) {
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

    @Override
    public String getDisplayName() {
        return "Tileset Palette";
    }

    /**
     * Returns true if ready for painting (layer selected AND in tilemap mode).
     */
    private boolean canPaint() {
        return scene != null
                && scene.getActiveLayer() != null
                && editorSelectionManager != null
                && editorSelectionManager.isTilemapLayerSelected();
    }

    /**
     * Returns true if a layer is selected in the dropdown (even if not in tilemap mode).
     */
    private boolean hasActiveLayer() {
        return scene != null && scene.getActiveLayer() != null;
    }

    /**
     * Renders the layer selector dropdown in the left column.
     */
    private void renderLayerSelector() {
        if (scene == null) {
            ImGui.textDisabled("No scene loaded");
            return;
        }

        List<TilemapLayer> layers = scene.getLayers();
        if (layers.isEmpty()) {
            ImGui.textDisabled("No layers");
            return;
        }

        TilemapLayer activeLayer = scene.getActiveLayer();
        String activeLayerName = activeLayer != null ? activeLayer.getName() : "None";

        ImGui.text("Layer:");
        ImGui.setNextItemWidth(-1);  // Fill available width
        if (ImGui.beginCombo("##layer", activeLayerName)) {
            // "None" option to deselect
            if (ImGui.selectable("None", activeLayer == null)) {
                if (editorSelectionManager != null) {
                    editorSelectionManager.clearSelection();
                }
            }

            ImGui.separator();

            for (int i = 0; i < layers.size(); i++) {
                TilemapLayer layer = layers.get(i);
                boolean isSelected = (activeLayer == layer);

                // Show lock icon if locked
                String label = layer.isLocked() ? "[L] " + layer.getName() : layer.getName();

                if (ImGui.selectable(label, isSelected)) {
                    // Sync Hierarchy to select Tilemap Layers (also sets scene.activeLayer)
                    syncHierarchyToTilemapLayers(i);
                }
            }
            ImGui.endCombo();
        }

        // Inline visibility toggle for active layer
        if (activeLayer != null) {
            ImGui.sameLine();
            boolean visible = activeLayer.isVisible();
            if (ImGui.checkbox("##vis", visible)) {
                activeLayer.setVisible(!visible);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Toggle layer visibility");
            }
        }
    }

    /**
     * Syncs the Hierarchy selection to Tilemap Layers when interacting with the palette.
     */
    private void syncHierarchyToTilemapLayers(int layerIndex) {
        if (editorSelectionManager != null && layerIndex >= 0) {
            editorSelectionManager.selectTilemapLayer(layerIndex);
        }
    }

    private void renderVertical() {
        renderSelectionWarning();
        renderLayerSelector();
        ImGui.separator();
        tilesetSelector.renderSelector();
        ImGui.separator();

        float reservedHeight = ImGui.getTextLineHeightWithSpacing() * 4 +
                ImGui.getStyle().getItemSpacingY() * 5 +
                ImGui.getFrameHeightWithSpacing() * 3 + 30;

        float availableHeight = ImGui.getContentRegionAvailY() - reservedHeight;

        // Grid is enabled when there's an active layer (clicking will switch to tilemap mode)
        boolean enabled = hasActiveLayer();
        ImGui.beginChild("tileGridChild", 0, availableHeight, false,
                imgui.flag.ImGuiWindowFlags.HorizontalScrollbar);
        gridRenderer.render(tilesetSelector.getSelectedTileset(), enabled);
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

            renderSelectionWarning();
            renderLayerSelector();
            ImGui.separator();
            tilesetSelector.renderSelector();
            ImGui.separator();
            renderSelectionInfo();
            renderToolSizeSlider();

            // Spacer to push tile size to bottom
            float remainingHeight = ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing() - 8;
            if (remainingHeight > 0) {
                ImGui.dummy(0, remainingHeight);
            }
            gridRenderer.renderSizeSlider();

            // ===== RIGHT COLUMN =====
            ImGui.tableNextColumn();

            // Grid is enabled when there's an active layer (clicking will switch to tilemap mode)
            boolean enabled = hasActiveLayer();
            ImGui.beginChild(
                    "TileGridScroll",
                    0,
                    0,
                    false,
                    ImGuiWindowFlags.HorizontalScrollbar
            );
            gridRenderer.render(tilesetSelector.getSelectedTileset(), enabled);
            ImGui.endChild();

            ImGui.endTable();
        }
    }


    private void syncSelectionWithTool() {
        if (!selectionManager.isDragging() && brushTool != null && brushTool.getSelection() == null) {
            selectionManager.clearSelection();
        }
    }

    /**
     * Renders the warning message above the layer selector based on current state.
     */
    private void renderSelectionWarning() {
        if (!hasActiveLayer()) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Select a layer to start painting");
        } else if (!canPaint()) {
            // Layer is selected but not in tilemap mode (e.g., entity selected)
            ImGui.textColored(1.0f, 0.6f, 0.2f, 1.0f, "Select a tile or brush to resume painting");
        }
    }

    private void renderSelectionInfo() {
        // Don't show selection info if can't paint
        if (!canPaint()) {
            return;
        }

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

        ImGui.text("Tool Size");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        int[] size = {brushTool.getBrushSize()};
        if (ImGui.sliderInt("##toolSize", size, 1, 10)) {
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

        // Sync Hierarchy to select Tilemap Layers when interacting with palette
        if (editorSelectionManager != null && scene != null) {
            TilemapLayer activeLayer = scene.getActiveLayer();
            if (activeLayer != null) {
                // Find the index of the active layer
                List<TilemapLayer> layers = scene.getLayers();
                int layerIndex = layers.indexOf(activeLayer);
                if (layerIndex >= 0) {
                    syncHierarchyToTilemapLayers(layerIndex);
                }
            }
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

    /**
     * Sets the editor selection manager and subscribes to selection change events.
     */
    public void setEditorSelectionManager(EditorSelectionManager manager) {
        this.editorSelectionManager = manager;
        if (manager != null) {
            EditorEventBus.get().subscribe(SelectionChangedEvent.class, this::onSelectionChanged);
        }
    }

    /**
     * Called when the editor selection changes.
     * Clears tile selection when leaving tilemap layer mode.
     */
    private void onSelectionChanged(SelectionChangedEvent event) {
        if (event.selectionType() != EditorSelectionManager.SelectionType.TILEMAP_LAYER) {
            clearSelection();
        }
    }
}