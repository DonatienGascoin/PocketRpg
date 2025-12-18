package com.pocket.rpg.editor;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages editor mode state and switching.
 * <p>
 * Supports listeners for mode changes to allow controllers to react
 * without tight coupling.
 */
public class EditorModeManager {

    /**
     * Available editor modes.
     */
    public enum Mode {
        TILEMAP("Tilemap", "M"),
        COLLISION("Collision", "N");

        @Getter
        private final String displayName;

        @Getter
        private final String shortcut;

        Mode(String displayName, String shortcut) {
            this.displayName = displayName;
            this.shortcut = shortcut;
        }
    }

    @Getter
    private Mode currentMode = Mode.TILEMAP;

    // Mode change listeners
    private final List<Consumer<Mode>> modeChangedListeners = new ArrayList<>();

    /**
     * Switches to the specified mode.
     * Notifies all listeners if mode actually changed.
     */
    public void switchTo(Mode mode) {
        if (mode == currentMode) {
            return;
        }

        Mode previousMode = currentMode;
        currentMode = mode;

        notifyModeChanged(mode);
    }

    /**
     * Switches to tilemap mode.
     */
    public void switchToTilemap() {
        switchTo(Mode.TILEMAP);
    }

    /**
     * Switches to collision mode.
     */
    public void switchToCollision() {
        switchTo(Mode.COLLISION);
    }

    /**
     * Toggles between modes.
     */
    public void toggle() {
        if (currentMode == Mode.TILEMAP) {
            switchTo(Mode.COLLISION);
        } else {
            switchTo(Mode.TILEMAP);
        }
    }

    /**
     * Checks if currently in tilemap mode.
     */
    public boolean isTilemapMode() {
        return currentMode == Mode.TILEMAP;
    }

    /**
     * Checks if currently in collision mode.
     */
    public boolean isCollisionMode() {
        return currentMode == Mode.COLLISION;
    }

    /**
     * Registers a listener for mode changes.
     */
    public void onModeChanged(Consumer<Mode> listener) {
        modeChangedListeners.add(listener);
    }

    /**
     * Removes a mode change listener.
     */
    public void removeModeChangedListener(Consumer<Mode> listener) {
        modeChangedListeners.remove(listener);
    }

    /**
     * Notifies all listeners of a mode change.
     */
    private void notifyModeChanged(Mode newMode) {
        for (var listener : modeChangedListeners) {
            listener.accept(newMode);
        }
    }
}