package com.pocket.rpg.editor.panels;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Setter;

/**
 * Panel for selecting collision types and configuring collision editing.
 * <p>
 * Features:
 * - Collision overlay visibility toggle
 * - Z-level selector (synchronized across all tools)
 * - Brush/eraser size controls
 * - Collision type selection with colored buttons
 * - Collision statistics
 */
public class CollisionPanel {

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

    private CollisionType selectedType = CollisionType.SOLID;

    // UI state
    private final ImBoolean visibilityToggle = new ImBoolean(true);
    private final ImInt zLevelSlider = new ImInt(0);
    private final ImInt brushSizeSlider = new ImInt(1);
    private final ImInt eraserSizeSlider = new ImInt(1);
    private final float[] opacitySlider = {0.4f};

    /**
     * Renders the collision panel.
     */
    public void render() {
        if (ImGui.begin("Collision")) {
            renderVisibilitySection();
            ImGui.separator();
            renderZLevelSection();
            ImGui.separator();
            renderBrushSection();
            ImGui.separator();
            renderCollisionTypeSection();
            ImGui.separator();
            renderStatsSection();
        }
        ImGui.end();
    }

    /**
     * Renders visibility controls.
     */
    private void renderVisibilitySection() {
        ImGui.text("Visibility");

        // Sync with scene
        if (scene != null) {
            visibilityToggle.set(scene.isCollisionVisible());
        }

        if (ImGui.checkbox("Show Collision Overlay", visibilityToggle)) {
            if (scene != null) {
                scene.setCollisionVisible(visibilityToggle.get());
            }
        }

        // Opacity slider
        if (scene != null) {
            opacitySlider[0] = scene.getCollisionOpacity();
        }
        if (ImGui.sliderFloat("Opacity", opacitySlider, 0.1f, 1.0f, "%.2f")) {
            if (scene != null) {
                scene.setCollisionOpacity(opacitySlider[0]);
            }
        }
    }

    /**
     * Renders Z-level controls.
     */
    private void renderZLevelSection() {
        ImGui.text("Z-Level");

        // Sync with scene
        if (scene != null) {
            zLevelSlider.set(scene.getCollisionZLevel());
        }

        if (ImGui.sliderInt("Edit Z-Level", zLevelSlider.getData(), 0, 3)) {
            int newZLevel = zLevelSlider.get();
            
            // Update scene
            if (scene != null) {
                scene.setCollisionZLevel(newZLevel);
            }

            // Sync all tools
            syncZLevel(newZLevel);
        }

        // Show Z-level description
        String zDesc = switch (zLevelSlider.get()) {
            case 0 -> "Ground level";
            case 1 -> "Bridge/elevated";
            case 2 -> "Second floor";
            case 3 -> "Rooftop";
            default -> "Level " + zLevelSlider.get();
        };
        ImGui.textDisabled(zDesc);
    }

    /**
     * Renders brush/eraser size controls.
     */
    private void renderBrushSection() {
        ImGui.text("Tool Size");

        // Brush size
        if (brushTool != null) {
            brushSizeSlider.set(brushTool.getBrushSize());
        }
        if (ImGui.sliderInt("Brush Size", brushSizeSlider.getData(), 1, 10)) {
            if (brushTool != null) {
                brushTool.setBrushSize(brushSizeSlider.get());
            }
        }

        // Eraser size
        if (eraserTool != null) {
            eraserSizeSlider.set(eraserTool.getEraserSize());
        }
        if (ImGui.sliderInt("Eraser Size", eraserSizeSlider.getData(), 1, 10)) {
            if (eraserTool != null) {
                eraserTool.setEraserSize(eraserSizeSlider.get());
            }
        }
    }

    /**
     * Renders collision type selection.
     */
    private void renderCollisionTypeSection() {
        ImGui.text("Collision Types");

        // Group: Movement
        ImGui.textDisabled("Movement");
        renderCollisionButton(CollisionType.NONE);
        renderCollisionButton(CollisionType.SOLID);

        // Group: Ledges
        ImGui.spacing();
        ImGui.textDisabled("Ledges");
        renderCollisionButton(CollisionType.LEDGE_DOWN);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_UP);
        renderCollisionButton(CollisionType.LEDGE_LEFT);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_RIGHT);

        // Group: Terrain
        ImGui.spacing();
        ImGui.textDisabled("Terrain");
        renderCollisionButton(CollisionType.WATER);
        renderCollisionButton(CollisionType.TALL_GRASS);
        renderCollisionButton(CollisionType.ICE);
        renderCollisionButton(CollisionType.SAND);

        // Group: Triggers
        ImGui.spacing();
        ImGui.textDisabled("Triggers");
        renderCollisionButton(CollisionType.WARP);
        renderCollisionButton(CollisionType.DOOR);
        renderCollisionButton(CollisionType.SCRIPT_TRIGGER);

        // Selected type info
        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Selected: " + selectedType.getDisplayName());
        ImGui.textWrapped(getCollisionTypeDescription(selectedType));
    }

    /**
     * Renders statistics section.
     */
    private void renderStatsSection() {
        ImGui.text("Statistics");

        if (scene != null && scene.getCollisionMap() != null) {
            ImGui.text("Total tiles: " + scene.getCollisionMap().getTileCount());
            ImGui.text("Z-levels in use: " + scene.getCollisionMap().getZLevels().size());

            // Show per-zlevel counts
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

    /**
     * Renders a button for a collision type.
     */
    private void renderCollisionButton(CollisionType type) {
        boolean isSelected = (type == selectedType);

        // Get type color
        float[] color = type.getOverlayColor();

        // Handle NONE specially (transparent becomes gray)
        float r = type == CollisionType.NONE ? 0.3f : color[0];
        float g = type == CollisionType.NONE ? 0.3f : color[1];
        float b = type == CollisionType.NONE ? 0.3f : color[2];

        // Push button colors
        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 0.8f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r * 1.1f, g * 1.1f, b * 1.1f, 0.9f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r * 0.9f, g * 0.9f, b * 0.9f, 1.0f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, r, g, b, 0.5f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, 0.7f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r, g, b, 0.9f);
        }

        // Button - shorter names for ledges to fit side-by-side
        String label = type.getDisplayName();
        float buttonWidth = label.contains("Ledge") ? 65 : 140;

        if (ImGui.button(label + "##" + type.name(), buttonWidth, 0)) {
            selectCollisionType(type);
        }

        ImGui.popStyleColor(3);

        // Tooltip
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(getCollisionTypeDescription(type));
        }
    }

    /**
     * Selects a collision type and updates all collision tools.
     */
    private void selectCollisionType(CollisionType type) {
        selectedType = type;

        if (brushTool != null) {
            brushTool.setSelectedType(type);
        }
        if (fillTool != null) {
            fillTool.setSelectedType(type);
        }
        if (rectangleTool != null) {
            rectangleTool.setSelectedType(type);
        }
    }

    /**
     * Synchronizes Z-level across all tools.
     */
    private void syncZLevel(int zLevel) {
        if (brushTool != null) {
            brushTool.setZLevel(zLevel);
        }
        if (eraserTool != null) {
            eraserTool.setZLevel(zLevel);
        }
        if (fillTool != null) {
            fillTool.setZLevel(zLevel);
        }
        if (rectangleTool != null) {
            rectangleTool.setZLevel(zLevel);
        }
        if (pickerTool != null) {
            pickerTool.setZLevel(zLevel);
        }
    }

    /**
     * Gets a description of a collision type.
     */
    private String getCollisionTypeDescription(CollisionType type) {
        return switch (type) {
            case NONE -> "No collision - fully walkable";
            case SOLID -> "Solid wall - blocks all movement";
            case LEDGE_DOWN -> "Ledge - can jump down (south)";
            case LEDGE_UP -> "Ledge - can jump up (north)";
            case LEDGE_LEFT -> "Ledge - can jump left (west)";
            case LEDGE_RIGHT -> "Ledge - can jump right (east)";
            case WATER -> "Water - requires swimming ability";
            case TALL_GRASS -> "Tall grass - triggers wild encounters";
            case ICE -> "Ice - causes sliding movement";
            case SAND -> "Sand - slows movement";
            case WARP -> "Warp zone - triggers scene transition";
            case DOOR -> "Door - triggers door interaction";
            case SCRIPT_TRIGGER -> "Script trigger - executes script on step";
        };
    }

    /**
     * Gets the currently selected collision type.
     */
    public CollisionType getSelectedType() {
        return selectedType;
    }

    /**
     * Sets the collision type (from external source like picker).
     */
    public void setSelectedType(CollisionType type) {
        selectCollisionType(type);
    }
}
