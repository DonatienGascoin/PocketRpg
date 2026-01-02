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
 * Supports depth sorting and configurable sorting strategies.
 * <p>
 * Uses deferred submission with global sorting and auto-flush for unlimited sprite counts.
 * Sprites are buffered during begin/end, then globally sorted and rendered in batches.
 * <p>
 * Uses world units for all position and size calculations.
 * Sprite dimensions come from {@link Sprite#getWorldWidth()} and {@link Sprite#getWorldHeight()}.
 */
public class SpriteBatch {

    // Maximum sprites per GPU batch (vertex buffer size)
    private final int maxBatchSize;

    // Submission buffers (unbounded)
    private final List<SpriteSubmission> spriteSubmissions = new ArrayList<>();
    private final List<TileSubmission> tileSubmissions = new ArrayList<>();

    // Processed items for rendering
    private final List<BatchItem> batchItems = new ArrayList<>();

    // Vertex buffer (fixed size, reused each flush)
    private final FloatBuffer vertexBuffer;

    // OpenGL resources
    private int vao;
    private int vbo;

    @Getter
    @Setter
    private SortingStrategy sortingStrategy;

    private boolean isBatching = false;

    // Statistics
    @Getter
    private int drawCalls = 0;
    @Getter
    private int totalSprites = 0;

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

    // ========================================================================
    // SUBMISSION RECORDS (buffered until end())
    // ========================================================================

    /**
     * Buffered sprite submission from SpriteRenderer.
     */
    private record SpriteSubmission(
            SpriteRenderer spriteRenderer,
            Vector4f tintColor
    ) {}

    /**
     * Buffered tile submission.
     */
    private record TileSubmission(
            Sprite sprite,
            float x,
            float y,
            float width,
            float height,
            float zIndex,
            Vector4f tintColor
    ) {}

    // ========================================================================
    // BATCH ITEM (created during processing)
    // ========================================================================

    /**
     * Processed item ready for sorting and rendering.
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
        boolean isTile;
        Vector4f tintColor;

        // Constructor for SpriteRenderer
        BatchItem(SpriteRenderer spriteRenderer, int textureId, float zIndex, float yPosition, Vector4f tintColor) {
            this.spriteRenderer = spriteRenderer;
            this.textureId = textureId;
            this.zIndex = zIndex;
            this.yPosition = yPosition;
            this.tintColor = tintColor;
            this.isTile = false;
        }

        // Constructor for Tile
        BatchItem(Sprite sprite, float x, float y, float width, float height,
                  int textureId, float zIndex, Vector4f tintColor) {
            this.tileSprite = sprite;
            this.tileX = x;
            this.tileY = y;
            this.tileWidth = width;
            this.tileHeight = height;
            this.textureId = textureId;
            this.zIndex = zIndex;
            this.yPosition = y;
            this.tintColor = tintColor;
            this.isTile = true;
        }
    }

    public SpriteBatch(RenderingConfig config) {
        this.maxBatchSize = config.getMaxBatchSize();
        this.sortingStrategy = config.getSortingStrategy();

        // Allocate vertex buffer (off-heap for performance)
        int bufferSize = maxBatchSize * VertexLayout.FLOATS_PER_SPRITE;
        vertexBuffer = MemoryUtil.memAllocFloat(bufferSize);

        initGL();
    }

    private void initGL() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Allocate buffer (dynamic because we update every frame)
        int bufferSizeBytes = maxBatchSize * VertexLayout.BYTES_PER_SPRITE;
        glBufferData(GL_ARRAY_BUFFER, bufferSizeBytes, GL_DYNAMIC_DRAW);

        VertexLayout.setupVertexAttributes();

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        System.out.println("SpriteBatch initialized:");
        System.out.println(VertexLayout.describe());
    }

    /**
     * Begins a new batch frame.
     */
    public void begin() {
        if (isBatching) {
            throw new IllegalStateException("Already batching! Call end() first.");
        }

        spriteSubmissions.clear();
        tileSubmissions.clear();
        batchItems.clear();
        drawCalls = 0;
        totalSprites = 0;
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

        spriteSubmissions.add(new SpriteSubmission(spriteRenderer, tintColor));
        totalSprites++;
    }

    // ========================================================================
    // TILEMAP SUPPORT
    // ========================================================================

    /**
     * Submits all tiles from a chunk to the batch.
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

                int tileX = baseX + tx;
                int tileY = baseY + ty;

                float worldX = tilemapPos.x + (tileX * tileSize);
                float worldY = tilemapPos.y + (tileY * tileSize);

                tileSubmissions.add(new TileSubmission(
                        tile.sprite(), worldX, worldY, tileSize, tileSize, zIndex, tintColor
                ));
                totalSprites++;
            }
        }
    }

    /**
     * Draws a sprite directly without requiring a SpriteRenderer.
     * Useful for editor previews, UI elements, etc.
     */
    public void draw(Sprite sprite, float x, float y, float width, float height, float zIndex, Vector4f tint) {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        if (sprite == null || sprite.getTexture() == null) {
            return;
        }

        tileSubmissions.add(new TileSubmission(sprite, x, y, width, height, zIndex, tint));
        totalSprites++;
    }

    /**
     * Draws a sprite with default white tint.
     */
    public void draw(Sprite sprite, float x, float y, float width, float height, float zIndex) {
        draw(sprite, x, y, width, height, zIndex, new Vector4f(1f, 1f, 1f, 1f));
    }



    /**
     * Ends batching and renders everything.
     */
    public void end() {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        processBatches();
        isBatching = false;
    }

    /**
     * Converts submissions to BatchItems, sorts globally, and renders in batches.
     */
    private void processBatches() {
        // Convert sprite submissions to batch items
        for (SpriteSubmission sub : spriteSubmissions) {
            SpriteRenderer sr = sub.spriteRenderer();
            Sprite sprite = sr.getSprite();
            int textureId = sprite.getTexture().getTextureId();
            Transform transform = sr.getGameObject().getTransform();
            float zIndex = sr.getZIndex();
            float yPosition = transform.getPosition().y;

            batchItems.add(new BatchItem(sr, textureId, zIndex, yPosition, sub.tintColor()));
        }

        // Convert tile submissions to batch items
        for (TileSubmission sub : tileSubmissions) {
            int textureId = sub.sprite().getTexture().getTextureId();
            batchItems.add(new BatchItem(
                    sub.sprite(), sub.x(), sub.y(), sub.width(), sub.height(),
                    textureId, sub.zIndex(), sub.tintColor()
            ));
        }

        if (batchItems.isEmpty()) {
            return;
        }

        // Global sort
        sortItems(batchItems);

        // Render with auto-flush
        renderWithAutoFlush();
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
     * Renders all batch items, automatically flushing when buffer is full.
     * Groups consecutive same-texture items for efficient batching.
     */
    private void renderWithAutoFlush() {
        int currentTextureId = -1;
        int batchStartIndex = 0;
        int spriteCountInBuffer = 0;

        for (int i = 0; i < batchItems.size(); i++) {
            BatchItem item = batchItems.get(i);

            // Check if we need to flush due to texture change
            boolean textureChanged = (item.textureId != currentTextureId && currentTextureId != -1);

            // Check if we need to flush due to buffer full
            boolean bufferFull = (spriteCountInBuffer >= maxBatchSize);

            if (textureChanged || bufferFull) {
                // Flush current batch
                if (spriteCountInBuffer > 0) {
                    flushBuffer(currentTextureId, spriteCountInBuffer);
                    spriteCountInBuffer = 0;
                }
            }

            // Add item to buffer
            if (item.isTile) {
                addTileVertices(item);
            } else {
                addSpriteVertices(item);
            }

            currentTextureId = item.textureId;
            spriteCountInBuffer++;
        }

        // Flush remaining
        if (spriteCountInBuffer > 0) {
            flushBuffer(currentTextureId, spriteCountInBuffer);
        }
    }

    /**
     * Uploads vertex buffer to GPU and draws.
     */
    private void flushBuffer(int textureId, int spriteCount) {
        if (spriteCount == 0) return;

        vertexBuffer.flip();

        // Upload to GPU
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);

        // Bind texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Draw
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, spriteCount * VertexLayout.VERTICES_PER_SPRITE);
        glBindVertexArray(0);

        drawCalls++;

        // Reset buffer for next batch
        vertexBuffer.clear();
    }

    /**
     * Adds vertex data for a tile to the vertex buffer.
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

        float r = spriteRenderer.getTintColor().x * item.tintColor.x;
        float g = spriteRenderer.getTintColor().y * item.tintColor.y;
        float b = spriteRenderer.getTintColor().z * item.tintColor.z;
        float a = spriteRenderer.getTintColor().w * item.tintColor.w;

        // Rotation (Z axis)
        float angle = (float) Math.toRadians(rotation.z);

        if (angle != 0.0f) {
            float centerX = pos.x;
            float centerY = pos.y;

            float[] corners = rotateQuad(x0, y0, x1, y1, centerX, centerY, angle);

            // Triangle 1
            putVertex(corners[0], corners[1], u0, v0, r, g, b, a);
            putVertex(corners[2], corners[3], u0, v1, r, g, b, a);
            putVertex(corners[4], corners[5], u1, v1, r, g, b, a);

            // Triangle 2
            putVertex(corners[0], corners[1], u0, v0, r, g, b, a);
            putVertex(corners[4], corners[5], u1, v1, r, g, b, a);
            putVertex(corners[6], corners[7], u1, v0, r, g, b, a);

        } else {
            // No rotation — fast path

            // Triangle 1
            putVertex(x0, y0, u0, v0, r, g, b, a);
            putVertex(x0, y1, u0, v1, r, g, b, a);
            putVertex(x1, y1, u1, v1, r, g, b, a);

            // Triangle 2
            putVertex(x0, y0, u0, v0, r, g, b, a);
            putVertex(x1, y1, u1, v1, r, g, b, a);
            putVertex(x1, y0, u1, v0, r, g, b, a);
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

        corners[0] = rotateX(x0, y0, centerX, centerY, cos, sin);
        corners[1] = rotateY(x0, y0, centerX, centerY, cos, sin);
        corners[2] = rotateX(x0, y1, centerX, centerY, cos, sin);
        corners[3] = rotateY(x0, y1, centerX, centerY, cos, sin);
        corners[4] = rotateX(x1, y1, centerX, centerY, cos, sin);
        corners[5] = rotateY(x1, y1, centerX, centerY, cos, sin);
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