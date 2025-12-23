package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.serialization.ComponentData;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for changing a component field value.
 */
public class SetComponentFieldCommand implements EditorCommand {

    private final ComponentData component;
    private final String fieldName;
    private final Object oldValue;
    private Object newValue;

    public SetComponentFieldCommand(ComponentData component, String fieldName,
                                    Object oldValue, Object newValue) {
        this.component = component;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public void execute() {
        component.getFields().put(fieldName, newValue);
    }

    @Override
    public void undo() {
        if (oldValue == null) {
            component.getFields().remove(fieldName);
        } else {
            component.getFields().put(fieldName, oldValue);
        }
    }

    @Override
    public String getDescription() {
        return "Change " + fieldName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetComponentFieldCommand cmd)) {
            return false;
        }

        return cmd.component == this.component &&
                cmd.fieldName.equals(this.fieldName);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetComponentFieldCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}