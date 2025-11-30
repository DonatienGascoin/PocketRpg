package com.pocket.rpg.input.events;

import com.pocket.rpg.input.KeyCode;
import lombok.Getter;

/**
 * Event fired when a key state changes.
 * Backend-agnostic - uses KeyCode enum instead of GLFW codes.
 */
public class KeyEvent extends InputEvent {

    public enum Action {
        PRESS,      // Key was just pressed this frame
        RELEASE,    // Key was just released this frame
        REPEAT      // Key is being held down (may not be needed with InputManager)
    }

    @Getter
    private final KeyCode key;
    @Getter
    private final Action action;
    private final boolean shift;
    private final boolean control;
    private final boolean alt;

    public KeyEvent(KeyCode key, Action action, boolean shift, boolean control, boolean alt) {
        super();
        this.key = key;
        this.action = action;
        this.shift = shift;
        this.control = control;
        this.alt = alt;
    }

    public boolean isPress() { return action == Action.PRESS; }
    public boolean isRelease() { return action == Action.RELEASE; }
    public boolean isRepeat() { return action == Action.REPEAT; }

    public boolean isShiftDown() { return shift; }
    public boolean isControlDown() { return control; }
    public boolean isAltDown() { return alt; }

    /**
     * Check if this event is for a specific key.
     */
    public boolean isKey(KeyCode keyCode) {
        return this.key == keyCode;
    }

    @Override
    public String toString() {
        return String.format("KeyEvent{key=%s, action=%s, shift=%b, ctrl=%b, alt=%b}",
                key, action, shift, control, alt);
    }
}