package com.pocket.rpg.components.player;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.KeyCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for player input. Wraps the raw {@link Input} class
 * with an {@link InputMode} that determines which systems receive input.
 * <p>
 * Other player components (PlayerMovement, InteractionController, PlayerPauseUI)
 * read from this component instead of accessing {@link Input} directly.
 * <p>
 * <b>Polled values</b> (movement direction) are available every frame — consumers
 * check the mode themselves before acting.
 * <p>
 * <b>Callbacks</b> (discrete actions like interact, menu) are registered for a
 * specific mode and only fire when that mode is active.
 */
@ComponentMeta(category = "Player")
public class PlayerInput extends Component {

    @Getter
    private transient InputMode mode = InputMode.OVERWORLD;

    private final transient List<ActionCallback> interactCallbacks = new ArrayList<>();
    private final transient List<ActionCallback> menuCallbacks = new ArrayList<>();

    // ========================================================================
    // MODE
    // ========================================================================

    public void setMode(InputMode mode) {
        this.mode = mode;
    }

    public boolean isOverworld() {
        return mode == InputMode.OVERWORLD;
    }

    public boolean isDialogue() {
        return mode == InputMode.DIALOGUE;
    }

    // ========================================================================
    // POLLED VALUES
    // ========================================================================

    /**
     * Returns the current movement direction based on directional key input,
     * or null if no directional key is held.
     * <p>
     * Available in all modes — the consumer decides whether to act on it.
     */
    public Direction getMovementDirection() {
        if (!Input.hasContext()) return null;

        if (Input.getKey(KeyCode.W) || Input.getKey(KeyCode.UP)) {
            return Direction.UP;
        } else if (Input.getKey(KeyCode.S) || Input.getKey(KeyCode.DOWN)) {
            return Direction.DOWN;
        } else if (Input.getKey(KeyCode.A) || Input.getKey(KeyCode.LEFT)) {
            return Direction.LEFT;
        } else if (Input.getKey(KeyCode.D) || Input.getKey(KeyCode.RIGHT)) {
            return Direction.RIGHT;
        }
        return null;
    }

    /**
     * Returns true if the interact action was just pressed this frame.
     * Available in all modes — the consumer decides whether to act on it.
     */
    public boolean isInteractPressed() {
        return Input.hasContext() && Input.isActionPressed(InputAction.INTERACT);
    }

    /**
     * Returns true if the menu action was just pressed this frame.
     * Available in all modes — the consumer decides whether to act on it.
     */
    public boolean isMenuPressed() {
        return Input.hasContext() && Input.isActionPressed(InputAction.MENU);
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    /**
     * Registers a callback that fires when the interact action is pressed
     * and the given mode is active.
     */
    public void onInteract(InputMode forMode, Runnable handler) {
        interactCallbacks.add(new ActionCallback(forMode, handler));
    }

    /**
     * Registers a callback that fires when the menu action is pressed
     * and the given mode is active.
     */
    public void onMenu(InputMode forMode, Runnable handler) {
        menuCallbacks.add(new ActionCallback(forMode, handler));
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!Input.hasContext()) return;

        if (Input.isActionPressed(InputAction.INTERACT)) {
            dispatchCallbacks(interactCallbacks);
        }
        if (Input.isActionPressed(InputAction.MENU)) {
            dispatchCallbacks(menuCallbacks);
        }
    }

    private void dispatchCallbacks(List<ActionCallback> callbacks) {
        for (ActionCallback callback : callbacks) {
            if (callback.mode == mode) {
                callback.handler.run();
            }
        }
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private record ActionCallback(InputMode mode, Runnable handler) {}
}
