# Pokemon, Inventory & Item System — Design Document

## Overview

The player needs three core game-data systems to support a classic Pokemon-style RPG:

1. **Pokemon System** — Pokemon species definitions, individual Pokemon instances, stats, moves, team management, PC storage
2. **Item System** — Item definitions (potions, pokeballs, key items, etc.) with categories and usage effects
3. **Inventory System** — Player-owned items with quantities, bag pockets, and a component to bridge into the ECS

These three systems are **data-model-first** — they define game state that will be persisted by the save system and consumed by the battle system later. They live outside the ECS component hierarchy as pure data, with thin ECS components providing the bridge.

---

## Architecture Approach

### Data vs Components

Following the existing PocketRpg pattern (Dialogue data model + DialogueInteractable component), the design separates:

| Layer | Purpose | Examples |
|-------|---------|---------|
| **Data models** (`pokemon/`, `items/`) | Pure game data, JSON-serializable, no ECS dependency | `PokemonSpecies`, `PokemonInstance`, `ItemDefinition` |
| **Components** (`components/pokemon/`) | ECS bridge — attach data to GameObjects | `PlayerPartyComponent`, `PlayerInventoryComponent` |
| **Registries** (static lookup) | Index definitions by ID for quick access | `Pokedex`, `ItemRegistry` |

### Package Layout

```
com.pocket.rpg/
├── pokemon/                          # Pokemon data models
│   ├── PokemonSpecies.java           # Species template (Bulbasaur, Pikachu...)
│   ├── PokemonInstance.java          # Individual Pokemon (your level 25 Pikachu)
│   ├── PokemonType.java              # Element enum (FIRE, WATER, GRASS...)
│   ├── Stats.java                    # HP/Atk/Def/SpAtk/SpDef/Spd
│   ├── Move.java                     # Move definition (Tackle, Thunderbolt...)
│   ├── MoveSlot.java                 # Move instance with current PP
│   ├── LearnedMove.java              # Level → Move mapping for learnsets
│   ├── Nature.java                   # Nature enum (stat modifiers)
│   └── Pokedex.java                  # Registry: speciesId → PokemonSpecies
│
├── items/                            # Item data models
│   ├── ItemDefinition.java           # Item template (Potion, Pokeball...)
│   ├── ItemCategory.java             # Category enum (MEDICINE, BALL, KEY_ITEM...)
│   ├── ItemStack.java                # Item + quantity pair
│   ├── Inventory.java                # Collection of ItemStacks, bag logic
│   └── ItemRegistry.java             # Registry: itemId → ItemDefinition
│
├── components/pokemon/               # ECS bridge (existing package)
│   ├── PlayerPartyComponent.java     # Player's team of up to 6 Pokemon
│   ├── PlayerInventoryComponent.java # Player's bag
│   └── PokemonStorageComponent.java  # PC box storage
```

---

## Class Diagram (ASCII)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              POKEMON SYSTEM                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────┐         ┌──────────────────┐        ┌───────────────┐        │
│  │ PokemonType  │         │  PokemonSpecies   │        │    Nature     │        │
│  │ «enum»       │         │                   │        │ «enum»        │        │
│  ├──────────────┤    type │ - speciesId: Str  │        ├───────────────┤        │
│  │ NORMAL       │◄────────│ - name: String    │        │ HARDY         │        │
│  │ FIRE         │         │ - type: PokeType  │        │ LONELY (+A-D) │        │
│  │ WATER        │         │ - baseStats: Stats│        │ BRAVE  (+A-Sp)│        │
│  │ GRASS        │         │ - learnset: List  │        │ ADAMANT(+A-SA)│        │
│  │ ELECTRIC     │         │ - baseExpYield:int│        │ ...           │        │
│  │ ICE          │         │ - catchRate: int  │        │               │        │
│  │ FIGHTING     │         │ - spriteId: Str   │        │ +atkMod():f   │        │
│  │ POISON       │         │                   │        │ +defMod():f   │        │
│  │ GROUND       │         └───────┬───────────┘        │ +spAtkMod():f │        │
│  │ FLYING       │                 │                    │ +spDefMod():f │        │
│  │ PSYCHIC      │                 │ defines            │ +spdMod():f   │        │
│  │ BUG          │                 ▼                    └───────────────┘        │
│  │ ROCK         │         ┌──────────────────┐                │                 │
│  │ GHOST        │         │ PokemonInstance   │   nature      │                 │
│  │ DRAGON       │         │                   │◄──────────────┘                 │
│  │ DARK         │         │ - nickname: Str?  │                                 │
│  │ STEEL        │         │ - species: SpecId │        ┌──────────────┐         │
│  │ FAIRY        │         │ - level: int      │        │   Stats      │         │
│  │              │         │ - exp: int        │ base   │              │         │
│  │ +effective() │         │ - nature: Nature  │───────►│ - hp: int    │         │
│  │ +resist()    │         │ - ivs: Stats      │ ivs    │ - atk: int   │         │
│  │ +immune()    │         │ - currentHp: int  │───────►│ - def: int   │         │
│  └──────────────┘         │ - moves: MoveSlot[4]       │ - spAtk: int │         │
│                           │ - ot: String      │        │ - spDef: int │         │
│                           │ - pokeball: Str   │        │ - spd: int   │         │
│                           │                   │        │              │         │
│                           │ +calcStat(s):int  │        │ +total():int │         │
│                           │ +isAlive():bool   │        └──────────────┘         │
│                           │ +canFight():bool  │                                 │
│                           │ +heal(hp):void    │                                 │
│                           │ +damage(hp):void  │                                 │
│                           └───────┬───────────┘                                 │
│                                   │ has 0..4                                    │
│                                   ▼                                             │
│                           ┌──────────────────┐        ┌───────────────┐         │
│                           │   MoveSlot        │        │    Move       │         │
│                           │                   │ refs   │               │         │
│                           │ - moveId: String  │───────►│ - moveId: Str │         │
│                           │ - currentPp: int  │        │ - name: Str   │         │
│                           │                   │        │ - type: PokeT │         │
│                           │ +hasPp():bool     │        │ - power: int  │         │
│                           └──────────────────┘        │ - accuracy:int│         │
│                                                        │ - pp: int     │         │
│  ┌──────────────────┐                                  │ - category:   │         │
│  │  LearnedMove      │                                  │   MoveCategory│        │
│  │                   │                                  │ - effect: Str │         │
│  │ - level: int      │           refs                  │               │         │
│  │ - moveId: String  │─────────────────────────────────►│               │        │
│  └──────────────────┘                                  └───────────────┘         │
│                                                                                 │
│  ┌──────────────────┐                                  ┌───────────────┐         │
│  │ MoveCategory     │                                  │    Pokedex    │         │
│  │ «enum»           │                                  │  «registry»   │         │
│  ├──────────────────┤                                  ├───────────────┤         │
│  │ PHYSICAL         │                                  │ -species: Map │         │
│  │ SPECIAL          │                                  │ -moves: Map   │         │
│  │ STATUS           │                                  │               │         │
│  └──────────────────┘                                  │ +getSpecies() │         │
│                                                        │ +getMove()    │         │
│                                                        │ +load(path)   │         │
│                                                        └───────────────┘         │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ITEM SYSTEM                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────┐        ┌───────────────────┐       ┌──────────────────┐  │
│  │  ItemCategory    │        │  ItemDefinition    │       │  ItemRegistry    │  │
│  │  «enum»          │        │                    │       │  «registry»      │  │
│  ├──────────────────┤  cat   │ - itemId: String   │       ├──────────────────┤  │
│  │ MEDICINE         │◄───────│ - name: String     │       │ -items: Map      │  │
│  │ POKEBALL         │        │ - description: Str │       │                  │  │
│  │ BATTLE           │        │ - category: ItemCat│       │ +get(id):ItemDef │  │
│  │ TM_HM            │        │ - price: int       │       │ +load(path)      │  │
│  │ BERRY            │        │ - sellPrice: int   │       └──────────────────┘  │
│  │ KEY_ITEM         │        │ - usableInBattle:b │                             │
│  │ HELD_ITEM        │        │ - usableOutside:b  │                             │
│  └──────────────────┘        │ - consumable: bool │                             │
│                              │ - spriteId: String │                             │
│                              │ - effect: String   │                             │
│                              └────────┬───────────┘                             │
│                                       │                                         │
│                                       │ stacked in                              │
│                                       ▼                                         │
│                              ┌───────────────────┐                              │
│                              │   ItemStack        │                              │
│                              │                    │                              │
│                              │ - itemId: String   │                              │
│                              │ - quantity: int    │                              │
│                              │                    │                              │
│                              │ +add(n): void      │                              │
│                              │ +remove(n): bool   │                              │
│                              │ +isEmpty(): bool   │                              │
│                              └────────┬───────────┘                              │
│                                       │                                         │
│                                       │ held in                                 │
│                                       ▼                                         │
│                              ┌───────────────────┐                              │
│                              │    Inventory       │                              │
│                              │                    │                              │
│                              │ - pockets: Map     │                              │
│                              │   <ItemCat,        │                              │
│                              │    List<ItemStack>> │                             │
│                              │                    │                              │
│                              │ +addItem(id,n):b   │                              │
│                              │ +removeItem(id,n):b│                              │
│                              │ +hasItem(id):bool  │                              │
│                              │ +getCount(id):int  │                              │
│                              │ +getPocket(cat)    │                              │
│                              │  :List<ItemStack>  │                              │
│                              └───────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                        ECS BRIDGE (Components)                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌────────────────────────┐  ┌───────────────────────┐  ┌────────────────────┐ │
│  │ PlayerPartyComponent   │  │PlayerInventoryComponent│  │PokemonStorageComp. │ │
│  │ «Component»            │  │ «Component»            │  │ «Component»        │ │
│  │ @ComponentMeta(        │  │ @ComponentMeta(        │  │ @ComponentMeta(    │ │
│  │  cat="Player")         │  │  cat="Player")         │  │  cat="Player")     │ │
│  ├────────────────────────┤  ├───────────────────────┤  ├────────────────────┤ │
│  │ -party: List           │  │ -inventory: Inventory  │  │ -boxes: List<List  │ │
│  │  <PokemonInstance>     │  │                        │  │  <PokemonInstance>>│ │
│  │  (max 6)               │  │ +addItem(id,n)         │  │ -boxNames: List   │ │
│  │                        │  │ +removeItem(id,n)      │  │  <String>          │ │
│  │ +getParty():List       │  │ +hasItem(id):bool      │  │ -boxCount: int    │ │
│  │ +addToParty(p):bool    │  │ +getInventory()        │  │ (default 8)        │ │
│  │ +removeFromParty(i)    │  │                        │  │                    │ │
│  │ +swapPositions(i,j)    │  └───────────────────────┘  │ +deposit(p, box)   │ │
│  │ +getFirstAlive():Pkm?  │                              │ +withdraw(box, i)  │ │
│  │ +isTeamAlive():bool    │                              │  :PokemonInstance  │ │
│  │ +partySize():int       │                              │ +getBox(i):List    │ │
│  └────────────────────────┘                              └────────────────────┘ │
│                                                                                 │
│  Relationship to existing player components:                                    │
│                                                                                 │
│  GameObject "Player"                                                            │
│  ├── Transform                                                                  │
│  ├── SpriteRenderer                                                             │
│  ├── GridMovement                                                               │
│  ├── PlayerInput                                                                │
│  ├── PlayerMovement                                                             │
│  ├── PlayerCameraFollow                                                         │
│  ├── InteractionController                                                      │
│  ├── PlayerDialogueManager                                                      │
│  ├── PlayerPartyComponent      ◄── NEW                                          │
│  ├── PlayerInventoryComponent  ◄── NEW                                          │
│  └── PokemonStorageComponent   ◄── NEW                                          │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Class Designs

### Pokemon System

#### `PokemonType` (enum)

```
NORMAL, FIRE, WATER, GRASS, ELECTRIC, ICE, FIGHTING, POISON,
GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY
```

Contains static type effectiveness chart:
- `getEffectiveness(PokemonType attacker, PokemonType defender) → float` (0.0, 0.5, 1.0, 2.0)

Single-type only for now (no dual typing).

#### `Stats` (record or class)

```java
int hp, atk, def, spAtk, spDef, spd
+total(): int
```

Used for: base stats on species, IVs on instances. Small, immutable-friendly.

#### `Nature` (enum)

25 natures, each with a boosted and hindered stat (+10% / -10%). 5 neutral natures (Hardy, Docile, Serious, Bashful, Quirky).

```java
+atkModifier(): float    // 0.9, 1.0, or 1.1
+defModifier(): float
+spAtkModifier(): float
+spDefModifier(): float
+spdModifier(): float
```

#### `PokemonSpecies` (data class)

Template for a Pokemon species. Loaded from JSON.

```java
- speciesId: String       // "bulbasaur", "pikachu"
- name: String            // "Bulbasaur"
- type: PokemonType       // Single type
- baseStats: Stats        // Species base stats
- learnset: List<LearnedMove>  // Moves learned by level-up
- baseExpYield: int       // Exp given when defeated
- catchRate: int          // 0-255
- growthRate: GrowthRate  // Exp curve (FAST, MEDIUM_FAST, MEDIUM_SLOW, SLOW)
- spriteId: String        // Asset path for sprite
- evolutionLevel: int     // 0 = no evolution
- evolvesInto: String     // speciesId of evolution, null if none
```

#### `Move` (data class)

Move template. Loaded from JSON.

```java
- moveId: String          // "tackle", "thunderbolt"
- name: String            // "Tackle"
- type: PokemonType       // Element type of the move
- category: MoveCategory  // PHYSICAL, SPECIAL, STATUS
- power: int              // 0 for status moves
- accuracy: int           // 0-100, 0 means always hits
- pp: int                 // Max PP
- effect: String          // Effect identifier (e.g., "PARALYZE", "HEAL_HALF", "")
- effectChance: int       // % chance to apply effect (0-100)
- priority: int           // Move priority (0 = normal)
```

#### `MoveSlot` (data class)

An individual move known by a Pokemon instance.

```java
- moveId: String          // References Move by ID
- currentPp: int          // Remaining PP
+hasPp(): boolean
+usePp(): void
+restorePp(n): void
+restoreAllPp(): void
```

#### `PokemonInstance` (data class)

A concrete Pokemon owned by a player or NPC.

```java
- species: String         // speciesId → lookup in Pokedex
- nickname: String        // null → use species name
- level: int              // 1-100
- exp: int                // Current exp points
- nature: Nature
- ivs: Stats              // 0-31 per stat, generated on creation
- currentHp: int          // Current HP (0 = fainted)
- moves: List<MoveSlot>   // 1-4 known moves
- originalTrainer: String // OT name
- caughtIn: String        // Pokeball itemId used to catch

// Computed
+getDisplayName(): String        // nickname or species name
+calcStat(StatType): int         // Full stat formula
+calcMaxHp(): int                // HP formula (different from others)
+isAlive(): boolean              // currentHp > 0
+canFight(): boolean             // isAlive && has move with PP
+heal(amount): void
+damage(amount): void
+healFull(): void
+gainExp(amount): LevelUpResult  // Returns moves learned, evolution, etc.
+getExpToNextLevel(): int
```

**Stat calculation formula** (Gen III simplified):
```
stat = ((2 * base + iv) * level / 100 + 5) * natureModifier
hp   = (2 * base + iv) * level / 100 + level + 10
```

#### `Pokedex` (registry)

Static registry loaded from JSON files at startup.

```java
- species: Map<String, PokemonSpecies>
- moves: Map<String, Move>

+getSpecies(id): PokemonSpecies
+getMove(id): Move
+getAllSpecies(): Collection<PokemonSpecies>
+load(speciesPath, movesPath): void
```

**Data files:**
- `gameData/data/pokemon/species.json` — Array of PokemonSpecies
- `gameData/data/pokemon/moves.json` — Array of Move

---

### Item System

#### `ItemCategory` (enum)

```
MEDICINE      // Potions, revives, status heals
POKEBALL      // All pokeball types
BATTLE        // X Attack, Guard Spec, etc.
TM_HM         // Technical/Hidden machines
BERRY          // Held berries
KEY_ITEM       // Bicycle, Surf, story items (not consumable, qty always 1)
HELD_ITEM      // Items a Pokemon can hold
```

#### `ItemDefinition` (data class)

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
- effect: String          // Effect identifier for the use-item system
- effectValue: int        // Numeric parameter (e.g., 20 for Potion = heal 20 HP)
```

#### `ItemStack` (data class)

```java
- itemId: String
- quantity: int

+add(n): void
+remove(n): boolean      // false if not enough
+isEmpty(): boolean
```

#### `Inventory` (data class)

Organizes items into pockets by category.

```java
- pockets: Map<ItemCategory, List<ItemStack>>

+addItem(itemId, quantity): boolean
+removeItem(itemId, quantity): boolean
+hasItem(itemId): boolean
+hasItem(itemId, minQuantity): boolean
+getCount(itemId): int
+getPocket(category): List<ItemStack>  // Unmodifiable view
+getAllItems(): List<ItemStack>
+isFull(category): boolean
```

#### `ItemRegistry` (registry)

```java
- items: Map<String, ItemDefinition>

+get(itemId): ItemDefinition
+getByCategory(cat): List<ItemDefinition>
+load(path): void
```

**Data file:**
- `gameData/data/items/items.json` — Array of ItemDefinition

---

### ECS Bridge Components

#### `PlayerPartyComponent`

```java
@ComponentMeta(category = "Player")
public class PlayerPartyComponent extends Component {
    public static final int MAX_PARTY_SIZE = 6;

    private List<PokemonInstance> party = new ArrayList<>();

    +getParty(): List<PokemonInstance>  // unmodifiable
    +addToParty(pokemon): boolean       // false if full
    +removeFromParty(index): PokemonInstance
    +swapPositions(i, j): void
    +getFirstAlive(): PokemonInstance   // null if all fainted
    +isTeamAlive(): boolean
    +partySize(): int
    +healAll(): void
}
```

#### `PlayerInventoryComponent`

```java
@ComponentMeta(category = "Player")
public class PlayerInventoryComponent extends Component {
    private Inventory inventory = new Inventory();

    +getInventory(): Inventory
    +addItem(itemId, quantity): boolean
    +removeItem(itemId, quantity): boolean
    +hasItem(itemId): boolean
    +getItemCount(itemId): int
}
```

#### `PokemonStorageComponent`

```java
@ComponentMeta(category = "Player")
public class PokemonStorageComponent extends Component {
    public static final int DEFAULT_BOX_COUNT = 8;
    public static final int BOX_CAPACITY = 30;

    private List<List<PokemonInstance>> boxes;
    private List<String> boxNames;

    +deposit(pokemon, boxIndex): boolean  // false if box full
    +withdraw(boxIndex, slotIndex): PokemonInstance
    +getBox(index): List<PokemonInstance>  // unmodifiable
    +getBoxName(index): String
    +setBoxName(index, name): void
    +getBoxCount(): int
    +findPokemon(speciesId): location?     // Search across all boxes
}
```

---

## Data Loading Pattern

Following the existing `DialogueLoader` / `Assets.load()` pattern:

```java
// At game startup (Main.java or GameApplication)
Pokedex.load("data/pokemon/species.json", "data/pokemon/moves.json");
ItemRegistry.load("data/items/items.json");

// Usage anywhere
PokemonSpecies bulbasaur = Pokedex.getSpecies("bulbasaur");
Move tackle = Pokedex.getMove("tackle");
ItemDefinition potion = ItemRegistry.get("potion");
```

Alternatively, integrate with the AssetLoader pipeline:
- `PokedexLoader` implementing `AssetLoader<Pokedex>`
- `ItemRegistryLoader` implementing `AssetLoader<ItemRegistry>`

This is a design decision — static registries are simpler, asset loaders enable hot-reload.

---

## JSON Data Format Examples

### species.json
```json
[
  {
    "speciesId": "bulbasaur",
    "name": "Bulbasaur",
    "type": "GRASS",
    "baseStats": { "hp": 45, "atk": 49, "def": 49, "spAtk": 65, "spDef": 65, "spd": 45 },
    "learnset": [
      { "level": 1, "moveId": "tackle" },
      { "level": 3, "moveId": "growl" },
      { "level": 7, "moveId": "vine_whip" }
    ],
    "baseExpYield": 64,
    "catchRate": 45,
    "growthRate": "MEDIUM_SLOW",
    "spriteId": "sprites/pokemon/bulbasaur",
    "evolutionLevel": 16,
    "evolvesInto": "ivysaur"
  }
]
```

### moves.json
```json
[
  {
    "moveId": "tackle",
    "name": "Tackle",
    "type": "NORMAL",
    "category": "PHYSICAL",
    "power": 40,
    "accuracy": 100,
    "pp": 35,
    "effect": "",
    "effectChance": 0,
    "priority": 0
  },
  {
    "moveId": "thunderbolt",
    "name": "Thunderbolt",
    "type": "ELECTRIC",
    "category": "SPECIAL",
    "power": 90,
    "accuracy": 100,
    "pp": 15,
    "effect": "PARALYZE",
    "effectChance": 10,
    "priority": 0
  }
]
```

### items.json
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
    "effectValue": 20
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
    "effectValue": 0
  }
]
```

---

## Integration with Save System

These data models are designed to be save-friendly:

- `PokemonInstance` — fully serializable (no transient engine references)
- `Inventory` — fully serializable (items stored by ID + quantity)
- `PokemonStorageComponent` — fully serializable (list of lists)

The save system would persist:
```json
{
  "party": [ /* PokemonInstance objects */ ],
  "inventory": { "pockets": { "MEDICINE": [...], "POKEBALL": [...] } },
  "pcBoxes": [ [ /* box 0 */ ], [ /* box 1 */ ] ],
  "pcBoxNames": ["Box 1", "Box 2", ...]
}
```

---

## Interaction with Future Battle System

The battle system will consume these models:

- **Pokemon selection**: `PlayerPartyComponent.getFirstAlive()` → lead battler
- **Move execution**: `MoveSlot` → lookup `Move` in `Pokedex` → damage calc using `Stats`, `PokemonType`
- **Item usage**: `Inventory.removeItem()` → apply `ItemDefinition.effect` to target `PokemonInstance`
- **Catch attempt**: `ItemDefinition` (pokeball) → `PokemonSpecies.catchRate` → capture formula
- **Exp/leveling**: `PokemonInstance.gainExp()` → check learnset → evolution check

---

## Questions for Design Improvement

### Pokemon System

1. **Dual typing**: Should `PokemonSpecies` support a secondary type from the start, or is single-type truly sufficient for now? Adding it later changes the type effectiveness lookup and the species data format.

2. **Abilities**: Classic Pokemon have abilities (Overgrow, Blaze, Torrent). Should we add a placeholder `ability: String` field now to avoid a data migration later?

3. **Status conditions**: Should `PokemonInstance` track status effects (BURN, PARALYZE, SLEEP, POISON, FREEZE) now, or defer to the battle system? Status persists outside battle in classic games.

4. **Friendship/happiness**: Classic Pokemon has a friendship value (0-255) that affects certain evolutions and the move Return. Include now?

5. **Held items**: Should `PokemonInstance` have a `heldItem: String` field? This affects both the item system and battle system.

6. **Gender**: Should Pokemon have gender? Affects breeding (if ever added) and some evolutions.

7. **Evolution method**: Current design only supports level-based evolution. Should we support item-based (Thunder Stone), trade-based, or happiness-based evolution from the start?

8. **Move learning**: When a Pokemon levels up and wants to learn a 5th move, the battle system needs to prompt "forget a move?". Is this in scope for this system, or does the battle system handle that flow?

9. **Wild Pokemon creation**: Should `Pokedex` or a `PokemonFactory` have a method like `createWild(speciesId, level)` that auto-generates IVs and picks level-appropriate moves?

### Item System

10. **Item effects**: The current design uses a `String effect` identifier. Should we define an `ItemEffect` enum instead, or keep it as string for data-driven flexibility?

11. **Bag pocket limits**: Should each pocket have a max number of unique item stacks (like 20 different medicine types)? Classic games have pocket limits.

12. **TM/HM items**: TMs teach moves to Pokemon. Should `ItemDefinition` have a `teachesMove: String` field for TM items?

13. **Registration items**: Items like Bicycle and Surf are "registered" to a button. Does the inventory need a `registeredItems` concept?

14. **Item sorting**: Should `Inventory` support sorting items within a pocket (by name, by ID, by type)?

### Integration

15. **Save system dependency**: Should this design block on the save system being implemented first, or can the data models exist independently and be wired into saves later?

16. **Dialogue integration**: Dialogue events can give items (`"effect": "GIVE_ITEM"`). Should we define `DialogueEvent` handlers for `GIVE_ITEM` and `GIVE_POKEMON` now?

17. **NPC trainers**: Will NPC trainers also use `PlayerPartyComponent`, or should there be a simpler `TrainerComponent` with just a party list and no PC?

18. **Pokemart/shop system**: Items have buy/sell prices. Should we design a shop system now, or is that a separate feature?

19. **Asset loaders vs static registries**: Should `Pokedex` and `ItemRegistry` be proper `AssetLoader` implementations (enabling hot-reload in the editor), or simple static registries loaded once at startup?

20. **Pokemon sprites**: How should Pokemon battle sprites be organized? Front/back per species? Animated? What asset structure?

---

## Implementation Phases (Preview)

| Phase | Scope |
|-------|-------|
| **1** | Core data models: `PokemonType`, `Stats`, `Nature`, `Move`, `MoveSlot`, `PokemonSpecies`, `PokemonInstance` |
| **2** | `Pokedex` registry + JSON loading + sample data files |
| **3** | Item models: `ItemCategory`, `ItemDefinition`, `ItemStack`, `Inventory`, `ItemRegistry` + JSON loading |
| **4** | ECS components: `PlayerPartyComponent`, `PlayerInventoryComponent`, `PokemonStorageComponent` |
| **5** | Factory methods: `PokemonFactory.createWild()`, `PokemonFactory.createStarter()` |
| **6** | Integration: wire into `PlayerPrefab`, dialogue events, save system hooks |
| **7** | Testing + code review |
