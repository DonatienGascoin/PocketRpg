package com.pocket.rpg.rendering.resources;

import lombok.Getter;

/**
 * A renderable 9-slice sprite that combines a source Sprite with NineSliceData.
 * <p>
 * Provides pre-computed UV coordinates for all 9 regions, ready for rendering.
 * The 9 regions are arranged as:
 * <pre>
 * +----+----+----+
 * | TL | TC | TR |
 * +----+----+----+
 * | ML | MC | MR |
 * +----+----+----+
 * | BL | BC | BR |
 * +----+----+----+
 * </pre>
 * <p>
 * Use {@link #getRegionUV(int)} to get UV coordinates for each region,
 * then render 9 quads with the appropriate sizes:
 * <ul>
 *   <li>Corners: Fixed size (sliceData.left x sliceData.top, etc.)</li>
 *   <li>Edges: Stretch/tile along one axis</li>
 *   <li>Center: Stretch/tile along both axes</li>
 * </ul>
 *
 * @see NineSliceData
 */
@Getter
public class NineSlice {

    // Region indices
    public static final int TOP_LEFT = 0;
    public static final int TOP_CENTER = 1;
    public static final int TOP_RIGHT = 2;
    public static final int MIDDLE_LEFT = 3;
    public static final int MIDDLE_CENTER = 4;
    public static final int MIDDLE_RIGHT = 5;
    public static final int BOTTOM_LEFT = 6;
    public static final int BOTTOM_CENTER = 7;
    public static final int BOTTOM_RIGHT = 8;

    private final Sprite sourceSprite;
    private final NineSliceData sliceData;

    /**
     * Pre-computed UV regions (9 sets of [u0, v0, u1, v1]).
     * Array indices match the region constants (TOP_LEFT, etc.).
     */
    private final float[][] regionUVs = new float[9][4];

    /**
     * Creates a NineSlice from a sprite and slice data.
     *
     * @param sourceSprite The source sprite to slice
     * @param sliceData    The border definitions
     * @throws IllegalArgumentException if sprite or sliceData is null
     */
    public NineSlice(Sprite sourceSprite, NineSliceData sliceData) {
        if (sourceSprite == null) {
            throw new IllegalArgumentException("Source sprite cannot be null");
        }
        if (sliceData == null) {
            throw new IllegalArgumentException("Slice data cannot be null");
        }

        this.sourceSprite = sourceSprite;
        this.sliceData = sliceData;

        computeRegionUVs();
    }

    /**
     * Computes UV coordinates for all 9 regions based on sprite UVs and slice data.
     */
    private void computeRegionUVs() {
        float spriteWidth = sourceSprite.getWidth();
        float spriteHeight = sourceSprite.getHeight();

        // Get sprite's UV bounds
        float baseU0 = sourceSprite.getU0();
        float baseV0 = sourceSprite.getV0();
        float baseU1 = sourceSprite.getU1();
        float baseV1 = sourceSprite.getV1();

        // Calculate UV span
        float uSpan = baseU1 - baseU0;
        float vSpan = baseV1 - baseV0;

        // Convert pixel borders to UV space
        float leftU = (sliceData.left / spriteWidth) * uSpan;
        float rightU = (sliceData.right / spriteWidth) * uSpan;
        float topV = (sliceData.top / spriteHeight) * vSpan;
        float bottomV = (sliceData.bottom / spriteHeight) * vSpan;

        // UV boundaries
        float u0 = baseU0;                  // Left edge
        float u1 = baseU0 + leftU;          // Left border edge
        float u2 = baseU1 - rightU;         // Right border edge
        float u3 = baseU1;                  // Right edge

        float v0 = baseV0;                  // Top edge
        float v1 = baseV0 + topV;           // Top border edge
        float v2 = baseV1 - bottomV;        // Bottom border edge
        float v3 = baseV1;                  // Bottom edge

        // Top row (V0 to V1)
        regionUVs[TOP_LEFT] = new float[]{u0, v0, u1, v1};
        regionUVs[TOP_CENTER] = new float[]{u1, v0, u2, v1};
        regionUVs[TOP_RIGHT] = new float[]{u2, v0, u3, v1};

        // Middle row (V1 to V2)
        regionUVs[MIDDLE_LEFT] = new float[]{u0, v1, u1, v2};
        regionUVs[MIDDLE_CENTER] = new float[]{u1, v1, u2, v2};
        regionUVs[MIDDLE_RIGHT] = new float[]{u2, v1, u3, v2};

        // Bottom row (V2 to V3)
        regionUVs[BOTTOM_LEFT] = new float[]{u0, v2, u1, v3};
        regionUVs[BOTTOM_CENTER] = new float[]{u1, v2, u2, v3};
        regionUVs[BOTTOM_RIGHT] = new float[]{u2, v2, u3, v3};
    }

    /**
     * Gets the UV coordinates for a specific region.
     *
     * @param region One of TOP_LEFT, TOP_CENTER, etc.
     * @return Array of [u0, v0, u1, v1]
     */
    public float[] getRegionUV(int region) {
        if (region < 0 || region > 8) {
            throw new IllegalArgumentException("Invalid region index: " + region);
        }
        return regionUVs[region];
    }

    /**
     * Gets the texture from the source sprite.
     *
     * @return The texture for rendering
     */
    public Texture getTexture() {
        return sourceSprite.getTexture();
    }

    /**
     * Gets the pixel width of the left border.
     */
    public int getLeftBorder() {
        return sliceData.left;
    }

    /**
     * Gets the pixel width of the right border.
     */
    public int getRightBorder() {
        return sliceData.right;
    }

    /**
     * Gets the pixel height of the top border.
     */
    public int getTopBorder() {
        return sliceData.top;
    }

    /**
     * Gets the pixel height of the bottom border.
     */
    public int getBottomBorder() {
        return sliceData.bottom;
    }

    /**
     * Gets the minimum renderable width (left + right borders).
     *
     * @return Minimum width in pixels
     */
    public int getMinWidth() {
        return sliceData.left + sliceData.right;
    }

    /**
     * Gets the minimum renderable height (top + bottom borders).
     *
     * @return Minimum height in pixels
     */
    public int getMinHeight() {
        return sliceData.top + sliceData.bottom;
    }

    /**
     * Gets the source sprite's pixel width.
     */
    public float getSpriteWidth() {
        return sourceSprite.getWidth();
    }

    /**
     * Gets the source sprite's pixel height.
     */
    public float getSpriteHeight() {
        return sourceSprite.getHeight();
    }

    @Override
    public String toString() {
        return String.format("NineSlice[sprite=%s, %s]",
                sourceSprite.getName(), sliceData);
    }
}
