package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for changing an entity property value.
 */
public class SetPropertyCommand implements EditorCommand {

    private final EditorEntity entity;
    private final String propertyName;
    private final Object oldValue;
    private Object newValue;

    public SetPropertyCommand(EditorEntity entity, String propertyName,
                              Object oldValue, Object newValue) {
        this.entity = entity;
        this.propertyName = propertyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @Override
    public void execute() {
        entity.setProperty(propertyName, newValue);
    }

    @Override
    public void undo() {
        entity.setProperty(propertyName, oldValue);
    }

    @Override
    public String getDescription() {
        return "Change " + propertyName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetPropertyCommand cmd)) {
            return false;
        }

        return cmd.entity == this.entity &&
                cmd.propertyName.equals(this.propertyName);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetPropertyCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}