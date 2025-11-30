package com.pocket.rpg.inputNew.events;

/**
 * Event fired when the mouse wheel is scrolled.
 */
public class MouseScrollEvent extends InputEvent {
    private final float scrollDelta;

    public MouseScrollEvent(float scrollDelta) {
        super();
        this.scrollDelta = scrollDelta;
    }

    public float getScrollDelta() { return scrollDelta; }

    public boolean isScrollingUp() { return scrollDelta > 0; }
    public boolean isScrollingDown() { return scrollDelta < 0; }

    @Override
    public String toString() {
        return String.format("MouseScrollEvent{delta=%.2f}", scrollDelta);
    }
}