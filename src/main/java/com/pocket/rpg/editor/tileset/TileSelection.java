package com.pocket.rpg.editor.tileset;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import lombok.Getter;

import java.util.List;

/**
 * Represents a tile selection from the palette.
 *
 * Can be:
 * - Single tile: 1x1 selection
 * - Multi-tile pattern: NxM selection for stamping
 *
 * Stores references to the source tileset so tiles can be
 * placed on any layer regardless of layer's spritesheet.
 */
@Getter
public class TileSelection {

    /** Name of the tileset this selection comes from */
    private final String tilesetName;

    /** Sprite dimensions in the source tileset */
    private final int spriteWidth;
    private final int spriteHeight;

    /** Width of selection in tiles */
    private final int width;

    /** Height of selection in tiles */
    private final int height;

    /**
     * Tile indices in row-major order.
     * Index -1 means empty/transparent tile in the pattern.
     */
    private final int[] tileIndices;

    /** Cached sprites for quick access */
    private final Sprite[] sprites;

    /**
     * Creates a single-tile selection.
     */
    public TileSelection(String tilesetName, int spriteWidth, int spriteHeight,
                         int tileIndex, Sprite sprite) {
        this.tilesetName = tilesetName;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        this.width = 1;
        this.height = 1;
        this.tileIndices = new int[] { tileIndex };
        this.sprites = new Sprite[] { sprite };
    }

    /**
     * Creates a multi-tile selection (pattern).
     *
     * @param tilesetName Source tileset
     * @param spriteWidth Sprite width in pixels
     * @param spriteHeight Sprite height in pixels
     * @param width Selection width in tiles
     * @param height Selection height in tiles
     * @param tileIndices Indices in row-major order (left-to-right, top-to-bottom)
     * @param sprites Corresponding sprites
     */
    public TileSelection(String tilesetName, int spriteWidth, int spriteHeight,
                         int width, int height, int[] tileIndices, Sprite[] sprites) {
        this.tilesetName = tilesetName;
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        this.width = width;
        this.height = height;
        this.tileIndices = tileIndices;
        this.sprites = sprites;
    }

    /**
     * Checks if this is a single tile selection.
     */
    public boolean isSingleTile() {
        return width == 1 && height == 1;
    }

    /**
     * Checks if this is a multi-tile pattern.
     */
    public boolean isPattern() {
        return width > 1 || height > 1;
    }

    /**
     * Gets the tile index at a position in the pattern.
     *
     * @param x X position (0 to width-1)
     * @param y Y position (0 to height-1)
     * @return Tile index, or -1 if empty/out of bounds
     */
    public int getTileIndex(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return -1;
        }
        return tileIndices[y * width + x];
    }

    /**
     * Gets the sprite at a position in the pattern.
     *
     * @param x X position (0 to width-1)
     * @param y Y position (0 to height-1)
     * @return Sprite, or null if empty/out of bounds
     */
    public Sprite getSprite(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null;
        }
        return sprites[y * width + x];
    }

    /**
     * Gets the first (or only) tile index.
     */
    public int getFirstTileIndex() {
        return tileIndices.length > 0 ? tileIndices[0] : -1;
    }

    /**
     * Gets the first (or only) sprite.
     */
    public Sprite getFirstSprite() {
        return sprites.length > 0 ? sprites[0] : null;
    }

    /**
     * Gets total number of tiles in this selection.
     */
    public int getTileCount() {
        return width * height;
    }

    /**
     * Gets a display string for the selection.
     */
    public String getDisplayString() {
        if (isSingleTile()) {
            return "Tile " + getFirstTileIndex();
        } else {
            return "Pattern " + width + "x" + height;
        }
    }

    @Override
    public String toString() {
        return String.format("TileSelection[tileset=%s, size=%dx%d, tiles=%d]",
                tilesetName, width, height, getTileCount());
    }

    // ========================================================================
    // BUILDER FOR MULTI-TILE SELECTIONS
    // ========================================================================

    /**
     * Builder for creating multi-tile selections from a rectangular region.
     */
    public static class Builder {
        private final String tilesetName;
        private final int spriteWidth;
        private final int spriteHeight;
        private int startX, startY;
        private int endX, endY;

        public Builder(String tilesetName, int spriteWidth, int spriteHeight) {
            this.tilesetName = tilesetName;
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
        }

        /**
         * Sets the selection region by tile grid coordinates in the palette.
         *
         * @param startX Start column (inclusive)
         * @param startY Start row (inclusive)
         * @param endX End column (inclusive)
         * @param endY End row (inclusive)
         */
        public Builder setRegion(int startX, int startY, int endX, int endY) {
            // Normalize so start <= end
            this.startX = Math.min(startX, endX);
            this.startY = Math.min(startY, endY);
            this.endX = Math.max(startX, endX);
            this.endY = Math.max(startY, endY);
            return this;
        }

        public TileSelection build() {
            TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(tilesetName);
            if (entry == null) {
                System.err.println("TileSelection.Builder: Tileset not found: " + tilesetName);
                return null;
            }

            SpriteSheet sheet = entry.getSpriteSheet();
            List<Sprite> allSprites = entry.getSprites();
            int tilesPerRow = sheet.getColumns();

            int width = endX - startX + 1;
            int height = endY - startY + 1;

            int[] indices = new int[width * height];
            Sprite[] sprites = new Sprite[width * height];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int paletteX = startX + x;
                    int paletteY = startY + y;
                    int tileIndex = paletteY * tilesPerRow + paletteX;

                    int arrayIndex = y * width + x;

                    if (tileIndex >= 0 && tileIndex < allSprites.size()) {
                        indices[arrayIndex] = tileIndex;
                        sprites[arrayIndex] = allSprites.get(tileIndex);
                    } else {
                        indices[arrayIndex] = -1;
                        sprites[arrayIndex] = null;
                    }
                }
            }

            return new TileSelection(tilesetName, spriteWidth, spriteHeight,
                    width, height, indices, sprites);
        }
    }
}