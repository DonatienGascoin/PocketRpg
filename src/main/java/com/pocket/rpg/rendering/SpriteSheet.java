package com.pocket.rpg.rendering;

import lombok.Getter;

/**
 * Represents a sprite sheet - a single texture containing multiple sprite frames.
 * Provides utilities for creating sprites from specific regions.
 */
@Getter
public class SpriteSheet {

    private final Texture texture;
    private final int spriteWidth;
    private final int spriteHeight;
    private final int columns;
    private final int rows;
    private final int spacing;
    private final int totalFrames;

    /**
     * Creates a sprite sheet with uniform sprite sizes and no spacing.
     *
     * @param texture      The texture containing all sprites
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     */
    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight) {
        this(texture, spriteWidth, spriteHeight, 0);
    }

    /**
     * Creates a sprite sheet with uniform sprite sizes and spacing.
     *
     * @param texture      The texture containing all sprites
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     * @param spacing      Spacing between sprites in pixels
     */
    public SpriteSheet(Texture texture, int spriteWidth, int spriteHeight, int spacing) {
        this.texture = texture;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        this.spacing = spacing;

        // Calculate grid dimensions
        this.columns = (texture.getWidth() + spacing) / (spriteWidth + spacing);
        this.rows = (texture.getHeight() + spacing) / (spriteHeight + spacing);
        this.totalFrames = columns * rows;
    }

    /**
     * Creates a sprite from a specific frame in the sheet.
     *
     * @param frameIndex Frame index (0-based, left-to-right, top-to-bottom)
     * @param x          Screen X position
     * @param y          Screen Y position
     * @return A sprite representing the specified frame
     */
    public Sprite getSprite(int frameIndex, float x, float y) {
        return getSprite(frameIndex, x, y, spriteWidth, spriteHeight);
    }

    /**
     * Creates a sprite from a specific frame with custom screen size.
     *
     * @param frameIndex   Frame index (0-based)
     * @param x            Screen X position
     * @param y            Screen Y position
     * @param screenWidth  Width on screen
     * @param screenHeight Height on screen
     * @return A sprite representing the specified frame
     */
    public Sprite getSprite(int frameIndex, float x, float y, float screenWidth, float screenHeight) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            throw new IndexOutOfBoundsException(
                    "Frame index " + frameIndex + " out of bounds (0-" + (totalFrames - 1) + ")"
            );
        }

        int row = frameIndex / columns;
        int col = frameIndex % columns;

        int sheetX = col * (spriteWidth + spacing);
        int sheetY = row * (spriteHeight + spacing);

        return new Sprite(texture, x, y, screenWidth, screenHeight,
                sheetX, sheetY, spriteWidth, spriteHeight);
    }

    /**
     * Creates a sprite from grid coordinates.
     *
     * @param col Column index (0-based)
     * @param row Row index (0-based)
     * @param x   Screen X position
     * @param y   Screen Y position
     * @return A sprite at the specified grid position
     */
    public Sprite getSpriteAt(int col, int row, float x, float y) {
        return getSpriteAt(col, row, x, y, spriteWidth, spriteHeight);
    }

    /**
     * Creates a sprite from grid coordinates with custom screen size.
     *
     * @param col          Column index (0-based)
     * @param row          Row index (0-based)
     * @param x            Screen X position
     * @param y            Screen Y position
     * @param screenWidth  Width on screen
     * @param screenHeight Height on screen
     * @return A sprite at the specified grid position
     */
    public Sprite getSpriteAt(int col, int row, float x, float y, float screenWidth, float screenHeight) {
        if (col < 0 || col >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException(
                    "Grid position (" + col + ", " + row + ") out of bounds (0-" + (columns - 1) + ", 0-" + (rows - 1) + ")"
            );
        }

        int sheetX = col * (spriteWidth + spacing);
        int sheetY = row * (spriteHeight + spacing);

        return new Sprite(texture, x, y, screenWidth, screenHeight,
                sheetX, sheetY, spriteWidth, spriteHeight);
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

        int sheetX = col * (spriteWidth + spacing);
        int sheetY = row * (spriteHeight + spacing);

        sprite.setUVsFromPixels(sheetX, sheetY, spriteWidth, spriteHeight);
    }
}
