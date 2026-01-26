package com.pocket.rpg.editor.events;

/**
 * Event published when a status message should be displayed in the status bar.
 *
 * @param message The message to display
 */
public record StatusMessageEvent(String message) implements EditorEvent {
}
