package com.pocket.rpg.serialization;

import com.google.gson.*;
import com.pocket.rpg.components.Component;

import java.lang.reflect.Type;

/**
 * Gson serializer for Component polymorphism.
 * Wraps component data with type information for deserialization.
 * <p>
 * Output format:
 * {
 * "type": "com.pocket.rpg.components.SpriteRenderer",
 * "properties": { ... all serializable fields ... }
 * }
 * <p>
 * Fields marked as transient are excluded from serialization.
 * Components must have a no-arg constructor for deserialization.
 */
public class ComponentSerializer implements JsonSerializer<Component> {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";

    @Override
    public JsonElement serialize(Component component, Type type, JsonSerializationContext context) {
        JsonObject result = new JsonObject();

        // Store the concrete class name for deserialization
        result.add(TYPE_FIELD, new JsonPrimitive(component.getClass().getCanonicalName()));

        // Serialize the component using its concrete type (Gson handles transient fields)
        result.add(PROPERTIES_FIELD, context.serialize(component, component.getClass()));

        return result;
    }
}
