package com.pocket.rpg.shop;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines what a shop sells. Loaded from JSON via {@link ShopRegistry}.
 */
@Getter
@Setter
public class ShopInventory {

    private String shopId;
    private String shopName;
    private List<ShopEntry> items = new ArrayList<>();

    public ShopInventory() {}

    public ShopInventory(String shopId, String shopName, List<ShopEntry> items) {
        this.shopId = shopId;
        this.shopName = shopName;
        this.items = new ArrayList<>(items);
    }

    public List<ShopEntry> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addEntry(ShopEntry entry) {
        items.add(entry);
    }

    public void removeEntry(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    /**
     * A single item listing in a shop.
     */
    @Getter
    @Setter
    public static class ShopEntry {
        /** References ItemRegistry (e.g., "potion", "pokeball"). */
        private String itemId;
        /** -1 = unlimited, >0 = limited stock. */
        private int stock = -1;

        public ShopEntry() {}

        public ShopEntry(String itemId, int stock) {
            this.itemId = itemId;
            this.stock = stock;
        }

        public boolean isUnlimitedStock() {
            return stock == -1;
        }
    }
}
