package com.pocket.rpg.animation.animator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Asset that defines an animator state machine.
 * <p>
 * Contains:
 * - States (each with animations)
 * - Transitions (rules for switching between states)
 * - Parameters (runtime values that drive transitions)
 * <p>
 * Loaded from .animator.json files via AnimatorControllerLoader.
 */
@Getter
@Setter
public class AnimatorController {

    /**
     * Name of this animator controller.
     */
    private String name;

    /**
     * The state to enter when the animator starts.
     */
    private String defaultState;

    /**
     * All states in this animator.
     */
    private List<AnimatorState> states = new ArrayList<>();

    /**
     * All transitions between states.
     */
    private List<AnimatorTransition> transitions = new ArrayList<>();

    /**
     * Parameters that can be set at runtime to drive transitions.
     */
    private List<AnimatorParameter> parameters = new ArrayList<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimatorController() {
        this.name = "";
    }

    public AnimatorController(String name) {
        this.name = name;
    }

    // ========================================================================
    // STATES
    // ========================================================================

    /**
     * Adds a state to this controller.
     */
    public void addState(AnimatorState state) {
        states.add(state);
        // If this is the first state, make it the default
        if (defaultState == null || defaultState.isEmpty()) {
            defaultState = state.getName();
        }
    }

    /**
     * Removes a state by name.
     */
    public void removeState(String name) {
        states.removeIf(s -> Objects.equals(s.getName(), name));
        // Also remove transitions involving this state
        transitions.removeIf(t ->
            Objects.equals(t.getFrom(), name) || Objects.equals(t.getTo(), name)
        );
        // Clear default if it was this state
        if (Objects.equals(defaultState, name)) {
            defaultState = states.isEmpty() ? null : states.get(0).getName();
        }
    }

    /**
     * Gets a state by name.
     */
    public AnimatorState getState(String name) {
        for (AnimatorState state : states) {
            if (Objects.equals(state.getName(), name)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Gets a state by index.
     */
    public AnimatorState getState(int index) {
        if (index < 0 || index >= states.size()) {
            return null;
        }
        return states.get(index);
    }

    /**
     * Gets the index of a state by name.
     */
    public int getStateIndex(String name) {
        for (int i = 0; i < states.size(); i++) {
            if (Objects.equals(states.get(i).getName(), name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a state exists.
     */
    public boolean hasState(String name) {
        return getState(name) != null;
    }

    /**
     * Gets the number of states.
     */
    public int getStateCount() {
        return states.size();
    }

    // ========================================================================
    // TRANSITIONS
    // ========================================================================

    /**
     * Adds a transition to this controller.
     */
    public void addTransition(AnimatorTransition transition) {
        transitions.add(transition);
    }

    /**
     * Removes a transition by index.
     */
    public void removeTransition(int index) {
        if (index >= 0 && index < transitions.size()) {
            transitions.remove(index);
        }
    }

    /**
     * Gets a transition by index.
     */
    public AnimatorTransition getTransition(int index) {
        if (index < 0 || index >= transitions.size()) {
            return null;
        }
        return transitions.get(index);
    }

    /**
     * Gets all transitions from a given state (including "any state" transitions).
     */
    public List<AnimatorTransition> getTransitionsFrom(String stateName) {
        List<AnimatorTransition> result = new ArrayList<>();
        for (AnimatorTransition t : transitions) {
            if (Objects.equals(t.getFrom(), stateName) || t.isFromAnyState()) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * Checks if a transition already exists between two states.
     */
    public boolean hasTransition(String from, String to) {
        for (AnimatorTransition t : transitions) {
            if (Objects.equals(t.getFrom(), from) && Objects.equals(t.getTo(), to)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of transitions.
     */
    public int getTransitionCount() {
        return transitions.size();
    }

    // ========================================================================
    // PARAMETERS
    // ========================================================================

    /**
     * Adds a parameter to this controller.
     */
    public void addParameter(AnimatorParameter parameter) {
        parameters.add(parameter);
    }

    /**
     * Removes a parameter by index.
     */
    public void removeParameter(int index) {
        if (index >= 0 && index < parameters.size()) {
            parameters.remove(index);
        }
    }

    /**
     * Removes a parameter by name.
     */
    public void removeParameter(String name) {
        parameters.removeIf(p -> Objects.equals(p.getName(), name));
    }

    /**
     * Gets a parameter by name.
     */
    public AnimatorParameter getParameter(String name) {
        for (AnimatorParameter param : parameters) {
            if (Objects.equals(param.getName(), name)) {
                return param;
            }
        }
        return null;
    }

    /**
     * Gets a parameter by index.
     */
    public AnimatorParameter getParameter(int index) {
        if (index < 0 || index >= parameters.size()) {
            return null;
        }
        return parameters.get(index);
    }

    /**
     * Checks if a parameter exists.
     */
    public boolean hasParameter(String name) {
        return getParameter(name) != null;
    }

    /**
     * Gets the number of parameters.
     */
    public int getParameterCount() {
        return parameters.size();
    }

    // ========================================================================
    // COPY
    // ========================================================================

    /**
     * Creates a deep copy of this controller.
     */
    public AnimatorController copy() {
        AnimatorController copy = new AnimatorController();
        copy.name = this.name;
        copy.defaultState = this.defaultState;

        for (AnimatorState state : this.states) {
            copy.states.add(state.copy());
        }
        for (AnimatorTransition trans : this.transitions) {
            copy.transitions.add(trans.copy());
        }
        for (AnimatorParameter param : this.parameters) {
            copy.parameters.add(param.copy());
        }

        return copy;
    }

    /**
     * Copies data from another controller into this one.
     * Used for hot-reload.
     */
    public void copyFrom(AnimatorController other) {
        this.name = other.name;
        this.defaultState = other.defaultState;

        this.states.clear();
        for (AnimatorState state : other.states) {
            this.states.add(state.copy());
        }

        this.transitions.clear();
        for (AnimatorTransition trans : other.transitions) {
            this.transitions.add(trans.copy());
        }

        this.parameters.clear();
        for (AnimatorParameter param : other.parameters) {
            this.parameters.add(param.copy());
        }
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    /**
     * Validates this controller and returns a list of issues.
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();

        if (name == null || name.isBlank()) {
            issues.add("Controller has no name");
        }

        if (states.isEmpty()) {
            issues.add("Controller has no states");
        }

        if (defaultState == null || defaultState.isBlank()) {
            issues.add("No default state set");
        } else if (!hasState(defaultState)) {
            issues.add("Default state '" + defaultState + "' does not exist");
        }

        // Check transitions reference valid states
        for (AnimatorTransition trans : transitions) {
            if (!trans.isFromAnyState() && !hasState(trans.getFrom())) {
                issues.add("Transition references unknown state: " + trans.getFrom());
            }
            if (!trans.isToPreviousState() && !hasState(trans.getTo())) {
                issues.add("Transition references unknown state: " + trans.getTo());
            }

            // Check conditions reference valid parameters
            for (TransitionCondition cond : trans.getConditions()) {
                if (!hasParameter(cond.getParameter())) {
                    issues.add("Condition references unknown parameter: " + cond.getParameter());
                }
            }
        }

        return issues;
    }

    /**
     * Checks if this controller is valid (has no validation issues).
     */
    public boolean isValid() {
        return validate().isEmpty();
    }

    @Override
    public String toString() {
        return "AnimatorController{" +
               "name='" + name + '\'' +
               ", states=" + states.size() +
               ", transitions=" + transitions.size() +
               ", parameters=" + parameters.size() +
               '}';
    }
}
