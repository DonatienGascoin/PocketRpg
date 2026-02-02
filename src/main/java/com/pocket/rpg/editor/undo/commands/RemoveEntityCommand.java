package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for removing an entity and all its children from a scene.
 */
public class RemoveEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorGameObject entity;

    // All entities removed (entity + descendants), saved on execute for undo
    private final List<EditorGameObject> allRemoved = new ArrayList<>();
    private final Map<EditorGameObject, String> savedParentIds = new HashMap<>();
    private final Map<EditorGameObject, Integer> savedOrders = new HashMap<>();

    public RemoveEntityCommand(EditorScene scene, EditorGameObject entity) {
        this.scene = scene;
        this.entity = entity;
    }

    @Override
    public void execute() {
        // Only snapshot state on first execute; redo reuses saved state
        if (allRemoved.isEmpty()) {
            collectDescendants(entity);
        }

        scene.removeEntity(entity);
    }

    private void collectDescendants(EditorGameObject e) {
        allRemoved.add(e);
        savedParentIds.put(e, e.getParentId());
        savedOrders.put(e, e.getOrder());
        for (EditorGameObject child : e.getChildren()) {
            collectDescendants(child);
        }
    }

    @Override
    public void undo() {
        // Re-add all removed entities
        for (EditorGameObject e : allRemoved) {
            // Restore parentId before adding so resolveHierarchy can rebuild links
            e.setParentId(savedParentIds.get(e));
            scene.addEntity(e);
        }

        // Rebuild parent-child links from parentId fields
        scene.resolveHierarchy();

        // Restore sibling orders
        for (EditorGameObject e : allRemoved) {
            Integer order = savedOrders.get(e);
            if (order != null) {
                e.setOrder(order);
            }
        }

        scene.markDirty();
    }

    @Override
    public String getDescription() {
        return "Delete " + entity.getName();
    }
}