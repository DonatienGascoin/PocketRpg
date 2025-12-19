package com.pocket.rpg.prefab;

/**
 * Defines an editable property for a prefab instance.
 * <p>
 * Properties allow per-instance customization in the editor.
 * For example, an NPC prefab might expose "dialogueId" as editable
 * while keeping movement speed fixed.
 *
 * @param name         Internal key and display name in editor
 * @param type         Property type for UI rendering
 * @param defaultValue Default value if not overridden
 * @param tooltip      Optional tooltip shown in editor (nullable)
 */
public record PropertyDefinition(
        String name,
        PropertyType type,
        Object defaultValue,
        String tooltip
) {
    /**
     * Creates a property definition without tooltip.
     */
    public PropertyDefinition(String name, PropertyType type, Object defaultValue) {
        this(name, type, defaultValue, null);
    }

    /**
     * Validates that a value is compatible with this property's type.
     */
    public boolean isValidValue(Object value) {
        if (value == null) {
            return true; // Null resets to default
        }

        return switch (type) {
            case STRING -> value instanceof String;
            case INT -> value instanceof Integer;
            case FLOAT -> value instanceof Float || value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case STRING_LIST -> value instanceof java.util.List;
            case VECTOR2 -> value instanceof float[] arr && arr.length == 2;
            case VECTOR3 -> value instanceof float[] arr && arr.length == 3;
            case ASSET_REF -> value instanceof String;
        };
    }

    /**
     * Coerces a value to the correct type if possible.
     * Useful when loading from JSON where numbers might be wrong type.
     */
    public Object coerceValue(Object value) {
        if (value == null) {
            return defaultValue;
        }

        try {
            return switch (type) {
                case STRING -> value.toString();
                case INT -> {
                    if (value instanceof Number n) {
                        yield n.intValue();
                    }
                    yield Integer.parseInt(value.toString());
                }
                case FLOAT -> {
                    if (value instanceof Number n) {
                        yield n.floatValue();
                    }
                    yield Float.parseFloat(value.toString());
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean b) {
                        yield b;
                    }
                    yield Boolean.parseBoolean(value.toString());
                }
                default -> value;
            };
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
