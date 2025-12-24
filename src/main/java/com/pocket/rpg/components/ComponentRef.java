package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a component reference that is resolved at runtime.
 * <p>
 * Fields annotated with @ComponentRef are NOT serialized. Instead, they are
 * automatically resolved when the GameObject is initialized by searching for
 * the component in the specified source location.
 * <p>
 * For single component fields, the first matching component is used.
 * For List fields, all matching components are collected.
 * <p>
 * Examples:
 * <pre>
 * // Reference to Transform on same GameObject (default)
 * {@literal @}ComponentRef
 * private Transform transform;
 *
 * // Reference to a component on the parent GameObject
 * {@literal @}ComponentRef(source = Source.PARENT)
 * private Inventory parentInventory;
 *
 * // Collect all Collider components from children
 * {@literal @}ComponentRef(source = Source.CHILDREN)
 * private List<Collider> childColliders;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ComponentRef {

    /**
     * Where to search for the referenced component.
     */
    Source source() default Source.SELF;

    /**
     * Whether this reference is required for the component to function.
     * If true and the reference cannot be resolved, a warning is logged.
     */
    boolean required() default true;

    /**
     * Specifies where to search for the referenced component.
     */
    enum Source {
        /**
         * Search on the same GameObject (default).
         */
        SELF,

        /**
         * Search on the parent GameObject.
         * Returns null if no parent exists.
         */
        PARENT,

        /**
         * Search on direct children GameObjects.
         * For single fields: returns first match found.
         * For List fields: returns all matches.
         */
        CHILDREN,

        /**
         * Search on all descendants (children, grandchildren, etc.).
         * For single fields: returns first match found (depth-first).
         * For List fields: returns all matches.
         */
        CHILDREN_RECURSIVE
    }
}
