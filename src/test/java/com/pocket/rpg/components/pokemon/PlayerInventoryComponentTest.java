package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.items.*;
import com.pocket.rpg.save.PlayerData;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Component-level tests for PlayerInventoryComponent.
 * Validates write-through persistence and onStart initialization.
 */
class PlayerInventoryComponentTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static ItemRegistry testRegistry;

    @BeforeAll
    static void initSerializer() {
        testRegistry = new ItemRegistry();
        testRegistry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .price(200).sellPrice(100).usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_HP).effectValue(20).build());
        testRegistry.addItem(ItemDefinition.builder("pokeball", "Poke Ball", ItemCategory.POKEBALL)
                .price(200).sellPrice(100).usableInBattle(true).consumable(true)
                .effect(ItemEffect.CAPTURE).effectValue(1).build());

        com.pocket.rpg.resources.Assets.setContext(new ItemRegistryStubContext(testRegistry));
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

    private PlayerInventoryComponent loadSceneWithInventory() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);
        });
        sceneManager.loadScene(scene);
        return scene.findGameObject("Player").getComponent(PlayerInventoryComponent.class);
    }

    @Test
    @DisplayName("addItem writes through to PlayerData immediately")
    void writeThroughAddItem() {
        PlayerInventoryComponent inv = loadSceneWithInventory();

        int added = inv.addItem("potion", 3);
        assertEquals(3, added);

        // Verify write-through
        PlayerData data = PlayerData.load();
        assertNotNull(data.inventory);
        Inventory restored = Inventory.fromSaveData(data.inventory);
        assertEquals(3, restored.getCount("potion"));
    }

    @Test
    @DisplayName("spendMoney writes through to PlayerData immediately")
    void writeThroughSpendMoney() {
        PlayerInventoryComponent inv = loadSceneWithInventory();

        inv.addMoney(500);
        assertEquals(500, inv.getMoney());

        assertTrue(inv.spendMoney(200));
        assertEquals(300, inv.getMoney());

        // Verify write-through
        PlayerData data = PlayerData.load();
        assertEquals(300, data.money);
    }

    @Test
    @DisplayName("spendMoney with insufficient funds returns false, no change")
    void spendMoneyInsufficient() {
        PlayerInventoryComponent inv = loadSceneWithInventory();

        inv.addMoney(100);
        assertFalse(inv.spendMoney(200));
        assertEquals(100, inv.getMoney());

        // PlayerData should still show 100
        PlayerData data = PlayerData.load();
        assertEquals(100, data.money);
    }

    @Test
    @DisplayName("onStart populates from existing PlayerData")
    void onStartPopulated() {
        // Pre-populate PlayerData with inventory and money
        PlayerData data = new PlayerData();
        data.money = 750;
        Inventory inv = new Inventory();
        inv.addItem("potion", 5, testRegistry);
        data.inventory = inv.toSaveData();
        data.save();

        // Load scene — PlayerInventoryComponent.onStart() should read from PlayerData
        PlayerInventoryComponent component = loadSceneWithInventory();

        assertEquals(750, component.getMoney());
        assertEquals(5, component.getItemCount("potion"));
    }

    @Test
    @DisplayName("onStart with empty PlayerData yields clean defaults")
    void onStartEmpty() {
        // SaveManager.newGame() already clears state
        PlayerInventoryComponent component = loadSceneWithInventory();

        assertEquals(0, component.getMoney());
        assertFalse(component.hasItem("potion"));
        assertEquals(0, component.getInventory().getAllItems().size());
    }

    // ========================================================================
    // Test infrastructure
    // ========================================================================

    private static class TestScene extends Scene {
        private Runnable setupAction;

        public TestScene(String name) {
            super(name);
        }

        void setSetupAction(Runnable action) {
            this.setupAction = action;
        }

        @Override
        public void onLoad() {
            if (setupAction != null) setupAction.run();
        }
    }

    /**
     * StubAssetContext that returns a test ItemRegistry for the items path.
     * All other asset loads return null.
     */
    private static class ItemRegistryStubContext implements com.pocket.rpg.resources.AssetContext {
        private final ItemRegistry registry;

        ItemRegistryStubContext(ItemRegistry registry) {
            this.registry = registry;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == ItemRegistry.class) return (T) registry;
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
