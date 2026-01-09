package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for removing an entity from a scene.
 */
public class RemoveEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorGameObject entity;

    public RemoveEntityCommand(EditorScene scene, EditorGameObject entity) {
        this.scene = scene;
        this.entity = entity;
    }

    @Override
    public void execute() {
        scene.removeEntity(entity);
    }

    @Override
    public void undo() {
        scene.addEntity(entity);
    }

    @Override
    public String getDescription() {
        return "Delete " + entity.getName();
    }
}