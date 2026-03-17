package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for adding a component at a specific index in an entity's component list.
 * Used for inserting managed visuals right after UIButton.
 */
public class AddComponentAtCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;
    private final int index;

    public AddComponentAtCommand(EditorGameObject entity, Component component, int index) {
        this.entity = entity;
        this.component = component;
        this.index = index;
    }

    @Override
    public void execute() {
        int insertAt = Math.min(index, entity.getComponents().size());
        entity.getComponents().add(insertAt, component);
        component.setGameObject(entity);
    }

    @Override
    public void undo() {
        entity.getComponents().remove(component);
        component.setGameObject(null);
    }

    @Override
    public String getDescription() {
        return "Add " + component.getClass().getSimpleName() + " at index " + index;
    }
}
