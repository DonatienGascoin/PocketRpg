package com.pocket.rpg.components;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a UIComponent field as resolved from a UIManager key at runtime.
 * <p>
 * The annotated field is a non-transient UIComponent subclass. It is serialized as
 * a plain JSON string (the uiKey value) rather than as a UIComponent object. At runtime,
 * the resolver reads the stored key, looks up the UIComponent via
 * {@code UIManager.get(key, type)}, and injects the result into this field — similar
 * to how {@link ComponentRef} resolves component references.
 * <p>
 * In the editor, the field is rendered as a dropdown of all available UI keys
 * in the scene, filtered by the expected UIComponent type.
 * <p>
 * Example:
 * <pre>
 * // Serialized as a JSON string key, resolved at runtime via UIManager
 * {@literal @}UiKeyReference
 * private UIText elevationText;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UiKeyReference {

    /**
     * Whether this reference is required for the component to function.
     * If true and the reference cannot be resolved, a warning is logged.
     */
    boolean required() default true;
}
