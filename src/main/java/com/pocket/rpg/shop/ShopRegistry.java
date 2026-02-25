package com.pocket.rpg.shop;

import java.util.*;

/**
 * Registry of shop definitions. Loaded via {@code AssetLoader<ShopRegistry>} pipeline.
 */
public class ShopRegistry {
    private Map<String, ShopInventory> shops = new HashMap<>();

    public ShopRegistry() {}

    public ShopInventory getShop(String shopId) {
        return shops.get(shopId);
    }

    public Collection<ShopInventory> getAll() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public void addShop(ShopInventory shop) {
        shops.put(shop.getShopId(), shop);
    }

    public void removeShop(String shopId) {
        shops.remove(shopId);
    }

    /**
     * Mutates this instance in place to match the other.
     * Required by the hot-reload contract.
     */
    public void copyFrom(ShopRegistry other) {
        this.shops.clear();
        this.shops.putAll(other.shops);
    }

    /**
     * Returns an unmodifiable view of all shops. For serialization and editor use.
     */
    public Map<String, ShopInventory> getShops() {
        return Collections.unmodifiableMap(shops);
    }
}
