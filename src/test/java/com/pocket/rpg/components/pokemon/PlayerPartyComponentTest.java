package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.save.PlayerData;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPartyComponentTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static Pokedex testPokedex;

    @BeforeAll
    static void initSerializer() {
        testPokedex = createTestPokedex();
        com.pocket.rpg.resources.Assets.setContext(new PokedexStubContext(testPokedex));
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        sceneManager = new SceneManager(
                new ViewportConfig(GameConfig.builder()
                        .gameWidth(800).gameHeight(600)
                        .windowWidth(800).windowHeight(600)
                        .build()),
                RenderingConfig.builder().defaultOrthographicSize(7.5f).build()
        );
        SaveManager.initialize(sceneManager, tempDir);
        SaveManager.newGame();
    }

    private PlayerPartyComponent loadSceneWithParty() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            scene.addGameObject(player);
        });
        sceneManager.loadScene(scene);
        return scene.findGameObject("Player").getComponent(PlayerPartyComponent.class);
    }

    // ========================================================================
    // addToParty
    // ========================================================================

    @Test
    @DisplayName("addToParty accepts up to 6 Pokemon")
    void addToPartyUpTo6() {
        PlayerPartyComponent comp = loadSceneWithParty();

        for (int i = 0; i < 6; i++) {
            assertTrue(comp.addToParty(createPokemon("bulbasaur", 5)));
        }
        assertEquals(6, comp.partySize());
    }

    @Test
    @DisplayName("addToParty returns false for 7th Pokemon")
    void addToPartyRejectsFull() {
        PlayerPartyComponent comp = loadSceneWithParty();

        for (int i = 0; i < 6; i++) {
            comp.addToParty(createPokemon("bulbasaur", 5));
        }
        assertFalse(comp.addToParty(createPokemon("bulbasaur", 5)));
        assertEquals(6, comp.partySize());
    }

    @Test
    @DisplayName("addToParty write-through — PlayerData reflects addition immediately")
    void addToPartyWriteThrough() {
        PlayerPartyComponent comp = loadSceneWithParty();

        comp.addToParty(createPokemon("bulbasaur", 10));

        PlayerData data = PlayerData.load();
        assertNotNull(data.team);
        assertEquals(1, data.team.size());
        assertEquals("bulbasaur", data.team.getFirst().species);
        assertEquals(10, data.team.getFirst().level);
    }

    // ========================================================================
    // removeFromParty
    // ========================================================================

    @Test
    @DisplayName("removeFromParty returns removed Pokemon, party size decrements")
    void removeFromParty() {
        PlayerPartyComponent comp = loadSceneWithParty();

        PokemonInstance p1 = createPokemon("bulbasaur", 5);
        PokemonInstance p2 = createPokemon("charmander", 5);
        comp.addToParty(p1);
        comp.addToParty(p2);

        PokemonInstance removed = comp.removeFromParty(0);
        assertEquals("bulbasaur", removed.getSpecies());
        assertEquals(1, comp.partySize());
        assertEquals("charmander", comp.getParty().getFirst().getSpecies());
    }

    @Test
    @DisplayName("removeFromParty write-through — PlayerData reflects removal immediately")
    void removeFromPartyWriteThrough() {
        PlayerPartyComponent comp = loadSceneWithParty();

        comp.addToParty(createPokemon("bulbasaur", 5));
        comp.addToParty(createPokemon("charmander", 5));
        comp.removeFromParty(0);

        PlayerData data = PlayerData.load();
        assertEquals(1, data.team.size());
        assertEquals("charmander", data.team.getFirst().species);
    }

    // ========================================================================
    // swapPositions
    // ========================================================================

    @Test
    @DisplayName("swapPositions changes party order correctly")
    void swapPositions() {
        PlayerPartyComponent comp = loadSceneWithParty();

        comp.addToParty(createPokemon("bulbasaur", 5));
        comp.addToParty(createPokemon("charmander", 5));
        comp.addToParty(createPokemon("squirtle", 5));

        comp.swapPositions(0, 2);

        assertEquals("squirtle", comp.getParty().get(0).getSpecies());
        assertEquals("charmander", comp.getParty().get(1).getSpecies());
        assertEquals("bulbasaur", comp.getParty().get(2).getSpecies());
    }

    // ========================================================================
    // getFirstAlive / isTeamAlive
    // ========================================================================

    @Test
    @DisplayName("getFirstAlive returns first non-fainted, null if all fainted")
    void getFirstAlive() {
        PlayerPartyComponent comp = loadSceneWithParty();

        PokemonInstance p1 = createPokemon("bulbasaur", 5);
        PokemonInstance p2 = createPokemon("charmander", 5);
        comp.addToParty(p1);
        comp.addToParty(p2);

        // Both alive — first returned
        assertEquals("bulbasaur", comp.getFirstAlive().getSpecies());

        // Faint first — second returned
        p1.setCurrentHp(0);
        assertEquals("charmander", comp.getFirstAlive().getSpecies());

        // Faint both — null
        p2.setCurrentHp(0);
        assertNull(comp.getFirstAlive());
    }

    @Test
    @DisplayName("isTeamAlive returns true if any alive, false if all fainted")
    void isTeamAlive() {
        PlayerPartyComponent comp = loadSceneWithParty();

        PokemonInstance p1 = createPokemon("bulbasaur", 5);
        comp.addToParty(p1);

        assertTrue(comp.isTeamAlive());
        p1.setCurrentHp(0);
        assertFalse(comp.isTeamAlive());
    }

    // ========================================================================
    // healAll
    // ========================================================================

    @Test
    @DisplayName("healAll restores HP, cures status, restores all move PP")
    void healAll() {
        PlayerPartyComponent comp = loadSceneWithParty();

        PokemonInstance p = createPokemon("bulbasaur", 10);
        comp.addToParty(p);

        // Damage and apply status
        int maxHp = p.calcMaxHp();
        p.damage(maxHp / 2);
        p.setStatusCondition(StatusCondition.POISON);
        p.getMoves().getFirst().usePp();

        comp.healAll();

        assertEquals(maxHp, p.getCurrentHp());
        assertEquals(StatusCondition.NONE, p.getStatusCondition());
        assertEquals(p.getMoves().getFirst().getMaxPp(), p.getMoves().getFirst().getCurrentPp());
    }

    @Test
    @DisplayName("healAll write-through — healed state reflected in PlayerData")
    void healAllWriteThrough() {
        PlayerPartyComponent comp = loadSceneWithParty();

        PokemonInstance p = createPokemon("bulbasaur", 10);
        comp.addToParty(p);

        int maxHp = p.calcMaxHp();
        p.damage(maxHp / 2);
        comp.healAll();

        PlayerData data = PlayerData.load();
        assertEquals(maxHp, data.team.getFirst().currentHp);
    }

    // ========================================================================
    // onStart
    // ========================================================================

    @Test
    @DisplayName("onStart from populated PlayerData — party correctly reconstructed")
    void onStartPopulated() {
        // Pre-populate PlayerData with a team
        PokemonInstance p = createPokemon("bulbasaur", 15);
        PlayerData data = new PlayerData();
        data.team = List.of(PokemonInstanceData.fromPokemonInstance(p));
        data.save();

        PlayerPartyComponent comp = loadSceneWithParty();

        assertEquals(1, comp.partySize());
        assertEquals("bulbasaur", comp.getParty().getFirst().getSpecies());
        assertEquals(15, comp.getParty().getFirst().getLevel());
    }

    @Test
    @DisplayName("onStart from empty PlayerData — empty party, no crash")
    void onStartEmpty() {
        PlayerPartyComponent comp = loadSceneWithParty();

        assertEquals(0, comp.partySize());
        assertNull(comp.getFirstAlive());
        assertFalse(comp.isTeamAlive());
    }

    // ========================================================================
    // Test infrastructure
    // ========================================================================

    private PokemonInstance createPokemon(String speciesId, int level) {
        return PokemonFactory.createWild(testPokedex, speciesId, level);
    }

    static Pokedex createTestPokedex() {
        Pokedex pokedex = new Pokedex();

        pokedex.addSpecies(new PokemonSpecies(
                "bulbasaur", "Bulbasaur", PokemonType.GRASS,
                new Stats(45, 49, 49, 65, 65, 45),
                List.of(new LearnedMove(1, "tackle"), new LearnedMove(3, "growl")),
                64, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/bulbasaur",
                EvolutionMethod.LEVEL, 16, null, "ivysaur"
        ));
        pokedex.addSpecies(new PokemonSpecies(
                "charmander", "Charmander", PokemonType.FIRE,
                new Stats(39, 52, 43, 60, 50, 65),
                List.of(new LearnedMove(1, "scratch"), new LearnedMove(4, "growl")),
                64, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/charmander",
                EvolutionMethod.LEVEL, 16, null, "charmeleon"
        ));
        pokedex.addSpecies(new PokemonSpecies(
                "squirtle", "Squirtle", PokemonType.WATER,
                new Stats(44, 48, 65, 50, 64, 43),
                List.of(new LearnedMove(1, "tackle"), new LearnedMove(4, "tail_whip")),
                64, 45, GrowthRate.MEDIUM_SLOW,
                "sprites/pokemon/squirtle",
                EvolutionMethod.LEVEL, 16, null, "wartortle"
        ));

        pokedex.addMove(new Move("tackle", "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, "", 0, 0));
        pokedex.addMove(new Move("growl", "Growl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 40, "", 0, 0));
        pokedex.addMove(new Move("scratch", "Scratch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, "", 0, 0));
        pokedex.addMove(new Move("tail_whip", "Tail Whip", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 30, "", 0, 0));

        return pokedex;
    }

    private static class TestScene extends Scene {
        private Runnable setupAction;

        public TestScene(String name) { super(name); }

        void setSetupAction(Runnable action) { this.setupAction = action; }

        @Override
        public void onLoad() {
            if (setupAction != null) setupAction.run();
        }
    }

    static class PokedexStubContext implements com.pocket.rpg.resources.AssetContext {
        private final Pokedex pokedex;

        PokedexStubContext(Pokedex pokedex) { this.pokedex = pokedex; }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == Pokedex.class) return (T) pokedex;
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
