package com.pocket.rpg.animation.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationPlayer;
import com.pocket.rpg.collision.Direction;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime state machine that evaluates transitions and manages animation playback.
 * <p>
 * This class holds the runtime state for an AnimatorController:
 * - Current state
 * - Parameter values
 * - Pending transitions
 * - Animation playback
 */
public class AnimatorStateMachine {

    private final AnimatorController controller;
    private final AnimationPlayer player;

    @Getter
    private String currentState;

    @Getter
    private String previousState;

    @Getter
    private Direction currentDirection = Direction.DOWN;

    private final Map<String, Object> parameterValues = new HashMap<>();

    // Pending transition (for WAIT_FOR_COMPLETION)
    private AnimatorTransition pendingTransition;

    // Track if we need to update the animation due to direction change
    private Direction lastAppliedDirection;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimatorStateMachine(AnimatorController controller, AnimationPlayer player) {
        this.controller = controller;
        this.player = player;

        // Initialize parameter values to defaults
        for (AnimatorParameter param : controller.getParameters()) {
            parameterValues.put(param.getName(), param.getDefaultValue());
        }

        // Enter default state
        if (controller.getDefaultState() != null) {
            enterState(controller.getDefaultState());
        }
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Updates the state machine, evaluating transitions and advancing animation.
     *
     * @param deltaTime Time since last update in seconds
     * @return true if the animation frame changed
     */
    public boolean update(float deltaTime) {
        if (controller == null || currentState == null) {
            return false;
        }

        // Check for pending transition completion
        if (pendingTransition != null) {
            if (canCompletePendingTransition()) {
                executeTransition(pendingTransition);
                pendingTransition = null;
            }
        } else {
            // Evaluate automatic transitions
            evaluateTransitions();
        }

        // Check for direction change in directional states
        AnimatorState state = controller.getState(currentState);
        if (state != null && state.getType() == StateType.DIRECTIONAL) {
            if (lastAppliedDirection != currentDirection) {
                applyStateAnimation(state);
            }
        }

        // Update animation playback
        return player.update(deltaTime);
    }

    /**
     * Evaluates all transitions from the current state and fires the first one that matches.
     */
    private void evaluateTransitions() {
        List<AnimatorTransition> transitions = controller.getTransitionsFrom(currentState);

        for (AnimatorTransition trans : transitions) {
            // Skip transitions to current state
            if (Objects.equals(trans.getTo(), currentState)) {
                continue;
            }

            // Check if all conditions are met
            if (checkConditions(trans)) {
                requestTransition(trans);
                break; // First matching transition wins
            }
        }
    }

    /**
     * Checks if all conditions for a transition are met.
     */
    private boolean checkConditions(AnimatorTransition transition) {
        if (!transition.hasConditions()) {
            return false; // No conditions = manual trigger only
        }

        for (TransitionCondition cond : transition.getConditions()) {
            if (!checkCondition(cond)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a single condition is met.
     */
    private boolean checkCondition(TransitionCondition condition) {
        Object currentValue = parameterValues.get(condition.getParameter());
        Object expectedValue = condition.getValue();

        if (currentValue == null) {
            return expectedValue == null;
        }

        // Handle Direction comparison (may be stored as String)
        if (currentValue instanceof Direction && expectedValue instanceof String) {
            return currentValue.toString().equals(expectedValue);
        }
        if (currentValue instanceof String && expectedValue instanceof Direction) {
            return currentValue.equals(expectedValue.toString());
        }

        return Objects.equals(currentValue, expectedValue);
    }

    /**
     * Requests a transition to occur.
     */
    private void requestTransition(AnimatorTransition transition) {
        switch (transition.getType()) {
            case INSTANT -> executeTransition(transition);
            case WAIT_FOR_COMPLETION -> {
                if (player.isFinished() || !player.hasAnimation()) {
                    executeTransition(transition);
                } else {
                    pendingTransition = transition;
                }
            }
            case WAIT_FOR_LOOP -> {
                // For looping animations, we need to wait for the loop point
                // For now, treat as instant for non-looping or if at start
                if (player.getCurrentFrame() == 0 || player.isFinished()) {
                    executeTransition(transition);
                } else {
                    pendingTransition = transition;
                }
            }
        }

        // Consume triggers
        consumeTriggers(transition);
    }

    /**
     * Checks if a pending transition can now complete.
     */
    private boolean canCompletePendingTransition() {
        if (pendingTransition == null) {
            return false;
        }

        return switch (pendingTransition.getType()) {
            case INSTANT -> true;
            case WAIT_FOR_COMPLETION -> player.isFinished() || !player.hasAnimation();
            case WAIT_FOR_LOOP -> player.getCurrentFrame() == 0 || player.isFinished();
        };
    }

    /**
     * Executes a transition to a new state.
     */
    private void executeTransition(AnimatorTransition transition) {
        String targetState = transition.getTo();

        // Handle "return to previous" wildcard
        if (transition.isToPreviousState()) {
            targetState = previousState != null ? previousState : controller.getDefaultState();
        }

        if (targetState != null && !Objects.equals(targetState, currentState)) {
            enterState(targetState);
        }
    }

    /**
     * Consumes any trigger parameters used in a transition's conditions.
     */
    private void consumeTriggers(AnimatorTransition transition) {
        for (TransitionCondition cond : transition.getConditions()) {
            AnimatorParameter param = controller.getParameter(cond.getParameter());
            if (param != null && param.getType() == ParameterType.TRIGGER) {
                parameterValues.put(cond.getParameter(), false);
            }
        }
    }

    /**
     * Enters a new state.
     */
    private void enterState(String stateName) {
        AnimatorState state = controller.getState(stateName);
        if (state == null) {
            return;
        }

        previousState = currentState;
        currentState = stateName;

        applyStateAnimation(state);
    }

    /**
     * Applies the animation for the current state and direction.
     */
    private void applyStateAnimation(AnimatorState state) {
        Animation anim = state.loadAnimation(currentDirection);
        if (anim != null) {
            player.setAnimation(anim);
        }
        lastAppliedDirection = currentDirection;
    }

    // ========================================================================
    // PARAMETER API
    // ========================================================================

    /**
     * Sets a boolean parameter value.
     */
    public void setBool(String name, boolean value) {
        AnimatorParameter param = controller.getParameter(name);
        if (param != null && param.getType() == ParameterType.BOOL) {
            parameterValues.put(name, value);
        }
    }

    /**
     * Gets a boolean parameter value.
     */
    public boolean getBool(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Boolean ? (Boolean) value : false;
    }

    /**
     * Sets the direction parameter (special built-in parameter).
     */
    public void setDirection(Direction direction) {
        this.currentDirection = direction;
        // Also set any direction-type parameters
        for (AnimatorParameter param : controller.getParameters()) {
            if (param.getType() == ParameterType.DIRECTION) {
                parameterValues.put(param.getName(), direction);
            }
        }
    }

    /**
     * Fires a trigger parameter.
     */
    public void setTrigger(String name) {
        AnimatorParameter param = controller.getParameter(name);
        if (param != null && param.getType() == ParameterType.TRIGGER) {
            parameterValues.put(name, true);
        }
    }

    /**
     * Resets a trigger parameter without consuming it.
     */
    public void resetTrigger(String name) {
        AnimatorParameter param = controller.getParameter(name);
        if (param != null && param.getType() == ParameterType.TRIGGER) {
            parameterValues.put(name, false);
        }
    }

    /**
     * Gets any parameter value.
     */
    public Object getParameterValue(String name) {
        return parameterValues.get(name);
    }

    // ========================================================================
    // STATE CONTROL
    // ========================================================================

    /**
     * Forces a transition to a specific state, ignoring conditions.
     */
    public void forceState(String stateName) {
        if (controller.hasState(stateName)) {
            pendingTransition = null;
            enterState(stateName);
        }
    }

    /**
     * Checks if currently in a specific state.
     */
    public boolean isInState(String stateName) {
        return Objects.equals(currentState, stateName);
    }

    /**
     * Checks if a transition is pending.
     */
    public boolean hasPendingTransition() {
        return pendingTransition != null;
    }

    /**
     * Cancels any pending transition.
     */
    public void cancelPendingTransition() {
        pendingTransition = null;
    }

    /**
     * Gets the animation player for direct access to playback state.
     */
    public AnimationPlayer getPlayer() {
        return player;
    }

    /**
     * Resets the state machine to the default state.
     */
    public void reset() {
        pendingTransition = null;

        // Reset parameters to defaults
        for (AnimatorParameter param : controller.getParameters()) {
            parameterValues.put(param.getName(), param.getDefaultValue());
        }

        // Enter default state without recording previous state
        if (controller.getDefaultState() != null) {
            enterState(controller.getDefaultState());
        }
        // Clear previous state after entering default (reset means fresh start)
        previousState = null;
    }
}
