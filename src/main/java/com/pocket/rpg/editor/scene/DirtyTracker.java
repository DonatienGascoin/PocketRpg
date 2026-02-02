package com.pocket.rpg.editor.scene;

/**
 * Abstraction for dirty-state tracking.
 * EditorScene implements this for scene editing.
 * Prefab edit mode provides its own implementation.
 */
@FunctionalInterface
public interface DirtyTracker {
    void markDirty();
}
