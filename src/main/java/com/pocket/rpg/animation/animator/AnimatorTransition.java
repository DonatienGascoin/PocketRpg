package com.pocket.rpg.animation.animator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Defines a transition between two states in an AnimatorController.
 */
@Getter
@Setter
public class AnimatorTransition {

    /**
     * Special constant for "any state" wildcard.
     */
    public static final String ANY_STATE = "*";

    /**
     * Source state name, or "*" for any state.
     */
    private String from;

    /**
     * Target state name, or "*" to return to previous state.
     */
    private String to;

    /**
     * How the transition occurs (instant, wait for completion, etc.).
     */
    private TransitionType type = TransitionType.INSTANT;

    /**
     * Conditions that must all be true for this transition to fire.
     * Empty list means no automatic conditions (manual trigger only).
     */
    private List<TransitionCondition> conditions = new ArrayList<>();

    /**
     * Default constructor for serialization.
     */
    public AnimatorTransition() {
        this.from = "";
        this.to = "";
    }

    /**
     * Creates a transition with no conditions (manual trigger only).
     */
    public AnimatorTransition(String from, String to, TransitionType type) {
        this.from = from;
        this.to = to;
        this.type = type;
    }

    /**
     * Creates a transition with conditions.
     */
    public AnimatorTransition(String from, String to, TransitionType type, List<TransitionCondition> conditions) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    // ========================================================================
    // CONDITIONS
    // ========================================================================

    /**
     * Adds a condition to this transition.
     */
    public void addCondition(TransitionCondition condition) {
        if (conditions == null) {
            conditions = new ArrayList<>();
        }
        conditions.add(condition);
    }

    /**
     * Removes a condition by index.
     */
    public void removeCondition(int index) {
        if (conditions != null && index >= 0 && index < conditions.size()) {
            conditions.remove(index);
        }
    }

    /**
     * Gets a condition by index.
     */
    public TransitionCondition getCondition(int index) {
        if (conditions == null || index < 0 || index >= conditions.size()) {
            return null;
        }
        return conditions.get(index);
    }

    /**
     * Checks if this transition has any conditions.
     */
    public boolean hasConditions() {
        return conditions != null && !conditions.isEmpty();
    }

    // ========================================================================
    // WILDCARDS
    // ========================================================================

    /**
     * Checks if this transition can fire from any state.
     */
    public boolean isFromAnyState() {
        return ANY_STATE.equals(from);
    }

    /**
     * Checks if this transition returns to the previous state.
     */
    public boolean isToPreviousState() {
        return ANY_STATE.equals(to);
    }

    // ========================================================================
    // COPY
    // ========================================================================

    /**
     * Creates a deep copy of this transition.
     */
    public AnimatorTransition copy() {
        AnimatorTransition copy = new AnimatorTransition();
        copy.from = this.from;
        copy.to = this.to;
        copy.type = this.type;
        if (this.conditions != null) {
            copy.conditions = new ArrayList<>();
            for (TransitionCondition cond : this.conditions) {
                copy.conditions.add(cond.copy());
            }
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimatorTransition that = (AnimatorTransition) o;
        return Objects.equals(from, that.from) &&
               Objects.equals(to, that.to) &&
               type == that.type &&
               Objects.equals(conditions, that.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, type, conditions);
    }

    @Override
    public String toString() {
        String condStr = hasConditions() ? " when " + conditions : "";
        return from + " -> " + to + " (" + type + ")" + condStr;
    }
}
