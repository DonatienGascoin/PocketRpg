package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for changing a component field value on a prefab instance.
 * <p>
 * For scratch entities, use SetComponentFieldCommand instead (operates on ComponentData directly).
 */
public class SetFieldCommand implements EditorCommand {

    private final EditorGameObject entity;
    private final String componentType;
    private final String fieldName;
    private final Object oldValue;
    private Object newValue;

    public SetFieldCommand(EditorGameObject entity, String componentType, String fieldName,
                           Object oldValue, Object newValue) {
        this.entity = entity;
        this.componentType = componentType;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public void execute() {
        entity.setFieldValue(componentType, fieldName, newValue);
    }

    @Override
    public void undo() {
        entity.setFieldValue(componentType, fieldName, oldValue);
    }

    @Override
    public String getDescription() {
        return "Change " + fieldName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetFieldCommand cmd)) {
            return false;
        }

        return cmd.entity == this.entity &&
                cmd.componentType.equals(this.componentType) &&
                cmd.fieldName.equals(this.fieldName);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetFieldCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}