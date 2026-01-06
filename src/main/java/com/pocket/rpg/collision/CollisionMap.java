package com.pocket.rpg.collision;

import lombok.Getter;

import java.io.*;
import java.util.*;

/**
 * Chunk-based collision map with Z-level support.
 * <p>
 * Stores CollisionType per (tileX, tileY, z-level) using chunk system.
 * Z-levels allow multi-layer collision (bridges over water, etc.)
 * <p>
 * Coordinate System:
 * - Integer tile coordinates (tileX, tileY, z)
 * - Z-level 0 is ground level
 * - Higher Z = higher elevation (bridges, second floor, etc.)
 * - Negative Z = underground/underwater
 */
public class CollisionMap {

    // Map of z-level -> chunk storage for that level
    private final Map<Integer, Map<Long, CollisionChunk>> zLayers = new HashMap<>();

    /**
     * Gets the collision type at a tile position on a specific Z-level.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param z     Z-level
     * @return CollisionType at that position (NONE if not set)
     */
    public CollisionType get(int tileX, int tileY, int z) {
        Map<Long, CollisionChunk> layer = zLayers.get(z);
        if (layer == null) return CollisionType.NONE;

        int cx = floorDiv(tileX, CollisionChunk.CHUNK_SIZE);
        int cy = floorDiv(tileY, CollisionChunk.CHUNK_SIZE);

        CollisionChunk chunk = layer.get(key(cx, cy));
        if (chunk == null) return CollisionType.NONE;

        int tx = tileX - cx * CollisionChunk.CHUNK_SIZE;
        int ty = tileY - cy * CollisionChunk.CHUNK_SIZE;

        return chunk.get(tx, ty);
    }

    /**
     * Gets collision on ground level (z=0).
     */
    public CollisionType get(int tileX, int tileY) {
        return get(tileX, tileY, 0);
    }

    /**
     * Sets the collision type at a tile position on a specific Z-level.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param z     Z-level
     * @param type  Collision type to set
     */
    public void set(int tileX, int tileY, int z, CollisionType type) {
        int cx = floorDiv(tileX, CollisionChunk.CHUNK_SIZE);
        int cy = floorDiv(tileY, CollisionChunk.CHUNK_SIZE);

        long k = key(cx, cy);

        // Get or create layer
        Map<Long, CollisionChunk> layer = zLayers.computeIfAbsent(z, k1 -> new HashMap<>());

        // If setting to NONE, remove from chunk
        if (type == CollisionType.NONE) {
            CollisionChunk chunk = layer.get(k);
            if (chunk != null) {
                int tx = tileX - cx * CollisionChunk.CHUNK_SIZE;
                int ty = tileY - cy * CollisionChunk.CHUNK_SIZE;
                chunk.set(tx, ty, CollisionType.NONE);

                // Remove empty chunks
                if (chunk.isEmpty()) {
                    layer.remove(k);
                }

                // Remove empty layers
                if (layer.isEmpty()) {
                    zLayers.remove(z);
                }
            }
            return;
        }

        // Create chunk if needed
        CollisionChunk chunk = layer.computeIfAbsent(k, k1 -> new CollisionChunk(cx, cy));

        int tx = tileX - cx * CollisionChunk.CHUNK_SIZE;
        int ty = tileY - cy * CollisionChunk.CHUNK_SIZE;

        chunk.set(tx, ty, type);
    }

    /**
     * Sets collision on ground level (z=0).
     */
    public void set(int tileX, int tileY, CollisionType type) {
        set(tileX, tileY, 0, type);
    }

    /**
     * Clears collision at a tile position (sets to NONE).
     */
    public void clear(int tileX, int tileY, int z) {
        set(tileX, tileY, z, CollisionType.NONE);
    }

    /**
     * Clears collision on ground level.
     */
    public void clear(int tileX, int tileY) {
        clear(tileX, tileY, 0);
    }

    /**
     * Gets all Z-levels that have collision data.
     */
    public Set<Integer> getZLevels() {
        return new HashSet<>(zLayers.keySet());
    }

    /**
     * Checks if a chunk exists at the given coordinates and Z-level.
     */
    public boolean hasChunk(int cx, int cy, int z) {
        Map<Long, CollisionChunk> layer = zLayers.get(z);
        return layer != null && layer.containsKey(key(cx, cy));
    }

    /**
     * Gets a chunk at the given coordinates and Z-level.
     */
    public CollisionChunk getChunk(int cx, int cy, int z) {
        Map<Long, CollisionChunk> layer = zLayers.get(z);
        return layer != null ? layer.get(key(cx, cy)) : null;
    }

    /**
     * Gets all chunks for a specific Z-level.
     */
    public Collection<CollisionChunk> getChunksForLevel(int z) {
        Map<Long, CollisionChunk> layer = zLayers.get(z);
        return layer != null ? layer.values() : Collections.emptyList();
    }

    /**
     * Gets all chunk keys for a specific Z-level.
     */
    public Set<Long> getChunkKeysForLevel(int z) {
        Map<Long, CollisionChunk> layer = zLayers.get(z);
        return layer != null ? layer.keySet() : Collections.emptySet();
    }

    /**
     * Decodes chunk X from key.
     */
    public static int chunkKeyToX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    /**
     * Decodes chunk Y from key.
     */
    public static int chunkKeyToY(long chunkKey) {
        return (int) chunkKey;
    }

    /**
     * Clears all collision data.
     */
    public void clear() {
        zLayers.clear();
    }

    /**
     * Gets the total number of non-empty tiles across all Z-levels.
     */
    public int getTileCount() {
        int count = 0;
        for (Map<Long, CollisionChunk> layer : zLayers.values()) {
            for (CollisionChunk chunk : layer.values()) {
                count += chunk.getTileCount();
            }
        }
        return count;
    }

    // ========================================================================
    // SERIALIZATION SUPPORT (Base64 Binary)
    // ========================================================================

    /**
     * Serializes collision data to a compact Base64 string.
     */
    public String toBase64() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeInt(zLayers.size());
            for (Map.Entry<Integer, Map<Long, CollisionChunk>> zEntry : zLayers.entrySet()) {
                dos.writeInt(zEntry.getKey());          // Z-Level
                dos.writeInt(zEntry.getValue().size()); // Chunk Count

                for (CollisionChunk chunk : zEntry.getValue().values()) {
                    dos.writeInt(chunk.getChunkX());
                    dos.writeInt(chunk.getChunkY());
                    dos.writeInt(chunk.getTileCount());

                    for (int x = 0; x < CollisionChunk.CHUNK_SIZE; x++) {
                        for (int y = 0; y < CollisionChunk.CHUNK_SIZE; y++) {
                            CollisionType type = chunk.get(x, y);
                            if (type != CollisionType.NONE) {
                                dos.writeByte(x);       // Local X (0-31)
                                dos.writeByte(y);       // Local Y (0-31)
                                dos.writeInt(type.getId());
                            }
                        }
                    }
                }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    /**
     * Loads collision data from a compact Base64 string.
     */
    public void fromBase64(String data) {
        if (data == null || data.isEmpty()) return;
        clear();

        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            int layers = dis.readInt();
            while (layers-- > 0) {
                int z = dis.readInt();
                int chunks = dis.readInt();

                // Get or create Z-layer map
                Map<Long, CollisionChunk> layer = zLayers.computeIfAbsent(z, k -> new HashMap<>());

                while (chunks-- > 0) {
                    int cx = dis.readInt();
                    int cy = dis.readInt();
                    int tiles = dis.readInt();

                    CollisionChunk chunk = new CollisionChunk(cx, cy);
                    layer.put(key(cx, cy), chunk);

                    while (tiles-- > 0) {
                        // Read local coords (byte) and ID (int)
                        chunk.set(dis.readByte(), dis.readByte(), CollisionType.fromId(dis.readInt()));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Encodes chunk coordinates into a 64-bit key.
     */
    private static long key(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }

    /**
     * Floor division that handles negative numbers correctly.
     */
    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    @Override
    public String toString() {
        return String.format("CollisionMap[zLevels=%d, tiles=%d]",
                zLayers.size(), getTileCount());
    }

    // ========================================================================
    // INNER CLASS: CollisionChunk
    // ========================================================================

    /**
     * A chunk of collision data. Chunks are 32x32 tiles.
     */
    @Getter
    public static class CollisionChunk {
        public static final int CHUNK_SIZE = 32;

        private final int chunkX;
        private final int chunkY;
        private final CollisionType[][] data;
        private int tileCount = 0;

        public CollisionChunk(int chunkX, int chunkY) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.data = new CollisionType[CHUNK_SIZE][CHUNK_SIZE];

            // Initialize all to NONE
            for (int x = 0; x < CHUNK_SIZE; x++) {
                Arrays.fill(data[x], CollisionType.NONE);
            }
        }

        /**
         * Gets collision type at local chunk coordinates.
         */
        public CollisionType get(int tx, int ty) {
            return data[tx][ty];
        }

        /**
         * Sets collision type at local chunk coordinates.
         */
        public void set(int tx, int ty, CollisionType type) {
            CollisionType old = data[tx][ty];
            data[tx][ty] = type;

            // Track tile count (only count non-NONE tiles)
            if (old == CollisionType.NONE && type != CollisionType.NONE) {
                tileCount++;
            } else if (old != CollisionType.NONE && type == CollisionType.NONE) {
                tileCount--;
            }
        }

        /**
         * Checks if this chunk is empty (all NONE).
         */
        public boolean isEmpty() {
            return tileCount == 0;
        }
    }
}