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

    // Pivot settings
    @Getter
    private float defaultPivotX = 0.5f;
    @Getter
    private float defaultPivotY = 0.5f;
    private final Map<Integer, float[]> spritePivots = new LinkedHashMap<>(); // Per-sprite pivot overrides

    // 9-slice settings
    @Getter
    private NineSliceData defaultNineSlice = null;
    private final Map<Integer, NineSliceData> spriteNineSlices = new LinkedHashMap<>(); // Per-sprite 9-slice overrides

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

        // Apply pivot (per-sprite override or default)
        float[] pivot = getEffectivePivot(frameIndex);
        sprite.setPivot(pivot[0], pivot[1]);

        // Apply 9-slice data (per-sprite override or default)
        NineSliceData nineSlice = getEffectiveNineSlice(frameIndex);
        if (nineSlice != null) {
            sprite.setNineSliceData(nineSlice.copy());
        }

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
        // Ensure all sprites are cached
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i);
            }
        }
        // Return sprites in frame index order, not insertion order
        List<Sprite> result = new ArrayList<>(totalFrames);
        for (int i = 0; i < totalFrames; i++) {
            Sprite sprite = spriteCache.get(i);
            if (sprite != null) {
                result.add(sprite);
            }
        }
        return result;
    }

    public List<Sprite> generateAllSprites(float width, float height) {
        // Ensure all sprites are cached
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteCache.containsKey(i)) {
                getSprite(i, width, height);
            }
        }
        // Return sprites in frame index order, not insertion order
        List<Sprite> result = new ArrayList<>(totalFrames);
        for (int i = 0; i < totalFrames; i++) {
            Sprite sprite = spriteCache.get(i);
            if (sprite != null) {
                result.add(sprite);
            }
        }
        return result;
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

    // -------------------------------------------------------------------------------------
    // Pivot Management
    // -------------------------------------------------------------------------------------

    /**
     * Sets the default pivot for all sprites in this sheet.
     * Sprites without per-sprite overrides will use this pivot.
     */
    public void setDefaultPivot(float pivotX, float pivotY) {
        this.defaultPivotX = pivotX;
        this.defaultPivotY = pivotY;
    }

    /**
     * Sets a per-sprite pivot override.
     * @param frameIndex The sprite index
     * @param pivotX Pivot X (0-1)
     * @param pivotY Pivot Y (0-1)
     */
    public void setSpritePivot(int frameIndex, float pivotX, float pivotY) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            return;
        }
        spritePivots.put(frameIndex, new float[]{pivotX, pivotY});

        // Apply to cached sprite if exists
        Sprite cached = spriteCache.get(frameIndex);
        if (cached != null) {
            cached.setPivot(pivotX, pivotY);
        }
    }

    /**
     * Removes a per-sprite pivot override, reverting to the default.
     */
    public void removeSpritePivot(int frameIndex) {
        spritePivots.remove(frameIndex);

        // Revert cached sprite to default if exists
        Sprite cached = spriteCache.get(frameIndex);
        if (cached != null) {
            cached.setPivot(defaultPivotX, defaultPivotY);
        }
    }

    /**
     * Gets the effective pivot for a sprite (per-sprite override or default).
     * @return float array [pivotX, pivotY]
     */
    public float[] getEffectivePivot(int frameIndex) {
        float[] override = spritePivots.get(frameIndex);
        if (override != null) {
            return override;
        }
        return new float[]{defaultPivotX, defaultPivotY};
    }

    /**
     * Checks if a sprite has a per-sprite pivot override.
     */
    public boolean hasSpritePivotOverride(int frameIndex) {
        return spritePivots.containsKey(frameIndex);
    }

    /**
     * Gets all per-sprite pivot overrides.
     * @return Unmodifiable map of frameIndex -> [pivotX, pivotY]
     */
    public Map<Integer, float[]> getSpritePivots() {
        return Collections.unmodifiableMap(spritePivots);
    }

    /**
     * Applies the default pivot to all sprites in the sheet.
     * Does not affect per-sprite overrides.
     */
    public void applyDefaultPivotToAllSprites() {
        for (int i = 0; i < totalFrames; i++) {
            if (!spritePivots.containsKey(i)) {
                Sprite cached = spriteCache.get(i);
                if (cached != null) {
                    cached.setPivot(defaultPivotX, defaultPivotY);
                }
            }
        }
    }

    /**
     * Clears all per-sprite pivot overrides.
     */
    public void clearSpritePivots() {
        spritePivots.clear();
        applyDefaultPivotToAllSprites();
    }

    // -------------------------------------------------------------------------------------
    // 9-Slice Management
    // -------------------------------------------------------------------------------------

    /**
     * Sets the default 9-slice data for all sprites in this sheet.
     * Sprites without per-sprite overrides will use this data.
     */
    public void setDefaultNineSlice(NineSliceData nineSlice) {
        this.defaultNineSlice = nineSlice != null ? nineSlice.copy() : null;
    }

    /**
     * Sets a per-sprite 9-slice override.
     * @param frameIndex The sprite index
     * @param nineSlice The 9-slice data (null to remove override)
     */
    public void setSpriteNineSlice(int frameIndex, NineSliceData nineSlice) {
        if (frameIndex < 0 || frameIndex >= totalFrames) {
            return;
        }

        if (nineSlice == null || !nineSlice.hasSlicing()) {
            spriteNineSlices.remove(frameIndex);
        } else {
            spriteNineSlices.put(frameIndex, nineSlice.copy());
        }

        // Apply to cached sprite if exists
        Sprite cached = spriteCache.get(frameIndex);
        if (cached != null) {
            NineSliceData effective = getEffectiveNineSlice(frameIndex);
            cached.setNineSliceData(effective != null ? effective.copy() : null);
        }
    }

    /**
     * Removes a per-sprite 9-slice override, reverting to the default.
     */
    public void removeSpriteNineSlice(int frameIndex) {
        spriteNineSlices.remove(frameIndex);

        // Revert cached sprite to default if exists
        Sprite cached = spriteCache.get(frameIndex);
        if (cached != null) {
            cached.setNineSliceData(defaultNineSlice != null ? defaultNineSlice.copy() : null);
        }
    }

    /**
     * Gets the effective 9-slice data for a sprite (per-sprite override or default).
     * @return NineSliceData or null if no 9-slice is configured
     */
    public NineSliceData getEffectiveNineSlice(int frameIndex) {
        NineSliceData override = spriteNineSlices.get(frameIndex);
        if (override != null) {
            return override;
        }
        return defaultNineSlice;
    }

    /**
     * Checks if a sprite has a per-sprite 9-slice override.
     */
    public boolean hasSpriteNineSliceOverride(int frameIndex) {
        return spriteNineSlices.containsKey(frameIndex);
    }

    /**
     * Gets all per-sprite 9-slice overrides.
     * @return Unmodifiable map of frameIndex -> NineSliceData
     */
    public Map<Integer, NineSliceData> getSpriteNineSlices() {
        return Collections.unmodifiableMap(spriteNineSlices);
    }

    /**
     * Applies the default 9-slice to all sprites in the sheet.
     * Does not affect per-sprite overrides.
     */
    public void applyDefaultNineSliceToAllSprites() {
        for (int i = 0; i < totalFrames; i++) {
            if (!spriteNineSlices.containsKey(i)) {
                Sprite cached = spriteCache.get(i);
                if (cached != null) {
                    cached.setNineSliceData(defaultNineSlice != null ? defaultNineSlice.copy() : null);
                }
            }
        }
    }

    /**
     * Clears all per-sprite 9-slice overrides.
     */
    public void clearSpriteNineSlices() {
        spriteNineSlices.clear();
        applyDefaultNineSliceToAllSprites();
    }
}