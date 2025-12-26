package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.prefab.Prefab;
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
 * Displays: Scene Camera, Tilemap Layers, Collision Map, Entities.
 * Listens to EditorModeManager for mode changes to sync selection state.
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

    @Getter
    private boolean tilemapLayersSelected = false;

    @Getter
    private boolean collisionMapSelected = false;

    // Rename state
    private Object renamingItem = null;
    private final ImString renameBuffer = new ImString(64);

    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    /**
     * Initializes the panel and registers for mode change events.
     * Call after setting modeManager.
     */
    public void init() {
        if (modeManager != null) {
            modeManager.onModeChanged(this::onModeChanged);
        }
    }

    /**
     * Called when editor mode changes from any source.
     */
    private void onModeChanged(EditorModeManager.Mode mode) {
        switch (mode) {
            case TILEMAP -> {
                cameraSelected = false;
                tilemapLayersSelected = true;
                collisionMapSelected = false;
                if (scene != null) {
                    scene.setSelectedEntity(null);
                }
            }
            case COLLISION -> {
                cameraSelected = false;
                tilemapLayersSelected = false;
                collisionMapSelected = true;
                if (scene != null) {
                    scene.setSelectedEntity(null);
                }
            }
            case ENTITY -> {
                tilemapLayersSelected = false;
                collisionMapSelected = false;
            }
        }
    }

    public void render() {
        if (ImGui.begin("Hierarchy")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            ImGui.text(FontAwesomeIcons.Map + " " + scene.getName());
            ImGui.separator();

            renderCameraItem();
            renderTilemapLayersItem();
            renderCollisionMapItem();

            ImGui.separator();

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

        ImGui.treeNodeEx("##camera", flags, FontAwesomeIcons.Camera + " Scene Camera");

        if (ImGui.isItemClicked()) {
            selectCamera();
        }
    }

    public void selectCamera() {
        cameraSelected = true;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        if (scene != null) {
            scene.setSelectedEntity(null);
            scene.setActiveLayer(-1);
        }
    }

    // ========================================================================
    // TILEMAP LAYERS
    // ========================================================================

    private void renderTilemapLayersItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (tilemapLayersSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        int layerCount = scene != null ? scene.getLayerCount() : 0;
        ImGui.treeNodeEx("##tilemapLayers", flags, FontAwesomeIcons.LayerGroup + " Tilemap Layers (" + layerCount + ")");

        if (ImGui.isItemClicked()) {
            selectTilemapLayers();
        }
    }

    public void selectTilemapLayers() {
        cameraSelected = false;
        if (modeManager != null) {
            modeManager.switchToTilemap();
        }
        if (toolManager != null && brushTool != null) {
            toolManager.setActiveTool(brushTool);
        }
    }

    // ========================================================================
    // COLLISION MAP
    // ========================================================================

    private void renderCollisionMapItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (collisionMapSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##collisionMap", flags, FontAwesomeIcons.BorderAll + " Collision Map");

        if (ImGui.isItemClicked()) {
            selectCollisionMap();
        }
    }

    public void selectCollisionMap() {
        cameraSelected = false;
        if (modeManager != null) {
            modeManager.switchToCollision();
        }
    }

    // ========================================================================
    // ENTITIES SECTION
    // ========================================================================

    private void renderEntitiesSection() {
        List<EditorEntity> entities = scene.getEntities();
        EditorEntity selected = scene.getSelectedEntity();

        if (entities.isEmpty()) {
            ImGui.textDisabled("No entities");
        } else {
            for (EditorEntity entity : entities) {
                renderEntityItem(entity, selected);
            }
        }

        ImGui.separator();

        if (ImGui.smallButton(FontAwesomeIcons.Plus + " New Entity")) {
            createEmptyEntity();
        }
        ImGui.sameLine();
        ImGui.textDisabled("or use Prefabs panel");
    }

    private void createEmptyEntity() {
        Vector3f position = new Vector3f(0, 0, 0);
        int count = scene.getEntities().size();
        String name = "Entity_" + (count + 1);

        EditorEntity entity = new EditorEntity(name, position, false);
        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
        selectEntity(entity);
        scene.markDirty();
    }

    private void renderEntityItem(EditorEntity entity, EditorEntity selected) {
        boolean isSelected = entity == selected && !cameraSelected && !tilemapLayersSelected && !collisionMapSelected;
        boolean isRenaming = entity == renamingItem;

        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (isSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.pushID(entity.getId());

        if (isRenaming) {
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
            String icon = entity.isScratchEntity() ? FontAwesomeIcons.Cube
                    : entity.isPrefabValid() ? FontAwesomeIcons.Cubes
                    : FontAwesomeIcons.ExclamationTriangle;

            ImGui.treeNodeEx("##entity", flags, icon + " " + entity.getName());

            if (ImGui.isItemClicked()) {
                selectEntity(entity);
            }

            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                renamingItem = entity;
                renameBuffer.set(entity.getName());
            }

            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                if (entity.isScratchEntity()) {
                    ImGui.text("Scratch Entity");
                    int compCount = entity.getComponents().size();
                    ImGui.textDisabled(compCount + " component" + (compCount != 1 ? "s" : ""));
                } else {
                    Prefab prefab = entity.getPrefab();
                    if (prefab != null) {
                        ImGui.text(prefab.getDisplayName());
                    } else {
                        ImGui.textColored(1f, 0.5f, 0.2f, 1f, "Missing prefab: " + entity.getPrefabId());
                    }
                }
                Vector3f pos = entity.getPosition();
                ImGui.textDisabled(String.format("Position: (%.1f, %.1f)", pos.x, pos.y));
                ImGui.endTooltip();
            }

            if (ImGui.beginPopupContextItem("entity_ctx_" + entity.getId())) {
                if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                    renamingItem = entity;
                    renameBuffer.set(entity.getName());
                }

                if (ImGui.menuItem(FontAwesomeIcons.Copy + " Duplicate")) {
                    duplicateEntity(entity);
                }

                if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
                    if (ImGui.menuItem(FontAwesomeIcons.Save + " Save as Prefab...")) {
                        savePrefabPopup.open(entity, savedPrefab -> {
                            System.out.println("Saved prefab: " + savedPrefab.getId());
                        });
                    }
                }

                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) {
                    scene.removeEntity(entity);
                }

                ImGui.endPopup();
            }
        }
        savePrefabPopup.render();
        ImGui.popID();
    }

    private void selectEntity(EditorEntity entity) {
        cameraSelected = false;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
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
        EditorEntity copy;

        if (original.isScratchEntity()) {
            copy = new EditorEntity(original.getName() + "_copy", newPos, false);
            for (ComponentData comp : original.getComponents()) {
                ComponentData compCopy = new ComponentData(comp.getType());
                compCopy.getFields().putAll(comp.getFields());
                copy.addComponent(compCopy);
            }
        } else {
            copy = new EditorEntity(original.getPrefabId(), newPos);
            copy.setName(original.getName() + "_copy");
            for (ComponentData comp : original.getComponents()) {
                String componentType = comp.getType();
                for (String fieldName : original.getOverriddenFields(componentType)) {
                    Object value = original.getFieldValue(componentType, fieldName);
                    copy.setFieldValue(componentType, fieldName, value);
                }
            }
        }

        scene.addEntity(copy);
        selectEntity(copy);
        scene.markDirty();
    }

    public void clearSelection() {
        cameraSelected = false;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        if (scene != null) {
            scene.setSelectedEntity(null);
        }
    }
}