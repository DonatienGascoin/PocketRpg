package com.pocket.rpg.inputNew.events;

import org.joml.Vector2f;

/**
 * Event fired when the mouse cursor moves.
 */
public class MouseMoveEvent extends InputEvent {
    private final Vector2f position;
    private final Vector2f delta;

    public MouseMoveEvent(Vector2f position, Vector2f delta) {
        super();
        this.position = new Vector2f(position);
        this.delta = new Vector2f(delta);
    }

    public Vector2f getPosition() {
        return new Vector2f(position);
    }

    public Vector2f getDelta() {
        return new Vector2f(delta);
    }

    public float getX() {
        return position.x;
    }

    public float getY() {
        return position.y;
    }

    public float getDeltaX() {
        return delta.x;
    }

    public float getDeltaY() {
        return delta.y;
    }

    public boolean hasMoved() {
        return delta.x != 0 || delta.y != 0;
    }

    @Override
    public String toString() {
        return String.format("MouseMoveEvent{pos=(%.1f, %.1f), delta=(%.1f, %.1f)}",
                position.x, position.y, delta.x, delta.y);
    }
}