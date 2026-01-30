package com.pocket.rpg.serialization;

import com.pocket.rpg.components.ui.UIComponent;

import java.lang.reflect.Field;

/**
 * Metadata about a @UiKeyReference field.
 * <p>
 * The annotated field is a non-transient UIComponent subclass that is serialized as a
 * plain JSON string (the uiKey). At runtime, the resolver reads the stored key and
 * injects the resolved UIComponent into this field.
 *
 * @param field         The UIComponent field annotated with @UiKeyReference
 * @param componentType Expected UIComponent subclass (e.g., UIText.class)
 * @param required      Whether resolution failure should log a warning
 */
public record UiKeyRefMeta(
        Field field,
        Class<? extends UIComponent> componentType,
        boolean required
) {
    /**
     * Gets the field name.
     */
    public String fieldName() {
        return field.getName();
    }

    /**
     * Gets a display-friendly name for the field.
     */
    public String getDisplayName() {
        String name = field.getName();
        String result = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }

    /**
     * Gets a description for editor display.
     * e.g., "UIText reference"
     */
    public String getEditorDescription() {
        return componentType.getSimpleName() + " reference";
    }
}
