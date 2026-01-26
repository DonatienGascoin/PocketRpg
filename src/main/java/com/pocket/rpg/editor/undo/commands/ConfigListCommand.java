package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.List;

/**
 * Generic undo command for list operations in configuration objects.
 * Supports add and remove operations on any List.
 *
 * @param <T> The type of elements in the list
 */
public class ConfigListCommand<T> implements EditorCommand {

    public enum Operation {
        ADD,
        REMOVE
    }

    private final List<T> list;
    private final Operation operation;
    private final int index;
    private final T item;
    private final String description;
    private final Runnable markDirty;

    /**
     * Creates a command for adding an item to a list.
     */
    public static <T> ConfigListCommand<T> add(List<T> list, T item, String itemDescription, Runnable markDirty) {
        return new ConfigListCommand<>(list, Operation.ADD, list.size(), item, "Add " + itemDescription, markDirty);
    }

    /**
     * Creates a command for adding an item at a specific index.
     */
    public static <T> ConfigListCommand<T> addAt(List<T> list, int index, T item, String itemDescription, Runnable markDirty) {
        return new ConfigListCommand<>(list, Operation.ADD, index, item, "Add " + itemDescription, markDirty);
    }

    /**
     * Creates a command for removing an item from a list.
     */
    public static <T> ConfigListCommand<T> remove(List<T> list, int index, String itemDescription, Runnable markDirty) {
        return new ConfigListCommand<>(list, Operation.REMOVE, index, list.get(index), "Remove " + itemDescription, markDirty);
    }

    private ConfigListCommand(List<T> list, Operation operation, int index, T item, String description, Runnable markDirty) {
        this.list = list;
        this.operation = operation;
        this.index = index;
        this.item = item;
        this.description = description;
        this.markDirty = markDirty;
    }

    @Override
    public void execute() {
        switch (operation) {
            case ADD -> list.add(index, item);
            case REMOVE -> list.remove(index);
        }
        if (markDirty != null) {
            markDirty.run();
        }
    }

    @Override
    public void undo() {
        switch (operation) {
            case ADD -> list.remove(index);
            case REMOVE -> list.add(index, item);
        }
        if (markDirty != null) {
            markDirty.run();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        return false; // List add/remove operations shouldn't merge
    }

    @Override
    public void mergeWith(EditorCommand other) {
        // Not supported
    }
}
