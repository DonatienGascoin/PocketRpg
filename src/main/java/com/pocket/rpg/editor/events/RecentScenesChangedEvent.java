package com.pocket.rpg.editor.events;

/**
 * Event published when the list of recent scenes changes.
 * <p>
 * Used to update the File menu's recent scenes list.
 */
public record RecentScenesChangedEvent() implements EditorEvent {
}
