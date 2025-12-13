package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.renderers.VertexLayout;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Batches sprites by texture to minimize draw calls.
 * Supports depth sorting, static sprites, and configurable sorting strategies.
 * <p>
 * Uses world units for all position and size calculations.
 * Sprite dimensions come from {@link Sprite#getWorldWidth()} and {@link Sprite#getWorldHeight()}.
 *
 * <p>Tilemap Support
 */
public class SpriteBatch {

    // Maximum sprites per batch
    private final int maxBatchSize;

    // Batch data
    private final List<BatchItem> dynamicItems;
    private final List<BatchItem> staticItems;
    private final FloatBuffer vertexBuffer;

    // OpenGL resources
    private int vao;
    private int vbo;

    /**
     * -- SETTER --
     * Sets the sorting strategy.
     */
    // Sorting strategy
    @Getter
    @Setter
    private SortingStrategy sortingStrategy;

    // Current batch state
    private boolean isBatching = false;
    private boolean staticBatchDirty = true;

    // Statistics
    @Getter
    private int drawCalls = 0;
    @Getter
    private int totalSprites = 0;
    @Getter
    private int staticSpritesRendered = 0;
    @Getter
    private int dynamicSpritesRendered = 0;

    /**
     * Sorting strategies for batch rendering.
     */
    public enum SortingStrategy {
        /**
         * Prioritize batching efficiency over correct depth.
         * Sort: Z-index → Texture → Y-position
         * Best for: Games with few overlapping sprites
         */
        TEXTURE_PRIORITY,

        /**
         * Prioritize correct depth rendering over batching.
         * Sort: Z-index → Y-position → Texture
         * Best for: Top-down games with overlapping sprites
         */
        DEPTH_PRIORITY,

        /**
         * Balance between batching and depth.
         * Sort: Z-index → Texture (within tolerance) → Y-position
         * Best for: Most games
         */
        BALANCED
    }

    /**
     * Represents a sprite submitted to the batch.
     */
    private static class BatchItem {
        // For SpriteRenderer-based items
        SpriteRenderer spriteRenderer;

        // For tile-based items (when spriteRenderer is null)
        Sprite tileSprite;
        float tileX;
        float tileY;
        float tileWidth;
        float tileHeight;

        // Common fields
        int textureId;
        float zIndex;
        float yPosition;
        boolean isStatic;
        boolean isTile;
        Vector4f tintColor;

        // Constructor for SpriteRenderer
        BatchItem(SpriteRenderer spriteRenderer, int textureId, float zIndex, float yPosition, boolean isStatic, Vector4f tintColor) {
            this.spriteRenderer = spriteRenderer;
            this.textureId = textureId;
            this.zIndex = zIndex;
            this.yPosition = yPosition;
            this.isStatic = isStatic;
            this.tintColor = tintColor;
            this.isTile = false;
        }

        // Constructor for Tile
        BatchItem(Sprite sprite, float x, float y, float width, float height,
                  int textureId, float zIndex, boolean isStatic, Vector4f tintColor) {
            this.tileSprite = sprite;
            this.tileX = x;
            this.tileY = y;
            this.tileWidth = width;
            this.tileHeight = height;
            this.textureId = textureId;
            this.zIndex = zIndex;
            this.yPosition = y;
            this.isStatic = isStatic;
            this.tintColor = tintColor;
            this.isTile = true;
        }
    }

    public SpriteBatch(RenderingConfig config) {
        this.maxBatchSize = config.getMaxBatchSize();
        this.sortingStrategy = config.getSortingStrategy();
        dynamicItems = new ArrayList<>(maxBatchSize);
        staticItems = new ArrayList<>(maxBatchSize);

        // Allocate vertex buffer (off-heap for performance)
        int bufferSize = maxBatchSize * VertexLayout.FLOATS_PER_SPRITE;
        vertexBuffer = MemoryUtil.memAllocFloat(bufferSize);

        initGL();
    }

    /**
     * Initializes OpenGL resources.
     */
    private void initGL() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Allocate buffer (dynamic because we update every frame)
        int bufferSizeBytes = maxBatchSize * VertexLayout.BYTES_PER_SPRITE;
        glBufferData(GL_ARRAY_BUFFER, bufferSizeBytes, GL_DYNAMIC_DRAW);

        // Setup vertex attributes using VertexLayout
        VertexLayout.setupVertexAttributes();

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        System.out.println("SpriteBatch initialized:");
        System.out.println(VertexLayout.describe());
    }

    /**
     * Begins a new batch.
     */
    public void begin() {
        if (isBatching) {
            throw new IllegalStateException("Already batching! Call end() first.");
        }

        dynamicItems.clear();
        drawCalls = 0;
        totalSprites = 0;
        staticSpritesRendered = 0;
        dynamicSpritesRendered = 0;
        isBatching = true;
    }

    /**
     * Submits a sprite to the batch.
     */
    public void submit(SpriteRenderer spriteRenderer, Vector4f tintColor) {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        Sprite sprite = spriteRenderer.getSprite();
        if (sprite == null || sprite.getTexture() == null) {
            return;
        }

        // Get sprite properties
        int textureId = sprite.getTexture().getTextureId();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        // Use SpriteRenderer.zIndex for sorting (NOT transform.position.z!)
        float zIndex = spriteRenderer.getZIndex();
        float yPosition = transform.getPosition().y;
        boolean isStatic = spriteRenderer.isStatic();

        // Create batch item
        BatchItem item = new BatchItem(spriteRenderer, textureId, zIndex, yPosition, isStatic, tintColor);

        // Add to appropriate list
        if (isStatic) {
            // Static sprites only need to be added once
            if (staticBatchDirty) { // TODO: Does this work ? What is staticBatchDirty is false and this item hasn't been added yet ?
                staticItems.add(item);
            }
        } else {
            dynamicItems.add(item);
        }

        totalSprites++;
    }

    // ========================================================================
    // TILEMAP SUPPORT
    // ========================================================================

    /**
     * Submits all tiles from a chunk to the batch.
     * More efficient than calling submitTile for each tile individually.
     *
     * @param tilemapRenderer The tilemap
     * @param cx              Chunk X coordinate
     * @param cy              Chunk Y coordinate
     */
    public void submitChunk(TilemapRenderer tilemapRenderer, int cx, int cy, Vector4f tintColor) {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        TilemapRenderer.TileChunk chunk = tilemapRenderer.getChunk(cx, cy);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        Vector3f tilemapPos = tilemapRenderer.getGameObject().getTransform().getPosition();
        float tileSize = tilemapRenderer.getTileSize();
        float zIndex = tilemapRenderer.getZIndex();
        boolean isStatic = tilemapRenderer.isStatic();

        int chunkSize = TilemapRenderer.TileChunk.CHUNK_SIZE;
        int baseX = cx * chunkSize;
        int baseY = cy * chunkSize;

        TilemapRenderer.Tile[][] tiles = chunk.getTiles();

        for (int tx = 0; tx < chunkSize; tx++) {
            for (int ty = 0; ty < chunkSize; ty++) {
                TilemapRenderer.Tile tile = tiles[tx][ty];
                if (tile == null || tile.sprite() == null || tile.sprite().getTexture() == null) {
                    continue;
                }

                Sprite sprite = tile.sprite();
                int textureId = sprite.getTexture().getTextureId();

                int tileX = baseX + tx;
                int tileY = baseY + ty;

                float worldX = tilemapPos.x + (tileX * tileSize);
                float worldY = tilemapPos.y + (tileY * tileSize);

                // Use tile size for dimensions (sprites are scaled to fit)
                BatchItem item = new BatchItem(sprite, worldX, worldY, tileSize, tileSize,
                        textureId, zIndex, isStatic, tintColor);

                if (isStatic) {
                    if (staticBatchDirty) {
                        staticItems.add(item);
                    }
                } else {
                    dynamicItems.add(item);
                }

                totalSprites++;
            }
        }
    }

    /**
     * Marks the static batch as dirty, forcing a rebuild.
     * Call this when static sprites are added/removed/modified.
     */
    public void markStaticBatchDirty() {
        staticBatchDirty = true;
        staticItems.clear();
    }

    /**
     * Ends batching and renders everything.
     */
    public void end() {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        // Render static sprites (only rebuild if dirty)
        if (!staticItems.isEmpty()) {
            if (staticBatchDirty) {
                sortItems(staticItems);
                staticBatchDirty = false;
            }
            staticSpritesRendered = staticItems.size();
            flushItems(staticItems);
        }

        // Render dynamic sprites (always rebuild)
        if (!dynamicItems.isEmpty()) {
            sortItems(dynamicItems);
            dynamicSpritesRendered = dynamicItems.size();
            flushItems(dynamicItems);
        }

        isBatching = false;
    }

    /**
     * Sorts batch items according to the current sorting strategy.
     */
    private void sortItems(List<BatchItem> items) {
        switch (sortingStrategy) {
            case TEXTURE_PRIORITY:
                // Z-index → Texture → Y-position
                items.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex, b.zIndex);
                    if (zCompare != 0) return zCompare;

                    int texCompare = Integer.compare(a.textureId, b.textureId);
                    if (texCompare != 0) return texCompare;

                    return Float.compare(a.yPosition, b.yPosition);
                });
                break;

            case DEPTH_PRIORITY:
                // Z-index → Y-position → Texture
                items.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex, b.zIndex);
                    if (zCompare != 0) return zCompare;

                    int yCompare = Float.compare(a.yPosition, b.yPosition);
                    if (yCompare != 0) return yCompare;

                    return Integer.compare(a.textureId, b.textureId);
                });
                break;

            case BALANCED:
                // Z-index → Texture (group nearby Y) → Y-position
                // Note: tolerance is now in world units (was 64 pixels, now ~4 world units)
                items.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex, b.zIndex);
                    if (zCompare != 0) return zCompare;

                    // Group sprites within 4 world units Y-distance by texture
                    float yDiff = Math.abs(a.yPosition - b.yPosition);
                    if (yDiff > 4f) {
                        return Float.compare(a.yPosition, b.yPosition);
                    }

                    int texCompare = Integer.compare(a.textureId, b.textureId);
                    if (texCompare != 0) return texCompare;
                    return Float.compare(a.yPosition, b.yPosition);
                });
                break;
        }
    }

    /**
     * Flushes a list of items to the GPU.
     * Groups sprites by texture and renders each group.
     */
    private void flushItems(List<BatchItem> items) {
        if (items.isEmpty()) return;

        int currentTextureId = -1;
        int batchStart = 0;

        for (int i = 0; i <= items.size(); i++) {
            boolean needsFlush;
            int textureId = -1;

            if (i < items.size()) {
                textureId = items.get(i).textureId;
                needsFlush = (textureId != currentTextureId && currentTextureId != -1);
            } else {
                needsFlush = true; // Flush remaining items
            }

            if (needsFlush) {
                renderBatch(items, batchStart, i, currentTextureId);
                batchStart = i;
            }

            currentTextureId = textureId;
        }
    }

    /**
     * Renders a subset of items with the same texture.
     */
    private void renderBatch(List<BatchItem> items, int start, int end, int textureId) {
        if (start >= end) return;
        int count = end - start;

        // Fill vertex buffer
        vertexBuffer.clear();
        for (int i = start; i < end; i++) {
            BatchItem item = items.get(i);
            if (item.isTile) {
                addTileVertices(item);
            } else {
                addSpriteVertices(item);
            }
        }
        vertexBuffer.flip();

        // Upload to GPU
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);

        // Bind texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Draw
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, count * VertexLayout.VERTICES_PER_SPRITE);
        glBindVertexArray(0);

        drawCalls++;
    }

    /**
     * Adds vertex data for a tile to the vertex buffer.
     * Tiles are axis-aligned (no rotation) and use bottom-left origin.
     */
    private void addTileVertices(BatchItem item) {
        Sprite sprite = item.tileSprite;

        float x0 = item.tileX;
        float y0 = item.tileY;
        float x1 = item.tileX + item.tileWidth;
        float y1 = item.tileY + item.tileHeight;

        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        float r = item.tintColor.x;
        float g = item.tintColor.y;
        float b = item.tintColor.z;
        float a = item.tintColor.w;

        // Triangle 1
        putVertex(x0, y0, u0, v0, r, g, b, a);
        putVertex(x0, y1, u0, v1, r, g, b, a);
        putVertex(x1, y1, u1, v1, r, g, b, a);

        // Triangle 2
        putVertex(x0, y0, u0, v0, r, g, b, a);
        putVertex(x1, y1, u1, v1, r, g, b, a);
        putVertex(x1, y0, u1, v0, r, g, b, a);
    }

    private void putVertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        vertexBuffer
                .put(x).put(y)
                .put(u).put(v)
                .put(r).put(g)
                .put(b).put(a);
    }

    /**
     * Adds vertex data for a sprite to the vertex buffer.
     * <p>
     * Uses world units for all calculations:
     * - Position from Transform (world units)
     * - Size from Sprite.getWorldWidth/Height() (world units)
     */
    private void addSpriteVertices(BatchItem item) {
        SpriteRenderer spriteRenderer = item.spriteRenderer;
        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();
        Vector3f rotation = transform.getRotation();

        // Final dimensions in world units
        float width = sprite.getWorldWidth() * scale.x;
        float height = sprite.getWorldHeight() * scale.y;

        // Origin offset (world units)
        float originX = width * spriteRenderer.getOriginX();
        float originY = height * spriteRenderer.getOriginY();

        // UVs
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        // Quad corners before rotation (Y-up)
        float x0 = pos.x - originX;
        float y0 = pos.y - originY;
        float x1 = pos.x + (width - originX);
        float y1 = pos.y + (height - originY);

        float r = item.spriteRenderer.getTintColor().x * item.tintColor.x;
        float g = item.spriteRenderer.getTintColor().y * item.tintColor.y;
        float b = item.spriteRenderer.getTintColor().z * item.tintColor.z;
        float a = item.spriteRenderer.getTintColor().w * item.tintColor.w;

        // Rotation (Z axis)
        float angle = (float) Math.toRadians(rotation.z);

        if (angle != 0.0f) {
            float centerX = pos.x;
            float centerY = pos.y;

            float[] corners = rotateQuad(x0, y0, x1, y1, centerX, centerY, angle);

            // Triangle 1
            putVertex(corners[0], corners[1], u0, v0, r, b, g, a); // Bottom-left
            putVertex(corners[2], corners[3], u0, v1, r, b, g, a); // Top-left
            putVertex(corners[4], corners[5], u1, v1, r, b, g, a); // Top-right

            // Triangle 2
            putVertex(corners[0], corners[1], u0, v0, r, g, b, a); // Bottom-left
            putVertex(corners[4], corners[5], u1, v1, r, g, b, a); // Top-right
            putVertex(corners[6], corners[7], u1, v0, r, g, b, a); // Bottom-right

        } else {
            // No rotation — fast path

            // Triangle 1
            putVertex(x0, y0, u0, v0, r, g, b, a); // Bottom-left
            putVertex(x0, y1, u0, v1, r, g, b, a); // Top-left
            putVertex(x1, y1, u1, v1, r, g, b, a); // Top-right

            // Triangle 2
            putVertex(x0, y0, u0, v0, r, g, b, a); // Bottom-left
            putVertex(x1, y1, u1, v1, r, g, b, a); // Top-right
            putVertex(x1, y0, u1, v0, r, g, b, a); // Bottom-right
        }

    }

    /**
     * Rotates quad corners around a center point.
     * Returns corners in order: [bottom-left, top-left, top-right, bottom-right]
     */
    private float[] rotateQuad(float x0, float y0, float x1, float y1,
                               float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float[] corners = new float[8];

        // Bottom-left (x0, y0)
        corners[0] = rotateX(x0, y0, centerX, centerY, cos, sin);
        corners[1] = rotateY(x0, y0, centerX, centerY, cos, sin);

        // Top-left (x0, y1)
        corners[2] = rotateX(x0, y1, centerX, centerY, cos, sin);
        corners[3] = rotateY(x0, y1, centerX, centerY, cos, sin);

        // Top-right (x1, y1)
        corners[4] = rotateX(x1, y1, centerX, centerY, cos, sin);
        corners[5] = rotateY(x1, y1, centerX, centerY, cos, sin);

        // Bottom-right (x1, y0)
        corners[6] = rotateX(x1, y0, centerX, centerY, cos, sin);
        corners[7] = rotateY(x1, y0, centerX, centerY, cos, sin);

        return corners;
    }

    private float rotateX(float x, float y, float cx, float cy, float cos, float sin) {
        return cos * (x - cx) - sin * (y - cy) + cx;
    }

    private float rotateY(float x, float y, float cx, float cy, float cos, float sin) {
        return sin * (x - cx) + cos * (y - cy) + cy;
    }

    /**
     * Destroys OpenGL resources.
     */
    public void destroy() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
        }
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
        }
    }
}