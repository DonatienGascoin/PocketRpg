# Item & Inventory System — Plan

## Overview

Item definitions, inventory management, and the player inventory ECS component. This plan covers everything from item data models (Potion, Pokeball, TM, Key Item) through the bag system with categorized pockets, sorting, and registered quick-use items. The `PlayerInventoryComponent` bridges the inventory into the ECS and handles money.

## Dependencies

- **pokemon-data** — `ItemDefinition.teachesMove` references moveIds from the Pokedex. `ItemEffect.TEACH_MOVE` and `EVOLUTION_ITEM` interact with Pokemon data.
- **scene-data-persistence** — `PlayerData` (for `PlayerInventoryComponent` write-through persistence)

## Package Layout

```
com.pocket.rpg/
├── items/
│   ├── ItemCategory.java       # Category enum (MEDICINE, POKEBALL, BATTLE, TM_HM, BERRY, KEY_ITEM, HELD_ITEM)
│   ├── ItemEffect.java         # Effect enum (HEAL_HP, CAPTURE, TEACH_MOVE, EVOLUTION_ITEM...)
│   ├── ItemDefinition.java     # Item template (Potion, Pokeball, TM24...)
│   ├── ItemStack.java          # Item + quantity pair
│   ├── SortMode.java           # BY_NAME, BY_ID, BY_CATEGORY
│   ├── Inventory.java          # Collection of ItemStacks organized by category pockets
│   ├── ItemRegistry.java       # Registry: itemId → ItemDefinition
│   └── ItemUseService.java     # Static utility: execute item effects on a Pokemon
│
├── components/pokemon/
│   └── PlayerInventoryComponent.java  # Player's bag + money (ISaveable)
│
├── components/interaction/
│   └── ItemPickup.java                # World-placed item pickup (Interactable)
│
├── resources/loaders/
│   └── ItemRegistryLoader.java        # AssetLoader<ItemRegistry> (hot-reload, editor panel)
```

---

## Detailed Class Designs

### `ItemCategory` (enum)

```
MEDICINE      // Potions, revives, status heals
POKEBALL      // All pokeball types
BATTLE        // X Attack, Guard Spec, etc.
TM_HM         // Technical/Hidden machines
BERRY          // Held berries
KEY_ITEM       // Bicycle, Surf, story items (not consumable, qty always 1)
HELD_ITEM      // Items a Pokemon can hold (includes evolution stones — they are consumed via EVOLUTION_ITEM effect)
```

### `ItemEffect` (enum)

```
NONE                // No usable effect
HEAL_HP             // Restore HP by effectValue amount
HEAL_FULL           // Full HP restore
HEAL_STATUS         // Cure status condition (effectValue: 0=all, or StatusCondition ordinal for specific)
HEAL_FULL_RESTORE   // Full HP + cure status (Full Restore item)
REVIVE              // Revive fainted Pokemon (effectValue = HP % restored)
BOOST_ATK           // Temporary battle stat boost
BOOST_DEF
BOOST_SP_ATK
BOOST_SP_DEF
BOOST_SPD
BOOST_ACCURACY
BOOST_CRIT
CAPTURE             // Pokeball capture attempt (effectValue = catch rate modifier)
TEACH_MOVE          // TM/HM — teaches a move (see teachesMove field)
EVOLUTION_ITEM      // Triggers evolution (e.g., Thunder Stone)
TOGGLE_BICYCLE      // Key item: toggle bicycle
REPEL               // Prevent wild encounters (effectValue = step count)
```

**HEAL_STATUS `effectValue` encoding:**
- `0` = cure all statuses (Full Heal)
- `1` = BURN only (Burn Heal)
- `2` = PARALYZE only (Parlyz Heal)
- `3` = SLEEP only (Awakening)
- `4` = POISON only (Antidote)
- `5` = FREEZE only (Ice Heal)

Values match `StatusCondition` ordinal (from pokemon-data). The item use system checks `effectValue == 0 || effectValue == target.getStatusCondition().ordinal()`.

**Note — Item Use Service:** The effects above define *what* items do. The actual execution logic (applying HP, checking status, consuming the item) belongs to a future `ItemUseService` or battle system. This plan only defines the data model.

### `ItemDefinition` (data class)

Item template. Loaded from JSON.

```java
- itemId: String          // "potion", "pokeball", "bicycle"
- name: String            // "Potion"
- description: String     // "Restores 20 HP."
- category: ItemCategory
- price: int              // Buy price (0 = not buyable)
- sellPrice: int          // Sell price (usually price/2)
- usableInBattle: boolean // Can be used during battle
- usableOutside: boolean  // Can be used from bag menu
- consumable: boolean     // Consumed on use (false for key items)
- stackLimit: int         // Max stack (99 for regular, 1 for key items)
- spriteId: String        // Asset path for icon
- effect: ItemEffect      // What happens when used
- effectValue: int        // Numeric parameter (e.g., 20 for Potion = heal 20 HP)
- teachesMove: String     // moveId for TM/HM items (e.g., "thunderbolt"), null otherwise
```

### `ItemStack` (data class)

```java
- itemId: String
- quantity: int

+add(n): void
+remove(n): boolean      // false if not enough
+isEmpty(): boolean

// Serialization
+toSaveData(): Map<String, Object>   // { "itemId": "potion", "quantity": 5 }
+fromSaveData(Map<String, Object>): ItemStack  // static
```

### `Inventory` (data class)

Organizes items into pockets by category, with per-pocket capacity limits.

```java
- pockets: Map<ItemCategory, List<ItemStack>>
- registeredItems: List<String>    // itemIds registered for quick-use (Bicycle, etc.)

public static final int POCKET_CAPACITY = 50;  // Max unique item stacks per pocket

+addItem(itemId, quantity): boolean     // false if pocket full or stack limit reached
+removeItem(itemId, quantity): boolean  // false if not enough
+hasItem(itemId): boolean
+hasItem(itemId, minQuantity): boolean
+getCount(itemId): int
+getPocket(category): List<ItemStack>  // Unmodifiable view
+getAllItems(): List<ItemStack>
+isFull(category): boolean             // pocket.size() >= POCKET_CAPACITY

// Registered items (quick-use from overworld)
+registerItem(itemId): void
+unregisterItem(itemId): void
+getRegisteredItems(): List<String>    // Unmodifiable

// Sorting
+sortPocket(category, SortMode): void

// Serialization
+toSaveData(): Map<String, Object>
+fromSaveData(Map<String, Object>): Inventory  // static
```

**`addItem` logic:**
1. Load `ItemRegistry` via `Assets.load()` internally, look up `ItemDefinition` to get category and stackLimit
2. Find the pocket for that category
3. Check pocket capacity (`pocket.size() < POCKET_CAPACITY` or item already exists in pocket)
4. If item exists: increment quantity (capped at stackLimit)
5. If item doesn't exist: add new `ItemStack` to pocket
6. Return false if pocket is full and item is new

### `SortMode` (enum)

```
BY_NAME       // Alphabetical by item name (looks up ItemRegistry for display name)
BY_ID         // Alphabetical by itemId
BY_CATEGORY   // Group by category (useful for ALL_ITEMS view)
```

### `ItemRegistry` (registry)

```java
- items: Map<String, ItemDefinition>

+get(itemId): ItemDefinition
+getByCategory(cat): List<ItemDefinition>
+getAll(): Collection<ItemDefinition>
+load(path): void

// For hot-reload support
+copyFrom(other): void
```

---

## ECS Component

### `PlayerInventoryComponent`

Uses **write-through persistence** to `PlayerData` — every mutation immediately flushes to `SaveManager.globalState`, so `PlayerData` is always up-to-date and `SaveManager.save()` can be called at any time.

```java
@ComponentMeta(category = "Player")
public class PlayerInventoryComponent extends Component {
    private Inventory inventory = new Inventory();
    private int money = 0;

    // Inventory delegation
    +getInventory(): Inventory
    +addItem(itemId, quantity): boolean
    +removeItem(itemId, quantity): boolean
    +hasItem(itemId): boolean
    +getItemCount(itemId): int

    // Money
    +getMoney(): int
    +addMoney(amount): void
    +spendMoney(amount): boolean  // false if insufficient
}
```

**PlayerData integration:**

This plan adds `inventory` and `money` fields to `PlayerData`:

```java
public class PlayerData {
    // ... existing fields ...
    public InventoryData inventory;    // Pockets + registered items
    public int money;
}
```

```java
@Override
protected void onStart() {
    PlayerData data = PlayerData.load();
    if (data.inventory != null) {
        inventory = data.inventory.toInventory();
    }
    money = data.money;
}

// Called at the end of every mutation method (addItem, removeItem, addMoney, spendMoney, sortPocket, etc.)
private void flushToPlayerData() {
    PlayerData data = PlayerData.load();
    data.inventory = InventoryData.fromInventory(inventory);
    data.money = money;
    data.save();
}
```

Example mutations:
```java
public boolean addItem(String itemId, int quantity) {
    boolean result = inventory.addItem(itemId, quantity);
    if (result) flushToPlayerData();
    return result;
}

public boolean spendMoney(int amount) {
    if (money < amount) return false;
    money -= amount;
    flushToPlayerData();
    return true;
}
```

**Why write-through?** `PlayerData.save()` writes to `SaveManager.globalState` in memory (not to disk) — it's cheap. This guarantees `SaveManager.save()` always persists the latest state, regardless of whether a scene transition happened. Without this, a player who buys items at a shop and then saves the game would lose their purchases.

### `ItemUseService` (utility)

Static utility that executes item effects on a target Pokemon. Called by the bag UI, battle system, or any system that needs to "use" an item.

```java
public class ItemUseService {

    +canUse(ItemDefinition item, PokemonInstance target): boolean
    +useItem(ItemDefinition item, PokemonInstance target): ItemUseResult

    public enum ItemUseResult {
        SUCCESS,           // Item used successfully
        NO_EFFECT,         // Item had no effect (e.g., healing a full-HP Pokemon)
        INVALID_TARGET     // Wrong target (e.g., status heal on a Pokemon without that status)
    }
}
```

**Effect execution logic** (inside `useItem`):

```java
switch (item.getEffect()) {
    case HEAL_HP -> {
        if (target.getCurrentHp() >= target.calcMaxHp()) return NO_EFFECT;
        target.heal(item.getEffectValue());
        return SUCCESS;
    }
    case HEAL_FULL -> {
        if (target.getCurrentHp() >= target.calcMaxHp()) return NO_EFFECT;
        target.healFull();
        return SUCCESS;
    }
    case HEAL_STATUS -> {
        StatusCondition current = target.getStatusCondition();
        if (current == StatusCondition.NONE) return NO_EFFECT;
        int targetOrdinal = item.getEffectValue();
        if (targetOrdinal != 0 && targetOrdinal != current.ordinal()) return INVALID_TARGET;
        target.cureStatus();
        return SUCCESS;
    }
    case HEAL_FULL_RESTORE -> {
        boolean healed = false;
        if (target.getCurrentHp() < target.calcMaxHp()) { target.healFull(); healed = true; }
        if (target.getStatusCondition() != StatusCondition.NONE) { target.cureStatus(); healed = true; }
        return healed ? SUCCESS : NO_EFFECT;
    }
    case REVIVE -> {
        if (target.isAlive()) return NO_EFFECT;
        int hpPercent = item.getEffectValue();
        int restoreHp = target.calcMaxHp() * hpPercent / 100;
        target.heal(restoreHp);
        return SUCCESS;
    }
    case TEACH_MOVE -> {
        // Returns SUCCESS if move learned, or INVALID_TARGET if move slots full (caller prompts replace)
        // Actual move teaching handled by caller (needs UI for slot selection)
        return SUCCESS;
    }
    case EVOLUTION_ITEM -> {
        // Check if target species can evolve with this item
        // Actual evolution handled by caller (needs Pokedex lookup + UI)
        return SUCCESS;
    }
    case CAPTURE, BOOST_ATK, BOOST_DEF, BOOST_SP_ATK, BOOST_SP_DEF,
         BOOST_SPD, BOOST_ACCURACY, BOOST_CRIT, REPEL -> {
        // Battle-only effects — handled by battle system, not ItemUseService
        return NO_EFFECT;
    }
    case TOGGLE_BICYCLE, NONE -> {
        return NO_EFFECT;
    }
}
```

**Consumption:** After `useItem()` returns `SUCCESS`, the caller is responsible for removing the item from inventory if `ItemDefinition.consumable` is true.

**Note:** `TEACH_MOVE` and `EVOLUTION_ITEM` require additional context (Pokedex, move slot selection UI) that `ItemUseService` doesn't own. The service validates basic eligibility; the caller drives the full interaction flow.

### `ItemPickup` (component)

World-placed item pickup. Attached to a GameObject in the scene (e.g., a Pokeball on the ground). When the player interacts with it, the item is added to their inventory and the pickup is destroyed/disabled.

Extends `InteractableComponent` (the standard base class for all interactables — handles TriggerZone registration, directional interaction, gizmo drawing).

```java
@ComponentMeta(category = "Interaction")
public class ItemPickup extends InteractableComponent {
    private String itemId;           // "potion", "tm24_thunderbolt", etc. (references ItemRegistry)
    private int quantity = 1;        // How many to give
    private boolean destroyOnPickup = true;  // true = destroy GameObject, false = disable it

    public ItemPickup() {
        gizmoShape = GizmoShape.CIRCLE;
        gizmoColor = GizmoColors.fromRGBA(0.2f, 1.0f, 0.4f, 0.9f);  // Green
    }

    @Override
    public void interact(GameObject player) {
        // 1. Get PlayerInventoryComponent from player
        // 2. Try inventory.addItem(itemId, quantity)
        // 3. If successful:
        //    a. (Optional) Show "Found X Potion(s)!" message via UI
        //    b. If destroyOnPickup: remove this GameObject from scene
        //       Else: disable this GameObject
        // 4. If inventory full: show "Bag is full!" message, do NOT destroy
    }

    @Override
    public String getInteractionPrompt() {
        return "Pick up";
    }
}
```

**Inspector fields:**
- `itemId` — text field (or dropdown from `ItemRegistry` if editor support is available)
- `quantity` — integer field (default 1)
- `destroyOnPickup` — checkbox (default true)

**Persistence:** The pickup GameObject needs a `PersistentEntity` component so the save system tracks that it was destroyed/disabled. When the player revisits the scene, the item stays gone.

**Scene setup example:**

```
ItemPickup_Potion (GameObject)
├── PersistentEntity("pickup_route1_potion")  ◄── required for save persistence
├── Transform (position on map)
├── SpriteRenderer (pokeball/item sprite)
├── TriggerZone                               ◄── auto-added via @RequiredComponent on InteractableComponent
└── ItemPickup
    ├── itemId: "potion"
    ├── quantity: 1
    └── destroyOnPickup: true
```

**Interaction flow:**

```
Player walks up to item → InteractionController detects TriggerZone
  → Player presses interact button
  → ItemPickup.interact(player) called
  → PlayerInventoryComponent.addItem("potion", 1)
  → If success:
      → Show "Found a Potion!"
      → scene.removeGameObject(this.gameObject)
      → SaveManager tracks destroyed entity via PersistentEntity
  → If bag full:
      → Show "Your bag is full!"
      → Item stays in the world (player can come back later)
```

---

## AssetLoader Integration

### `ItemRegistryLoader` implements `AssetLoader<ItemRegistry>`

```java
- getSupportedExtensions(): [".items.json"]
- supportsHotReload(): true
- load(path): Parses items from JSON → ItemRegistry
- reload(existing, path): Mutates existing ItemRegistry in place (MUST return same reference)
- save(registry, path): Serializes back to JSON
- getPlaceholder(): Empty ItemRegistry
- getIconCodepoint(): MaterialIcons.Backpack (or similar)
- getEditorPanelType(): ITEM_REGISTRY_EDITOR  // NOTE: Add ITEM_REGISTRY_EDITOR to EditorPanelType enum during implementation
```

### Data File Location

```
gameData/data/items/items.items.json
```

### Usage

```java
ItemRegistry items = Assets.load("data/items/items.items.json", ItemRegistry.class);
ItemDefinition potion = items.get("potion");
```

---

## JSON Data Format

### items.items.json

```json
[
  {
    "itemId": "potion",
    "name": "Potion",
    "description": "Restores 20 HP to a single Pokemon.",
    "category": "MEDICINE",
    "price": 200,
    "sellPrice": 100,
    "usableInBattle": true,
    "usableOutside": true,
    "consumable": true,
    "stackLimit": 99,
    "spriteId": "sprites/items/potion",
    "effect": "HEAL_HP",
    "effectValue": 20,
    "teachesMove": null
  },
  {
    "itemId": "pokeball",
    "name": "Poke Ball",
    "description": "A device for catching wild Pokemon.",
    "category": "POKEBALL",
    "price": 200,
    "sellPrice": 100,
    "usableInBattle": true,
    "usableOutside": false,
    "consumable": true,
    "stackLimit": 99,
    "spriteId": "sprites/items/pokeball",
    "effect": "CAPTURE",
    "effectValue": 1,
    "teachesMove": null
  },
  {
    "itemId": "tm24_thunderbolt",
    "name": "TM24",
    "description": "Teaches the move Thunderbolt.",
    "category": "TM_HM",
    "price": 3000,
    "sellPrice": 1500,
    "usableInBattle": false,
    "usableOutside": true,
    "consumable": true,
    "stackLimit": 99,
    "spriteId": "sprites/items/tm_electric",
    "effect": "TEACH_MOVE",
    "effectValue": 0,
    "teachesMove": "thunderbolt"
  },
  {
    "itemId": "thunder_stone",
    "name": "Thunder Stone",
    "description": "Evolves certain Pokemon when used.",
    "category": "HELD_ITEM",
    "price": 2100,
    "sellPrice": 1050,
    "usableInBattle": false,
    "usableOutside": true,
    "consumable": true,
    "stackLimit": 99,
    "spriteId": "sprites/items/thunder_stone",
    "effect": "EVOLUTION_ITEM",
    "effectValue": 0,
    "teachesMove": null
  },
  {
    "itemId": "bicycle",
    "name": "Bicycle",
    "description": "A folding bicycle that doubles movement speed.",
    "category": "KEY_ITEM",
    "price": 0,
    "sellPrice": 0,
    "usableInBattle": false,
    "usableOutside": true,
    "consumable": false,
    "stackLimit": 1,
    "spriteId": "sprites/items/bicycle",
    "effect": "TOGGLE_BICYCLE",
    "effectValue": 0,
    "teachesMove": null
  }
]
```

## Item Sprites

```
gameData/assets/sprites/items/
├── potion.png
├── pokeball.png
├── great_ball.png
├── tm_electric.png
├── thunder_stone.png
├── bicycle.png
└── ...
```

`ItemDefinition.spriteId` stores the full path (e.g., `"sprites/items/potion"`).

---

## Save Structure (via PlayerData)

Inventory state is stored in `SaveManager.globalState["player"]["data"]` as part of the PlayerData JSON:

```json
{
  "playerName": "Red",
  "money": 3000,
  "inventory": {
    "pockets": {
      "MEDICINE": [
        { "itemId": "potion", "quantity": 5 },
        { "itemId": "super_potion", "quantity": 2 }
      ],
      "POKEBALL": [
        { "itemId": "pokeball", "quantity": 10 }
      ]
    },
    "registeredItems": ["bicycle"]
  }
}
```

---

## Implementation Phases

| Phase | Scope |
|-------|-------|
| **1** | Enums: `ItemCategory`, `ItemEffect`, `SortMode` |
| **2** | Data classes: `ItemDefinition`, `ItemStack` (with toSaveData/fromSaveData) |
| **3** | `Inventory` (pockets, add/remove, registered items, sorting, serialization) |
| **4** | `ItemRegistry` registry class |
| **5** | `ItemRegistryLoader` (AssetLoader) + sample `items.items.json` |
| **6** | `ItemUseService` (effect execution for HEAL_HP, HEAL_STATUS, REVIVE, etc.) |
| **7** | `PlayerInventoryComponent` (write-through PlayerData integration, money) + add `inventory`/`money` fields to `PlayerData` |
| **8** | `ItemPickup` component (extends InteractableComponent) |
| **9** | Unit tests for add/remove, pocket limits, sorting, serialization round-trip, ItemUseService effects, pickup interaction |

## Acceptance Criteria

- [ ] 7 `ItemCategory` values with distinct behavior (KEY_ITEM: stackLimit=1, not consumable, not sellable)
- [ ] `Inventory` enforces POCKET_CAPACITY (50 unique stacks per pocket) and per-item stackLimit
- [ ] `addItem` / `removeItem` correctly track quantities across category pockets
- [ ] All 3 `SortMode` values produce correct pocket ordering
- [ ] `ItemRegistry` loads from JSON and provides lookup by ID and by category
- [ ] `ItemRegistryLoader` integrates with asset pipeline (load, hot-reload, save, editor icon)
- [ ] `ItemUseService` correctly executes all healable effects (HEAL_HP, HEAL_FULL, HEAL_STATUS, HEAL_FULL_RESTORE, REVIVE) and returns appropriate result codes
- [ ] `PlayerInventoryComponent` uses write-through to PlayerData — `SaveManager.save()` always captures latest state
- [ ] `ItemPickup` adds item on interact and destroys/disables itself; stays in world when bag is full
- [ ] Money: `addMoney` / `spendMoney` work correctly, insufficient funds rejected

## Testing Plan

### Unit Tests

**Inventory:**
- `addItem` — new item creates stack in correct pocket, existing item increments quantity
- `addItem` — quantity capped at stackLimit (e.g., 99)
- `addItem` — 51st unique item in a pocket returns false (POCKET_CAPACITY)
- `addItem` — item already at stackLimit, additional quantity rejected
- `removeItem` — quantity decremented correctly
- `removeItem` — insufficient quantity returns false, no state change
- `removeItem` — removing last unit removes the stack entirely
- `hasItem` / `hasItem(id, minQty)` / `getCount` — correct for present and absent items
- `getPocket` — returns unmodifiable view of correct category
- Key items — stackLimit enforced at 1, added to KEY_ITEM pocket
- `sortPocket(BY_NAME)` — alphabetical by display name
- `sortPocket(BY_ID)` — alphabetical by itemId
- `sortPocket(BY_CATEGORY)` — grouped by category
- Registered items — `registerItem`, `unregisterItem`, `getRegisteredItems` (unmodifiable)
- Serialization round-trip — `toSaveData()` → `fromSaveData()` preserves all pockets, quantities, and registered items

**ItemRegistry & ItemRegistryLoader:**
- `ItemRegistry.get()` — returns correct definition for known ID, null for unknown
- `ItemRegistry.getByCategory()` — returns all items in that category
- `ItemRegistryLoader.load()` — parses sample JSON, items accessible by ID
- `ItemRegistryLoader.reload()` — mutates existing registry in place, updated data reflected

**ItemUseService:**
- `HEAL_HP` — heals by effectValue, clamped at maxHp
- `HEAL_HP` on full-HP Pokemon — returns NO_EFFECT
- `HEAL_FULL` — restores to maxHp
- `HEAL_STATUS` with effectValue=0 (Full Heal) — cures any status
- `HEAL_STATUS` with specific ordinal — cures matching status, returns INVALID_TARGET for wrong status
- `HEAL_STATUS` on healthy Pokemon (NONE) — returns NO_EFFECT
- `HEAL_FULL_RESTORE` — heals HP + cures status; NO_EFFECT if both already fine
- `REVIVE` — restores fainted Pokemon to effectValue% HP
- `REVIVE` on alive Pokemon — returns NO_EFFECT
- Battle-only effects (CAPTURE, BOOST_*) — return NO_EFFECT from ItemUseService

**PlayerInventoryComponent:**
- Write-through — `addItem()` → `PlayerData.load()` immediately reflects the new item
- Write-through — `spendMoney()` → `PlayerData.load()` immediately reflects reduced money
- `spendMoney` with insufficient funds — returns false, money unchanged, PlayerData unchanged
- `onStart()` from populated PlayerData — inventory and money correctly initialized
- `onStart()` from empty PlayerData — clean defaults (empty inventory, 0 money)

**ItemPickup:**
- `interact()` — item added to player inventory, correct itemId and quantity
- `interact()` with destroyOnPickup=true — GameObject removed from scene
- `interact()` with destroyOnPickup=false — GameObject disabled
- `interact()` with full pocket — item NOT added, GameObject remains active

### Manual Tests

- Load `items.items.json` in editor — verify it appears in asset browser with correct icon
- Edit JSON externally, trigger hot-reload — verify changes reflected
- Place `ItemPickup` in a test scene, run game, walk up and interact — verify item appears in inventory
- Place `ItemPickup` with full pocket — verify "bag full" behavior (item stays in world)
- Interact with ItemPickup, save game, reload — verify pickup stays gone (PersistentEntity)
- Add/spend money via debug, save game mid-scene, reload — verify money persisted correctly
