package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Command for map entry operations: put or remove.
 */
public class MapItemCommand implements EditorCommand {

    public enum Operation {
        PUT,    // Add or update entry
        REMOVE  // Remove entry
    }

    private final Component component;
    private final String fieldName;
    private final Operation operation;
    private final Object key;
    private final Object oldValue;
    private Object newValue;
    private final EditorGameObject entity;

    public MapItemCommand(Component component, String fieldName,
                          Operation operation, Object key,
                          Object oldValue, Object newValue,
                          EditorGameObject entity) {
        this.component = component;
        this.fieldName = fieldName;
        this.operation = operation;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.entity = entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute() {
        Map<Object, Object> map = (Map<Object, Object>) ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (map == null) return;

        switch (operation) {
            case PUT -> map.put(key, newValue);
            case REMOVE -> map.remove(key);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void undo() {
        Map<Object, Object> map = (Map<Object, Object>) ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (map == null) return;

        switch (operation) {
            case PUT -> {
                if (oldValue == null) {
                    map.remove(key);
                } else {
                    map.put(key, oldValue);
                }
            }
            case REMOVE -> map.put(key, oldValue);
        }
    }

    @Override
    public String getDescription() {
        return switch (operation) {
            case PUT -> "Change " + fieldName + "[" + key + "]";
            case REMOVE -> "Remove from " + fieldName + "[" + key + "]";
        };
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof MapItemCommand cmd)) {
            return false;
        }
        // Only merge PUT operations on the same key
        return operation == Operation.PUT
                && cmd.operation == Operation.PUT
                && cmd.component == this.component
                && cmd.fieldName.equals(this.fieldName)
                && Objects.equals(cmd.key, this.key);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof MapItemCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}
