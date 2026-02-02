package com.pocket.rpg.editor.events;

import com.pocket.rpg.editor.EditorMode;

/**
 * Fired when the editor mode changes.
 */
public record EditorModeChangedEvent(
        EditorMode previousMode,
        EditorMode newMode
) implements EditorEvent {}
