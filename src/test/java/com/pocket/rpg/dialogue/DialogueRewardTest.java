package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.dialogue.DialogueEventListener;
import com.pocket.rpg.components.dialogue.DialogueReaction;
import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.components.pokemon.PlayerPartyComponent;
import com.pocket.rpg.components.pokemon.PokemonStorageComponent;
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
 * Tests for GIVE_ITEM, GIVE_POKEMON, and HEAL_PARTY dialogue reactions.
 */
class DialogueRewardTest {

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

        // Set player name for PokemonFactory
        PlayerData data = PlayerData.load();
        data.playerName = "Red";
        data.save();
    }

    // ========================================================================
    // GIVE_ITEM
    // ========================================================================

    @Test
    @DisplayName("GIVE_ITEM adds items to player inventory")
    void giveItemAddsToInventory() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_ITEM);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setItemId("potion");
        listener.setQuantity(3);

        listener.onDialogueEvent();

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(3, inv.getItemCount("potion"));
    }

    @Test
    @DisplayName("GIVE_ITEM with quantity 1 (default)")
    void giveItemDefaultQuantity() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_ITEM);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setItemId("pokeball");

        listener.onDialogueEvent();

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(1, inv.getItemCount("pokeball"));
    }

    @Test
    @DisplayName("GIVE_ITEM with missing PlayerInventoryComponent — no crash")
    void giveItemNoInventory() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player without inventory
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            scene.addGameObject(player);

            // Listener
            GameObject listenerObj = new GameObject("Reward");
            DialogueEventListener listener = new DialogueEventListener();
            listener.setEventName("GIVE_ITEM_TEST");
            listener.setReaction(DialogueReaction.GIVE_ITEM);
            listener.setItemId("potion");
            listener.setQuantity(1);
            listenerObj.addComponent(listener);
            scene.addGameObject(listenerObj);
        });
        sceneManager.loadScene(scene);

        DialogueEventListener listener = scene.findGameObject("Reward")
                .getComponent(DialogueEventListener.class);

        assertDoesNotThrow(() -> listener.onDialogueEvent());
    }

    @Test
    @DisplayName("GIVE_ITEM replay guard — hasFired skips without adding item")
    void giveItemReplayGuard() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_ITEM);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setItemId("potion");
        listener.setQuantity(5);

        // Mark event as already fired
        DialogueEventStore.markFired("REWARD_TEST");

        listener.onDialogueEvent();

        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);
        assertEquals(0, inv.getItemCount("potion"), "Item should NOT be added on replay");
    }

    // ========================================================================
    // GIVE_POKEMON
    // ========================================================================

    @Test
    @DisplayName("GIVE_POKEMON adds Pokemon to party")
    void givePokemonAddsToParty() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_POKEMON);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setSpeciesId("bulbasaur");
        listener.setLevel(10);

        listener.onDialogueEvent();

        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals(1, party.partySize());
        assertEquals("bulbasaur", party.getParty().getFirst().getSpecies());
        assertEquals(10, party.getParty().getFirst().getLevel());
    }

    @Test
    @DisplayName("GIVE_POKEMON sets OT to player name")
    void givePokemonSetsTrainerName() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_POKEMON);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setSpeciesId("charmander");
        listener.setLevel(5);

        listener.onDialogueEvent();

        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals("Red", party.getParty().getFirst().getOriginalTrainer());
    }

    @Test
    @DisplayName("GIVE_POKEMON with full party deposits to PC storage")
    void givePokemonFullPartyGoesToStorage() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_POKEMON);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setSpeciesId("squirtle");
        listener.setLevel(5);

        // Fill party to 6
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        for (int i = 0; i < 6; i++) {
            party.addToParty(PokemonFactory.createWild(testPokedex, "bulbasaur", 5));
        }
        assertEquals(6, party.partySize());

        listener.onDialogueEvent();

        // Party still 6, storage has 1
        assertEquals(6, party.partySize());
        PokemonStorageComponent storage = f.player.getComponent(PokemonStorageComponent.class);
        assertEquals(1, storage.getTotalStored());
        assertEquals("squirtle", storage.getBox(0).getFirst().getSpecies());
    }

    @Test
    @DisplayName("GIVE_POKEMON with full party and full storage — no crash")
    void givePokemonFullPartyFullStorage() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_POKEMON);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setSpeciesId("squirtle");
        listener.setLevel(5);

        // Fill party to 6
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        for (int i = 0; i < 6; i++) {
            party.addToParty(PokemonFactory.createWild(testPokedex, "bulbasaur", 5));
        }

        // Fill all storage boxes
        PokemonStorageComponent storage = f.player.getComponent(PokemonStorageComponent.class);
        for (int box = 0; box < storage.getBoxCount(); box++) {
            for (int slot = 0; slot < PokemonStorageComponent.BOX_CAPACITY; slot++) {
                storage.deposit(PokemonFactory.createWild(testPokedex, "bulbasaur", 5), box);
            }
        }

        assertDoesNotThrow(() -> listener.onDialogueEvent());
        assertEquals(6, party.partySize());
    }

    @Test
    @DisplayName("GIVE_POKEMON with missing PlayerPartyComponent — no crash")
    void givePokemonNoParty() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player without party
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            // Listener
            GameObject listenerObj = new GameObject("Reward");
            DialogueEventListener listener = new DialogueEventListener();
            listener.setEventName("GIVE_POKEMON_TEST");
            listener.setReaction(DialogueReaction.GIVE_POKEMON);
            listener.setSpeciesId("bulbasaur");
            listener.setLevel(5);
            listenerObj.addComponent(listener);
            scene.addGameObject(listenerObj);
        });
        sceneManager.loadScene(scene);

        DialogueEventListener listener = scene.findGameObject("Reward")
                .getComponent(DialogueEventListener.class);

        assertDoesNotThrow(() -> listener.onDialogueEvent());
    }

    @Test
    @DisplayName("GIVE_POKEMON replay guard — hasFired skips without adding Pokemon")
    void givePokemonReplayGuard() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.GIVE_POKEMON);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);
        listener.setSpeciesId("bulbasaur");
        listener.setLevel(5);

        // Mark event as already fired
        DialogueEventStore.markFired("REWARD_TEST");

        listener.onDialogueEvent();

        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        assertEquals(0, party.partySize(), "Pokemon should NOT be added on replay");
    }

    // ========================================================================
    // HEAL_PARTY
    // ========================================================================

    @Test
    @DisplayName("HEAL_PARTY heals all party Pokemon")
    void healPartyHealsAll() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.HEAL_PARTY);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);

        // Add a damaged Pokemon
        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        PokemonInstance p = PokemonFactory.createWild(testPokedex, "bulbasaur", 10);
        party.addToParty(p);
        int maxHp = p.calcMaxHp();
        p.damage(maxHp / 2);
        p.setStatusCondition(StatusCondition.POISON);
        p.getMoves().getFirst().usePp();

        listener.onDialogueEvent();

        assertEquals(maxHp, p.getCurrentHp());
        assertEquals(StatusCondition.NONE, p.getStatusCondition());
        assertEquals(p.getMoves().getFirst().getMaxPp(), p.getMoves().getFirst().getCurrentPp());
    }

    @Test
    @DisplayName("HEAL_PARTY with missing PlayerPartyComponent — no crash")
    void healPartyNoParty() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player without party
            GameObject player = new GameObject("Player");
            scene.addGameObject(player);

            // Listener
            GameObject listenerObj = new GameObject("Reward");
            DialogueEventListener listener = new DialogueEventListener();
            listener.setEventName("HEAL_TEST");
            listener.setReaction(DialogueReaction.HEAL_PARTY);
            listenerObj.addComponent(listener);
            scene.addGameObject(listenerObj);
        });
        sceneManager.loadScene(scene);

        DialogueEventListener listener = scene.findGameObject("Reward")
                .getComponent(DialogueEventListener.class);

        assertDoesNotThrow(() -> listener.onDialogueEvent());
    }

    @Test
    @DisplayName("HEAL_PARTY is idempotent — no replay guard needed, safe to repeat")
    void healPartyIdempotent() {
        SceneFixture f = loadSceneWithReward(DialogueReaction.HEAL_PARTY);
        DialogueEventListener listener = f.listener.getComponent(DialogueEventListener.class);

        PlayerPartyComponent party = f.player.getComponent(PlayerPartyComponent.class);
        PokemonInstance p = PokemonFactory.createWild(testPokedex, "bulbasaur", 10);
        party.addToParty(p);
        int maxHp = p.calcMaxHp();
        p.damage(maxHp / 2);

        // Mark as fired — HEAL_PARTY should still heal (no replay guard)
        DialogueEventStore.markFired("REWARD_TEST");

        listener.onDialogueEvent();

        assertEquals(maxHp, p.getCurrentHp(), "Heal should work even on replay");
    }

    // ========================================================================
    // SCENE SETUP
    // ========================================================================

    private record SceneFixture(Scene scene, GameObject player, GameObject listener) {}

    private SceneFixture loadSceneWithReward(DialogueReaction reaction) {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player with all components
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            player.addComponent(new PokemonStorageComponent());
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            // Listener
            GameObject listenerObj = new GameObject("Reward");
            DialogueEventListener listener = new DialogueEventListener();
            listener.setEventName("REWARD_TEST");
            listener.setReaction(reaction);
            listenerObj.addComponent(listener);
            scene.addGameObject(listenerObj);
        });
        sceneManager.loadScene(scene);
        return new SceneFixture(
                scene,
                scene.findGameObject("Player"),
                scene.findGameObject("Reward")
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

    /**
     * Stub AssetContext that returns both a Pokedex and an ItemRegistry.
     */
    static class CombinedStubContext implements com.pocket.rpg.resources.AssetContext {
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
