# Phase 4: Inventory System

## Overview

A basic inventory system for storing key items. This is a minimal implementation focused on supporting the Interactable system (doors, chests, locks).

**Scope**:
- Key items (single quantity items like keys, quest items)
- Stackable items (consumables, collectibles)
- Basic add/remove/check operations

**Out of Scope** (future work):
- Equipment slots
- Item categories/sorting
- UI inventory display
- Item database/definitions
- Crafting

---

## ItemStack

```java
package com.pocket.rpg.components.inventory;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a stack of items in inventory.
 *
 * For key items, quantity is typically 1.
 * For stackable items, quantity can be any positive number.
 */
public class ItemStack {

    /**
     * Unique item identifier.
     * Examples: "dungeon_key", "potion_health", "coin"
     */
    @Getter @Setter
    private String itemId;

    /**
     * Number of items in this stack.
     */
    @Getter @Setter
    private int quantity;

    /**
     * Maximum stack size (0 = unlimited).
     */
    @Getter @Setter
    private int maxStack = 0;

    public ItemStack() {
        this("", 1);
    }

    public ItemStack(String itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    /**
     * Adds to stack, respecting max stack size.
     *
     * @param amount Amount to add
     * @return Amount that couldn't be added (overflow)
     */
    public int add(int amount) {
        if (maxStack <= 0) {
            quantity += amount;
            return 0;
        }

        int canAdd = maxStack - quantity;
        int toAdd = Math.min(amount, canAdd);
        quantity += toAdd;
        return amount - toAdd;
    }

    /**
     * Removes from stack.
     *
     * @param amount Amount to remove
     * @return Amount actually removed
     */
    public int remove(int amount) {
        int toRemove = Math.min(amount, quantity);
        quantity -= toRemove;
        return toRemove;
    }

    /**
     * Whether this stack is empty.
     */
    public boolean isEmpty() {
        return quantity <= 0;
    }

    /**
     * Creates a copy of this stack.
     */
    public ItemStack copy() {
        ItemStack copy = new ItemStack(itemId, quantity);
        copy.maxStack = maxStack;
        return copy;
    }
}
```

---

## Inventory Component

```java
package com.pocket.rpg.components.inventory;

import com.pocket.rpg.components.Component;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Component for storing items.
 *
 * Attach to player or any entity that can carry items.
 *
 * Features:
 * - Add/remove items by ID
 * - Check if item exists
 * - Get item count
 * - Iterate all items
 */
public class Inventory extends Component {

    /**
     * Maximum number of unique item types (0 = unlimited).
     */
    @Getter
    private int maxSlots = 0;

    /**
     * Item storage: itemId -> ItemStack
     */
    private final Map<String, ItemStack> items = new HashMap<>();

    // ========================================================================
    // BASIC OPERATIONS
    // ========================================================================

    /**
     * Adds item(s) to inventory.
     *
     * @param itemId   Item identifier
     * @param quantity Amount to add
     * @return true if all items were added, false if inventory full
     */
    public boolean addItem(String itemId, int quantity) {
        if (itemId == null || itemId.isBlank() || quantity <= 0) {
            return false;
        }

        ItemStack existing = items.get(itemId);

        if (existing != null) {
            // Add to existing stack
            int overflow = existing.add(quantity);
            return overflow == 0;
        } else {
            // Create new stack
            if (maxSlots > 0 && items.size() >= maxSlots) {
                return false; // Inventory full
            }

            items.put(itemId, new ItemStack(itemId, quantity));
            return true;
        }
    }

    /**
     * Adds a single item.
     */
    public boolean addItem(String itemId) {
        return addItem(itemId, 1);
    }

    /**
     * Removes item(s) from inventory.
     *
     * @param itemId   Item identifier
     * @param quantity Amount to remove
     * @return true if removed successfully, false if not enough
     */
    public boolean removeItem(String itemId, int quantity) {
        if (itemId == null || itemId.isBlank() || quantity <= 0) {
            return false;
        }

        ItemStack stack = items.get(itemId);
        if (stack == null || stack.getQuantity() < quantity) {
            return false;
        }

        stack.remove(quantity);

        // Remove empty stacks
        if (stack.isEmpty()) {
            items.remove(itemId);
        }

        return true;
    }

    /**
     * Removes a single item.
     */
    public boolean removeItem(String itemId) {
        return removeItem(itemId, 1);
    }

    /**
     * Checks if inventory contains at least one of an item.
     */
    public boolean hasItem(String itemId) {
        return getItemCount(itemId) > 0;
    }

    /**
     * Checks if inventory contains at least the specified quantity.
     */
    public boolean hasItem(String itemId, int quantity) {
        return getItemCount(itemId) >= quantity;
    }

    /**
     * Gets the quantity of an item.
     */
    public int getItemCount(String itemId) {
        ItemStack stack = items.get(itemId);
        return stack != null ? stack.getQuantity() : 0;
    }

    /**
     * Gets an item stack (read-only).
     */
    public ItemStack getItem(String itemId) {
        return items.get(itemId);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Gets all item stacks.
     */
    public List<ItemStack> getAllItems() {
        return new ArrayList<>(items.values());
    }

    /**
     * Gets all item IDs.
     */
    public List<String> getItemIds() {
        return new ArrayList<>(items.keySet());
    }

    /**
     * Gets number of unique item types.
     */
    public int getSlotCount() {
        return items.size();
    }

    /**
     * Whether inventory is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Whether inventory has room for more item types.
     */
    public boolean hasSpace() {
        return maxSlots <= 0 || items.size() < maxSlots;
    }

    /**
     * Clears all items.
     */
    public void clear() {
        items.clear();
    }

    // ========================================================================
    // SERIALIZATION SUPPORT
    // ========================================================================

    /**
     * Gets items as serializable list.
     */
    public List<ItemStack> getItemsForSerialization() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : items.values()) {
            list.add(stack.copy());
        }
        return list;
    }

    /**
     * Sets items from deserialized list.
     */
    public void setItemsFromSerialization(List<ItemStack> list) {
        items.clear();
        if (list != null) {
            for (ItemStack stack : list) {
                items.put(stack.getItemId(), stack.copy());
            }
        }
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }
}
```

---

## Usage Examples

### Player Setup

```java
// Player has inventory
GameObject player = new GameObject("Player");
player.addComponent(new Inventory());
player.addComponent(new InteractionController());
player.addComponent(new GridMovement());
```

### Giving Key on Game Start

```java
// Start of dungeon
Inventory playerInv = player.getComponent(Inventory.class);
playerInv.addItem("dungeon_key");
```

### Door Checking Key

```java
// In Door.tryUnlock()
Inventory inv = actor.getComponent(Inventory.class);
if (inv != null && inv.hasItem("dungeon_key")) {
    if (consumeKey) {
        inv.removeItem("dungeon_key");
    }
    return true; // Unlocked!
}
return false;
```

### Chest Giving Items

```java
// Chest contents configuration
chest.setContents(List.of(
    new ItemStack("gold_coin", 50),
    new ItemStack("potion_health", 3),
    new ItemStack("secret_note", 1)
));

// On open
for (ItemStack item : contents) {
    playerInv.addItem(item.getItemId(), item.getQuantity());
}
```

---

## Key Items vs Stackables

### Key Items (Quantity = 1)

Used for locks, quest progress:
- `dungeon_key` - Opens dungeon door
- `boss_key` - Opens boss room
- `quest_item_letter` - Deliver to NPC
- `ability_double_jump` - Permanent ability

```java
// Check for key item
if (inventory.hasItem("dungeon_key")) {
    // Can open dungeon door
}
```

### Stackable Items

Used for consumables, currency:
- `gold_coin` - Currency (quantity: any)
- `potion_health` - Healing (quantity: limited stack)
- `arrow` - Ammunition (quantity: any)

```java
// Check for consumable
if (inventory.hasItem("potion_health")) {
    inventory.removeItem("potion_health", 1);
    player.heal(50);
}
```

---

## Serialization

The Inventory component serializes its items list:

```java
// In component serialization
public class InventoryData {
    private int maxSlots;
    private List<ItemStackData> items;
}

public class ItemStackData {
    private String itemId;
    private int quantity;
    private int maxStack;
}
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `components/inventory/ItemStack.java` | Item stack class |
| `components/inventory/Inventory.java` | Inventory component |

---

## Testing Checklist

- [ ] addItem adds new item
- [ ] addItem increases existing stack
- [ ] removeItem decreases stack
- [ ] removeItem removes empty stack
- [ ] hasItem returns true when present
- [ ] hasItem returns false when absent
- [ ] getItemCount returns correct count
- [ ] maxSlots prevents adding new types
- [ ] clear removes all items
- [ ] Serialization preserves items

---

## Future Enhancements

### Item Definitions

```java
// ItemRegistry with item properties
ItemDefinition potion = ItemRegistry.get("potion_health");
potion.getName();        // "Health Potion"
potion.getDescription(); // "Restores 50 HP"
potion.getMaxStack();    // 10
potion.getIcon();        // Sprite
```

### Equipment Slots

```java
public class Equipment extends Component {
    private ItemStack weapon;
    private ItemStack armor;
    private ItemStack accessory;

    public void equip(ItemStack item, EquipSlot slot);
    public void unequip(EquipSlot slot);
}
```

### Inventory UI

```java
public class InventoryUI extends UIComponent {
    // Grid display of items
    // Drag and drop
    // Item tooltips
}
```

---

## Next Phase

Phase 5: Collision Map Runtime Modification
