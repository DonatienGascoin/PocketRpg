package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command to delete multiple entities at once.
 * Preserves hierarchy information for undo.
 */
public class BulkDeleteCommand implements EditorCommand {

    private final EditorScene scene;
    private final List<EditorGameObject> entities;

    // All entities removed (selected + their descendants), saved on execute for undo
    private final List<EditorGameObject> allRemoved = new ArrayList<>();
    private final Map<EditorGameObject, String> savedParentIds = new HashMap<>();
    private final Map<EditorGameObject, Integer> savedOrders = new HashMap<>();

    public BulkDeleteCommand(EditorScene scene, Set<EditorGameObject> entities) {
        this.scene = scene;
        this.entities = new ArrayList<>(entities);
    }

    @Override
    public void execute() {
        allRemoved.clear();
        savedParentIds.clear();
        savedOrders.clear();

        // Collect all entities + descendants before removal (clears parent references)
        Set<EditorGameObject> visited = new java.util.LinkedHashSet<>();
        for (EditorGameObject entity : entities) {
            collectDescendants(entity, visited);
        }
        allRemoved.addAll(visited);

        // Remove all selected entities (removeEntity handles children recursively)
        for (EditorGameObject entity : entities) {
            scene.removeEntity(entity);
        }

        scene.clearSelection();
        scene.markDirty();
    }

    private void collectDescendants(EditorGameObject e, Set<EditorGameObject> visited) {
        if (!visited.add(e)) return;
        savedParentIds.put(e, e.getParentId());
        savedOrders.put(e, e.getOrder());
        for (EditorGameObject child : e.getChildren()) {
            collectDescendants(child, visited);
        }
    }

    @Override
    public void undo() {
        // Re-add all removed entities (selected + their descendants)
        for (EditorGameObject entity : allRemoved) {
            entity.setParentId(savedParentIds.get(entity));
            scene.addEntity(entity);
        }

        // Restore hierarchy (need second pass after all entities exist)
        scene.resolveHierarchy();

        // Restore orders
        for (EditorGameObject entity : allRemoved) {
            Integer order = savedOrders.get(entity);
            if (order != null) {
                entity.setOrder(order);
            }
        }

        scene.markDirty();
    }

    @Override
    public String getDescription() {
        return "Delete " + entities.size() + " entities";
    }
}