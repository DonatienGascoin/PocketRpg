package com.pocket.rpg.items;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collection of {@link ItemStack}s organized by {@link ItemCategory} pockets.
 *
 * <p>Supports per-pocket capacity limits, per-item stack limits, registered quick-use items,
 * and sorting. Serialization to/from save data preserves pocket assignments.
 */
public class Inventory {
    public static final int POCKET_CAPACITY = 50;

    private final Map<ItemCategory, List<ItemStack>> pockets = new EnumMap<>(ItemCategory.class);
    private final List<String> registeredItems = new ArrayList<>();

    public Inventory() {
        for (ItemCategory cat : ItemCategory.values()) {
            pockets.put(cat, new ArrayList<>());
        }
    }

    /**
     * Add items to the inventory. Looks up the item definition to determine category and stack limit.
     *
     * @return the quantity actually added (0 if the item is unknown, pocket is full, or quantity is invalid)
     */
    public int addItem(String itemId, int quantity, ItemRegistry registry) {
        if (quantity <= 0) return 0;
        ItemDefinition def = registry.get(itemId);
        if (def == null) return 0;

        List<ItemStack> pocket = pockets.get(def.getCategory());
        ItemStack existing = findStack(pocket, itemId);

        if (existing != null) {
            int space = def.getStackLimit() - existing.getQuantity();
            if (space <= 0) return 0;
            int toAdd = Math.min(quantity, space);
            existing.add(toAdd);
            return toAdd;
        }

        if (pocket.size() >= POCKET_CAPACITY) return 0;

        int toAdd = Math.min(quantity, def.getStackLimit());
        pocket.add(new ItemStack(itemId, toAdd));
        return toAdd;
    }

    /**
     * Remove items from the inventory.
     *
     * @return true if the full quantity was removed, false if not enough
     */
    public boolean removeItem(String itemId, int quantity) {
        if (quantity <= 0) return false;
        for (List<ItemStack> pocket : pockets.values()) {
            ItemStack stack = findStack(pocket, itemId);
            if (stack != null) {
                if (!stack.remove(quantity)) return false;
                if (stack.isEmpty()) {
                    pocket.remove(stack);
                }
                return true;
            }
        }
        return false;
    }

    public boolean hasItem(String itemId) {
        return getCount(itemId) > 0;
    }

    public boolean hasItem(String itemId, int minQuantity) {
        return getCount(itemId) >= minQuantity;
    }

    public int getCount(String itemId) {
        for (List<ItemStack> pocket : pockets.values()) {
            ItemStack stack = findStack(pocket, itemId);
            if (stack != null) return stack.getQuantity();
        }
        return 0;
    }

    public List<ItemStack> getPocket(ItemCategory category) {
        return Collections.unmodifiableList(pockets.get(category));
    }

    public List<ItemStack> getAllItems() {
        return pockets.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toUnmodifiableList());
    }

    public boolean isFull(ItemCategory category) {
        return pockets.get(category).size() >= POCKET_CAPACITY;
    }

    // --- Registered items ---

    public void registerItem(String itemId) {
        if (!registeredItems.contains(itemId)) {
            registeredItems.add(itemId);
        }
    }

    public void unregisterItem(String itemId) {
        registeredItems.remove(itemId);
    }

    public List<String> getRegisteredItems() {
        return Collections.unmodifiableList(registeredItems);
    }

    // --- Sorting ---

    public void sortPocket(ItemCategory category, SortMode mode, ItemRegistry registry) {
        List<ItemStack> pocket = pockets.get(category);
        switch (mode) {
            case BY_NAME -> pocket.sort((a, b) -> {
                String nameA = getDisplayName(a.getItemId(), registry);
                String nameB = getDisplayName(b.getItemId(), registry);
                return nameA.compareTo(nameB);
            });
            case BY_ID -> pocket.sort(Comparator.comparing(ItemStack::getItemId));
            case BY_CATEGORY -> pocket.sort((a, b) -> {
                ItemCategory catA = getCategoryOf(a.getItemId(), registry);
                ItemCategory catB = getCategoryOf(b.getItemId(), registry);
                int cmp = catA.compareTo(catB);
                if (cmp != 0) return cmp;
                return a.getItemId().compareTo(b.getItemId());
            });
        }
    }

    // --- Serialization ---

    public InventoryData toSaveData() {
        InventoryData data = new InventoryData();
        data.pockets = new HashMap<>();
        for (Map.Entry<ItemCategory, List<ItemStack>> entry : pockets.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                data.pockets.put(entry.getKey().name(),
                        entry.getValue().stream()
                                .map(s -> new InventoryData.StackEntry(s.getItemId(), s.getQuantity()))
                                .collect(Collectors.toList()));
            }
        }
        data.registeredItems = new ArrayList<>(registeredItems);
        return data;
    }

    public static Inventory fromSaveData(InventoryData data) {
        Inventory inv = new Inventory();
        if (data == null) return inv;

        if (data.pockets != null) {
            for (Map.Entry<String, List<InventoryData.StackEntry>> entry : data.pockets.entrySet()) {
                try {
                    ItemCategory cat = ItemCategory.valueOf(entry.getKey());
                    List<ItemStack> pocket = inv.pockets.get(cat);
                    for (InventoryData.StackEntry stackEntry : entry.getValue()) {
                        if (pocket.size() >= POCKET_CAPACITY) break;
                        if (stackEntry != null && stackEntry.itemId != null && stackEntry.quantity > 0) {
                            pocket.add(new ItemStack(stackEntry.itemId, stackEntry.quantity));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip unknown categories from old save data
                }
            }
        }

        if (data.registeredItems != null) {
            inv.registeredItems.addAll(data.registeredItems);
        }

        return inv;
    }

    // --- Private helpers ---

    private static ItemStack findStack(List<ItemStack> pocket, String itemId) {
        for (ItemStack stack : pocket) {
            if (stack.getItemId().equals(itemId)) return stack;
        }
        return null;
    }

    private static String getDisplayName(String itemId, ItemRegistry registry) {
        ItemDefinition def = registry.get(itemId);
        return def != null ? def.getName() : itemId;
    }

    private static ItemCategory getCategoryOf(String itemId, ItemRegistry registry) {
        ItemDefinition def = registry.get(itemId);
        return def != null ? def.getCategory() : ItemCategory.MEDICINE;
    }
}
