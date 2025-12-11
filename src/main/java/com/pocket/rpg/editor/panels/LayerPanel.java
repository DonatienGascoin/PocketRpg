package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerVisibilityMode;
import com.pocket.rpg.editor.scene.TilemapLayer;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import lombok.Setter;

/**
 * Panel for managing tilemap layers.
 *
 * Features:
 * - Add/delete/rename layers
 * - Toggle visibility
 * - Toggle lock state
 * - Reorder layers (move up/down)
 * - Visibility mode selection (All/Selected/Dimmed)
 *
 * Note: Layers no longer require a spritesheet at creation.
 * Tiles from any tileset can be placed on any layer.
 */
public class LayerPanel {

    @Setter
    private EditorScene scene;

    // Add layer dialog state
    private boolean showAddLayerDialog = false;
    private ImString newLayerName = new ImString("New Layer", 64);
    private boolean focusNameInput = false;

    // Rename dialog state
    private boolean showRenameDialog = false;
    private int renameLayerIndex = -1;
    private ImString renameBuffer = new ImString(64);

    // Context menu state
    private int contextMenuLayerIndex = -1;

    public void render() {
        if (ImGui.begin("Layers")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            // Visibility mode selector
            renderVisibilityModeSelector();

            ImGui.separator();

            // Add layer button
            if (ImGui.button("+ Add Layer")) {
                showAddLayerDialog = true;
                focusNameInput = true;
                newLayerName.set("Layer " + (scene.getLayerCount() + 1));
            }

            ImGui.separator();

            // Layer list
            renderLayerList();

            // Dialogs
            renderAddLayerDialog();
            renderRenameDialog();
        }
        ImGui.end();
    }

    /**
     * Renders visibility mode radio buttons.
     */
    private void renderVisibilityModeSelector() {
        ImGui.text("Visibility Mode:");

        LayerVisibilityMode currentMode = scene.getVisibilityMode();

        if (ImGui.radioButton("All", currentMode == LayerVisibilityMode.ALL)) {
            scene.setVisibilityMode(LayerVisibilityMode.ALL);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Selected", currentMode == LayerVisibilityMode.SELECTED_ONLY)) {
            scene.setVisibilityMode(LayerVisibilityMode.SELECTED_ONLY);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Dimmed", currentMode == LayerVisibilityMode.SELECTED_DIMMED)) {
            scene.setVisibilityMode(LayerVisibilityMode.SELECTED_DIMMED);
        }

        // Opacity slider for dimmed mode
        if (currentMode == LayerVisibilityMode.SELECTED_DIMMED) {
            float[] opacity = {scene.getDimmedOpacity()};
            if (ImGui.sliderFloat("Dim Opacity", opacity, 0.1f, 0.9f)) {
                scene.setDimmedOpacity(opacity[0]);
            }
        }
    }

    /**
     * Renders the list of layers.
     */
    private void renderLayerList() {
        int layerCount = scene.getLayerCount();

        if (layerCount == 0) {
            ImGui.textDisabled("No layers");
            ImGui.textDisabled("Click '+ Add Layer' to create one");
            return;
        }

        // Render layers from top (highest zIndex) to bottom
        for (int i = layerCount - 1; i >= 0; i--) {
            TilemapLayer layer = scene.getLayer(i);
            if (layer == null) continue;

            ImGui.pushID(i);

            boolean isActive = (i == scene.getActiveLayerIndex());

            // Visibility toggle
            boolean visible = layer.isVisible();
            if (ImGui.checkbox("##visible", visible)) {
                layer.setVisible(!visible);
                scene.markDirty();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Toggle visibility");
            }

            ImGui.sameLine();

            // Lock toggle
            boolean locked = layer.isLocked();
            String lockIcon = locked ? "[L]" : "[ ]";
            if (ImGui.smallButton(lockIcon)) {
                layer.setLocked(!locked);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(locked ? "Unlock layer" : "Lock layer");
            }

            ImGui.sameLine();

            // Layer name (selectable)
            String displayName = layer.getName();
            if (locked) displayName += " (locked)";

            int flags = ImGuiSelectableFlags.AllowDoubleClick;
            if (ImGui.selectable(displayName, isActive, flags)) {
                scene.setActiveLayer(i);

                // Double-click to rename
                if (ImGui.isMouseDoubleClicked(0)) {
                    showRenameDialog = true;
                    renameLayerIndex = i;
                    renameBuffer.set(layer.getName());
                }
            }

            // Context menu
            if (ImGui.beginPopupContextItem("layer_context_" + i, ImGuiPopupFlags.MouseButtonRight)) {
                contextMenuLayerIndex = i;

                if (ImGui.menuItem("Rename")) {
                    showRenameDialog = true;
                    renameLayerIndex = i;
                    renameBuffer.set(layer.getName());
                }

                if (ImGui.menuItem("Delete")) {
                    scene.removeLayer(i);
                }

                ImGui.separator();

                if (ImGui.menuItem("Move Up", "", false, i < layerCount - 1)) {
                    scene.moveLayerUp(i);
                }

                if (ImGui.menuItem("Move Down", "", false, i > 0)) {
                    scene.moveLayerDown(i);
                }

                ImGui.endPopup();
            }

            ImGui.popID();
        }
    }

    /**
     * Renders the add layer dialog.
     */
    private void renderAddLayerDialog() {
        if (!showAddLayerDialog) return;

        ImGui.openPopup("Add Layer");

        if (ImGui.beginPopupModal("Add Layer")) {
            // Focus name input on first frame
            if (focusNameInput) {
                ImGui.setKeyboardFocusHere();
                focusNameInput = false;
            }

            // Layer name input - check for Enter key
            int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue;
            boolean enterPressed = ImGui.inputText("Name", newLayerName, inputFlags);

            ImGui.textDisabled("Tiles from any tileset can be placed on this layer.");

            ImGui.separator();

            // Create button or Enter pressed
            boolean shouldCreate = ImGui.button("Create") || enterPressed;
            if (shouldCreate) {
                String name = newLayerName.get().trim();
                if (!name.isEmpty()) {
                    scene.addLayer(name);
                    showAddLayerDialog = false;
                    ImGui.closeCurrentPopup();
                }
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                showAddLayerDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    /**
     * Renders the rename layer dialog.
     */
    private void renderRenameDialog() {
        if (!showRenameDialog) return;

        ImGui.openPopup("Rename Layer");

        if (ImGui.beginPopupModal("Rename Layer")) {
            // Focus input on first frame
            ImGui.setKeyboardFocusHere();

            int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue;
            boolean enterPressed = ImGui.inputText("Name", renameBuffer, inputFlags);

            boolean shouldRename = ImGui.button("Rename") || enterPressed;
            if (shouldRename) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty() && renameLayerIndex >= 0) {
                    scene.renameLayer(renameLayerIndex, newName);
                }
                showRenameDialog = false;
                renameLayerIndex = -1;
                ImGui.closeCurrentPopup();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel") || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                showRenameDialog = false;
                renameLayerIndex = -1;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }
}