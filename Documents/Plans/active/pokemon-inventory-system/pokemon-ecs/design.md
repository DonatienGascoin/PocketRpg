# Pokemon ECS Components — Plan

## Overview

ECS bridge components that attach Pokemon data to GameObjects: player party, PC storage, and NPC trainers. Player components use **write-through persistence** to `PlayerData` (from scene-data-persistence plan) — every mutation immediately flushes to `SaveManager.globalState`, so `PlayerData` is always up-to-date and `SaveManager.save()` can be called at any time. NPC `TrainerComponent` uses `ISaveable` for per-entity persistence.

## Dependencies

- **pokemon-data** — `PokemonInstance`, `PokemonFactory`, `Pokedex`
- **item-inventory** — `PlayerInventoryComponent` (for money awarded after trainer battles)
- **scene-data-persistence** — `PlayerData`

## Package Layout

```
com.pocket.rpg/
├── components/pokemon/
│   ├── PlayerPartyComponent.java       # Player's team of up to 6 Pokemon (PlayerData)
│   ├── PokemonStorageComponent.java    # PC box storage (PlayerData)
│   ├── TrainerComponent.java           # NPC trainer (ISaveable)
│   └── HealZoneComponent.java         # Pokemon Center healing (InteractableComponent)
```

---

## PlayerData Integration

The scene-data-persistence plan defines `PlayerData` as the single source of truth for cross-scene player state, stored in `SaveManager.globalState` and serialized via Gson. It already has commented placeholder fields for `team`, `inventory`, and `gold`.

This plan adds concrete fields to `PlayerData`:

```java
public class PlayerData {
    // ... existing position fields ...
    public String playerName;                           // Player's name (for OT, dialogue, etc.)
    public List<PokemonInstanceData> team;              // Party Pokemon (up to 6)
    public List<List<PokemonInstanceData>> boxes;       // PC storage (8 boxes × 30 capacity)
    public List<String> boxNames;                       // "Box 1", "Box 2", ...
}
```

`PokemonInstanceData` is the serialized form of `PokemonInstance` — Gson handles it automatically since `PokemonInstance.toSaveData()` / `fromSaveData()` already produce maps, but with Gson we can serialize the object directly without manual map conversion.

**Write-through persistence:**
- `onStart()`: Read from `PlayerData.load()` → populate in-memory cache
- Every mutation (`addToParty`, `removeFromParty`, `swapPositions`, `healAll`, etc.) immediately flushes to `PlayerData.save()` after modifying in-memory state
- Reads (`getParty`, `getFirstAlive`, `isTeamAlive`, etc.) use the in-memory cache for performance
- No `onBeforeSceneUnload()` needed — `PlayerData` is always current

**Why write-through?** `PlayerData.save()` writes to `SaveManager.globalState` in memory (not to disk) — it's cheap. This guarantees `SaveManager.save()` always persists the latest state, regardless of whether a scene transition happened. Without this, a player who picks up items and saves mid-scene would lose their changes.

---

## Detailed Class Designs

### `PlayerPartyComponent`

The player's active team of Pokemon. Max 6. Attached to the player GameObject.

```java
@ComponentMeta(category = "Player")
public class PlayerPartyComponent extends Component {
    public static final int MAX_PARTY_SIZE = 6;

    private List<PokemonInstance> party = new ArrayList<>();

    +getParty(): List<PokemonInstance>  // unmodifiable
    +addToParty(pokemon): boolean       // false if full (size >= 6)
    +removeFromParty(index): PokemonInstance
    +swapPositions(i, j): void
    +getFirstAlive(): PokemonInstance   // null if all fainted
    +isTeamAlive(): boolean             // any party member alive
    +partySize(): int
    +healAll(): void                    // Full HP + cure status + restore all move PP for all
}
```

**PlayerData integration:**

```java
@Override
protected void onStart() {
    PlayerData data = PlayerData.load();
    if (data.team != null) {
        party.clear();
        for (PokemonInstanceData pid : data.team) {
            party.add(pid.toPokemonInstance());
        }
    }
}

// Called at the end of every mutation method (addToParty, removeFromParty, swapPositions, healAll)
private void flushToPlayerData() {
    PlayerData data = PlayerData.load();
    data.team = party.stream()
        .map(PokemonInstanceData::fromPokemonInstance)
        .toList();
    data.save();
}
```

Example mutation:
```java
public boolean addToParty(PokemonInstance pokemon) {
    if (party.size() >= MAX_PARTY_SIZE) return false;
    party.add(pokemon);
    flushToPlayerData();
    return true;
}
```

### `PokemonStorageComponent`

PC box system for storing Pokemon beyond the party. Attached to the player GameObject.

```java
@ComponentMeta(category = "Player")
public class PokemonStorageComponent extends Component {
    public static final int DEFAULT_BOX_COUNT = 8;
    public static final int BOX_CAPACITY = 30;

    private List<List<PokemonInstance>> boxes;   // Initialized to DEFAULT_BOX_COUNT empty lists
    private List<String> boxNames;               // "Box 1", "Box 2", ...

    +deposit(pokemon, boxIndex): boolean           // false if box full
    +depositToFirstAvailable(pokemon): boolean    // finds first box with space, deposits
    +withdraw(boxIndex, slotIndex): PokemonInstance
    +getBox(index): List<PokemonInstance>          // unmodifiable
    +getBoxName(index): String
    +setBoxName(index, name): void
    +getBoxCount(): int
    +findPokemon(speciesId): location?             // Search across all boxes, return box+slot
    +getTotalStored(): int                         // Count of all Pokemon across all boxes
}
```

**PlayerData integration:**

```java
@Override
protected void onStart() {
    PlayerData data = PlayerData.load();
    if (data.boxes != null) {
        // Reconstruct boxes from PlayerData
        boxes = data.boxes.stream()
            .map(box -> box.stream().map(PokemonInstanceData::toPokemonInstance)
                .collect(Collectors.toCollection(ArrayList::new)))
            .collect(Collectors.toCollection(ArrayList::new));
        boxNames = data.boxNames != null ? new ArrayList<>(data.boxNames) : initDefaultBoxNames();
    } else {
        boxes = initEmptyBoxes();
        boxNames = initDefaultBoxNames();
    }
}

// Called at the end of every mutation method (deposit, withdraw, setBoxName, depositToFirstAvailable)
private void flushToPlayerData() {
    PlayerData data = PlayerData.load();
    data.boxes = boxes.stream()
        .map(box -> box.stream().map(PokemonInstanceData::fromPokemonInstance).toList())
        .toList();
    data.boxNames = new ArrayList<>(boxNames);
    data.save();
}
```

### `TrainerComponent`

NPC trainer — has a party, awards money on defeat, has pre/post battle dialogue. Uses **ISaveable** for per-entity persistence (only the `defeated` boolean needs saving).

**Party is defined as declarative specs** (not full `PokemonInstance` objects) for simpler scene authoring in the inspector. At runtime, `getParty()` lazily creates `PokemonInstance` objects from the specs via `PokemonFactory.createTrainer()`.

```java
@ComponentMeta(category = "Pokemon")
public class TrainerComponent extends Component implements ISaveable {
    private String trainerName = "";
    private List<TrainerPokemonSpec> partySpecs = new ArrayList<>();  // Declarative, authored in inspector
    private int defeatMoney = 0;                                      // Money awarded to player on defeat
    private String preDialogue = "";                                   // dialogueId spoken before battle
    private String postDialogue = "";                                  // dialogueId spoken after defeat
    private boolean defeated = false;                                  // Persisted — trainer only fights once

    private transient List<PokemonInstance> party;  // Lazily built from partySpecs

    +getTrainerName(): String
    +getParty(): List<PokemonInstance>     // lazy-creates from partySpecs via PokemonFactory
    +getFirstAlive(): PokemonInstance      // null if all fainted
    +isDefeated(): boolean
    +markDefeated(): void
    +getDefeatMoney(): int
    +getPreDialogue(): String
    +getPostDialogue(): String

    // Inner class for scene authoring
    public static class TrainerPokemonSpec {
        private String speciesId;               // "onix", "geodude"
        private int level;                      // 12, 14
        private List<String> moves;             // Optional override, null = auto from learnset
    }
}
```

**`getParty()` logic:**
```java
if (party == null) {
    party = new ArrayList<>();
    for (TrainerPokemonSpec spec : partySpecs) {
        if (spec.moves != null && !spec.moves.isEmpty()) {
            party.add(PokemonFactory.createTrainer(spec.speciesId, spec.level, spec.moves, trainerName));
        } else {
            party.add(PokemonFactory.createTrainer(spec.speciesId, spec.level, null, trainerName));
        }
    }
}
return party;
```

**ISaveable implementation** — only persists `defeated` state (party specs are authored in the scene file, not runtime-modified):

```java
@Override
public Map<String, Object> getSaveState() {
    return Map.of("defeated", defeated);
}

@Override
public void loadSaveState(Map<String, Object> state) {
    defeated = (boolean) state.getOrDefault("defeated", false);
}

@Override
public boolean hasSaveableState() { return defeated; }
```

### `HealZoneComponent`

Pokemon Center healing interaction. Extends `InteractableComponent`. When the player interacts, all party Pokemon are fully healed.

```java
@ComponentMeta(category = "Interaction")
public class HealZoneComponent extends InteractableComponent {

    public HealZoneComponent() {
        gizmoShape = GizmoShape.CROSS;
        gizmoColor = GizmoColors.fromRGBA(1.0f, 0.4f, 0.6f, 0.9f);  // Pink
    }

    @Override
    public void interact(GameObject player) {
        PlayerPartyComponent party = player.getComponent(PlayerPartyComponent.class);
        if (party != null) {
            party.healAll();
            // (Future) Show "Your Pokemon have been fully restored!" message via UI
        }
    }

    @Override
    public String getInteractionPrompt() {
        return "Heal";
    }
}
```

**Scene setup:**

```
GameObject "Pokemon Center Nurse"
├── Transform
├── SpriteRenderer
├── TriggerZone                    ◄── auto-added by InteractableComponent
└── HealZoneComponent
```

---

## Player Prefab Wiring

The player entity is a **prefab instance** in every scene (as required by scene-data-persistence plan). New components are added to the player prefab:

```
GameObject "Player"
├── PlayerStateTracker          ◄── from scene-data-persistence plan
├── Transform
├── SpriteRenderer
├── GridMovement
├── PlayerMovement
├── PlayerCameraFollow
├── InteractionController
├── PlayerDialogueManager
├── PlayerPartyComponent        ◄── NEW (write-through to PlayerData)
├── PlayerInventoryComponent    ◄── NEW (from item-inventory plan, write-through to PlayerData)
└── PokemonStorageComponent     ◄── NEW (write-through to PlayerData)
```

### NPC Trainer Prefab

```
GameObject "NPC Trainer"
├── PersistentEntity("trainer_brock")   ◄── required for ISaveable
├── Transform
├── SpriteRenderer
├── DialogueInteractable
└── TrainerComponent                ◄── NEW (ISaveable — only persists defeated)
    ├── trainerName: "Brock"
    ├── partySpecs:
    │   ├── { speciesId: "geodude", level: 12 }
    │   └── { speciesId: "onix", level: 14 }
    ├── defeatMoney: 1200
    ├── preDialogue: "brock_challenge"
    ├── postDialogue: "brock_defeated"
    └── defeated: false
```

---

## PlayerData Save Structure

Player state is stored in `SaveManager.globalState["player"]["data"]` as a Gson-serialized JSON string:

```json
{
  "playerName": "Red",
  "lastOverworldScene": "route_1",
  "lastGridX": 12,
  "lastGridY": 8,
  "lastDirection": "DOWN",
  "returningFromBattle": false,
  "team": [
    {
      "species": "pikachu",
      "nickname": "Sparky",
      "level": 25,
      "exp": 12500,
      "nature": "JOLLY",
      "ivs": { "hp": 28, "atk": 31, "def": 15, "spAtk": 20, "spDef": 22, "spd": 30 },
      "currentHp": 58,
      "statusCondition": "NONE",
      "heldItem": null,
      "moves": [
        { "moveId": "thunderbolt", "maxPp": 15, "currentPp": 12 },
        { "moveId": "quick_attack", "maxPp": 30, "currentPp": 30 }
      ],
      "originalTrainer": "Red",
      "caughtIn": "pokeball"
    }
  ],
  "boxes": [[], [], [], [], [], [], [], []],
  "boxNames": ["Box 1", "Box 2", "Box 3", "Box 4", "Box 5", "Box 6", "Box 7", "Box 8"]
}
```

Trainer defeated state stays in `sceneStates.modifiedEntities` (ISaveable):

```json
{
  "trainer_brock": {
    "componentStates": {
      "TrainerComponent": { "defeated": true }
    }
  }
}
```

---

## Integration with Battle System (Future)

These components are the entry points for the battle system:

- **Player battle**: `PlayerPartyComponent.getFirstAlive()` → lead Pokemon
- **Trainer battle**: `TrainerComponent.getParty()` → opponent team
- **Post-battle**: `TrainerComponent.markDefeated()`, `PlayerInventoryComponent.addMoney(defeatMoney)`
- **Pokemon Center**: `PlayerPartyComponent.healAll()` — restores HP, cures status, restores PP for all party members
- **PC access**: `PokemonStorageComponent.deposit()` / `withdraw()` / `depositToFirstAvailable()`
- **Catch with full party**: `PokemonStorageComponent.depositToFirstAvailable(pokemon)` when party is full
- **Battle scene**: `BattleManager.onStart()` reads `PlayerData.load().team` directly (no player entity in battle scene)

## Player Name

The player's name is stored as `PlayerData.playerName`. Components that need it (e.g., `PokemonFactory.createStarter()`) read it from `PlayerData.load().playerName`. The name is set during game start (new game screen) via `PlayerData.save()`.

---

## Implementation Phases

| Phase | Scope |
|-------|-------|
| **1** | Add `team`, `boxes`, `boxNames`, `playerName` fields to `PlayerData` |
| **2** | `PlayerPartyComponent` with write-through PlayerData integration |
| **3** | `PokemonStorageComponent` with write-through PlayerData integration |
| **4** | `TrainerComponent` (ISaveable) with declarative party specs |
| **5** | `HealZoneComponent` (extends InteractableComponent) |
| **6** | Wire into player prefab (add components to existing player GameObject in scene) |
| **7** | Unit tests for party limits, box overflow, PlayerData round-trips, trainer defeated state, heal zone |

## Acceptance Criteria

- [ ] `PlayerPartyComponent` enforces max party size of 6
- [ ] `PokemonStorageComponent` supports 8 boxes × 30 capacity with named boxes
- [ ] All player component mutations write-through to `PlayerData` immediately — `PlayerData.load()` reflects latest state at all times
- [ ] `SaveManager.save()` mid-scene produces correct save file (no stale data)
- [ ] `TrainerComponent` lazily creates party from declarative specs via `PokemonFactory`
- [ ] `TrainerComponent.defeated` persisted via ISaveable across scene loads
- [ ] `HealZoneComponent` fully heals all party Pokemon (HP + status + PP) on interact
- [ ] Components initialize correctly from empty `PlayerData` (new game scenario)
- [ ] `PlayerData.playerName` available for OT stamping and dialogue

## Testing Plan

### Unit Tests

**PlayerPartyComponent:**
- `addToParty()` — accepts up to 6 Pokemon, returns false for 7th
- `addToParty()` write-through — `PlayerData.load().team` reflects addition immediately
- `removeFromParty()` — returns removed Pokemon, party size decrements
- `removeFromParty()` write-through — `PlayerData.load().team` reflects removal immediately
- `swapPositions()` — party order changes correctly (verify indices)
- `getFirstAlive()` — returns first non-fainted, null if all fainted
- `isTeamAlive()` — true if any alive, false if all fainted
- `healAll()` — all party Pokemon: currentHp = maxHp, statusCondition = NONE, all move PP = maxPp
- `healAll()` write-through — healed state reflected in PlayerData
- `partySize()` — correct count after add/remove
- `onStart()` from populated PlayerData — party correctly reconstructed
- `onStart()` from empty PlayerData — empty party, no crash

**PokemonStorageComponent:**
- `deposit()` — adds Pokemon to specified box, returns false if box full (30)
- `deposit()` write-through — `PlayerData.load().boxes` reflects deposit immediately
- `depositToFirstAvailable()` — finds first box with space across all 8 boxes
- `depositToFirstAvailable()` — returns false when all boxes full (8 × 30 = 240)
- `withdraw()` — returns Pokemon from specified box+slot, slot becomes empty
- `withdraw()` write-through — `PlayerData.load().boxes` reflects withdrawal immediately
- `getBox()` — returns unmodifiable view
- `setBoxName()` — name updated, persisted via write-through
- `getBoxCount()` — returns 8 (DEFAULT_BOX_COUNT)
- `getTotalStored()` — correct count across all boxes
- `findPokemon()` — locates by speciesId, returns box+slot; null if not found
- `onStart()` from populated PlayerData — boxes and names correctly reconstructed
- `onStart()` from empty PlayerData — 8 empty boxes with default names

**PlayerData round-trip:**
- Populate party + storage → new component `onStart()` from same PlayerData → equivalent state

**TrainerComponent:**
- `getParty()` — lazily creates PokemonInstance list from partySpecs
- `getParty()` — second call returns same cached list (no re-creation)
- `getParty()` with explicit moves — moves match spec
- `getParty()` with null moves — auto-selected from learnset
- `getFirstAlive()` — returns first non-fainted trainer Pokemon
- `markDefeated()` → `isDefeated()` returns true
- ISaveable `getSaveState()` — returns `{ "defeated": true/false }`
- ISaveable `loadSaveState()` — restores defeated flag
- `hasSaveableState()` — returns true only when defeated

**HealZoneComponent:**
- `interact()` with player that has `PlayerPartyComponent` — all Pokemon healed
- `interact()` with player that lacks `PlayerPartyComponent` — no crash, no-op
- `getInteractionPrompt()` — returns "Heal"

### Manual Tests

- Add all 3 player components to player prefab, run scene — verify components initialize without errors
- Add Pokemon to party via debug, save game mid-scene, reload — verify party intact
- Add Pokemon to party, transition to new scene, verify party persists
- Fill party to 6, attempt to add 7th — verify rejection
- Deposit/withdraw Pokemon from PC storage, save, reload — verify storage intact
- Place HealZone NPC in scene, damage party Pokemon, interact — verify full heal (HP + status + PP)
- Place Trainer NPC with partySpecs, interact — verify party created correctly
- Defeat trainer, leave scene, return — verify trainer stays defeated
- New game (empty PlayerData) — verify all components initialize with clean defaults
