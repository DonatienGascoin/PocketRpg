package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Handles selection logic for the hierarchy panel.
 */
public class HierarchySelectionHandler {

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

    @Getter
    private boolean cameraSelected = false;

    @Getter
    private boolean tilemapLayersSelected = false;

    @Getter
    private boolean collisionMapSelected = false;

    private EditorGameObject lastClickedEntity = null;

    public void init() {
        if (modeManager != null) {
            modeManager.onModeChanged(this::onModeChanged);
        }
    }

    private void onModeChanged(EditorModeManager.Mode mode) {
        switch (mode) {
            case TILEMAP -> {
                cameraSelected = false;
                tilemapLayersSelected = true;
                collisionMapSelected = false;
                if (scene != null) scene.clearSelection();
            }
            case COLLISION -> {
                cameraSelected = false;
                tilemapLayersSelected = false;
                collisionMapSelected = true;
                if (scene != null) scene.clearSelection();
            }
            case ENTITY -> {
                tilemapLayersSelected = false;
                collisionMapSelected = false;
            }
        }
    }

    public void selectCamera() {
        cameraSelected = true;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        if (scene != null) {
            scene.clearSelection();
            scene.setActiveLayer(-1);
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

    public void selectCollisionMap() {
        cameraSelected = false;
        if (modeManager != null) {
            modeManager.switchToCollision();
        }
    }

    public void handleEntityClick(EditorGameObject entity) {
        boolean ctrlHeld = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shiftHeld = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        if (ctrlHeld) {
            scene.toggleSelection(entity);
        } else if (shiftHeld && lastClickedEntity != null) {
            selectRange(lastClickedEntity, entity);
        } else {
            selectEntity(entity);
        }

        lastClickedEntity = entity;
    }

    /**
     * FIX: Make public for EntityCreationService to use.
     * Selects entity and switches to appropriate mode.
     */
    public void selectEntity(EditorGameObject entity) {
        cameraSelected = false;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        scene.setSelection(Set.of(entity));
        switchToEntityMode();
    }

    private void switchToEntityMode() {
        if (modeManager != null) {
            modeManager.switchToEntity();
        }
        if (toolManager != null && selectionTool != null) {
            toolManager.setActiveTool(selectionTool);
        }
    }

    private void selectRange(EditorGameObject from, EditorGameObject to) {
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

        scene.setSelection(rangeSet);
        switchToEntityMode();
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
