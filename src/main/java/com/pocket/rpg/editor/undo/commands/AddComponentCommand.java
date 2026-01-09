package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for adding a component to an entity.
 */
public class AddComponentCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;

    public AddComponentCommand(EditorGameObject entity, Component component) {
        this.entity = entity;
        this.component = component;
    }

    @Override
    public void execute() {
        entity.addComponent(component);
    }

    @Override
    public void undo() {
        entity.removeComponent(component);
    }

    @Override
    public String getDescription() {
        return "Add " + component.getClass().getSimpleName();
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return false;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        // Not used
    }
}
