package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.function.Consumer;

/**
 * Generic undo command that uses a Consumer/setter for both execute and undo.
 * Works with any type that has a setter method.
 *
 * @param <T> The value type (e.g., Float, Vector2f, etc.)
 */
public class SetterUndoCommand<T> implements EditorCommand {

    private final Consumer<T> setter;
    private final T oldValue;
    private T newValue;
    private final String description;

    public SetterUndoCommand(Consumer<T> setter, T oldValue, T newValue, String description) {
        this.setter = setter;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.description = description;
    }

    @Override
    public void execute() {
        setter.accept(newValue);
    }

    @Override
    public void undo() {
        setter.accept(oldValue);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetterUndoCommand<?> cmd)) {
            return false;
        }
        // Merge if same setter reference (same field being edited)
        return cmd.setter == this.setter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetterUndoCommand<?> cmd) {
            this.newValue = (T) cmd.newValue;
        }
    }
}
