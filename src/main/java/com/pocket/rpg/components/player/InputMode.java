package com.pocket.rpg.components.player;

/**
 * Defines the current input context for the player.
 * Only one mode is active at a time. Components check the mode
 * before consuming input, so switching modes blocks/unblocks
 * the right systems without disabling components.
 */
public enum InputMode {
    OVERWORLD,
    DIALOGUE,
    BATTLE,
    MENU
}
