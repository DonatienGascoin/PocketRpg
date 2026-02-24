package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.items.*;
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
 * Interaction tests for ItemPickup component.
 * Validates pickup adds items, destroy/disable behavior, and full-pocket rejection.
 */
class ItemPickupTest {

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

    private record SceneFixture(Scene scene, GameObject player, GameObject item) {}

    private SceneFixture loadScene(String itemId, int quantity, boolean destroyOnPickup) {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player with inventory
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            // Item pickup
            GameObject item = new GameObject("Item");
            item.addComponent(new TriggerZone());
            ItemPickup pickup = new ItemPickup();
            pickup.setItemId(itemId);
            pickup.setQuantity(quantity);
            pickup.setDestroyOnPickup(destroyOnPickup);
            pickup.setDirectionalInteraction(false); // disable direction checks for test
            item.addComponent(pickup);
            scene.addGameObject(item);
        });
        sceneManager.loadScene(scene);
        return new SceneFixture(
                scene,
                scene.findGameObject("Player"),
                scene.findGameObject("Item")
        );
    }

    @Test
    @DisplayName("interact adds item to player inventory")
    void interactAddsItem() {
        SceneFixture f = loadScene("potion", 3, true);
        ItemPickup pickup = f.item.getComponent(ItemPickup.class);
        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);

        pickup.interact(f.player);

        assertEquals(3, inv.getItemCount("potion"));
    }

    @Test
    @DisplayName("interact with destroyOnPickup=true removes GameObject from scene")
    void destroyOnPickupRemoves() {
        SceneFixture f = loadScene("potion", 1, true);
        ItemPickup pickup = f.item.getComponent(ItemPickup.class);

        pickup.interact(f.player);

        // GameObject should be removed from scene
        assertNull(f.scene.findGameObject("Item"));
    }

    @Test
    @DisplayName("interact with destroyOnPickup=false disables GameObject")
    void disableOnPickup() {
        SceneFixture f = loadScene("potion", 1, false);
        ItemPickup pickup = f.item.getComponent(ItemPickup.class);

        pickup.interact(f.player);

        // GameObject should still exist but be disabled
        GameObject item = f.scene.findGameObject("Item");
        assertNotNull(item);
        assertFalse(item.isEnabled());
    }

    @Test
    @DisplayName("interact with full pocket does NOT add item, GameObject stays active")
    void fullPocketStays() {
        SceneFixture f = loadScene("potion", 1, true);
        PlayerInventoryComponent inv = f.player.getComponent(PlayerInventoryComponent.class);

        // Fill the MEDICINE pocket to capacity
        for (int i = 0; i < Inventory.POCKET_CAPACITY; i++) {
            testRegistry.addItem(ItemDefinition.builder("fill_" + i, "Fill " + i, ItemCategory.MEDICINE)
                    .price(100).sellPrice(50).usableInBattle(true).usableOutside(true).consumable(true)
                    .effect(ItemEffect.HEAL_HP).effectValue(10).build());
            inv.addItem("fill_" + i, 1);
        }

        // Now try to interact — pocket is full, potion is a new item
        ItemPickup pickup = f.item.getComponent(ItemPickup.class);
        pickup.interact(f.player);

        // Item should NOT have been added, pickup should still exist
        assertEquals(0, inv.getItemCount("potion"));
        assertNotNull(f.scene.findGameObject("Item"));
        assertTrue(f.item.isEnabled());
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
