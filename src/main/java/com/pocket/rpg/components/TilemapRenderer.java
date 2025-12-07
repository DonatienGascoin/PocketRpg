package com.pocket.rpg.components;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.Renderable;
import com.pocket.rpg.rendering.Sprite;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Component for efficient tile-based map rendering.
 * Uses chunk-based storage for memory efficiency and culling optimization.
 *
 * <h2>Coordinate System</h2>
 * Tile coordinates are integers where (0,0) is at the GameObject's transform position.
 * Positive X goes right, positive Y goes up (Y-up coordinate system).
 *
 * <h2>Chunks</h2>
 * Tiles are grouped into {@value TileChunk#CHUNK_SIZE}x{@value TileChunk#CHUNK_SIZE} chunks.
 * Chunks are created on-demand when tiles are set, and culled at chunk-level during rendering.
 *
 * <h2>World Space</h2>
 * Each tile occupies {@link #tileSize} world units. The world position of tile (tx, ty) is:
 * <pre>
 * worldX = transform.position.x + (tx * tileSize)
 * worldY = transform.position.y + (ty * tileSize)
 * </pre>
 *
 * @see TileChunk
 * @see Tile
 */
public class TilemapRenderer extends Component implements Renderable {

    private final Map<Long, TileChunk> chunks = new HashMap<>();

    /**
     * Z-index for render ordering.
     * All tiles in this tilemap render at this z-level.
     */
    @Getter
    @Setter
    private int zIndex = 0;

    /**
     * Size of each tile in world units.
     * Defaults to 1.0 (one tile = one world unit).
     */
    @Getter
    @Setter
    private float tileSize = 1.0f;

    /**
     * If true, chunk vertex data can be pre-baked for performance.
     * Use for tilemaps that don't change at runtime.
     */
    @Getter
    private boolean isStatic = true;

    // Track dirty chunks for static batching invalidation
    private final Map<Long, Boolean> dirtyChunks = new HashMap<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public TilemapRenderer() {
        // Default constructor - uses default tileSize of 1.0
    }

    /**
     * Creates a tilemap with specified tile size.
     *
     * @param tileSize Size of each tile in world units
     */
    public TilemapRenderer(float tileSize) {
        this.tileSize = tileSize;
    }

    /**
     * Creates a tilemap with tile size derived from PPU.
     * Tile size = 1.0 / pixelsPerUnit (so a 16px tile at PPU=16 is 1 world unit).
     *
     * @param pixelsPerUnit Pixels per unit for tile size calculation
     * @return New Tilemap with calculated tile size
     */
    public static TilemapRenderer withPixelsPerUnit(float pixelsPerUnit) {
        TilemapRenderer tilemapRenderer = new TilemapRenderer();
        tilemapRenderer.tileSize = 1.0f; // 1 world unit per tile at given PPU
        return tilemapRenderer;
    }

    /**
     * Creates a tilemap using the global PPU from config.
     *
     * @return New Tilemap with tile size from config
     */
    public static TilemapRenderer withDefaultPPU() {
        RenderingConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        return withPixelsPerUnit(config.getPixelsPerUnit());
    }

    // ========================================================================
    // RENDERABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean isRenderVisible() {
        if (!isEnabled()) {
            return false;
        }
        if (gameObject == null || !gameObject.isEnabled()) {
            return false;
        }
        // Must have at least one chunk
        return !chunks.isEmpty();
    }

    // ========================================================================
    // TILE ACCESS
    // ========================================================================

    /**
     * Gets the tile at the specified tile coordinates.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return The tile at that position, or null if empty
     */
    public Tile get(int tileX, int tileY) {
        int cx = floorDiv(tileX, TileChunk.CHUNK_SIZE);
        int cy = floorDiv(tileY, TileChunk.CHUNK_SIZE);

        TileChunk chunk = chunks.get(key(cx, cy));
        if (chunk == null) return null;

        int tx = tileX - cx * TileChunk.CHUNK_SIZE;
        int ty = tileY - cy * TileChunk.CHUNK_SIZE;

        return chunk.get(tx, ty);
    }

    /**
     * Sets a tile at the specified tile coordinates.
     * Creates the containing chunk if it doesn't exist.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param tile  The tile to set (null to clear)
     */
    public void set(int tileX, int tileY, Tile tile) {
        int cx = floorDiv(tileX, TileChunk.CHUNK_SIZE);
        int cy = floorDiv(tileY, TileChunk.CHUNK_SIZE);

        long k = key(cx, cy);
        TileChunk chunk = chunks.computeIfAbsent(k, k1 -> new TileChunk(cx, cy));

        int tx = tileX - cx * TileChunk.CHUNK_SIZE;
        int ty = tileY - cy * TileChunk.CHUNK_SIZE;

        chunk.set(tx, ty, tile);

        // Mark chunk as dirty for static batch invalidation
        markChunkDirty(cx, cy);
    }

    /**
     * Clears a tile at the specified coordinates.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     */
    public void clear(int tileX, int tileY) {
        set(tileX, tileY, null);
    }

    // ========================================================================
    // CHUNK ACCESS
    // ========================================================================

    /**
     * Checks if a chunk exists at the specified chunk coordinates.
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     * @return true if chunk exists
     */
    public boolean hasChunk(int cx, int cy) {
        return chunks.containsKey(key(cx, cy));
    }

    /**
     * Gets a chunk at the specified chunk coordinates.
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     * @return The chunk, or null if it doesn't exist
     */
    public TileChunk getChunk(int cx, int cy) {
        return chunks.get(key(cx, cy));
    }

    /**
     * Returns all chunks in this tilemap.
     */
    public Collection<TileChunk> allChunks() {
        return chunks.values();
    }

    /**
     * Returns all chunk keys (encoded cx, cy pairs).
     */
    public Set<Long> chunkKeys() {
        return chunks.keySet();
    }

    /**
     * Decodes chunk X coordinate from a chunk key.
     *
     * @param chunkKey Encoded chunk key
     * @return Chunk X coordinate
     */
    public static int chunkKeyToX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    /**
     * Decodes chunk Y coordinate from a chunk key.
     *
     * @param chunkKey Encoded chunk key
     * @return Chunk Y coordinate
     */
    public static int chunkKeyToY(long chunkKey) {
        return (int) chunkKey;
    }

    // ========================================================================
    // WORLD SPACE CALCULATIONS
    // ========================================================================

    /**
     * Calculates world-space AABB for a chunk.
     * Used for frustum culling.
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     * @return AABB as [minX, minY, maxX, maxY] in world units
     */
    public float[] getChunkWorldBounds(int cx, int cy) {
        Vector3f pos = getWorldPosition();

        float chunkWorldSize = TileChunk.CHUNK_SIZE * tileSize;

        float minX = pos.x + (cx * chunkWorldSize);
        float minY = pos.y + (cy * chunkWorldSize);
        float maxX = minX + chunkWorldSize;
        float maxY = minY + chunkWorldSize;

        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * Converts tile coordinates to world position.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return World position of tile's bottom-left corner
     */
    public Vector3f tileToWorld(int tileX, int tileY) {
        Vector3f pos = getWorldPosition();
        return new Vector3f(
                pos.x + (tileX * tileSize),
                pos.y + (tileY * tileSize),
                pos.z
        );
    }

    /**
     * Converts world position to tile coordinates.
     *
     * @param worldX World X position
     * @param worldY World Y position
     * @return Tile coordinates as [tileX, tileY]
     */
    public int[] worldToTile(float worldX, float worldY) {
        Vector3f pos = getWorldPosition();
        int tileX = (int) Math.floor((worldX - pos.x) / tileSize);
        int tileY = (int) Math.floor((worldY - pos.y) / tileSize);
        return new int[]{tileX, tileY};
    }

    /**
     * Gets the tilemap's world position from its transform.
     */
    private Vector3f getWorldPosition() {
        if (gameObject == null) {
            return new Vector3f(0, 0, 0);
        }
        return gameObject.getTransform().getPosition();
    }

    // ========================================================================
    // STATIC BATCHING
    // ========================================================================

    /**
     * Sets whether this tilemap is static (for chunk pre-batching).
     *
     * @param isStatic true to enable static batching
     */
    public void setStatic(boolean isStatic) {
        if (this.isStatic != isStatic) {
            this.isStatic = isStatic;

            if (gameObject != null && gameObject.getScene() != null) {
                gameObject.getScene().markStaticBatchDirty();
            }
        }
    }

    /**
     * Marks a chunk as dirty (needs rebatching).
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     */
    public void markChunkDirty(int cx, int cy) {
        dirtyChunks.put(key(cx, cy), true);

        if (isStatic && gameObject != null && gameObject.getScene() != null) {
            gameObject.getScene().markStaticBatchDirty();
        }
    }

    /**
     * Checks if a chunk is dirty.
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     * @return true if chunk needs rebatching
     */
    public boolean isChunkDirty(int cx, int cy) {
        return dirtyChunks.getOrDefault(key(cx, cy), false);
    }

    /**
     * Clears the dirty flag for a chunk.
     *
     * @param cx Chunk X coordinate
     * @param cy Chunk Y coordinate
     */
    public void clearChunkDirty(int cx, int cy) {
        dirtyChunks.remove(key(cx, cy));
    }

    /**
     * Clears all dirty flags.
     */
    public void clearAllDirty() {
        dirtyChunks.clear();
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
        return String.format("Tilemap[chunks=%d, tileSize=%.2f, zIndex=%d, static=%b]",
                chunks.size(), tileSize, zIndex, isStatic);
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * A chunk of tiles. Chunks are {@value #CHUNK_SIZE}x{@value #CHUNK_SIZE} tiles.
     */
    @Getter
    public static final class TileChunk {
        public static final int CHUNK_SIZE = 32;

        private final int chunkX;
        private final int chunkY;
        /**
         * -- GETTER --
         *  Returns the raw tile array.
         *  Use with caution - modifications bypass dirty tracking.
         */
        private final Tile[][] tiles;
        /**
         * -- GETTER --
         *  Returns the number of non-null tiles in this chunk.
         */
        private int tileCount = 0;

        public TileChunk(int chunkX, int chunkY) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.tiles = new Tile[CHUNK_SIZE][CHUNK_SIZE];
        }

        /**
         * Gets a tile at local chunk coordinates.
         *
         * @param tx Local X (0 to CHUNK_SIZE-1)
         * @param ty Local Y (0 to CHUNK_SIZE-1)
         * @return The tile, or null if empty
         */
        public Tile get(int tx, int ty) {
            return tiles[tx][ty];
        }

        /**
         * Sets a tile at local chunk coordinates.
         *
         * @param tx   Local X (0 to CHUNK_SIZE-1)
         * @param ty   Local Y (0 to CHUNK_SIZE-1)
         * @param tile The tile to set (null to clear)
         */
        public void set(int tx, int ty, Tile tile) {
            Tile old = tiles[tx][ty];
            tiles[tx][ty] = tile;

            // Track tile count
            if (old == null && tile != null) {
                tileCount++;
            } else if (old != null && tile == null) {
                tileCount--;
            }
        }

        /**
         * Checks if this chunk is empty (no tiles).
         */
        public boolean isEmpty() {
            return tileCount == 0;
        }

    }

    /**
     * Represents a single tile's data.
     * Immutable record containing sprite and metadata.
     */
    public record Tile(
            String name,
            Sprite sprite
    ) {
        /**
         * Creates a tile with just a sprite (name derived from sprite).
         */
        public Tile(Sprite sprite) {
            this(sprite != null ? sprite.getName() : "empty", sprite);
        }
    }
}