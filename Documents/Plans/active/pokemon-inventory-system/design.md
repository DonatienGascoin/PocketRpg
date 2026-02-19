# Pokemon, Inventory & Item System — Overview

## Summary

The player needs core game-data systems to support a classic Pokemon-style RPG. This was originally a single monolithic design that has been split into **6 self-contained plans**, each implementable independently (respecting dependency order).

## Plan Index

| Plan | Directory | Description | Dependencies |
|------|-----------|-------------|--------------|
| **Core Persistence** | `core-persistence/` | `PlayerData` class, `onBeforeSceneUnload` + `onPostSceneInitialize` lifecycle hooks, persistence patterns | None (foundational) |
| **Pokemon Data** | `pokemon-data/` | Species, moves, stats, types, Pokedex registry, PokemonFactory, PokedexLoader | None (foundational) |
| **Item & Inventory** | `item-inventory/` | Item definitions, inventory with pockets, ItemRegistry, ItemRegistryLoader, PlayerInventoryComponent | core-persistence, pokemon-data |
| **Pokemon ECS** | `pokemon-ecs/` | PlayerPartyComponent, PokemonStorageComponent (PlayerData), TrainerComponent (ISaveable) | core-persistence, pokemon-data, item-inventory |
| **Shop System** | `shop-system/` | ShopInventory, ShopRegistry, ShopComponent, buy/sell logic | item-inventory |
| **Dialogue Rewards** | `dialogue-rewards/` | GIVE_ITEM + GIVE_POKEMON reactions on DialogueEventListener | core-persistence, pokemon-data, item-inventory, pokemon-ecs |

## Dependency Graph

```
core-persistence (foundational) ──┐
                                  ├──► item-inventory ──┬──► pokemon-ecs ──► dialogue-rewards
pokemon-data (foundational) ──────┘        │            └──► shop-system
                                           │
                              scene-data-persistence (parallel plan, also depends on core-persistence)
```

## Implementation Order

Recommended order respecting dependencies:

0. **core-persistence** — 3 phases, `PlayerData` + lifecycle hooks (must be first)
1. **pokemon-data** — 7 phases, ~14 classes (can parallel with core-persistence)
2. **scene-data-persistence** — 8 phases, parallel plan that also depends on core-persistence
3. **item-inventory** — 9 phases, ~8 classes (needs core-persistence + pokemon-data)
4. **pokemon-ecs** — 7 phases, ~3 classes (can parallel with shop-system)
5. **shop-system** — 7 phases, ~4 classes (can parallel with pokemon-ecs)
6. **dialogue-rewards** — 5 phases, 0 new classes (modifies existing DialogueReaction + DialogueEventListener)

## Architecture Approach

All plans follow the same pattern (matching existing PocketRpg architecture):

| Layer | Purpose | Examples |
|-------|---------|---------|
| **Data models** (`pokemon/`, `items/`, `shop/`) | Pure game data, JSON-serializable, no ECS dependency | `PokemonSpecies`, `ItemDefinition`, `ShopInventory` |
| **Components** (`components/pokemon/`, `components/dialogue/`) | ECS bridge — attach data to GameObjects. Player components use write-through to `PlayerData`; NPC components use `ISaveable` | `PlayerPartyComponent`, `TrainerComponent` |
| **Registries** (via AssetLoader) | Index definitions by ID, hot-reloadable in editor | `Pokedex`, `ItemRegistry`, `ShopRegistry` |
| **Asset Loaders** (`resources/loaders/`) | `AssetLoader<T>` implementations, auto-discovered via reflection | `PokedexLoader`, `ItemRegistryLoader` |

## Full Package Layout

```
com.pocket.rpg/
├── pokemon/                              # Pokemon data models (pokemon-data plan)
│   ├── PokemonType.java
│   ├── StatType.java
│   ├── Stats.java
│   ├── Nature.java
│   ├── StatusCondition.java
│   ├── EvolutionMethod.java
│   ├── GrowthRate.java
│   ├── MoveCategory.java
│   ├── LearnMoveResult.java
│   ├── LevelUpResult.java
│   ├── Move.java
│   ├── MoveSlot.java
│   ├── LearnedMove.java
│   ├── PokemonSpecies.java
│   ├── PokemonInstance.java
│   ├── PokemonFactory.java
│   └── Pokedex.java
│
├── items/                                # Item data models (item-inventory plan)
│   ├── ItemCategory.java
│   ├── ItemEffect.java
│   ├── ItemDefinition.java
│   ├── ItemStack.java
│   ├── SortMode.java
│   ├── Inventory.java
│   └── ItemRegistry.java
│
├── shop/                                 # Shop system (shop-system plan)
│   ├── ShopInventory.java
│   └── ShopRegistry.java
│
├── components/pokemon/                   # ECS bridge (pokemon-ecs + shop-system plans)
│   ├── PlayerPartyComponent.java
│   ├── PlayerInventoryComponent.java
│   ├── PokemonStorageComponent.java
│   ├── TrainerComponent.java
│   └── ShopComponent.java
│
├── components/dialogue/                  # Dialogue extensions (dialogue-rewards plan)
│   ├── DialogueReaction.java            # MODIFIED — add GIVE_ITEM, GIVE_POKEMON
│   └── DialogueEventListener.java       # MODIFIED — add reward fields + switch cases
│
├── resources/loaders/                    # Asset loaders (various plans)
│   ├── PokedexLoader.java
│   ├── ItemRegistryLoader.java
│   └── ShopRegistryLoader.java
```

## Design Decisions (Resolved)

### Pokemon System

| # | Question | Decision |
|---|----------|----------|
| 1 | Dual typing | **No** — single type only |
| 2 | Abilities | **No** — not included |
| 3 | Status conditions | **Yes** — `StatusCondition` enum on `PokemonInstance` |
| 4 | Friendship/happiness | **No** |
| 5 | Held items | **Yes** — `heldItem: String` field |
| 6 | Gender | **No** |
| 7 | Evolution method | **Level + Item** — `EvolutionMethod` enum |
| 8 | Move learning | **Data capability here, UI elsewhere** |
| 9 | Wild Pokemon creation | **Yes** — `PokemonFactory` |

### Item System

| # | Question | Decision |
|---|----------|----------|
| 10 | Item effects | **Enum** — `ItemEffect` |
| 11 | Bag pocket limits | **Yes** — `POCKET_CAPACITY = 50` |
| 12 | TM/HM teachesMove | **Yes** — `teachesMove: String` (moveId reference) |
| 13 | Registration items | **Yes** — `Inventory.registeredItems` |
| 14 | Item sorting | **Yes** — `SortMode` enum |

### Integration

| # | Question | Decision |
|---|----------|----------|
| 15 | Save system | **Integrated** — player components use write-through to `PlayerData`; NPC `TrainerComponent` uses `ISaveable` |
| 16 | Dialogue integration | **Yes** — `GIVE_ITEM` + `GIVE_POKEMON` reactions added to existing `DialogueReaction` enum + `DialogueEventListener` |
| 17 | NPC trainers | **Dedicated `TrainerComponent`** |
| 18 | Shop system | **Yes** — full shop system with `ShopComponent` |
| 19 | Asset loaders | **AssetLoader** — hot-reload in editor |
| 20 | Pokemon sprites | **Three variants**: `icon.png`, `front.png`, `back.png` per species |
