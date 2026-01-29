package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

import java.util.List;

/**
 * Command for list item operations: add, remove, or set element.
 */
public class ListItemCommand implements EditorCommand {

    public enum Operation {
        ADD,    // Add item at index
        REMOVE, // Remove item at index
        SET     // Set item at index
    }

    private final Component component;
    private final String fieldName;
    private final Operation operation;
    private final int index;
    private final Object oldValue;
    private Object newValue;
    private final EditorGameObject entity;

    public ListItemCommand(Component component, String fieldName,
                           Operation operation, int index,
                           Object oldValue, Object newValue,
                           EditorGameObject entity) {
        this.component = component;
        this.fieldName = fieldName;
        this.operation = operation;
        this.index = index;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.entity = entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute() {
        List<Object> list = (List<Object>) ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (list == null) return;

        switch (operation) {
            case ADD -> list.add(index, newValue);
            case REMOVE -> list.remove(index);
            case SET -> list.set(index, newValue);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void undo() {
        List<Object> list = (List<Object>) ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (list == null) return;

        switch (operation) {
            case ADD -> list.remove(index);
            case REMOVE -> list.add(index, oldValue);
            case SET -> list.set(index, oldValue);
        }
    }

    @Override
    public String getDescription() {
        return switch (operation) {
            case ADD -> "Add item to " + fieldName;
            case REMOVE -> "Remove item from " + fieldName;
            case SET -> "Change " + fieldName + "[" + index + "]";
        };
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof ListItemCommand cmd)) {
            return false;
        }
        // Only merge SET operations on the same element
        return operation == Operation.SET &&
                cmd.operation == Operation.SET &&
                cmd.component == this.component &&
                cmd.fieldName.equals(this.fieldName) &&
                cmd.index == this.index;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof ListItemCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}
