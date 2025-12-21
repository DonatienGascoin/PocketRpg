package com.pocket.rpg.editor.components;

import java.lang.reflect.Field;

/**
 * Metadata about a single editable field in a component.
 * Used by the inspector to render the correct UI control.
 */
public record FieldMeta(
        String name,           // Field name (e.g., "speed")
        Class<?> type,         // Field type (e.g., float.class)
        Field field,           // Actual Field object for reflection
        Object defaultValue    // Default value for reset button
) {
    /**
     * Gets a display-friendly name.
     * "moveSpeed" → "Move Speed"
     */
    public String getDisplayName() {
        // Insert space before capitals and capitalize first letter
        String result = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
}