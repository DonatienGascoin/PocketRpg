# Shop System — Plan

## Overview

Pokemarts / shops let the player buy and sell items from NPC shopkeepers. The shop system is data-driven — shop inventories are defined in JSON and loaded via the asset pipeline. A `ShopComponent` on NPC GameObjects triggers the shop UI when interacted with.

## Dependencies

- **item-inventory** — `ItemDefinition`, `ItemRegistry`, `Inventory`, `PlayerInventoryComponent` (money + item management)

## Package Layout

```
com.pocket.rpg/
├── shop/
│   ├── ShopInventory.java          # Shop definition (items + stock)
│   └── ShopRegistry.java           # Registry: shopId → ShopInventory
│
├── components/pokemon/
│   └── ShopComponent.java          # NPC shopkeeper (Interactable)
│
├── resources/loaders/
│   └── ShopRegistryLoader.java     # AssetLoader<ShopRegistry> (hot-reload)
```

---

## Detailed Class Designs

### `ShopInventory` (data class)

Defines what a shop sells.

```java
- shopId: String                    // "viridian_pokemart"
- shopName: String                  // "Viridian City Pokemart"
- items: List<ShopEntry>            // Available items for purchase

public static class ShopEntry {
    - itemId: String                // References ItemRegistry (e.g., "potion", "pokeball")
    - stock: int                    // -1 = unlimited, >0 = limited stock
}
```

### `ShopRegistry` (registry)

```java
- shops: Map<String, ShopInventory>

+getShop(shopId): ShopInventory
+getAll(): Collection<ShopInventory>

// For hot-reload support
+copyFrom(other): void
```

### `ShopComponent` (ECS bridge)

Attached to NPC shopkeeper GameObjects. Opens the shop UI when interacted with. Extends `InteractableComponent` (the standard base class — provides TriggerZone registration, directional interaction, gizmo drawing).

```java
@ComponentMeta(category = "Interaction")
public class ShopComponent extends InteractableComponent {
    private String shopId;           // References ShopRegistry (e.g., "viridian_pokemart")

    public ShopComponent() {
        gizmoShape = GizmoShape.SQUARE;
        gizmoColor = GizmoColors.fromRGBA(0.2f, 0.6f, 1.0f, 0.9f);  // Blue
    }

    @Override
    public void interact(GameObject player) {
        // 1. Load ShopRegistry from Assets
        // 2. Look up ShopInventory by shopId
        // 3. Open shop UI (buy/sell menu) — UI system is out of scope for this plan
    }

    @Override
    public String getInteractionPrompt() {
        return "Shop";
    }
}
```

**NPC Shopkeeper prefab:**

```
GameObject "Shopkeeper"
├── Transform
├── SpriteRenderer
├── TriggerZone                ◄── auto-added via @RequiredComponent on InteractableComponent
└── ShopComponent              ◄── NEW
    └── shopId: "viridian_pokemart"
```

---

## Buy / Sell Logic

### Buy Flow

**Validate first, then execute** — both checks pass before any state changes:

```
1. Player selects item + quantity from shop UI
2. Look up ItemDefinition from ItemRegistry
3. Calculate total cost: ItemDefinition.price * quantity
--- VALIDATION (no state changes) ---
4. Check: PlayerInventoryComponent.getMoney() >= totalCost → fail "Not enough money!"
5. Check: Inventory can accept the item (not full, within stack limit) → fail "Bag is full!"
--- EXECUTION (only if both checks pass) ---
6. PlayerInventoryComponent.spendMoney(totalCost)
7. PlayerInventoryComponent.addItem(itemId, quantity)
8. If stock is limited (stock > 0): decrement ShopEntry.stock
```

### Sell Flow

```
1. Player selects item from inventory + quantity
2. Look up ItemDefinition from ItemRegistry
3. Guard: KEY_ITEM category cannot be sold
4. Calculate total value: ItemDefinition.sellPrice * quantity
5. PlayerInventoryComponent.removeItem(itemId, quantity)
6. PlayerInventoryComponent.addMoney(totalValue)
```

### Edge Cases

- **Insufficient money**: Buy fails, UI shows message
- **Inventory full**: Buy fails if pocket is at POCKET_CAPACITY and item is new
- **Stack limit**: Buy reduces quantity to fit within stackLimit
- **Out of stock**: Limited stock items removed from display when stock reaches 0
- **Sell price 0**: Item cannot be sold (same treatment as KEY_ITEM)

---

## AssetLoader Integration

### `ShopRegistryLoader` implements `AssetLoader<ShopRegistry>`

```java
- getSupportedExtensions(): [".shops.json"]
- supportsHotReload(): true
- load(path): Parses shops from JSON → ShopRegistry
- reload(existing, path): Mutates existing ShopRegistry in place (MUST return same reference)
- save(registry, path): Serializes back to JSON
- getPlaceholder(): Empty ShopRegistry
- getIconCodepoint(): MaterialIcons.Store (or similar)
```

### Data File Location

```
gameData/data/shops/shops.shops.json
```

### Usage

```java
ShopRegistry shops = Assets.load("data/shops/shops.shops.json", ShopRegistry.class);
ShopInventory viridian = shops.getShop("viridian_pokemart");
```

---

## JSON Data Format

### shops.shops.json

```json
[
  {
    "shopId": "viridian_pokemart",
    "shopName": "Viridian City Pokemart",
    "items": [
      { "itemId": "potion", "stock": -1 },
      { "itemId": "pokeball", "stock": -1 },
      { "itemId": "antidote", "stock": -1 },
      { "itemId": "repel", "stock": 10 }
    ]
  },
  {
    "shopId": "pewter_pokemart",
    "shopName": "Pewter City Pokemart",
    "items": [
      { "itemId": "potion", "stock": -1 },
      { "itemId": "super_potion", "stock": -1 },
      { "itemId": "pokeball", "stock": -1 },
      { "itemId": "great_ball", "stock": -1 },
      { "itemId": "escape_rope", "stock": -1 }
    ]
  }
]
```

---

## Limited Stock Persistence

If a shop has limited stock items (e.g., `"stock": 10` for repel), the remaining stock needs to persist across save/load. Two approaches:

**Option A — Global State (simpler):**
Store remaining stock in `SaveManager.setGlobal("shops", "viridian_pokemart.repel.stock", 7)`.

**Option B — ShopComponent ISaveable:**
Make `ShopComponent` implement `ISaveable` and persist modified stock counts.

Recommend **Option A** since shop stock is global state (not per-entity), and the save system's global state API is designed for this.

---

## Implementation Phases

| Phase | Scope |
|-------|-------|
| **1** | Data classes: `ShopInventory`, `ShopEntry` |
| **2** | `ShopRegistry` registry class |
| **3** | `ShopRegistryLoader` (AssetLoader) + sample `shops.shops.json` |
| **4** | `ShopComponent` (extends InteractableComponent) |
| **5** | Buy/sell logic (can be a utility class or methods on ShopComponent) |
| **6** | Limited stock persistence (global state approach) |
| **7** | Unit tests for buy/sell edge cases, stock management |

## Acceptance Criteria

- [ ] `ShopRegistry` loads shop definitions from JSON and provides lookup by shopId
- [ ] `ShopRegistryLoader` integrates with asset pipeline (load, hot-reload, save)
- [ ] `ShopComponent` extends `InteractableComponent` and triggers shop interaction on interact
- [ ] Buy flow validates money + inventory space **before** any state changes
- [ ] Sell flow rejects KEY_ITEM and items with sellPrice = 0
- [ ] Limited stock decrements on buy, unavailable at 0
- [ ] Limited stock persisted via `SaveManager.globalState`
- [ ] Unlimited stock (-1) never depletes

## Testing Plan

### Unit Tests

**ShopRegistry & ShopRegistryLoader:**
- `ShopRegistry.getShop()` — returns correct `ShopInventory` for known shopId, null for unknown
- `ShopRegistry.getAll()` — returns all loaded shops
- `ShopRegistryLoader.load()` — parses sample JSON, shops accessible by ID
- `ShopRegistryLoader.reload()` — mutates existing registry in place, updated data reflected

**Buy flow:**
- Buy success — money deducted by `price × quantity`, item added to inventory with correct quantity
- Buy insufficient money — returns false, money unchanged, inventory unchanged (no partial state change)
- Buy inventory full — pocket at POCKET_CAPACITY with new item — returns false, money unchanged
- Buy partial stack limit — quantity reduced to fit within item's stackLimit
- Buy unlimited stock (-1) — stock remains -1 after purchase
- Buy limited stock — stock decremented by quantity purchased
- Buy out-of-stock (stock = 0) — rejected

**Sell flow:**
- Sell success — item removed from inventory, money increased by `sellPrice × quantity`
- Sell KEY_ITEM — rejected, no state change
- Sell item with sellPrice = 0 — rejected, no state change
- Sell more than owned — rejected, no state change

**Limited stock persistence:**
- Buy limited stock item → save → reload → remaining stock preserved
- Stock stored in `SaveManager.globalState` under correct key

### Manual Tests

- Load `shops.shops.json` in editor — verify it appears in asset browser
- Hot-reload shop data — verify changes reflected
- Place ShopComponent NPC in test scene, run game, interact — verify shop interaction triggers
- Buy items — verify money deducted and items appear in inventory (write-through)
- Attempt buy with insufficient money — verify rejection message
- Sell items — verify money added and items removed
- Buy limited stock item, save, reload — verify remaining stock persisted
- Attempt to sell a Key Item — verify rejection
