package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A generic EditorCommand that captures/restores full state via deep-copy snapshots.
 * <p>
 * Wraps the snapshot-based undo pattern (used by editor panels) as an EditorCommand
 * so it can live in UndoManager's stack via target redirection.
 *
 * @param <T> The target object type (e.g., Pokedex, AnimatorController)
 */
public class SnapshotCommand<T> implements EditorCommand {

    private final T target;
    private final Object beforeSnapshot;
    private final Object afterSnapshot;
    private final BiConsumer<T, Object> restorer;
    private final String description;

    /**
     * Creates a snapshot command.
     *
     * @param target         The object being edited
     * @param beforeSnapshot Deep copy of state before the change
     * @param afterSnapshot  Deep copy of state after the change
     * @param restorer       Function that restores a snapshot onto the target
     * @param description    Human-readable description for undo menu
     */
    public SnapshotCommand(T target, Object beforeSnapshot, Object afterSnapshot,
                           BiConsumer<T, Object> restorer, String description) {
        this.target = target;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.restorer = restorer;
        this.description = description;
    }

    /**
     * Convenience factory that captures before/after snapshots around a mutation.
     *
     * @param target      The object being edited
     * @param capturer    Function that creates a deep copy of the current state
     * @param restorer    Function that restores a snapshot onto the target
     * @param mutation     The mutation to apply
     * @param description Human-readable description
     * @param <T>         Target type
     * @param <S>         Snapshot type
     * @return A SnapshotCommand wrapping the mutation
     */
    public static <T, S> SnapshotCommand<T> capture(T target,
                                                     Function<T, S> capturer,
                                                     BiConsumer<T, Object> restorer,
                                                     Runnable mutation,
                                                     String description) {
        S before = capturer.apply(target);
        mutation.run();
        S after = capturer.apply(target);
        return new SnapshotCommand<>(target, before, after, restorer, description);
    }

    @Override
    public void execute() {
        restorer.accept(target, afterSnapshot);
    }

    @Override
    public void undo() {
        restorer.accept(target, beforeSnapshot);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
