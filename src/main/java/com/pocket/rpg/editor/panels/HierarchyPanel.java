package com.pocket.rpg.editor.panels;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.PrefabEditController;
import com.pocket.rpg.editor.SelectionGuard;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeSelectionManager;
import com.pocket.rpg.editor.assets.HierarchyDropTarget;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.EntityCreationService;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyDragDropHandler;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.panels.hierarchy.HierarchySelectionHandler;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyTreeRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.scenes.Scene;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Unified hierarchy panel - orchestrates tree rendering, selection, drag-drop, and entity creation.
 */
public class HierarchyPanel extends EditorPanel {

    private static final String PANEL_ID = "hierarchy";

    @Setter
    private EditorScene scene;

    @Setter
    private PlayModeController playModeController;

    @Setter
    private PrefabEditController prefabEditController;

    private final HierarchySelectionHandler selectionHandler = new HierarchySelectionHandler();
    private final HierarchyDragDropHandler dragDropHandler = new HierarchyDragDropHandler();
    @Getter
    private final EntityCreationService creationService = new EntityCreationService();
    private final HierarchyTreeRenderer treeRenderer = new HierarchyTreeRenderer();

    public HierarchyPanel() {
        super(PANEL_ID, true); // Default open - core panel
    }

    public void setToolManager(ToolManager toolManager) {
        selectionHandler.setToolManager(toolManager);
    }

    public void setSelectionTool(EditorTool selectionTool) {
        selectionHandler.setSelectionTool(selectionTool);
    }

    public void setBrushTool(EditorTool brushTool) {
        selectionHandler.setBrushTool(brushTool);
    }

    public void setSelectionManager(SelectionGuard selectionManager) {
        selectionHandler.setSelectionManager(selectionManager);
    }

    public void setUiFactory(UIEntityFactory uiFactory) {
        creationService.setUiFactory(uiFactory);
    }

    public void setUiController(com.pocket.rpg.editor.EditorUIController uiController) {
        selectionHandler.setUiController(uiController);
    }

    public boolean isCameraSelected() {
        return selectionHandler.isCameraSelected();
    }

    public boolean isTilemapLayersSelected() {
        return selectionHandler.isTilemapLayersSelected();
    }

    public boolean isCollisionMapSelected() {
        return selectionHandler.isCollisionMapSelected();
    }

    public void init() {
        selectionHandler.init();

        // Wire up dependencies
        selectionHandler.setScene(scene);
        dragDropHandler.setScene(scene);
        creationService.setScene(scene);
        creationService.setSelectionHandler(selectionHandler);
        treeRenderer.setScene(scene);
        treeRenderer.setSelectionHandler(selectionHandler);
        treeRenderer.setDragDropHandler(dragDropHandler);
        treeRenderer.setCreationService(creationService);
    }

    public void selectCamera() {
        selectionHandler.selectCamera();
    }

    public void selectTilemapLayers() {
        selectionHandler.selectTilemapLayers();
    }

    public void selectCollisionMap() {
        selectionHandler.selectCollisionMap();
    }


    @Override
    public void render() {
        if (!isOpen()) return;

        if (ImGui.begin("Hierarchy")) {
            if (isPlayMode()) {
                renderPlayModeHeader();
                renderRuntimeHierarchy();
            } else if (isPrefabEditMode()) {
                renderPrefabEditHierarchy();
            } else {
                renderEditorHierarchy();
            }
        }
        ImGui.end();

        if (!isPlayMode() && !isPrefabEditMode()) {
            dragDropHandler.resetDropTarget();
        }
    }

    private boolean isPlayMode() {
        return playModeController != null && playModeController.isActive();
    }

    private boolean isPrefabEditMode() {
        return prefabEditController != null && prefabEditController.isActive();
    }

    private void renderEditorHierarchy() {
        if (scene == null) {
            ImGui.textDisabled("No scene loaded");
            return;
        }

        // Update scene references
        selectionHandler.setScene(scene);
        dragDropHandler.setScene(scene);
        creationService.setScene(scene);
        treeRenderer.setScene(scene);

        ImGui.text(IconUtils.getSceneIcon() + " " + scene.getName());
        ImGui.sameLine(ImGui.getContentRegionMaxX() - 15);
        if (ImGui.smallButton(MaterialIcons.Add)) {
            ImGui.openPopup("CreateEntity_Popup");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add...");
        }

        ImGui.separator();

        renderCameraItem();
        renderTilemapLayersItem();
        renderCollisionMapItem();

        ImGui.separator();

        renderEntitiesSection();
        renderEntityCreationMenu();

        // Detect click on empty space to deselect all
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && ImGui.isWindowHovered()
                && !ImGui.isAnyItemHovered()) {
            selectionHandler.clearSelection();
        }
    }

    private void renderPlayModeHeader() {
        ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.6f, 0.2f, 1f);
        ImGui.text(MaterialIcons.PlayArrow + " PLAY MODE");
        ImGui.popStyleColor();
        ImGui.separator();
    }

    private void renderRuntimeHierarchy() {
        Scene runtimeScene = playModeController.getRuntimeScene();
        if (runtimeScene == null) {
            ImGui.textDisabled("No runtime scene");
            return;
        }

        PlayModeSelectionManager selMgr = playModeController.getPlayModeSelectionManager();

        // Camera item
        renderRuntimeCameraItem(selMgr);

        ImGui.separator();

        // Entities
        List<GameObject> allObjects = runtimeScene.getGameObjects();
        List<GameObject> rootObjects = allObjects.stream()
                .filter(obj -> obj.getParent() == null)
                .toList();

        if (rootObjects.isEmpty()) {
            ImGui.textDisabled("No entities");
        } else {
            for (GameObject obj : rootObjects) {
                HierarchyItem adapter = RuntimeGameObjectAdapter.of(obj);
                treeRenderer.renderHierarchyItemTree(adapter, selMgr);
            }
        }

        // Click empty space to deselect
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && ImGui.isWindowHovered()
                && !ImGui.isAnyItemHovered()) {
            if (selMgr != null) {
                selMgr.clearSelection();
            }
        }
    }

    private void renderRuntimeCameraItem(PlayModeSelectionManager selMgr) {
        int flags = ImGuiTreeNodeFlags.Leaf
                | ImGuiTreeNodeFlags.NoTreePushOnOpen
                | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (selMgr != null && selMgr.isCameraSelected()) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##runtimeCamera", flags,
                IconUtils.getCameraIcon() + " Scene Camera");

        if (ImGui.isItemClicked() && selMgr != null) {
            selMgr.selectCamera();
        }
    }

    private void renderPrefabEditHierarchy() {
        // ===== Fixed Header Section (non-scrollable) =====
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.0f, 0.15f, 0.15f, 1.0f);
        if (ImGui.beginChild("##prefabControlBar", 0, 140, true)) {
            // Teal header
            ImGui.pushStyleColor(ImGuiCol.Text, 0.0f, 0.8f, 0.8f, 1f);
            ImGui.text(MaterialIcons.Widgets + " PREFAB MODE");
            ImGui.popStyleColor();

            // Prefab info
            if (prefabEditController != null) {
                com.pocket.rpg.prefab.JsonPrefab prefab = prefabEditController.getTargetPrefab();
                if (prefab != null) {
                    ImGui.text("Editing: " + prefab.getDisplayName());
                    ImGui.textDisabled("(" + prefab.getId() + ")");
                    int instanceCount = countPrefabInstances(prefab.getId());
                    ImGui.text(instanceCount + " instance" + (instanceCount != 1 ? "s" : "") + " in scene");
                }
            }

            ImGui.separator();

            // Buttons
            float buttonWidth = ImGui.getContentRegionAvailX();
            boolean isDirty = prefabEditController != null && prefabEditController.isDirty();

            // Save button (green when dirty)
            if (isDirty) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
            }
            if (ImGui.button(MaterialIcons.Save + " Save", buttonWidth, 0)) {
                prefabEditController.save();
            }
            if (isDirty) {
                ImGui.popStyleColor(2);
            }

            // Save & Exit
            if (ImGui.button(MaterialIcons.SaveAs + " Save & Exit", buttonWidth, 0)) {
                prefabEditController.saveAndExit();
            }

            // Revert all (disabled when clean)
            if (!isDirty) ImGui.beginDisabled();
            if (ImGui.button(MaterialIcons.Restore + " Revert all", buttonWidth, 0)) {
                prefabEditController.resetToSaved();
            }
            if (!isDirty) ImGui.endDisabled();

            // Exit
            if (ImGui.button(MaterialIcons.ExitToApp + " Exit", buttonWidth, 0)) {
                prefabEditController.requestExit(null);
            }
        }
        ImGui.endChild();
        ImGui.popStyleColor();

        ImGui.separator();

        // ===== Scrollable Entity Section =====
        if (ImGui.beginChild("##prefabEntities", 0, 0, false)) {
            EditorGameObject workingEntity = prefabEditController.getWorkingEntity();
            if (workingEntity != null) {
                // Single entity item - always selected in prefab edit mode
                int flags = ImGuiTreeNodeFlags.Leaf
                        | ImGuiTreeNodeFlags.NoTreePushOnOpen
                        | ImGuiTreeNodeFlags.SpanAvailWidth
                        | ImGuiTreeNodeFlags.Selected;

                String label = IconUtils.getScratchEntityIcon() + " " + workingEntity.getName();
                ImGui.treeNodeEx("##prefabEntity", flags, label);
            } else {
                ImGui.textDisabled("No working entity");
            }
        }
        ImGui.endChild();
    }

    private int countPrefabInstances(String prefabId) {
        com.pocket.rpg.editor.scene.EditorScene currentScene = scene;
        if (currentScene == null) return 0;
        int count = 0;
        for (EditorGameObject entity : currentScene.getEntities()) {
            if (prefabId.equals(entity.getPrefabId())) {
                count++;
            }
        }
        return count;
    }

    private void renderCameraItem() {
        int flags = imgui.flag.ImGuiTreeNodeFlags.Leaf |
                imgui.flag.ImGuiTreeNodeFlags.NoTreePushOnOpen |
                imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
        if (selectionHandler.isCameraSelected()) {
            flags |= imgui.flag.ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##camera", flags, IconUtils.getCameraIcon() + " Scene Camera");

        if (ImGui.isItemClicked()) {
            selectionHandler.selectCamera();
        }
    }

    private void renderTilemapLayersItem() {
        int flags = imgui.flag.ImGuiTreeNodeFlags.Leaf |
                imgui.flag.ImGuiTreeNodeFlags.NoTreePushOnOpen |
                imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
        if (selectionHandler.isTilemapLayersSelected()) {
            flags |= imgui.flag.ImGuiTreeNodeFlags.Selected;
        }

        int layerCount = scene != null ? scene.getLayerCount() : 0;
        ImGui.treeNodeEx("##tilemapLayers", flags, IconUtils.getLayersIcon() + " Tilemap Layers (" + layerCount + ")");

        if (ImGui.isItemClicked()) {
            selectionHandler.selectTilemapLayers();
        }
    }

    private void renderCollisionMapItem() {
        int flags = imgui.flag.ImGuiTreeNodeFlags.Leaf |
                imgui.flag.ImGuiTreeNodeFlags.NoTreePushOnOpen |
                imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth;
        if (selectionHandler.isCollisionMapSelected()) {
            flags |= imgui.flag.ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##collisionMap", flags, IconUtils.getCollisionsIcon() + " Collision Map");

        if (ImGui.isItemClicked()) {
            selectionHandler.selectCollisionMap();
        }
    }

    private void renderEntitiesSection() {
        List<EditorGameObject> rootEntities = scene.getRootEntities();
        rootEntities.sort(Comparator.comparingInt(EditorGameObject::getOrder));

        if (rootEntities.isEmpty()) {
            ImGui.textDisabled("No entities");
            dragDropHandler.renderDropZone(null, 0, null);
        } else {
            for (int i = 0; i < rootEntities.size(); i++) {
                EditorGameObject entity = rootEntities.get(i);
                dragDropHandler.renderDropZone(null, i, entity);
                treeRenderer.renderEntityTree(entity);
            }
            dragDropHandler.renderDropZone(null, rootEntities.size(), null);
        }

        renderMultiSelectionContextMenu();
        HierarchyDropTarget.handleEmptyAreaDrop(scene);

        // If the user clicks on the invisible drop target (the "empty space"), clear selection
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            selectionHandler.clearSelection();
        }
    }

    private void renderEntityCreationMenu() {
        if (ImGui.beginPopup("CreateEntity_Popup")) {
            renderCreateEntityMenuItems();
            ImGui.endPopup();
        }
    }

    private void renderMultiSelectionContextMenu() {
        // NoOpenOverItems prevents this from overriding entity-specific context menus
        int popupFlags = ImGuiPopupFlags.MouseButtonRight | ImGuiPopupFlags.NoOpenOverItems;
        if (ImGui.beginPopupContextWindow("hierarchy_ctx", popupFlags)) {
            Set<EditorGameObject> selected = scene.getSelectedEntities();

            // Always show create options (creates as child if entity selected)
            renderCreateEntityMenuItems();

            // Selection-specific actions
            if (!selected.isEmpty()) {
                ImGui.separator();

                if (selected.size() == 1) {
                    ImGui.text("1 selected");
                } else {
                    ImGui.text(selected.size() + " selected");
                }

                if (ImGui.menuItem(MaterialIcons.Delete + " Delete Selected")) {
                    UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
                }

                if (ImGui.menuItem(MaterialIcons.Cancel + " Clear Selection")) {
                    scene.clearSelection();
                }
            }

            ImGui.endPopup();
        }
    }

    /**
     * Renders the shared create entity menu items.
     * Used by both the Add button popup and the context menu.
     */
    private void renderCreateEntityMenuItems() {
        if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Entity")) {
            creationService.createEmptyEntity();
        }

        if (ImGui.beginMenu(MaterialIcons.Widgets + " Create UI")) {
            // Canvas
            if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                creationService.createUIElement("Canvas");
            }
            ImGui.separator();

            // Basic elements
            if (ImGui.menuItem(IconUtils.getUIPanelIcon() + " Panel")) {
                creationService.createUIElement("Panel");
            }
            if (ImGui.menuItem(IconUtils.getUIImageIcon() + " Image")) {
                creationService.createUIElement("Image");
            }
            if (ImGui.menuItem(IconUtils.getUIButtonIcon() + " Button")) {
                creationService.createUIElement("Button");
            }
            if (ImGui.menuItem(IconUtils.getUITextIcon() + " Text")) {
                creationService.createUIElement("Text");
            }
            ImGui.separator();

            // Layout submenu
            if (ImGui.beginMenu(MaterialIcons.ViewModule + " Layout")) {
                if (ImGui.menuItem(IconUtils.getUIHorizontalLayoutIcon() + " Horizontal Layout")) {
                    creationService.createUIElement("HorizontalLayout");
                }
                if (ImGui.menuItem(IconUtils.getUIVerticalLayoutIcon() + " Vertical Layout")) {
                    creationService.createUIElement("VerticalLayout");
                }
                if (ImGui.menuItem(IconUtils.getUIGridLayoutIcon() + " Grid Layout")) {
                    creationService.createUIElement("GridLayout");
                }
                ImGui.endMenu();
            }

            ImGui.endMenu();
        }
    }
}
