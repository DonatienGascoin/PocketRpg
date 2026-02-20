package com.pocket.rpg.editor.panels;

import com.pocket.rpg.core.GameObject;
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
import com.pocket.rpg.editor.core.EditorColors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private Set<String> lastSelectedEntityIds = Set.of();

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

        // resetFrame() is called inside renderEntitiesSection() each frame
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

        // Popup must be at the same scope as openPopup (outside the child window)
        renderEntityCreationMenu();

        // Scrollable child region — header stays fixed above
        if (ImGui.beginChild("##sceneEntities", 0, 0, false)) {
            renderEntitiesSection();

            // Detect click on empty space to deselect all
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)
                    && ImGui.isWindowHovered()
                    && !ImGui.isAnyItemHovered()) {
                selectionHandler.clearSelection();
            }
        }
        ImGui.endChild();
    }

    private void renderPlayModeHeader() {
        ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], EditorColors.WARNING[2], EditorColors.WARNING[3]);
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
        if (ImGui.beginChild("##prefabControlBar", 0, 120, true)) {
            // Prefab mode header
            ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.PREFAB[0], EditorColors.PREFAB[1], EditorColors.PREFAB[2], EditorColors.PREFAB[3]);
            ImGui.text(MaterialIcons.Widgets + " PREFAB MODE");
            ImGui.popStyleColor();

            // Prefab info
            if (prefabEditController != null) {
                com.pocket.rpg.prefab.JsonPrefab prefab = prefabEditController.getTargetPrefab();
                if (prefab != null) {
                    ImGui.text("Editing: " + prefab.getDisplayName());
                    ImGui.sameLine();
                    ImGui.textDisabled("(" + prefab.getId() + ")");
                }
            }

            ImGui.separator();

            // Buttons
            float buttonWidth = ImGui.getContentRegionAvailX();
            float halfWidth = (buttonWidth / 2) - ImGui.getStyle().getFramePaddingX();
            boolean isDirty = prefabEditController != null && prefabEditController.isDirty();

            // Save button (green when dirty)
            if (isDirty) {
                EditorColors.pushSuccessButton();
            }
            if (ImGui.button(MaterialIcons.Save + " Save", halfWidth, 0)) {
                prefabEditController.save();
            }

            ImGui.sameLine();

            // Save & Exit (green when dirty)
            if (ImGui.button(MaterialIcons.SaveAs + " Save & Exit", halfWidth, 0)) {
                prefabEditController.saveAndExit();
            }
            if (isDirty) {
                EditorColors.popButtonColors();
            }

            // Revert all (disabled when clean)
            if (!isDirty) ImGui.beginDisabled();
            if (ImGui.button(MaterialIcons.Restore + " Revert all", halfWidth, 0)) {
                prefabEditController.resetToSaved();
            }
            if (!isDirty) ImGui.endDisabled();

            // Exit
            ImGui.sameLine();
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.ExitToApp + " Exit", halfWidth, 0)) {
                prefabEditController.requestExit(null);
            }
            EditorColors.popButtonColors();
        }
        ImGui.endChild();
        ImGui.popStyleColor();

        ImGui.separator();

        // ===== Scrollable Entity Section =====
        if (ImGui.beginChild("##prefabEntities", 0, 0, false)) {
            EditorScene workingScene = prefabEditController.getWorkingScene();
            if (workingScene != null) {
                // Point scene refs to working scene for tree rendering
                selectionHandler.setScene(workingScene);
                dragDropHandler.setScene(workingScene);
                creationService.setScene(workingScene);
                treeRenderer.setScene(workingScene);

                float baseIndentX = ImGui.getCursorScreenPosX();
                dragDropHandler.resetFrame(baseIndentX);

                List<EditorGameObject> rootEntities = workingScene.getRootEntities();
                rootEntities.sort(Comparator.comparingInt(EditorGameObject::getOrder));

                if (rootEntities.isEmpty()) {
                    ImGui.textDisabled("No entities");
                } else {
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
                            ImGui.getStyle().getItemSpacingX(), 1f);
                    for (EditorGameObject entity : rootEntities) {
                        treeRenderer.renderEntityTree(entity, 0);
                    }
                    ImGui.popStyleVar();
                }

                dragDropHandler.drawDropIndicator();

                // Bridge dirty: tree actions mark working scene dirty, propagate to controller
                if (workingScene.isDirty()) {
                    prefabEditController.markDirty();
                    workingScene.clearDirty();
                }

                // Empty area handling
                float avail = ImGui.getContentRegionAvailY();
                if (avail > 20) {
                    ImGui.invisibleButton("##prefab_empty_drop", ImGui.getContentRegionAvailX(), avail - 10);
                    dragDropHandler.handleEmptyAreaEntityDrop(rootEntities.size());
                    if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
                        selectionHandler.clearSelection();
                    }
                }
            } else {
                ImGui.textDisabled("No working scene");
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
        // Detect selection changes and request scroll-to for newly selected entities
        Set<String> currentSelectedIds = scene.getSelectedEntities().stream()
                .map(EditorGameObject::getId)
                .collect(Collectors.toSet());

        if (!currentSelectedIds.equals(lastSelectedEntityIds) && !currentSelectedIds.isEmpty()) {
            EditorGameObject target = scene.getSelectedEntities().iterator().next();
            treeRenderer.requestScrollToEntity(target);
        }
        lastSelectedEntityIds = currentSelectedIds;

        float baseIndentX = ImGui.getCursorScreenPosX();
        dragDropHandler.resetFrame(baseIndentX);

        List<EditorGameObject> rootEntities = scene.getRootEntities();
        rootEntities.sort(Comparator.comparingInt(EditorGameObject::getOrder));

        if (rootEntities.isEmpty()) {
            ImGui.textDisabled("No entities");
        } else {
            // Minimal vertical gap — 1px keeps nodes tight but leaves room for the drop indicator line
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,
                    ImGui.getStyle().getItemSpacingX(), 1f);

            for (EditorGameObject entity : rootEntities) {
                treeRenderer.renderEntityTree(entity, 0);
            }

            ImGui.popStyleVar();
        }

        dragDropHandler.drawDropIndicator();
        renderMultiSelectionContextMenu();

        // Empty area: invisible button for drops + deselect
        float avail = ImGui.getContentRegionAvailY();
        if (avail > 20) {
            ImGui.invisibleButton("##empty_drop", ImGui.getContentRegionAvailX(), avail - 10);
            HierarchyDropTarget.handleEmptyAreaDropOnLastItem(scene);
            dragDropHandler.handleEmptyAreaEntityDrop(rootEntities.size());
            if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
                selectionHandler.clearSelection();
            }
            if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
                selectionHandler.clearSelection();
                ImGui.openPopup("EmptyArea_CreateEntity_Popup");
            }
        }

        if (ImGui.beginPopup("EmptyArea_CreateEntity_Popup")) {
            renderCreateEntityMenuItems();
            ImGui.endPopup();
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
