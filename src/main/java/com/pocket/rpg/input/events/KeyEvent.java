package com.pocket.rpg.input.events;

import com.pocket.rpg.input.KeyCode;
import lombok.Getter;

/**
 * Event fired when a key state changes.
 * Backend-agnostic - uses KeyCode enum instead of raw backend codes.
 */
@Getter
public class KeyEvent {

    /**
     * The type of key action.
     */
    public enum Action {
        /**
         * Key was just pressed this frame
         */
        PRESS,
        /**
         * Key was just released this frame
         */
        RELEASE,
        /**
         * Key is being held down (auto-repeat)
         */
        REPEAT
    }

    private final KeyCode key;
    private final Action action;

    public KeyEvent(KeyCode key, Action action) {
        this.key = key;
        this.action = action;
    }

    public boolean isPress() {
        return action == Action.PRESS;
    }

    public boolean isRelease() {
        return action == Action.RELEASE;
    }

    public boolean isRepeat() {
        return action == Action.REPEAT;
    }

    /**
     * Check if this event is for a specific key.
     */
    public boolean isKey(KeyCode keyCode) {
        return this.key == keyCode;
    }

    @Override
    public String toString() {
        return String.format("KeyEvent{key=%s, action=%s}", key, action);
    }
}