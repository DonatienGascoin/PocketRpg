package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.TilemapRenderer.LedgeDirection;
import com.pocket.rpg.components.TilemapRenderer.Tile;
import com.pocket.rpg.components.TilemapRenderer.TileChunk;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.SpriteReference;

import java.io.*;
import java.util.*;

public class ComponentTypeAdapterFactory implements TypeAdapterFactory {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";
    private final AssetContext context;

    public ComponentTypeAdapterFactory(AssetContext context) {
        this.context = context;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Component.class.isAssignableFrom(type.getRawType())) return null;

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) { out.nullValue(); return; }
                Component component = (Component) value;

                out.beginObject();
                out.name(TYPE_FIELD).value(component.getClass().getCanonicalName());
                out.name(PROPERTIES_FIELD);

                if (component instanceof TilemapRenderer) {
                    writeTilemapBinary(out, (TilemapRenderer) component);
                } else {
                    TypeAdapter<T> delegate = gson.getDelegateAdapter(ComponentTypeAdapterFactory.this, type);
                    delegate.write(out, value);
                }
                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return null; }

                in.beginObject();
                String componentType = null;
                JsonElement properties = null;

                while (in.hasNext()) {
                    String name = in.nextName();
                    if (TYPE_FIELD.equals(name)) componentType = in.nextString();
                    else if (PROPERTIES_FIELD.equals(name)) properties = Streams.parse(in);
                    else in.skipValue();
                }
                in.endObject();

                try {
                    Class<?> clazz = Class.forName(componentType);
                    if (TilemapRenderer.class.isAssignableFrom(clazz)) {
                        // MIGRATION CHECK: Is it a String (Binary) or Object (Old Map)?
                        if (properties.isJsonPrimitive() && properties.getAsJsonPrimitive().isString()) {
                            return (T) readTilemapBinary(properties.getAsString());
                        } else {
                            return (T) readLegacyTilemap(properties, gson);
                        }
                    } else {
                        return (T) gson.fromJson(properties, (java.lang.reflect.Type) clazz);
                    }
                } catch (Exception e) {
                    throw new JsonParseException(e);
                }
            }
        };
    }

    private void writeTilemapBinary(JsonWriter out, TilemapRenderer tilemap) throws IOException {
        List<Tile> palette = new ArrayList<>();
        Map<Tile, Integer> tileToIndex = new HashMap<>();

        // Collect unique tiles
        for (TileChunk chunk : tilemap.allChunks()) {
            for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                    Tile t = chunk.get(x, y);
                    if (t != null && !tileToIndex.containsKey(t)) {
                        tileToIndex.put(t, palette.size());
                        palette.add(t);
                    }
                }
            }
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeFloat(tilemap.getTileSize());
            dos.writeInt(tilemap.getZIndex());

            // Palette
            dos.writeInt(palette.size());
            for (Tile t : palette) {
                dos.writeUTF(t.name() != null ? t.name() : "");
                dos.writeUTF(serializeSpriteRef(t.sprite()));
                dos.writeBoolean(t.solid());
                dos.writeByte(t.ledgeDirection().ordinal());
            }

            // Chunks
            Collection<TileChunk> chunks = tilemap.allChunks();
            dos.writeInt(chunks.size());
            for (TileChunk chunk : chunks) {
                dos.writeInt(chunk.getChunkX());
                dos.writeInt(chunk.getChunkY());
                dos.writeInt(chunk.getTileCount());

                for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                        Tile t = chunk.get(x, y);
                        if (t != null) {
                            dos.writeByte(x);
                            dos.writeByte(y);
                            dos.writeInt(tileToIndex.get(t)); // ID in palette
                        }
                    }
                }
            }
            out.value(Base64.getEncoder().encodeToString(baos.toByteArray()));
        }
    }

    private TilemapRenderer readTilemapBinary(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            float size = dis.readFloat();
            int z = dis.readInt();
            TilemapRenderer tilemap = new TilemapRenderer(size);
            tilemap.setZIndex(z);

            // Read Palette
            int palSize = dis.readInt();
            Tile[] palette = new Tile[palSize];
            for (int i = 0; i < palSize; i++) {
                String name = dis.readUTF();
                String spriteName = dis.readUTF();
                boolean solid = dis.readBoolean();
                LedgeDirection ledge = LedgeDirection.values()[dis.readByte()];
                palette[i] = new Tile(name, resolveSpriteRef(spriteName), solid, ledge);
            }

            // Read Chunks
            int chunkCount = dis.readInt();
            for (int i = 0; i < chunkCount; i++) {
                int cx = dis.readInt();
                int cy = dis.readInt();
                int tCount = dis.readInt();
                for (int j = 0; j < tCount; j++) {
                    int tx = dis.readByte();
                    int ty = dis.readByte();
                    int palIdx = dis.readInt();
                    tilemap.set(cx * TileChunk.CHUNK_SIZE + tx, cy * TileChunk.CHUNK_SIZE + ty, palette[palIdx]);
                }
            }
            return tilemap;
        }
    }

    private TilemapRenderer readLegacyTilemap(JsonElement props, Gson gson) {
        JsonObject json = props.getAsJsonObject();
        TilemapRenderer tm = new TilemapRenderer(json.has("tileSize") ? json.get("tileSize").getAsFloat() : 1.0f);
        tm.setZIndex(json.has("zIndex") ? json.get("zIndex").getAsInt() : 0);

        if (json.has("chunks")) {
            JsonObject chunks = json.getAsJsonObject("chunks");
            java.lang.reflect.Type type = new TypeToken<Map<String, Tile>>(){}.getType();
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet()) {
                String[] c = entry.getKey().split(",");
                int cx = Integer.parseInt(c[0]), cy = Integer.parseInt(c[1]);
                Map<String, Tile> tiles = gson.fromJson(entry.getValue(), type);
                tiles.forEach((pos, tile) -> {
                    String[] p = pos.split(",");
                    tm.set(cx * 32 + Integer.parseInt(p[0]), cy * 32 + Integer.parseInt(p[1]), tile);
                });
            }
        }
        return tm;
    }

    /**
     * Serializes a sprite reference using the centralized SpriteReference utility.
     * Returns the full path including #index for spritesheet sprites.
     */
    private String serializeSpriteRef(Sprite sprite) {
        if (sprite == null) return "";
        String path = SpriteReference.toPath(sprite);
        return path != null ? path : "";
    }

    /**
     * Resolves a sprite reference using SpriteReference utility.
     * Handles both direct paths and spritesheet#index format.
     */
    private Sprite resolveSpriteRef(String ref) {
        return SpriteReference.fromPath(ref);
    }
}
