package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PokemonTypeTest {

    @Test
    @DisplayName("all 18 types defined")
    void allTypesDefined() {
        assertEquals(18, PokemonType.values().length);
    }

    @Test
    @DisplayName("Fire is super effective against Grass")
    void fireSuperEffectiveVsGrass() {
        assertEquals(2.0f, PokemonType.FIRE.getEffectiveness(PokemonType.GRASS));
    }

    @Test
    @DisplayName("Water is super effective against Fire")
    void waterSuperEffectiveVsFire() {
        assertEquals(2.0f, PokemonType.WATER.getEffectiveness(PokemonType.FIRE));
    }

    @Test
    @DisplayName("Grass is super effective against Water")
    void grassSuperEffectiveVsWater() {
        assertEquals(2.0f, PokemonType.GRASS.getEffectiveness(PokemonType.WATER));
    }

    @Test
    @DisplayName("Electric is super effective against Water")
    void electricSuperEffectiveVsWater() {
        assertEquals(2.0f, PokemonType.ELECTRIC.getEffectiveness(PokemonType.WATER));
    }

    @Test
    @DisplayName("Normal has no effect on Ghost")
    void normalImmuneToGhost() {
        assertEquals(0.0f, PokemonType.NORMAL.getEffectiveness(PokemonType.GHOST));
    }

    @Test
    @DisplayName("Ghost has no effect on Normal")
    void ghostImmuneToNormal() {
        assertEquals(0.0f, PokemonType.GHOST.getEffectiveness(PokemonType.NORMAL));
    }

    @Test
    @DisplayName("Electric has no effect on Ground")
    void electricImmuneToGround() {
        assertEquals(0.0f, PokemonType.ELECTRIC.getEffectiveness(PokemonType.GROUND));
    }

    @Test
    @DisplayName("Ground has no effect on Flying")
    void groundImmuneToFlying() {
        assertEquals(0.0f, PokemonType.GROUND.getEffectiveness(PokemonType.FLYING));
    }

    @Test
    @DisplayName("Dragon has no effect on Fairy")
    void dragonImmuneToFairy() {
        assertEquals(0.0f, PokemonType.DRAGON.getEffectiveness(PokemonType.FAIRY));
    }

    @Test
    @DisplayName("Psychic has no effect on Dark")
    void psychicImmuneToDark() {
        assertEquals(0.0f, PokemonType.PSYCHIC.getEffectiveness(PokemonType.DARK));
    }

    @Test
    @DisplayName("Poison has no effect on Steel")
    void poisonImmuneToSteel() {
        assertEquals(0.0f, PokemonType.POISON.getEffectiveness(PokemonType.STEEL));
    }

    @Test
    @DisplayName("Fighting has no effect on Ghost")
    void fightingImmuneToGhost() {
        assertEquals(0.0f, PokemonType.FIGHTING.getEffectiveness(PokemonType.GHOST));
    }

    @Test
    @DisplayName("Electric not very effective against Electric")
    void electricNotEffectiveVsElectric() {
        assertEquals(0.5f, PokemonType.ELECTRIC.getEffectiveness(PokemonType.ELECTRIC));
    }

    @Test
    @DisplayName("Fire not very effective against Water")
    void fireNotEffectiveVsWater() {
        assertEquals(0.5f, PokemonType.FIRE.getEffectiveness(PokemonType.WATER));
    }

    @Test
    @DisplayName("Normal vs Normal is neutral")
    void normalVsNormalNeutral() {
        assertEquals(1.0f, PokemonType.NORMAL.getEffectiveness(PokemonType.NORMAL));
    }

    @Test
    @DisplayName("Fighting is super effective against Normal")
    void fightingSuperEffectiveVsNormal() {
        assertEquals(2.0f, PokemonType.FIGHTING.getEffectiveness(PokemonType.NORMAL));
    }

    @Test
    @DisplayName("Fairy is super effective against Dragon")
    void fairySuperEffectiveVsDragon() {
        assertEquals(2.0f, PokemonType.FAIRY.getEffectiveness(PokemonType.DRAGON));
    }

    @Test
    @DisplayName("static convenience method matches instance method")
    void staticMethodMatchesInstance() {
        assertEquals(
                PokemonType.FIRE.getEffectiveness(PokemonType.GRASS),
                PokemonType.getEffectiveness(PokemonType.FIRE, PokemonType.GRASS)
        );
    }

    @Test
    @DisplayName("Ice is super effective against Dragon")
    void iceSuperEffectiveVsDragon() {
        assertEquals(2.0f, PokemonType.ICE.getEffectiveness(PokemonType.DRAGON));
    }

    @Test
    @DisplayName("Steel is super effective against Fairy")
    void steelSuperEffectiveVsFairy() {
        assertEquals(2.0f, PokemonType.STEEL.getEffectiveness(PokemonType.FAIRY));
    }
}
