package com.pocket.rpg.editor.events;

import com.pocket.rpg.editor.scene.EditorScene;

/**
 * Event published when the current scene changes.
 * <p>
 * This includes loading a new scene, creating a new scene, or closing the current scene.
 *
 * @param scene The new scene, or null if no scene is loaded
 */
public record SceneChangedEvent(EditorScene scene) implements EditorEvent {
}
