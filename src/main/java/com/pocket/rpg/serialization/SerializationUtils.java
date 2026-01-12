package com.pocket.rpg.serialization;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

/**
 * Utility methods for converting serialized values to proper types.
 * Handles vectors, enums, assets, and other common conversions.
 */
public final class SerializationUtils {

    private SerializationUtils() {}

    /**
     * Converts a serialized value to the target type.
     * Handles common type conversions needed during deserialization.
     *
     * @param value      The serialized value (may be Map, List, String, etc.)
     * @param targetType The expected type
     * @return The converted value, or original if no conversion needed
     */
    @SuppressWarnings("unchecked")
    public static Object fromSerializable(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // Already correct type
        if (targetType.isInstance(value)) {
            return value;
        }

        // Vector2f
        if (targetType == Vector2f.class) {
            return toVector2f(value);
        }

        // Vector3f
        if (targetType == Vector3f.class) {
            return toVector3f(value);
        }

        // Vector4f
        if (targetType == Vector4f.class) {
            return toVector4f(value);
        }

        // Enum
        if (targetType.isEnum()) {
            return toEnum(value, (Class<? extends Enum>) targetType);
        }

        // Sprite
        if (targetType == Sprite.class) {
            return toSprite(value);
        }

        // Asset types (load by path)
        if (isAssetType(targetType) && value instanceof String path) {
            return loadAsset(path, targetType);
        }

        // Number conversions
        if (value instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) {
                return num.intValue();
            }
            if (targetType == float.class || targetType == Float.class) {
                return num.floatValue();
            }
            if (targetType == double.class || targetType == Double.class) {
                return num.doubleValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return num.longValue();
            }
        }

        // Boolean from string
        if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof String str) {
            return Boolean.parseBoolean(str);
        }

        return value;
    }

    private static Vector2f toVector2f(Object value) {
        if (value instanceof Vector2f v) return new Vector2f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloat(m, "x", 0f);
            float y = getFloat(m, "y", 0f);
            return new Vector2f(x, y);
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            return new Vector2f(x, y);
        }
        return new Vector2f();
    }

    private static Vector3f toVector3f(Object value) {
        if (value instanceof Vector3f v) return new Vector3f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloat(m, "x", 0f);
            float y = getFloat(m, "y", 0f);
            float z = getFloat(m, "z", 0f);
            return new Vector3f(x, y, z);
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            float z = ((Number) list.get(2)).floatValue();
            return new Vector3f(x, y, z);
        }
        return new Vector3f();
    }

    private static Vector4f toVector4f(Object value) {
        if (value instanceof Vector4f v) return new Vector4f(v);
        if (value instanceof Map<?, ?> m) {
            float x = getFloat(m, "x", 0f);
            float y = getFloat(m, "y", 0f);
            float z = getFloat(m, "z", 0f);
            float w = getFloat(m, "w", 1f);
            return new Vector4f(x, y, z, w);
        }
        if (value instanceof List<?> list && list.size() >= 4) {
            float x = ((Number) list.get(0)).floatValue();
            float y = ((Number) list.get(1)).floatValue();
            float z = ((Number) list.get(2)).floatValue();
            float w = ((Number) list.get(3)).floatValue();
            return new Vector4f(x, y, z, w);
        }
        return new Vector4f(0, 0, 0, 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toEnum(Object value, Class<? extends Enum> enumType) {
        if (enumType.isInstance(value)) {
            return value;
        }
        String name = value.toString();
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException e) {
            // Try case-insensitive match
            for (Enum<?> constant : enumType.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
            return null;
        }
    }

    private static Sprite toSprite(Object value) {
        if (value instanceof Sprite s) return s;
        if (value instanceof String path) {
            return SpriteReference.fromPath(path);
        }
        return null;
    }

    private static Object loadAsset(String path, Class<?> type) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return Assets.load(path, type);
        } catch (Exception e) {
            System.err.println("Failed to load asset: " + path + " as " + type.getSimpleName());
            return null;
        }
    }

    private static boolean isAssetType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("com.pocket.rpg.rendering.") ||
               name.startsWith("com.pocket.rpg.ui.text.");
    }

    private static float getFloat(Map<?, ?> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        return defaultValue;
    }
}
