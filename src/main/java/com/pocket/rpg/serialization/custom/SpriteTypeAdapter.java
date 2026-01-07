package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.Assets;

import java.io.IOException;

/**
 * Unified TypeAdapter for Sprite serialization.
 * Handles String references ("path/to/sprite.png"), SpriteSheet references ("path#index"),
 * and full Object definitions for custom sprites.
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

        // 1. Try to serialize as a Reference String (Path or Sheet index)
        String path = sprite.getSourcePath();
        if (path == null) {
            path = Assets.getPathForResource(sprite);
        }

        if (path != null) {
            if (sprite.getSpriteIndex() != null) {
                out.value(path + "#" + sprite.getSpriteIndex());
            } else {
                out.value(path);
            }
            return;
        }

        // 2. Fallback: Serialize as a Full Object
        out.beginObject();
        out.name("name").value(sprite.getName());

        if (sprite.getTexture() != null && context != null) {
            out.name("texturePath").value(context.getRelativePath(sprite.getTexture().getFilePath()));
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

        // Handle String Reference ("assets/player.png" or "assets/sheet.png#5")
        if (token == JsonToken.STRING) {
            String value = in.nextString();
            int hashIndex = value.indexOf('#');

            if (hashIndex != -1) {
                String sheetPath = value.substring(0, hashIndex);
                int spriteIndex = Integer.parseInt(value.substring(hashIndex + 1));
                SpriteSheet sheet = Assets.load(sheetPath, SpriteSheet.class);
                return sheet.getSprite(spriteIndex);
            }

            return Assets.load(value, Sprite.class);
        }

        // Handle Object Definition
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