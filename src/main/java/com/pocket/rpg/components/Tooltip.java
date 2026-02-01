package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a tooltip to a component field in the inspector.
 * Shown when the user hovers over the field label.
 * <p>
 * Example:
 * <pre>
 * {@literal @}Tooltip("Direction the player must approach from to interact")
 * private Direction interactFrom = Direction.DOWN;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Tooltip {
    String value();
}
