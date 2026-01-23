package com.pocket.rpg.collision.trigger;

/**
 * Types of scene transitions for warp and door triggers.
 */
public enum TransitionType {
    /**
     * No transition effect - instant switch.
     */
    NONE,

    /**
     * Fade to black and back.
     */
    FADE,

    /**
     * Slide the camera/scene left.
     */
    SLIDE_LEFT,

    /**
     * Slide the camera/scene right.
     */
    SLIDE_RIGHT,

    /**
     * Slide the camera/scene up.
     */
    SLIDE_UP,

    /**
     * Slide the camera/scene down.
     */
    SLIDE_DOWN
}
