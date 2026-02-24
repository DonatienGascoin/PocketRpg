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
│   ├── ItemDefinition.java     # Item template with Builder pattern (Potion, Pokeball, TM24...)
│   ├── ItemStack.java          # Item + quantity pair
│   ├── InventoryData.java      # Typed save-data POJO for Inventory (stored in PlayerData)
│   ├── SortMode.java           # BY_NAME, BY_ID, BY_CATEGORY
│   ├── Inventory.java          # Collection of ItemStacks organized by category pockets
│   ├── ItemRegistry.java       # Registry: itemId → ItemDefinition
│   └── ItemUseService.java     # Static utility: execute item effects on a Pokemon
│
├── components/pokemon/
│   └── PlayerInventoryComponent.java  # Player's bag + money (write-through to PlayerData)
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
HEAL_STATUS         // Cure status condition (uses targetStatus field, see below)
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

**HEAL_STATUS `targetStatus` encoding:**

Uses the `ItemDefinition.targetStatus` string field (the `StatusCondition` enum name):
- `null` or `""` = cure all statuses (Full Heal)
- `"BURN"` = BURN only (Burn Heal)
- `"PARALYZE"` = PARALYZE only (Parlyz Heal)
- `"SLEEP"` = SLEEP only (Awakening)
- `"POISON"` = POISON only (Antidote)
- `"FREEZE"` = FREEZE only (Ice Heal)

The item use system checks `StatusCondition.valueOf(targetStatus)` and compares to the target's current status. Invalid `targetStatus` strings are treated as NO_EFFECT.

### `ItemDefinition` (data class — Builder pattern)

Item template. Loaded from JSON by `ItemRegistryLoader` (not Gson auto-deserialization). Constructed programmatically via the Builder pattern. The `sprite` field holds an actual `Sprite` object at runtime; the loader converts between JSON string paths and Sprite objects via `SpriteReference`.

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
- sprite: Sprite          // Item icon (loaded from asset path by ItemRegistryLoader)
- effect: ItemEffect      // What happens when used (default: NONE)
- effectValue: int        // Numeric parameter (e.g., 20 for Potion = heal 20 HP)
- teachesMove: String     // moveId for TM/HM items (e.g., "thunderbolt"), null otherwise
- targetStatus: String    // For HEAL_STATUS: StatusCondition name ("POISON", "BURN"), null/empty = cure all
```

**Builder usage:**
```java
ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
    .description("Restores 20 HP.").price(200).sellPrice(100)
    .usableInBattle(true).usableOutside(true).consumable(true)
    .effect(ItemEffect.HEAL_HP).effectValue(20).build();
```

### `ItemStack` (data class)

```java
- itemId: String
- quantity: int

+add(n): boolean         // false if n <= 0 (consistent with remove)
+remove(n): boolean      // false if not enough or n <= 0
+isEmpty(): boolean

// Serialization
+toSaveData(): Map<String, Object>   // { "itemId": "potion", "quantity": 5 }
+fromSaveData(Map<String, Object>): ItemStack  // static, clamps negative qty to 0
```

### `Inventory` (data class)

Organizes items into pockets by category, with per-pocket capacity limits.

```java
- pockets: Map<ItemCategory, List<ItemStack>>
- registeredItems: List<String>    // itemIds registered for quick-use (Bicycle, etc.)

public static final int POCKET_CAPACITY = 50;  // Max unique item stacks per pocket

+addItem(itemId, quantity, registry): int  // returns quantity actually added (0 if pocket full, unknown item, or invalid qty)
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
+sortPocket(category, SortMode, ItemRegistry): void

// Serialization — uses typed InventoryData (not loose Map)
+toSaveData(): InventoryData
+fromSaveData(InventoryData): Inventory  // static, validates capacity + qty > 0
```

**`addItem` logic:**
1. Receives `ItemRegistry` as parameter (caller provides it)
2. Look up `ItemDefinition` to get category and stackLimit; return 0 if unknown
3. Find the pocket for that category
4. If item exists: increment quantity (capped at stackLimit), return actual amount added
5. If item doesn't exist and pocket is full: return 0
6. If item doesn't exist: add new `ItemStack` to pocket, return quantity (capped at stackLimit)

### `SortMode` (enum)

```
BY_NAME       // Alphabetical by item name (looks up ItemRegistry for display name)
BY_ID         // Alphabetical by itemId
BY_CATEGORY   // Group by category (useful for ALL_ITEMS view)
```

### `ItemRegistry` (registry)

```java
- items: Map<String, ItemDefinition>

+get(itemId): ItemDefinition            // null for unknown
+getByCategory(cat): List<ItemDefinition>  // unmodifiable
+getAll(): Collection<ItemDefinition>      // unmodifiable
+getItems(): Map<String, ItemDefinition>   // unmodifiable
+addItem(def): void
+removeItem(itemId): void

// For hot-reload support (used by ItemRegistryLoader.copyInto)
+copyFrom(other): void
```

---

## ECS Component

### `PlayerInventoryComponent`

Uses **write-through persistence** to `PlayerData` — every mutation immediately flushes to `SaveManager.globalState`, so `PlayerData` is always up-to-date and `SaveManager.save()` can be called at any time.

```java
@ComponentMeta(category = "Player")
public class PlayerInventoryComponent extends Component {
    public static final int MAX_MONEY = 999_999;

    private transient Inventory inventory = new Inventory();
    private transient int money = 0;

    // Inventory delegation
    +getInventory(): Inventory
    +addItem(itemId, quantity): int      // returns quantity actually added
    +removeItem(itemId, quantity): boolean
    +hasItem(itemId): boolean
    +getItemCount(itemId): int

    // Money
    +getMoney(): int
    +addMoney(amount): void              // capped at MAX_MONEY, overflow-safe
    +spendMoney(amount): boolean         // false if insufficient
}
```

**PlayerData integration:**

This plan adds `inventory` and `money` fields to `PlayerData`:

```java
public class PlayerData {
    // ... existing fields ...
    public InventoryData inventory;    // Typed save-data for pockets + registered items
    public int money;
}
```

`InventoryData` is a typed POJO (not a loose `Map<String, Object>`) that Gson serializes directly. It contains `Map<String, List<StackEntry>> pockets` and `List<String> registeredItems`, where `StackEntry` has `String itemId` and `int quantity`.

```java
@Override
protected void onStart() {
    PlayerData data = PlayerData.load();
    if (data.inventory != null) {
        inventory = Inventory.fromSaveData(data.inventory);
    }
    money = Math.max(0, Math.min(data.money, MAX_MONEY));
}

private void flushToPlayerData() {
    PlayerData data = PlayerData.load();
    data.inventory = inventory.toSaveData();
    data.money = money;
    data.save();
}
```

Example mutations:
```java
public int addItem(String itemId, int quantity) {
    ItemRegistry registry = getRegistry();
    if (registry == null) return 0;
    int added = inventory.addItem(itemId, quantity, registry);
    if (added > 0) flushToPlayerData();
    return added;
}

public void addMoney(int amount) {
    if (amount <= 0) return;
    money = (int) Math.min((long) money + amount, MAX_MONEY);  // overflow-safe
    flushToPlayerData();
}

public boolean spendMoney(int amount) {
    if (amount <= 0) return false;
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
        if (!target.isAlive()) return INVALID_TARGET;
        StatusCondition current = target.getStatusCondition();
        if (current == StatusCondition.NONE) return NO_EFFECT;
        String ts = item.getTargetStatus();
        if (ts != null && !ts.isEmpty()) {
            try {
                StatusCondition required = StatusCondition.valueOf(ts);
                if (current != required) return INVALID_TARGET;
            } catch (IllegalArgumentException e) {
                return NO_EFFECT;  // invalid targetStatus string
            }
        }
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
public class ItemPickup extends InteractableComponent implements ISaveable {
    private String itemId;           // "potion", "tm24_thunderbolt", etc. (references ItemRegistry)
    private int quantity = 1;        // How many to give
    private boolean destroyOnPickup = true;  // true = destroy GameObject, false = disable it
    private transient boolean pickedUp = false;  // persisted via ISaveable

    public ItemPickup() {
        gizmoShape = GizmoShape.CIRCLE;
        gizmoColor = GizmoColors.fromRGBA(0.2f, 1.0f, 0.4f, 0.9f);  // Green
    }

    @Override
    public boolean canInteract(GameObject player) {
        if (pickedUp || quantity <= 0) return false;
        return super.canInteract(player);
    }

    @Override
    public void interact(GameObject player) {
        // 1. Validate itemId and quantity
        // 2. Get PlayerInventoryComponent from player
        // 3. int added = inventory.addItem(itemId, quantity)
        // 4. If added == 0: "Bag is full!", do NOT destroy
        // 5. If added < quantity: partial pickup, reduce quantity, keep in world
        // 6. If added == quantity: full pickup, set pickedUp = true
        //    a. If destroyOnPickup: scene.removeGameObject(this.gameObject)
        //       Else: gameObject.setEnabled(false)
    }

    @Override
    protected void onInteractableStart() {
        if (pickedUp) {
            // Re-apply picked-up state on scene reload
            if (destroyOnPickup) scene.removeGameObject(gameObject);
            else gameObject.setEnabled(false);
        }
    }

    // ISaveable — persists pickedUp and quantity across scene reloads
    @Override public Map<String, Object> getSaveState() { ... }
    @Override public void loadSaveState(Map<String, Object> state) { ... }
    @Override public boolean hasSaveableState() { return pickedUp || quantity != 1; }

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

**Persistence:** Implements `ISaveable` to persist `pickedUp` and `quantity` via `PersistentId`. This handles both full and partial pickups across scene reloads. The `onInteractableStart()` hook re-applies the destroyed/disabled state when the scene is revisited.

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
  → ItemPickup.canInteract(player) checks: !pickedUp && quantity > 0 && directional check
  → ItemPickup.interact(player) called
  → int added = PlayerInventoryComponent.addItem("potion", quantity)
  → If added == 0 (bag full):
      → Show "Your bag is full!"
      → Item stays in the world (player can come back later)
  → If added < quantity (partial pickup):
      → Reduce quantity by added
      → Item stays with remaining quantity, ISaveable persists new quantity
  → If added == quantity (full pickup):
      → pickedUp = true
      → If destroyOnPickup: scene.removeGameObject(this.gameObject)
         Else: gameObject.setEnabled(false)
      → ISaveable persists pickedUp state via PersistentId
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

The JSON format wraps items in an `{"items": [...]}` root object (for `JsonAssetLoader` compatibility). Status-healing items use `targetStatus` instead of ordinal `effectValue`:

```json
{
  "items": [
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
}
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

`ItemDefinition.sprite` holds the actual `Sprite` object at runtime. The JSON stores string paths (e.g., `"spriteId": "sprites/items/potion"`), and `ItemRegistryLoader` converts between them via `SpriteReference.fromPath()` / `SpriteReference.toPath()`.

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

- [x] 7 `ItemCategory` values with distinct behavior (KEY_ITEM: stackLimit=1, not consumable, not sellable)
- [x] `Inventory` enforces POCKET_CAPACITY (50 unique stacks per pocket) and per-item stackLimit
- [x] `addItem` / `removeItem` correctly track quantities across category pockets
- [x] All 3 `SortMode` values produce correct pocket ordering
- [x] `ItemRegistry` loads from JSON and provides lookup by ID and by category
- [x] `ItemRegistryLoader` integrates with asset pipeline (load, hot-reload, save, editor icon)
- [x] `ItemUseService` correctly executes all healable effects (HEAL_HP, HEAL_FULL, HEAL_STATUS, HEAL_FULL_RESTORE, REVIVE) and returns appropriate result codes
- [x] `PlayerInventoryComponent` uses write-through to PlayerData — `SaveManager.save()` always captures latest state
- [x] `ItemPickup` adds item on interact and destroys/disables itself; stays in world when bag is full
- [x] Money: `addMoney` / `spendMoney` work correctly, insufficient funds rejected

## Testing Plan

### Unit Tests

**Inventory:**
- `addItem` — new item creates stack in correct pocket, existing item increments quantity
- `addItem` — quantity capped at stackLimit (e.g., 99), returns actual qty added
- `addItem` — 51st unique item in a pocket returns 0 (POCKET_CAPACITY enforced)
- `addItem` — item already at stackLimit, returns 0
- `addItem` — unknown item returns 0; zero/negative qty returns 0
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
- `fromSaveData` — null returns empty; unknown category skipped; enforces pocket capacity; skips zero-quantity stacks

**ItemRegistry & ItemRegistryLoader:**
- `ItemRegistry.get()` — returns correct definition for known ID, null for unknown
- `ItemRegistry.getByCategory()` — returns all items in that category (unmodifiable)
- `ItemRegistry.getAll()` — returns unmodifiable collection
- `ItemRegistry.getItems()` — returns unmodifiable map
- `ItemRegistry.removeItem()` — removes existing, no-op for unknown
- `ItemRegistry.copyFrom()` — replaces all items
- `ItemRegistryLoader` — uses builder pattern, parses `targetStatus`

**ItemUseService:**
- `HEAL_HP` — heals by effectValue, clamped at maxHp; NO_EFFECT on full HP; INVALID_TARGET on fainted
- `HEAL_FULL` — restores to maxHp; NO_EFFECT on full HP; INVALID_TARGET on fainted
- `HEAL_STATUS` with null/empty targetStatus (Full Heal) — cures any status
- `HEAL_STATUS` with specific targetStatus — cures matching status, INVALID_TARGET for wrong status
- `HEAL_STATUS` on healthy Pokemon (NONE) — returns NO_EFFECT; INVALID_TARGET on fainted
- `HEAL_STATUS` with invalid targetStatus string — returns NO_EFFECT
- `HEAL_FULL_RESTORE` — heals HP + cures status; status-only at full HP; HP-only without status; NO_EFFECT if both fine; INVALID_TARGET on fainted
- `REVIVE` — restores fainted Pokemon to effectValue% HP; 0% restores at least 1 HP
- `REVIVE` on alive Pokemon — returns NO_EFFECT
- Battle-only effects (CAPTURE, BOOST_*) — return NO_EFFECT from ItemUseService
- Null safety — null item/target returns INVALID_TARGET; canUse with null returns false
- Edge effects — null effect, NONE, TEACH_MOVE (SUCCESS), EVOLUTION_ITEM (SUCCESS), TOGGLE_BICYCLE (NO_EFFECT), REPEL (NO_EFFECT)
- `canUse` — dry-run (no side effects on target)

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
