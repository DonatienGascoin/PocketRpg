package com.pocket.rpg.pokemon;

import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PokemonInstanceData serialization wrapper round-trips.
 */
class PokemonInstanceDataTest {

    private static Pokedex pokedex;

    @BeforeAll
    static void init() {
        // Minimal AssetContext for Serializer — not needed for PokemonInstanceData
        // but Serializer.init may be needed for Gson
        com.pocket.rpg.resources.Assets.setContext(new StubContext());
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());

        pokedex = new Pokedex();
        pokedex.addSpecies(new PokemonSpecies(
                "pikachu", "Pikachu", PokemonType.ELECTRIC,
                new Stats(35, 55, 40, 50, 50, 90),
                List.of(new LearnedMove(1, "thunder_shock"), new LearnedMove(5, "growl")),
                64, 190, GrowthRate.MEDIUM_FAST,
                "sprites/pokemon/pikachu",
                EvolutionMethod.ITEM, 0, "thunder_stone", "raichu"
        ));
        pokedex.addMove(new Move("thunder_shock", "Thunder Shock", PokemonType.ELECTRIC, MoveCategory.SPECIAL, 40, 100, 30, "PARALYZE", 10, 0));
        pokedex.addMove(new Move("growl", "Growl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 40, "", 0, 0));
    }

    @Test
    @DisplayName("round-trip preserves all fields")
    void roundTrip() {
        PokemonInstance original = PokemonFactory.createStarter(pokedex, "pikachu", 25, "Red");
        original.setNickname("Sparky");
        original.setStatusCondition(StatusCondition.PARALYZE);
        original.setHeldItem("light_ball");
        original.damage(10);
        original.getMoves().getFirst().usePp();

        PokemonInstanceData data = PokemonInstanceData.fromPokemonInstance(original);
        PokemonInstance restored = data.toPokemonInstance();

        assertEquals(original.getSpecies(), restored.getSpecies());
        assertEquals(original.getNickname(), restored.getNickname());
        assertEquals(original.getLevel(), restored.getLevel());
        assertEquals(original.getExp(), restored.getExp());
        assertEquals(original.getNature(), restored.getNature());
        assertEquals(original.getIvs(), restored.getIvs());
        assertEquals(original.getCurrentHp(), restored.getCurrentHp());
        assertEquals(original.getStatusCondition(), restored.getStatusCondition());
        assertEquals(original.getHeldItem(), restored.getHeldItem());
        assertEquals(original.getOriginalTrainer(), restored.getOriginalTrainer());
        assertEquals(original.getCaughtIn(), restored.getCaughtIn());

        // Verify moves
        assertEquals(original.getMoveCount(), restored.getMoveCount());
        for (int i = 0; i < original.getMoveCount(); i++) {
            MoveSlot origSlot = original.getMoves().get(i);
            MoveSlot restoredSlot = restored.getMoves().get(i);
            assertEquals(origSlot.getMoveId(), restoredSlot.getMoveId());
            assertEquals(origSlot.getMaxPp(), restoredSlot.getMaxPp());
            assertEquals(origSlot.getCurrentPp(), restoredSlot.getCurrentPp());
        }
    }

    @Test
    @DisplayName("round-trip through Gson JSON preserves all fields")
    void roundTripThroughGson() {
        PokemonInstance original = PokemonFactory.createStarter(pokedex, "pikachu", 25, "Red");
        original.setNickname("Sparky");
        original.damage(5);

        PokemonInstanceData data = PokemonInstanceData.fromPokemonInstance(original);

        // Serialize to JSON and back
        String json = Serializer.toJson(data);
        PokemonInstanceData restored = Serializer.fromJson(json, PokemonInstanceData.class);

        assertEquals(data.species, restored.species);
        assertEquals(data.nickname, restored.nickname);
        assertEquals(data.level, restored.level);
        assertEquals(data.currentHp, restored.currentHp);
        assertEquals(data.nature, restored.nature);
        assertEquals(data.ivs, restored.ivs);
        assertEquals(data.moves.size(), restored.moves.size());
    }

    @Test
    @DisplayName("toPokemonInstance with null/default fields does not crash")
    void toPokemonInstanceDefaults() {
        PokemonInstanceData data = new PokemonInstanceData();
        data.species = "pikachu";
        data.level = 5;

        PokemonInstance p = data.toPokemonInstance();
        assertEquals("pikachu", p.getSpecies());
        assertEquals(5, p.getLevel());
        assertNotNull(p.getNature()); // defaults to HARDY
        assertEquals(StatusCondition.NONE, p.getStatusCondition());
    }

    // ========================================================================
    // Stub
    // ========================================================================

    private static class StubContext implements com.pocket.rpg.resources.AssetContext {
        @Override public <T> T load(String path, Class<T> type) { return null; }
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
