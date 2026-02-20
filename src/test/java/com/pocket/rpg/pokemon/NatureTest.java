package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NatureTest {

    @Test
    @DisplayName("25 natures defined")
    void allNaturesDefined() {
        assertEquals(25, Nature.values().length);
    }

    @Test
    @DisplayName("5 neutral natures have all 1.0 modifiers")
    void neutralNaturesAllOne() {
        Nature[] neutrals = { Nature.HARDY, Nature.DOCILE, Nature.SERIOUS, Nature.BASHFUL, Nature.QUIRKY };
        for (Nature n : neutrals) {
            assertTrue(n.isNeutral(), n + " should be neutral");
            for (StatType stat : StatType.values()) {
                if (stat == StatType.HP) continue; // HP not affected by nature
                assertEquals(1.0f, n.getModifier(stat), n + " should have 1.0 for " + stat);
            }
        }
    }

    @Test
    @DisplayName("Adamant boosts ATK, hinders SP_ATK")
    void adamantModifiers() {
        assertEquals(1.1f, Nature.ADAMANT.atkModifier());
        assertEquals(1.0f, Nature.ADAMANT.defModifier());
        assertEquals(0.9f, Nature.ADAMANT.spAtkModifier());
        assertEquals(1.0f, Nature.ADAMANT.spDefModifier());
        assertEquals(1.0f, Nature.ADAMANT.spdModifier());
        assertFalse(Nature.ADAMANT.isNeutral());
    }

    @Test
    @DisplayName("Modest boosts SP_ATK, hinders ATK")
    void modestModifiers() {
        assertEquals(0.9f, Nature.MODEST.atkModifier());
        assertEquals(1.1f, Nature.MODEST.spAtkModifier());
    }

    @Test
    @DisplayName("Jolly boosts SPD, hinders SP_ATK")
    void jollyModifiers() {
        assertEquals(1.1f, Nature.JOLLY.spdModifier());
        assertEquals(0.9f, Nature.JOLLY.spAtkModifier());
    }

    @Test
    @DisplayName("Brave boosts ATK, hinders SPD")
    void braveModifiers() {
        assertEquals(1.1f, Nature.BRAVE.atkModifier());
        assertEquals(0.9f, Nature.BRAVE.spdModifier());
    }

    @Test
    @DisplayName("Timid boosts SPD, hinders ATK")
    void timidModifiers() {
        assertEquals(1.1f, Nature.TIMID.spdModifier());
        assertEquals(0.9f, Nature.TIMID.atkModifier());
    }

    @Test
    @DisplayName("every non-neutral nature has exactly one 1.1 and one 0.9")
    void nonNeutralHaveOneBoostOneHinder() {
        for (Nature n : Nature.values()) {
            if (n.isNeutral()) continue;

            int boostCount = 0;
            int hinderCount = 0;
            int neutralCount = 0;
            for (StatType stat : StatType.values()) {
                if (stat == StatType.HP) continue;
                float mod = n.getModifier(stat);
                if (mod == 1.1f) boostCount++;
                else if (mod == 0.9f) hinderCount++;
                else if (mod == 1.0f) neutralCount++;
            }
            assertEquals(1, boostCount, n + " should have exactly 1 boosted stat");
            assertEquals(1, hinderCount, n + " should have exactly 1 hindered stat");
            assertEquals(3, neutralCount, n + " should have 3 neutral stats");
        }
    }

    @Test
    @DisplayName("getBoostedStat and getHinderedStat match modifiers")
    void boostedAndHinderedMatchModifiers() {
        for (Nature n : Nature.values()) {
            if (n.isNeutral()) continue;
            assertEquals(1.1f, n.getModifier(n.getBoostedStat()), n + " boosted stat should be 1.1");
            assertEquals(0.9f, n.getModifier(n.getHinderedStat()), n + " hindered stat should be 0.9");
        }
    }
}
