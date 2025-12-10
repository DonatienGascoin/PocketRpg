package com.pocket.rpg.serialization;

import com.google.gson.*;
import com.pocket.rpg.components.Component;

import java.lang.reflect.Type;

/**
 * Gson deserializer for Component polymorphism.
 * Reads type information and deserializes to the correct concrete class.
 * 
 * Expected input format:
 * {
 *   "type": "com.pocket.rpg.components.SpriteRenderer",
 *   "properties": { ... }
 * }
 * 
 * Components must have a no-arg constructor (can be private).
 * Asset references (Sprite, Texture) are resolved via their respective TypeAdapters.
 */
public class ComponentDeserializer implements JsonDeserializer<Component> {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";

    @Override
    public Component deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) 
            throws JsonParseException {
        
        JsonObject json = jsonElement.getAsJsonObject();
        
        // Get the concrete type name
        if (!json.has(TYPE_FIELD)) {
            throw new JsonParseException("Component JSON missing 'type' field");
        }
        String componentType = json.get(TYPE_FIELD).getAsString();
        
        // Get the properties element
        if (!json.has(PROPERTIES_FIELD)) {
            throw new JsonParseException("Component JSON missing 'properties' field");
        }
        JsonElement properties = json.get(PROPERTIES_FIELD);

        try {
            // Load the class and deserialize
            Class<?> componentClass = Class.forName(componentType);
            
            // Verify it's actually a Component
            if (!Component.class.isAssignableFrom(componentClass)) {
                throw new JsonParseException("Type '" + componentType + "' is not a Component");
            }
            
            return context.deserialize(properties, componentClass);
            
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown component type: " + componentType, e);
        }
    }
}
