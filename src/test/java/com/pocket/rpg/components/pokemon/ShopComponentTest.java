package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.interaction.TriggerZone;
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
import com.pocket.rpg.shop.ShopInventory;
import com.pocket.rpg.shop.ShopRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShopComponent interaction behavior.
 */
class ShopComponentTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static ItemRegistry testRegistry;
    private static ShopRegistry testShopRegistry;

    @BeforeAll
    static void initSerializer() {
        testRegistry = createTestItemRegistry();
        testShopRegistry = createTestShopRegistry();
        com.pocket.rpg.resources.Assets.setContext(new ShopStubContext(testRegistry, testShopRegistry));
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
    // INTERACT
    // ========================================================================

    @Test
    @DisplayName("interact with valid shopId — no crash")
    void interactValidShop() {
        SceneFixture f = loadScene("viridian_pokemart");

        ShopComponent shop = f.shopkeeper.getComponent(ShopComponent.class);
        assertDoesNotThrow(() -> shop.interact(f.player));
    }

    @Test
    @DisplayName("interact with null shopId — no crash")
    void interactNullShopId() {
        SceneFixture f = loadScene(null);

        ShopComponent shop = f.shopkeeper.getComponent(ShopComponent.class);
        assertDoesNotThrow(() -> shop.interact(f.player));
    }

    @Test
    @DisplayName("interact with empty shopId — no crash")
    void interactEmptyShopId() {
        SceneFixture f = loadScene("");

        ShopComponent shop = f.shopkeeper.getComponent(ShopComponent.class);
        assertDoesNotThrow(() -> shop.interact(f.player));
    }

    @Test
    @DisplayName("interact with unknown shopId — no crash")
    void interactUnknownShopId() {
        SceneFixture f = loadScene("nonexistent_shop");

        ShopComponent shop = f.shopkeeper.getComponent(ShopComponent.class);
        assertDoesNotThrow(() -> shop.interact(f.player));
    }

    @Test
    @DisplayName("interact without PlayerInventoryComponent — no crash")
    void interactNoInventory() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            // Player without inventory
            GameObject player = new GameObject("Player");
            scene.addGameObject(player);

            GameObject shopkeeper = new GameObject("Shopkeeper");
            shopkeeper.addComponent(new TriggerZone());
            ShopComponent shop = new ShopComponent();
            shop.setShopId("viridian_pokemart");
            shop.setDirectionalInteraction(false);
            shopkeeper.addComponent(shop);
            scene.addGameObject(shopkeeper);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        ShopComponent shop = scene.findGameObject("Shopkeeper").getComponent(ShopComponent.class);

        assertDoesNotThrow(() -> shop.interact(player));
    }

    @Test
    @DisplayName("getInteractionPrompt returns 'Shop'")
    void getInteractionPrompt() {
        assertEquals("Shop", new ShopComponent().getInteractionPrompt());
    }

    // ========================================================================
    // SCENE SETUP
    // ========================================================================

    private record SceneFixture(Scene scene, GameObject player, GameObject shopkeeper) {}

    private SceneFixture loadScene(String shopId) {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerInventoryComponent());
            scene.addGameObject(player);

            GameObject shopkeeper = new GameObject("Shopkeeper");
            shopkeeper.addComponent(new TriggerZone());
            ShopComponent shop = new ShopComponent();
            shop.setShopId(shopId);
            shop.setDirectionalInteraction(false);
            shopkeeper.addComponent(shop);
            scene.addGameObject(shopkeeper);
        });
        sceneManager.loadScene(scene);
        return new SceneFixture(
                scene,
                scene.findGameObject("Player"),
                scene.findGameObject("Shopkeeper")
        );
    }

    // ========================================================================
    // TEST DATA
    // ========================================================================

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

    private static ShopRegistry createTestShopRegistry() {
        ShopRegistry registry = new ShopRegistry();
        registry.addShop(new ShopInventory("viridian_pokemart", "Viridian City Pokemart",
                List.of(
                        new ShopInventory.ShopEntry("potion", -1),
                        new ShopInventory.ShopEntry("pokeball", -1)
                )));
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

    private static class ShopStubContext implements com.pocket.rpg.resources.AssetContext {
        private final ItemRegistry itemRegistry;
        private final ShopRegistry shopRegistry;

        ShopStubContext(ItemRegistry itemRegistry, ShopRegistry shopRegistry) {
            this.itemRegistry = itemRegistry;
            this.shopRegistry = shopRegistry;
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T load(String path, Class<T> type) {
            if (type == ItemRegistry.class) return (T) itemRegistry;
            if (type == ShopRegistry.class) return (T) shopRegistry;
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
