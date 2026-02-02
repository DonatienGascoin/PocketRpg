package com.pocket.rpg.editor.undo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages undo/redo history for the editor.
 * <p>
 * Usage:
 * UndoManager.getInstance().execute(new SetPropertyCommand(...));
 * UndoManager.getInstance().undo();
 * UndoManager.getInstance().redo();
 */
public class UndoManager {

    private static UndoManager instance;

    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();

    // Scope stack for isolated undo/redo contexts (e.g., prefab edit mode)
    private final Deque<UndoScope> scopeStack = new ArrayDeque<>();

    private int maxHistorySize = 100;
    /**
     * -- SETTER --
     * Temporarily disables undo tracking.
     * Use for batch operations that shouldn't be undone individually.
     * -- GETTER --
     * Checks if undo tracking is enabled.
     */
    @Getter
    @Setter
    private boolean enabled = true;

    // For merging rapid changes
    private EditorCommand lastCommand = null;
    private long lastCommandTime = 0;
    private static final long MERGE_WINDOW_MS = 500;  // Half second

    private UndoManager() {
    }

    public static UndoManager getInstance() {
        if (instance == null) {
            instance = new UndoManager();
        }
        return instance;
    }

    /**
     * Executes a command and adds it to the undo history.
     */
    public void execute(EditorCommand command) {
        if (!enabled || command == null) {
            return;
        }

        // Try to merge with last command
        long now = System.currentTimeMillis();
        if (lastCommand != null &&
                (now - lastCommandTime) < MERGE_WINDOW_MS &&
                lastCommand.canMergeWith(command)) {

            lastCommand.mergeWith(command);
            lastCommandTime = now;
            return;
        }

        // Execute the command
        command.execute();

        // Add to undo stack
        undoStack.push(command);
        lastCommand = command;
        lastCommandTime = now;

        // Clear redo stack (new action invalidates redo history)
        redoStack.clear();

        // Enforce max history size
        while (undoStack.size() > maxHistorySize) {
            undoStack.removeLast();
        }
    }


    /**
     * Adds a command to history without executing it.
     * Use when the change was already applied (e.g., during drag).
     */
    public void push(EditorCommand command) {
        if (!enabled || command == null) {
            return;
        }

        undoStack.push(command);
        redoStack.clear();
        lastCommand = command;
        lastCommandTime = System.currentTimeMillis();

        while (undoStack.size() > maxHistorySize) {
            undoStack.removeLast();
        }
    }

    /**
     * Undoes the last command.
     *
     * @return true if a command was undone
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        EditorCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);

        lastCommand = null;  // Break merge chain

        return true;
    }

    /**
     * Redoes the last undone command.
     *
     * @return true if a command was redone
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }

        EditorCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);

        lastCommand = command;
        lastCommandTime = System.currentTimeMillis();

        return true;
    }

    /**
     * Checks if undo is available.
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Checks if redo is available.
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Gets the description of the next undo action.
     */
    public String getUndoDescription() {
        return undoStack.isEmpty() ? null : undoStack.peek().getDescription();
    }

    /**
     * Gets the description of the next redo action.
     */
    public String getRedoDescription() {
        return redoStack.isEmpty() ? null : redoStack.peek().getDescription();
    }

    /**
     * Gets the undo stack size.
     */
    public int getUndoCount() {
        return undoStack.size();
    }

    /**
     * Gets the redo stack size.
     */
    public int getRedoCount() {
        return redoStack.size();
    }

    /**
     * Clears all history.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        lastCommand = null;
    }

    /**
     * Sets the maximum history size.
     */
    public void setMaxHistorySize(int size) {
        this.maxHistorySize = Math.max(1, size);
    }

    /**
     * Executes without adding to history.
     * Useful for initialization or loading.
     */
    public void executeWithoutHistory(Runnable action) {
        boolean wasEnabled = enabled;
        enabled = false;
        try {
            action.run();
        } finally {
            enabled = wasEnabled;
        }
    }

    // ========================================================================
    // SCOPED STACKS
    // ========================================================================

    /**
     * Pushes a new isolated undo scope.
     * Current undo/redo state is saved and a fresh scope begins.
     * Use when entering a sub-editing context (e.g., prefab edit mode).
     */
    public void pushScope() {
        scopeStack.push(new UndoScope(
                new ArrayDeque<>(undoStack),
                new ArrayDeque<>(redoStack),
                lastCommand,
                lastCommandTime
        ));

        undoStack.clear();
        redoStack.clear();
        lastCommand = null;
        lastCommandTime = 0;
    }

    /**
     * Pops the current scope and restores the previous undo state.
     * All commands in the current scope are discarded.
     *
     * @throws IllegalStateException if no scope has been pushed
     */
    public void popScope() {
        if (scopeStack.isEmpty()) {
            throw new IllegalStateException("No undo scope to pop");
        }

        UndoScope scope = scopeStack.pop();

        undoStack.clear();
        undoStack.addAll(scope.undoStack());
        redoStack.clear();
        redoStack.addAll(scope.redoStack());
        lastCommand = scope.lastCommand();
        lastCommandTime = scope.lastCommandTime();
    }

    /**
     * Returns the current scope nesting depth (0 = root scope).
     */
    public int getScopeDepth() {
        return scopeStack.size();
    }

    /**
     * Returns true if currently inside a pushed scope (not the root scope).
     */
    public boolean isInScope() {
        return !scopeStack.isEmpty();
    }

    /**
     * Snapshot of undo state for scope isolation.
     */
    private record UndoScope(
            Deque<EditorCommand> undoStack,
            Deque<EditorCommand> redoStack,
            EditorCommand lastCommand,
            long lastCommandTime
    ) {}
}
