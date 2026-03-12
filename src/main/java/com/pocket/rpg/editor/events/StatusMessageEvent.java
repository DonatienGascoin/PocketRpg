package com.pocket.rpg.editor.events;

/**
 * Event published when a status message should be displayed in the status bar.
 *
 * @param message The message to display
 * @param type    The message severity type
 */
public record StatusMessageEvent(String message, MessageType type) implements EditorEvent {

    public enum MessageType {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * Convenience constructor that defaults to INFO type.
     */
    public StatusMessageEvent(String message) {
        this(message, MessageType.INFO);
    }
}
