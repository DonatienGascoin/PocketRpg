package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetContext;

import java.lang.reflect.Type;

/**
 * Gson TypeAdapter for Sprite serialization/deserialization.
 * <p>
 * Serializes sprite data including texture path (not the texture itself),
 * dimensions, UV coordinates, pivot, and name.
 * <p>
 * On deserialization, resolves texture via AssetManager.
 * <p>
 * JSON format:
 * {
 * "name": "player_idle_0",
 * "texturePath": "gameData/assets/sprites/player.png",
 * "width": 16.0,
 * "height": 16.0,
 * "u0": 0.0, "v0": 0.0, "u1": 0.0625, "v1": 0.0625,
 * "pivotX": 0.5, "pivotY": 0.0,
 * "pixelsPerUnitOverride": null
 * }
 */
public class SpriteTypeAdapter implements JsonSerializer<Sprite>, JsonDeserializer<Sprite> {

    private final AssetContext assetManager;

    /**
     * Creates adapter with AssetManager for texture resolution.
     *
     * @param assetManager AssetManager instance (can be null for serialization-only)
     */
    public SpriteTypeAdapter(AssetContext assetManager) {
        this.assetManager = assetManager;
    }

    @Override
    public JsonElement serialize(Sprite sprite, Type type, JsonSerializationContext context) {
        if (sprite == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject json = new JsonObject();

        json.addProperty("name", sprite.getName());

        // Store texture path, not the texture object
        if (sprite.getTexture() != null) {
            json.addProperty("texturePath", assetManager.getRelativePath(sprite.getTexture().getFilePath()));
        }

        json.addProperty("width", sprite.getWidth());
        json.addProperty("height", sprite.getHeight());

        // UV coordinates
        json.addProperty("u0", sprite.getU0());
        json.addProperty("v0", sprite.getV0());
        json.addProperty("u1", sprite.getU1());
        json.addProperty("v1", sprite.getV1());

        // Pivot
        json.addProperty("pivotX", sprite.getPivotX());
        json.addProperty("pivotY", sprite.getPivotY());

        // Optional PPU override
        if (sprite.getPixelsPerUnitOverride() != null) {
            json.addProperty("pixelsPerUnitOverride", sprite.getPixelsPerUnitOverride());
        }

        return json;
    }

    @Override
    public Sprite deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context)
            throws JsonParseException {

        if (jsonElement.isJsonNull()) {
            return null;
        }

        JsonObject json = jsonElement.getAsJsonObject();

        // Load texture via AssetManager
        Texture texture = null;
        if (json.has("texturePath") && !json.get("texturePath").isJsonNull()) {
            String texturePath = json.get("texturePath").getAsString();

            if (assetManager != null) {
                texture = assetManager.load(texturePath);
            } else {
                // Fallback: create texture directly (may not be cached)
                texture = new Texture(texturePath);
            }
        }

        // Create sprite with texture and dimensions
        float width = json.has("width") ? json.get("width").getAsFloat() :
                (texture != null ? texture.getWidth() : 16);
        float height = json.has("height") ? json.get("height").getAsFloat() :
                (texture != null ? texture.getHeight() : 16);

        Sprite sprite = new Sprite(texture, width, height);

        // Set name
        if (json.has("name")) {
            sprite.setName(json.get("name").getAsString());
        }

        // Set UV coordinates
        if (json.has("u0") && json.has("v0") && json.has("u1") && json.has("v1")) {
            sprite.setUVs(
                    json.get("u0").getAsFloat(),
                    json.get("v0").getAsFloat(),
                    json.get("u1").getAsFloat(),
                    json.get("v1").getAsFloat()
            );
        }

        // Set pivot
        if (json.has("pivotX") && json.has("pivotY")) {
            sprite.setPivot(
                    json.get("pivotX").getAsFloat(),
                    json.get("pivotY").getAsFloat()
            );
        }

        // Set PPU override
        if (json.has("pixelsPerUnitOverride") && !json.get("pixelsPerUnitOverride").isJsonNull()) {
            sprite.setPixelsPerUnitOverride(json.get("pixelsPerUnitOverride").getAsFloat());
        }

        return sprite;
    }
}
