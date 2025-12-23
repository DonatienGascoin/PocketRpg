package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for adding an entity to a scene.
 */
public class AddEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorEntity entity;

    public AddEntityCommand(EditorScene scene, EditorEntity entity) {
        this.scene = scene;
        this.entity = entity;
    }

    @Override
    public void execute() {
        scene.addEntity(entity);
    }

    @Override
    public void undo() {
        scene.removeEntity(entity);
    }

    @Override
    public String getDescription() {
        return "Add " + entity.getName();
    }
}