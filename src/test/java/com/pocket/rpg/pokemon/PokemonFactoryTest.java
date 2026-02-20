package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PokemonFactoryTest {

    private Pokedex pokedex;

    @BeforeEach
    void setUp() {
        pokedex = new Pokedex();

        pokedex.addSpecies(new PokemonSpecies(
                "bulbasaur", "Bulbasaur", PokemonType.GRASS,
                new Stats(45, 49, 49, 65, 65, 45),
                List.of(
                        new LearnedMove(1, "tackle"),
                        new LearnedMove(3, "growl"),
                        new LearnedMove(7, "vine_whip"),
                        new LearnedMove(13, "razor_leaf"),
                        new LearnedMove(20, "sleep_powder")
                ),
                64, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/bulbasaur",
                EvolutionMethod.LEVEL, 16, null, "ivysaur"
        ));

        pokedex.addMove(new Move("tackle", "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, "", 0, 0));
        pokedex.addMove(new Move("growl", "Growl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 40, "", 0, 0));
        pokedex.addMove(new Move("vine_whip", "Vine Whip", PokemonType.GRASS, MoveCategory.PHYSICAL, 45, 100, 25, "", 0, 0));
        pokedex.addMove(new Move("razor_leaf", "Razor Leaf", PokemonType.GRASS, MoveCategory.PHYSICAL, 55, 95, 25, "", 0, 0));
        pokedex.addMove(new Move("sleep_powder", "Sleep Powder", PokemonType.GRASS, MoveCategory.STATUS, 0, 75, 15, "SLEEP", 100, 0));
        pokedex.addMove(new Move("thunderbolt", "Thunderbolt", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 90, 100, 15, "PARALYZE", 10, 0));
    }

    // ========================================================================
    // createWild
    // ========================================================================

    @Nested
    @DisplayName("createWild")
    class CreateWild {

        @Test
        @DisplayName("creates valid wild Pokemon at given level")
        void createsAtLevel() {
            PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 10);

            assertEquals("bulbasaur", p.getSpecies());
            assertEquals(10, p.getLevel());
            assertNull(p.getNickname());
            assertEquals("Wild", p.getOriginalTrainer());
            assertNull(p.getCaughtIn());
            assertEquals(StatusCondition.NONE, p.getStatusCondition());
            assertNull(p.getHeldItem());
        }

        @Test
        @DisplayName("HP starts at max")
        void hpAtMax() {
            PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 10);
            assertEquals(p.calcMaxHp(), p.getCurrentHp());
        }

        @Test
        @DisplayName("IVs are in 0-31 range")
        void ivsInRange() {
            for (int i = 0; i < 20; i++) {
                PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 5);
                Stats ivs = p.getIvs();
                for (StatType stat : StatType.values()) {
                    int iv = ivs.get(stat);
                    assertTrue(iv >= 0 && iv <= 31, "IV out of range: " + stat + "=" + iv);
                }
            }
        }

        @Test
        @DisplayName("selects last 4 moves at or below level")
        void selectsLevelAppropriateMoves() {
            // Level 13: tackle(1), growl(3), vine_whip(7), razor_leaf(13) — all 4
            PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 13);
            assertEquals(4, p.getMoveCount());
            assertEquals("tackle", p.getMoves().get(0).getMoveId());
            assertEquals("growl", p.getMoves().get(1).getMoveId());
            assertEquals("vine_whip", p.getMoves().get(2).getMoveId());
            assertEquals("razor_leaf", p.getMoves().get(3).getMoveId());
        }

        @Test
        @DisplayName("takes last 4 moves when more than 4 eligible")
        void takesLast4Moves() {
            // Level 20: all 5 eligible → last 4: growl, vine_whip, razor_leaf, sleep_powder
            PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 20);
            assertEquals(4, p.getMoveCount());
            assertEquals("growl", p.getMoves().get(0).getMoveId());
            assertEquals("vine_whip", p.getMoves().get(1).getMoveId());
            assertEquals("razor_leaf", p.getMoves().get(2).getMoveId());
            assertEquals("sleep_powder", p.getMoves().get(3).getMoveId());
        }

        @Test
        @DisplayName("level 1 gets only level 1 moves")
        void level1Moves() {
            PokemonInstance p = PokemonFactory.createWild(pokedex, "bulbasaur", 1);
            assertEquals(1, p.getMoveCount());
            assertEquals("tackle", p.getMoves().get(0).getMoveId());
        }

        @Test
        @DisplayName("throws for unknown species")
        void throwsUnknownSpecies() {
            assertThrows(IllegalArgumentException.class,
                    () -> PokemonFactory.createWild(pokedex, "missingno", 5));
        }
    }

    // ========================================================================
    // createStarter
    // ========================================================================

    @Nested
    @DisplayName("createStarter")
    class CreateStarter {

        @Test
        @DisplayName("uses provided trainer name and pokeball")
        void usesTrainerName() {
            PokemonInstance p = PokemonFactory.createStarter(pokedex, "bulbasaur", 5, "Red");

            assertEquals("Red", p.getOriginalTrainer());
            assertEquals("pokeball", p.getCaughtIn());
        }

        @Test
        @DisplayName("otherwise same as wild")
        void sameAsWild() {
            PokemonInstance p = PokemonFactory.createStarter(pokedex, "bulbasaur", 5, "Red");
            assertEquals("bulbasaur", p.getSpecies());
            assertEquals(5, p.getLevel());
            assertEquals(p.calcMaxHp(), p.getCurrentHp());
        }
    }

    // ========================================================================
    // createTrainer
    // ========================================================================

    @Nested
    @DisplayName("createTrainer")
    class CreateTrainer {

        @Test
        @DisplayName("uses explicit move list")
        void usesExplicitMoves() {
            PokemonInstance p = PokemonFactory.createTrainer(
                    pokedex, "bulbasaur", 10,
                    List.of("thunderbolt", "tackle"), "Brock"
            );

            assertEquals(2, p.getMoveCount());
            assertEquals("thunderbolt", p.getMoves().get(0).getMoveId());
            assertEquals("tackle", p.getMoves().get(1).getMoveId());
            assertEquals("Brock", p.getOriginalTrainer());
        }

        @Test
        @DisplayName("falls back to learnset when moves is null")
        void fallsBackToLearnset() {
            PokemonInstance p = PokemonFactory.createTrainer(
                    pokedex, "bulbasaur", 10, null, "Brock"
            );

            assertTrue(p.getMoveCount() > 0);
            assertEquals("Brock", p.getOriginalTrainer());
        }

        @Test
        @DisplayName("falls back to learnset when moves is empty")
        void fallsBackOnEmptyList() {
            PokemonInstance p = PokemonFactory.createTrainer(
                    pokedex, "bulbasaur", 10, List.of(), "Brock"
            );

            assertTrue(p.getMoveCount() > 0);
        }

        @Test
        @DisplayName("uses correct PP from move definitions")
        void correctPpFromMove() {
            PokemonInstance p = PokemonFactory.createTrainer(
                    pokedex, "bulbasaur", 10,
                    List.of("thunderbolt"), "Brock"
            );

            assertEquals(15, p.getMoves().get(0).getMaxPp()); // Thunderbolt has 15 PP
        }
    }
}
