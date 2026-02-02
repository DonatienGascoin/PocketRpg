package com.pocket.rpg.editor.events;

import com.pocket.rpg.prefab.JsonPrefab;

/**
 * Published by entry points (asset browser, inspector) to request entering prefab edit mode.
 * PrefabEditController subscribes to this event.
 */
public record RequestPrefabEditEvent(JsonPrefab prefab) implements EditorEvent {}
