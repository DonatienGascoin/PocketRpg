package com.pocket.rpg.ui.text;

/**
 * Represents a single character glyph in a font atlas.
 * Contains all metrics needed for rendering and layout.
 *
 * Coordinate system uses TOP-LEFT origin:
 * - bearingY is distance from baseline UP to top of glyph
 * - In rendering, glyphs are positioned relative to text baseline
 */
public class Glyph {

    /** The character this glyph represents */
    public final int codepoint;

    /** Width of the glyph in pixels */
    public final int width;

    /** Height of the glyph in pixels */
    public final int height;

    /** Horizontal offset from cursor to left edge of glyph */
    public final int bearingX;

    /** Vertical offset from baseline to top edge of glyph */
    public final int bearingY;

    /** How far to advance cursor after this glyph (in pixels) */
    public final int advance;

    /** UV coordinates in atlas (0-1 range) */
    public final float u0, v0, u1, v1;

    public Glyph(int codepoint, int width, int height,
                 int bearingX, int bearingY, int advance,
                 float u0, float v0, float u1, float v1) {
        this.codepoint = codepoint;
        this.width = width;
        this.height = height;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
        this.advance = advance;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }

    /**
     * Checks if this is a whitespace character (no visible rendering).
     */
    public boolean isWhitespace() {
        return width == 0 || height == 0;
    }

    @Override
    public String toString() {
        char c = (char) codepoint;
        String charStr = Character.isISOControl(c) ? "0x" + Integer.toHexString(codepoint) : String.valueOf(c);
        return String.format("Glyph['%s' size=%dx%d bearing=(%d,%d) advance=%d]",
                charStr, width, height, bearingX, bearingY, advance);
    }
}