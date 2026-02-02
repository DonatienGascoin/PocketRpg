package com.pocket.rpg.editor.events;

/**
 * Published when prefab edit mode has been exited.
 * Panels subscribe to restore their normal rendering.
 */
public record PrefabEditStoppedEvent() implements EditorEvent {}
