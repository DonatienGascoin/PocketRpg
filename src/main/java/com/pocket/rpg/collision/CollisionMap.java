package com.pocket.rpg.collision;

import lombok.Getter;

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
    // SERIALIZATION SUPPORT
    // ========================================================================

    /**
     * Gets collision data in sparse format for serialization.
     * Format: Map<"z", Map<"cx,cy", Map<"tx,ty", CollisionTypeId>>>
     */
    public Map<String, Map<String, Map<String, Integer>>> toSparseFormat() {
        Map<String, Map<String, Map<String, Integer>>> sparse = new HashMap<>();

        for (Map.Entry<Integer, Map<Long, CollisionChunk>> zEntry : zLayers.entrySet()) {
            int z = zEntry.getKey();
            Map<Long, CollisionChunk> layer = zEntry.getValue();

            Map<String, Map<String, Integer>> layerData = new HashMap<>();

            for (Long chunkKey : layer.keySet()) {
                int cx = chunkKeyToX(chunkKey);
                int cy = chunkKeyToY(chunkKey);
                CollisionChunk chunk = layer.get(chunkKey);

                Map<String, Integer> tiles = new HashMap<>();
                for (int tx = 0; tx < CollisionChunk.CHUNK_SIZE; tx++) {
                    for (int ty = 0; ty < CollisionChunk.CHUNK_SIZE; ty++) {
                        CollisionType type = chunk.get(tx, ty);
                        if (type != CollisionType.NONE) {
                            tiles.put(tx + "," + ty, type.getId());
                        }
                    }
                }

                if (!tiles.isEmpty()) {
                    layerData.put(cx + "," + cy, tiles);
                }
            }

            if (!layerData.isEmpty()) {
                sparse.put(String.valueOf(z), layerData);
            }
        }

        return sparse;
    }

    /**
     * Loads collision data from sparse format.
     */
    public void fromSparseFormat(Map<String, Map<String, Map<String, Integer>>> sparse) {
        if (sparse == null) return;

        clear(); // Clear existing data

        for (Map.Entry<String, Map<String, Map<String, Integer>>> zEntry : sparse.entrySet()) {
            int z = Integer.parseInt(zEntry.getKey());
            Map<String, Map<String, Integer>> layerData = zEntry.getValue();

            for (Map.Entry<String, Map<String, Integer>> chunkEntry : layerData.entrySet()) {
                String[] coords = chunkEntry.getKey().split(",");
                int cx = Integer.parseInt(coords[0]);
                int cy = Integer.parseInt(coords[1]);

                for (Map.Entry<String, Integer> tileEntry : chunkEntry.getValue().entrySet()) {
                    String[] tileCoords = tileEntry.getKey().split(",");
                    int localX = Integer.parseInt(tileCoords[0]);
                    int localY = Integer.parseInt(tileCoords[1]);

                    int worldTx = cx * CollisionChunk.CHUNK_SIZE + localX;
                    int worldTy = cy * CollisionChunk.CHUNK_SIZE + localY;

                    CollisionType type = CollisionType.fromId(tileEntry.getValue());
                    set(worldTx, worldTy, z, type);
                }
            }
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