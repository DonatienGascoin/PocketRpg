package com.pocket.rpg.rendering.resources;

/**
 * Defines 9-slice border insets for a sprite.
 * <p>
 * All values are in pixels, measured inward from the sprite edges.
 * When rendering at a different size than the source sprite:
 * <ul>
 *   <li>Corners remain fixed size</li>
 *   <li>Edges stretch along one axis</li>
 *   <li>Center stretches or tiles in both directions</li>
 * </ul>
 * <p>
 * Example for a 32x32 button with 4px borders:
 * <pre>
 * +---+--------+---+
 * | TL|   TC   |TR |  <- top = 4px
 * +---+--------+---+
 * |   |        |   |
 * |ML |   MC   |MR |
 * |   |        |   |
 * +---+--------+---+
 * | BL|   BC   |BR |  <- bottom = 4px
 * +---+--------+---+
 *   ^            ^
 *   left = 4px   right = 4px
 * </pre>
 *
 * @see NineSlice
 */
public class NineSliceData {

    /**
     * Left border inset in pixels.
     */
    public int left = 0;

    /**
     * Right border inset in pixels.
     */
    public int right = 0;

    /**
     * Top border inset in pixels.
     */
    public int top = 0;

    /**
     * Bottom border inset in pixels.
     */
    public int bottom = 0;

    /**
     * Creates an empty 9-slice data (no slicing).
     */
    public NineSliceData() {
    }

    /**
     * Creates 9-slice data with uniform borders.
     *
     * @param border The border size in pixels for all sides
     */
    public NineSliceData(int border) {
        this.left = border;
        this.right = border;
        this.top = border;
        this.bottom = border;
    }

    /**
     * Creates 9-slice data with separate horizontal and vertical borders.
     *
     * @param horizontal The border size for left and right
     * @param vertical   The border size for top and bottom
     */
    public NineSliceData(int horizontal, int vertical) {
        this.left = horizontal;
        this.right = horizontal;
        this.top = vertical;
        this.bottom = vertical;
    }

    /**
     * Creates 9-slice data with individual border sizes.
     *
     * @param left   Left border in pixels
     * @param right  Right border in pixels
     * @param top    Top border in pixels
     * @param bottom Bottom border in pixels
     */
    public NineSliceData(int left, int right, int top, int bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    /**
     * Checks if all border values are non-negative.
     *
     * @return true if all borders are >= 0
     */
    public boolean isValid() {
        return left >= 0 && right >= 0 && top >= 0 && bottom >= 0;
    }

    /**
     * Checks if any border is set (non-zero).
     *
     * @return true if at least one border > 0
     */
    public boolean hasSlicing() {
        return left > 0 || right > 0 || top > 0 || bottom > 0;
    }

    /**
     * Checks if all borders are zero (no slicing).
     *
     * @return true if all borders are 0
     */
    public boolean isEmpty() {
        return left == 0 && right == 0 && top == 0 && bottom == 0;
    }

    /**
     * Gets the total horizontal border size.
     *
     * @return left + right
     */
    public int getHorizontalBorder() {
        return left + right;
    }

    /**
     * Gets the total vertical border size.
     *
     * @return top + bottom
     */
    public int getVerticalBorder() {
        return top + bottom;
    }

    /**
     * Creates a deep copy of this data.
     *
     * @return A new NineSliceData with the same values
     */
    public NineSliceData copy() {
        return new NineSliceData(left, right, top, bottom);
    }

    @Override
    public String toString() {
        return String.format("NineSliceData[L=%d, R=%d, T=%d, B=%d]",
                left, right, top, bottom);
    }
}
