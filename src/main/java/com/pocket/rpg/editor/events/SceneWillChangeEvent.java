package com.pocket.rpg.editor.events;

/**
 * Event published BEFORE a scene change occurs (new scene, open scene).
 * <p>
 * Subscribers can use this to clean up state before the scene is destroyed.
 * This is processed synchronously, so subscribers complete before the scene changes.
 */
public record SceneWillChangeEvent() implements EditorEvent {
}
