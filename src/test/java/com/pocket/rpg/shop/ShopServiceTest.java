package com.pocket.rpg.shop;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
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
 * Tests for ShopService buy/sell logic and stock persistence.
 */
class ShopServiceTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static ItemRegistry testRegistry;

    private PlayerInventoryComponent playerInventory;

    @BeforeAll
    static void initSerializer() {
        testRegistry = new ItemRegistry();
        testRegistry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .price(200).sellPrice(100).usableInBattle(true).usableOutside(true)
                .consumable(true).effect(ItemEffect.HEAL_HP).effectValue(20).build());
        testRegistry.addItem(ItemDefinition.builder("pokeball", "Poke Ball", ItemCategory.POKEBALL)
                .price(200).sellPrice(100).usableInBattle(true).consumable(true)
                .effect(ItemEffect.CAPTURE).effectValue(1).build());
        testRegistry.addItem(ItemDefinition.builder("super_potion", "Super Potion", ItemCategory.MEDICINE)
                .price(700).sellPrice(350).usableInBattle(true).usableOutside(true)
                .consumable(true).effect(ItemEffect.HEAL_HP).effectValue(60).build());
        testRegistry.addItem(ItemDefinition.builder("bicycle", "Bicycle", ItemCategory.KEY_ITEM)
                .price(0).sellPrice(0).usableOutside(true).consumable(false)
                .stackLimit(1).effect(ItemEffect.TOGGLE_BICYCLE).build());
        testRegistry.addItem(ItemDefinition.builder("repel", "Repel", ItemCategory.BATTLE)
                .price(350).sellPrice(175).usableOutside(true).consumable(true)
                .effect(ItemEffect.REPEL).effectValue(100).build());
        testRegistry.addItem(ItemDefinition.builder("rare_candy", "Rare Candy", ItemCategory.MEDICINE)
                .price(4800).sellPrice(0).usableOutside(true).consumable(true)
                .effect(ItemEffect.NONE).build());

        com.pocket.rpg.resources.Assets.setContext(new StubAssetContext(testRegistry));
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

        // Give player starting money
        PlayerData data = new PlayerData();
        data.money = 5000;
        data.save();

        playerInventory = loadSceneWithInventory();
    }

    // ========================================================================
    // BUY FLOW TESTS
    // ========================================================================

    @Test
    @DisplayName("buy success — money deducted, item added")
    void buySuccess() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("potion", -1);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 3, playerInventory);

        assertEquals(ShopService.TransactionResult.SUCCESS, result);
        assertEquals(5000 - 200 * 3, playerInventory.getMoney());
        assertEquals(3, playerInventory.getItemCount("potion"));
    }

    @Test
    @DisplayName("buy insufficient money — no state change")
    void buyInsufficientMoney() {
        // Set money to only 100
        playerInventory.addMoney(-5000 + 100); // hack: spend to leave 100
        playerInventory.spendMoney(playerInventory.getMoney());
        playerInventory.addMoney(100);

        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("potion", -1);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 1, playerInventory);

        assertEquals(ShopService.TransactionResult.INSUFFICIENT_MONEY, result);
        assertEquals(100, playerInventory.getMoney());
        assertFalse(playerInventory.hasItem("potion"));
    }

    @Test
    @DisplayName("buy inventory full — no state change")
    void buyInventoryFull() {
        // Fill the MEDICINE pocket to capacity
        for (int i = 0; i < Inventory.POCKET_CAPACITY; i++) {
            // Add items with unique IDs to fill slots
            String fakeId = "filler_" + i;
            testRegistry.addItem(ItemDefinition.builder(fakeId, "Filler " + i, ItemCategory.MEDICINE)
                    .price(10).sellPrice(5).build());
            playerInventory.addItem(fakeId, 1);
        }

        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("super_potion", -1);
        int moneyBefore = playerInventory.getMoney();

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 1, playerInventory);

        assertEquals(ShopService.TransactionResult.INVENTORY_FULL, result);
        assertEquals(moneyBefore, playerInventory.getMoney());
        assertFalse(playerInventory.hasItem("super_potion"));

        // Cleanup test items
        for (int i = 0; i < Inventory.POCKET_CAPACITY; i++) {
            testRegistry.removeItem("filler_" + i);
        }
    }

    @Test
    @DisplayName("buy unlimited stock — stock remains -1 after purchase")
    void buyUnlimitedStock() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("potion", -1);

        ShopService.buy("test_shop", entry, 5, playerInventory);

        assertEquals(-1, ShopService.getEffectiveStock("test_shop", entry));
    }

    @Test
    @DisplayName("buy limited stock — stock decremented by quantity")
    void buyLimitedStock() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("repel", 10);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 3, playerInventory);

        assertEquals(ShopService.TransactionResult.SUCCESS, result);
        assertEquals(7, ShopService.getEffectiveStock("test_shop", entry));
    }

    @Test
    @DisplayName("buy out of stock — rejected")
    void buyOutOfStock() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("repel", 0);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 1, playerInventory);

        assertEquals(ShopService.TransactionResult.OUT_OF_STOCK, result);
    }

    @Test
    @DisplayName("buy clamps to available stock when requesting more than available")
    void buyClampsToAvailableStock() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("repel", 3);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 10, playerInventory);

        assertEquals(ShopService.TransactionResult.SUCCESS, result);
        assertEquals(3, playerInventory.getItemCount("repel")); // only 3 available
        assertEquals(0, ShopService.getEffectiveStock("test_shop", entry));
    }

    @Test
    @DisplayName("buy unknown item — returns ITEM_NOT_FOUND")
    void buyUnknownItem() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("nonexistent", -1);

        ShopService.TransactionResult result = ShopService.buy("test_shop", entry, 1, playerInventory);

        assertEquals(ShopService.TransactionResult.ITEM_NOT_FOUND, result);
    }

    // ========================================================================
    // SELL FLOW TESTS
    // ========================================================================

    @Test
    @DisplayName("sell success — item removed, money added")
    void sellSuccess() {
        playerInventory.addItem("potion", 5);
        int moneyBefore = playerInventory.getMoney();

        ShopService.TransactionResult result = ShopService.sell("potion", 3, playerInventory);

        assertEquals(ShopService.TransactionResult.SUCCESS, result);
        assertEquals(2, playerInventory.getItemCount("potion"));
        assertEquals(moneyBefore + 100 * 3, playerInventory.getMoney());
    }

    @Test
    @DisplayName("sell KEY_ITEM — rejected, no state change")
    void sellKeyItem() {
        playerInventory.addItem("bicycle", 1);
        int moneyBefore = playerInventory.getMoney();

        ShopService.TransactionResult result = ShopService.sell("bicycle", 1, playerInventory);

        assertEquals(ShopService.TransactionResult.CANNOT_SELL, result);
        assertTrue(playerInventory.hasItem("bicycle"));
        assertEquals(moneyBefore, playerInventory.getMoney());
    }

    @Test
    @DisplayName("sell item with sellPrice 0 — rejected, no state change")
    void sellZeroPrice() {
        playerInventory.addItem("rare_candy", 3);
        int moneyBefore = playerInventory.getMoney();

        ShopService.TransactionResult result = ShopService.sell("rare_candy", 1, playerInventory);

        assertEquals(ShopService.TransactionResult.CANNOT_SELL, result);
        assertEquals(3, playerInventory.getItemCount("rare_candy"));
        assertEquals(moneyBefore, playerInventory.getMoney());
    }

    @Test
    @DisplayName("sell more than owned — rejected, no state change")
    void sellMoreThanOwned() {
        playerInventory.addItem("potion", 2);
        int moneyBefore = playerInventory.getMoney();

        ShopService.TransactionResult result = ShopService.sell("potion", 5, playerInventory);

        assertEquals(ShopService.TransactionResult.INSUFFICIENT_ITEMS, result);
        assertEquals(2, playerInventory.getItemCount("potion"));
        assertEquals(moneyBefore, playerInventory.getMoney());
    }

    @Test
    @DisplayName("sell unknown item — returns ITEM_NOT_FOUND")
    void sellUnknownItem() {
        ShopService.TransactionResult result = ShopService.sell("nonexistent", 1, playerInventory);

        assertEquals(ShopService.TransactionResult.ITEM_NOT_FOUND, result);
    }

    // ========================================================================
    // STOCK PERSISTENCE TESTS
    // ========================================================================

    @Test
    @DisplayName("limited stock persisted via SaveManager global state")
    void stockPersisted() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("repel", 10);

        ShopService.buy("persist_shop", entry, 4, playerInventory);

        // Verify stock is persisted
        assertEquals(6, ShopService.getEffectiveStock("persist_shop", entry));
    }

    @Test
    @DisplayName("getEffectiveStock returns default for unlimited")
    void effectiveStockUnlimited() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("potion", -1);
        assertEquals(-1, ShopService.getEffectiveStock("any_shop", entry));
    }

    @Test
    @DisplayName("getEffectiveStock returns original stock when no persisted state")
    void effectiveStockDefault() {
        ShopInventory.ShopEntry entry = new ShopInventory.ShopEntry("repel", 15);
        assertEquals(15, ShopService.getEffectiveStock("fresh_shop", entry));
    }

    // ========================================================================
    // Test infrastructure
    // ========================================================================

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

    private static class StubAssetContext implements com.pocket.rpg.resources.AssetContext {
        private final ItemRegistry registry;

        StubAssetContext(ItemRegistry registry) {
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
