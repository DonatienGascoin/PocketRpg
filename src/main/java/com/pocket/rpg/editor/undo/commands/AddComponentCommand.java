package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for adding a component to an entity.
 */
public class AddComponentCommand implements EditorCommand {

    private final EditorEntity entity;
    private final ComponentData component;

    public AddComponentCommand(EditorEntity entity, ComponentData component) {
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
        return "Add " + component.getDisplayName();
    }
}