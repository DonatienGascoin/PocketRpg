package com.pocket.rpg.editor.panels.collisions;

import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Setter;

import java.util.function.IntConsumer;

public class CollisionToolConfigView {

    @Setter
    private EditorScene scene;

    /**
     * Callback when elevation level changes.
     */
    @Setter
    private IntConsumer onElevationChanged;

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

    private final ImBoolean visibilityToggle = new ImBoolean(true);
    private final ImInt zLevelSlider = new ImInt(0);
    private final ImInt brushSizeSlider = new ImInt(1);
    private final float[] opacitySlider = {1f};

    public void renderVisibilitySection() {
        ImGui.text("Visibility");

        // When collision panel is open, collision overlay is always visible
        visibilityToggle.set(true);

        if (scene != null) {
            opacitySlider[0] = scene.getCollisionOpacity();
        }
        if (ImGui.sliderFloat("Opacity", opacitySlider, 0.1f, 1.0f, "%.2f")) {
            if (scene != null) {
                scene.setCollisionOpacity(opacitySlider[0]);
            }
        }
    }

    public void renderZLevelSection() {
        ImGui.text("Elevation Level");
        ImGui.spacing();
        renderElevationButtons(false);
    }

    public void renderBrushSection() {
        ImGui.text("Tool Size");

        // Use brush tool's size as the shared size
        if (brushTool != null) {
            brushSizeSlider.set(brushTool.getBrushSize());
        }
        if (ImGui.sliderInt("Size", brushSizeSlider.getData(), 1, 10)) {
            int size = brushSizeSlider.get();
            // Sync to all size-aware tools
            if (brushTool != null) {
                brushTool.setBrushSize(size);
            }
            if (eraserTool != null) {
                eraserTool.setEraserSize(size);
            }
        }
    }

    public void renderStatsSection() {
        ImGui.text("Statistics");

        if (scene != null && scene.getCollisionMap() != null) {
            ImGui.text("Total tiles: " + scene.getCollisionMap().getTileCount());
            ImGui.text("Z-levels in use: " + scene.getCollisionMap().getZLevels().size());

            for (int z : scene.getCollisionMap().getZLevels()) {
                int count = 0;
                for (var chunk : scene.getCollisionMap().getChunksForLevel(z)) {
                    count += chunk.getTileCount();
                }
                ImGui.text("  Z=" + z + ": " + count + " tiles");
            }
        } else {
            ImGui.textDisabled("No scene loaded");
        }
    }

    public void renderToolSizeSlider() {
        // Use brush tool's size as the shared size source
        if (brushTool != null) {
            brushSizeSlider.set(brushTool.getBrushSize());
        }
        if (ImGui.sliderInt("Size", brushSizeSlider.getData(), 1, 10)) {
            int size = brushSizeSlider.get();
            // Sync to all size-aware tools
            if (brushTool != null) {
                brushTool.setBrushSize(size);
            }
            if (eraserTool != null) {
                eraserTool.setEraserSize(size);
            }
        }
    }

    private void syncZLevel(int zLevel) {
        if (brushTool != null) brushTool.setZLevel(zLevel);
        if (eraserTool != null) eraserTool.setZLevel(zLevel);
        if (fillTool != null) fillTool.setZLevel(zLevel);
        if (rectangleTool != null) rectangleTool.setZLevel(zLevel);
        if (pickerTool != null) pickerTool.setZLevel(zLevel);
    }

    // ========================================================================
    // Compact UI Elements for Top Bar
    // ========================================================================

    /**
     * Renders a compact visibility checkbox (no label).
     */
    public void renderVisibilityToggle() {
        visibilityToggle.set(scene != null && scene.isCollisionVisible());
        if (ImGui.checkbox("Show##vis", visibilityToggle)) {
            if (scene != null) {
                scene.setCollisionVisible(visibilityToggle.get());
            }
        }
    }

    /**
     * Renders compact elevation buttons for the top bar.
     */
    public void renderElevationInput() {
        renderElevationButtons(true);
    }

    /**
     * Renders elevation level buttons (0-3).
     * @param compact If true, uses smaller buttons for top bar; otherwise uses larger buttons for sidebar
     */
    private void renderElevationButtons(boolean compact) {
        int currentLevel = scene != null ? scene.getCollisionZLevel() : 0;

        // Button size
        float buttonWidth = compact ? 28 : 70;
        float buttonHeight = compact ? 0 : 30;  // 0 = auto height

        // Selected button colors (green highlight)
        float[] selectedBg = {0.2f, 0.7f, 0.3f, 1.0f};
        float[] selectedHover = {0.3f, 0.8f, 0.4f, 1.0f};

        for (ElevationLevel elev : ElevationLevel.getAll()) {
            int level = elev.getLevel();
            String name = elev.getDisplayName();

            if (level > 0) {
                ImGui.sameLine();
            }

            boolean isSelected = (level == currentLevel);

            // Apply highlight style for selected button
            if (isSelected) {
                ImGui.pushStyleColor(ImGuiCol.Button, selectedBg[0], selectedBg[1], selectedBg[2], selectedBg[3]);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, selectedHover[0], selectedHover[1], selectedHover[2], selectedHover[3]);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, selectedBg[0], selectedBg[1], selectedBg[2], selectedBg[3]);
            }

            // Button label: show name in non-compact mode, level number in compact mode
            String buttonLabel = compact ? String.valueOf(level) : name;

            if (ImGui.button(buttonLabel + "##elev" + level, buttonWidth, buttonHeight)) {
                setElevation(level);
            }

            if (isSelected) {
                ImGui.popStyleColor(3);
            }

            // Tooltip
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(name + " (Level " + level + ")");
                ImGui.endTooltip();
            }
        }
    }

    /**
     * Sets the current elevation level.
     */
    private void setElevation(int level) {
        zLevelSlider.set(level);
        if (scene != null) {
            scene.setCollisionZLevel(level);
        }
        syncZLevel(level);

        // Notify listeners of elevation change
        if (onElevationChanged != null) {
            onElevationChanged.accept(level);
        }
    }

    /**
     * Gets the current elevation level.
     */
    public int getElevation() {
        return scene != null ? scene.getCollisionZLevel() : 0;
    }

    /**
     * Gets total collision tile count.
     */
    public int getTileCount() {
        if (scene != null && scene.getCollisionMap() != null) {
            return scene.getCollisionMap().getTileCount();
        }
        return 0;
    }

    /**
     * Checks if overlay is currently visible.
     */
    public boolean isOverlayVisible() {
        return scene != null && scene.isCollisionVisible();
    }

    /**
     * Sets overlay visibility.
     */
    public void setOverlayVisible(boolean visible) {
        if (scene != null) {
            scene.setCollisionVisible(visible);
        }
    }
}