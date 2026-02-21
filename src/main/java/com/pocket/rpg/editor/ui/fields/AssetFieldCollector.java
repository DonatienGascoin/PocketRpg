package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.serialization.FieldMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers editable fields on any POJO via reflection.
 * Similar to ComponentRegistry.collectFields() but without Component-specific exclusions.
 * <p>
 * Results are cached per class since fields don't change at runtime.
 */
public final class AssetFieldCollector {

    private static final Map<Class<?>, List<FieldMeta>> cache = new ConcurrentHashMap<>();

    private AssetFieldCollector() {}

    /**
     * Returns the editable fields for the given class, cached.
     *
     * @param clazz The class to inspect
     * @return List of FieldMeta describing each editable field
     */
    public static List<FieldMeta> getFields(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, AssetFieldCollector::collectFields);
    }

    private static List<FieldMeta> collectFields(Class<?> clazz) {
        List<FieldMeta> fields = new ArrayList<>();
        collectFieldsRecursive(clazz, fields);
        return List.copyOf(fields);
    }

    private static void collectFieldsRecursive(Class<?> clazz, List<FieldMeta> fields) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        // Walk superclass first so parent fields appear before subclass fields
        collectFieldsRecursive(clazz.getSuperclass(), fields);

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(HideInInspector.class)) {
                continue;
            }

            field.setAccessible(true);

            Class<?> type = field.getType();
            Object defaultValue = getDefaultValue(type);

            // Extract generic element type for List fields
            Class<?> elementType = null;
            if (List.class.isAssignableFrom(type)) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> c) {
                        elementType = c;
                    }
                }
            }

            fields.add(new FieldMeta(field.getName(), type, field, defaultValue, elementType));
        }
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == double.class) return 0.0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
