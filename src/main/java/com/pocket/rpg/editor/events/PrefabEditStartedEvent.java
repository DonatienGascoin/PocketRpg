package com.pocket.rpg.editor.events;

/**
 * Published when prefab edit mode has been entered.
 * Panels subscribe to update their rendering accordingly.
 */
public record PrefabEditStartedEvent() implements EditorEvent {}
