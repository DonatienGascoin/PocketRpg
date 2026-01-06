package com.pocket.rpg.serialization.custom;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.TilemapRenderer.LedgeDirection;
import com.pocket.rpg.components.TilemapRenderer.Tile;
import com.pocket.rpg.components.TilemapRenderer.TileChunk;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.AssetContext;

import java.io.*;
import java.util.*;

/**
 * Optimized GSON Adapter for TilemapRenderer.
 * <p>
 * Uses a Palette-based Binary format encoded in Base64:
 * 1. Collects all unique Tile objects (The Palette).
 * 2. Writes the Palette (Tile definitions).
 * 3. Writes Chunks using IDs referencing the Palette (instead of full tile data).
 * <p>
 * Output Format: "Base64String"
 */
public class TilemapTypeAdapter extends TypeAdapter<TilemapRenderer> {

    private final AssetContext context;

    public TilemapTypeAdapter(AssetContext context) {
        this.context = context;
    }

    @Override
    public void write(JsonWriter out, TilemapRenderer value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        // 1. Build Palette (Identify unique tiles)
        List<Tile> palette = new ArrayList<>();
        Map<Tile, Integer> tileToIndex = new HashMap<>();

        for (TileChunk chunk : value.allChunks()) {
            for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                    Tile tile = chunk.get(x, y);
                    if (tile != null && !tileToIndex.containsKey(tile)) {
                        tileToIndex.put(tile, palette.size());
                        palette.add(tile);
                    }
                }
            }
        }

        // 2. Binary Serialize
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            // A. Write Config
            dos.writeFloat(value.getTileSize());
            dos.writeInt(value.getZIndex());

            // B. Write Palette
            dos.writeInt(palette.size());
            for (Tile tile : palette) {
                writeTileDefinition(dos, tile);
            }

            // C. Write Chunks
            Collection<TileChunk> chunks = value.allChunks();
            dos.writeInt(chunks.size());

            for (TileChunk chunk : chunks) {
                if (chunk.isEmpty()) continue;

                dos.writeInt(chunk.getChunkX());
                dos.writeInt(chunk.getChunkY());
                dos.writeInt(chunk.getTileCount());

                for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                        Tile tile = chunk.get(x, y);
                        if (tile != null) {
                            dos.writeByte(x); // Local X (0-31)
                            dos.writeByte(y); // Local Y (0-31)
                            dos.writeInt(tileToIndex.get(tile)); // Palette ID
                        }
                    }
                }
            }

            // Encode to Base64 String
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            out.value(base64);

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Tilemap", e);
        }
    }

    @Override
    public TilemapRenderer read(JsonReader in) throws IOException {
        String data = in.nextString();
        if (data == null || data.isEmpty()) return new TilemapRenderer();

        byte[] bytes = Base64.getDecoder().decode(data);

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {

            // A. Read Config
            float tileSize = dis.readFloat();
            int zIndex = dis.readInt();

            TilemapRenderer tilemap = new TilemapRenderer(tileSize);
            tilemap.setZIndex(zIndex);

            // B. Read Palette
            int paletteSize = dis.readInt();
            Tile[] palette = new Tile[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = readTileDefinition(dis);
            }

            // C. Read Chunks
            int chunkCount = dis.readInt();
            for (int i = 0; i < chunkCount; i++) {
                int cx = dis.readInt();
                int cy = dis.readInt();
                int tileCount = dis.readInt();

                // We don't need to manually create the chunk, .set() handles it
                // but for speed we could, let's stick to public API
                for (int j = 0; j < tileCount; j++) {
                    int tx = dis.readByte();
                    int ty = dis.readByte();
                    int paletteId = dis.readInt();

                    // Calculate world tile coordinates
                    int worldX = cx * TileChunk.CHUNK_SIZE + tx;
                    int worldY = cy * TileChunk.CHUNK_SIZE + ty;

                    if (paletteId >= 0 && paletteId < palette.length) {
                        tilemap.set(worldX, worldY, palette[paletteId]);
                    }
                }
            }

            return tilemap;

        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize Tilemap", e);
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void writeTileDefinition(DataOutputStream dos, Tile tile) throws IOException {
        dos.writeUTF(tile.name());

        // Handle Sprite (Only name is saved)
        Sprite sprite = tile.sprite();
        dos.writeUTF(sprite != null ? sprite.getName() : "");

        dos.writeBoolean(tile.solid());
        dos.writeByte(tile.ledgeDirection().ordinal());
    }

    private Tile readTileDefinition(DataInputStream dis) throws IOException {
        String name = dis.readUTF();
        String spriteName = dis.readUTF();
        boolean solid = dis.readBoolean();
        int ledgeOrdinal = dis.readByte();

        LedgeDirection dir = LedgeDirection.values()[ledgeOrdinal];
        Sprite sprite = resolveSprite(spriteName);

        // Reconstruct Record
        return new Tile(name, sprite, solid, dir);
    }

    /**
     * Resolves a Sprite object from its name.
     * <p>
     * TODO: Connect this to your AssetManager or SpriteRegistry.
     * Currently creates a placeholder or returns null if name is empty.
     */
    protected Sprite resolveSprite(String spriteName) {
        if (spriteName == null || spriteName.isEmpty()) return null;

        // return AssetManager.getSprite(spriteName);
        // For now, returning a temporary sprite or null as I don't have your AssetManager code
        return context.get(spriteName);
    }
}