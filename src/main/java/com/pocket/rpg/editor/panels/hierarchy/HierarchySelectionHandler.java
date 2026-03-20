package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.SelectionGuard;
import com.pocket.rpg.editor.EditorUIController;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.core.GameObject;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles selection logic for the hierarchy panel.
 * Delegates to SelectionGuard for mode-aware state management.
 */
public class HierarchySelectionHandler {

    @Setter
    private EditorScene scene;

    @Setter
    private SelectionGuard selectionManager;

    @Setter
    private ToolManager toolManager;

    @Setter
    private EditorTool selectionTool;

    @Setter
    private EditorTool brushTool;

    @Setter
    private EditorUIController uiController;

    @Setter
    private HierarchyTreeRenderer treeRenderer;

    private EditorGameObject lastClickedEntity = null;
    private EditorGameObject pendingNarrowSelect = null;

    public void init() {
        // No longer registers mode change listeners (modeless design)
    }

    public void clearPendingNarrowSelect() {
        pendingNarrowSelect = null;
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
        // Auto-open and focus the tileset palette
        if (uiController != null) {
            uiController.openTilesetPalette();
        }
    }

    public void selectCollisionMap() {
        if (selectionManager != null) {
            selectionManager.selectCollisionLayer();
        }
        // Auto-open and focus the collision panel
        if (uiController != null) {
            uiController.openCollisionPanel();
        }
    }

    // Query methods delegate to underlying EditorSelectionManager (read-only, no guard needed)
    public boolean isCameraSelected() {
        return selectionManager != null && selectionManager.getSelectionManager().isCameraSelected();
    }

    public boolean isTilemapLayersSelected() {
        return selectionManager != null && selectionManager.getSelectionManager().isTilemapLayerSelected();
    }

    public boolean isCollisionMapSelected() {
        return selectionManager != null && selectionManager.getSelectionManager().isCollisionLayerSelected();
    }

    public void handleEntityClick(EditorGameObject entity) {
        boolean ctrlHeld = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shiftHeld = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        if (ctrlHeld) {
            pendingNarrowSelect = null;
            if (selectionManager != null) {
                selectionManager.toggleEntitySelection(entity);
            } else if (scene != null) {
                scene.toggleSelection(entity);
            }
            activateSelectionTool();
        } else if (shiftHeld && lastClickedEntity != null) {
            pendingNarrowSelect = null;
            selectRange(lastClickedEntity, entity);
        } else if (scene != null && scene.isSelected(entity)) {
            // Entity already selected — defer to mouse release so multi-drag works
            pendingNarrowSelect = entity;
        } else {
            pendingNarrowSelect = null;
            selectEntity(entity);
        }

        // Update anchor for all click types (ctrl-click, shift-click, normal click)
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
        lastClickedEntity = entity;
        activateSelectionTool();
    }

    /**
     * Called once per frame to resolve pending narrow-select on mouse release.
     * If the user pressed on an already-selected entity without dragging,
     * narrows the selection to just that entity on mouse release.
     */
    public void updatePendingSelection() {
        if (pendingNarrowSelect == null) return;

        // Drag started — cancel pending, keep multi-selection for the drag
        if (ImGui.getDragDropPayload() != null) {
            pendingNarrowSelect = null;
            return;
        }

        // Mouse released without drag — narrow selection to the clicked entity
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            selectEntity(pendingNarrowSelect);
            pendingNarrowSelect = null;
        }
    }

    /**
     * Handles arrow key navigation in the hierarchy (Unity-style).
     * Up/Down moves selection, Left collapses or moves to parent, Right expands or moves to first child.
     */
    public void handleKeyboardNavigation() {
        if (scene == null || treeRenderer == null) return;
        if (!ImGui.isWindowFocused()) return;
        if (treeRenderer.isRenaming()) return;

        boolean up = ImGui.isKeyPressed(ImGuiKey.UpArrow);
        boolean down = ImGui.isKeyPressed(ImGuiKey.DownArrow);
        boolean left = ImGui.isKeyPressed(ImGuiKey.LeftArrow);
        boolean right = ImGui.isKeyPressed(ImGuiKey.RightArrow);

        if (!up && !down && !left && !right) return;

        Set<EditorGameObject> selected = scene.getSelectedEntities();
        if (selected.isEmpty()) {
            // Nothing selected — select first visible entity on any arrow press
            List<EditorGameObject> roots = scene.getRootEntities();
            if (!roots.isEmpty()) {
                roots.sort(Comparator.comparingInt(EditorGameObject::getOrder));
                selectEntity(roots.getFirst());
            }
            return;
        }

        // Use first selected entity as anchor
        EditorGameObject current = selected.iterator().next();
        Set<String> expandedIds = treeRenderer.getExpandedEntityIds();

        if (up || down) {
            List<EditorGameObject> flat = new ArrayList<>();
            flattenVisibleEntities(scene.getRootEntities(), flat, expandedIds);
            int idx = flat.indexOf(current);
            if (idx == -1) return;

            int newIdx = up ? idx - 1 : idx + 1;
            if (newIdx < 0 || newIdx >= flat.size()) return;

            EditorGameObject target = flat.get(newIdx);
            selectEntity(target);
            treeRenderer.requestScrollToEntity(target);
        } else if (left) {
            if (current.hasChildren() && expandedIds.contains(current.getId())) {
                // Collapse
                treeRenderer.setEntityOpen(current.getId(), false);
            } else {
                // Move to parent
                GameObject parent = current.getParent();
                if (parent instanceof EditorGameObject editorParent) {
                    selectEntity(editorParent);
                    treeRenderer.requestScrollToEntity(editorParent);
                }
            }
        } else { // right
            if (current.hasChildren()) {
                if (!expandedIds.contains(current.getId())) {
                    // Expand
                    treeRenderer.setEntityOpen(current.getId(), true);
                } else {
                    // Move to first child
                    List<EditorGameObject> children = current.getChildren().stream()
                            .map(go -> (EditorGameObject) go)
                            .sorted(Comparator.comparingInt(EditorGameObject::getOrder))
                            .toList();
                    if (!children.isEmpty()) {
                        EditorGameObject firstChild = children.getFirst();
                        selectEntity(firstChild);
                        treeRenderer.requestScrollToEntity(firstChild);
                    }
                }
            }
        }
    }

    public void clearSelection() {
        lastClickedEntity = null; // Reset the anchor for Shift+Click range selection
        pendingNarrowSelect = null;

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

        Set<String> expandedIds = treeRenderer != null
                ? treeRenderer.getExpandedEntityIds()
                : Set.of();

        List<EditorGameObject> flat = new ArrayList<>();
        flattenVisibleEntities(scene.getRootEntities(), flat, expandedIds);

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

    /**
     * Flattens the entity tree into a linear list, only including visible entities.
     * Children of collapsed (non-expanded) nodes are skipped.
     */
    private void flattenVisibleEntities(List<EditorGameObject> entities, List<EditorGameObject> result,
                                        Set<String> expandedIds) {
        for (EditorGameObject entity : entities) {
            result.add(entity);
            if (entity.hasChildren() && expandedIds.contains(entity.getId())) {
                List<EditorGameObject> children = entity.getChildren().stream()
                        .map(go -> (EditorGameObject) go)
                        .sorted(Comparator.comparingInt(EditorGameObject::getOrder))
                        .collect(Collectors.toCollection(ArrayList::new));
                flattenVisibleEntities(children, result, expandedIds);
            }
        }
    }
}
