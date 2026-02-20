package com.pocket.rpg.serialization;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Metadata about a single serializable field in a component.
 * Used for serialization and by the editor to render the correct UI control.
 */
public record FieldMeta(
        String name,           // Field name (e.g., "speed")
        Class<?> type,         // Field type (e.g., float.class)
        Field field,           // Actual Field object for reflection
        Object defaultValue,   // Default value for reset
        Class<?> elementType,  // For List<T>, the element type T; null for non-List fields
        Class<?> keyType,      // For Map<K,V>, the key type K; null for non-Map fields
        Class<?> valueType     // For Map<K,V>, the value type V; null for non-Map fields
) {
    /**
     * Constructor for non-collection fields (backwards compatible).
     */
    public FieldMeta(String name, Class<?> type, Field field, Object defaultValue) {
        this(name, type, field, defaultValue, null, null, null);
    }

    /**
     * Constructor for List fields (backwards compatible).
     */
    public FieldMeta(String name, Class<?> type, Field field, Object defaultValue, Class<?> elementType) {
        this(name, type, field, defaultValue, elementType, null, null);
    }

    /**
     * Returns true if this field is a List with a known element type.
     */
    public boolean isList() {
        return List.class.isAssignableFrom(type) && elementType != null;
    }

    /**
     * Returns true if this field is a Map with known key and value types.
     */
    public boolean isMap() {
        return Map.class.isAssignableFrom(type) && keyType != null && valueType != null;
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
