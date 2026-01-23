package com.pocket.rpg.collision.trigger;

/**
 * Defines when a trigger activates.
 */
public enum ActivationMode {
    /**
     * Fires when entity steps onto the tile.
     */
    ON_ENTER,

    /**
     * Fires when player presses interact while on or facing tile.
     */
    ON_INTERACT,

    /**
     * Fires when entity steps off the tile.
     */
    ON_EXIT
}
