package com.pocket.rpg.collision.trigger;

/**
 * Immutable coordinate for a tile position with elevation.
 * <p>
 * Used as keys in TriggerDataMap and for trigger position references.
 *
 * @param x         Tile X coordinate
 * @param y         Tile Y coordinate
 * @param elevation Floor/layer level (0 = ground, positive = upper floors, negative = basements)
 */
public record TileCoord(int x, int y, int elevation) {

    /**
     * Creates a coordinate at ground level (elevation 0).
     */
    public TileCoord(int x, int y) {
        this(x, y, 0);
    }

    /**
     * Packs coordinates into a single long for efficient map keys.
     * <p>
     * Format: bits 0-19 = x, bits 20-39 = y, bits 40-55 = elevation
     * Supports coordinates from -524288 to 524287 and elevations from -32768 to 32767.
     */
    public long pack() {
        return ((long) x & 0xFFFFF)
                | (((long) y & 0xFFFFF) << 20)
                | (((long) elevation & 0xFFFF) << 40);
    }

    /**
     * Unpacks a long back into coordinates.
     */
    public static TileCoord unpack(long packed) {
        int x = (int) (packed & 0xFFFFF);
        int y = (int) ((packed >> 20) & 0xFFFFF);
        int elev = (int) ((packed >> 40) & 0xFFFF);

        // Handle sign extension for negative values
        if (x >= 0x80000) x |= 0xFFF00000;
        if (y >= 0x80000) y |= 0xFFF00000;
        if (elev >= 0x8000) elev |= 0xFFFF0000;

        return new TileCoord(x, y, elev);
    }

    @Override
    public String toString() {
        if (elevation == 0) {
            return "(" + x + ", " + y + ")";
        }
        return "(" + x + ", " + y + ", elev=" + elevation + ")";
    }
}
