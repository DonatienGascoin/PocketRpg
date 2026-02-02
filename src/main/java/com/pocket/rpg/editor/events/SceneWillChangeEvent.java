package com.pocket.rpg.editor.events;

/**
 * Event published BEFORE a scene change occurs (new scene, open scene).
 * <p>
 * Subscribers can cancel the event to prevent the scene change (e.g., to show
 * a confirmation dialog when there are unsaved prefab edits).
 * This is processed synchronously, so subscribers complete before the scene changes.
 */
public class SceneWillChangeEvent implements EditorEvent {

    private boolean cancelled = false;

    /**
     * Cancels the scene change. The caller should check isCancelled()
     * after publishing and abort if true.
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Returns true if any subscriber cancelled this event.
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
