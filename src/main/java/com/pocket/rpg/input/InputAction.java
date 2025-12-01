package com.pocket.rpg.input;

import java.util.List;

/**
 * Strongly-typed identifiers for game actions.
 * Actions map to one or more key bindings defined in {@link com.pocket.rpg.config.InputConfig}.
 * <p>
 * This enum only defines action identifiers and their default bindings.
 * Actual bindings are stored in InputConfig and can be changed at runtime.
 */
public enum InputAction {
    /**
     * Primary fire/attack action.
     * Default: SPACE, MOUSE_BUTTON_LEFT
     */
    FIRE,

    /**
     * Jump action.
     * Default: SPACE
     */
    JUMP,

    /**
     * Crouch/stealth action.
     * Default: LEFT_CONTROL
     */
    CROUCH,

    /**
     * Reload weapon action.
     * Default: R
     */
    RELOAD,

    /**
     * Interact with objects/NPCs.
     * Default: E
     */
    INTERACT,

    /**
     * Open main menu.
     * Default: ESCAPE
     */
    MENU,

    /**
     * Pause game.
     * Default: P, ESCAPE
     */
    PAUSE;

    /**
     * Provides default key bindings for this action.
     * These are fallback defaults used when no config file exists.
     * Can be overridden by loading from InputConfig file.
     *
     * @return List of default key codes for this action
     */
    public List<KeyCode> getDefaultBinding() {
        return switch (this) {
            case FIRE -> List.of(KeyCode.SPACE, KeyCode.MOUSE_BUTTON_LEFT);
            case JUMP -> List.of(KeyCode.SPACE);
            case CROUCH -> List.of(KeyCode.LEFT_CONTROL);
            case RELOAD -> List.of(KeyCode.R);
            case INTERACT -> List.of(KeyCode.E);
            case MENU -> List.of(KeyCode.ESCAPE);
            case PAUSE -> List.of(KeyCode.P, KeyCode.ESCAPE);
        };
    }
}