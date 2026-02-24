package com.pocket.rpg.items;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all item definitions. Loaded via {@code AssetLoader<ItemRegistry>} pipeline.
 */
public class ItemRegistry {
    private Map<String, ItemDefinition> items = new HashMap<>();

    public ItemRegistry() {}

    public ItemDefinition get(String itemId) {
        return items.get(itemId);
    }

    public List<ItemDefinition> getByCategory(ItemCategory category) {
        return items.values().stream()
                .filter(def -> def.getCategory() == category)
                .collect(Collectors.toUnmodifiableList());
    }

    public Collection<ItemDefinition> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public void addItem(ItemDefinition def) {
        items.put(def.getItemId(), def);
    }

    public void removeItem(String itemId) {
        items.remove(itemId);
    }

    /**
     * Mutates this instance in place to match the other.
     * Required by the hot-reload contract.
     */
    public void copyFrom(ItemRegistry other) {
        this.items.clear();
        this.items.putAll(other.items);
    }

    /**
     * Returns an unmodifiable view of all items. For serialization and editor use.
     */
    public Map<String, ItemDefinition> getItems() {
        return Collections.unmodifiableMap(items);
    }
}
