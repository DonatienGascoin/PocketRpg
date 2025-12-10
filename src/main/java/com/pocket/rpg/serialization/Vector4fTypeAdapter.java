package com.pocket.rpg.serialization;

import com.google.gson.*;
import org.joml.Vector4f;

import java.lang.reflect.Type;

/**
 * Gson TypeAdapter for JOML Vector4f serialization/deserialization.
 * Often used for colors (r, g, b, a).
 * 
 * JSON format: [x, y, z, w] (compact array format)
 * 
 * Alternative format supported for reading: {"x": 0, "y": 0, "z": 0, "w": 0}
 */
public class Vector4fTypeAdapter implements JsonSerializer<Vector4f>, JsonDeserializer<Vector4f> {

    @Override
    public JsonElement serialize(Vector4f vector, Type type, JsonSerializationContext context) {
        if (vector == null) {
            return JsonNull.INSTANCE;
        }
        
        // Use compact array format: [x, y, z, w]
        JsonArray array = new JsonArray();
        array.add(vector.x);
        array.add(vector.y);
        array.add(vector.z);
        array.add(vector.w);
        return array;
    }

    @Override
    public Vector4f deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) 
            throws JsonParseException {
        
        if (jsonElement.isJsonNull()) {
            return null;
        }
        
        // Support array format: [x, y, z, w]
        if (jsonElement.isJsonArray()) {
            JsonArray array = jsonElement.getAsJsonArray();
            if (array.size() != 4) {
                throw new JsonParseException("Vector4f array must have exactly 4 elements");
            }
            return new Vector4f(
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat(),
                array.get(3).getAsFloat()
            );
        }
        
        // Support object format: {x, y, z, w}
        if (jsonElement.isJsonObject()) {
            JsonObject obj = jsonElement.getAsJsonObject();
            return new Vector4f(
                obj.has("x") ? obj.get("x").getAsFloat() : 0,
                obj.has("y") ? obj.get("y").getAsFloat() : 0,
                obj.has("z") ? obj.get("z").getAsFloat() : 0,
                obj.has("w") ? obj.get("w").getAsFloat() : 1
            );
        }
        
        throw new JsonParseException("Vector4f must be an array [x,y,z,w] or object {x,y,z,w}");
    }
}
