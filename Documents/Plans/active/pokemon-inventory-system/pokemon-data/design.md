# Pokemon Data Models — Plan

## Overview

Core data models for the Pokemon system: species definitions, individual Pokemon instances, moves, stats, types, and the Pokedex registry. These are **pure data classes** with no ECS dependency — they live in `com.pocket.rpg.pokemon/` and are consumed by ECS components and the battle system.

## Dependencies

- **None** — this is the foundational layer. All other plans depend on this one.

## Package Layout

```
com.pocket.rpg/
├── pokemon/
│   ├── PokemonType.java        # Element enum (18 types + effectiveness chart)
│   ├── StatType.java           # Stat name enum (HP, ATK, DEF, SP_ATK, SP_DEF, SPD)
│   ├── Stats.java              # HP/Atk/Def/SpAtk/SpDef/Spd
│   ├── Nature.java             # 25 nature enum (stat modifiers)
│   ├── StatusCondition.java    # NONE, BURN, PARALYZE, SLEEP, POISON, FREEZE
│   ├── EvolutionMethod.java    # NONE, LEVEL, ITEM
│   ├── GrowthRate.java         # FAST, MEDIUM_FAST, MEDIUM_SLOW, SLOW (with exp formulas)
│   ├── MoveCategory.java       # PHYSICAL, SPECIAL, STATUS
│   ├── LearnMoveResult.java    # OK, FULL
│   ├── LevelUpResult.java      # Record: leveledUp, newMoves, canEvolve, etc.
│   ├── Move.java               # Move template (Tackle, Thunderbolt...)
│   ├── MoveSlot.java           # Move instance with current/max PP
│   ├── LearnedMove.java        # Level → moveId mapping for learnsets
│   ├── PokemonSpecies.java     # Species template (Bulbasaur, Pikachu...)
│   ├── PokemonInstance.java    # Individual Pokemon with level, IVs, moves, HP
│   ├── PokemonFactory.java     # Factory: createWild, createStarter, createTrainer
│   └── Pokedex.java            # Registry: speciesId → PokemonSpecies, moveId → Move
│
├── resources/loaders/
│   └── PokedexLoader.java      # AssetLoader<Pokedex> (hot-reload, editor panel)
```

---

## Detailed Class Designs

### `PokemonType` (enum)

```
NORMAL, FIRE, WATER, GRASS, ELECTRIC, ICE, FIGHTING, POISON,
GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY
```

Contains static type effectiveness chart:
- `getEffectiveness(PokemonType attacker, PokemonType defender) → float` (0.0, 0.5, 1.0, 2.0)

Single-type only (no dual typing).

### `Stats` (record or class)

```java
int hp, atk, def, spAtk, spDef, spd
+total(): int
```

Used for: base stats on species, IVs on instances. Small, immutable-friendly.

### `Nature` (enum)

25 natures, each with a boosted and hindered stat (+10% / -10%). 5 neutral natures (Hardy, Docile, Serious, Bashful, Quirky).

```java
+atkModifier(): float    // 0.9, 1.0, or 1.1
+defModifier(): float
+spAtkModifier(): float
+spDefModifier(): float
+spdModifier(): float
```

### `StatusCondition` (enum)

Persistent status effects that survive outside of battle (classic Pokemon behavior).

```
NONE
BURN         // Halves Attack, deals 1/16 HP per turn
PARALYZE     // 25% chance to skip turn, halves Speed
SLEEP        // Cannot act for 1-3 turns
POISON       // Deals 1/8 HP per turn
FREEZE       // Cannot act until thawed
```

A Pokemon can have at most **one** status condition at a time.

### `EvolutionMethod` (enum)

```
NONE          // Does not evolve
LEVEL         // Evolves at a specific level
ITEM          // Evolves when an evolution item is used (e.g., Thunder Stone)
```

### `StatType` (enum)

```
HP, ATK, DEF, SP_ATK, SP_DEF, SPD
```

Used by `Nature` modifiers and stat calculation to reference individual stats programmatically.

### `GrowthRate` (enum)

```
FAST
MEDIUM_FAST
MEDIUM_SLOW
SLOW
```

Determines the experience curve — how much total exp is needed to reach each level.

**Exp-to-level formulas** (total exp required to reach level `n`):

```
FAST:        4/5 * n^3
MEDIUM_FAST: n^3
MEDIUM_SLOW: 6/5 * n^3 - 15 * n^2 + 100 * n - 140
SLOW:        5/4 * n^3
```

Each value provides:
```java
+expForLevel(level): int     // Total exp needed to reach this level
```

### `MoveCategory` (enum)

```
PHYSICAL      // Uses Attack vs Defense
SPECIAL       // Uses Sp.Atk vs Sp.Def
STATUS        // No damage, applies effects
```

### `Move` (data class)

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

### `LearnMoveResult` (enum)

Result of attempting to teach a Pokemon a new move.

```
OK          // Move learned successfully (had fewer than 4 moves)
FULL        // Move slots full (already has 4 moves) — caller must prompt replace/cancel
```

### `LevelUpResult` (record)

Returned by `PokemonInstance.gainExp()` to communicate what happened.

```java
- leveledUp: boolean               // Whether at least one level was gained
- oldLevel: int
- newLevel: int
- newMoves: List<String>           // moveIds learned at the new level(s) from learnset
- canEvolve: boolean               // Whether evolution conditions are met
- evolvesInto: String              // speciesId of evolution target, null if none
```

### `MoveSlot` (data class)

An individual move known by a Pokemon instance.

```java
- moveId: String          // References Move by ID
- maxPp: int              // Max PP for this move (copied from Move.pp at creation time)
- currentPp: int          // Remaining PP
+hasPp(): boolean
+usePp(): void
+restorePp(n): void
+restoreAllPp(): void     // currentPp = maxPp

// Serialization
+toSaveData(): Map<String, Object>
+fromSaveData(Map<String, Object>): MoveSlot  // static
```

### `LearnedMove` (data class)

Entry in a species learnset — maps a level to a move.

```java
- level: int              // Level at which this move is learned
- moveId: String          // References Move by ID
```

### `PokemonSpecies` (data class)

Template for a Pokemon species. Loaded from JSON.

```java
- speciesId: String                 // "bulbasaur", "pikachu"
- name: String                      // "Bulbasaur"
- type: PokemonType                 // Single type (no dual typing)
- baseStats: Stats                  // Species base stats
- learnset: List<LearnedMove>       // Moves learned by level-up
- baseExpYield: int                 // Exp given when defeated
- catchRate: int                    // 0-255
- growthRate: GrowthRate            // Exp curve (FAST, MEDIUM_FAST, MEDIUM_SLOW, SLOW)
- spriteId: String                  // Asset base path for sprites
- evolutionMethod: EvolutionMethod  // NONE, LEVEL, ITEM
- evolutionLevel: int               // Required level (if LEVEL method), 0 = N/A
- evolutionItem: String             // itemId required (if ITEM method), null = N/A
- evolvesInto: String               // speciesId of evolution, null if none
```

### `PokemonInstance` (data class)

A concrete Pokemon owned by a player or NPC.

```java
- species: String              // speciesId → lookup in Pokedex
- nickname: String             // null → use species name
- level: int                   // 1-100
- exp: int                     // Current exp points
- nature: Nature
- ivs: Stats                   // 0-31 per stat, generated on creation
- currentHp: int               // Current HP (0 = fainted)
- moves: List<MoveSlot>        // 1-4 known moves
- originalTrainer: String      // OT name
- caughtIn: String             // Pokeball itemId used to catch
- statusCondition: StatusCondition  // Persistent status (NONE by default)
- heldItem: String             // itemId of held item, null if none

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

// Status
+getStatusCondition(): StatusCondition
+setStatusCondition(StatusCondition): void
+cureStatus(): void

// Held item
+getHeldItem(): String           // null if none
+setHeldItem(itemId): void
+removeHeldItem(): String        // Returns removed itemId

// Move management (data capability — UI prompt handled by battle/UI system)
+learnMove(MoveSlot): LearnMoveResult  // OK if <4 moves, FULL if 4 already
+replaceMove(index, MoveSlot): void    // Replace move at slot index
+getMoveCount(): int

// Evolution
+evolve(Pokedex): PokemonInstance      // Creates evolved form (see below)

// Serialization (for save system)
+toSaveData(): Map<String, Object>
+fromSaveData(Map<String, Object>): PokemonInstance  // static
```

**Stat calculation formula** (Gen III simplified):
```
stat = ((2 * base + iv) * level / 100 + 5) * natureModifier
hp   = (2 * base + iv) * level / 100 + level + 10
```

**`evolve()` logic:**
1. Look up current species in Pokedex → get `evolvesInto` speciesId
2. Look up target species in Pokedex
3. Create new PokemonInstance with:
   - `species` = target speciesId
   - Preserve: nickname, level, exp, nature, ivs, moves, originalTrainer, caughtIn, heldItem
   - Recalculate maxHp from new base stats, adjust currentHp proportionally
   - Clear statusCondition to NONE
4. Return the evolved instance (caller replaces the old one in party/storage)

**Save data format** (`toSaveData()`):
```java
public Map<String, Object> toSaveData() {
    Map<String, Object> data = new HashMap<>();
    data.put("species", species);
    data.put("nickname", nickname);
    data.put("level", level);
    data.put("exp", exp);
    data.put("nature", nature.name());
    data.put("ivs", Map.of("hp", ivs.hp(), "atk", ivs.atk(), "def", ivs.def(),
                            "spAtk", ivs.spAtk(), "spDef", ivs.spDef(), "spd", ivs.spd()));
    data.put("currentHp", currentHp);
    data.put("statusCondition", statusCondition.name());
    data.put("heldItem", heldItem);
    data.put("moves", moves.stream().map(MoveSlot::toSaveData).toList());
    data.put("originalTrainer", originalTrainer);
    data.put("caughtIn", caughtIn);
    return data;
}

public static PokemonInstance fromSaveData(Map<String, Object> data) {
    // Reconstruct from map using SerializationUtils for type conversion
}
```

### `Pokedex` (registry)

Registry loaded via `AssetLoader<Pokedex>` pipeline. Holds all species and move definitions.

```java
- species: Map<String, PokemonSpecies>
- moves: Map<String, Move>

+getSpecies(id): PokemonSpecies
+getMove(id): Move
+getAllSpecies(): Collection<PokemonSpecies>
+getAllMoves(): Collection<Move>

// For hot-reload support (AssetLoader contract)
+copyFrom(other): void    // Mutate in place — required by reload()
```

### `PokemonFactory` (utility)

Factory for creating Pokemon instances with proper initialization.

The `trainerName` parameter is the OT (Original Trainer) name stamped on the Pokemon. Callers provide it from the appropriate source:
- **Player Pokemon**: `PlayerData.load().playerName` (set during new game)
- **NPC trainer Pokemon**: `TrainerComponent.trainerName` (authored in inspector)
- **Wild Pokemon**: hardcoded `"Wild"`

```java
+createWild(speciesId, level): PokemonInstance
    // - Looks up species in Pokedex
    // - Generates random IVs (0-31 per stat)
    // - Picks random Nature
    // - Selects level-appropriate moves from learnset (last 4 learned at or below level)
    // - Sets currentHp to calcMaxHp()
    // - OT = "Wild", caughtIn = null
    // - statusCondition = NONE, heldItem = null

+createStarter(speciesId, level, trainerName): PokemonInstance
    // - Same as createWild but OT = trainerName
    // - caughtIn = "pokeball"
    // - Caller passes PlayerData.load().playerName as trainerName

+createTrainer(speciesId, level, moves, trainerName): PokemonInstance
    // - Specific moves list (for NPC trainer Pokemon)
    // - OT = trainerName (from TrainerComponent.trainerName)
    // - Random IVs, random Nature
```

---

## AssetLoader Integration

### `PokedexLoader` implements `AssetLoader<Pokedex>`

```java
- getSupportedExtensions(): [".pokedex.json"]
- supportsHotReload(): true
- load(path): Parses species + moves from JSON → Pokedex
- reload(existing, path): Mutates existing Pokedex in place (MUST return same reference)
- save(pokedex, path): Serializes back to JSON (editor editing)
- getPlaceholder(): Empty Pokedex
- getIconCodepoint(): MaterialIcons.CatchingPokemon (or similar)
- getEditorPanelType(): POKEDEX_EDITOR  // NOTE: Add POKEDEX_EDITOR to EditorPanelType enum during implementation
```

Auto-discovered via reflection (placed in `com.pocket.rpg.resources.loaders/`). No manual registration needed.

### Data File Location

```
gameData/data/pokemon/pokedex.pokedex.json
```

### Usage

```java
// Load via AssetLoader pipeline (auto-cached, hot-reloadable)
Pokedex pokedex = Assets.load("data/pokemon/pokedex.pokedex.json", Pokedex.class);

// Query
PokemonSpecies bulbasaur = pokedex.getSpecies("bulbasaur");
Move tackle = pokedex.getMove("tackle");

// Hot-reload in editor (automatic via AssetBrowserPanel.refresh())
Assets.reload("data/pokemon/pokedex.pokedex.json");
```

---

## JSON Data Format

### pokedex.pokedex.json

```json
{
  "species": [
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
      "evolutionMethod": "LEVEL",
      "evolutionLevel": 16,
      "evolutionItem": null,
      "evolvesInto": "ivysaur"
    }
  ],
  "moves": [
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
}
```

---

## Sprite Organization

Pokemon sprites are organized by species with three variants. No animation for now.

```
gameData/assets/sprites/pokemon/
├── bulbasaur/
│   ├── icon.png           # Small icon (party menu, PC box, Pokedex list)
│   ├── front.png          # Battle sprite — opponent's view
│   └── back.png           # Battle sprite — player's view
├── pikachu/
│   ├── icon.png
│   ├── front.png
│   └── back.png
└── ...
```

`PokemonSpecies.spriteId` stores the base path (e.g., `"sprites/pokemon/pikachu"`). Consumers resolve the variant:

```java
String basePath = species.getSpriteId();
Sprite icon  = Assets.load(basePath + "/icon.png", Sprite.class);
Sprite front = Assets.load(basePath + "/front.png", Sprite.class);
Sprite back  = Assets.load(basePath + "/back.png", Sprite.class);
```

---

## Implementation Phases

| Phase | Scope |
|-------|-------|
| **1** | Enums: `PokemonType` (with effectiveness chart), `StatType`, `MoveCategory`, `StatusCondition`, `EvolutionMethod`, `GrowthRate` (with exp formulas), `Nature` (25 values with modifiers), `LearnMoveResult` |
| **2** | Data classes: `Stats`, `Move`, `MoveSlot` (with maxPp), `LearnedMove`, `LevelUpResult` |
| **3** | `PokemonSpecies`, `PokemonInstance` (with stat calc, evolve(), toSaveData/fromSaveData) |
| **4** | `Pokedex` registry class |
| **5** | `PokedexLoader` (AssetLoader) + sample `pokedex.pokedex.json` |
| **6** | `PokemonFactory`: `createWild()`, `createStarter()`, `createTrainer()` |
| **7** | Unit tests for stat calculation, move learning, factory, serialization |

## Acceptance Criteria

- [ ] All 18 `PokemonType` values defined with complete effectiveness chart (super-effective, not-effective, immune)
- [ ] 25 `Nature` values with correct +10%/-10% stat modifiers (5 neutral)
- [ ] `GrowthRate` exp formulas produce correct totals for each level
- [ ] Stat calculation matches Gen III simplified formula for both HP and other stats
- [ ] `PokemonInstance` can learn moves (up to 4), gain exp, level up, and evolve
- [ ] `PokemonFactory` creates valid Pokemon for all three contexts (wild, starter, trainer)
- [ ] `Pokedex` loads species + moves from JSON and provides lookup by ID
- [ ] `PokedexLoader` integrates with asset pipeline (load, hot-reload, save, editor icon)
- [ ] Serialization round-trip (`toSaveData()` → `fromSaveData()`) preserves all `PokemonInstance` fields

## Testing Plan

### Unit Tests

**Enums & formulas:**
- `PokemonType.getEffectiveness()` — spot-check: Fire→Grass = 2.0, Water→Fire = 2.0, Normal→Ghost = 0.0, Electric→Electric = 0.5
- `Nature` modifier values — verify a boosting nature returns 1.1/0.9, neutral returns 1.0 for all stats
- `GrowthRate.expForLevel()` — verify level 1 = 0, level 100 produces expected totals for each curve
- `StatusCondition` — 6 values, ordinal stability (used by HEAL_STATUS encoding)

**Data classes:**
- `Stats.total()` — sum of all 6 values
- `MoveSlot` — `usePp()` decrements, `hasPp()` returns false at 0, `restoreAllPp()` resets to maxPp
- `MoveSlot` serialization round-trip

**PokemonInstance:**
- `calcStat()` / `calcMaxHp()` — verify against hand-calculated Gen III values (known species + level + IVs + nature)
- `learnMove()` — returns OK when < 4 moves, FULL when already 4
- `replaceMove()` — move at target index replaced
- `gainExp()` — single level-up returns correct `LevelUpResult` (newMoves, canEvolve)
- `gainExp()` — multi-level-up (enough exp to gain 2+ levels at once)
- `evolve()` — species changes, nickname/IVs/nature/OT/moves preserved, HP proportionally adjusted, status cleared
- `heal()` / `damage()` / `healFull()` — HP clamped to 0..maxHp
- `setStatusCondition()` / `cureStatus()` — one status at a time
- `toSaveData()` → `fromSaveData()` — all fields preserved (species, nickname, level, exp, nature, IVs, currentHp, moves with PP, OT, caughtIn, status, heldItem)

**PokemonFactory:**
- `createWild()` — IVs in 0..31 range, moves are last ≤4 from learnset at or below level, currentHp = maxHp, OT = "Wild", caughtIn = null
- `createStarter()` — OT = provided trainerName, caughtIn = "pokeball"
- `createTrainer()` with explicit moves — uses provided move list, OT = provided trainerName
- `createTrainer()` with null moves — falls back to level-appropriate learnset

**Pokedex & PokedexLoader:**
- `Pokedex.getSpecies()` — returns correct species for known ID, null for unknown
- `Pokedex.getMove()` — returns correct move for known ID
- `PokedexLoader.load()` — parses sample JSON, species and moves accessible
- `PokedexLoader.reload()` — mutates existing Pokedex in place (same reference), updated data reflected

### Manual Tests

- Load `pokedex.pokedex.json` in editor asset browser — verify it appears with correct icon
- Double-click to open editor panel (once POKEDEX_EDITOR exists) — verify species/moves display
- Edit JSON externally, trigger hot-reload — verify changes reflected without editor restart
- Create a Pokemon via `PokemonFactory.createWild()` in a debug/test scene — verify stats and moves are reasonable
