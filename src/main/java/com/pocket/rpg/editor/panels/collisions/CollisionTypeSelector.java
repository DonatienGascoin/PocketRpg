package com.pocket.rpg.editor.panels.collisions;

import com.pocket.rpg.collision.CollisionCategory;
import com.pocket.rpg.collision.CollisionType;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Selector for collision types in the editor.
 * <p>
 * Auto-generates UI from CollisionType enum grouped by CollisionCategory.
 */
public class CollisionTypeSelector {

    @Getter private CollisionType selectedType = CollisionType.SOLID;
    @Setter private Consumer<CollisionType> onTypeSelected;

    // Track enabled state for rendering
    private boolean enabled = true;

    public void render() {
        render(true);
    }

    public void render(boolean enabled) {
        this.enabled = enabled;

        ImGui.text("Collision Types");

        // Auto-generate UI from enum categories
        for (CollisionCategory category : CollisionCategory.inOrder()) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.spacing();
            ImGui.textDisabled(category.getDisplayName());

            for (CollisionType type : types) {
                renderCollisionButton(type);
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Selected: " + selectedType.getDisplayName());
        ImGui.textWrapped(selectedType.getDescription());
    }

    public void renderHorizontal() {
        renderHorizontal(true);
    }

    public void renderHorizontal(boolean enabled) {
        this.enabled = enabled;

        // Auto-generate UI from enum categories
        for (CollisionCategory category : CollisionCategory.inOrder()) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.spacing();
            ImGui.textDisabled(category.getDisplayName());

            boolean first = true;
            for (CollisionType type : types) {
                if (!first) ImGui.sameLine();
                first = false;
                renderCollisionButton(type);
            }
        }
    }

    /**
     * Renders Column 1: Basic types (Movement, Ledges, Terrain).
     * These types don't require metadata configuration.
     */
    public void renderColumn1() {
        List<CollisionCategory> col1Categories = List.of(
                CollisionCategory.MOVEMENT,
                CollisionCategory.LEDGE,
                CollisionCategory.TERRAIN
        );

        for (CollisionCategory category : col1Categories) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.textDisabled(category.getDisplayName());

            boolean first = true;
            for (CollisionType type : types) {
                if (!first) ImGui.sameLine();
                first = false;
                renderCompactButton(type);
            }

            ImGui.spacing();
        }
    }

    /**
     * Renders Column 2: Metadata types (Elevation, Triggers) + selected type info.
     * These types require metadata configuration.
     */
    public void renderColumn2() {
        List<CollisionCategory> col2Categories = List.of(
                CollisionCategory.ELEVATION,
                CollisionCategory.TRIGGER
        );

        for (CollisionCategory category : col2Categories) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.textDisabled(category.getDisplayName());

            boolean first = true;
            for (CollisionType type : types) {
                if (!first) ImGui.sameLine();
                first = false;
                renderCompactButton(type);
            }

            ImGui.spacing();
        }

        // Selected type info at bottom
        ImGui.separator();
        ImGui.text("Selected: " + selectedType.getDisplayName());
        ImGui.textWrapped(selectedType.getDescription());
    }

    private void renderCollisionButton(CollisionType type) {
        boolean isSelected = (type == selectedType);
        float[] color = type.getOverlayColor();

        float r = type == CollisionType.NONE ? 0.3f : color[0];
        float g = type == CollisionType.NONE ? 0.3f : color[1];
        float b = type == CollisionType.NONE ? 0.3f : color[2];

        // Dim colors when disabled (but still allow clicks to switch modes)
        float dimFactor = enabled ? 1.0f : 0.4f;

        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, r * dimFactor, g * dimFactor, b * dimFactor, 0.8f * dimFactor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r * 1.1f, g * 1.1f, b * 1.1f, 0.9f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r * 0.9f, g * 0.9f, b * 0.9f, 1.0f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, r * dimFactor, g * dimFactor, b * dimFactor, 0.5f * dimFactor);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, 0.7f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r, g, b, 0.9f);
        }

        String label = type.getDisplayName();
        float buttonWidth = type.isLedge() ? 65 : 140;

        if (ImGui.button(label + "##" + type.name(), buttonWidth, 0)) {
            selectType(type);
        }

        ImGui.popStyleColor(3);

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(type.getDescription());
        }
    }

    private void renderCompactButton(CollisionType type) {
        boolean isSelected = (type == selectedType);
        float[] color = type.getOverlayColor();

        float r = type == CollisionType.NONE ? 0.3f : color[0];
        float g = type == CollisionType.NONE ? 0.3f : color[1];
        float b = type == CollisionType.NONE ? 0.3f : color[2];

        // Dim colors when disabled
        float dimFactor = enabled ? 1.0f : 0.4f;

        if (isSelected) {
            ImGui.pushStyleColor(ImGuiCol.Button, r * dimFactor, g * dimFactor, b * dimFactor, 0.9f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r * 1.1f, g * 1.1f, b * 1.1f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r * 0.8f, g * 0.8f, b * 0.8f, 1.0f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, r * dimFactor, g * dimFactor, b * dimFactor, 0.4f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, r, g, b, 0.6f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, r, g, b, 0.8f);
        }

        // Compact label - use icon if available, else abbreviation
        String label = type.hasIcon()
                ? type.getIcon()
                : abbreviate(type.getDisplayName());

        float buttonWidth = type.isLedge() ? 35 : 60;

        if (ImGui.button(label + "##" + type.name(), buttonWidth, 0)) {
            selectType(type);
        }

        ImGui.popStyleColor(3);

        // Tooltip with full name and description
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(type.getDisplayName() + "\n" + type.getDescription());
        }
    }

    private String abbreviate(String name) {
        if (name.length() <= 5) return name;
        if (name.contains(" ")) {
            String[] parts = name.split(" ");
            return parts[parts.length - 1]; // Last word
        }
        return name.substring(0, 4);
    }

    private void selectType(CollisionType type) {
        this.selectedType = type;
        if (onTypeSelected != null) {
            onTypeSelected.accept(type);
        }
    }

    public void setSelectedType(CollisionType type) {
        selectType(type);
    }
}
