package com.pocket.rpg.editor.panels.collisions;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.*;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import lombok.Setter;

public class CollisionToolConfigView {

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

    private final ImBoolean visibilityToggle = new ImBoolean(true);
    private final ImInt zLevelSlider = new ImInt(0);
    private final ImInt brushSizeSlider = new ImInt(1);
    private final float[] opacitySlider = {1f};

    public void renderVisibilitySection() {
        ImGui.text("Visibility");

        boolean inCollisionMode = modeManager != null && modeManager.isCollisionMode();

        if (scene != null) {
            if (inCollisionMode) {
                visibilityToggle.set(true);
            } else {
                visibilityToggle.set(scene.isCollisionVisible());
            }
        }

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
        ImGui.text("Z-Level");

        if (scene != null) {
            zLevelSlider.set(scene.getCollisionZLevel());
        }

        if (ImGui.sliderInt("Edit Z-Level", zLevelSlider.getData(), 0, 3)) {
            int newZLevel = zLevelSlider.get();

            if (scene != null) {
                scene.setCollisionZLevel(newZLevel);
            }

            syncZLevel(newZLevel);
        }

        String zDesc = switch (zLevelSlider.get()) {
            case 0 -> "Ground level";
            case 1 -> "Bridge/elevated";
            case 2 -> "Second floor";
            case 3 -> "Rooftop";
            default -> "Level " + zLevelSlider.get();
        };
        ImGui.textDisabled(zDesc);
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
}