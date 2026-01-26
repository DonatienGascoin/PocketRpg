package com.pocket.rpg.resources;

import com.pocket.rpg.rendering.resources.NineSliceData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata for sprite assets supporting both single and multiple (spritesheet) modes.
 * <p>
 * This class holds editor-configurable properties for sprites that are
 * persisted separately from the image files. Metadata is stored in
 * {@code gameData/.metadata/} using {@link AssetMetadata}.
 * <p>
 * <b>Sprite Modes:</b>
 * <ul>
 *   <li>{@link SpriteMode#SINGLE} - The texture is a single sprite (default)</li>
 *   <li>{@link SpriteMode#MULTIPLE} - The texture is a grid of sprites (spritesheet)</li>
 * </ul>
 * <p>
 * Null values indicate "use default" - this allows the metadata file to
 * remain minimal and only store values that differ from defaults.
 * <p>
 * Example single mode metadata ({@code .metadata/sprites/icon.png.meta}):
 * <pre>
 * {
 *   "spriteMode": "SINGLE",
 *   "pivotX": 0.5,
 *   "pivotY": 0.0
 * }
 * </pre>
 * <p>
 * Example multiple mode metadata ({@code .metadata/spritesheets/player.png.meta}):
 * <pre>
 * {
 *   "spriteMode": "MULTIPLE",
 *   "grid": {
 *     "spriteWidth": 16,
 *     "spriteHeight": 16,
 *     "spacingX": 0,
 *     "spacingY": 0,
 *     "offsetX": 0,
 *     "offsetY": 0
 *   },
 *   "defaultPivot": { "x": 0.5, "y": 0.0 },
 *   "sprites": {
 *     "0": { "pivot": { "x": 0.5, "y": 0.0 } },
 *     "5": { "pivot": { "x": 0.3, "y": 0.7 }, "nineSlice": { "left": 4, "right": 4, "top": 4, "bottom": 4 } }
 *   }
 * }
 * </pre>
 *
 * @see AssetMetadata
 */
public class SpriteMetadata {

    // ========================================================================
    // ENUMS AND INNER CLASSES
    // ========================================================================

    /**
     * Sprite mode determining how the texture is interpreted.
     */
    public enum SpriteMode {
        /**
         * The texture is a single sprite (default).
         */
        SINGLE,

        /**
         * The texture is a grid of sprites (spritesheet).
         */
        MULTIPLE
    }

    /**
     * Grid settings for multiple mode spritesheets.
     */
    public static class GridSettings {
        /** Width of each sprite in pixels. */
        public int spriteWidth = 16;

        /** Height of each sprite in pixels. */
        public int spriteHeight = 16;

        /** Horizontal spacing between sprites in pixels. */
        public int spacingX = 0;

        /** Vertical spacing between sprites in pixels. */
        public int spacingY = 0;

        /** X offset from the left edge of the texture in pixels. */
        public int offsetX = 0;

        /** Y offset from the top edge of the texture in pixels. */
        public int offsetY = 0;

        public GridSettings() {
        }

        public GridSettings(int spriteWidth, int spriteHeight) {
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
        }

        public GridSettings(int spriteWidth, int spriteHeight, int spacingX, int spacingY, int offsetX, int offsetY) {
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
            this.spacingX = spacingX;
            this.spacingY = spacingY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        /**
         * Creates a copy of this grid settings.
         */
        public GridSettings copy() {
            return new GridSettings(spriteWidth, spriteHeight, spacingX, spacingY, offsetX, offsetY);
        }

        /**
         * Calculates the number of columns that fit in the given texture width.
         */
        public int calculateColumns(int textureWidth) {
            int usableWidth = Math.max(0, textureWidth - offsetX);
            int cols = 0;
            int x = 0;
            while (x + spriteWidth <= usableWidth) {
                cols++;
                x += spriteWidth + spacingX;
            }
            return cols;
        }

        /**
         * Calculates the number of rows that fit in the given texture height.
         */
        public int calculateRows(int textureHeight) {
            int usableHeight = Math.max(0, textureHeight - offsetY);
            int rows = 0;
            int y = 0;
            while (y + spriteHeight <= usableHeight) {
                rows++;
                y += spriteHeight + spacingY;
            }
            return rows;
        }

        /**
         * Calculates the total number of sprites for the given texture dimensions.
         */
        public int calculateTotalSprites(int textureWidth, int textureHeight) {
            return calculateColumns(textureWidth) * calculateRows(textureHeight);
        }

        @Override
        public String toString() {
            return String.format("GridSettings[%dx%d, spacing=(%d,%d), offset=(%d,%d)]",
                    spriteWidth, spriteHeight, spacingX, spacingY, offsetX, offsetY);
        }
    }

    /**
     * Pivot data with x and y coordinates.
     */
    public static class PivotData {
        /** X coordinate (0-1). 0 = left, 0.5 = center, 1 = right. */
        public float x = 0.5f;

        /** Y coordinate (0-1). 0 = bottom, 0.5 = center, 1 = top. */
        public float y = 0.5f;

        public PivotData() {
        }

        public PivotData(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public PivotData copy() {
            return new PivotData(x, y);
        }

        @Override
        public String toString() {
            return String.format("PivotData[%.2f, %.2f]", x, y);
        }
    }

    /**
     * Per-sprite override data for multiple mode.
     */
    public static class SpriteOverride {
        /** Optional pivot override. Null means use default. */
        public PivotData pivot;

        /** Optional 9-slice override. Null means use default. */
        public NineSliceData nineSlice;

        public SpriteOverride() {
        }

        public SpriteOverride(PivotData pivot, NineSliceData nineSlice) {
            this.pivot = pivot;
            this.nineSlice = nineSlice;
        }

        /**
         * Checks if this override has any data.
         */
        public boolean isEmpty() {
            return pivot == null && (nineSlice == null || nineSlice.isEmpty());
        }

        public SpriteOverride copy() {
            return new SpriteOverride(
                    pivot != null ? pivot.copy() : null,
                    nineSlice != null ? nineSlice.copy() : null
            );
        }
    }

    // ========================================================================
    // COMMON FIELDS (BOTH MODES)
    // ========================================================================

    /**
     * Sprite mode. Null or SINGLE means single sprite mode.
     */
    public SpriteMode spriteMode;

    /**
     * Optional per-sprite pixels-per-unit override.
     * Null means use the global PPU from RenderingConfig.
     */
    public Float pixelsPerUnitOverride;

    // ========================================================================
    // SINGLE MODE FIELDS
    // ========================================================================

    /**
     * Pivot X coordinate (0-1) for single mode. Null means use default (0.5 = center).
     * <ul>
     *   <li>0.0 = left edge</li>
     *   <li>0.5 = center (default)</li>
     *   <li>1.0 = right edge</li>
     * </ul>
     */
    public Float pivotX;

    /**
     * Pivot Y coordinate (0-1) for single mode. Null means use default (0.5 = center).
     * <ul>
     *   <li>0.0 = bottom edge</li>
     *   <li>0.5 = center (default)</li>
     *   <li>1.0 = top edge</li>
     * </ul>
     */
    public Float pivotY;

    /**
     * 9-slice border data for single mode scalable UI sprites.
     * Null means no 9-slice (render as normal sprite).
     */
    public NineSliceData nineSlice;

    // ========================================================================
    // MULTIPLE MODE FIELDS
    // ========================================================================

    /**
     * Grid settings for multiple mode. Required when spriteMode is MULTIPLE.
     */
    public GridSettings grid;

    /**
     * Default pivot for all sprites in multiple mode.
     * Individual sprites can override this in the {@link #sprites} map.
     */
    public PivotData defaultPivot;

    /**
     * Default 9-slice data for all sprites in multiple mode.
     * Individual sprites can override this in the {@link #sprites} map.
     */
    public NineSliceData defaultNineSlice;

    /**
     * Per-sprite overrides for multiple mode.
     * Keys are sprite indices (0-based). Only sprites with overrides need entries.
     */
    public Map<Integer, SpriteOverride> sprites;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates empty metadata with all defaults (single mode).
     */
    public SpriteMetadata() {
    }

    /**
     * Creates single mode metadata with pivot values.
     *
     * @param pivotX X pivot (0-1)
     * @param pivotY Y pivot (0-1)
     */
    public SpriteMetadata(float pivotX, float pivotY) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    /**
     * Creates multiple mode metadata with grid settings.
     *
     * @param grid Grid settings for the spritesheet
     */
    public SpriteMetadata(GridSettings grid) {
        this.spriteMode = SpriteMode.MULTIPLE;
        this.grid = grid;
    }

    // ========================================================================
    // MODE HELPERS
    // ========================================================================

    /**
     * Checks if this is single mode (or no mode set, which defaults to single).
     */
    public boolean isSingle() {
        return spriteMode == null || spriteMode == SpriteMode.SINGLE;
    }

    /**
     * Checks if this is multiple mode (spritesheet).
     */
    public boolean isMultiple() {
        return spriteMode == SpriteMode.MULTIPLE;
    }

    /**
     * Gets the sprite mode, defaulting to SINGLE if null.
     */
    public SpriteMode getSpriteModeOrDefault() {
        return spriteMode != null ? spriteMode : SpriteMode.SINGLE;
    }

    // ========================================================================
    // SINGLE MODE HELPERS
    // ========================================================================

    /**
     * Checks if this metadata has any non-default values (for single mode).
     * Used to determine if the metadata file should be created/kept.
     *
     * @return true if all values are null/empty (defaults)
     */
    public boolean isEmpty() {
        if (isMultiple()) {
            return grid == null && defaultPivot == null && defaultNineSlice == null
                    && (sprites == null || sprites.isEmpty());
        }
        return pivotX == null && pivotY == null && pixelsPerUnitOverride == null
                && (nineSlice == null || nineSlice.isEmpty());
    }

    /**
     * Checks if 9-slice data is set and has actual borders (single mode).
     *
     * @return true if nineSlice is non-null and has at least one border > 0
     */
    public boolean hasNineSlice() {
        return nineSlice != null && nineSlice.hasSlicing();
    }

    /**
     * Checks if pivot values are set (single mode).
     *
     * @return true if both pivotX and pivotY are non-null
     */
    public boolean hasPivot() {
        return pivotX != null && pivotY != null;
    }

    /**
     * Gets pivotX with fallback to default (single mode).
     *
     * @return pivotX value or 0.5 if null
     */
    public float getPivotXOrDefault() {
        return pivotX != null ? pivotX : 0.5f;
    }

    /**
     * Gets pivotY with fallback to default (single mode).
     *
     * @return pivotY value or 0.5 if null
     */
    public float getPivotYOrDefault() {
        return pivotY != null ? pivotY : 0.5f;
    }

    // ========================================================================
    // MULTIPLE MODE HELPERS
    // ========================================================================

    /**
     * Gets the effective pivot for a sprite in multiple mode.
     *
     * @param spriteIndex The sprite index
     * @return Pivot data (from override or default)
     */
    public PivotData getEffectivePivot(int spriteIndex) {
        // Check for per-sprite override
        if (sprites != null) {
            SpriteOverride override = sprites.get(spriteIndex);
            if (override != null && override.pivot != null) {
                return override.pivot;
            }
        }
        // Fall back to default
        if (defaultPivot != null) {
            return defaultPivot;
        }
        // Ultimate default
        return new PivotData(0.5f, 0.5f);
    }

    /**
     * Gets the effective 9-slice data for a sprite in multiple mode.
     *
     * @param spriteIndex The sprite index
     * @return NineSliceData or null if no 9-slice configured
     */
    public NineSliceData getEffectiveNineSlice(int spriteIndex) {
        // Check for per-sprite override
        if (sprites != null) {
            SpriteOverride override = sprites.get(spriteIndex);
            if (override != null && override.nineSlice != null) {
                return override.nineSlice;
            }
        }
        // Fall back to default
        return defaultNineSlice;
    }

    /**
     * Sets or updates a sprite override in multiple mode.
     * Creates the sprites map if it doesn't exist.
     *
     * @param spriteIndex The sprite index
     * @param pivot Pivot override (null to keep existing or use default)
     * @param nineSlice NineSlice override (null to keep existing or use default)
     */
    public void setSpriteOverride(int spriteIndex, PivotData pivot, NineSliceData nineSlice) {
        if (sprites == null) {
            sprites = new LinkedHashMap<>();
        }
        SpriteOverride override = sprites.computeIfAbsent(spriteIndex, k -> new SpriteOverride());
        if (pivot != null) {
            override.pivot = pivot.copy();
        }
        if (nineSlice != null) {
            override.nineSlice = nineSlice.copy();
        }
        // Remove empty overrides
        if (override.isEmpty()) {
            sprites.remove(spriteIndex);
        }
    }

    /**
     * Removes a sprite override, reverting to defaults.
     *
     * @param spriteIndex The sprite index
     */
    public void removeSpriteOverride(int spriteIndex) {
        if (sprites != null) {
            sprites.remove(spriteIndex);
        }
    }

    /**
     * Checks if a sprite has any overrides.
     *
     * @param spriteIndex The sprite index
     * @return true if the sprite has pivot or 9-slice overrides
     */
    public boolean hasSpriteOverride(int spriteIndex) {
        if (sprites == null) {
            return false;
        }
        SpriteOverride override = sprites.get(spriteIndex);
        return override != null && !override.isEmpty();
    }

    /**
     * Calculates the total number of sprites for the given texture dimensions.
     * Only valid for multiple mode.
     *
     * @param textureWidth Texture width in pixels
     * @param textureHeight Texture height in pixels
     * @return Number of sprites, or 1 if single mode or no grid
     */
    public int calculateSpriteCount(int textureWidth, int textureHeight) {
        if (!isMultiple() || grid == null) {
            return 1;
        }
        return grid.calculateTotalSprites(textureWidth, textureHeight);
    }

    // ========================================================================
    // CONVERSION HELPERS
    // ========================================================================

    /**
     * Converts this metadata to multiple mode with the given grid settings.
     * Preserves existing pivot as the default pivot.
     *
     * @param gridSettings The grid settings for the spritesheet
     */
    public void convertToMultiple(GridSettings gridSettings) {
        this.spriteMode = SpriteMode.MULTIPLE;
        this.grid = gridSettings;

        // Convert single mode pivot to default pivot
        if (pivotX != null || pivotY != null) {
            this.defaultPivot = new PivotData(getPivotXOrDefault(), getPivotYOrDefault());
            this.pivotX = null;
            this.pivotY = null;
        }

        // Convert single mode 9-slice to default 9-slice
        if (nineSlice != null) {
            this.defaultNineSlice = nineSlice;
            this.nineSlice = null;
        }
    }

    /**
     * Converts this metadata to single mode.
     * Uses sprite 0's effective pivot and 9-slice as the single mode values.
     */
    public void convertToSingle() {
        // Get sprite 0's effective values before clearing
        PivotData pivot0 = getEffectivePivot(0);
        NineSliceData nineSlice0 = getEffectiveNineSlice(0);

        // Clear multiple mode fields
        this.spriteMode = SpriteMode.SINGLE;
        this.grid = null;
        this.sprites = null;
        this.defaultPivot = null;
        this.defaultNineSlice = null;

        // Set single mode values from sprite 0
        this.pivotX = pivot0.x;
        this.pivotY = pivot0.y;
        this.nineSlice = nineSlice0 != null ? nineSlice0.copy() : null;
    }

    // ========================================================================
    // OBJECT OVERRIDES
    // ========================================================================

    @Override
    public String toString() {
        if (isMultiple()) {
            int spriteCount = sprites != null ? sprites.size() : 0;
            return String.format("SpriteMetadata[MULTIPLE, grid=%s, overrides=%d]",
                    grid, spriteCount);
        }
        return String.format("SpriteMetadata[SINGLE, pivot=(%.2f, %.2f), ppu=%s]",
                getPivotXOrDefault(), getPivotYOrDefault(),
                pixelsPerUnitOverride != null ? pixelsPerUnitOverride.toString() : "default");
    }
}
