package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.serialization.ComponentData;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.List;

/**
 * Command for removing a component from an entity.
 */
public class RemoveComponentCommand implements EditorCommand {

    private final EditorEntity entity;
    private final ComponentData component;
    private final int originalIndex;

    public RemoveComponentCommand(EditorEntity entity, ComponentData component) {
        this.entity = entity;
        this.component = component;
        this.originalIndex = entity.getComponents().indexOf(component);
    }

    @Override
    public void execute() {
        entity.removeComponent(component);
    }

    @Override
    public void undo() {
        List<ComponentData> components = entity.getComponents();
        if (originalIndex >= 0 && originalIndex <= components.size()) {
            components.add(originalIndex, component);
        } else {
            entity.addComponent(component);
        }
    }

    @Override
    public String getDescription() {
        return "Remove " + component.getDisplayName();
    }
}