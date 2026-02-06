package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a component reference that is resolved at runtime.
 * <p>
 * Unifies the previous {@code @ComponentRef} (hierarchy-based) and {@code @UiKeyReference}
 * (key-based) annotations into a single annotation with a mandatory {@link Source} parameter.
 * <p>
 * <b>Hierarchy sources</b> (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE) are NOT serialized.
 * They are resolved from the GameObject tree at runtime.
 * <p>
 * <b>Key source</b> (KEY) IS serialized as a JSON string (or string array for List fields).
 * At runtime, the stored key is looked up in {@link com.pocket.rpg.ui.ComponentKeyRegistry}.
 * <p>
 * Examples:
 * <pre>
 * // Same GameObject — not serialized
 * {@literal @}ComponentReference(source = Source.SELF)
 * private Transform transform;
 *
 * // Parent GameObject — not serialized
 * {@literal @}ComponentReference(source = Source.PARENT)
 * private Inventory parentInventory;
 *
 * // Direct children — not serialized
 * {@literal @}ComponentReference(source = Source.CHILDREN)
 * private List&lt;Collider&gt; childColliders;
 *
 * // Key lookup — serialized as string
 * {@literal @}ComponentReference(source = Source.KEY)
 * private BattlerDetailsUI opponentDetails;
 *
 * // Key lookup list — serialized as string[]
 * {@literal @}ComponentReference(source = Source.KEY)
 * private List&lt;BattlerDetailsUI&gt; allDetails;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ComponentReference {

    /**
     * Where to search for the referenced component. Must be specified explicitly.
     */
    Source source();

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
         * Search on the same GameObject. Not serialized.
         */
        SELF,

        /**
         * Search on the parent GameObject. Not serialized.
         * Returns null if no parent exists.
         */
        PARENT,

        /**
         * Search on direct children GameObjects. Not serialized.
         * For single fields: returns first match found.
         * For List fields: returns all matches.
         */
        CHILDREN,

        /**
         * Search on all descendants (children, grandchildren, etc.). Not serialized.
         * For single fields: returns first match found (depth-first).
         * For List fields: returns all matches.
         */
        CHILDREN_RECURSIVE,

        /**
         * Look up by componentKey in {@link com.pocket.rpg.ui.ComponentKeyRegistry}.
         * Serialized as a JSON string (single field) or string array (List field).
         */
        KEY
    }
}
