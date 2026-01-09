package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;

/**
 * Command for changing a component field value.
 */
public class SetComponentFieldCommand implements EditorCommand {

    private final Component component;
    private final String fieldName;
    private final Object oldValue;
    private Object newValue;

    public SetComponentFieldCommand(Component component, String fieldName,
                                    Object oldValue, Object newValue) {
        this.component = component;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public void execute() {
        ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
    }

    @Override
    public void undo() {
        ComponentReflectionUtils.setFieldValue(component, fieldName, oldValue);
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
