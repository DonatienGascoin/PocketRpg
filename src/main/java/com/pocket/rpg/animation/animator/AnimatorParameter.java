package com.pocket.rpg.animation.animator;

import com.pocket.rpg.collision.Direction;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * Defines a parameter in an AnimatorController.
 * Parameters are runtime values that drive transition conditions.
 */
@Getter
@Setter
public class AnimatorParameter {

    private String name;
    private ParameterType type;
    private Object defaultValue;

    /**
     * Default constructor for serialization.
     */
    public AnimatorParameter() {
        this.name = "";
        this.type = ParameterType.BOOL;
        this.defaultValue = false;
    }

    /**
     * Creates a boolean parameter.
     */
    public AnimatorParameter(String name, boolean defaultValue) {
        this.name = name;
        this.type = ParameterType.BOOL;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a direction parameter.
     */
    public AnimatorParameter(String name, Direction defaultValue) {
        this.name = name;
        this.type = ParameterType.DIRECTION;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a trigger parameter.
     */
    public static AnimatorParameter trigger(String name) {
        AnimatorParameter param = new AnimatorParameter();
        param.name = name;
        param.type = ParameterType.TRIGGER;
        param.defaultValue = false;
        return param;
    }

    /**
     * Full constructor.
     */
    public AnimatorParameter(String name, ParameterType type, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a deep copy of this parameter.
     */
    public AnimatorParameter copy() {
        return new AnimatorParameter(name, type, defaultValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimatorParameter that = (AnimatorParameter) o;
        return Objects.equals(name, that.name) &&
               type == that.type &&
               Objects.equals(defaultValue, that.defaultValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, defaultValue);
    }

    @Override
    public String toString() {
        return "AnimatorParameter{" +
               "name='" + name + '\'' +
               ", type=" + type +
               ", defaultValue=" + defaultValue +
               '}';
    }
}
