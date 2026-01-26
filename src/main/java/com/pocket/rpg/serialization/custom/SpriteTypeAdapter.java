package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.Texture;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;

import java.io.IOException;

/**
 * Unified TypeAdapter for Sprite serialization.
 * <p>
 * Handles multiple formats:
 * <ul>
 *   <li>String references - "path/to/sprite.png" or "path/to/sheet.png#3" (new format)</li>
 *   <li>Legacy references - "path/to/sheet.spritesheet#3" (auto-converted to new format)</li>
 *   <li>Object definitions - for programmatic sprites without asset paths</li>
 * </ul>
 * <p>
 * Path resolution is centralized through {@link Assets#getPathForResource(Object)}
 * and {@link Assets#load(String, Class)}. The #index format for sprite grid sprites
 * is handled automatically by AssetManager.
 * <p>
 * <b>Migration Support:</b> Old .spritesheet paths are automatically converted to the
 * new .png format via {@link SpriteReference#migratePathIfNeeded(String)}. When saving,
 * sprites are always written in the new format.
 */
public class SpriteTypeAdapter extends TypeAdapter<Sprite> {

    private final AssetContext context;

    public SpriteTypeAdapter(AssetContext context) {
        this.context = context;
    }

    @Override
    public void write(JsonWriter out, Sprite sprite) throws IOException {
        if (sprite == null) {
            out.nullValue();
            return;
        }

        // Single source of truth - SpriteReference uses resourcePaths map
        // Path already includes #index for spritesheet sprites
        String path = SpriteReference.toPath(sprite);

        if (path != null) {
            out.value(path);
            return;
        }

        // Fallback: Serialize as full object (programmatic sprites)
        out.beginObject();
        out.name("name").value(sprite.getName());

        if (sprite.getTexture() != null && context != null) {
            String texturePath = context.getPathForResource(sprite.getTexture());
            if (texturePath == null) {
                texturePath = context.getRelativePath(sprite.getTexture().getFilePath());
            }
            out.name("texturePath").value(texturePath);
        }

        out.name("width").value(sprite.getWidth());
        out.name("height").value(sprite.getHeight());
        out.name("u0").value(sprite.getU0());
        out.name("v0").value(sprite.getV0());
        out.name("u1").value(sprite.getU1());
        out.name("v1").value(sprite.getV1());
        out.name("pivotX").value(sprite.getPivotX());
        out.name("pivotY").value(sprite.getPivotY());

        if (sprite.getPixelsPerUnitOverride() != null) {
            out.name("pixelsPerUnitOverride").value(sprite.getPixelsPerUnitOverride());
        }

        out.endObject();
    }

    @Override
    public Sprite read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        // String reference - SpriteReference.fromPath() handles #index parsing automatically
        if (token == JsonToken.STRING) {
            String value = in.nextString();
            return SpriteReference.fromPath(value);
        }

        // Object definition - for programmatic sprites
        if (token == JsonToken.BEGIN_OBJECT) {
            return deserializeObject(in);
        }

        throw new IOException("Expected String or Object for Sprite, but found " + token);
    }

    private Sprite deserializeObject(JsonReader in) throws IOException {
        in.beginObject();

        String name = null;
        String texturePath = null;
        float width = 16, height = 16;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        float pivotX = 0.5f, pivotY = 0.5f;
        Float ppuOverride = null;

        while (in.hasNext()) {
            String key = in.nextName();
            switch (key) {
                case "name": name = in.nextString(); break;
                case "texturePath": texturePath = in.nextString(); break;
                case "width": width = (float) in.nextDouble(); break;
                case "height": height = (float) in.nextDouble(); break;
                case "u0": u0 = (float) in.nextDouble(); break;
                case "v0": v0 = (float) in.nextDouble(); break;
                case "u1": u1 = (float) in.nextDouble(); break;
                case "v1": v1 = (float) in.nextDouble(); break;
                case "pivotX": pivotX = (float) in.nextDouble(); break;
                case "pivotY": pivotY = (float) in.nextDouble(); break;
                case "pixelsPerUnitOverride":
                    ppuOverride = (float) in.nextDouble(); break;
                default: in.skipValue(); break;
            }
        }
        in.endObject();

        Texture texture = null;
        if (texturePath != null) {
            texture = (context != null) ? context.load(texturePath) : new Texture(texturePath);
        }

        Sprite sprite = new Sprite(texture, width, height);
        sprite.setName(name);
        sprite.setUVs(u0, v0, u1, v1);
        sprite.setPivot(pivotX, pivotY);
        if (ppuOverride != null) sprite.setPixelsPerUnitOverride(ppuOverride);

        return sprite;
    }
}
