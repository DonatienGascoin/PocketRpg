package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TrainerComponent. These test the ISaveable contract and party lazy-creation
 * via a TrainerRegistry stub loaded through the AssetContext.
 */
class TrainerComponentTest {

    private static Pokedex testPokedex;
    private static TrainerRegistry testRegistry;

    @BeforeAll
    static void initSerializer() {
        testPokedex = PlayerPartyComponentTest.createTestPokedex();
        testRegistry = new TrainerRegistry();

        // Create test trainer definitions
        TrainerDefinition brock = new TrainerDefinition("brock");
        brock.setTrainerName("Brock");
        brock.setDefeatMoney(1200);
        brock.setParty(List.of(
                new TrainerPokemonSpec("bulbasaur", 12, null),
                new TrainerPokemonSpec("charmander", 14, null)
        ));
        testRegistry.addTrainer(brock);

        TrainerDefinition brockMoves = new TrainerDefinition("brock_moves");
        brockMoves.setTrainerName("Brock");
        brockMoves.setDefeatMoney(1200);
        brockMoves.setParty(List.of(
                new TrainerPokemonSpec("bulbasaur", 10, List.of("tackle", "growl"))
        ));
        testRegistry.addTrainer(brockMoves);

        TrainerDefinition brockAuto = new TrainerDefinition("brock_auto");
        brockAuto.setTrainerName("Brock");
        brockAuto.setDefeatMoney(1200);
        brockAuto.setParty(List.of(
                new TrainerPokemonSpec("bulbasaur", 10, null)
        ));
        testRegistry.addTrainer(brockAuto);

        TrainerDefinition brockEmpty = new TrainerDefinition("brock_empty");
        brockEmpty.setTrainerName("Brock");
        brockEmpty.setDefeatMoney(1200);
        brockEmpty.setParty(new ArrayList<>());
        testRegistry.addTrainer(brockEmpty);

        TrainerDefinition brockSingle = new TrainerDefinition("brock_single");
        brockSingle.setTrainerName("Brock");
        brockSingle.setDefeatMoney(1200);
        brockSingle.setParty(List.of(
                new TrainerPokemonSpec("bulbasaur", 12, null)
        ));
        testRegistry.addTrainer(brockSingle);

        com.pocket.rpg.resources.Assets.setContext(
                new PokedexAndRegistryStubContext(testPokedex, testRegistry));
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
        ComponentRegistry.initialize();
    }

    private TrainerComponent createTrainer(String trainerId) {
        TrainerComponent trainer = new TrainerComponent();
        trainer.setTrainerId(trainerId);
        return trainer;
    }

    // ========================================================================
    // getParty — lazy creation
    // ========================================================================

    @Test
    @DisplayName("getParty lazily creates PokemonInstance list from definition")
    void getPartyLazyCreation() {
        TrainerComponent trainer = createTrainer("brock");

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
        TrainerComponent trainer = createTrainer("brock_single");

        List<PokemonInstance> first = trainer.getParty();
        List<PokemonInstance> second = trainer.getParty();
        assertSame(first, second);
    }

    @Test
    @DisplayName("getParty with explicit moves — moves match spec")
    void getPartyWithExplicitMoves() {
        TrainerComponent trainer = createTrainer("brock_moves");

        List<PokemonInstance> party = trainer.getParty();
        assertEquals(2, party.getFirst().getMoveCount());
        assertEquals("tackle", party.getFirst().getMoves().get(0).getMoveId());
        assertEquals("growl", party.getFirst().getMoves().get(1).getMoveId());
    }

    @Test
    @DisplayName("getParty with null moves — auto-selected from learnset")
    void getPartyAutoMoves() {
        TrainerComponent trainer = createTrainer("brock_auto");

        List<PokemonInstance> party = trainer.getParty();
        assertTrue(party.getFirst().getMoveCount() > 0);
    }

    // ========================================================================
    // getDefinition + delegation
    // ========================================================================

    @Test
    @DisplayName("getTrainerName delegates to definition")
    void getTrainerName() {
        TrainerComponent trainer = createTrainer("brock");
        assertEquals("Brock", trainer.getTrainerName());
    }

    @Test
    @DisplayName("getDefeatMoney delegates to definition")
    void getDefeatMoney() {
        TrainerComponent trainer = createTrainer("brock");
        assertEquals(1200, trainer.getDefeatMoney());
    }

    @Test
    @DisplayName("getDialogueVariables includes TRAINER_NAME")
    void getDialogueVariables() {
        TrainerComponent trainer = createTrainer("brock");
        Map<String, String> vars = trainer.getDialogueVariables();
        assertEquals("Brock", vars.get("TRAINER_NAME"));
    }

    @Test
    @DisplayName("missing trainerId returns empty name")
    void missingTrainerId() {
        TrainerComponent trainer = createTrainer("nonexistent");
        assertEquals("", trainer.getTrainerName());
        assertEquals(0, trainer.getDefeatMoney());
        assertTrue(trainer.getParty().isEmpty());
    }

    // ========================================================================
    // getFirstAlive
    // ========================================================================

    @Test
    @DisplayName("getFirstAlive returns first non-fainted trainer Pokemon")
    void getFirstAlive() {
        TrainerComponent trainer = createTrainer("brock");

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
        TrainerComponent trainer = createTrainer("brock_empty");

        assertFalse(trainer.isDefeated());
        trainer.markDefeated();
        assertTrue(trainer.isDefeated());
    }

    @Test
    @DisplayName("ISaveable getSaveState returns defeated flag")
    void getSaveState() {
        TrainerComponent trainer = createTrainer("brock_empty");

        Map<String, Object> state = trainer.getSaveState();
        assertEquals(false, state.get("defeated"));

        trainer.markDefeated();
        state = trainer.getSaveState();
        assertEquals(true, state.get("defeated"));
    }

    @Test
    @DisplayName("ISaveable loadSaveState restores defeated flag")
    void loadSaveState() {
        TrainerComponent trainer = createTrainer("brock_empty");

        trainer.loadSaveState(Map.of("defeated", true));
        assertTrue(trainer.isDefeated());

        trainer.loadSaveState(Map.of("defeated", false));
        assertFalse(trainer.isDefeated());
    }

    @Test
    @DisplayName("hasSaveableState returns true only when defeated")
    void hasSaveableState() {
        TrainerComponent trainer = createTrainer("brock_empty");

        assertFalse(trainer.hasSaveableState());
        trainer.markDefeated();
        assertTrue(trainer.hasSaveableState());
    }

    @Test
    @DisplayName("loadSaveState handles null gracefully")
    void loadSaveStateNull() {
        TrainerComponent trainer = createTrainer("brock_empty");
        assertDoesNotThrow(() -> trainer.loadSaveState(null));
    }

    // ========================================================================
    // Stub AssetContext
    // ========================================================================

    static class PokedexAndRegistryStubContext implements com.pocket.rpg.resources.AssetContext {
        private final Pokedex pokedex;
        private final TrainerRegistry registry;

        PokedexAndRegistryStubContext(Pokedex pokedex, TrainerRegistry registry) {
            this.pokedex = pokedex;
            this.registry = registry;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == Pokedex.class) return (T) pokedex;
            if (type == TrainerRegistry.class) return (T) registry;
            return null;
        }

        @Override public <T> T load(String path) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions, Class<T> type) { return null; }
        @Override public <T> T get(String path) { return null; }
        @Override public <T> java.util.List<T> getAll(Class<T> type) { return java.util.Collections.emptyList(); }
        @Override public boolean isLoaded(String path) { return false; }
        @Override public java.util.Set<String> getLoadedPaths() { return java.util.Collections.emptySet(); }
        @Override public String getPathForResource(Object resource) { return null; }
        @Override public void persist(Object resource) {}
        @Override public void persist(Object resource, String path) {}
        @Override public void persist(Object resource, String path, com.pocket.rpg.resources.LoadOptions options) {}
        @Override public com.pocket.rpg.resources.AssetsConfiguration configure() { return null; }
        @Override public com.pocket.rpg.resources.CacheStats getStats() { return null; }
        @Override public java.util.List<String> scanByType(Class<?> type) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanByType(Class<?> type, String directory) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll(String directory) { return java.util.Collections.emptyList(); }
        @Override public void setAssetRoot(String assetRoot) {}
        @Override public String getAssetRoot() { return null; }
        @Override public com.pocket.rpg.resources.ResourceCache getCache() { return null; }
        @Override public void setErrorMode(com.pocket.rpg.resources.ErrorMode errorMode) {}
        @Override public void setStatisticsEnabled(boolean enableStatistics) {}
        @Override public String getRelativePath(String fullPath) { return null; }
        @Override public com.pocket.rpg.rendering.resources.Sprite getPreviewSprite(String path, Class<?> type) { return null; }
        @Override public Class<?> getTypeForPath(String path) { return null; }
        @Override public void registerResource(Object resource, String path) {}
        @Override public void unregisterResource(Object resource) {}
        @Override public boolean isAssetType(Class<?> type) { return false; }
        @Override public boolean canInstantiate(Class<?> type) { return false; }
        @Override public com.pocket.rpg.editor.scene.EditorGameObject instantiate(String path, Class<?> type, org.joml.Vector3f position) { return null; }
        @Override public com.pocket.rpg.editor.EditorPanelType getEditorPanelType(Class<?> type) { return null; }
        @Override public java.util.Set<com.pocket.rpg.resources.EditorCapability> getEditorCapabilities(Class<?> type) { return java.util.Collections.emptySet(); }
        @Override public String getIconCodepoint(Class<?> type) { return null; }
        @Override public boolean canSave(Class<?> type) { return false; }
    }
}
