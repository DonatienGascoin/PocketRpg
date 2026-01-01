package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.assets.HierarchyDropTarget;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.hierarchy.EntityCreationService;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyDragDropHandler;
import com.pocket.rpg.editor.panels.hierarchy.HierarchySelectionHandler;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyTreeRenderer;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Unified hierarchy panel - orchestrates tree rendering, selection, drag-drop, and entity creation.
 */
public class HierarchyPanel {

    @Setter
    private EditorScene scene;

    private final HierarchySelectionHandler selectionHandler = new HierarchySelectionHandler();
    private final HierarchyDragDropHandler dragDropHandler = new HierarchyDragDropHandler();
    private final EntityCreationService creationService = new EntityCreationService();
    private final HierarchyTreeRenderer treeRenderer = new HierarchyTreeRenderer();

    public void setModeManager(EditorModeManager modeManager) {
        selectionHandler.setModeManager(modeManager);
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

    public void setUiFactory(UIEntityFactory uiFactory) {
        creationService.setUiFactory(uiFactory);
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

    public void render() {
        if (ImGui.begin("Hierarchy")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            // Update scene references
            selectionHandler.setScene(scene);
            dragDropHandler.setScene(scene);
            creationService.setScene(scene);
            treeRenderer.setScene(scene);

            ImGui.text(IconUtils.getSceneIcon() + " " + scene.getName());
            ImGui.sameLine(ImGui.getContentRegionMaxX() - 15);
            if (ImGui.smallButton(FontAwesomeIcons.Plus)) {
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
        }
        ImGui.end();

        dragDropHandler.resetDropTarget();
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
        List<EditorEntity> rootEntities = scene.getRootEntities();
        rootEntities.sort(Comparator.comparingInt(EditorEntity::getOrder));

        if (rootEntities.isEmpty()) {
            ImGui.textDisabled("No entities");
            dragDropHandler.renderDropZone(null, 0, null);
        } else {
            for (int i = 0; i < rootEntities.size(); i++) {
                EditorEntity entity = rootEntities.get(i);
                dragDropHandler.renderDropZone(null, i, entity);
                treeRenderer.renderEntityTree(entity);
            }
            dragDropHandler.renderDropZone(null, rootEntities.size(), null);
        }

        renderMultiSelectionContextMenu();
        HierarchyDropTarget.handleEmptyAreaDrop(scene);
    }

    private void renderEntityCreationMenu() {
        if (ImGui.beginPopup("CreateEntity_Popup")) {
            if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Entity")) {
                creationService.createEmptyEntity();
            }

            if (ImGui.beginMenu(FontAwesomeIcons.WindowMaximize + " Create UI")) {
                if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                    creationService.createUIElement("Canvas");
                }
                ImGui.separator();
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
                ImGui.endMenu();
            }

            ImGui.endPopup();
        }
    }

    private void renderMultiSelectionContextMenu() {
        if (ImGui.beginPopupContextWindow("hierarchy_ctx", ImGuiMouseButton.Right)) {
            Set<EditorEntity> selected = scene.getSelectedEntities();

            if (!selected.isEmpty()) {
                ImGui.text(selected.size() + " selected");
                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete Selected")) {
                    UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
                }

                if (ImGui.menuItem(FontAwesomeIcons.TimesCircle + " Clear Selection")) {
                    scene.clearSelection();
                }
            } else {
                if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Entity")) {
                    creationService.createEmptyEntity();
                }

                if (ImGui.beginMenu(FontAwesomeIcons.WindowMaximize + " Create UI")) {
                    if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                        creationService.createUIElement("Canvas");
                    }
                    ImGui.separator();
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
                    ImGui.endMenu();
                }
            }

            ImGui.endPopup();
        }
    }
}
