# Random Encounters & Pokemon Data Design

## Overview

Design for two interconnected systems:
1. **Random Encounter System** - triggers battles when the player walks on TALL_GRASS tiles, with per-zone Pokemon configuration
2. **Pokemon Data Storage** - minimal data format for defining Pokemon species (encounter-focused)

---

## Part 1: Random Encounter System

### Existing Infrastructure (what we build on)

| System | File | Role |
|--------|------|------|
| `TALL_GRASS` collision type | `CollisionType.java:75` | Already marks encounter tiles |
| `TallGrassBehavior` | `TallGrassBehavior.java` | Returns `MovementModifier.ENCOUNTER` |
| `GridMovement.finishMovement()` | `GridMovement.java:481` | Fires tile events after each step |
| `PersistentEntity` | `PersistentEntity.java` | Snapshots/restores player across scene transitions |
| `SceneTransition` | `SceneTransition.java` | Static API for scene transitions with fades |
| `SaveManager.setGlobal/getGlobal` | `SaveManager.java` | Global state that survives scene transitions |
| `TriggerZone` / `WarpZone` pattern | `WarpZone.java` | Entity-based interaction model |

### Architecture

```
Player steps on TALL_GRASS tile
        |
        v
GridMovement.finishMovement()
  - currentModifier == ENCOUNTER
        |
        v
EncounterSystem.rollEncounter(gridX, gridY, zLevel, scene)
  1. Find which EncounterZone entity covers this tile
  2. Roll random chance (per-zone encounter rate)
  3. If triggered: pick a Pokemon from the zone's table (weighted)
        |
        v
EncounterSystem stores encounter context in SaveManager global state:
  - "encounter/returnScene" = current scene name
  - "encounter/returnX", "encounter/returnY", "encounter/returnZ" = player position
  - "encounter/returnDirection" = player facing direction
  - "encounter/pokemonId" = selected pokemon species ID
  - "encounter/pokemonLevel" = rolled level (from min/max range)
        |
        v
SceneTransition.loadSceneWithoutPersistentEntity("battle")
  - Player avatar destroyed with overworld, NOT carried to battle
  - Battle scene has its own entities (no overworld player)
        |
        v
Battle scene loads, reads encounter data from SaveManager globals
Battle accesses player team/inventory via PlayerData (SaveManager-backed)
        |
        v
After battle: SceneTransition.loadSceneWithoutPersistentEntity(returnScene)
  - Overworld loads from file with default Player avatar
  - EncounterSystem teleports player using saved position/direction globals
  - Clear encounter globals
```

### Key Components

#### 1. `EncounterZone` Component

An entity-based component placed in the scene editor. Supports **multiple rectangular areas** to cover complex shapes. Areas use **absolute tile coordinates** (transform-independent).

```java
@ComponentMeta(category = "Interaction")
public class EncounterZone extends Component implements GizmoDrawable {
    // Multiple areas to support complex shapes (L-shapes, etc.)
    private List<TileRect> areas = new ArrayList<>();

    // Encounter rate (0.0 - 1.0, probability per step on TALL_GRASS)
    private float encounterRate = 0.1f;    // 10% per step

    // TODO: Replace with asset reference once EncounterTable asset is designed
    private List<EncounterEntry> encounters = new ArrayList<>();

    // --- Methods ---
    boolean containsTile(int x, int y, int z) {
        for (TileRect rect : areas) {
            if (rect.contains(x, y, z)) return true;
        }
        return false;
    }
    EncounterEntry rollPokemon(Random rng);      // weighted random pick
}

public class TileRect {
    int minX, minY, maxX, maxY;  // absolute tile coordinates
    int zLevel;

    boolean contains(int x, int y, int z) {
        return z == zLevel && x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
```

**`EncounterEntry`** (nested or standalone class):
```java
public class EncounterEntry {
    String pokemonId;      // reference to PokemonSpecies
    int weight;            // relative probability (higher = more common)
    int minLevel;
    int maxLevel;
}
```

**Design decisions:**
- **Transform-independent**: Areas use absolute world coordinates. Entity position is irrelevant — the entity is just a container for the zone data.
- **Overlap allowed**: Multiple areas can overlap. A tile is "in the zone" if ANY area contains it.

##### Editor Inspector

```
┌─────────────────────────────────────────┐
│ EncounterZone                       [-] │
├─────────────────────────────────────────┤
│ Encounter Rate  [====●====] 0.10        │
│ Encounter Table [... TODO ...]          │
│                                         │
│ ┌─ Areas ─────────────────────────────┐ │
│ │  ┌─ Area 0 ───────────────────────┐ │ │
│ │  │ Z-Level [0 ]                   │ │ │
│ │  │ Min (2, 3)  Max (8, 7)         │ │ │
│ │  │ [Edit Handles]  [Delete]       │ │ │
│ │  └────────────────────────────────┘ │ │
│ │                                     │ │
│ │  ┌─ Area 1 ───────────────────────┐ │ │
│ │  │ Z-Level [0 ]                   │ │ │
│ │  │ Min (10, 3)  Max (14, 10)      │ │ │
│ │  │ [Edit Handles]  [Delete]       │ │ │
│ │  └────────────────────────────────┘ │ │
│ │                                     │ │
│ │  [+ Add Area]                       │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

##### Editor Gizmo — Visual Style

Distinct from `CameraBoundsZone` for easy differentiation:

| Aspect | CameraBoundsZone | EncounterZone |
|--------|------------------|---------------|
| Color | Red | Green (grass theme) |
| Line style | Dashed | Solid |
| Fill | None | Semi-transparent fill |
| Center button | None | [✎] edit button |

```java
// CameraBoundsZone (existing)
private static final int ZONE_COLOR = GizmoColors.fromRGBA(1.0f, 0.15f, 0.15f, 1.0f);  // Red

// EncounterZone (new)
private static final int ZONE_OUTLINE = GizmoColors.fromRGBA(0.2f, 0.8f, 0.3f, 1.0f);  // Green
private static final int ZONE_FILL = GizmoColors.fromRGBA(0.2f, 0.8f, 0.3f, 0.15f);    // Green 15% opacity
```

**Scene view example (both zones visible):**
```
     0   1   2   3   4   5   6   7   8   9  10  11  12
   ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
10 ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ← Red dashed
   ├ ─ ┼ ─ ┼ ─ ┼ ─ ┼───┼───┼───┼───┼───┼ ─ ┼ ─ ┼ ─ ┼ ─ ┤  (CameraBoundsZone)
 9 ┊   ┊   ┊   ┊   │███│███│███│███│███│   ┊   ┊   ┊   ┊
   ├ ─ ┼ ─ ┼ ─ ┼ ─ ┼───┼───┼───┼───┼───┼ ─ ┼ ─ ┼ ─ ┼ ─ ┤
 8 ┊   ┊   ┊   ┊   │███│███│[✎]│███│███│   ┊   ┊   ┊   ┊  ← Green solid + fill
   ├ ─ ┼ ─ ┼ ─ ┼ ─ ┼───┼───┼───┼───┼───┼ ─ ┼ ─ ┼ ─ ┼ ─ ┤    (EncounterZone)
 7 ┊   ┊   ┊   ┊   │███│███│███│███│███│   ┊   ┊   ┊   ┊
   ├ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┼ ─ ┤
 6 ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊   ┊
   └ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┴ ─ ┘

┊ ─ ┊ = Red dashed line (CameraBoundsZone)
│███│ = Green solid line + semi-transparent green fill (EncounterZone)
[✎]  = Click to edit this area
```

##### Editor Tool — `EncounterZoneTool`

Extends `EditorTool` interface with key handling:
```java
default boolean onKeyPressed(int keyCode) { return false; }
```

**State machine:**
```
Normal Mode
    │
    ▼ Click "+ Add Area" in inspector
Placement Mode
    │├──► Mouse move → ghost 3x3 rectangle follows cursor (dashed green)
    │├──► Click → create area at cursor, switch to Edit Mode
    │└──► Escape → cancel, return to Normal Mode
    │
    ▼ Click [✎] button on area OR "Edit Handles" in inspector
Edit Mode (area N)
    │├──► Drag handles → resize (8 handles: corners + edge midpoints)
    │├──► Click [✎] on different area → switch to that area
    │├──► Escape → deselect, return to Normal Mode
    │└──► Delete key → delete area, return to Normal Mode
```

**Placement mode visual:**
```
     5   6   7   8   9  10  11  12
   ┌───┬───┬───┬───┬───┬───┬───┬───┐
 7 │   │   │┈┈┈│┈┈┈│┈┈┈│   │   │   │
   ├───┼───┼┈┈┈┼┈┈┈┼┈┈┈┼───┼───┼───┤
 6 │   │   │┈┈┈│ ✛ │┈┈┈│   │   │   │  ← Mouse at (8,6)
   ├───┼───┼┈┈┈┼┈┈┈┼┈┈┈┼───┼───┼───┤
 5 │   │   │┈┈┈│┈┈┈│┈┈┈│   │   │   │
   └───┴───┴───┴───┴───┴───┴───┴───┘

┈┈┈ = Ghost 3x3 dashed rectangle
 ✛  = Cursor position

Inspector hint: "Click to place area. Escape to cancel."
```

**Edit mode visual:**
```
     10  11  12  13  14
   ┌───┬───┬───┬───┬───┐
 8 │[■]──[■]──[■]│   │
   ├─│─┼───┼─│─┼───┼───┤
 7 │ │███│███│ │ │   │
   ├[■]───────[■]┼───┼───┤
 6 │ │███│███│ │ │   │
   ├─│─┼───┼─│─┼───┼───┤
 5 │[■]──[■]──[■]│   │
   └───┴───┴───┴───┴───┘

[■] = Drag handles (8 total)
```

#### 2. `EncounterSystem` (Static API)

Follows the `SceneTransition` static API pattern. Singleton initialized at game startup.

```java
public class EncounterSystem {
    static void initialize(SceneManager sceneManager);

    // Called by GridMovement after stepping on ENCOUNTER tile
    static void checkEncounter(Scene scene, int tileX, int tileY, int zLevel);

    // Query: find which EncounterZone covers a tile
    static EncounterZone findZone(Scene scene, int tileX, int tileY, int zLevel);
}
```

**Integration point**: In `GridMovement.finishMovement()`, after the existing tile events, add:
```java
// Check for random encounter (tall grass)
if (finishedModifier == MovementModifier.ENCOUNTER) {
    EncounterSystem.checkEncounter(gameObject.getScene(), gridX, gridY, zLevel);
}
```

This is the only change needed in `GridMovement`.

#### 3. Battle Scene Transition Flow

**Going to battle:**
1. `EncounterSystem.checkEncounter()` rolls dice, picks Pokemon
2. Stores context in `SaveManager` global state:
   - `encounter/returnScene` - scene name to return to
   - `encounter/returnX`, `returnY`, `returnZ` - player grid position
   - `encounter/returnDirection` - player facing direction
   - `encounter/pokemonId` - the selected Pokemon species ID
   - `encounter/pokemonLevel` - the rolled level
3. Calls `SceneTransition.loadSceneWithoutPersistentEntity("battle")`
4. Player avatar is destroyed with the overworld scene — NOT carried to battle

**Returning from battle:**
1. Battle scene reads `SaveManager.getGlobal("encounter", "returnScene")`
2. Calls `SceneTransition.loadSceneWithoutPersistentEntity(returnScene)`
3. Overworld loads from file with default Player avatar at editor position
4. `EncounterSystem` listens to scene loaded event, reads position globals, teleports player
5. Clear encounter globals: `SaveManager.removeGlobal("encounter", ...)`

**Remembering the selected Pokemon:**
- Stored in `SaveManager.setGlobal("encounter", "pokemonId", id)` before transitioning
- Battle scene reads it with `SaveManager.getGlobal("encounter", "pokemonId", "")`
- Cleared after battle ends

#### 4. Player Data Architecture (prerequisite)

Battle requires access to player's Pokemon team and inventory, but the overworld Player entity is not carried over (isolated transition).

**Solution:** RPG state lives in SaveManager, accessed via `PlayerData` helper class.

```java
// Battle scene can access team without Player entity
List<OwnedPokemon> team = PlayerData.getTeam();

// Using items in battle
if (PlayerData.useItem("potion")) { ... }
```

| State type | Location | Why |
|------------|----------|-----|
| Avatar (position, visuals, movement) | Player entity | Scene-specific, not needed in battle |
| RPG (team, inventory, money) | PlayerData / SaveManager | Global, needed everywhere |

This pattern applies beyond encounters: shops, menus, Pokemon Centers, etc. all use `PlayerData.*` without needing to find the Player entity.

**Note:** `PlayerData` and `OwnedPokemon` classes are not part of this plan — they are a prerequisite to be designed separately.

#### 5. New SceneTransition API

Add to `SceneTransition`:

```java
/**
 * Loads a scene without PersistentEntity snapshot/restore.
 * Use for transitions where entities should NOT carry over (battle, cutscenes, minigames).
 */
public static void loadSceneWithoutPersistentEntity(String sceneName);
public static void loadSceneWithoutPersistentEntity(String sceneName, TransitionConfig config);
```

Implementation: One flag on `SceneManager` (`skipPersistence`), checked in `loadSceneInternal()`, auto-resets after the transition completes.

#### 6. PersistentEntity Bug Fix (separate from encounters)

**Issue:** `PersistentEntitySnapshot.applySnapshot()` skips Transform, but `createScratchEntity()` restores it. Behavior depends on whether target scene has a matching entity.

**Fix:** In `applySnapshot()`, replace Transform skip with setter-based copy:

```java
// Before (line 124-125):
if (snapshotComp instanceof Transform) continue;

// After:
if (snapshotComp instanceof Transform snapshotTransform) {
    Transform targetTransform = target.getTransform();
    targetTransform.setLocalPosition(snapshotTransform.getLocalPosition());
    targetTransform.setLocalRotation(snapshotTransform.getLocalRotation());
    targetTransform.setLocalScale(snapshotTransform.getLocalScale());
    continue;
}
```

Now Transform is always restored from snapshot. Spawn point teleport happens after and overrides if provided. This fix is independent of the encounter system but was discovered during this design.

---

## Part 2: Pokemon Data Storage

### Recommended Format: JSON Files

**Why JSON:**
- Consistent with the entire project (Gson-based serialization everywhere)
- ConfigLoader, MusicConfig, prefabs, scene files all use JSON
- Easy to load with existing `Serializer` / Gson infrastructure
- Easy to edit by hand or with editor tools

**Why NOT Excel/CSV:**
- No existing CSV/Excel parsing in the project
- Would require a new dependency or build step
- JSON is already the lingua franca of this engine
- If mass-editing is desired later, a CSV-to-JSON converter script can be added

### Data Structure: `PokemonSpecies`

Minimal encounter-focused data (battle stats deferred to later):

```java
public class PokemonSpecies {
    String id;              // "pikachu", "rattata" - unique key
    String name;            // "Pikachu" - display name

    // Encounter visuals
    String spritePath;      // path to sprite asset (overworld appearance)
    String battleSpritePath; // path to battle sprite (front view)

    // Base info for encounter display
    String type1;           // "Electric" (primary type)
    String type2;           // null or "" if single-type
}
```

### Storage Location

```
gameData/config/pokemon/
    species.json            -- master species list
```

**`species.json` format:**
```json
{
  "species": [
    {
      "id": "pikachu",
      "name": "Pikachu",
      "type1": "Electric",
      "type2": "",
      "spritePath": "assets/sprites/pokemon/pikachu.png",
      "battleSpritePath": "assets/sprites/pokemon/battle/pikachu.png"
    },
    {
      "id": "rattata",
      "name": "Rattata",
      "type1": "Normal",
      "type2": "",
      "spritePath": "assets/sprites/pokemon/rattata.png",
      "battleSpritePath": "assets/sprites/pokemon/battle/rattata.png"
    }
  ]
}
```

### Registry: `SpeciesRegistry`

A runtime registry that loads the JSON and provides lookup by ID.

```java
public class SpeciesRegistry {
    private static SpeciesRegistry instance;
    private Map<String, PokemonSpecies> speciesById;

    static void load();                              // load from species.json
    static PokemonSpecies get(String id);            // lookup by ID
    static List<PokemonSpecies> getAll();            // all species
}
```

Follows the same singleton/static-API pattern as `SceneTransition`, `SaveManager`, `UIManager`.

**Note:** "Pokedex" is reserved for the player's seen/caught tracking (future feature).

### EncounterZone References Pokemon by ID

The `EncounterEntry` in `EncounterZone` references Pokemon by string ID:
```json
{
  "encounters": [
    { "pokemonId": "pikachu", "weight": 5,  "minLevel": 3, "maxLevel": 6 },
    { "pokemonId": "rattata", "weight": 20, "minLevel": 2, "maxLevel": 5 },
    { "pokemonId": "pidgey",  "weight": 15, "minLevel": 2, "maxLevel": 5 }
  ]
}
```

Weight-based selection: `totalWeight = sum(weights)`, roll `[0, totalWeight)`, iterate to find which entry the roll lands in.

---

## Summary: Data Flow

```
species.json  -->  SpeciesRegistry (loaded at startup)
                      |
EncounterZone  -->  references pokemonId strings
(entity in scene)     |
                      v
Player steps on   EncounterSystem.checkEncounter()
TALL_GRASS   -->    1. Find zone for this tile
                    2. Roll encounter rate
                    3. Pick pokemon (weighted)
                    4. Store in SaveManager globals (pokemon + return position)
                    5. SceneTransition.loadSceneWithoutPersistentEntity("battle")
                          |
                          v
                    Battle scene loads (no overworld player)
                    - Reads encounter globals for wild pokemon
                    - Reads PlayerData for team/inventory
                          |
                          v
                    After battle:
                    - loadSceneWithoutPersistentEntity(returnScene)
                    - EncounterSystem teleports player to saved position
                    - Clear encounter globals
```


---

## Feedback & Resolutions

### Resolved

| Feedback | Resolution |
|----------|------------|
| Player input in battle scene | No longer an issue — player avatar not carried to battle (isolated transition) |
| PersistentEntity reliability | Bypassed for battle transitions via `loadSceneWithoutPersistentEntity()` |
| zLevel transient concern | `zLevel` is NOT transient in current code — it survives snapshots. Return position stored explicitly in globals anyway. |
| PokedexRegistry naming | Renamed to `SpeciesRegistry` to avoid confusion with player's seen/caught tracking |
| Encounter zone shapes | Multi-rectangle approach with `EncounterZoneTool`. Areas are transform-independent (absolute coords). Visual: green solid + fill (vs red dashed for CameraBoundsZone). |

### Still Open (See Separate Design Files)

| Feedback | Design File |
|----------|-------------|
| EncounterEntry as reusable asset | [encounter-table-asset.md](encounter-table-asset.md) |
| Pokemon as asset vs registry | [pokemon-data.md](pokemon-data.md) |
| pokemonId dropdown vs asset picker | [pokemon-id-selection.md](pokemon-id-selection.md) |

### Design Challenges (Needs Resolution)

See [design-challenges.md](design-challenges.md) for detailed analysis and proposed solutions.

| # | Challenge | Severity | Question |
|---|-----------|----------|----------|
| 1 | Static API necessity | Low | Does `EncounterSystem` need to be a singleton, or just a stateless utility? |
| 2 | No zone covering tile | Medium | Player on TALL_GRASS but no `EncounterZone` — silent failure or warning? |
| 3 | Rate on zone vs tile type | Medium | Can't have different encounter rates per tile type within the same zone |
| 4 | Overlapping zones | High | Two different `EncounterZone` entities overlap — which zone's table is used? |
| 5 | Grid vs Transform teleport | High | When returning from battle, do we set Transform or grid position? Flow unclear |
| 6 | Scene loaded timing | High | Race condition — does `GridMovement.onStart()` run before or after teleport? |
| 7 | Single battle scene | Medium | No support for different battle backgrounds (route vs cave vs water) |
| 8 | Return trigger undefined | Medium | Who/what triggers the return from battle? Out of scope or gap? |
| 9 | Repel / encounter modifiers | Low | Where does Repel, Cleanse Tag, abilities logic live? |
| 10 | Non-grass encounters | Medium | Cave floors, surfing, fishing, headbutt — same system or different? |
| 11 | End-to-end test scenario | High | No validated full cycle (overworld → battle → overworld). Need automated test. |
| 12 | Trainer battles & BattleParameters | High | Trainer battles need same system. Use `BattleParameters` record instead of separate globals. |
