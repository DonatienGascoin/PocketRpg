package com.pocket.rpg.editor.events;

/**
 * Marker interface for all editor events.
 * <p>
 * Events are published through {@link EditorEventBus} and received by subscribers.
 * Implementations should be immutable (preferably Java records).
 */
public interface EditorEvent {
    // Marker interface - implementations define their own data
}
