package com.pocket.rpg.editor.panels;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.SelectionChangedEvent;
import com.pocket.rpg.editor.events.TriggerFocusRequestEvent;
import com.pocket.rpg.editor.events.TriggerSelectedEvent;
import com.pocket.rpg.editor.panels.collision.TriggerListSection;
import com.pocket.rpg.editor.panels.collisions.CollisionToolConfigView;
import com.pocket.rpg.editor.panels.collisions.CollisionTypeSelector;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

/**
 * Panel for editing collision map and selecting collision types.
 * When open, collision painting tools become available.
 */
public class CollisionPanel extends EditorPanel {

    private static final String PANEL_ID = "collisionPanel";

    @Setter
    private EditorScene scene;

    @Setter
    private CollisionBrushTool brushTool;

    @Setter
    private CollisionEraserTool eraserTool;

    @Setter
    private CollisionFillTool fillTool;

    @Setter
    private CollisionRectangleTool rectangleTool;

    @Setter
    private CollisionPickerTool pickerTool;

    private final CollisionTypeSelector typeSelector;
    private final CollisionToolConfigView toolConfigView;
    private final TriggerListSection triggerListSection;

    @Getter
    @Setter private boolean isHorizontalLayout = false;

    /**
     * Callback to switch to brush tool when selecting a collision type.
     */
    @Setter
    private Runnable onSwitchToBrushTool;

    /**
     * Supplier to get the current active tool for checking if we should switch.
     */
    @Setter
    private Supplier<EditorTool> activeToolSupplier;

    private EditorSelectionManager editorSelectionManager;

    public CollisionPanel() {
        super(PANEL_ID, false); // Default closed - painting panel
        this.typeSelector = new CollisionTypeSelector();
        this.toolConfigView = new CollisionToolConfigView();
        this.triggerListSection = new TriggerListSection();

        typeSelector.setOnTypeSelected(this::onTypeSelected);
        triggerListSection.setOnTriggerSelected(this::handleTriggerSelected);
        triggerListSection.setOnTriggerFocus(this::handleTriggerFocus);
        toolConfigView.setOnElevationChanged(this::onElevationChanged);
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
     */
    private void onSelectionChanged(SelectionChangedEvent event) {
        // Clear trigger selection when leaving collision mode
        if (event.selectionType() != EditorSelectionManager.SelectionType.COLLISION_LAYER) {
            clearTriggerSelection();
        }
    }

    /**
     * Called when the elevation level changes.
     */
    private void onElevationChanged(int newLevel) {
        // Clear trigger selection when switching elevation levels
        clearTriggerSelection();
    }

    /**
     * Returns true if ready for painting (collision layer selected).
     */
    private boolean canPaint() {
        return editorSelectionManager != null
                && editorSelectionManager.isCollisionLayerSelected();
    }

    @Override
    public void render() {
        if (!isOpen()) {
            setContentVisible(false);
            return;
        }

        boolean visible = ImGui.begin("Collision Panel");
        setContentVisible(visible);

        if (visible) {
            updateDependencies();

            if (isHorizontalLayout) {
                renderHorizontal();
            } else {
                renderVertical();
            }
        }
        ImGui.end();
    }

    @Override
    public String getDisplayName() {
        return "Collision Panel";
    }

    private void updateDependencies() {
        toolConfigView.setScene(scene);
        toolConfigView.setBrushTool(brushTool);
        toolConfigView.setEraserTool(eraserTool);
        toolConfigView.setFillTool(fillTool);
        toolConfigView.setRectangleTool(rectangleTool);
        toolConfigView.setPickerTool(pickerTool);

        triggerListSection.setScene(scene);
        triggerListSection.setCurrentElevation(toolConfigView.getElevation());
    }

    private void renderVertical() {
        renderSelectionWarning();
        toolConfigView.renderVisibilitySection();
        ImGui.separator();
        toolConfigView.renderZLevelSection();
        ImGui.separator();
        toolConfigView.renderBrushSection();
        ImGui.separator();
        typeSelector.render(canPaint());
        ImGui.separator();
        toolConfigView.renderStatsSection();
    }

    /**
     * Renders the warning message when collision map is not selected.
     */
    private void renderSelectionWarning() {
        if (!canPaint()) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Select collision map to start painting");
        }
    }

    private void renderHorizontal() {
        renderSelectionWarning();

        // Top bar: Tool size, visibility, elevation, stats
        renderTopBar();
        ImGui.separator();

        // 3-column layout
        if (ImGui.beginTable("collisionTable3", 3, ImGuiTableFlags.BordersInnerV)) {
            float availWidth = ImGui.getContentRegionAvailX();
            ImGui.tableSetupColumn("Col1_Basic", ImGuiTableColumnFlags.WidthFixed, availWidth * 0.30f);
            ImGui.tableSetupColumn("Col2_Metadata", ImGuiTableColumnFlags.WidthFixed, availWidth * 0.30f);
            ImGui.tableSetupColumn("Col3_Triggers", ImGuiTableColumnFlags.WidthStretch);

            // ===== COLUMN 1: Basic Types =====
            ImGui.tableNextColumn();
            typeSelector.renderColumn1();

            // ===== COLUMN 2: Metadata Types + Selected Info =====
            ImGui.tableNextColumn();
            typeSelector.renderColumn2();

            // ===== COLUMN 3: Trigger List =====
            ImGui.tableNextColumn();
            triggerListSection.render();

            ImGui.endTable();
        }
    }

    private void renderTopBar() {
        // Brush size (compact)
        ImGui.text("Brush Size:");
        ImGui.sameLine();
        ImGui.setNextItemWidth(50);
        toolConfigView.renderToolSizeSlider();
        ImGui.sameLine();

        // Elevation buttons
        ImGui.text("Elevation:");
        ImGui.sameLine();
        toolConfigView.renderElevationInput();
        ImGui.sameLine();

        // Stats
        ImGui.textDisabled("| " + toolConfigView.getTileCount() + " tiles");
    }


    private void renderToolSizeForActiveTool() {
        ImGui.text("Tool Size");
        toolConfigView.renderToolSizeSlider();
    }

    private void onTypeSelected(CollisionType type) {
        if (brushTool != null) brushTool.setSelectedType(type);
        if (fillTool != null) fillTool.setSelectedType(type);
        if (rectangleTool != null) rectangleTool.setSelectedType(type);

        // Sync Hierarchy to select Collision Map when interacting with panel
        if (editorSelectionManager != null) {
            editorSelectionManager.selectCollisionLayer();
        }

        // Switch to brush tool if not already in fill or rectangle tool
        if (onSwitchToBrushTool != null && activeToolSupplier != null) {
            EditorTool activeTool = activeToolSupplier.get();
            boolean isFillOrRectangle = activeTool == fillTool || activeTool == rectangleTool;
            if (!isFillOrRectangle) {
                onSwitchToBrushTool.run();
            }
        }
    }

    public CollisionType getSelectedType() {
        return typeSelector.getSelectedType();
    }

    public void setSelectedType(CollisionType type) {
        typeSelector.setSelectedType(type);
    }

    private void handleTriggerSelected(TileCoord coord) {
        // Publish trigger selected event
        EditorEventBus.get().publish(new TriggerSelectedEvent(coord));

        // Also ensure collision layer is selected
        if (editorSelectionManager != null) {
            editorSelectionManager.selectCollisionLayer();
        }
    }

    private void handleTriggerFocus(TileCoord coord) {
        // First select the trigger
        handleTriggerSelected(coord);

        // Then publish focus request event
        EditorEventBus.get().publish(new TriggerFocusRequestEvent(coord));
    }

    /**
     * Gets the currently selected trigger tile, or null if none.
     */
    public TileCoord getSelectedTrigger() {
        return triggerListSection.getSelectedTrigger();
    }

    /**
     * Clears the trigger selection and notifies listeners.
     */
    public void clearTriggerSelection() {
        triggerListSection.clearSelection();
        // Publish event to clear trigger display in inspector
        EditorEventBus.get().publish(new TriggerSelectedEvent(null));
    }

    /**
     * Sets the selected trigger (e.g., when clicking in scene view).
     */
    public void setSelectedTrigger(TileCoord coord) {
        triggerListSection.setSelectedTrigger(coord);
    }

    /**
     * Sets the camera world bounds supplier for visibility checking.
     */
    public void setCameraWorldBoundsSupplier(Supplier<float[]> supplier) {
        triggerListSection.setCameraWorldBoundsSupplier(supplier);
    }
}