package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrowthRateTest {

    @Test
    @DisplayName("level 1 requires 0 exp for all growth rates")
    void level1IsZero() {
        for (GrowthRate rate : GrowthRate.values()) {
            assertEquals(0, rate.expForLevel(1), rate + " at level 1");
        }
    }

    @Test
    @DisplayName("FAST: exp for level 100 = 800,000")
    void fastLevel100() {
        // 4/5 * 100^3 = 800,000
        assertEquals(800_000, GrowthRate.FAST.expForLevel(100));
    }

    @Test
    @DisplayName("MEDIUM_FAST: exp for level 100 = 1,000,000")
    void mediumFastLevel100() {
        // 100^3 = 1,000,000
        assertEquals(1_000_000, GrowthRate.MEDIUM_FAST.expForLevel(100));
    }

    @Test
    @DisplayName("SLOW: exp for level 100 = 1,250,000")
    void slowLevel100() {
        // 5/4 * 100^3 = 1,250,000
        assertEquals(1_250_000, GrowthRate.SLOW.expForLevel(100));
    }

    @Test
    @DisplayName("MEDIUM_SLOW: exp for level 100")
    void mediumSlowLevel100() {
        // 6/5 * 100^3 - 15 * 100^2 + 100 * 100 - 140
        // = 1,200,000 - 150,000 + 10,000 - 140 = 1,059,860
        assertEquals(1_059_860, GrowthRate.MEDIUM_SLOW.expForLevel(100));
    }

    @Test
    @DisplayName("FAST level 10 = 800")
    void fastLevel10() {
        // 4/5 * 1000 = 800
        assertEquals(800, GrowthRate.FAST.expForLevel(10));
    }

    @Test
    @DisplayName("MEDIUM_FAST level 10 = 1000")
    void mediumFastLevel10() {
        assertEquals(1000, GrowthRate.MEDIUM_FAST.expForLevel(10));
    }

    @Test
    @DisplayName("SLOW level 10 = 1250")
    void slowLevel10() {
        // 5/4 * 1000 = 1250
        assertEquals(1250, GrowthRate.SLOW.expForLevel(10));
    }

    @Test
    @DisplayName("exp increases with level for all growth rates")
    void expIncreasesWithLevel() {
        for (GrowthRate rate : GrowthRate.values()) {
            int prev = 0;
            for (int level = 2; level <= 100; level++) {
                int exp = rate.expForLevel(level);
                assertTrue(exp > prev, rate + " level " + level + " should be > level " + (level - 1));
                prev = exp;
            }
        }
    }

    @Test
    @DisplayName("FAST is always less exp than MEDIUM_FAST at same level")
    void fastLessThanMediumFast() {
        for (int level = 2; level <= 100; level++) {
            assertTrue(
                    GrowthRate.FAST.expForLevel(level) < GrowthRate.MEDIUM_FAST.expForLevel(level),
                    "FAST should require less exp than MEDIUM_FAST at level " + level
            );
        }
    }

    @Test
    @DisplayName("MEDIUM_FAST is always less exp than SLOW at same level")
    void mediumFastLessThanSlow() {
        for (int level = 2; level <= 100; level++) {
            assertTrue(
                    GrowthRate.MEDIUM_FAST.expForLevel(level) < GrowthRate.SLOW.expForLevel(level),
                    "MEDIUM_FAST should require less exp than SLOW at level " + level
            );
        }
    }
}
