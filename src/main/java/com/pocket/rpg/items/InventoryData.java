package com.pocket.rpg.items;

import java.util.List;
import java.util.Map;

/**
 * Typed save-data class for {@link Inventory} state. Stored in {@link com.pocket.rpg.save.PlayerData}.
 *
 * <p>Gson-serialized. The field names and structure match the existing JSON format,
 * so old saves deserialize correctly without migration.
 */
public class InventoryData {

    public Map<String, List<StackEntry>> pockets;
    public List<String> registeredItems;

    public InventoryData() {}

    /** A single item stack entry within a pocket. */
    public static class StackEntry {
        public String itemId;
        public int quantity;

        public StackEntry() {}

        public StackEntry(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
