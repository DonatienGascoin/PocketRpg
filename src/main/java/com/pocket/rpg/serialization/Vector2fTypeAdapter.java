package com.pocket.rpg.serialization;

import com.google.gson.*;
import org.joml.Vector2f;

import java.lang.reflect.Type;

/**
 * Gson TypeAdapter for JOML Vector2f serialization/deserialization.
 * 
 * JSON format: [x, y] (compact array format)
 * 
 * Alternative format supported for reading: {"x": 0, "y": 0}
 */
public class Vector2fTypeAdapter implements JsonSerializer<Vector2f>, JsonDeserializer<Vector2f> {

    @Override
    public JsonElement serialize(Vector2f vector, Type type, JsonSerializationContext context) {
        if (vector == null) {
            return JsonNull.INSTANCE;
        }
        
        // Use compact array format: [x, y]
        JsonArray array = new JsonArray();
        array.add(vector.x);
        array.add(vector.y);
        return array;
    }

    @Override
    public Vector2f deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) 
            throws JsonParseException {
        
        if (jsonElement.isJsonNull()) {
            return null;
        }
        
        // Support array format: [x, y]
        if (jsonElement.isJsonArray()) {
            JsonArray array = jsonElement.getAsJsonArray();
            if (array.size() != 2) {
                throw new JsonParseException("Vector2f array must have exactly 2 elements");
            }
            return new Vector2f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat()
            );
        }
        
        // Support object format: {x, y}
        if (jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            return new Vector2f(
                obj.has("x") ? obj.get("x").getAsFloat() : 0,
                obj.has("y") ? obj.get("y").getAsFloat() : 0
            );
        }
        
        throw new JsonParseException("Vector2f must be an array [x,y] or object {x,y}");
    }
}
