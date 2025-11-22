package com.pocket.rpg.rendering;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a sprite sheet - a single texture containing multiple sprite frames.
 * Provides utilities for creating sprites from specific regions with support for
 * independent X/Y spacing and offsets, plus sprite caching for performance.
 *
 * Now creates Sprites without position parameters since Sprites no longer hold position.
 */
@Getter
public class SpriteSheet {

    private final Texture texture;
    private final int spriteWidth;
    private final int spriteHeight;
    private final int columns;
    private final int rows;
    private final int spacingX;
    private final int spacingY;
    private final int offsetX;
    private final int offsetY;
    private final int totalFrames;

    // Cache for created sprites to avoid recreating them
    private final Map<Integer, Sprite> spriteCache;

    // List of all sprites for easy iteration
    private final List<Sprite> allSprites;

    /**
     * Creates a sprite sheet with uniform sprite sizes and no spacing or offset.
     *
     * @param texture      The texture containing all sprites
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     */
    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight) {
        this(texture, spriteWidth, spriteHeight, 0, 0, 0, 0);
    }

    /**
     * Creates a sprite sheet with uniform sprite sizes and uniform spacing.
     *
     * @param texture      The texture containing all sprites
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     * @param spacing      Spacing between sprites in pixels (both X and Y)
     */
    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight, int spacing) {
        this(texture, spriteWidth, spriteHeight, spacing, spacing, 0, 0);
    }

    /**
     * Creates a sprite sheet with independent X/Y spacing and offsets.
     *
     * @param texture      The texture containing all sprites
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     * @param spacingX     Horizontal spacing between sprites in pixels
     * @param spacingY     Vertical spacing between sprites in pixels
     * @param offsetX      Initial horizontal offset from texture edge in pixels
     * @param offsetY      Initial vertical offset from texture edge in pixels
     */
    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight,
                       int spacingX, int spacingY, int offsetX, int offsetY) {
        this.texture = texture;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        this.spacingX = spacingX;
        this.spacingY = spacingY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.spriteCache = new HashMap<>();
        this.allSprites = new ArrayList<>();

        // Calculate grid dimensions accounting for offset
        int availableWidth = texture.getWidth() - offsetX;
        int availableHeight = texture.getHeight() - offsetY;

        this.columns = (availableWidth + spacingX) / (spriteWidth + spacingX);
        this.rows = (availableHeight + spacingY) / (spriteHeight + spacingY);
        this.totalFrames = columns * rows;
    }

    /**
     * Creates a sprite from a specific frame in the sheet.
     * Uses caching - subsequent calls with the same frame index return the same sprite instance.
     *
     * @param frameIndex Frame index (0-based, left-to-right, top-to-bottom)
     * @return A sprite representing the specified frame
     */
    public Sprite getSprite(int frameIndex) {
        return getSprite(frameIndex, spriteWidth, spriteHeight);
    }

    /**
     * Creates a sprite from a specific frame with custom size.
     * Uses caching - sprites are cached by frame index only.
     *
     * @param frameIndex Frame index (0-based)
     * @param width      Sprite width
     * @param height     Sprite height
     * @return A sprite representing the specified frame
     */
    public Sprite getSprite(int frameIndex, float width, float height) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException(
                    "Frame index " + frameIndex + " out of bounds (0-" + (totalFrames - 1) + ")"
            );
        }

        // Check cache first
        Sprite cachedSprite = spriteCache.get(frameIndex);
        if (cachedSprite != null) {
            // Update size if different
            if (cachedSprite.getWidth() != width || cachedSprite.getHeight() != height) {
                cachedSprite.setSize(width, height);
            }
            return cachedSprite;
        }

        // Calculate grid position
        int row = frameIndex / columns;
        int col = frameIndex % columns;

        int sheetX = offsetX + col * (spriteWidth + spacingX);
        int sheetY = offsetY + row * (spriteHeight + spacingY);

        Sprite newSprite = new Sprite(texture, width, height,
                sheetX, sheetY, spriteWidth, spriteHeight,
                "Frame_" + frameIndex);

        // Cache the sprite
        spriteCache.put(frameIndex, newSprite);
        allSprites.add(newSprite);

        return newSprite;
    }

    /**
     * Creates a sprite from grid coordinates.
     *
     * @param col Column index (0-based)
     * @param row Row index (0-based)
     * @return A sprite at the specified grid position
     */
    public Sprite getSpriteAt(int col, int row) {
        return getSpriteAt(col, row, spriteWidth, spriteHeight);
    }

    /**
     * Creates a sprite from grid coordinates with custom size.
     *
     * @param col    Column index (0-based)
     * @param row    Row index (0-based)
     * @param width  Sprite width
     * @param height Sprite height
     * @return A sprite at the specified grid position
     */
    public Sprite getSpriteAt(int col, int row, float width, float height) {
        if (col < 0 || col >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException(
                    "Grid position (" + col + ", " + row + ") out of bounds (0-" + (columns - 1) + ", 0-" + (rows - 1) + ")"
            );
        }

        int frameIndex = row * columns + col;
        return getSprite(frameIndex, width, height);
    }

    /**
     * Updates a sprite's UVs to show a different frame.
     *
     * @param sprite     The sprite to update
     * @param frameIndex The frame to display
     */
    public void updateSpriteFrame(Sprite sprite, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException(
                    "Frame index " + frameIndex + " out of bounds (0-" + (totalFrames - 1) + ")"
            );
        }

        int row = frameIndex / columns;
        int col = frameIndex % columns;

        int sheetX = offsetX + col * (spriteWidth + spacingX);
        int sheetY = offsetY + row * (spriteHeight + spacingY);

        sprite.setUVsFromPixels(sheetX, sheetY, spriteWidth, spriteHeight);
        sprite.setName("Frame_" + frameIndex);
    }

    /**
     * Pre-generates all sprites and caches them.
     * Useful for animation systems that need quick access to all frames.
     *
     * @return List of all generated sprites
     */
    public List<Sprite> generateAllSprites() {
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i);
            }
        }
        return new ArrayList<>(allSprites);
    }

    /**
     * Pre-generates all sprites with custom size and caches them.
     *
     * @param width  Sprite width
     * @param height Sprite height
     * @return List of all generated sprites
     */
    public List<Sprite> generateAllSprites(float width, float height) {
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i, width, height);
            }
        }
        return new ArrayList<>(allSprites);
    }

    /**
     * Gets a specific cached sprite by frame index.
     * Returns null if the sprite hasn't been created yet.
     *
     * @param frameIndex Frame index
     * @return The cached sprite, or null if not yet created
     */
    public Sprite getCachedSprite(int frameIndex) {
        return spriteCache.get(frameIndex);
    }

    /**
     * Gets all currently cached sprites.
     *
     * @return List of all cached sprites
     */
    public List<Sprite> getAllSprites() {
        return new ArrayList<>(allSprites);
    }

    /**
     * Clears the sprite cache. Useful for memory management.
     * Note: This doesn't destroy the sprites, just removes them from the cache.
     */
    public void clearCache() {
        spriteCache.clear();
        allSprites.clear();
    }

    /**
     * Gets the number of sprites currently in the cache.
     *
     * @return Number of cached sprites
     */
    public int getCachedSpriteCount() {
        return spriteCache.size();
    }
}