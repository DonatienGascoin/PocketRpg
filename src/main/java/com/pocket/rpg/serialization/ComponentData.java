package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

                    // Update fields map with resolved asset (so editor shows correct name)
                    if (converted != value) {
                        fields.put(fieldMeta.name(), converted);
                    }

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
    // ASSET RESOLUTION
    // ========================================================================

    /**
     * Resolves asset references in the fields map.
     * Converts String paths to actual asset objects (Sprite, Texture, Font, etc.)
     * <p>
     * Call this after deserializing ComponentData to ensure the fields map
     * contains resolved asset objects instead of path strings.
     */
    public void resolveAssetReferences() {
        ComponentMeta meta = ComponentRegistry.getByClassName(type);
        if (meta == null) {
            return;
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            String fieldName = fieldMeta.name();
            Object value = fields.get(fieldName);

            if (value != null) {
                Object resolved = fromSerializable(value, fieldMeta.type());
                if (resolved != value) {
                    fields.put(fieldName, resolved);
                }
            }
        }
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

        if (value instanceof Sprite sprite) {
            // Use centralized path lookup (handles both direct sprites and spritesheet#index)
            String path = SpriteReference.toPath(sprite);
            if (path != null) {
                return path;
            }

            // Fallback: texture path for programmatically created sprites
            if (sprite.getTexture() != null) {
                return Assets.getRelativePath(sprite.getTexture().getFilePath());
            }
            return null;
        }
        if (value instanceof Texture texture) {
            return Map.of("_type", "Texture", "path", Assets.getRelativePath(texture.getFilePath()));
        }
        // Unknown - try toString
        return value.toString();
    }

    /**
     * Converts a serialized value back to the target type.
     */
    @SuppressWarnings("unchecked")
    public static Object fromSerializable(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // Handle Map objects (from Gson deserialization)
        if (value instanceof Map<?, ?> map) {
            // Custom asset types with _type marker
            if (map.containsKey("_type")) {
                String assetType = (String) map.get("_type");
                String path = (String) map.get("path");

                if ("Sprite".equals(assetType)) {
                    return Assets.load(path, Sprite.class);
                }
                if ("Texture".equals(assetType)) {
                    return Assets.load(path, Texture.class);
                }
                if ("Font".equals(assetType)) {
                    return Assets.load(path, com.pocket.rpg.ui.text.Font.class);
                }
            }

            // Handle Sprite serialized without _type marker (from full object serialization)
            if (targetType == Sprite.class && map.containsKey("texturePath")) {
                String path = (String) map.get("texturePath");
                return Assets.load(path, Sprite.class);
            }

            // Handle Texture serialized without _type marker
            if (targetType == Texture.class && map.containsKey("filePath")) {
                String path = (String) map.get("filePath");
                return Assets.load(path, Texture.class);
            }

            // Handle Font serialized without _type marker
            if (targetType == com.pocket.rpg.ui.text.Font.class && map.containsKey("path")) {
                String path = (String) map.get("path");
                return Assets.load(path, com.pocket.rpg.ui.text.Font.class);
            }

            // Convert Map to List for Vector types
            if (targetType == Vector2f.class || targetType == Vector3f.class || targetType == Vector4f.class) {
                if (!map.isEmpty()) {
                    value = new ArrayList<>(map.values());
                }
            }
        }

        // Handle String → Asset types (Sprite, Texture, Font)
        if (value instanceof String path && !path.isEmpty()) {
            if (targetType == Sprite.class) {
                // SpriteReference.fromPath handles both direct and #index format
                return SpriteReference.fromPath(path);
            }
            if (targetType == Texture.class) {
                return Assets.load(path, Texture.class);
            }
            if (targetType == com.pocket.rpg.ui.text.Font.class) {
                return Assets.load(path, com.pocket.rpg.ui.text.Font.class);
            }
        }

        // Handle arrays and lists → Vectors
        Object listValue = value;
        if (value.getClass().isArray()) {
            if (value instanceof float[] arr) {
                listValue = new ArrayList<>();
                for (float f : arr) ((List<Object>)listValue).add(f);
            } else if (value instanceof double[] arr) {
                listValue = new ArrayList<>();
                for (double d : arr) ((List<Object>)listValue).add(d);
            }
        }

        if (listValue instanceof List<?> list) {
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

        // Handle number conversions
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
            if (targetType == boolean.class || targetType == Boolean.class) {
                return n.intValue() != 0;
            }
        }

        return value;
    }
}