package com.pocket.rpg.components;

import com.pocket.rpg.animation.AnimationPlayer;
import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorStateMachine;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.rendering.resources.Sprite;
import lombok.Getter;
import lombok.Setter;

/**
 * Component that manages animation state machines.
 * <p>
 * Uses an {@link AnimatorController} asset to define states, transitions, and parameters.
 * Internally runs an {@link AnimatorStateMachine} that evaluates transitions automatically.
 * <p>
 * Requires a SpriteRenderer component on the same GameObject.
 */
@ComponentMeta(category = "Rendering")
public class AnimatorComponent extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * The animator controller asset.
     * Serialized as path string via AssetReferenceTypeAdapterFactory.
     */
    @Getter
    @Setter
    private AnimatorController controller;

    /**
     * Whether to start in the default state when scene loads.
     */
    @Getter
    @Setter
    private boolean autoPlay = true;

    /**
     * Playback speed multiplier (1.0 = normal speed).
     */
    @Getter
    private float speed = 1.0f;

    // ========================================================================
    // RUNTIME STATE (not serialized)
    // ========================================================================

    @HideInInspector
    private AnimationPlayer player;

    @HideInInspector
    private AnimatorStateMachine stateMachine;

    // ========================================================================
    // COMPONENT REFERENCE (auto-resolved, not serialized)
    // ========================================================================

    @ComponentRef
    private SpriteRenderer spriteRenderer;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimatorComponent() {
    }

    public AnimatorComponent(AnimatorController controller) {
        this.controller = controller;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        if (controller != null) {
            initializeStateMachine();
            if (autoPlay) {
                updateSprite();
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        if (stateMachine == null) return;

        // Update the state machine (evaluates transitions, advances animation)
        boolean frameChanged = stateMachine.update(deltaTime * speed);

        if (frameChanged && spriteRenderer != null) {
            updateSprite();
        }
    }

    private void initializeStateMachine() {
        player = new AnimationPlayer();
        player.setSpeed(speed);
        stateMachine = new AnimatorStateMachine(controller, player);
    }

    private void updateSprite() {
        if (player == null || spriteRenderer == null) return;

        Sprite sprite = player.getCurrentSprite();
        if (sprite != null) {
            spriteRenderer.setSprite(sprite);
        }
    }

    // ========================================================================
    // PARAMETER API
    // ========================================================================

    /**
     * Sets a boolean parameter value.
     */
    public void setBool(String name, boolean value) {
        if (stateMachine != null) {
            stateMachine.setBool(name, value);
        }
    }

    /**
     * Gets a boolean parameter value.
     */
    public boolean getBool(String name) {
        return stateMachine != null && stateMachine.getBool(name);
    }

    /**
     * Sets the facing direction.
     * Directional states will update their animation accordingly.
     */
    public void setDirection(Direction direction) {
        if (stateMachine != null) {
            stateMachine.setDirection(direction);
        }
    }

    /**
     * Gets the current facing direction from the direction parameter.
     * Returns DOWN if no direction parameter is defined or no state machine.
     */
    public Direction getDirection() {
        return stateMachine != null ? stateMachine.getCurrentDirection() : Direction.DOWN;
    }

    /**
     * Checks if this animator has a direction parameter defined.
     */
    public boolean hasDirectionParameter() {
        return controller != null && controller.getParameters().stream()
            .anyMatch(p -> p.getType() == com.pocket.rpg.animation.animator.ParameterType.DIRECTION);
    }

    /**
     * Fires a trigger parameter.
     * Triggers are automatically consumed after a transition uses them.
     */
    public void setTrigger(String name) {
        if (stateMachine != null) {
            stateMachine.setTrigger(name);
        }
    }

    /**
     * Resets a trigger without consuming it.
     */
    public void resetTrigger(String name) {
        if (stateMachine != null) {
            stateMachine.resetTrigger(name);
        }
    }

    /**
     * Gets any parameter value.
     */
    public Object getParameterValue(String name) {
        return stateMachine != null ? stateMachine.getParameterValue(name) : null;
    }

    // ========================================================================
    // STATE CONTROL
    // ========================================================================

    /**
     * Forces a transition to a specific state, ignoring conditions.
     */
    public void forceState(String stateName) {
        if (stateMachine != null) {
            stateMachine.forceState(stateName);
            updateSprite();
        }
    }

    /**
     * Gets the current state name.
     */
    public String getCurrentState() {
        return stateMachine != null ? stateMachine.getCurrentState() : null;
    }

    /**
     * Gets the previous state name.
     */
    public String getPreviousState() {
        return stateMachine != null ? stateMachine.getPreviousState() : null;
    }

    /**
     * Checks if currently in a specific state.
     */
    public boolean isInState(String stateName) {
        return stateMachine != null && stateMachine.isInState(stateName);
    }

    /**
     * Checks if a transition is pending (waiting for animation completion).
     */
    public boolean hasPendingTransition() {
        return stateMachine != null && stateMachine.hasPendingTransition();
    }

    /**
     * Cancels any pending transition.
     */
    public void cancelPendingTransition() {
        if (stateMachine != null) {
            stateMachine.cancelPendingTransition();
        }
    }

    /**
     * Resets the state machine to the default state.
     */
    public void reset() {
        if (stateMachine != null) {
            stateMachine.reset();
            updateSprite();
        }
    }

    // ========================================================================
    // CONTROLLER MANAGEMENT
    // ========================================================================

    /**
     * Switches to a different animator controller.
     */
    public void setController(AnimatorController controller) {
        this.controller = controller;
        if (controller != null) {
            initializeStateMachine();
            updateSprite();
        } else {
            stateMachine = null;
            player = null;
        }
    }

    /**
     * Gets the underlying AnimationPlayer for direct access to playback state.
     */
    public AnimationPlayer getPlayer() {
        return player;
    }

    /**
     * Gets the underlying AnimatorStateMachine for advanced control.
     */
    public AnimatorStateMachine getStateMachine() {
        return stateMachine;
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
        if (player != null) {
            player.setSpeed(this.speed);
        }
    }
}
