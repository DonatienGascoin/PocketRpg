package com.pocket.rpg.rendering.resources;

import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;

import java.util.*;

/**
 * A robust sprite sheet helper class that supports:
 * - offsets (header trimming)
 * - independent X/Y spacing
 * - automatic safe grid detection
 * - cached sprite extraction
 * <p>
 * Path tracking for serialization is handled automatically. When sprites are extracted
 * via {@link #getSprite(int)}, they are registered in {@link Assets#getPathForResource(Object)}
 * if the sheet itself is tracked.
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

    private final Map<Integer, Sprite> spriteCache = new HashMap<>();
    private final List<Sprite> allSprites = new ArrayList<>();

    // -------------------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------------------

    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight) {
        this(texture, spriteWidth, spriteHeight, 0, 0, 0, 0);
    }

    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight, int spacing) {
        this(texture, spriteWidth, spriteHeight, spacing, spacing, 0, 0);
    }

    /**
     * Main constructor: completely rewritten with safe grid detection.
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

        // --- Safe grid detection ---
        int usableWidth  = Math.max(0, texture.getWidth()  - offsetX);
        int usableHeight = Math.max(0, texture.getHeight() - offsetY);

        this.columns = computeColumns(usableWidth);
        this.rows    = computeRows(usableHeight);

        this.totalFrames = columns * rows;
    }

    // -------------------------------------------------------------------------------------
    // Grid Calculations (robust)
    // -------------------------------------------------------------------------------------

    private int computeColumns(int usableWidth) {
        int cols = 0;
        int x = 0;

        // Keep placing columns while there's room for a full sprite
        while (x + spriteWidth <= usableWidth) {
            cols++;
            x += spriteWidth + spacingX;
        }
        return cols;
    }

    private int computeRows(int usableHeight) {
        int r = 0;
        int y = 0;

        while (y + spriteHeight <= usableHeight) {
            r++;
            y += spriteHeight + spacingY;
        }
        return r;
    }

    // -------------------------------------------------------------------------------------
    // Sprite Access
    // -------------------------------------------------------------------------------------

    public Sprite getSprite(int frameIndex) {
        return getSprite(frameIndex, spriteWidth, spriteHeight);
    }

    public Sprite getSprite(int frameIndex, float width, float height) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException(
                    "Frame index " + frameIndex + " out of bounds (0-" + (totalFrames - 1) + ")"
            );
        }

        Sprite cached = spriteCache.get(frameIndex);
        if (cached != null) {
            if (cached.getWidth() != width || cached.getHeight() != height) {
                cached.setSize(width, height);
            }
            // Ensure cached sprite is registered (may have been cached before sheet was tracked)
            ensureSpriteRegistered(cached, frameIndex);
            return cached;
        }

        int row = frameIndex / columns;
        int col = frameIndex % columns;

        int px = offsetX + col * (spriteWidth + spacingX);
        int pyTop = offsetY + row * (spriteHeight + spacingY);

        // Convert top-based Y to bottom-based Y for renderer/UVs
        int py = texture.getHeight() - (pyTop + spriteHeight);

        Sprite sprite = new Sprite(texture, width, height,
                px, py, spriteWidth, spriteHeight,
                "Frame_" + frameIndex);

        // Register sprite in resourcePaths if sheet is tracked
        ensureSpriteRegistered(sprite, frameIndex);

        spriteCache.put(frameIndex, sprite);
        allSprites.add(sprite);

        return sprite;
    }

    /**
     * Ensures a sprite is registered in Assets.resourcePaths if the sheet is tracked.
     */
    private void ensureSpriteRegistered(Sprite sprite, int frameIndex) {
        // Only register if sheet is tracked and sprite is not already registered
        String sheetPath = Assets.getPathForResource(this);
        if (sheetPath != null && Assets.getPathForResource(sprite) == null) {
            String spritePath = sheetPath + AssetManager.SUB_ASSET_SEPARATOR + frameIndex;
            Assets.registerResource(sprite, spritePath);
        }
    }

    public Sprite getSpriteAt(int col, int row) {
        return getSpriteAt(col, row, spriteWidth, spriteHeight);
    }

    public Sprite getSpriteAt(int col, int row, float width, float height) {
        if (col < 0 || col >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException(
                    "Grid position (" + col + ", " + row + ") out of bounds " +
                            "(0-" + (columns - 1) + ", 0-" + (rows - 1) + ")"
            );
        }

        int frameIndex = row * columns + col;
        return getSprite(frameIndex, width, height);
    }

    // -------------------------------------------------------------------------------------
    // UV Updating
    // -------------------------------------------------------------------------------------

    public void updateSpriteFrame(Sprite sprite, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException(
                    "Frame index " + frameIndex + " out of bounds (0-" + (totalFrames - 1) + ")"
            );
        }

        int row = frameIndex / columns;
        int col = frameIndex % columns;

        int px = offsetX + col * (spriteWidth + spacingX);
        int py = offsetY + row * (spriteHeight + spacingY);

        sprite.setUVsFromPixels(px, py, spriteWidth, spriteHeight);
        sprite.setName("Frame_" + frameIndex);
    }

    // -------------------------------------------------------------------------------------
    // Pre-Generation
    // -------------------------------------------------------------------------------------

    public List<Sprite> generateAllSprites() {
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i);
            }
        }
        return new ArrayList<>(allSprites);
    }

    public List<Sprite> generateAllSprites(float width, float height) {
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i, width, height);
            }
        }
        return new ArrayList<>(allSprites);
    }

    // -------------------------------------------------------------------------------------
    // Cache Utilities
    // -------------------------------------------------------------------------------------

    public Sprite getCachedSprite(int frameIndex) {
        return spriteCache.get(frameIndex);
    }

    public List<Sprite> getAllSprites() {
        return new ArrayList<>(allSprites);
    }

    public void clearCache() {
        spriteCache.clear();
        allSprites.clear();
    }

    public int getCachedSpriteCount() {
        return spriteCache.size();
    }
}