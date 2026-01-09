package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Command to move multiple entities by a relative offset.
 */
public class BulkMoveCommand implements EditorCommand {

    private final EditorScene scene;
    private final Map<EditorGameObject, Vector3f> oldPositions = new HashMap<>();
    private final Vector3f offset;

    public BulkMoveCommand(EditorScene scene, Set<EditorGameObject> entities, Vector3f offset) {
        this.scene = scene;
        this.offset = new Vector3f(offset);

        for (EditorGameObject entity : entities) {
            oldPositions.put(entity, new Vector3f(entity.getPosition()));
        }
    }

    @Override
    public void execute() {
        for (EditorGameObject entity : oldPositions.keySet()) {
            Vector3f pos = entity.getPosition();
            entity.setPosition(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
        }
        scene.markDirty();
    }

    @Override
    public void undo() {
        for (Map.Entry<EditorGameObject, Vector3f> entry : oldPositions.entrySet()) {
            entry.getKey().setPosition(entry.getValue());
        }
        scene.markDirty();
    }

    @Override
    public String getDescription() {
        return "Move " + oldPositions.size() + " entities";
    }
}