package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.TilemapRenderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * TypeAdapterFactory that provides:
 * 1. Polymorphic serialization for all Components (type + properties wrapper)
 * 2. Special sparse serialization for TilemapRenderer
 * <p>
 * This replaces both ComponentSerializer/Deserializer and any TilemapRenderer-specific adapters.
 */
public class ComponentTypeAdapterFactory implements TypeAdapterFactory {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        // Only handle Component and its subclasses
        if (!Component.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                Component component = (Component) value;

                out.beginObject();

                // Write type field
                out.name(TYPE_FIELD);
                out.value(component.getClass().getCanonicalName());

                // Write properties field
                out.name(PROPERTIES_FIELD);

                // Special handling for TilemapRenderer
                if (component instanceof TilemapRenderer) {
                    writeTilemapRenderer(out, (TilemapRenderer) component, gson);
                } else {
                    // Standard serialization for other components
                    TypeAdapter<T> delegateAdapter = gson.getDelegateAdapter(
                            ComponentTypeAdapterFactory.this,
                            type
                    );
                    delegateAdapter.write(out, value);
                }

                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                in.beginObject();

                String componentType = null;
                JsonElement properties = null;

                while (in.hasNext()) {
                    String name = in.nextName();

                    if (TYPE_FIELD.equals(name)) {
                        componentType = in.nextString();
                    } else if (PROPERTIES_FIELD.equals(name)) {
                        properties = Streams.parse(in);
                    } else {
                        in.skipValue();
                    }
                }

                in.endObject();

                if (componentType == null) {
                    throw new JsonParseException("Component JSON missing 'type' field");
                }
                if (properties == null) {
                    throw new JsonParseException("Component JSON missing 'properties' field");
                }

                try {
                    Class<?> componentClass = Class.forName(componentType);

                    if (!Component.class.isAssignableFrom(componentClass)) {
                        throw new JsonParseException("Type '" + componentType + "' is not a Component");
                    }

                    // Special handling for TilemapRenderer
                    if (TilemapRenderer.class.isAssignableFrom(componentClass)) {
                        return (T) readTilemapRenderer(properties, gson);
                    } else {
                        // Standard deserialization for other components
                        return (T) gson.fromJson(properties, componentClass);
                    }

                } catch (ClassNotFoundException e) {
                    throw new JsonParseException("Unknown component type: " + componentType, e);
                }
            }
        };
    }

    /**
     * Writes TilemapRenderer with sparse chunk serialization.
     */
    private void writeTilemapRenderer(JsonWriter out, TilemapRenderer tilemap, Gson gson) throws IOException {
        out.beginObject();

        // Write basic properties
        out.name("zIndex").value(tilemap.getZIndex());
        out.name("tileSize").value(tilemap.getTileSize());

        // Write chunks sparsely
        out.name("chunks");
        out.beginObject();

        for (Long chunkKey : tilemap.chunkKeys()) {
            int cx = TilemapRenderer.chunkKeyToX(chunkKey);
            int cy = TilemapRenderer.chunkKeyToY(chunkKey);
            TilemapRenderer.TileChunk chunk = tilemap.getChunk(cx, cy);

            // Collect non-null tiles
            Map<String, TilemapRenderer.Tile> tiles = new HashMap<>();
            for (int tx = 0; tx < TilemapRenderer.TileChunk.CHUNK_SIZE; tx++) {
                for (int ty = 0; ty < TilemapRenderer.TileChunk.CHUNK_SIZE; ty++) {
                    TilemapRenderer.Tile tile = chunk.get(tx, ty);
                    if (tile != null && tile.sprite() != null) {
                        tiles.put(tx + "," + ty, tile);
                    }
                }
            }

            // Write chunk if it has tiles
            if (!tiles.isEmpty()) {
                out.name(cx + "," + cy);
                gson.toJson(tiles, new TypeToken<Map<String, TilemapRenderer.Tile>>() {
                }.getType(), out);
            }
        }

        out.endObject();
        out.endObject();
    }

    /**
     * Reads TilemapRenderer with sparse chunk deserialization.
     */
    private TilemapRenderer readTilemapRenderer(JsonElement properties, Gson gson) {
        JsonObject json = properties.getAsJsonObject();

        // Read basic properties
        float tileSize = json.has("tileSize") ? json.get("tileSize").getAsFloat() : 1.0f;
        int zIndex = json.has("zIndex") ? json.get("zIndex").getAsInt() : 0;
        boolean isStatic = json.has("isStatic") && json.get("isStatic").getAsBoolean();

        // Create tilemap
        TilemapRenderer tilemap = new TilemapRenderer(tileSize);
        tilemap.setZIndex(zIndex);

        // Read chunks
        if (json.has("chunks")) {
            JsonObject chunksJson = json.getAsJsonObject("chunks");

            TypeToken<Map<String, TilemapRenderer.Tile>> tileMapType =
                    new TypeToken<Map<String, TilemapRenderer.Tile>>() {
                    };

            for (var chunkEntry : chunksJson.entrySet()) {
                String[] coords = chunkEntry.getKey().split(",");
                int cx = Integer.parseInt(coords[0]);
                int cy = Integer.parseInt(coords[1]);

                Map<String, TilemapRenderer.Tile> tiles = gson.fromJson(
                        chunkEntry.getValue(),
                        tileMapType.getType()
                );

                for (Map.Entry<String, TilemapRenderer.Tile> tileEntry : tiles.entrySet()) {
                    String[] tileCoords = tileEntry.getKey().split(",");
                    int localX = Integer.parseInt(tileCoords[0]);
                    int localY = Integer.parseInt(tileCoords[1]);

                    int worldTx = cx * TilemapRenderer.TileChunk.CHUNK_SIZE + localX;
                    int worldTy = cy * TilemapRenderer.TileChunk.CHUNK_SIZE + localY;

                    tilemap.set(worldTx, worldTy, tileEntry.getValue());
                }
            }
        }

        return tilemap;
    }
}