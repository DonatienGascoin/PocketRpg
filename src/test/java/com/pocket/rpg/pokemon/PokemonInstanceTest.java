package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PokemonInstanceTest {

    private Pokedex pokedex;
    private PokemonSpecies bulbasaur;
    private PokemonSpecies ivysaur;

    @BeforeEach
    void setUp() {
        pokedex = new Pokedex();

        bulbasaur = new PokemonSpecies(
                "bulbasaur", "Bulbasaur", PokemonType.GRASS,
                new Stats(45, 49, 49, 65, 65, 45),
                List.of(
                        new LearnedMove(1, "tackle"),
                        new LearnedMove(3, "growl"),
                        new LearnedMove(7, "vine_whip"),
                        new LearnedMove(10, "leech_seed")
                ),
                64, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/bulbasaur",
                EvolutionMethod.LEVEL, 16, null, "ivysaur"
        );

        ivysaur = new PokemonSpecies(
                "ivysaur", "Ivysaur", PokemonType.GRASS,
                new Stats(60, 62, 63, 80, 80, 60),
                List.of(
                        new LearnedMove(1, "tackle"),
                        new LearnedMove(3, "growl")
                ),
                142, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/ivysaur",
                EvolutionMethod.NONE, 0, null, null
        );

        pokedex.addSpecies(bulbasaur);
        pokedex.addSpecies(ivysaur);

        pokedex.addMove(new Move("tackle", "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL,
                40, 100, 35, "", 0, 0));
        pokedex.addMove(new Move("growl", "Growl", PokemonType.NORMAL, MoveCategory.STATUS,
                0, 100, 40, "", 0, 0));
        pokedex.addMove(new Move("vine_whip", "Vine Whip", PokemonType.GRASS, MoveCategory.PHYSICAL,
                45, 100, 25, "", 0, 0));
        pokedex.addMove(new Move("leech_seed", "Leech Seed", PokemonType.GRASS, MoveCategory.STATUS,
                0, 90, 10, "", 0, 0));
    }

    private PokemonInstance createBulbasaur(int level) {
        Stats ivs = new Stats(15, 15, 15, 15, 15, 15);
        PokemonInstance p = new PokemonInstance(
                "bulbasaur", null, level,
                GrowthRate.MEDIUM_SLOW.expForLevel(level),
                Nature.ADAMANT, ivs,
                List.of(new MoveSlot("tackle", 35)),
                "Ash", "pokeball"
        );
        p.setSpeciesData(bulbasaur);
        p.setCurrentHp(p.calcMaxHp());
        return p;
    }

    // ========================================================================
    // STAT CALCULATION
    // ========================================================================

    @Nested
    @DisplayName("Stat Calculation")
    class StatCalc {

        @Test
        @DisplayName("HP formula: (2*base + iv) * level / 100 + level + 10")
        void hpFormula() {
            // Bulbasaur base HP=45, IV=15, level=50
            // (2*45 + 15) * 50/100 + 50 + 10 = 105*50/100 + 60 = 52 + 60 = 112
            PokemonInstance p = createBulbasaur(50);
            assertEquals(112, p.calcMaxHp());
        }

        @Test
        @DisplayName("ATK with Adamant nature (+10% ATK)")
        void atkWithAdamant() {
            // Bulbasaur base ATK=49, IV=15, level=50, Adamant (+ATK)
            // ((2*49 + 15) * 50/100 + 5) * 1.1 = (113*50/100 + 5) * 1.1
            // = (56 + 5) * 1.1 = 61 * 1.1 = 67.1 → 67
            PokemonInstance p = createBulbasaur(50);
            assertEquals(67, p.calcStat(StatType.ATK));
        }

        @Test
        @DisplayName("SP_ATK with Adamant nature (-10% SP_ATK)")
        void spAtkWithAdamant() {
            // Bulbasaur base SP_ATK=65, IV=15, level=50, Adamant (-SP_ATK)
            // ((2*65 + 15) * 50/100 + 5) * 0.9 = (145*50/100 + 5) * 0.9
            // = (72 + 5) * 0.9 = 77 * 0.9 = 69.3 → 69
            PokemonInstance p = createBulbasaur(50);
            assertEquals(69, p.calcStat(StatType.SP_ATK));
        }

        @Test
        @DisplayName("DEF with no nature modifier")
        void defNoModifier() {
            // Bulbasaur base DEF=49, IV=15, level=50, Adamant (neutral DEF)
            // ((2*49 + 15) * 50/100 + 5) * 1.0 = 56 + 5 = 61
            PokemonInstance p = createBulbasaur(50);
            assertEquals(61, p.calcStat(StatType.DEF));
        }

        @Test
        @DisplayName("calcStat for HP delegates to calcMaxHp")
        void calcStatHpDelegatesToCalcMaxHp() {
            PokemonInstance p = createBulbasaur(50);
            assertEquals(p.calcMaxHp(), p.calcStat(StatType.HP));
        }

        @Test
        @DisplayName("throws if speciesData not set")
        void throwsWithoutSpeciesData() {
            PokemonInstance p = new PokemonInstance();
            p.setCurrentHp(1);
            assertThrows(IllegalStateException.class, () -> p.calcStat(StatType.ATK));
            assertThrows(IllegalStateException.class, p::calcMaxHp);
        }
    }

    // ========================================================================
    // HP MANAGEMENT
    // ========================================================================

    @Nested
    @DisplayName("HP Management")
    class HpManagement {

        @Test
        @DisplayName("isAlive returns true when HP > 0")
        void aliveWhenHpPositive() {
            PokemonInstance p = createBulbasaur(5);
            assertTrue(p.isAlive());
        }

        @Test
        @DisplayName("isAlive returns false when HP = 0")
        void deadWhenHpZero() {
            PokemonInstance p = createBulbasaur(5);
            p.setCurrentHp(0);
            assertFalse(p.isAlive());
        }

        @Test
        @DisplayName("damage reduces HP, clamped at 0")
        void damageClampedAtZero() {
            PokemonInstance p = createBulbasaur(5);
            p.damage(9999);
            assertEquals(0, p.getCurrentHp());
        }

        @Test
        @DisplayName("heal increases HP, clamped at max")
        void healClampedAtMax() {
            PokemonInstance p = createBulbasaur(5);
            int maxHp = p.calcMaxHp();
            p.damage(10);
            p.heal(9999);
            assertEquals(maxHp, p.getCurrentHp());
        }

        @Test
        @DisplayName("healFull restores HP, PP, and cures status")
        void healFullRestoresEverything() {
            PokemonInstance p = createBulbasaur(5);
            p.damage(10);
            p.setStatusCondition(StatusCondition.BURN);
            p.getMoves().get(0).usePp();
            p.healFull();

            assertEquals(p.calcMaxHp(), p.getCurrentHp());
            assertEquals(StatusCondition.NONE, p.getStatusCondition());
            assertEquals(35, p.getMoves().get(0).getCurrentPp());
        }

        @Test
        @DisplayName("canFight requires alive and has PP")
        void canFightRequiresAliveAndPp() {
            PokemonInstance p = createBulbasaur(5);
            assertTrue(p.canFight());

            // Use all PP
            for (int i = 0; i < 35; i++) p.getMoves().get(0).usePp();
            assertFalse(p.canFight());

            // Restore PP but faint
            p.getMoves().get(0).restoreAllPp();
            p.setCurrentHp(0);
            assertFalse(p.canFight());
        }
    }

    // ========================================================================
    // MOVE MANAGEMENT
    // ========================================================================

    @Nested
    @DisplayName("Move Management")
    class MoveManagement {

        @Test
        @DisplayName("learnMove succeeds when < 4 moves")
        void learnMoveOk() {
            PokemonInstance p = createBulbasaur(5); // starts with 1 move
            assertEquals(LearnMoveResult.OK, p.learnMove(new MoveSlot("growl", 40)));
            assertEquals(2, p.getMoveCount());
        }

        @Test
        @DisplayName("learnMove returns FULL when already has 4 moves")
        void learnMoveFull() {
            PokemonInstance p = createBulbasaur(5);
            p.learnMove(new MoveSlot("growl", 40));
            p.learnMove(new MoveSlot("vine_whip", 25));
            p.learnMove(new MoveSlot("leech_seed", 10));
            assertEquals(4, p.getMoveCount());

            assertEquals(LearnMoveResult.FULL, p.learnMove(new MoveSlot("razor_leaf", 25)));
            assertEquals(4, p.getMoveCount());
        }

        @Test
        @DisplayName("replaceMove swaps move at index")
        void replaceMoveAtIndex() {
            PokemonInstance p = createBulbasaur(5);
            p.learnMove(new MoveSlot("growl", 40));
            p.replaceMove(0, new MoveSlot("vine_whip", 25));
            assertEquals("vine_whip", p.getMoves().get(0).getMoveId());
            assertEquals("growl", p.getMoves().get(1).getMoveId());
        }
    }

    // ========================================================================
    // STATUS
    // ========================================================================

    @Nested
    @DisplayName("Status Condition")
    class StatusTests {

        @Test
        @DisplayName("starts with NONE")
        void startsNone() {
            PokemonInstance p = createBulbasaur(5);
            assertEquals(StatusCondition.NONE, p.getStatusCondition());
        }

        @Test
        @DisplayName("set and cure status")
        void setAndCure() {
            PokemonInstance p = createBulbasaur(5);
            p.setStatusCondition(StatusCondition.POISON);
            assertEquals(StatusCondition.POISON, p.getStatusCondition());

            p.cureStatus();
            assertEquals(StatusCondition.NONE, p.getStatusCondition());
        }
    }

    // ========================================================================
    // HELD ITEM
    // ========================================================================

    @Nested
    @DisplayName("Held Item")
    class HeldItemTests {

        @Test
        @DisplayName("starts with no held item")
        void startsNull() {
            PokemonInstance p = createBulbasaur(5);
            assertNull(p.getHeldItem());
        }

        @Test
        @DisplayName("set and remove held item")
        void setAndRemove() {
            PokemonInstance p = createBulbasaur(5);
            p.setHeldItem("oran_berry");
            assertEquals("oran_berry", p.getHeldItem());

            String removed = p.removeHeldItem();
            assertEquals("oran_berry", removed);
            assertNull(p.getHeldItem());
        }
    }

    // ========================================================================
    // EXPERIENCE & LEVELING
    // ========================================================================

    @Nested
    @DisplayName("Experience & Leveling")
    class ExpLeveling {

        @Test
        @DisplayName("single level up returns correct result")
        void singleLevelUp() {
            PokemonInstance p = createBulbasaur(2);
            // MEDIUM_SLOW level 3 = 6/5 * 27 - 15 * 9 + 100 * 3 - 140
            // = 32 - 135 + 300 - 140 = 57
            int expNeeded = GrowthRate.MEDIUM_SLOW.expForLevel(3) - p.getExp();
            LevelUpResult result = p.gainExp(expNeeded);

            assertTrue(result.leveledUp());
            assertEquals(2, result.oldLevel());
            assertEquals(3, result.newLevel());
            assertEquals(3, p.getLevel());
            // Level 3 learns "growl"
            assertTrue(result.newMoves().contains("growl"));
        }

        @Test
        @DisplayName("multi-level up gains all intermediate moves")
        void multiLevelUp() {
            PokemonInstance p = createBulbasaur(1);
            // Give enough exp to reach level 7
            int expNeeded = GrowthRate.MEDIUM_SLOW.expForLevel(7);
            LevelUpResult result = p.gainExp(expNeeded);

            assertTrue(result.leveledUp());
            assertEquals(1, result.oldLevel());
            assertEquals(7, result.newLevel());
            // Should learn growl (3) and vine_whip (7)
            assertTrue(result.newMoves().contains("growl"));
            assertTrue(result.newMoves().contains("vine_whip"));
        }

        @Test
        @DisplayName("no level up when exp insufficient")
        void noLevelUp() {
            PokemonInstance p = createBulbasaur(5);
            LevelUpResult result = p.gainExp(1);

            assertFalse(result.leveledUp());
            assertEquals(5, result.oldLevel());
            assertEquals(5, result.newLevel());
            assertTrue(result.newMoves().isEmpty());
        }

        @Test
        @DisplayName("evolution triggered at correct level")
        void evolutionTriggered() {
            PokemonInstance p = createBulbasaur(15);
            int expNeeded = GrowthRate.MEDIUM_SLOW.expForLevel(16) - p.getExp();
            LevelUpResult result = p.gainExp(expNeeded);

            assertTrue(result.canEvolve());
            assertEquals("ivysaur", result.evolvesInto());
        }

        @Test
        @DisplayName("no evolution before evolution level")
        void noEvolutionBeforeLevel() {
            PokemonInstance p = createBulbasaur(5);
            int expNeeded = GrowthRate.MEDIUM_SLOW.expForLevel(6) - p.getExp();
            LevelUpResult result = p.gainExp(expNeeded);

            assertFalse(result.canEvolve());
            assertNull(result.evolvesInto());
        }

        @Test
        @DisplayName("level capped at 100")
        void levelCappedAt100() {
            PokemonInstance p = createBulbasaur(99);
            LevelUpResult result = p.gainExp(999_999_999);
            assertEquals(100, p.getLevel());
        }

        @Test
        @DisplayName("getExpToNextLevel returns remaining exp")
        void expToNextLevel() {
            PokemonInstance p = createBulbasaur(5);
            int toNext = p.getExpToNextLevel();
            int needed = GrowthRate.MEDIUM_SLOW.expForLevel(6) - p.getExp();
            assertEquals(needed, toNext);
        }

        @Test
        @DisplayName("getExpToNextLevel returns 0 at level 100")
        void expToNextLevelAt100() {
            PokemonInstance p = createBulbasaur(99);
            p.gainExp(999_999_999);
            assertEquals(0, p.getExpToNextLevel());
        }
    }

    // ========================================================================
    // EVOLUTION
    // ========================================================================

    @Nested
    @DisplayName("Evolution")
    class EvolutionTests {

        @Test
        @DisplayName("evolve changes species, preserves nickname/IVs/nature/OT/moves")
        void evolvePreservesFields() {
            PokemonInstance p = createBulbasaur(16);
            p.setNickname("Buddy");
            p.setHeldItem("oran_berry");

            PokemonInstance evolved = p.evolve(pokedex);

            assertEquals("ivysaur", evolved.getSpecies());
            assertEquals("Buddy", evolved.getNickname());
            assertEquals(16, evolved.getLevel());
            assertEquals(Nature.ADAMANT, evolved.getNature());
            assertEquals(p.getIvs(), evolved.getIvs());
            assertEquals("Ash", evolved.getOriginalTrainer());
            assertEquals("pokeball", evolved.getCaughtIn());
            assertEquals("oran_berry", evolved.getHeldItem());
            assertEquals(1, evolved.getMoveCount());
            assertEquals("tackle", evolved.getMoves().get(0).getMoveId());
        }

        @Test
        @DisplayName("evolve adjusts HP proportionally")
        void evolveAdjustsHp() {
            PokemonInstance p = createBulbasaur(16);
            int oldMax = p.calcMaxHp();
            p.damage(oldMax / 2); // take half damage

            PokemonInstance evolved = p.evolve(pokedex);
            int newMax = evolved.calcMaxHp();

            // HP should be roughly proportional
            assertTrue(evolved.getCurrentHp() > 0);
            assertTrue(evolved.getCurrentHp() <= newMax);
        }

        @Test
        @DisplayName("evolve clears status condition")
        void evolveClearsStatus() {
            PokemonInstance p = createBulbasaur(16);
            p.setStatusCondition(StatusCondition.BURN);

            PokemonInstance evolved = p.evolve(pokedex);
            assertEquals(StatusCondition.NONE, evolved.getStatusCondition());
        }

        @Test
        @DisplayName("evolve throws when species cannot evolve")
        void evolveThrowsWhenCannotEvolve() {
            Stats ivs = new Stats(15, 15, 15, 15, 15, 15);
            PokemonInstance p = new PokemonInstance(
                    "ivysaur", null, 32,
                    GrowthRate.MEDIUM_SLOW.expForLevel(32),
                    Nature.ADAMANT, ivs,
                    List.of(new MoveSlot("tackle", 35)),
                    "Ash", "pokeball"
            );
            p.setSpeciesData(ivysaur); // ivysaur has no evolution in our test data

            assertThrows(IllegalStateException.class, () -> p.evolve(pokedex));
        }
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("toSaveData/fromSaveData round-trip preserves all fields")
        void roundTrip() {
            PokemonInstance original = createBulbasaur(25);
            original.setNickname("Buddy");
            original.setStatusCondition(StatusCondition.POISON);
            original.setHeldItem("oran_berry");
            original.learnMove(new MoveSlot("growl", 40));
            original.getMoves().get(0).usePp(); // tackle at 34/35
            original.damage(5);

            Map<String, Object> data = original.toSaveData();
            PokemonInstance restored = PokemonInstance.fromSaveData(data);

            assertEquals("bulbasaur", restored.getSpecies());
            assertEquals("Buddy", restored.getNickname());
            assertEquals(25, restored.getLevel());
            assertEquals(original.getExp(), restored.getExp());
            assertEquals(Nature.ADAMANT, restored.getNature());
            assertEquals(original.getIvs(), restored.getIvs());
            assertEquals(original.getCurrentHp(), restored.getCurrentHp());
            assertEquals(StatusCondition.POISON, restored.getStatusCondition());
            assertEquals("oran_berry", restored.getHeldItem());
            assertEquals("Ash", restored.getOriginalTrainer());
            assertEquals("pokeball", restored.getCaughtIn());

            // Moves
            assertEquals(2, restored.getMoveCount());
            assertEquals("tackle", restored.getMoves().get(0).getMoveId());
            assertEquals(34, restored.getMoves().get(0).getCurrentPp());
            assertEquals(35, restored.getMoves().get(0).getMaxPp());
            assertEquals("growl", restored.getMoves().get(1).getMoveId());
        }

        @Test
        @DisplayName("round-trip handles null nickname and heldItem")
        void roundTripNulls() {
            PokemonInstance original = createBulbasaur(10);
            // nickname and heldItem are null by default

            Map<String, Object> data = original.toSaveData();
            PokemonInstance restored = PokemonInstance.fromSaveData(data);

            assertNull(restored.getNickname());
            assertNull(restored.getHeldItem());
        }
    }

    // ========================================================================
    // DISPLAY NAME
    // ========================================================================

    @Nested
    @DisplayName("Display Name")
    class DisplayNameTests {

        @Test
        @DisplayName("uses nickname when set")
        void usesNickname() {
            PokemonInstance p = createBulbasaur(5);
            p.setNickname("Buddy");
            assertEquals("Buddy", p.getDisplayName());
        }

        @Test
        @DisplayName("uses species name when no nickname")
        void usesSpeciesName() {
            PokemonInstance p = createBulbasaur(5);
            assertEquals("Bulbasaur", p.getDisplayName());
        }

        @Test
        @DisplayName("falls back to species ID without speciesData")
        void fallsBackToSpeciesId() {
            PokemonInstance p = new PokemonInstance();
            // Use reflection or direct field to set species without speciesData
            Map<String, Object> data = Map.of(
                    "species", "pikachu",
                    "level", 5,
                    "exp", 0,
                    "nature", "HARDY",
                    "ivs", Map.of("hp", 0, "atk", 0, "def", 0, "spAtk", 0, "spDef", 0, "spd", 0),
                    "currentHp", 20,
                    "statusCondition", "NONE",
                    "moves", List.of()
            );
            PokemonInstance restored = PokemonInstance.fromSaveData(data);
            assertEquals("pikachu", restored.getDisplayName());
        }
    }
}
