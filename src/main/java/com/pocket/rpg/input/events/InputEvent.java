package com.pocket.rpg.input.events;

import lombok.Getter;

/**
 * Base class for all input events.
 * Events are backend-agnostic and use KeyCode enum instead of raw GLFW codes.
 */
@Getter
public abstract class InputEvent {
    private final long timestamp;
    private boolean consumed = false;

    protected InputEvent() {
        this.timestamp = System.nanoTime();
    }

    /**
     * Consume this event to prevent further propagation.
     * Useful for UI elements that want to "eat" input.
     */
    public void consume() {
        consumed = true;
    }

    /**
     * Get timestamp in milliseconds since event creation.
     */
    public long getAge() {
        return (System.nanoTime() - timestamp) / 1_000_000;
    }
}