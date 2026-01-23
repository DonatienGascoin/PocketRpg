package com.pocket.rpg.serialization;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Metadata about a single serializable field in a component.
 * Used for serialization and by the editor to render the correct UI control.
 */
public record FieldMeta(
        String name,           // Field name (e.g., "speed")
        Class<?> type,         // Field type (e.g., float.class)
        Field field,           // Actual Field object for reflection
        Object defaultValue,   // Default value for reset
        Class<?> elementType   // For List<T>, the element type T; null for non-List fields
) {
    /**
     * Constructor for non-List fields (backwards compatible).
     */
    public FieldMeta(String name, Class<?> type, Field field, Object defaultValue) {
        this(name, type, field, defaultValue, null);
    }

    /**
     * Returns true if this field is a List with a known element type.
     */
    public boolean isList() {
        return List.class.isAssignableFrom(type) && elementType != null;
    }

    /**
     * Gets a display-friendly name.
     * "moveSpeed" → "Move Speed"
     */
    public String getDisplayName() {
        String result = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1);
    }
}
