package com.pocket.rpg.editor.panels.collisions;

import com.pocket.rpg.collision.CollisionType;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

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

        ImGui.textDisabled("Movement");
        renderCollisionButton(CollisionType.NONE);
        renderCollisionButton(CollisionType.SOLID);

        ImGui.spacing();
        ImGui.textDisabled("Ledges");
        renderCollisionButton(CollisionType.LEDGE_DOWN);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_UP);
        renderCollisionButton(CollisionType.LEDGE_LEFT);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_RIGHT);

        ImGui.spacing();
        ImGui.textDisabled("Terrain");
        renderCollisionButton(CollisionType.WATER);
        renderCollisionButton(CollisionType.TALL_GRASS);
        renderCollisionButton(CollisionType.ICE);
        renderCollisionButton(CollisionType.SAND);

        ImGui.spacing();
        ImGui.textDisabled("Triggers");
        renderCollisionButton(CollisionType.WARP);
        renderCollisionButton(CollisionType.DOOR);
        renderCollisionButton(CollisionType.SCRIPT_TRIGGER);

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Selected: " + selectedType.getDisplayName());
        ImGui.textWrapped(getTypeDescription(selectedType));
    }

    public void renderHorizontal() {
        renderHorizontal(true);
    }

    public void renderHorizontal(boolean enabled) {
        this.enabled = enabled;

        // Movement block
        ImGui.textDisabled("Movement");
        renderCollisionButton(CollisionType.NONE);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.SOLID);

        // Ledges block
        ImGui.spacing();
        ImGui.textDisabled("Ledges");
        renderCollisionButton(CollisionType.LEDGE_DOWN);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_UP);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_LEFT);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.LEDGE_RIGHT);

        // Terrain block
        ImGui.spacing();
        ImGui.textDisabled("Terrain");
        renderCollisionButton(CollisionType.WATER);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.TALL_GRASS);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.ICE);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.SAND);

        // Triggers block
        ImGui.spacing();
        ImGui.textDisabled("Triggers");
        renderCollisionButton(CollisionType.WARP);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.DOOR);
        ImGui.sameLine();
        renderCollisionButton(CollisionType.SCRIPT_TRIGGER);
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
        float buttonWidth = label.contains("Ledge") ? 65 : 140;

        if (ImGui.button(label + "##" + type.name(), buttonWidth, 0)) {
            selectType(type);
        }

        ImGui.popStyleColor(3);

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(getTypeDescription(type));
        }
    }

    private void selectType(CollisionType type) {
        this.selectedType = type;
        if (onTypeSelected != null) {
            onTypeSelected.accept(type);
        }
    }

    public String getTypeDescription(CollisionType type) {
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

    public void setSelectedType(CollisionType type) {
        selectType(type);
    }

}