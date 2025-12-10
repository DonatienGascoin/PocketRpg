package com.pocket.rpg.serialization;

import com.google.gson.*;
import org.joml.Vector3f;

import java.lang.reflect.Type;

/**
 * Gson TypeAdapter for JOML Vector3f serialization/deserialization.
 * 
 * JSON format: [x, y, z] (compact array format)
 * 
 * Alternative format supported for reading: {"x": 0, "y": 0, "z": 0}
 */
public class Vector3fTypeAdapter implements JsonSerializer<Vector3f>, JsonDeserializer<Vector3f> {

    @Override
    public JsonElement serialize(Vector3f vector, Type type, JsonSerializationContext context) {
        if (vector == null) {
            return JsonNull.INSTANCE;
        }
        
        // Use compact array format: [x, y, z]
        JsonArray array = new JsonArray();
        array.add(vector.x);
        array.add(vector.y);
        array.add(vector.z);
        return array;
    }

    @Override
    public Vector3f deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) 
            throws JsonParseException {
        
        if (jsonElement.isJsonNull()) {
            return null;
        }
        
        // Support array format: [x, y, z]
        if (jsonElement.isJsonArray()) {
            JsonArray array = jsonElement.getAsJsonArray();
            if (array.size() != 3) {
                throw new JsonParseException("Vector3f array must have exactly 3 elements");
            }
            return new Vector3f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
            );
        }
        
        // Support object format: {x, y, z}
        if (jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            return new Vector3f(
                obj.has("x") ? obj.get("x").getAsFloat() : 0,
                obj.has("y") ? obj.get("y").getAsFloat() : 0,
                obj.has("z") ? obj.get("z").getAsFloat() : 0
            );
        }
        
        throw new JsonParseException("Vector3f must be an array [x,y,z] or object {x,y,z}");
    }
}
