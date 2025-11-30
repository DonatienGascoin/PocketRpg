package com.pocket.rpg.inputNew.events;

import com.pocket.rpg.inputNew.KeyCode;
import lombok.Getter;
import org.joml.Vector2f;

/**
 * Event fired when a mouse button state changes.
 */
public class MouseButtonEvent extends InputEvent {

    public enum Action {
        PRESS,
        RELEASE
    }

    @Getter
    private final KeyCode button;
    @Getter
    private final Action action;
    private final Vector2f position;

    public MouseButtonEvent(KeyCode button, Action action, Vector2f position) {
        super();
        this.button = button;
        this.action = action;
        this.position = new Vector2f(position);
    }

    public Vector2f getPosition() { return new Vector2f(position); }

    public float getX() { return position.x; }
    public float getY() { return position.y; }

    public boolean isPress() { return action == Action.PRESS; }
    public boolean isRelease() { return action == Action.RELEASE; }

    public boolean isLeftButton() { return button == KeyCode.MOUSE_BUTTON_LEFT; }
    public boolean isRightButton() { return button == KeyCode.MOUSE_BUTTON_RIGHT; }
    public boolean isMiddleButton() { return button == KeyCode.MOUSE_BUTTON_MIDDLE; }

    @Override
    public String toString() {
        return String.format("MouseButtonEvent{button=%s, action=%s, pos=(%.1f, %.1f)}",
                button, action, position.x, position.y);
    }
}