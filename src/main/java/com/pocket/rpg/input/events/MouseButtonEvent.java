package com.pocket.rpg.input.events;

import com.pocket.rpg.input.KeyCode;
import lombok.Getter;

/**
 * Event fired when a mouse button state changes.
 */
@Getter
public class MouseButtonEvent {

    /**
     * The type of mouse button action.
     */
    public enum Action {
        /**
         * Button was just pressed
         */
        PRESS,
        /**
         * Button was just released
         */
        RELEASE
    }

    private final KeyCode button;
    private final Action action;

    public MouseButtonEvent(KeyCode button, Action action) {
        this.button = button;
        this.action = action;
    }

    public boolean isPress() {
        return action == Action.PRESS;
    }

    public boolean isRelease() {
        return action == Action.RELEASE;
    }

    public boolean isLeftButton() {
        return button == KeyCode.MOUSE_BUTTON_LEFT;
    }

    public boolean isRightButton() {
        return button == KeyCode.MOUSE_BUTTON_RIGHT;
    }

    public boolean isMiddleButton() {
        return button == KeyCode.MOUSE_BUTTON_MIDDLE;
    }

    @Override
    public String toString() {
        return String.format("MouseButtonEvent{button=%s, action=%s}", button, action);
    }
}