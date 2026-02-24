package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TrainerComponent. These test the ISaveable contract and party lazy-creation
 * without needing SaveManager or scene loading — TrainerComponent doesn't use PlayerData.
 */
class TrainerComponentTest {

    private static Pokedex testPokedex;

    @BeforeAll
    static void initSerializer() {
        testPokedex = PlayerPartyComponentTest.createTestPokedex();
        com.pocket.rpg.resources.Assets.setContext(new PlayerPartyComponentTest.PokedexStubContext(testPokedex));
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
        ComponentRegistry.initialize();
    }

    private TrainerComponent createTrainer(String name, List<TrainerComponent.TrainerPokemonSpec> specs, int money) {
        try {
            TrainerComponent trainer = new TrainerComponent();
            setField(trainer, "trainerName", name);
            setField(trainer, "partySpecs", new ArrayList<>(specs));
            setField(trainer, "defeatMoney", money);
            setField(trainer, "preDialogue", "pre_" + name.toLowerCase());
            setField(trainer, "postDialogue", "post_" + name.toLowerCase());
            return trainer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TrainerComponent for test", e);
        }
    }

    private TrainerComponent.TrainerPokemonSpec createSpec(String speciesId, int level) {
        try {
            TrainerComponent.TrainerPokemonSpec spec = new TrainerComponent.TrainerPokemonSpec();
            setField(spec, "speciesId", speciesId);
            setField(spec, "level", level);
            return spec;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TrainerPokemonSpec for test", e);
        }
    }

    private TrainerComponent.TrainerPokemonSpec createSpecWithMoves(String speciesId, int level, List<String> moves) {
        try {
            TrainerComponent.TrainerPokemonSpec spec = createSpec(speciesId, level);
            setField(spec, "moves", new ArrayList<>(moves));
            return spec;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create TrainerPokemonSpec for test", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ========================================================================
    // getParty — lazy creation
    // ========================================================================

    @Test
    @DisplayName("getParty lazily creates PokemonInstance list from partySpecs")
    void getPartyLazyCreation() {
        TrainerComponent trainer = createTrainer("Brock",
                List.of(createSpec("bulbasaur", 12), createSpec("charmander", 14)),
                1200);

        List<PokemonInstance> party = trainer.getParty();
        assertEquals(2, party.size());
        assertEquals("bulbasaur", party.get(0).getSpecies());
        assertEquals(12, party.get(0).getLevel());
        assertEquals("charmander", party.get(1).getSpecies());
        assertEquals(14, party.get(1).getLevel());
    }

    @Test
    @DisplayName("getParty second call returns same cached list (no re-creation)")
    void getPartyCached() {
        TrainerComponent trainer = createTrainer("Brock",
                List.of(createSpec("bulbasaur", 12)),
                1200);

        List<PokemonInstance> first = trainer.getParty();
        List<PokemonInstance> second = trainer.getParty();
        assertSame(first, second);
    }

    @Test
    @DisplayName("getParty with explicit moves — moves match spec")
    void getPartyWithExplicitMoves() {
        TrainerComponent trainer = createTrainer("Brock",
                List.of(createSpecWithMoves("bulbasaur", 10, List.of("tackle", "growl"))),
                1200);

        List<PokemonInstance> party = trainer.getParty();
        assertEquals(2, party.getFirst().getMoveCount());
        assertEquals("tackle", party.getFirst().getMoves().get(0).getMoveId());
        assertEquals("growl", party.getFirst().getMoves().get(1).getMoveId());
    }

    @Test
    @DisplayName("getParty with null moves — auto-selected from learnset")
    void getPartyAutoMoves() {
        TrainerComponent trainer = createTrainer("Brock",
                List.of(createSpec("bulbasaur", 10)),
                1200);

        List<PokemonInstance> party = trainer.getParty();
        assertTrue(party.getFirst().getMoveCount() > 0);
    }

    // ========================================================================
    // getFirstAlive
    // ========================================================================

    @Test
    @DisplayName("getFirstAlive returns first non-fainted trainer Pokemon")
    void getFirstAlive() {
        TrainerComponent trainer = createTrainer("Brock",
                List.of(createSpec("bulbasaur", 12), createSpec("charmander", 14)),
                1200);

        assertEquals("bulbasaur", trainer.getFirstAlive().getSpecies());

        // Faint first
        trainer.getParty().getFirst().setCurrentHp(0);
        assertEquals("charmander", trainer.getFirstAlive().getSpecies());

        // Faint both
        trainer.getParty().get(1).setCurrentHp(0);
        assertNull(trainer.getFirstAlive());
    }

    // ========================================================================
    // defeated state + ISaveable
    // ========================================================================

    @Test
    @DisplayName("markDefeated → isDefeated returns true")
    void markDefeated() {
        TrainerComponent trainer = createTrainer("Brock", List.of(), 1200);

        assertFalse(trainer.isDefeated());
        trainer.markDefeated();
        assertTrue(trainer.isDefeated());
    }

    @Test
    @DisplayName("ISaveable getSaveState returns defeated flag")
    void getSaveState() {
        TrainerComponent trainer = createTrainer("Brock", List.of(), 1200);

        Map<String, Object> state = trainer.getSaveState();
        assertEquals(false, state.get("defeated"));

        trainer.markDefeated();
        state = trainer.getSaveState();
        assertEquals(true, state.get("defeated"));
    }

    @Test
    @DisplayName("ISaveable loadSaveState restores defeated flag")
    void loadSaveState() {
        TrainerComponent trainer = createTrainer("Brock", List.of(), 1200);

        trainer.loadSaveState(Map.of("defeated", true));
        assertTrue(trainer.isDefeated());

        trainer.loadSaveState(Map.of("defeated", false));
        assertFalse(trainer.isDefeated());
    }

    @Test
    @DisplayName("hasSaveableState returns true only when defeated")
    void hasSaveableState() {
        TrainerComponent trainer = createTrainer("Brock", List.of(), 1200);

        assertFalse(trainer.hasSaveableState());
        trainer.markDefeated();
        assertTrue(trainer.hasSaveableState());
    }

    @Test
    @DisplayName("loadSaveState handles null gracefully")
    void loadSaveStateNull() {
        TrainerComponent trainer = createTrainer("Brock", List.of(), 1200);
        assertDoesNotThrow(() -> trainer.loadSaveState(null));
    }
}
