package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetContext;

import java.lang.reflect.Type;

/**
 * Gson TypeAdapter for Texture serialization/deserialization.
 * <p>
 * Serializes only the file path. On deserialization, loads the texture
 * via AssetManager (ensuring proper caching).
 * <p>
 * JSON format: "gameData/assets/sprites/player.png"
 * (simple string, not an object)
 */
public class TextureTypeAdapter implements JsonSerializer<Texture>, JsonDeserializer<Texture> {

    private final AssetContext assetManager;

    /**
     * Creates adapter with AssetManager for texture loading.
     *
     * @param assetManager AssetManager instance (can be null for serialization-only)
     */
    public TextureTypeAdapter(AssetContext assetManager) {
        this.assetManager = assetManager;
    }

    @Override
    public JsonElement serialize(Texture texture, Type type, JsonSerializationContext context) {
        if (texture == null) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(texture.getFilePath());
    }

    @Override
    public Texture deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
            throws JsonParseException {

        if (jsonElement.isJsonNull()) {
            return null;
        }

        String filePath = jsonElement.getAsString();

        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        if (assetManager != null) {
            return assetManager.load(filePath);
        } else {
            // Fallback: create texture directly (may not be cached)
            return new Texture(filePath);
        }
    }
}
