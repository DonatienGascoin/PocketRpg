package com.pocket.rpg.animation.animator;

/**
 * Types of parameters that can drive animator transitions.
 */
public enum ParameterType {
    /**
     * Boolean parameter (true/false).
     */
    BOOL,

    /**
     * Direction parameter (UP, DOWN, LEFT, RIGHT).
     */
    DIRECTION,

    /**
     * Trigger parameter - automatically resets to false after being consumed.
     */
    TRIGGER
}
