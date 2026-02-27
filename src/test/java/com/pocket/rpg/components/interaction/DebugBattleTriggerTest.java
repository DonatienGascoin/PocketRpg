package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.components.pokemon.TrainerComponent;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.items.*;
import com.pocket.rpg.pokemon.*;
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
 * Tests for DebugBattleTrigger component.
 */
class DebugBattleTriggerTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static Pokedex testPokedex;
    private static ItemRegistry testRegistry;
    private static TrainerRegistry testTrainerRegistry;

    @BeforeAll
    static void initSerializer() {
        testPokedex = createTestPokedex();
        testRegistry = createTestItemRegistry();
        testTrainerRegistry = createTestTrainerRegistry();
        com.pocket.rpg.resources.Assets.setContext(
                new FullStubContext(testPokedex, testRegistry, testTrainerRegistry));
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
    // canInteract
    // ========================================================================

    @Test
    @DisplayName("canInteract returns true when trainer is not defeated")
    void canInteractNotDefeated() {
        SceneFixture f = loadScene();
        DebugBattleTrigger trigger = f.rival.getComponent(DebugBattleTrigger.class);

        assertTrue(trigger.canInteract(f.player));
    }

    @Test
    @DisplayName("canInteract returns false when trainer is defeated")
    void canInteractDefeated() {
        SceneFixture f = loadScene();
        TrainerComponent trainer = f.rival.getComponent(TrainerComponent.class);
        trainer.markDefeated();

        DebugBattleTrigger trigger = f.rival.getComponent(DebugBattleTrigger.class);
        assertFalse(trigger.canInteract(f.player));
    }

    // ========================================================================
    // interact
    // ========================================================================

    @Test
    @DisplayName("interact marks trainer defeated and awards money")
    void interactMarksDefeatedAndAwardsMoney() {
        SceneFixture f = loadScene();
        DebugBattleTrigger trigger = f.rival.getComponent(DebugBattleTrigger.class);
        TrainerComponent trainer = f.rival.getComponent(TrainerComponent.class);

        // Give player a Pokemon so they have a party
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        party.addToParty(PokemonFactory.createWild(testPokedex, "bulbasaur", 10));

        try {
            trigger.interact(f.player);
        } catch (Exception e) {
            // SceneTransition.loadScene throws since TransitionManager is not initialized
        }

        assertTrue(trainer.isDefeated());
        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(500, inv.getMoney());
    }

    @Test
    @DisplayName("interact with already-defeated trainer does nothing")
    void interactAlreadyDefeated() {
        SceneFixture f = loadScene();
        TrainerComponent trainer = f.rival.getComponent(TrainerComponent.class);
        trainer.markDefeated();

        DebugBattleTrigger trigger = f.rival.getComponent(DebugBattleTrigger.class);

        assertDoesNotThrow(() -> trigger.interact(f.player));

        // Money should not be awarded again
        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(0, inv.getMoney());
    }

    @Test
    @DisplayName("interact without TrainerComponent — no crash")
    void interactNoTrainer() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            // Rival without TrainerComponent
            GameObject rival = new GameObject("Rival");
            rival.addComponent(new TriggerZone());
            DebugBattleTrigger trigger = new DebugBattleTrigger();
            trigger.setDirectionalInteraction(false);
            rival.addComponent(trigger);
            scene.addGameObject(rival);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        DebugBattleTrigger trigger = scene.findGameObject("Rival")
                .getComponent(DebugBattleTrigger.class);

        assertDoesNotThrow(() -> trigger.interact(player));
    }

    @Test
    @DisplayName("interact without player party — trainer still defeated, no money (no inventory)")
    void interactNoPlayerParty() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player without party or inventory
            GameObject player = new GameObject("Player");
            scene.addGameObject(player);

            GameObject rival = new GameObject("Rival");
            rival.addComponent(new TriggerZone());
            TrainerComponent trainer = new TrainerComponent();
            trainer.setTrainerId("rival_blue");
            rival.addComponent(trainer);
            DebugBattleTrigger trigger = new DebugBattleTrigger();
            trigger.setDirectionalInteraction(false);
            rival.addComponent(trigger);
            scene.addGameObject(rival);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        DebugBattleTrigger trigger = scene.findGameObject("Rival")
                .getComponent(DebugBattleTrigger.class);

        try {
            trigger.interact(player);
        } catch (Exception e) {
            // SceneTransition.loadScene throws
        }

        TrainerComponent trainer = scene.findGameObject("Rival").getComponent(TrainerComponent.class);
        assertTrue(trainer.isDefeated());
    }

    @Test
    @DisplayName("getInteractionPrompt returns 'Battle'")
    void getInteractionPrompt() {
        assertEquals("Battle", new DebugBattleTrigger().getInteractionPrompt());
    }

    // ========================================================================
    // SCENE SETUP
    // ========================================================================

    private record SceneFixture(Scene scene, GameObject player, GameObject rival) {}

    private SceneFixture loadScene() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            GameObject rival = new GameObject("Rival");
            rival.addComponent(new TriggerZone());
            TrainerComponent trainer = new TrainerComponent();
            trainer.setTrainerId("rival_blue");
            rival.addComponent(trainer);
            DebugBattleTrigger trigger = new DebugBattleTrigger();
            trigger.setDirectionalInteraction(false);
            rival.addComponent(trigger);
            scene.addGameObject(rival);
        });
        sceneManager.loadScene(scene);
        return new SceneFixture(
                scene,
                scene.findGameObject("Player"),
                scene.findGameObject("Rival")
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
        pokedex.addMove(new Move("tackle", "Tackle", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, "", 0, 0));
        pokedex.addMove(new Move("growl", "Growl", PokemonType.NORMAL, MoveCategory.STATUS, 0, 100, 40, "", 0, 0));
        pokedex.addMove(new Move("scratch", "Scratch", PokemonType.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, "", 0, 0));
        return pokedex;
    }

    private static ItemRegistry createTestItemRegistry() {
        ItemRegistry registry = new ItemRegistry();
        registry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .price(200).sellPrice(100).usableInBattle(true).usableOutside(true)
                .consumable(true).effect(ItemEffect.HEAL_HP).effectValue(20).build());
        return registry;
    }

    private static TrainerRegistry createTestTrainerRegistry() {
        TrainerRegistry registry = new TrainerRegistry();
        TrainerDefinition blue = new TrainerDefinition("rival_blue");
        blue.setTrainerName("Blue");
        blue.setDefeatMoney(500);
        blue.getParty().add(new TrainerPokemonSpec("charmander", 8, null));
        registry.addTrainer(blue);
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

    private static class FullStubContext implements com.pocket.rpg.resources.AssetContext {
        private final Pokedex pokedex;
        private final ItemRegistry itemRegistry;
        private final TrainerRegistry trainerRegistry;

        FullStubContext(Pokedex pokedex, ItemRegistry itemRegistry, TrainerRegistry trainerRegistry) {
            this.pokedex = pokedex;
            this.itemRegistry = itemRegistry;
            this.trainerRegistry = trainerRegistry;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == Pokedex.class) return (T) pokedex;
            if (type == ItemRegistry.class) return (T) itemRegistry;
            if (type == TrainerRegistry.class) return (T) trainerRegistry;
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
