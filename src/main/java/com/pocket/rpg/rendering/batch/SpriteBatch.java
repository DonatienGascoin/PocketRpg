package com.pocket.rpg.rendering.batch;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.rendering.resources.Sprite;
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
 * All submission types are normalized to {@link RenderableQuad} before rendering,
 * ensuring a single code path for vertex generation.
 * <p>
 * Uses world units for all position and size calculations.
 * Sprite dimensions come from {@link Sprite#getWorldWidth()} and {@link Sprite#getWorldHeight()}.
 */
public class SpriteBatch {

    // Maximum sprites per GPU batch (vertex buffer size)
    private final int maxBatchSize;

    // Submission buffers (unbounded) - different input types
    private final List<SpriteRendererSubmission> spriteRendererSubmissions = new ArrayList<>();
    private final List<TileSubmission> tileSubmissions = new ArrayList<>();
    private final List<SpriteSubmission> spriteSubmissions = new ArrayList<>();

    // Normalized quads for rendering (populated during processBatches)
    private final List<RenderableQuad> renderableQuads = new ArrayList<>();

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
     * Buffered submission from SpriteRenderer component.
     */
    private record SpriteRendererSubmission(
            SpriteRenderer spriteRenderer,
            Vector4f tintColor
    ) {}

    /**
     * Buffered tile submission (no rotation support).
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

    /**
     * Buffered sprite submission with full transform support.
     * Used for entities that need rotation but aren't SpriteRenderers.
     */
    private record SpriteSubmission(
            Sprite sprite,
            float x,
            float y,
            float width,
            float height,
            float rotation,    // Z rotation in degrees
            float originX,     // 0-1 normalized origin
            float originY,
            float zIndex,
            Vector4f tintColor
    ) {}

    // ========================================================================
    // NORMALIZED QUAD (single format for rendering)
    // ========================================================================

    /**
     * Normalized quad ready for sorting and rendering.
     * All submission types are converted to this format during processBatches().
     * <p>
     * This ensures a single code path for vertex generation regardless of input type.
     */
    private record RenderableQuad(
            int textureId,
            float x, float y,                          // World position
            float width, float height,                 // Size in world units
            float rotation,                            // Z rotation in degrees
            float originX, float originY,              // 0-1 normalized origin
            float u0, float v0, float u1, float v1,    // UVs
            float zIndex,
            float yPosition,                           // For depth sorting
            float r, float g, float b, float a         // Final tint (pre-multiplied)
    ) {}

    // ========================================================================
    // CONSTRUCTOR & INITIALIZATION
    // ========================================================================

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
    }

    // ========================================================================
    // BATCHING LIFECYCLE
    // ========================================================================

    /**
     * Begins a new batch frame.
     */
    public void begin() {
        if (isBatching) {
            throw new IllegalStateException("Already batching! Call end() first.");
        }

        spriteRendererSubmissions.clear();
        tileSubmissions.clear();
        spriteSubmissions.clear();
        renderableQuads.clear();
        drawCalls = 0;
        totalSprites = 0;
        isBatching = true;
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

    // ========================================================================
    // SUBMISSION METHODS
    // ========================================================================

    /**
     * Submits a SpriteRenderer to the batch.
     *
     * @param spriteRenderer The sprite renderer component
     * @param tintColor      Additional tint to apply
     */
    public void submit(SpriteRenderer spriteRenderer, Vector4f tintColor) {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        Sprite sprite = spriteRenderer.getSprite();
        if (sprite == null || sprite.getTexture() == null) {
            return;
        }

        spriteRendererSubmissions.add(new SpriteRendererSubmission(spriteRenderer, tintColor));
        totalSprites++;
    }

    /**
     * Submits all tiles from a tilemap chunk to the batch.
     *
     * @param tilemapRenderer The tilemap renderer
     * @param cx              Chunk X coordinate
     * @param cy              Chunk Y coordinate
     * @param tintColor       Tint to apply to all tiles
     */
    public void submit(TilemapRenderer tilemapRenderer, int cx, int cy, Vector4f tintColor) {
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
     * Draws a sprite directly without rotation support.
     * Useful for tiles, UI elements, etc.
     *
     * @param sprite Sprite to draw
     * @param x      World X position
     * @param y      World Y position
     * @param width  Width in world units
     * @param height Height in world units
     * @param zIndex Depth sorting index
     * @param tint   Tint color
     */
    public void submit(Sprite sprite, float x, float y, float width, float height, float zIndex, Vector4f tint) {
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
    public void submit(Sprite sprite, float x, float y, float width, float height, float zIndex) {
        submit(sprite, x, y, width, height, zIndex, new Vector4f(1f, 1f, 1f, 1f));
    }

    /**
     * Draws a sprite with full transform support (position, rotation, origin).
     * Use this for entities that need rotation but aren't SpriteRenderers.
     *
     * @param sprite   Sprite to draw
     * @param x        World X position
     * @param y        World Y position
     * @param width    Width in world units
     * @param height   Height in world units
     * @param rotation Z rotation in degrees
     * @param originX  Origin X (0-1, where 0=left, 0.5=center, 1=right)
     * @param originY  Origin Y (0-1, where 0=bottom, 0.5=center, 1=top)
     * @param zIndex   Depth sorting index
     * @param tint     Tint color
     */
    public void submit(Sprite sprite, float x, float y, float width, float height,
                       float rotation, float originX, float originY, float zIndex, Vector4f tint) {
        if (!isBatching) {
            throw new IllegalStateException("Not batching! Call begin() first.");
        }

        if (sprite == null || sprite.getTexture() == null) {
            return;
        }

        spriteSubmissions.add(new SpriteSubmission(
                sprite, x, y, width, height, rotation, originX, originY, zIndex, tint
        ));
        totalSprites++;
    }

    // ========================================================================
    // BATCH PROCESSING
    // ========================================================================

    /**
     * Converts all submissions to RenderableQuads, sorts globally, and renders in batches.
     */
    private void processBatches() {
        // Normalize all submissions to RenderableQuad format
        for (SpriteRendererSubmission sub : spriteRendererSubmissions) {
            renderableQuads.add(normalizeFromSpriteRenderer(sub));
        }

        for (TileSubmission sub : tileSubmissions) {
            renderableQuads.add(normalizeFromTile(sub));
        }

        for (SpriteSubmission sub : spriteSubmissions) {
            renderableQuads.add(normalizeFromSprite(sub));
        }

        if (renderableQuads.isEmpty()) {
            return;
        }

        // Global sort
        sortQuads(renderableQuads);

        // Render with auto-flush
        renderQuads(renderableQuads);
    }

    // ========================================================================
    // NORMALIZATION (convert submissions to RenderableQuad)
    // ========================================================================

    /**
     * Normalizes a SpriteRenderer submission to RenderableQuad.
     */
    private RenderableQuad normalizeFromSpriteRenderer(SpriteRendererSubmission sub) {
        SpriteRenderer sr = sub.spriteRenderer();
        Sprite sprite = sr.getSprite();
        Transform transform = sr.getGameObject().getTransform();

        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();

        float width = sprite.getWorldWidth() * scale.x;
        float height = sprite.getWorldHeight() * scale.y;

        // Pre-multiply tints
        Vector4f tint = sub.tintColor();
        Vector4f spriteTint = sr.getTintColor();

        return new RenderableQuad(
                sprite.getTexture().getTextureId(),
                pos.x, pos.y,
                width, height,
                transform.getRotation().z,
                sr.getEffectiveOriginX(), sr.getEffectiveOriginY(),
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                sr.getZIndex(),
                pos.y,
                spriteTint.x * tint.x,
                spriteTint.y * tint.y,
                spriteTint.z * tint.z,
                spriteTint.w * tint.w
        );
    }

    /**
     * Normalizes a tile submission to RenderableQuad.
     */
    private RenderableQuad normalizeFromTile(TileSubmission sub) {
        Sprite sprite = sub.sprite();
        Vector4f tint = sub.tintColor();

        return new RenderableQuad(
                sprite.getTexture().getTextureId(),
                sub.x(), sub.y(),
                sub.width(), sub.height(),
                0f,    // No rotation for tiles
                0f, 0f, // Origin at bottom-left
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                sub.zIndex(),
                sub.y(),
                tint.x, tint.y, tint.z, tint.w
        );
    }

    /**
     * Normalizes a sprite submission to RenderableQuad.
     */
    private RenderableQuad normalizeFromSprite(SpriteSubmission sub) {
        Sprite sprite = sub.sprite();
        Vector4f tint = sub.tintColor();

        return new RenderableQuad(
                sprite.getTexture().getTextureId(),
                sub.x(), sub.y(),
                sub.width(), sub.height(),
                sub.rotation(),
                sub.originX(), sub.originY(),
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                sub.zIndex(),
                sub.y(),
                tint.x, tint.y, tint.z, tint.w
        );
    }

    // ========================================================================
    // SORTING
    // ========================================================================

    /**
     * Sorts quads according to the current sorting strategy.
     */
    private void sortQuads(List<RenderableQuad> quads) {
        switch (sortingStrategy) {
            case TEXTURE_PRIORITY:
                // Z-index → Texture → Y-position
                quads.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex(), b.zIndex());
                    if (zCompare != 0) return zCompare;

                    int texCompare = Integer.compare(a.textureId(), b.textureId());
                    if (texCompare != 0) return texCompare;

                    return Float.compare(a.yPosition(), b.yPosition());
                });
                break;

            case DEPTH_PRIORITY:
                // Z-index → Y-position → Texture
                quads.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex(), b.zIndex());
                    if (zCompare != 0) return zCompare;

                    int yCompare = Float.compare(a.yPosition(), b.yPosition());
                    if (yCompare != 0) return yCompare;

                    return Integer.compare(a.textureId(), b.textureId());
                });
                break;

            case BALANCED:
                // Z-index → Texture (group nearby Y) → Y-position
                quads.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex(), b.zIndex());
                    if (zCompare != 0) return zCompare;

                    // Group sprites within 4 world units Y-distance by texture
                    float yDiff = Math.abs(a.yPosition() - b.yPosition());
                    if (yDiff > 4f) {
                        return Float.compare(a.yPosition(), b.yPosition());
                    }

                    int texCompare = Integer.compare(a.textureId(), b.textureId());
                    if (texCompare != 0) return texCompare;
                    return Float.compare(a.yPosition(), b.yPosition());
                });
                break;
        }
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders all quads, automatically flushing when buffer is full or texture changes.
     */
    private void renderQuads(List<RenderableQuad> quads) {
        int currentTextureId = -1;
        int spriteCountInBuffer = 0;

        for (RenderableQuad quad : quads) {
            // Check if we need to flush due to texture change
            boolean textureChanged = (quad.textureId() != currentTextureId && currentTextureId != -1);

            // Check if we need to flush due to buffer full
            boolean bufferFull = (spriteCountInBuffer >= maxBatchSize);

            if (textureChanged || bufferFull) {
                if (spriteCountInBuffer > 0) {
                    flushBuffer(currentTextureId, spriteCountInBuffer);
                    spriteCountInBuffer = 0;
                }
            }

            // Add quad vertices to buffer
            addQuadVertices(quad);

            currentTextureId = quad.textureId();
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

    // ========================================================================
    // VERTEX GENERATION (single unified method)
    // ========================================================================

    /**
     * Adds vertex data for a quad to the vertex buffer.
     * Handles rotation if non-zero, otherwise uses fast path.
     */
    private void addQuadVertices(RenderableQuad quad) {
        // Origin offset in world units
        float originOffsetX = quad.width() * quad.originX();
        float originOffsetY = quad.height() * quad.originY();

        // Quad corners (Y-up coordinate system)
        float x0 = quad.x() - originOffsetX;
        float y0 = quad.y() - originOffsetY;
        float x1 = quad.x() + (quad.width() - originOffsetX);
        float y1 = quad.y() + (quad.height() - originOffsetY);

        // Rotation
        float angle = (float) Math.toRadians(quad.rotation());

        if (angle != 0.0f) {
            // Rotated path
            float centerX = quad.x();
            float centerY = quad.y();

            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            // Rotate all 4 corners
            float blX = rotateX(x0, y0, centerX, centerY, cos, sin);
            float blY = rotateY(x0, y0, centerX, centerY, cos, sin);
            float tlX = rotateX(x0, y1, centerX, centerY, cos, sin);
            float tlY = rotateY(x0, y1, centerX, centerY, cos, sin);
            float trX = rotateX(x1, y1, centerX, centerY, cos, sin);
            float trY = rotateY(x1, y1, centerX, centerY, cos, sin);
            float brX = rotateX(x1, y0, centerX, centerY, cos, sin);
            float brY = rotateY(x1, y0, centerX, centerY, cos, sin);

            // Triangle 1: BL, TL, TR
            putVertex(blX, blY, quad.u0(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(tlX, tlY, quad.u0(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(trX, trY, quad.u1(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());

            // Triangle 2: BL, TR, BR
            putVertex(blX, blY, quad.u0(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(trX, trY, quad.u1(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(brX, brY, quad.u1(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
        } else {
            // Fast path - no rotation
            // Triangle 1: BL, TL, TR
            putVertex(x0, y0, quad.u0(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(x0, y1, quad.u0(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(x1, y1, quad.u1(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());

            // Triangle 2: BL, TR, BR
            putVertex(x0, y0, quad.u0(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(x1, y1, quad.u1(), quad.v1(), quad.r(), quad.g(), quad.b(), quad.a());
            putVertex(x1, y0, quad.u1(), quad.v0(), quad.r(), quad.g(), quad.b(), quad.a());
        }
    }

    /**
     * Writes a single vertex to the buffer.
     */
    private void putVertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        vertexBuffer
                .put(x).put(y)
                .put(u).put(v)
                .put(r).put(g).put(b).put(a);
    }

    // ========================================================================
    // ROTATION HELPERS
    // ========================================================================

    private float rotateX(float x, float y, float cx, float cy, float cos, float sin) {
        return cos * (x - cx) - sin * (y - cy) + cx;
    }

    private float rotateY(float x, float y, float cx, float cy, float cos, float sin) {
        return sin * (x - cx) + cos * (y - cy) + cy;
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Destroys OpenGL resources.
     */
    public void destroy() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vertexBuffer != null) {
            MemoryUtil.memFree(vertexBuffer);
        }
    }
}