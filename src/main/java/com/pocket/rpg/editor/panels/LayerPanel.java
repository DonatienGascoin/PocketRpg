package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerVisibilityMode;
import com.pocket.rpg.editor.scene.TilemapLayer;
import imgui.ImGui;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import lombok.Setter;

import java.util.List;

/**
 * ImGui panel for managing tilemap layers.
 * 
 * Features:
 * - Layer list with selection
 * - Add/delete layers
 * - Rename layers (via context menu)
 * - Reorder layers (move up/down)
 * - Visibility toggle per layer
 * - Lock toggle per layer
 * - Global visibility mode (All / Selected Only / Selected + Dimmed)
 */
public class LayerPanel {
    
    @Setter
    private EditorScene scene;
    
    // Rename dialog state
    private boolean showRenameDialog = false;
    private int renameLayerIndex = -1;
    private final ImString renameBuffer = new ImString(64);
    
    // Add layer dialog state
    private boolean showAddLayerDialog = false;
    private final ImString newLayerNameBuffer = new ImString("New Layer", 64);
    
    /**
     * Renders the layer panel.
     */
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
                newLayerNameBuffer.set("Layer " + (scene.getLayerCount() + 1));
            }
            
            ImGui.sameLine();
            
            // Delete layer button
            boolean canDelete = scene.getActiveLayer() != null;
            if (!canDelete) ImGui.beginDisabled();
            if (ImGui.button("Delete")) {
                int activeIndex = scene.getActiveLayerIndex();
                if (activeIndex >= 0) {
                    scene.removeLayer(activeIndex);
                }
            }
            if (!canDelete) ImGui.endDisabled();
            
            ImGui.separator();
            
            // Layer list (render in reverse order - top layer first visually)
            renderLayerList();
        }
        ImGui.end();
        
        // Dialogs
        renderAddLayerDialog();
        renderRenameDialog();
    }
    
    /**
     * Renders the visibility mode selector.
     */
    private void renderVisibilityModeSelector() {
        ImGui.text("Visibility:");
        ImGui.sameLine();
        
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
        
        // Dimmed opacity slider (only show when in SELECTED_DIMMED mode)
        if (currentMode == LayerVisibilityMode.SELECTED_DIMMED) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(150);
            float[] opacity = {scene.getDimmedOpacity()};
            if (ImGui.sliderFloat("Dim Opacity", opacity, 0.1f, 0.9f, "%.1f")) {
                scene.setDimmedOpacity(opacity[0]);
            }
        }
    }
    
    /**
     * Renders the layer list.
     */
    private void renderLayerList() {
        List<TilemapLayer> layers = scene.getLayers();
        int activeIndex = scene.getActiveLayerIndex();
        
        // Render in reverse order (top layer first in UI)
        for (int i = layers.size() - 1; i >= 0; i--) {
            TilemapLayer layer = layers.get(i);
            
            ImGui.pushID(i);
            
            // Visibility checkbox
            boolean visible = layer.isVisible();
            if (ImGui.checkbox("##visible", visible)) {
                layer.setVisible(!visible);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Toggle visibility");
            }
            
            ImGui.sameLine();
            
            // Lock checkbox
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
            boolean isActive = (i == activeIndex);
            int flags = ImGuiSelectableFlags.SpanAllColumns;
            
            // Build label with zIndex
            String label = String.format("%s (z:%d)", layer.getName(), layer.getZIndex());
            
            if (ImGui.selectable(label, isActive, flags)) {
                scene.setActiveLayer(i);
            }
            
            // Context menu
            if (ImGui.beginPopupContextItem("layer_context_" + i)) {
                if (ImGui.menuItem("Rename")) {
                    renameLayerIndex = i;
                    renameBuffer.set(layer.getName());
                    showRenameDialog = true;
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("Move Up", "", false, i < layers.size() - 1)) {
                    scene.moveLayerUp(i);
                }
                if (ImGui.menuItem("Move Down", "", false, i > 0)) {
                    scene.moveLayerDown(i);
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("Delete")) {
                    scene.removeLayer(i);
                }
                
                ImGui.endPopup();
            }
            
            // Drag and drop reordering (future enhancement)
            // if (ImGui.beginDragDropSource()) { ... }
            
            ImGui.popID();
        }
        
        // Show message if no layers
        if (layers.isEmpty()) {
            ImGui.textDisabled("No layers. Click '+ Add Layer' to create one.");
        }
    }
    
    /**
     * Renders the add layer dialog.
     */
    private void renderAddLayerDialog() {
        if (!showAddLayerDialog) return;
        
        ImGui.openPopup("Add Layer");
        
        if (ImGui.beginPopupModal("Add Layer")) {
            ImGui.text("Layer Name:");
            ImGui.inputText("##layername", newLayerNameBuffer);
            
            ImGui.separator();
            
            if (ImGui.button("Create")) {
                String name = newLayerNameBuffer.get().trim();
                if (!name.isEmpty()) {
                    // Create layer with default spritesheet (Road for testing)
                    scene.addLayer(name, 
                        "gameData/assets/sprites/Road_16x16.png", 16, 16);
                }
                showAddLayerDialog = false;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
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
            ImGui.text("New Name:");
            
            // Auto-focus input on first frame
            if (ImGui.isWindowAppearing()) {
                ImGui.setKeyboardFocusHere();
            }
            
            boolean enterPressed = ImGui.inputText("##rename", renameBuffer, 
                imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
            
            ImGui.separator();
            
            if (ImGui.button("OK") || enterPressed) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty() && renameLayerIndex >= 0) {
                    scene.renameLayer(renameLayerIndex, newName);
                }
                showRenameDialog = false;
                renameLayerIndex = -1;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("Cancel")) {
                showRenameDialog = false;
                renameLayerIndex = -1;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
    }
}
