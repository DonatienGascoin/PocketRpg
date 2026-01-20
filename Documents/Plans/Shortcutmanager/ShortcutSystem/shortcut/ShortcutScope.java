package com.pocket.rpg.editor.shortcut;

/**
 * Defines the scope/priority level for a shortcut.
 * Higher priority scopes are checked first.
 */
public enum ShortcutScope {

    /**
     * Active only when a popup/modal is open.
     * Highest priority - overrides all other scopes.
     */
    POPUP(0),

    /**
     * Active when a specific panel has keyboard focus.
     */
    PANEL_FOCUSED(1),

    /**
     * Active when a specific panel is visible (rendered).
     */
    PANEL_VISIBLE(2),

    /**
     * Always active unless a higher-priority scope handles the shortcut.
     */
    GLOBAL(3);

    private final int priority;

    ShortcutScope(int priority) {
        this.priority = priority;
    }

    /**
     * Returns priority value (lower = higher priority).
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns true if this scope has higher priority than the other.
     */
    public boolean higherPriorityThan(ShortcutScope other) {
        return this.priority < other.priority;
    }
}
