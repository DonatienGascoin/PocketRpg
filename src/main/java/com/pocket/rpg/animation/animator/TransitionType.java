package com.pocket.rpg.animation.animator;

/**
 * Defines how transitions between animator states occur.
 */
public enum TransitionType {
    /**
     * Switch to the new state immediately, interrupting any current animation.
     */
    INSTANT,

    /**
     * Wait for the current animation to finish before transitioning.
     * Only applies to non-looping animations; looping animations use exitTime.
     */
    WAIT_FOR_COMPLETION,

    /**
     * Wait until the animation reaches its loop point before transitioning.
     * Useful for seamless transitions between looping animations.
     */
    WAIT_FOR_LOOP
}
