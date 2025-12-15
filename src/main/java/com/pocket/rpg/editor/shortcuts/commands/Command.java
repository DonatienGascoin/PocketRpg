package com.pocket.rpg.editor.shortcuts.commands;

public interface Command {

    /**
     * Unique, stable id (used for bindings, config, palettes, etc.)
     */
    String id();

    /**
     * Can this command execute right now?
     */
    boolean isEnabled();

    /**
     * Execute the command
     */
    void execute();

    /**
     * Optional user-facing label
     */
    default String label() {
        return id();
    }
}
