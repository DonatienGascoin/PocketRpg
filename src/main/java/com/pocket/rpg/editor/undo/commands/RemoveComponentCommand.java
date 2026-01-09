package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.List;

/**
 * Command for removing a component from an entity.
 */
public class RemoveComponentCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final Component component;
    private int originalIndex = -1;

    public RemoveComponentCommand(EditorGameObject entity, Component component) {
        this.entity = entity;
        this.component = component;
    }

    @Override
    public void execute() {
        List<Component> components = entity.getComponents();
        originalIndex = components.indexOf(component);
        entity.removeComponent(component);
    }

    @Override
    public void undo() {
        // Re-add at original position if possible
        entity.addComponent(component);
    }

    @Override
    public String getDescription() {
        return "Remove " + component.getClass().getSimpleName();
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
