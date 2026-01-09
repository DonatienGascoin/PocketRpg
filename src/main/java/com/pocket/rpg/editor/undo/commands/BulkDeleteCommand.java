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

    // Saved state for undo
    private final Map<EditorGameObject, String> savedParentIds = new HashMap<>();
    private final Map<EditorGameObject, Integer> savedOrders = new HashMap<>();

    public BulkDeleteCommand(EditorScene scene, Set<EditorGameObject> entities) {
        this.scene = scene;
        this.entities = new ArrayList<>(entities);
    }

    @Override
    public void execute() {
        // Save hierarchy state before removal
        for (EditorGameObject entity : entities) {
            savedParentIds.put(entity, entity.getParentId());
            savedOrders.put(entity, entity.getOrder());
        }

        // Remove all entities
        for (EditorGameObject entity : entities) {
            scene.removeEntity(entity);
        }

        scene.clearSelection();
        scene.markDirty();
    }

    @Override
    public void undo() {
        // Re-add entities
        for (EditorGameObject entity : entities) {
            scene.addEntity(entity);
        }

        // Restore hierarchy (need second pass after all entities exist)
        scene.resolveHierarchy();

        // Restore orders
        for (EditorGameObject entity : entities) {
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