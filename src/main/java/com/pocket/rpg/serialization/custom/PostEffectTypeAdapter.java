package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.pocket.rpg.postProcessing.PostEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Reflection-based Gson TypeAdapter for PostEffect interface.
 * <p>
 * Automatically serializes/deserializes all PostEffect implementations without
 * hardcoded instanceof checks or manual registration.
 * <p>
 * Features:
 * - Automatic field discovery and serialization
 * - Smart constructor matching
 * - Handles primitives, wrappers, and strings
 * - Proper error handling and logging
 * <p>
 * Requirements for PostEffect implementations:
 * 1. All parameters should be private/protected fields
 * 2. Must have a constructor that takes all parameters
 * 3. Constructor parameters should match field names (case-insensitive)
 * 4. Supported types: float, int, double, boolean, String (and their wrappers)
 * <p>
 * Example:
 * public class VignetteEffect implements PostEffect {
 * private float intensity;
 * private float radius;
 * <p>
 * public VignetteEffect(float intensity, float radius) {
 * this.intensity = intensity;
 * this.radius = radius;
 * }
 * }
 */
public class PostEffectTypeAdapter implements JsonSerializer<PostEffect>, JsonDeserializer<PostEffect> {

    private static final String PACKAGE_PREFIX = "com.pocket.rpg.postProcessing.postEffects.";

    @Override
    public JsonElement serialize(PostEffect effect, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();

        // Store the simple class name
        String className = effect.getClass().getSimpleName();
        json.addProperty("type", className);

        // Serialize all instance fields
        JsonObject params = new JsonObject();
        serializeFields(effect, params);

        json.add("params", params);
        return json;
    }

    @Override
    public PostEffect deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();
        String type = obj.get("type").getAsString();
        JsonObject params = obj.getAsJsonObject("params");

        try {
            // Get the full class name
            String fullClassName = PACKAGE_PREFIX + type;
            Class<?> effectClass = Class.forName(fullClassName);

            // Verify it's a PostEffect
            if (!PostEffect.class.isAssignableFrom(effectClass)) {
                throw new JsonParseException(type + " does not implement PostEffect");
            }

            // Instantiate using reflection
            return instantiateEffect(effectClass, params);

        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown effect type: " + type +
                    ". Make sure the class exists in " + PACKAGE_PREFIX, e);
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize effect: " + type +
                    ". Error: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes all non-static, non-transient fields of an effect.
     */
    private void serializeFields(PostEffect effect, JsonObject params) {
        Class<?> clazz = effect.getClass();

        // Get all fields including inherited ones
        List<Field> allFields = getAllFields(clazz);

        for (Field field : allFields) {
            // Skip static, transient, and synthetic fields
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) ||
                    Modifier.isTransient(modifiers) ||
                    field.isSynthetic()) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object value = field.get(effect);

                if (value == null) {
                    continue; // Skip null values
                }

                // Serialize based on type
                String fieldName = field.getName();
                addToJson(params, fieldName, value);

            } catch (IllegalAccessException e) {
                System.err.println("Warning: Failed to serialize field '" +
                        field.getName() + "' in " + clazz.getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Adds a value to JSON based on its type.
     */
    private void addToJson(JsonObject params, String name, Object value) {
        switch (value) {
            case Number number -> params.addProperty(name, number);
            case Boolean b -> params.addProperty(name, b);
            case String s -> params.addProperty(name, s);
            case Character c -> params.addProperty(name, c);
            default -> {
                // For unknown types, try toString()
                System.err.println("Warning: Unsupported field type " +
                        value.getClass().getSimpleName() + " for field '" + name +
                        "'. Using toString().");
                params.addProperty(name, value.toString());
            }
        }
    }

    /**
     * Gets all fields from a class including inherited fields.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        // Walk up the class hierarchy
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * Instantiates an effect by finding and invoking the best matching constructor.
     */
    private PostEffect instantiateEffect(Class<?> effectClass, JsonObject params)
            throws Exception {

        Constructor<?>[] constructors = effectClass.getConstructors();

        if (constructors.length == 0) {
            throw new IllegalArgumentException(
                    effectClass.getSimpleName() + " has no public constructors"
            );
        }

        // Try constructors in order of parameter count (most parameters first)
        Arrays.sort(constructors, (a, b) ->
                Integer.compare(b.getParameterCount(), a.getParameterCount())
        );

        Exception lastException = null;

        for (Constructor<?> constructor : constructors) {
            try {
                Object[] args = buildConstructorArgs(constructor, params, effectClass);
                return (PostEffect) constructor.newInstance(args);
            } catch (Exception e) {
                lastException = e;
                // Try next constructor
            }
        }

        // If we get here, no constructor worked
        throw new IllegalArgumentException(
                "Could not find suitable constructor for " + effectClass.getSimpleName() +
                        ". Available params: " + params.keySet() +
                        ". Last error: " + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException
        );
    }

    /**
     * Builds constructor arguments by matching parameter types with JSON values.
     */
    private Object[] buildConstructorArgs(Constructor<?> constructor, JsonObject params,
                                          Class<?> effectClass) throws Exception {

        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        // Get field names from the class to match with constructor parameters
        List<Field> fields = getAllFields(effectClass);
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fieldMap.put(field.getName().toLowerCase(), field);
            }
        }

        // Try to match constructor parameters with JSON params
        // Assumption: constructor parameters are in the same order as fields appear in JSON
        int paramIndex = 0;
        for (Map.Entry<String, JsonElement> entry : params.entrySet()) {
            if (paramIndex >= paramTypes.length) {
                break; // More JSON params than constructor params
            }

            String paramName = entry.getKey();
            JsonElement value = entry.getValue();
            Class<?> targetType = paramTypes[paramIndex];

            // Try to convert the JSON value to the target type
            try {
                args[paramIndex] = convertJsonToType(value, targetType);
                paramIndex++;
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to convert parameter '" + paramName + "' to type " +
                                targetType.getSimpleName() + " for constructor of " +
                                effectClass.getSimpleName(), e
                );
            }
        }

        // Check if we have enough arguments
        if (paramIndex < paramTypes.length) {
            throw new IllegalArgumentException(
                    "Not enough parameters for constructor of " + effectClass.getSimpleName() +
                            ". Expected " + paramTypes.length + ", got " + paramIndex
            );
        }

        return args;
    }

    /**
     * Converts a JSON element to the target type.
     */
    private Object convertJsonToType(JsonElement element, Class<?> targetType) {
        if (element.isJsonNull()) {
            return null;
        }

        // Handle primitives and their wrappers
        if (targetType == float.class || targetType == Float.class) {
            return element.getAsFloat();
        } else if (targetType == int.class || targetType == Integer.class) {
            return element.getAsInt();
        } else if (targetType == double.class || targetType == Double.class) {
            return element.getAsDouble();
        } else if (targetType == long.class || targetType == Long.class) {
            return element.getAsLong();
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return element.getAsBoolean();
        } else if (targetType == byte.class || targetType == Byte.class) {
            return element.getAsByte();
        } else if (targetType == short.class || targetType == Short.class) {
            return element.getAsShort();
        } else if (targetType == char.class || targetType == Character.class) {
            return element.getAsCharacter();
        } else if (targetType == String.class) {
            return element.getAsString();
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: " + targetType.getName() +
                        ". Only primitives, wrappers, and String are supported."
        );
    }
}