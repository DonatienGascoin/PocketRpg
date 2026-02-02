package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.EditorModeChangedEvent;

/**
 * Tracks the current editor mode and manages transitions.
 * Mode changes are published via EditorEventBus.
 */
public class EditorModeManager {

    private EditorMode currentMode = EditorMode.SCENE;

    /**
     * Gets the current editor mode.
     */
    public EditorMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Sets the editor mode and fires an EditorModeChangedEvent.
     * No-op if the mode is already the requested mode.
     */
    public void setMode(EditorMode mode) {
        if (this.currentMode == mode) return;
        EditorMode previous = this.currentMode;
        this.currentMode = mode;
        EditorEventBus.get().publish(new EditorModeChangedEvent(previous, mode));
    }

    /**
     * Returns true if the editor is in scene editing mode.
     */
    public boolean isSceneMode() {
        return currentMode == EditorMode.SCENE;
    }

    /**
     * Returns true if the editor is in play mode.
     */
    public boolean isPlayMode() {
        return currentMode == EditorMode.PLAY;
    }

    /**
     * Returns true if the editor is in prefab edit mode.
     */
    public boolean isPrefabEditMode() {
        return currentMode == EditorMode.PREFAB_EDIT;
    }
}
