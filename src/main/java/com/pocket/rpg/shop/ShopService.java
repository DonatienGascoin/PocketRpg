package com.pocket.rpg.shop;

import com.pocket.rpg.components.pokemon.PlayerInventoryComponent;
import com.pocket.rpg.items.ItemCategory;
import com.pocket.rpg.items.ItemDefinition;
import com.pocket.rpg.items.ItemRegistry;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.SaveManager;

/**
 * Buy/sell transaction logic for the shop system.
 * <p>
 * All methods are static utility methods. Buy flow validates money and inventory
 * space before any state changes. Sell flow rejects KEY_ITEMs and items with
 * sellPrice = 0. Limited stock is persisted via {@link SaveManager} global state.
 */
public final class ShopService {

    private static final Logger LOG = Log.getLogger(ShopService.class);
    private static final String ITEM_REGISTRY_PATH = "data/items/items.items.json";
    private static final String STOCK_NAMESPACE = "shop_stock";

    private ShopService() {}

    // ========================================================================
    // BUY
    // ========================================================================

    /**
     * Result of a buy or sell transaction.
     */
    public enum TransactionResult {
        SUCCESS,
        ITEM_NOT_FOUND,
        INSUFFICIENT_MONEY,
        INVENTORY_FULL,
        OUT_OF_STOCK,
        CANNOT_SELL,
        INSUFFICIENT_ITEMS
    }

    /**
     * Attempts to buy items from a shop.
     * <p>
     * Validates money and inventory space <em>before</em> any state changes.
     * If the shop entry has limited stock, the remaining stock is decremented
     * and persisted to {@link SaveManager} global state.
     *
     * @param shopId    the shop identifier (for stock persistence key)
     * @param entry     the shop entry being purchased
     * @param quantity  how many to buy
     * @param inventory the player's inventory component
     * @return the transaction result
     */
    public static TransactionResult buy(String shopId, ShopInventory.ShopEntry entry,
                                        int quantity, PlayerInventoryComponent inventory) {
        if (quantity <= 0) return TransactionResult.INSUFFICIENT_MONEY;

        ItemRegistry itemRegistry = Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);
        if (itemRegistry == null) return TransactionResult.ITEM_NOT_FOUND;

        ItemDefinition def = itemRegistry.get(entry.getItemId());
        if (def == null) return TransactionResult.ITEM_NOT_FOUND;

        // Check limited stock
        int availableStock = getEffectiveStock(shopId, entry);
        if (!entry.isUnlimitedStock() && availableStock <= 0) {
            return TransactionResult.OUT_OF_STOCK;
        }

        // Clamp quantity to available stock for limited items
        int buyQuantity = quantity;
        if (!entry.isUnlimitedStock()) {
            buyQuantity = Math.min(buyQuantity, availableStock);
        }

        // --- VALIDATION (no state changes) ---
        int totalCost = def.getPrice() * buyQuantity;
        if (inventory.getMoney() < totalCost) {
            return TransactionResult.INSUFFICIENT_MONEY;
        }

        // Dry-run: check if inventory can accept the items
        if (!canAddToInventory(def, buyQuantity, inventory)) {
            return TransactionResult.INVENTORY_FULL;
        }

        // --- EXECUTION (both checks passed) ---
        inventory.spendMoney(totalCost);
        int added = inventory.addItem(entry.getItemId(), buyQuantity);

        // Decrement limited stock
        if (!entry.isUnlimitedStock()) {
            int newStock = availableStock - added;
            setPersistedStock(shopId, entry.getItemId(), newStock);
        }

        LOG.info("Bought {} x{} for {} money", def.getName(), added, totalCost);
        return TransactionResult.SUCCESS;
    }

    // ========================================================================
    // SELL
    // ========================================================================

    /**
     * Attempts to sell items from the player's inventory.
     * <p>
     * Rejects KEY_ITEMs and items with sellPrice = 0.
     *
     * @param itemId    the item to sell
     * @param quantity  how many to sell
     * @param inventory the player's inventory component
     * @return the transaction result
     */
    public static TransactionResult sell(String itemId, int quantity,
                                         PlayerInventoryComponent inventory) {
        if (quantity <= 0) return TransactionResult.INSUFFICIENT_ITEMS;

        ItemRegistry itemRegistry = Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);
        if (itemRegistry == null) return TransactionResult.ITEM_NOT_FOUND;

        ItemDefinition def = itemRegistry.get(itemId);
        if (def == null) return TransactionResult.ITEM_NOT_FOUND;

        // KEY_ITEMs cannot be sold
        if (def.getCategory() == ItemCategory.KEY_ITEM) {
            return TransactionResult.CANNOT_SELL;
        }

        // Items with sellPrice = 0 cannot be sold
        if (def.getSellPrice() <= 0) {
            return TransactionResult.CANNOT_SELL;
        }

        // Check the player has enough
        if (inventory.getItemCount(itemId) < quantity) {
            return TransactionResult.INSUFFICIENT_ITEMS;
        }

        // Execute
        int totalValue = def.getSellPrice() * quantity;
        boolean removed = inventory.removeItem(itemId, quantity);
        if (!removed) {
            return TransactionResult.INSUFFICIENT_ITEMS;
        }

        inventory.addMoney(totalValue);
        LOG.info("Sold {} x{} for {} money", def.getName(), quantity, totalValue);
        return TransactionResult.SUCCESS;
    }

    // ========================================================================
    // STOCK PERSISTENCE
    // ========================================================================

    /**
     * Gets the effective stock for a shop entry, checking persisted state first.
     *
     * @param shopId the shop identifier
     * @param entry  the shop entry
     * @return -1 for unlimited, or the remaining stock count
     */
    public static int getEffectiveStock(String shopId, ShopInventory.ShopEntry entry) {
        if (entry.isUnlimitedStock()) return -1;

        String key = stockKey(shopId, entry.getItemId());
        return SaveManager.getGlobal(STOCK_NAMESPACE, key, entry.getStock());
    }

    /**
     * Persists the remaining stock for a limited-stock shop entry.
     */
    private static void setPersistedStock(String shopId, String itemId, int remaining) {
        String key = stockKey(shopId, itemId);
        SaveManager.setGlobal(STOCK_NAMESPACE, key, remaining);
    }

    private static String stockKey(String shopId, String itemId) {
        return shopId + "." + itemId;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Checks if the inventory can accept the given quantity of an item
     * without actually modifying state.
     */
    private static boolean canAddToInventory(ItemDefinition def, int quantity,
                                             PlayerInventoryComponent inventory) {
        int currentCount = inventory.getItemCount(def.getItemId());
        if (currentCount > 0) {
            // Already have this item — check stack limit
            return currentCount + quantity <= def.getStackLimit();
        }
        // New item — check pocket capacity
        return !inventory.getInventory().isFull(def.getCategory());
    }

    /**
     * Logs the shop contents to the console (for debug/testing before UI exists).
     */
    public static void logShopContents(ShopInventory shop, String shopId) {
        ItemRegistry itemRegistry = Assets.load(ITEM_REGISTRY_PATH, ItemRegistry.class);

        for (ShopInventory.ShopEntry entry : shop.getItems()) {
            int stock = getEffectiveStock(shopId, entry);
            String stockStr = stock == -1 ? "unlimited" : String.valueOf(stock);

            String name = entry.getItemId();
            int price = 0;
            if (itemRegistry != null) {
                ItemDefinition def = itemRegistry.get(entry.getItemId());
                if (def != null) {
                    name = def.getName();
                    price = def.getPrice();
                }
            }

            LOG.info("  {} - {} money (stock: {})", name, price, stockStr);
        }
    }
}
