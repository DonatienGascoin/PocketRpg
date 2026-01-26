package com.pocket.rpg.rendering.resources;

import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import com.pocket.rpg.resources.SpriteMetadata.GridSettings;
import com.pocket.rpg.resources.SpriteMetadata.PivotData;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper class for managing sprite grids from textures with multiple-mode metadata.
 * <p>
 * This class extracts grid calculation and sprite generation logic into a reusable
 * component that can be used by {@code SpriteLoader}, {@code TilesetRegistry}, and
 * other systems that need to work with sprite grids.
 * <p>
 * Key features:
 * <ul>
 *   <li>Lazy sprite generation - sprites are created on first access</li>
 *   <li>Per-sprite pivot and 9-slice support</li>
 *   <li>Automatic registration with Assets for path tracking</li>
 *   <li>Cache management for memory efficiency</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * Texture texture = Assets.load("player.png", Texture.class);
 * SpriteMetadata meta = AssetMetadata.load("player.png", SpriteMetadata.class);
 *
 * SpriteGrid grid = new SpriteGrid(texture, meta, "player.png");
 *
 * Sprite firstSprite = grid.getSprite(0);
 * List&lt;Sprite&gt; allSprites = grid.getAllSprites();
 * </pre>
 *
 * @see SpriteMetadata
 * @see SpriteMetadata.GridSettings
 */
@Getter
public class SpriteGrid {

    private final Texture texture;
    private final SpriteMetadata metadata;
    private final GridSettings grid;
    private final String basePath;

    private final int columns;
    private final int rows;
    private final int totalSprites;

    private final Map<Integer, Sprite> spriteCache = new HashMap<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates a SpriteGrid from a texture and metadata.
     *
     * @param texture  The source texture
     * @param metadata The sprite metadata (must have spriteMode == MULTIPLE and valid grid)
     * @throws IllegalArgumentException if metadata is null, not in multiple mode, or has no grid
     */
    public SpriteGrid(Texture texture, SpriteMetadata metadata) {
        this(texture, metadata, null);
    }

    // ========================================================================
    // FACTORY METHODS (for programmatic creation)
    // ========================================================================

    /**
     * Creates a SpriteGrid programmatically from a texture and grid dimensions.
     * <p>
     * This is a convenience method for creating sprite grids without a metadata file,
     * useful for testing, demos, or programmatically generated content.
     *
     * @param texture      The source texture
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     * @return A new SpriteGrid
     */
    public static SpriteGrid create(Texture texture, int spriteWidth, int spriteHeight) {
        return create(texture, spriteWidth, spriteHeight, 0, 0, 0, 0);
    }

    /**
     * Creates a SpriteGrid programmatically with spacing.
     *
     * @param texture       The source texture
     * @param spriteWidth   Width of each sprite in pixels
     * @param spriteHeight  Height of each sprite in pixels
     * @param uniformSpacing Spacing between sprites (both X and Y)
     * @return A new SpriteGrid
     */
    public static SpriteGrid create(Texture texture, int spriteWidth, int spriteHeight, int uniformSpacing) {
        return create(texture, spriteWidth, spriteHeight, uniformSpacing, uniformSpacing, 0, 0);
    }

    /**
     * Creates a SpriteGrid programmatically with full control over layout.
     *
     * @param texture      The source texture
     * @param spriteWidth  Width of each sprite in pixels
     * @param spriteHeight Height of each sprite in pixels
     * @param spacingX     Horizontal spacing between sprites
     * @param spacingY     Vertical spacing between sprites
     * @param offsetX      X offset from left edge of texture
     * @param offsetY      Y offset from top edge of texture
     * @return A new SpriteGrid
     */
    public static SpriteGrid create(Texture texture, int spriteWidth, int spriteHeight,
                                    int spacingX, int spacingY, int offsetX, int offsetY) {
        SpriteMetadata meta = new SpriteMetadata();
        meta.convertToMultiple(new GridSettings(spriteWidth, spriteHeight, spacingX, spacingY, offsetX, offsetY));
        return new SpriteGrid(texture, meta);
    }

    /**
     * Creates a SpriteGrid from a texture and metadata with path tracking.
     *
     * @param texture  The source texture
     * @param metadata The sprite metadata (must have spriteMode == MULTIPLE and valid grid)
     * @param basePath The base path for registering sprites with Assets (e.g., "spritesheets/player.png")
     * @throws IllegalArgumentException if metadata is null, not in multiple mode, or has no grid
     */
    public SpriteGrid(Texture texture, SpriteMetadata metadata, String basePath) {
        if (texture == null) {
            throw new IllegalArgumentException("Texture cannot be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        if (metadata.isSingle()) {
            throw new IllegalArgumentException("Metadata must be in MULTIPLE mode");
        }
        if (metadata.grid == null) {
            throw new IllegalArgumentException("Metadata must have grid settings");
        }

        this.texture = texture;
        this.metadata = metadata;
        this.grid = metadata.grid;
        this.basePath = basePath;

        // Calculate grid dimensions
        this.columns = grid.calculateColumns(texture.getWidth());
        this.rows = grid.calculateRows(texture.getHeight());
        this.totalSprites = columns * rows;
    }

    // ========================================================================
    // SPRITE ACCESS
    // ========================================================================

    /**
     * Gets a sprite by index, creating it if not cached.
     *
     * @param index The sprite index (0-based, row-major order)
     * @return The sprite at that index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public Sprite getSprite(int index) {
        if (index < 0 || index >= totalSprites) {
            throw new IndexOutOfBoundsException(
                    "Sprite index " + index + " out of bounds (0-" + (totalSprites - 1) + ")"
            );
        }

        return spriteCache.computeIfAbsent(index, this::createSprite);
    }

    /**
     * Gets a sprite by grid position, creating it if not cached.
     *
     * @param col Column index (0-based)
     * @param row Row index (0-based)
     * @return The sprite at that position
     * @throws IndexOutOfBoundsException if position is out of range
     */
    public Sprite getSpriteAt(int col, int row) {
        if (col < 0 || col >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException(
                    "Grid position (" + col + ", " + row + ") out of bounds " +
                            "(0-" + (columns - 1) + ", 0-" + (rows - 1) + ")"
            );
        }

        int index = row * columns + col;
        return getSprite(index);
    }

    /**
     * Gets all sprites in the grid, creating them if not cached.
     *
     * @return List of all sprites in frame index order
     */
    public List<Sprite> getAllSprites() {
        List<Sprite> sprites = new ArrayList<>(totalSprites);
        for (int i = 0; i < totalSprites; i++) {
            sprites.add(getSprite(i));
        }
        return sprites;
    }

    /**
     * Gets a cached sprite without creating it.
     *
     * @param index The sprite index
     * @return The cached sprite, or null if not yet created
     */
    public Sprite getCachedSprite(int index) {
        return spriteCache.get(index);
    }

    /**
     * Checks if a sprite at the given index is cached.
     *
     * @param index The sprite index
     * @return true if the sprite is cached
     */
    public boolean isCached(int index) {
        return spriteCache.containsKey(index);
    }

    /**
     * Gets the number of currently cached sprites.
     *
     * @return Number of cached sprites
     */
    public int getCachedCount() {
        return spriteCache.size();
    }

    // ========================================================================
    // SPRITE CREATION
    // ========================================================================

    /**
     * Creates a sprite at the given index.
     *
     * @param index The sprite index
     * @return The newly created sprite
     */
    private Sprite createSprite(int index) {
        int row = index / columns;
        int col = index % columns;

        int px = grid.offsetX + col * (grid.spriteWidth + grid.spacingX);
        int pyTop = grid.offsetY + row * (grid.spriteHeight + grid.spacingY);
        // Convert top-based Y to bottom-based Y for renderer/UVs
        int py = texture.getHeight() - (pyTop + grid.spriteHeight);

        Sprite sprite = new Sprite(
                texture,
                grid.spriteWidth, grid.spriteHeight,
                px, py, grid.spriteWidth, grid.spriteHeight,
                "Frame_" + index
        );

        // Apply pivot (per-sprite override or default)
        PivotData pivot = metadata.getEffectivePivot(index);
        sprite.setPivot(pivot.x, pivot.y);

        // Apply 9-slice data (per-sprite override or default)
        NineSliceData nineSlice = metadata.getEffectiveNineSlice(index);
        if (nineSlice != null) {
            sprite.setNineSliceData(nineSlice.copy());
        }

        // Apply PPU override
        if (metadata.pixelsPerUnitOverride != null) {
            sprite.setPixelsPerUnitOverride(metadata.pixelsPerUnitOverride);
        }

        // Register with Assets if we have a base path
        if (basePath != null) {
            String spritePath = basePath + AssetManager.SUB_ASSET_SEPARATOR + index;
            Assets.registerResource(sprite, spritePath);
        }

        return sprite;
    }

    // ========================================================================
    // GRID INFO
    // ========================================================================

    /**
     * Gets the width of each sprite in pixels.
     *
     * @return Sprite width in pixels
     */
    public int getSpriteWidth() {
        return grid.spriteWidth;
    }

    /**
     * Gets the height of each sprite in pixels.
     *
     * @return Sprite height in pixels
     */
    public int getSpriteHeight() {
        return grid.spriteHeight;
    }

    /**
     * Gets the horizontal spacing between sprites in pixels.
     *
     * @return Horizontal spacing in pixels
     */
    public int getSpacingX() {
        return grid.spacingX;
    }

    /**
     * Gets the vertical spacing between sprites in pixels.
     *
     * @return Vertical spacing in pixels
     */
    public int getSpacingY() {
        return grid.spacingY;
    }

    /**
     * Gets the X offset from the left edge of the texture.
     *
     * @return X offset in pixels
     */
    public int getOffsetX() {
        return grid.offsetX;
    }

    /**
     * Gets the Y offset from the top edge of the texture.
     *
     * @return Y offset in pixels
     */
    public int getOffsetY() {
        return grid.offsetY;
    }

    /**
     * Converts a grid position to a sprite index.
     *
     * @param col Column index
     * @param row Row index
     * @return The sprite index
     */
    public int positionToIndex(int col, int row) {
        return row * columns + col;
    }

    /**
     * Converts a sprite index to column position.
     *
     * @param index Sprite index
     * @return Column index
     */
    public int indexToColumn(int index) {
        return index % columns;
    }

    /**
     * Converts a sprite index to row position.
     *
     * @param index Sprite index
     * @return Row index
     */
    public int indexToRow(int index) {
        return index / columns;
    }

    // ========================================================================
    // PIVOT AND 9-SLICE ACCESS
    // ========================================================================

    /**
     * Gets the default pivot for sprites in this grid.
     *
     * @return Default pivot data
     */
    public PivotData getDefaultPivot() {
        return metadata.defaultPivot != null ? metadata.defaultPivot : new PivotData(0.5f, 0.5f);
    }

    /**
     * Gets the effective pivot for a sprite.
     *
     * @param index Sprite index
     * @return Pivot data (from override or default)
     */
    public PivotData getEffectivePivot(int index) {
        return metadata.getEffectivePivot(index);
    }

    /**
     * Gets the default 9-slice data for sprites in this grid.
     *
     * @return Default 9-slice data, or null if not set
     */
    public NineSliceData getDefaultNineSlice() {
        return metadata.defaultNineSlice;
    }

    /**
     * Gets the effective 9-slice data for a sprite.
     *
     * @param index Sprite index
     * @return 9-slice data, or null if not set
     */
    public NineSliceData getEffectiveNineSlice(int index) {
        return metadata.getEffectiveNineSlice(index);
    }

    /**
     * Checks if a sprite has a pivot override.
     *
     * @param index Sprite index
     * @return true if the sprite has a custom pivot
     */
    public boolean hasPivotOverride(int index) {
        return metadata.hasSpriteOverride(index) &&
                metadata.sprites.get(index).pivot != null;
    }

    /**
     * Checks if a sprite has a 9-slice override.
     *
     * @param index Sprite index
     * @return true if the sprite has custom 9-slice data
     */
    public boolean hasNineSliceOverride(int index) {
        return metadata.hasSpriteOverride(index) &&
                metadata.sprites.get(index).nineSlice != null;
    }

    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================

    /**
     * Clears the sprite cache, freeing memory.
     * Sprites will be recreated on next access.
     */
    public void clearCache() {
        spriteCache.clear();
    }

    /**
     * Pre-generates all sprites in the grid.
     * Use this when you know you'll need all sprites immediately.
     */
    public void pregenerate() {
        for (int i = 0; i < totalSprites; i++) {
            getSprite(i);
        }
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Updates the pivot for a cached sprite.
     * If the sprite is not cached, this has no effect.
     *
     * @param index  Sprite index
     * @param pivotX New pivot X
     * @param pivotY New pivot Y
     */
    public void updateSpritePivot(int index, float pivotX, float pivotY) {
        Sprite sprite = spriteCache.get(index);
        if (sprite != null) {
            sprite.setPivot(pivotX, pivotY);
        }
    }

    /**
     * Updates the 9-slice data for a cached sprite.
     * If the sprite is not cached, this has no effect.
     *
     * @param index     Sprite index
     * @param nineSlice New 9-slice data (null to remove)
     */
    public void updateSpriteNineSlice(int index, NineSliceData nineSlice) {
        Sprite sprite = spriteCache.get(index);
        if (sprite != null) {
            sprite.setNineSliceData(nineSlice != null ? nineSlice.copy() : null);
        }
    }

    @Override
    public String toString() {
        return String.format("SpriteGrid[%dx%d=%d sprites, %dx%d px, path=%s]",
                columns, rows, totalSprites,
                grid.spriteWidth, grid.spriteHeight,
                basePath != null ? basePath : "untracked");
    }
}
