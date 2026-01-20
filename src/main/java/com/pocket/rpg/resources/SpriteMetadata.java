package com.pocket.rpg.resources;

/**
 * Metadata for standalone sprite assets.
 * <p>
 * This class holds editor-configurable properties for sprites that are
 * persisted separately from the image files. Metadata is stored in
 * {@code gameData/.metadata/} using {@link AssetMetadata}.
 * <p>
 * Null values indicate "use default" - this allows the metadata file to
 * remain minimal and only store values that differ from defaults.
 * <p>
 * Example metadata file ({@code .metadata/sprites/player.png.meta}):
 * <pre>
 * {
 *   "pivotX": 0.5,
 *   "pivotY": 0.0
 * }
 * </pre>
 *
 * @see AssetMetadata
 */
public class SpriteMetadata {

    /**
     * Pivot X coordinate (0-1). Null means use default (0.5 = center).
     * <ul>
     *   <li>0.0 = left edge</li>
     *   <li>0.5 = center (default)</li>
     *   <li>1.0 = right edge</li>
     * </ul>
     */
    public Float pivotX;

    /**
     * Pivot Y coordinate (0-1). Null means use default (0.5 = center).
     * <ul>
     *   <li>0.0 = bottom edge</li>
     *   <li>0.5 = center (default)</li>
     *   <li>1.0 = top edge</li>
     * </ul>
     */
    public Float pivotY;

    /**
     * Optional per-sprite pixels-per-unit override.
     * Null means use the global PPU from RenderingConfig.
     */
    public Float pixelsPerUnitOverride;

    // Future fields can be added here:
    // public NineSliceData nineSlice;
    // public PhysicsShapeData physicsShape;

    /**
     * Creates empty metadata with all defaults.
     */
    public SpriteMetadata() {
    }

    /**
     * Creates metadata with pivot values.
     *
     * @param pivotX X pivot (0-1)
     * @param pivotY Y pivot (0-1)
     */
    public SpriteMetadata(float pivotX, float pivotY) {
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }

    /**
     * Checks if this metadata has any non-default values.
     * Used to determine if the metadata file should be created/kept.
     *
     * @return true if all values are null (defaults)
     */
    public boolean isEmpty() {
        return pivotX == null && pivotY == null && pixelsPerUnitOverride == null;
    }

    /**
     * Checks if pivot values are set.
     *
     * @return true if both pivotX and pivotY are non-null
     */
    public boolean hasPivot() {
        return pivotX != null && pivotY != null;
    }

    /**
     * Gets pivotX with fallback to default.
     *
     * @return pivotX value or 0.5 if null
     */
    public float getPivotXOrDefault() {
        return pivotX != null ? pivotX : 0.5f;
    }

    /**
     * Gets pivotY with fallback to default.
     *
     * @return pivotY value or 0.5 if null
     */
    public float getPivotYOrDefault() {
        return pivotY != null ? pivotY : 0.5f;
    }

    @Override
    public String toString() {
        return String.format("SpriteMetadata[pivot=(%.2f, %.2f), ppu=%s]",
                getPivotXOrDefault(), getPivotYOrDefault(),
                pixelsPerUnitOverride != null ? pixelsPerUnitOverride.toString() : "default");
    }
}
