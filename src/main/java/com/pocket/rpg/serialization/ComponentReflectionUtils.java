package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;

import java.lang.reflect.Field;

/**
 * Utility for reading/writing Component fields via reflection.
 * Used by editor UI when working with Component instances directly.
 */
public final class ComponentReflectionUtils {

    private ComponentReflectionUtils() {}

    /**
     * Gets a field value from a component.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return The field value, or null if not found
     */
    public static Object getFieldValue(Component component, String fieldName) {
        if (component == null || fieldName == null) {
            return null;
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return null;
        }

        for (FieldMeta fm : meta.fields()) {
            if (fm.name().equals(fieldName)) {
                try {
                    Field field = fm.field();
                    field.setAccessible(true);
                    return field.get(component);
                } catch (IllegalAccessException e) {
                    System.err.println("Failed to read field " + fieldName + ": " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Sets a field value on a component.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @param value     The value to set (will be converted if needed)
     * @return true if successful
     */
    public static boolean setFieldValue(Component component, String fieldName, Object value) {
        if (component == null || fieldName == null) {
            return false;
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return false;
        }

        for (FieldMeta fm : meta.fields()) {
            if (fm.name().equals(fieldName)) {
                try {
                    Field field = fm.field();
                    field.setAccessible(true);
                    Object converted = SerializationUtils.fromSerializable(value, fm.type());
                    field.set(component, converted);
                    return true;
                } catch (Exception e) {
                    System.err.println("Failed to set field " + fieldName + ": " + e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Gets the FieldMeta for a field name.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return FieldMeta or null if not found
     */
    public static FieldMeta getFieldMeta(Component component, String fieldName) {
        if (component == null || fieldName == null) {
            return null;
        }

        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return null;
        }

        for (FieldMeta fm : meta.fields()) {
            if (fm.name().equals(fieldName)) {
                return fm;
            }
        }
        return null;
    }

    /**
     * Gets the ComponentMeta for a component.
     *
     * @param component The component instance
     * @return ComponentMeta or null if not in registry
     */
    public static ComponentMeta getMeta(Component component) {
        if (component == null) {
            return null;
        }
        return ComponentRegistry.getByClassName(component.getClass().getName());
    }

    /**
     * Gets the simple class name (e.g., "SpriteRenderer").
     *
     * @param component The component instance
     * @return Simple class name
     */
    public static String getSimpleName(Component component) {
        return component.getClass().getSimpleName();
    }

    /**
     * Gets the full class name (e.g., "com.pocket.rpg.components.SpriteRenderer").
     *
     * @param component The component instance
     * @return Full class name
     */
    public static String getClassName(Component component) {
        return component.getClass().getName();
    }

    /**
     * Gets the display name (e.g., "Sprite Renderer").
     *
     * @param component The component instance
     * @return Human-readable display name
     */
    public static String getDisplayName(Component component) {
        return ComponentMeta.toDisplayName(getSimpleName(component));
    }

    /**
     * Clones a component by creating a new instance and copying all field values.
     *
     * @param source The component to clone
     * @return Cloned component, or null if cloning failed
     */
    public static Component cloneComponent(Component source) {
        if (source == null) {
            return null;
        }

        ComponentMeta meta = getMeta(source);
        if (meta == null) {
            return null;
        }

        Component clone = ComponentRegistry.instantiateByClassName(source.getClass().getName());
        if (clone == null) {
            return null;
        }

        for (FieldMeta fm : meta.fields()) {
            Object value = getFieldValue(source, fm.name());
            if (value != null) {
                // Deep copy for mutable types
                value = deepCopyValue(value);
                setFieldValue(clone, fm.name(), value);
            }
        }

        return clone;
    }

    /**
     * Creates a deep copy of a value if it's a known mutable type.
     */
    private static Object deepCopyValue(Object value) {
        if (value == null) {
            return null;
        }

        // Vector types - create new instances
        if (value instanceof org.joml.Vector2f v) {
            return new org.joml.Vector2f(v);
        }
        if (value instanceof org.joml.Vector3f v) {
            return new org.joml.Vector3f(v);
        }
        if (value instanceof org.joml.Vector4f v) {
            return new org.joml.Vector4f(v);
        }

        // Immutable types - return as-is
        // (String, Number, Enum, Sprite, Texture, Font are all effectively immutable references)
        return value;
    }

    /**
     * Checks if a component has a field with the given name.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return true if field exists
     */
    public static boolean hasField(Component component, String fieldName) {
        return getFieldMeta(component, fieldName) != null;
    }

    /**
     * Gets a field value as a specific type with null safety.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @param type      Expected type
     * @param <T>       Return type
     * @return The field value cast to type, or null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getFieldValueAs(Component component, String fieldName, Class<T> type) {
        Object value = getFieldValue(component, fieldName);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Gets a float field value with default.
     */
    public static float getFloat(Component component, String fieldName, float defaultValue) {
        Object value = getFieldValue(component, fieldName);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }

    /**
     * Gets an int field value with default.
     */
    public static int getInt(Component component, String fieldName, int defaultValue) {
        Object value = getFieldValue(component, fieldName);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    /**
     * Gets a boolean field value with default.
     */
    public static boolean getBoolean(Component component, String fieldName, boolean defaultValue) {
        Object value = getFieldValue(component, fieldName);
        if (value instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    /**
     * Gets a String field value with default.
     */
    public static String getString(Component component, String fieldName, String defaultValue) {
        Object value = getFieldValue(component, fieldName);
        if (value instanceof String s) {
            return s;
        }
        return defaultValue;
    }

    /**
     * Checks if a field has @Required annotation and is null/empty.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return true if the field is required and has no value
     */
    public static boolean isRequiredAndMissing(Component component, String fieldName) {
        if (component == null || fieldName == null) {
            return false;
        }

        try {
            Field field = getFieldRecursive(component.getClass(), fieldName);
            if (field == null) return false;

            if (!field.isAnnotationPresent(Required.class)) {
                return false;
            }

            field.setAccessible(true);
            Object value = field.get(component);

            if (value == null) return true;
            if (value instanceof String s && s.isEmpty()) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets a field from a class or its superclasses.
     */
    private static Field getFieldRecursive(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
