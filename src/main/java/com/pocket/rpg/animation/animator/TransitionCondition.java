package com.pocket.rpg.animation.animator;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * A condition that must be met for a transition to occur.
 * Compares a parameter value against an expected value.
 */
@Getter
@Setter
public class TransitionCondition {

    /**
     * The parameter name to check.
     */
    private String parameter;

    /**
     * The expected value. Type depends on parameter type:
     * - BOOL: Boolean
     * - DIRECTION: Direction enum (stored as String for serialization)
     * - TRIGGER: always true (triggers are consumed when checked)
     */
    private Object value;

    /**
     * Default constructor for serialization.
     */
    public TransitionCondition() {
        this.parameter = "";
        this.value = true;
    }

    /**
     * Creates a boolean condition.
     */
    public TransitionCondition(String parameter, boolean value) {
        this.parameter = parameter;
        this.value = value;
    }

    /**
     * Creates a condition with any value type.
     */
    public TransitionCondition(String parameter, Object value) {
        this.parameter = parameter;
        this.value = value;
    }

    /**
     * Creates a deep copy of this condition.
     */
    public TransitionCondition copy() {
        return new TransitionCondition(parameter, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransitionCondition that = (TransitionCondition) o;
        return Objects.equals(parameter, that.parameter) &&
               Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameter, value);
    }

    @Override
    public String toString() {
        return parameter + " == " + value;
    }
}
