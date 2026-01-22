package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.Setter;

import java.util.*;

/**
 * Handles selection logic for the hierarchy panel.
 * Delegates to EditorSelectionManager for state management.
 */
public class HierarchySelectionHandler {

    @Setter
    private EditorScene scene;

    @Setter
    private EditorSelectionManager selectionManager;

    @Setter
    private ToolManager toolManager;

    @Setter
    private EditorTool selectionTool;

    @Setter
    private EditorTool brushTool;

    private EditorGameObject lastClickedEntity = null;

    public void init() {
        // No longer registers mode change listeners (modeless design)
    }

    public void selectCamera() {
        if (selectionManager != null) {
            selectionManager.selectCamera();
        }
    }

    public void selectTilemapLayers() {
        if (selectionManager != null) {
            selectionManager.selectTilemapLayer(0);
        }
        if (toolManager != null && brushTool != null) {
            toolManager.setActiveTool(brushTool);
        }
    }

    public void selectCollisionMap() {
        if (selectionManager != null) {
            selectionManager.selectCollisionLayer();
        }
    }

    // Query methods delegate to selectionManager
    public boolean isCameraSelected() {
        return selectionManager != null && selectionManager.isCameraSelected();
    }

    public boolean isTilemapLayersSelected() {
        return selectionManager != null && selectionManager.isTilemapLayerSelected();
    }

    public boolean isCollisionMapSelected() {
        return selectionManager != null && selectionManager.isCollisionLayerSelected();
    }

    public void handleEntityClick(EditorGameObject entity) {
        boolean ctrlHeld = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shiftHeld = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        if (ctrlHeld) {
            if (selectionManager != null) {
                selectionManager.toggleEntitySelection(entity);
            } else if (scene != null) {
                scene.toggleSelection(entity);
            }
            activateSelectionTool();
        } else if (shiftHeld && lastClickedEntity != null) {
            selectRange(lastClickedEntity, entity);
        } else {
            selectEntity(entity);
        }

        lastClickedEntity = entity;
    }

    /**
     * Selects entity and activates selection tool.
     */
    public void selectEntity(EditorGameObject entity) {
        if (selectionManager != null) {
            selectionManager.selectEntity(entity);
        } else if (scene != null) {
            scene.setSelection(Set.of(entity));
        }
        activateSelectionTool();
    }

    public void clearSelection() {
        lastClickedEntity = null; // Reset the anchor for Shift+Click range selection

        if (selectionManager != null) {
            selectionManager.clearSelection();
        } else if (scene != null) {
            scene.clearSelection();
        }
    }

    private void activateSelectionTool() {
        if (toolManager != null && selectionTool != null) {
            toolManager.setActiveTool(selectionTool);
        }
    }

    private void selectRange(EditorGameObject from, EditorGameObject to) {
        if (scene == null) return;

        List<EditorGameObject> flat = new ArrayList<>();
        flattenEntities(scene.getRootEntities(), flat);

        int fromIdx = flat.indexOf(from);
        int toIdx = flat.indexOf(to);

        if (fromIdx == -1 || toIdx == -1) return;

        int start = Math.min(fromIdx, toIdx);
        int end = Math.max(fromIdx, toIdx);

        Set<EditorGameObject> rangeSet = new HashSet<>();
        for (int i = start; i <= end; i++) {
            rangeSet.add(flat.get(i));
        }

        if (selectionManager != null) {
            selectionManager.selectEntities(rangeSet);
        } else {
            scene.setSelection(rangeSet);
        }
        activateSelectionTool();
    }

    private void flattenEntities(List<EditorGameObject> entities, List<EditorGameObject> result) {
        for (EditorGameObject entity : entities) {
            result.add(entity);
            if (entity.hasChildren()) {
                List<EditorGameObject> children = new ArrayList<>(entity.getChildren());
                children.sort(Comparator.comparingInt(EditorGameObject::getOrder));
                flattenEntities(children, result);
            }
        }
    }
}
