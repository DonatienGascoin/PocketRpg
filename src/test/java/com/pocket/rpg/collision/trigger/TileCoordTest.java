package com.pocket.rpg.collision.trigger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TileCoord record.
 */
class TileCoordTest {

    @Test
    @DisplayName("Constructor with x, y creates coordinate at elevation 0")
    void constructorWithXYDefaultsToElevationZero() {
        TileCoord coord = new TileCoord(5, 10);
        assertEquals(5, coord.x());
        assertEquals(10, coord.y());
        assertEquals(0, coord.elevation());
    }

    @Test
    @DisplayName("Full constructor sets all fields")
    void fullConstructorSetsAllFields() {
        TileCoord coord = new TileCoord(3, 7, 2);
        assertEquals(3, coord.x());
        assertEquals(7, coord.y());
        assertEquals(2, coord.elevation());
    }

    @Test
    @DisplayName("Pack and unpack preserves positive coordinates")
    void packUnpackPreservesPositiveCoordinates() {
        TileCoord original = new TileCoord(100, 200, 3);
        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertEquals(original, unpacked);
    }

    @Test
    @DisplayName("Pack and unpack preserves negative coordinates")
    void packUnpackPreservesNegativeCoordinates() {
        TileCoord original = new TileCoord(-50, -100, -2);
        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertEquals(original, unpacked);
    }

    @Test
    @DisplayName("Pack and unpack preserves zero coordinates")
    void packUnpackPreservesZeroCoordinates() {
        TileCoord original = new TileCoord(0, 0, 0);
        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertEquals(original, unpacked);
    }

    @Test
    @DisplayName("Pack and unpack preserves mixed coordinates")
    void packUnpackPreservesMixedCoordinates() {
        TileCoord original = new TileCoord(-10, 50, -1);
        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertEquals(original, unpacked);
    }

    @Test
    @DisplayName("toString shows elevation only when non-zero")
    void toStringShowsElevationOnlyWhenNonZero() {
        TileCoord atGround = new TileCoord(5, 10, 0);
        TileCoord elevated = new TileCoord(5, 10, 2);

        assertEquals("(5, 10)", atGround.toString());
        assertEquals("(5, 10, elev=2)", elevated.toString());
    }

    @Test
    @DisplayName("Different coordinates have different packed values")
    void differentCoordinatesHaveDifferentPackedValues() {
        TileCoord a = new TileCoord(1, 2, 0);
        TileCoord b = new TileCoord(2, 1, 0);
        TileCoord c = new TileCoord(1, 2, 1);

        assertNotEquals(a.pack(), b.pack());
        assertNotEquals(a.pack(), c.pack());
        assertNotEquals(b.pack(), c.pack());
    }

    @Test
    @DisplayName("Same coordinates have same packed values")
    void sameCoordinatesHaveSamePackedValues() {
        TileCoord a = new TileCoord(5, 10, 2);
        TileCoord b = new TileCoord(5, 10, 2);

        assertEquals(a.pack(), b.pack());
    }
}
