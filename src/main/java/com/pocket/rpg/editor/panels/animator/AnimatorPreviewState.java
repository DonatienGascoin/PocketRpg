package com.pocket.rpg.editor.panels.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime state for animator preview in the editor.
 * Simulates the AnimatorStateMachine behavior without needing a game object.
 */
public class AnimatorPreviewState {

    @Getter
    private AnimatorController controller;

    // Parameter values (separate from controller defaults)
    private final Map<String, Object> parameterValues = new HashMap<>();

    // Current state
    @Getter
    private String currentStateName;

    @Getter
    private AnimatorState currentState;

    // Animation playback
    @Getter
    private Animation currentAnimation;

    @Getter
    private int currentFrameIndex = 0;

    @Getter
    private float frameTimer = 0f;

    // Transition state
    @Getter
    private AnimatorTransition pendingTransition;

    @Getter
    private boolean waitingForCompletion = false;

    // Playback control
    @Getter @Setter
    private boolean playing = false;

    @Getter @Setter
    private float playbackSpeed = 1.0f;

    public AnimatorPreviewState() {
    }

    /**
     * Sets the controller and resets to default state.
     */
    public void setController(AnimatorController controller) {
        this.controller = controller;
        reset();
    }

    /**
     * Resets to the default state with default parameter values.
     */
    public void reset() {
        parameterValues.clear();
        pendingTransition = null;
        waitingForCompletion = false;
        currentFrameIndex = 0;
        frameTimer = 0f;

        if (controller == null) {
            currentStateName = null;
            currentState = null;
            currentAnimation = null;
            return;
        }

        // Initialize parameter values from controller defaults
        for (int i = 0; i < controller.getParameterCount(); i++) {
            AnimatorParameter param = controller.getParameter(i);
            parameterValues.put(param.getName(), param.getDefaultValue());
        }

        // Go to default state
        String defaultStateName = controller.getDefaultState();
        if (defaultStateName != null && controller.hasState(defaultStateName)) {
            enterState(defaultStateName);
        } else if (controller.getStateCount() > 0) {
            enterState(controller.getState(0).getName());
        } else {
            currentStateName = null;
            currentState = null;
            currentAnimation = null;
        }
    }

    /**
     * Updates the preview state.
     */
    public void update(float deltaTime) {
        if (!playing || controller == null || currentState == null) {
            return;
        }

        // Update animation
        updateAnimation(deltaTime);

        // Check for transitions
        checkTransitions();
    }

    private void updateAnimation(float deltaTime) {
        if (currentAnimation == null || currentAnimation.getFrameCount() == 0) {
            return;
        }

        frameTimer += deltaTime * playbackSpeed;

        float frameDuration = currentAnimation.getFrame(currentFrameIndex).duration();
        while (frameTimer >= frameDuration && frameDuration > 0) {
            frameTimer -= frameDuration;
            currentFrameIndex++;

            if (currentFrameIndex >= currentAnimation.getFrameCount()) {
                if (currentAnimation.isLooping()) {
                    currentFrameIndex = 0;
                    // Animation completed a loop - check if we were waiting
                    if (waitingForCompletion && pendingTransition != null) {
                        if (pendingTransition.getType() == TransitionType.WAIT_FOR_LOOP) {
                            executeTransition(pendingTransition);
                            return;
                        }
                    }
                } else {
                    currentFrameIndex = currentAnimation.getFrameCount() - 1;
                    // Animation completed - execute pending transition
                    if (waitingForCompletion && pendingTransition != null) {
                        executeTransition(pendingTransition);
                        return;
                    }
                }
            }

            if (currentFrameIndex < currentAnimation.getFrameCount()) {
                frameDuration = currentAnimation.getFrame(currentFrameIndex).duration();
            }
        }
    }

    private void checkTransitions() {
        if (currentState == null || waitingForCompletion) {
            return;
        }

        // Find matching transitions from current state
        for (int i = 0; i < controller.getTransitionCount(); i++) {
            AnimatorTransition trans = controller.getTransition(i);
            if (!trans.getFrom().equals(currentStateName)) {
                continue;
            }

            // Check if conditions are met
            if (evaluateConditions(trans)) {
                if (trans.getType() == TransitionType.INSTANT) {
                    executeTransition(trans);
                    return;
                } else {
                    // Wait for completion/loop
                    pendingTransition = trans;
                    waitingForCompletion = true;
                    return;
                }
            }
        }
    }

    private boolean evaluateConditions(AnimatorTransition trans) {
        for (TransitionCondition cond : trans.getConditions()) {
            Object value = parameterValues.get(cond.getParameter());
            if (value == null) {
                return false;
            }

            // For triggers, any true value means the trigger was fired
            AnimatorParameter param = controller.getParameter(cond.getParameter());
            if (param != null && param.getType() == ParameterType.TRIGGER) {
                if (!(value instanceof Boolean) || !(Boolean) value) {
                    return false;
                }
                continue;
            }

            // For other types, check if values match
            Object expectedValue = cond.getValue();
            boolean conditionMet;
            if (expectedValue instanceof Boolean expectedBool && value instanceof Boolean actualBool) {
                conditionMet = expectedBool.equals(actualBool);
            } else if (expectedValue instanceof String expectedStr && value instanceof Direction actualDir) {
                // Direction stored as string in condition
                conditionMet = actualDir.name().equals(expectedStr);
            } else if (value instanceof Direction actualDir && expectedValue instanceof Direction expectedDir) {
                conditionMet = actualDir == expectedDir;
            } else {
                conditionMet = value.equals(expectedValue);
            }

            if (!conditionMet) {
                return false;
            }
        }
        return true;
    }

    private void executeTransition(AnimatorTransition trans) {
        // Consume triggers
        for (TransitionCondition cond : trans.getConditions()) {
            AnimatorParameter param = controller.getParameter(cond.getParameter());
            if (param != null && param.getType() == ParameterType.TRIGGER) {
                parameterValues.put(param.getName(), false);
            }
        }

        pendingTransition = null;
        waitingForCompletion = false;
        enterState(trans.getTo());
    }

    private void enterState(String stateName) {
        currentStateName = stateName;
        currentState = controller.getState(stateName);
        currentFrameIndex = 0;
        frameTimer = 0f;
        pendingTransition = null;
        waitingForCompletion = false;

        if (currentState == null) {
            currentAnimation = null;
            return;
        }

        // Get animation path based on state type
        String animPath = null;
        if (currentState.getType() == StateType.SIMPLE) {
            animPath = currentState.getAnimation();
        } else if (currentState.getType() == StateType.DIRECTIONAL) {
            // Get direction from parameter
            Direction dir = getDirectionParameter();
            animPath = currentState.getDirectionalAnimation(dir);
        }

        // Load animation
        if (animPath != null && !animPath.isBlank()) {
            try {
                currentAnimation = Assets.load(animPath, Animation.class);
            } catch (Exception e) {
                currentAnimation = null;
            }
        } else {
            currentAnimation = null;
        }
    }

    private Direction getDirectionParameter() {
        // Use the explicit direction parameter from the state if set
        if (currentState != null && currentState.getDirectionParameter() != null) {
            String paramName = currentState.getDirectionParameter();
            Object value = parameterValues.get(paramName);
            if (value instanceof Direction) {
                return (Direction) value;
            }
        }

        // Fallback: find first direction parameter
        for (int i = 0; i < controller.getParameterCount(); i++) {
            AnimatorParameter param = controller.getParameter(i);
            if (param.getType() == ParameterType.DIRECTION) {
                Object value = parameterValues.get(param.getName());
                if (value instanceof Direction) {
                    return (Direction) value;
                }
            }
        }
        return Direction.DOWN;
    }

    // ========================================================================
    // PARAMETER ACCESS
    // ========================================================================

    /**
     * Gets a parameter value.
     */
    public Object getParameterValue(String name) {
        return parameterValues.get(name);
    }

    /**
     * Sets a bool parameter value.
     */
    public void setBool(String name, boolean value) {
        AnimatorParameter param = controller != null ? controller.getParameter(name) : null;
        if (param != null && param.getType() == ParameterType.BOOL) {
            parameterValues.put(name, value);
            // Re-check transitions when parameter changes
            if (playing) {
                checkTransitions();
            }
        }
    }

    /**
     * Fires a trigger parameter.
     */
    public void setTrigger(String name) {
        AnimatorParameter param = controller != null ? controller.getParameter(name) : null;
        if (param != null && param.getType() == ParameterType.TRIGGER) {
            parameterValues.put(name, true);
            // Immediately check transitions
            if (playing) {
                checkTransitions();
            }
        }
    }

    /**
     * Sets a direction parameter value.
     */
    public void setDirection(String name, Direction value) {
        AnimatorParameter param = controller != null ? controller.getParameter(name) : null;
        if (param != null && param.getType() == ParameterType.DIRECTION) {
            parameterValues.put(name, value);
            // If current state is directional, update animation
            if (currentState != null && currentState.getType() == StateType.DIRECTIONAL) {
                String animPath = currentState.getDirectionalAnimation(value);
                if (animPath != null && !animPath.isBlank()) {
                    try {
                        currentAnimation = Assets.load(animPath, Animation.class);
                        // Keep current frame if possible
                        if (currentAnimation != null && currentFrameIndex >= currentAnimation.getFrameCount()) {
                            currentFrameIndex = 0;
                            frameTimer = 0f;
                        }
                    } catch (Exception e) {
                        currentAnimation = null;
                    }
                }
            }
            // Re-check transitions
            if (playing) {
                checkTransitions();
            }
        }
    }

    /**
     * Gets the bool value of a parameter.
     */
    public boolean getBool(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * Gets the direction value of a parameter.
     */
    public Direction getDirection(String name) {
        Object value = parameterValues.get(name);
        return value instanceof Direction ? (Direction) value : Direction.DOWN;
    }
}
