package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Unified hierarchy panel showing scene structure.
 * <p>
 * Displays:
 * - Scene Camera (always first)
 * - Layers section (tilemap layers)
 * - Entities section (placed entity instances)
 * <p>
 * Supports selection, renaming, and context menus.
 */
public class HierarchyPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorModeManager modeManager;

    @Setter
    private ToolManager toolManager;

    @Setter
    private EditorTool selectionTool;

    @Setter
    private EditorTool brushTool;

    // Selection state
    @Getter
    private boolean cameraSelected = false;

    // Rename state
    private Object renamingItem = null;
    private final ImString renameBuffer = new ImString(64);

    /**
     * Renders the hierarchy panel.
     */
    public void render() {
        if (ImGui.begin("Hierarchy")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            // Scene name header
            ImGui.text(FontAwesomeIcons.Map + " " + scene.getName());
            ImGui.separator();

            // Camera item (always first)
            renderCameraItem();

            ImGui.separator();

            // Layers section
            renderLayersSection();

            // Entities section
            renderEntitiesSection();
        }
        ImGui.end();
    }

    // ========================================================================
    // CAMERA
    // ========================================================================

    private void renderCameraItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;

        if (cameraSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        String label = FontAwesomeIcons.Camera + " Scene Camera";
        ImGui.treeNodeEx("##camera", flags, label);

        if (ImGui.isItemClicked()) {
            selectCamera();
        }
    }

    /**
     * Selects the camera (deselects everything else).
     */
    public void selectCamera() {
        cameraSelected = true;
        if (scene != null) {
            scene.setSelectedEntity(null);
            scene.setActiveLayer(-1);
        }
    }

    // ========================================================================
    // LAYERS SECTION
    // ========================================================================

    private void renderLayersSection() {
        int headerFlags = ImGuiTreeNodeFlags.DefaultOpen;

        if (ImGui.collapsingHeader("Layers", headerFlags)) {
            List<TilemapLayer> layers = scene.getLayers();

            if (layers.isEmpty()) {
                ImGui.textDisabled("No layers");
            } else {
                for (int i = 0; i < layers.size(); i++) {
                    renderLayerItem(i, layers.get(i));
                }
            }

            // Add layer button
            if (ImGui.smallButton(FontAwesomeIcons.Plus + " Add Layer")) {
                scene.addLayer("Layer " + scene.getLayerCount());
            }
        }
    }

    private void renderLayerItem(int index, TilemapLayer layer) {
        boolean isActive = index == scene.getActiveLayerIndex() && !cameraSelected;
        boolean isRenaming = layer == renamingItem;

        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;

        if (isActive && modeManager != null && modeManager.isTilemapMode()) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.pushID(index);

        if (isRenaming) {
            // Rename mode
            ImGui.setNextItemWidth(120);
            ImGui.setKeyboardFocusHere();

            int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll;
            if (ImGui.inputText("##rename", renameBuffer, inputFlags)) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty()) {
                    scene.renameLayer(index, newName);
                }
                renamingItem = null;
            }

            // Cancel on escape or click outside
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                renamingItem = null;
            }
        } else {
            // Normal display
            String visIcon = layer.isVisible() ? FontAwesomeIcons.Eye : FontAwesomeIcons.EyeSlash;
            String label = visIcon + " " + layer.getName() + " (z:" + layer.getZIndex() + ")";

            ImGui.treeNodeEx("##layer" + index, flags, label);

            if (ImGui.isItemClicked()) {
                selectLayer(index);
            }

            // Double-click to rename
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                renamingItem = layer;
                renameBuffer.set(layer.getName());
            }

            // Context menu
            if (ImGui.beginPopupContextItem("layer_ctx_" + index)) {
                if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                    renamingItem = layer;
                    renameBuffer.set(layer.getName());
                }

                if (ImGui.menuItem(layer.isVisible() ? FontAwesomeIcons.EyeSlash + " Hide" : FontAwesomeIcons.Eye + " Show")) {
                    layer.setVisible(!layer.isVisible());
                    scene.markDirty();
                }

                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.ArrowUp + " Move Up")) {
                    layer.setZIndex(layer.getZIndex() + 1);
                    scene.markDirty();
                }

                if (ImGui.menuItem(FontAwesomeIcons.ArrowDown + " Move Down")) {
                    layer.setZIndex(layer.getZIndex() - 1);
                    scene.markDirty();
                }

                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) {
                    scene.removeLayer(index);
                }

                ImGui.endPopup();
            }
        }

        ImGui.popID();
    }

    private void selectLayer(int index) {
        cameraSelected = false;
        scene.setSelectedEntity(null);
        scene.setActiveLayer(index);

        if (modeManager != null) {
            modeManager.switchToTilemap();
        }

        if (toolManager != null && brushTool != null) {
            toolManager.setActiveTool(brushTool);
        }
    }

    // ========================================================================
    // ENTITIES SECTION
    // ========================================================================

    private void renderEntitiesSection() {
        int headerFlags = ImGuiTreeNodeFlags.DefaultOpen;

        if (ImGui.collapsingHeader("Entities", headerFlags)) {
            List<EditorEntity> entities = scene.getEntities();
            EditorEntity selected = scene.getSelectedEntity();

            if (entities.isEmpty()) {
                ImGui.textDisabled("No entities");
                ImGui.textDisabled("Use Prefabs panel to place");
            } else {
                for (EditorEntity entity : entities) {
                    renderEntityItem(entity, selected);
                }
            }
        }
    }

    private void renderEntityItem(EditorEntity entity, EditorEntity selected) {
        boolean isSelected = entity == selected && !cameraSelected;
        boolean isRenaming = entity == renamingItem;

        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;

        if (isSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.pushID(entity.getId());

        if (isRenaming) {
            // Rename mode
            ImGui.setNextItemWidth(150);
            ImGui.setKeyboardFocusHere();

            int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll;
            if (ImGui.inputText("##rename", renameBuffer, inputFlags)) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty()) {
                    entity.setName(newName);
                    scene.markDirty();
                }
                renamingItem = null;
            }

            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                renamingItem = null;
            }
        } else {
            // Normal display
            String icon = FontAwesomeIcons.Cube;

            // Check if prefab is valid
            if (!entity.isPrefabValid()) {
                icon = FontAwesomeIcons.ExclamationTriangle;
            }

            String label = icon + " " + entity.getName();

            ImGui.treeNodeEx("##entity", flags, label);

            if (ImGui.isItemClicked()) {
                selectEntity(entity);
            }

            // Double-click to rename
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                renamingItem = entity;
                renameBuffer.set(entity.getName());
            }

            // Tooltip with details
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                Prefab prefab = entity.getPrefab();
                if (prefab != null) {
                    ImGui.text(prefab.getDisplayName());
                } else {
                    ImGui.textColored(1f, 0.5f, 0.2f, 1f, "Missing prefab: " + entity.getPrefabId());
                }
                Vector3f pos = entity.getPosition();
                ImGui.textDisabled(String.format("Position: (%.1f, %.1f)", pos.x, pos.y));
                ImGui.endTooltip();
            }

            // Context menu
            if (ImGui.beginPopupContextItem("entity_ctx_" + entity.getId())) {
                if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                    renamingItem = entity;
                    renameBuffer.set(entity.getName());
                }

                if (ImGui.menuItem(FontAwesomeIcons.Copy + " Duplicate")) {
                    duplicateEntity(entity);
                }

                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) {
                    scene.removeEntity(entity);
                }

                ImGui.endPopup();
            }
        }

        ImGui.popID();
    }

    private void selectEntity(EditorEntity entity) {
        cameraSelected = false;
        scene.setSelectedEntity(entity);

        if (modeManager != null) {
            modeManager.switchToEntity();
        }

        if (toolManager != null && selectionTool != null) {
            toolManager.setActiveTool(selectionTool);
        }
    }

    private void duplicateEntity(EditorEntity original) {
        Vector3f newPos = new Vector3f(original.getPosition()).add(1, 0, 0);
        EditorEntity copy = new EditorEntity(original.getPrefabId(), newPos);

        // Copy properties
        for (var entry : original.getProperties().entrySet()) {
            copy.setProperty(entry.getKey(), entry.getValue());
        }

        scene.addEntity(copy);
        selectEntity(copy);
    }

    /**
     * Clears any selection in the hierarchy.
     */
    public void clearSelection() {
        cameraSelected = false;
        if (scene != null) {
            scene.setSelectedEntity(null);
        }
    }
}
