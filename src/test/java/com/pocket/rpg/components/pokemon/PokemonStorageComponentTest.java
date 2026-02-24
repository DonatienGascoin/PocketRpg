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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PokemonStorageComponentTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static Pokedex testPokedex;

    @BeforeAll
    static void initSerializer() {
        testPokedex = PlayerPartyComponentTest.createTestPokedex();
        com.pocket.rpg.resources.Assets.setContext(new PlayerPartyComponentTest.PokedexStubContext(testPokedex));
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

    private PokemonStorageComponent loadSceneWithStorage() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PokemonStorageComponent());
            scene.addGameObject(player);
        });
        sceneManager.loadScene(scene);
        return scene.findGameObject("Player").getComponent(PokemonStorageComponent.class);
    }

    // ========================================================================
    // deposit
    // ========================================================================

    @Test
    @DisplayName("deposit adds Pokemon to specified box")
    void depositToBox() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        assertTrue(storage.deposit(createPokemon("bulbasaur", 5), 0));
        assertEquals(1, storage.getBox(0).size());
        assertEquals("bulbasaur", storage.getBox(0).getFirst().getSpecies());
    }

    @Test
    @DisplayName("deposit returns false if box is full (30)")
    void depositBoxFull() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        for (int i = 0; i < PokemonStorageComponent.BOX_CAPACITY; i++) {
            assertTrue(storage.deposit(createPokemon("bulbasaur", 5), 0));
        }
        assertFalse(storage.deposit(createPokemon("bulbasaur", 5), 0));
    }

    @Test
    @DisplayName("deposit write-through — PlayerData reflects deposit immediately")
    void depositWriteThrough() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.deposit(createPokemon("bulbasaur", 10), 2);

        PlayerData data = PlayerData.load();
        assertNotNull(data.boxes);
        assertEquals(1, data.boxes.get(2).size());
        assertEquals("bulbasaur", data.boxes.get(2).getFirst().species);
    }

    // ========================================================================
    // depositToFirstAvailable
    // ========================================================================

    @Test
    @DisplayName("depositToFirstAvailable finds first box with space across all boxes")
    void depositToFirstAvailable() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        // Fill box 0
        for (int i = 0; i < PokemonStorageComponent.BOX_CAPACITY; i++) {
            storage.deposit(createPokemon("bulbasaur", 5), 0);
        }

        // Should go to box 1
        assertTrue(storage.depositToFirstAvailable(createPokemon("charmander", 5)));
        assertEquals(1, storage.getBox(1).size());
        assertEquals("charmander", storage.getBox(1).getFirst().getSpecies());
    }

    @Test
    @DisplayName("depositToFirstAvailable returns false when all boxes full")
    void depositToFirstAvailableAllFull() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        // Fill all boxes
        for (int b = 0; b < PokemonStorageComponent.DEFAULT_BOX_COUNT; b++) {
            for (int s = 0; s < PokemonStorageComponent.BOX_CAPACITY; s++) {
                storage.deposit(createPokemon("bulbasaur", 5), b);
            }
        }

        assertFalse(storage.depositToFirstAvailable(createPokemon("bulbasaur", 5)));
        assertEquals(PokemonStorageComponent.DEFAULT_BOX_COUNT * PokemonStorageComponent.BOX_CAPACITY,
                storage.getTotalStored());
    }

    // ========================================================================
    // withdraw
    // ========================================================================

    @Test
    @DisplayName("withdraw returns Pokemon from specified box+slot")
    void withdraw() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.deposit(createPokemon("bulbasaur", 5), 0);
        storage.deposit(createPokemon("charmander", 5), 0);

        PokemonInstance withdrawn = storage.withdraw(0, 0);
        assertEquals("bulbasaur", withdrawn.getSpecies());
        assertEquals(1, storage.getBox(0).size());
    }

    @Test
    @DisplayName("withdraw write-through — PlayerData reflects withdrawal immediately")
    void withdrawWriteThrough() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.deposit(createPokemon("bulbasaur", 5), 0);
        storage.withdraw(0, 0);

        PlayerData data = PlayerData.load();
        assertEquals(0, data.boxes.getFirst().size());
    }

    // ========================================================================
    // Box operations
    // ========================================================================

    @Test
    @DisplayName("getBox returns unmodifiable view")
    void getBoxUnmodifiable() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        assertThrows(UnsupportedOperationException.class,
                () -> storage.getBox(0).add(createPokemon("bulbasaur", 5)));
    }

    @Test
    @DisplayName("setBoxName updates name and persists via write-through")
    void setBoxName() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.setBoxName(0, "Fire Types");
        assertEquals("Fire Types", storage.getBoxName(0));

        PlayerData data = PlayerData.load();
        assertEquals("Fire Types", data.boxNames.getFirst());
    }

    @Test
    @DisplayName("getBoxCount returns 8 (DEFAULT_BOX_COUNT)")
    void getBoxCount() {
        PokemonStorageComponent storage = loadSceneWithStorage();
        assertEquals(PokemonStorageComponent.DEFAULT_BOX_COUNT, storage.getBoxCount());
    }

    @Test
    @DisplayName("getTotalStored returns correct count across all boxes")
    void getTotalStored() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.deposit(createPokemon("bulbasaur", 5), 0);
        storage.deposit(createPokemon("charmander", 5), 0);
        storage.deposit(createPokemon("squirtle", 5), 3);

        assertEquals(3, storage.getTotalStored());
    }

    @Test
    @DisplayName("findPokemon locates by speciesId, returns box+slot; null if not found")
    void findPokemon() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        storage.deposit(createPokemon("bulbasaur", 5), 0);
        storage.deposit(createPokemon("charmander", 5), 2);

        int[] location = storage.findPokemon("charmander");
        assertNotNull(location);
        assertEquals(2, location[0]); // box index
        assertEquals(0, location[1]); // slot index

        assertNull(storage.findPokemon("squirtle"));
    }

    // ========================================================================
    // onStart
    // ========================================================================

    @Test
    @DisplayName("onStart from populated PlayerData — boxes and names correctly reconstructed")
    void onStartPopulated() {
        PlayerData data = new PlayerData();
        data.boxes = new ArrayList<>();
        for (int i = 0; i < PokemonStorageComponent.DEFAULT_BOX_COUNT; i++) {
            data.boxes.add(new ArrayList<>());
        }
        data.boxes.getFirst().add(PokemonInstanceData.fromPokemonInstance(createPokemon("bulbasaur", 20)));
        data.boxNames = new ArrayList<>(List.of("A", "B", "C", "D", "E", "F", "G", "H"));
        data.save();

        PokemonStorageComponent storage = loadSceneWithStorage();

        assertEquals(1, storage.getBox(0).size());
        assertEquals("bulbasaur", storage.getBox(0).getFirst().getSpecies());
        assertEquals("A", storage.getBoxName(0));
    }

    @Test
    @DisplayName("onStart from empty PlayerData — 8 empty boxes with default names")
    void onStartEmpty() {
        PokemonStorageComponent storage = loadSceneWithStorage();

        assertEquals(PokemonStorageComponent.DEFAULT_BOX_COUNT, storage.getBoxCount());
        assertEquals(0, storage.getTotalStored());
        assertEquals("Box 1", storage.getBoxName(0));
        assertEquals("Box 8", storage.getBoxName(7));
    }

    // ========================================================================
    // Test infrastructure
    // ========================================================================

    private PokemonInstance createPokemon(String speciesId, int level) {
        return PokemonFactory.createWild(testPokedex, speciesId, level);
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
}
