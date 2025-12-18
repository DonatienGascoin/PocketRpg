package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Panel for managing tilemap layers.
 * <p>
 * Features:
 * - Layer list sorted by Z-index (descending)
 * - Z-index display for each layer
 * - Entity reference separator at z=0
 * - Warning for layers at entity z-level
 * - Add/remove/reorder layers
 * - Visibility toggle per layer
 * - Disabled when in collision mode
 */
public class LayerPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorModeManager modeManager;

    // Rename state
    private int renamingLayerIndex = -1;
    private final ImString renameBuffer = new ImString(64);

    // Entity Z-level constant
    private static final int ENTITY_Z_LEVEL = 0;

    /**
     * Renders the layer panel.
     */
    public void render() {
        if (ImGui.begin("Layers")) {
            boolean inCollisionMode = modeManager != null && modeManager.isCollisionMode();

            // Disable entire panel in collision mode
            if (inCollisionMode) {
                ImGui.beginDisabled();
                ImGui.textColored(0.7f, 0.7f, 0.2f, 1.0f,
                        FontAwesomeIcons.ExclamationTriangle + " Collision Mode Active");
                ImGui.separator();
            }

            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
            } else {
                renderLayerControls();
                ImGui.separator();
                renderLayerList();
            }

            if (inCollisionMode) {
                ImGui.endDisabled();
            }
        }
        ImGui.end();
    }

    /**
     * Renders add/remove layer controls.
     */
    private void renderLayerControls() {
        // Add layer button
        if (ImGui.button(FontAwesomeIcons.Plus + " Add Layer")) {
            int layerCount = scene.getLayerCount();
            scene.addLayer("Layer " + layerCount);
        }

        ImGui.sameLine();

        // Remove layer button
        boolean canRemove = scene.getActiveLayer() != null;
        if (!canRemove) {
            ImGui.beginDisabled();
        }
        if (ImGui.button(FontAwesomeIcons.Trash + " Remove")) {
            int activeIndex = scene.getActiveLayerIndex();
            if (activeIndex >= 0) {
                scene.removeLayer(activeIndex);
            }
        }
        if (!canRemove) {
            ImGui.endDisabled();
        }
    }

    /**
     * Renders the layer list sorted by z-index with entity separator.
     */
    private void renderLayerList() {
        List<TilemapLayer> layers = scene.getLayers();
        if (layers.isEmpty()) {
            ImGui.textDisabled("No layers. Click 'Add Layer' to create one.");
            return;
        }

        // Create sorted list with indices
        List<LayerEntry> sortedLayers = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            sortedLayers.add(new LayerEntry(i, layers.get(i)));
        }
        sortedLayers.sort(Comparator.comparingInt((LayerEntry e) -> e.layer.getZIndex()).reversed());

        // Track if we've rendered the entity separator
        boolean entitySeparatorRendered = false;
        int activeIndex = scene.getActiveLayerIndex();

        for (LayerEntry entry : sortedLayers) {
            int zIndex = entry.layer.getZIndex();

            // Render entity separator before first layer at or below z=0
            if (!entitySeparatorRendered && zIndex <= ENTITY_Z_LEVEL) {
                renderEntitySeparator();
                entitySeparatorRendered = true;
            }

            renderLayerItem(entry.originalIndex, entry.layer, activeIndex);
        }

        // If all layers are above z=0, render separator at bottom
        if (!entitySeparatorRendered) {
            renderEntitySeparator();
        }
    }

    /**
     * Renders the entity reference separator.
     */
    private void renderEntitySeparator() {
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.8f, 0.6f, 1.0f);

        // Center the text
        String text = FontAwesomeIcons.User + " -- Entities (z: 0) --";
        float textWidth = ImGui.calcTextSize(text).x;
        float windowWidth = ImGui.getContentRegionAvailX();
        float indent = (windowWidth - textWidth) / 2;
        if (indent > 0) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        }

        ImGui.text(text);
        ImGui.popStyleColor();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Player and entities render at Z-index 0\nLayers above appear in front, layers below appear behind");
        }

        ImGui.spacing();
    }

    /**
     * Renders a single layer item.
     */
    private void renderLayerItem(int index, TilemapLayer layer, int activeIndex) {
        boolean isActive = index == activeIndex;
        boolean isRenaming = index == renamingLayerIndex;
        int zIndex = layer.getZIndex();
        boolean atEntityLevel = zIndex == ENTITY_Z_LEVEL;

        ImGui.pushID(index);

        // Visibility toggle
        boolean visible = layer.isVisible();
        String visIcon = visible ? FontAwesomeIcons.Eye : FontAwesomeIcons.EyeSlash;
        if (ImGui.smallButton(visIcon)) {
            layer.setVisible(!visible);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(visible ? "Hide layer" : "Show layer");
        }

        ImGui.sameLine();

        // Layer name with z-index
        String zDisplay = "(z: " + zIndex + ")";
        if (atEntityLevel) {
            zDisplay = "(z: 0 " + FontAwesomeIcons.ExclamationCircle + ")";
        }

        if (isRenaming) {
            // Rename mode
            ImGui.setNextItemWidth(100);
            ImGui.setKeyboardFocusHere();
            int flags = ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll;
            if (ImGui.inputText("##rename", renameBuffer, flags)) {
                // Enter pressed - confirm
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty()) {
                    scene.renameLayer(index, newName);
                }
                renamingLayerIndex = -1;
            }

            // Cancel on Escape
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                renamingLayerIndex = -1;
            }

            // Cancel on click outside
            if (!ImGui.isItemActive() && ImGui.isMouseClicked(0)) {
                renamingLayerIndex = -1;
            }

            ImGui.sameLine();
            ImGui.textDisabled(zDisplay);
        } else {
            // Normal display - selectable
            String label = layer.getName() + " " + zDisplay;

            // Color warning for entity-level layers
            if (atEntityLevel) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f);
            }

            int flags = ImGuiSelectableFlags.AllowDoubleClick | ImGuiSelectableFlags.AllowItemOverlap;
            if (ImGui.selectable(label, isActive, flags)) {
                scene.setActiveLayer(index);

                // Double-click to rename
                if (ImGui.isMouseDoubleClicked(0)) {
                    renamingLayerIndex = index;
                    renameBuffer.set(layer.getName());
                }
            }

            if (atEntityLevel) {
                ImGui.popStyleColor();
            }

            if (atEntityLevel && ImGui.isItemHovered()) {
                ImGui.setTooltip("Same Z-index as entities!\nTiles may overlap with player/NPCs");
            }
        }

        // Context menu
        if (ImGui.beginPopupContextItem("layer_context_" + index)) {
            if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                renamingLayerIndex = index;
                renameBuffer.set(layer.getName());
            }
            if (ImGui.menuItem(FontAwesomeIcons.ArrowUp + " Move Up")) {
                scene.moveLayerUp(index);
            }
            if (ImGui.menuItem(FontAwesomeIcons.ArrowDown + " Move Down")) {
                scene.moveLayerDown(index);
            }
            ImGui.separator();
            if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) {
                scene.removeLayer(index);
            }
            ImGui.endPopup();
        }

        // Z-index adjustment buttons (inline)
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 50);

        if (ImGui.smallButton(FontAwesomeIcons.ChevronUp + "##up" + index)) {
            layer.setZIndex(zIndex + 1);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Increase Z-index (move forward)");
        }

        ImGui.sameLine();

        if (ImGui.smallButton(FontAwesomeIcons.ChevronDown + "##down" + index)) {
            layer.setZIndex(zIndex - 1);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Decrease Z-index (move backward)");
        }

        ImGui.popID();
    }

    /**
     * Internal class to track layer with original index.
     */
    private record LayerEntry(int originalIndex, TilemapLayer layer) {}
}