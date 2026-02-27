package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.items.*;
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

/**
 * Tests for DebugStarterGiver component.
 */
class DebugStarterGiverTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static Pokedex testPokedex;
    private static ItemRegistry testRegistry;

    @BeforeAll
    static void initSerializer() {
        testPokedex = createTestPokedex();
        testRegistry = createTestItemRegistry();
        com.pocket.rpg.resources.Assets.setContext(new CombinedStubContext(testPokedex, testRegistry));
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

    // ========================================================================
    // GIVE POKEMON
    // ========================================================================

    @Test
    @DisplayName("interact gives Pokemon to player party")
    void interactGivesPokemon() {
        SceneFixture f = loadScene("bulbasaur", 5, true);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);

        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals(1, party.partySize());
        assertEquals("bulbasaur", party.getParty().getFirst().getSpecies());
        assertEquals(5, party.getParty().getFirst().getLevel());
    }

    @Test
    @DisplayName("interact sets player name to 'Red' if not set")
    void interactSetsDefaultPlayerName() {
        SceneFixture f = loadScene("charmander", 5, false);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);

        assertEquals("Red", PlayerData.load().playerName);
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals("Red", party.getParty().getFirst().getOriginalTrainer());
    }

    @Test
    @DisplayName("interact preserves existing player name")
    void interactPreservesPlayerName() {
        PlayerData data = PlayerData.load();
        data.playerName = "Ash";
        data.save();

        SceneFixture f = loadScene("squirtle", 10, false);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);

        assertEquals("Ash", PlayerData.load().playerName);
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals("Ash", party.getParty().getFirst().getOriginalTrainer());
    }

    @Test
    @DisplayName("interact with full party does not add Pokemon")
    void interactFullParty() {
        SceneFixture f = loadScene("squirtle", 5, false);
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);

        // Fill party to 6
        for (int i = 0; i < 6; i++) {
            party.addToParty(PokemonFactory.createWild(testPokedex, "bulbasaur", 5));
        }

        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);
        giver.interact(f.player);

        assertEquals(6, party.partySize(), "Party should still be 6");
    }

    // ========================================================================
    // STARTER KIT
    // ========================================================================

    @Test
    @DisplayName("interact with giveStarterKit adds items and money")
    void interactGivesStarterKit() {
        SceneFixture f = loadScene("bulbasaur", 5, true);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(5, inv.getItemCount("potion"));
        assertEquals(5, inv.getItemCount("pokeball"));
        assertEquals(3000, inv.getMoney());
    }

    @Test
    @DisplayName("starter kit only given once — second interact skips items")
    void starterKitOnlyOnce() {
        SceneFixture f = loadScene("bulbasaur", 5, true);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);
        giver.interact(f.player);

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(5, inv.getItemCount("potion"), "Should still be 5, not 10");
        assertEquals(5, inv.getItemCount("pokeball"));
        assertEquals(3000, inv.getMoney(), "Should still be 3000, not 6000");
    }

    @Test
    @DisplayName("interact with giveStarterKit=false skips items and money")
    void interactNoStarterKit() {
        SceneFixture f = loadScene("bulbasaur", 5, false);
        DebugStarterGiver giver = f.giver.getComponent(DebugStarterGiver.class);

        giver.interact(f.player);

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(0, inv.getItemCount("potion"));
        assertEquals(0, inv.getItemCount("pokeball"));
        assertEquals(0, inv.getMoney());
    }

    // ========================================================================
    // EDGE CASES
    // ========================================================================

    @Test
    @DisplayName("interact without PlayerPartyComponent — no crash, kit still given")
    void interactNoPartyComponent() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            GameObject giverObj = new GameObject("Giver");
            DebugStarterGiver giver = new DebugStarterGiver();
            giver.setSpeciesId("bulbasaur");
            giver.setLevel(5);
            giver.setGiveStarterKit(true);
            giverObj.addComponent(giver);
            scene.addGameObject(giverObj);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        DebugStarterGiver giver = scene.findGameObject("Giver").getComponent(DebugStarterGiver.class);

        assertDoesNotThrow(() -> giver.interact(player));

        // Kit should still be given
        PlayerInventoryComponent inv = player.getComponent(PlayerInventoryComponent.class);
        assertEquals(5, inv.getItemCount("potion"));
        assertEquals(3000, inv.getMoney());
    }

    @Test
    @DisplayName("getInteractionPrompt returns 'Get Pokemon'")
    void getInteractionPrompt() {
        assertEquals("Get Pokemon", new DebugStarterGiver().getInteractionPrompt());
    }

    // ========================================================================
    // SCENE SETUP
    // ========================================================================

    private record SceneFixture(Scene scene, GameObject player, GameObject giver) {}

    private SceneFixture loadScene(String speciesId, int level, boolean giveKit) {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            GameObject giverObj = new GameObject("Giver");
            DebugStarterGiver giver = new DebugStarterGiver();
            giver.setSpeciesId(speciesId);
            giver.setLevel(level);
            giver.setGiveStarterKit(giveKit);
            giverObj.addComponent(giver);
            scene.addGameObject(giverObj);
        });
        sceneManager.loadScene(scene);
        return new SceneFixture(
                scene,
                scene.findGameObject("Player"),
                scene.findGameObject("Giver")
        );
    }

    // ========================================================================
    // TEST DATA
    // ========================================================================

    private static Pokedex createTestPokedex() {
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

    private static ItemRegistry createTestItemRegistry() {
        ItemRegistry registry = new ItemRegistry();
        registry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .price(200).sellPrice(100).usableInBattle(true).usableOutside(true)
                .consumable(true).effect(ItemEffect.HEAL_HP).effectValue(20).build());
        registry.addItem(ItemDefinition.builder("pokeball", "Poke Ball", ItemCategory.POKEBALL)
                .price(200).sellPrice(100).usableInBattle(true).consumable(true)
                .effect(ItemEffect.CAPTURE).effectValue(1).build());
        return registry;
    }

    // ========================================================================
    // TEST INFRASTRUCTURE
    // ========================================================================

    private static class TestScene extends Scene {
        private Runnable setupAction;
        public TestScene(String name) { super(name); }
        void setSetupAction(Runnable action) { this.setupAction = action; }
        @Override public void onLoad() { if (setupAction != null) setupAction.run(); }
    }

    private static class CombinedStubContext implements com.pocket.rpg.resources.AssetContext {
        private final Pokedex pokedex;
        private final ItemRegistry itemRegistry;

        CombinedStubContext(Pokedex pokedex, ItemRegistry itemRegistry) {
            this.pokedex = pokedex;
            this.itemRegistry = itemRegistry;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == Pokedex.class) return (T) pokedex;
            if (type == ItemRegistry.class) return (T) itemRegistry;
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
