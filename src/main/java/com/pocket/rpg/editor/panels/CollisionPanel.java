package com.pocket.rpg.editor.panels;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.EditorModeManager;
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

public class CollisionPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorModeManager modeManager;

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
    @Getter
    @Setter private boolean isHorizontalLayout = false;

    public CollisionPanel() {
        this.typeSelector = new CollisionTypeSelector();
        this.toolConfigView = new CollisionToolConfigView();

        typeSelector.setOnTypeSelected(this::onTypeSelected);
    }

    public void render() {
        if (ImGui.begin("Collision")) {
            updateDependencies();

            if (isHorizontalLayout) {
                renderHorizontal();
            } else {
                renderVertical();
            }
        }
        ImGui.end();
    }

    private void updateDependencies() {
        toolConfigView.setScene(scene);
        toolConfigView.setModeManager(modeManager);
        toolConfigView.setBrushTool(brushTool);
        toolConfigView.setEraserTool(eraserTool);
        toolConfigView.setFillTool(fillTool);
        toolConfigView.setRectangleTool(rectangleTool);
        toolConfigView.setPickerTool(pickerTool);
    }

    private void renderVertical() {
        toolConfigView.renderVisibilitySection();
        ImGui.separator();
        toolConfigView.renderZLevelSection();
        ImGui.separator();
        toolConfigView.renderBrushSection();
        ImGui.separator();
        typeSelector.render();
        ImGui.separator();
        toolConfigView.renderStatsSection();
    }

    private void renderHorizontal() {
        if (ImGui.beginTable("collisionTable", 2, ImGuiTableFlags.BordersInnerV)) {

            ImGui.tableSetupColumn(
                    "Left",
                    ImGuiTableColumnFlags.WidthFixed,
                    ImGui.getWindowWidth() * 0.33f
            );
            ImGui.tableSetupColumn("Right");

            // ===== LEFT COLUMN =====
            ImGui.tableNextColumn();

            renderToolSizeForActiveTool();
            toolConfigView.renderVisibilitySection();

            // Separator between visibility and selected
            ImGui.separator();

            ImGui.text("Selected: " + typeSelector.getSelectedType().getDisplayName());
            ImGui.textWrapped(
                    typeSelector.getTypeDescription(typeSelector.getSelectedType())
            );

            ImGui.separator();
            toolConfigView.renderStatsSection();

            // ===== RIGHT COLUMN =====
            ImGui.tableNextColumn();

            toolConfigView.renderZLevelSection();

            // Separator between Z index and collision type
            ImGui.separator();

            // Scrollable collision type ONLY
            ImGui.text("Collision Types");
            ImGui.beginChild(
                    "CollisionTypeScroll",
                    0,
                    0,
                    false,
                    ImGuiWindowFlags.HorizontalScrollbar
            );

            typeSelector.renderHorizontal();
            ImGui.endChild();

            ImGui.endTable();
        }
    }


    private void renderToolSizeForActiveTool() {
        ImGui.text("Tool Size");

        // Only show size control for active tool
        if (brushTool != null && isToolActive(brushTool)) {
            toolConfigView.renderBrushSizeOnly();
        } else if (eraserTool != null && isToolActive(eraserTool)) {
            toolConfigView.renderEraserSizeOnly();
        }
    }

    private boolean isToolActive(Object tool) {
        // This assumes you have access to check active tool
        // Adjust based on your actual tool management
        return true; // Simplified - implement actual check
    }

    private void onTypeSelected(CollisionType type) {
        if (brushTool != null) brushTool.setSelectedType(type);
        if (fillTool != null) fillTool.setSelectedType(type);
        if (rectangleTool != null) rectangleTool.setSelectedType(type);
    }

    public CollisionType getSelectedType() {
        return typeSelector.getSelectedType();
    }

    public void setSelectedType(CollisionType type) {
        typeSelector.setSelectedType(type);
    }
}