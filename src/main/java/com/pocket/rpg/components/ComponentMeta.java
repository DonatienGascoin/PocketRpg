package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation for components.
 * Used to provide additional information for the component browser.
 * <p>
 * Inherited by subclasses, so adding to a base class applies to all derived classes.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ComponentMeta {
    /**
     * Category for grouping in the component browser.
     * Examples: "Rendering", "Audio", "Physics", "UI"
     */
    String category() default "";

    // Future extensibility:
    // String icon() default "";
    // String description() default "";
}
