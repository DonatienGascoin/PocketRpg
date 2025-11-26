package com.pocket.rpg.rendering;

import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.Transform;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL33.*;

/**
 * Batches sprites by texture to minimize draw calls.
 * Supports depth sorting, static sprites, and configurable sorting strategies.
 */
public class SpriteBatch {

    // Maximum sprites per batch
    private static final int MAX_BATCH_SIZE = 10000;

    // Batch data
    private final List<BatchItem> dynamicItems = new ArrayList<>(MAX_BATCH_SIZE);
    private final List<BatchItem> staticItems = new ArrayList<>(MAX_BATCH_SIZE);
    private final FloatBuffer vertexBuffer;

    // OpenGL resources
    private int vao;
    private int vbo;

    // Sorting strategy
    private SortingStrategy sortingStrategy = SortingStrategy.BALANCED;

    // Current batch state
    private boolean isBatching = false;
    private boolean staticBatchDirty = true;

    // Statistics
    private int drawCalls = 0;
    private int totalSprites = 0;
    private int staticSprites = 0;
    private int dynamicSprites = 0;

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
        SpriteRenderer spriteRenderer;
        int textureId;
        float zIndex;
        float yPosition;
        boolean isStatic;

        BatchItem(SpriteRenderer spriteRenderer, int textureId, float zIndex, float yPosition, boolean isStatic) {
            this.spriteRenderer = spriteRenderer;
            this.textureId = textureId;
            this.zIndex = zIndex;
            this.yPosition = yPosition;
            this.isStatic = isStatic;
        }
    }

    public SpriteBatch() {
        // Allocate vertex buffer (off-heap for performance)
        int bufferSize = MAX_BATCH_SIZE * VertexLayout.FLOATS_PER_SPRITE;
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
        int bufferSizeBytes = MAX_BATCH_SIZE * VertexLayout.BYTES_PER_SPRITE;
        glBufferData(GL_ARRAY_BUFFER, bufferSizeBytes, GL_DYNAMIC_DRAW);

        // Setup vertex attributes using VertexLayout
        VertexLayout.setupVertexAttributes();

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        System.out.println("SpriteBatch initialized:");
        System.out.println(VertexLayout.describe());
    }

    /**
     * Sets the sorting strategy.
     */
    public void setSortingStrategy(SortingStrategy strategy) {
        this.sortingStrategy = strategy;
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
        staticSprites = 0;
        dynamicSprites = 0;
        isBatching = true;
    }

    /**
     * Submits a sprite to the batch.
     */
    public void submit(SpriteRenderer spriteRenderer) {
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
        float zIndex = transform.getPosition().z;
        float yPosition = transform.getPosition().y;
        boolean isStatic = spriteRenderer.isStatic();

        // Create batch item
        BatchItem item = new BatchItem(spriteRenderer, textureId, zIndex, yPosition, isStatic);

        // Add to appropriate list
        if (isStatic) {
            // Static sprites only need to be added once
            if (staticBatchDirty) {
                staticItems.add(item);
                staticSprites++;
            }
        } else {
            dynamicItems.add(item);
            dynamicSprites++;
        }

        totalSprites++;
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
            flushItems(staticItems);
        }

        // Render dynamic sprites (always rebuild)
        if (!dynamicItems.isEmpty()) {
            sortItems(dynamicItems);
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
                items.sort((a, b) -> {
                    int zCompare = Float.compare(a.zIndex, b.zIndex);
                    if (zCompare != 0) return zCompare;

                    // Group sprites within 64 pixels Y-distance by texture
                    float yDiff = Math.abs(a.yPosition - b.yPosition);
                    if (yDiff > 64) {
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
            boolean needsFlush = false;
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
            addSpriteVertices(item.spriteRenderer);
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
     * Adds vertex data for a sprite to the vertex buffer.
     */
    private void addSpriteVertices(SpriteRenderer spriteRenderer) {
        Sprite sprite = spriteRenderer.getSprite();
        Transform transform = spriteRenderer.getGameObject().getTransform();

        Vector3f pos = transform.getPosition();
        Vector3f scale = transform.getScale();
        Vector3f rotation = transform.getRotation();

        // Calculate final dimensions
        float width = sprite.getWidth() * scale.x;
        float height = sprite.getHeight() * scale.y;

        // Calculate origin offset
        float originX = width * spriteRenderer.getOriginX();
        float originY = height * spriteRenderer.getOriginY();

        // Get UV coordinates
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        // Calculate corner positions (before rotation)
        float x0 = pos.x - originX;
        float y0 = pos.y - originY;
        float x1 = pos.x + (width - originX);
        float y1 = pos.y + (height - originY);

        // Apply rotation if needed
        float angle = (float) Math.toRadians(rotation.z);
        if (angle != 0) {
            float centerX = pos.x;
            float centerY = pos.y;

            // Rotate corners around center
            float[] corners = rotateQuad(x0, y0, x1, y1, centerX, centerY, angle);

            // Triangle 1
            vertexBuffer.put(corners[0]).put(corners[1]).put(u0).put(v1); // Top-left
            vertexBuffer.put(corners[2]).put(corners[3]).put(u0).put(v0); // Bottom-left
            vertexBuffer.put(corners[4]).put(corners[5]).put(u1).put(v0); // Bottom-right

            // Triangle 2
            vertexBuffer.put(corners[0]).put(corners[1]).put(u0).put(v1); // Top-left
            vertexBuffer.put(corners[4]).put(corners[5]).put(u1).put(v0); // Bottom-right
            vertexBuffer.put(corners[6]).put(corners[7]).put(u1).put(v1); // Top-right

        } else {
            // No rotation - simple quad

            // Triangle 1
            vertexBuffer.put(x0).put(y0).put(u0).put(v1); // Top-left
            vertexBuffer.put(x0).put(y1).put(u0).put(v0); // Bottom-left
            vertexBuffer.put(x1).put(y1).put(u1).put(v0); // Bottom-right

            // Triangle 2
            vertexBuffer.put(x0).put(y0).put(u0).put(v1); // Top-left
            vertexBuffer.put(x1).put(y1).put(u1).put(v0); // Bottom-right
            vertexBuffer.put(x1).put(y0).put(u1).put(v1); // Top-right
        }
    }

    /**
     * Rotates quad corners around a center point.
     */
    private float[] rotateQuad(float x0, float y0, float x1, float y1,
                               float centerX, float centerY, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float[] corners = new float[8];

        // Top-left
        corners[0] = rotateX(x0, y0, centerX, centerY, cos, sin);
        corners[1] = rotateY(x0, y0, centerX, centerY, cos, sin);

        // Bottom-left
        corners[2] = rotateX(x0, y1, centerX, centerY, cos, sin);
        corners[3] = rotateY(x0, y1, centerX, centerY, cos, sin);

        // Bottom-right
        corners[4] = rotateX(x1, y1, centerX, centerY, cos, sin);
        corners[5] = rotateY(x1, y1, centerX, centerY, cos, sin);

        // Top-right
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

    // Statistics
    public int getDrawCalls() { return drawCalls; }
    public int getTotalSprites() { return totalSprites; }
    public int getStaticSprites() { return staticSprites; }
    public int getDynamicSprites() { return dynamicSprites; }
    public SortingStrategy getSortingStrategy() { return sortingStrategy; }
}