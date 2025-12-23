package com.pocket.rpg.editor.undo;

/**
 * Represents a reversible editor action.
 * <p>
 * Commands store enough information to both execute and undo an action.
 */
public interface EditorCommand {

    /**
     * Executes the command.
     */
    void execute();

    /**
     * Undoes the command, restoring previous state.
     */
    void undo();

    /**
     * Gets a human-readable description for the undo menu.
     */
    String getDescription();

    /**
     * Checks if this command can be merged with another.
     * Used for combining rapid small changes (e.g., typing, dragging).
     */
    default boolean canMergeWith(EditorCommand other) {
        return false;
    }

    /**
     * Merges another command into this one.
     * Called when canMergeWith returns true.
     */
    default void mergeWith(EditorCommand other) {
        // Default: no merge
    }
}