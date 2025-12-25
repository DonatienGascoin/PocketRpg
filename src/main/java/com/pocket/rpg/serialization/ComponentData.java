package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable representation of a component's field values.
 * <p>
 * This is the bridge between serialization and runtime Component instances.
 * Only regular fields are serialized; @ComponentRef fields are resolved at runtime.
 * <p>
 * JSON format:
 * {
 *   "type": "com.pocket.rpg.components.SpriteRenderer",
 *   "fields": {
 *     "zIndex": 10,
 *     "isStatic": false
 *   }
 * }
 */
@Setter
@Getter
public class ComponentData {

    private String type;                    // Full class name
    private Map<String, Object> fields;     // Field name → value

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public ComponentData() {
        this.fields = new HashMap<>();
    }

    public ComponentData(String type) {
        this.type = type;
        this.fields = new HashMap<>();
    }

    // ========================================================================
    // GETTERS/SETTERS
    // ========================================================================

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates ComponentData from a live Component instance.
     * Extracts all serializable field values (excludes @ComponentRef fields).
     */
    public static ComponentData fromComponent(Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component cannot be null");
        }

        ComponentData data = new ComponentData(component.getClass().getName());
        ComponentMeta meta = ComponentRegistry.getByClassName(data.type);

        if (meta == null) {
            System.err.println("Warning: No metadata for component " + data.type);
            return data;
        }

        // Only serialize regular fields (not @ComponentRef)
        for (FieldMeta fieldMeta : meta.fields()) {
            try {
                Field field = fieldMeta.field();
                field.setAccessible(true);
                Object value = field.get(component);

                // Store serializable form
                data.fields.put(fieldMeta.name(), toSerializable(value));

            } catch (IllegalAccessException e) {
                System.err.println("Failed to read field " + fieldMeta.name() + ": " + e.getMessage());
            }
        }

        return data;
    }

    /**
     * Creates a live Component instance from this data.
     * Returns null if component can't be instantiated.
     * <p>
     * Note: @ComponentRef fields are NOT populated here.
     * Call ComponentRefResolver.resolve() after the GameObject hierarchy is set up.
     */
    public Component toComponent() {
        ComponentMeta meta = ComponentRegistry.getByClassName(type);
        if (meta == null) {
            System.err.println("Unknown component type: " + type);
            return null;
        }

        if (!meta.hasNoArgConstructor()) {
            System.err.println("Component has no default constructor: " + type);
            return null;
        }

        Component component = ComponentRegistry.instantiateByClassName(type);
        if (component == null) {
            return null;
        }

        // Apply field values
        for (FieldMeta fieldMeta : meta.fields()) {
            Object value = fields.get(fieldMeta.name());
            if (value != null) {
                try {
                    Field field = fieldMeta.field();
                    field.setAccessible(true);

                    // Convert from serialized form
                    Object converted = fromSerializable(value, fieldMeta.type());
                    field.set(component, converted);

                } catch (Exception e) {
                    System.err.println("Failed to set field " + fieldMeta.name() + ": " + e.getMessage());
                }
            }
        }

        return component;
    }

    // ========================================================================
    // DISPLAY
    // ========================================================================

    /**
     * Gets the simple display name for this component.
     */
    public String getDisplayName() {
        int lastDot = type.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? type.substring(lastDot + 1) : type;
        return ComponentMeta.toDisplayName(simpleName);
    }

    /**
     * Gets the simple class name.
     */
    public String getSimpleName() {
        int lastDot = type.lastIndexOf('.');
        return lastDot >= 0 ? type.substring(lastDot + 1) : type;
    }

    // ========================================================================
    // SERIALIZATION HELPERS
    // ========================================================================

    /**
     * Converts a value to a JSON-serializable form.
     */
    private static Object toSerializable(Object value) {
        if (value == null) {
            return null;
        }

        // Vectors → arrays
        if (value instanceof Vector2f v) {
            return new float[]{v.x, v.y};
        }
        if (value instanceof Vector3f v) {
            return new float[]{v.x, v.y, v.z};
        }
        if (value instanceof Vector4f v) {
            return new float[]{v.x, v.y, v.z, v.w};
        }

        // Enums → strings
        if (value instanceof Enum<?> e) {
            return e.name();
        }

        // Primitives and Strings pass through
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }

        // Unknown - try toString
        return value.toString();
    }

    /**
     * Converts a serialized value back to the target type.
     */
    @SuppressWarnings("unchecked")
    private static Object fromSerializable(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // Handle arrays → Vectors
        if (value instanceof List<?> list) {
            if (targetType == Vector2f.class && list.size() >= 2) {
                return new Vector2f(
                        ((Number) list.get(0)).floatValue(),
                        ((Number) list.get(1)).floatValue()
                );
            }
            if (targetType == Vector3f.class && list.size() >= 3) {
                return new Vector3f(
                        ((Number) list.get(0)).floatValue(),
                        ((Number) list.get(1)).floatValue(),
                        ((Number) list.get(2)).floatValue()
                );
            }
            if (targetType == Vector4f.class && list.size() >= 4) {
                return new Vector4f(
                        ((Number) list.get(0)).floatValue(),
                        ((Number) list.get(1)).floatValue(),
                        ((Number) list.get(2)).floatValue(),
                        ((Number) list.get(3)).floatValue()
                );
            }
        }

        // Handle strings → Enums
        if (value instanceof String s && targetType.isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) targetType, s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Handle number conversions (Gson may load as Double)
        if (value instanceof Number n) {
            if (targetType == int.class || targetType == Integer.class) {
                return n.intValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return n.floatValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return n.doubleValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return n.longValue();
            }
        }

        return value;
    }
}
