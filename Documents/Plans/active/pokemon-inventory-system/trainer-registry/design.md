# Trainer Registry — Design

## Problem

Currently, trainers are defined **inline on each TrainerComponent** in the scene. A designer who wants to see all trainers at a glance, compare their teams, or rebalance difficulty across the game has to click through every NPC in every scene. There's no central place to view, search, or bulk-edit trainer data.

The Pokedex editor and Item Registry editor already solve this for species/moves and items respectively — a Trainer Registry would complete the set.

## Goals

- **Central catalog** of all trainer definitions, browsable and searchable in one editor panel
- **Same pattern** as Pokedex / Item Registry: asset file + loader + editor content
- **Scene TrainerComponent references a trainer by ID** instead of embedding party data inline
- **TrainerComponentInspector simplified** to a single dropdown (pick trainer ID), with a "Jump to Registry" button

## Non-Goals

- AI / battle strategy configuration (future work)
- Trainer progression / rematches (future work)
- Route-based grouping (can be done with tags later)

## Dialogue Integration

Add `TRAINER_NAME` as a **STATIC** variable in `variables.dialogue-vars.json`. When the TrainerComponent triggers pre/post dialogue, it passes `Map.of("TRAINER_NAME", def.getTrainerName())` as static vars. This lets a single dialogue asset (e.g. "I am [TRAINER_NAME], prepare for battle!") be reused across many trainers — each trainer's name gets substituted at runtime.

---

## Data Model

### `TrainerDefinition.java`

```
com.pocket.rpg.pokemon.TrainerDefinition

@Getter @Setter
public class TrainerDefinition {
    private String trainerId;                    // unique key, e.g. "rival_1", "brock"
    private String trainerName;                  // display name, e.g. "Brock"
    private String trainerClass;                 // e.g. "Gym Leader", "Bug Catcher", "Rival"
    private String spriteId;                     // trainer portrait/sprite path (nullable)
    private List<TrainerPokemonSpec> party;       // reuse existing TrainerPokemonSpec
    private int defeatMoney;                     // reward money on defeat
    private Dialogue preDialogue;                // dialogue asset before battle
    private Dialogue postDialogue;               // dialogue asset after battle
}
```

`TrainerPokemonSpec` is the existing inner class from `TrainerComponent` — it gets **moved** to `com.pocket.rpg.pokemon.TrainerPokemonSpec` as a top-level class so both `TrainerDefinition` and `TrainerComponent` can reference it.

### `TrainerRegistry.java`

```
com.pocket.rpg.pokemon.TrainerRegistry

public class TrainerRegistry {
    private Map<String, TrainerDefinition> trainers = new LinkedHashMap<>();

    +getTrainer(String id): TrainerDefinition
    +addTrainer(TrainerDefinition def): void
    +removeTrainer(String id): void
    +getAllTrainers(): Collection<TrainerDefinition>
    +copyFrom(TrainerRegistry other): void       // hot-reload support
}
```

### File format: `.trainers.json`

```json
{
  "trainers": [
    {
      "trainerId": "rival_1",
      "trainerName": "Blue",
      "trainerClass": "Rival",
      "spriteId": null,
      "party": [
        { "speciesId": "charmander", "level": 5, "moves": null }
      ],
      "defeatMoney": 150,
      "preDialogue": null,
      "postDialogue": null
    },
    {
      "trainerId": "brock",
      "trainerName": "Brock",
      "trainerClass": "Gym Leader",
      "party": [
        { "speciesId": "geodude", "level": 12, "moves": ["tackle", "defense_curl"] },
        { "speciesId": "onix", "level": 14, "moves": ["tackle", "harden", "bind"] }
      ],
      "defeatMoney": 480
    }
  ]
}
```

---

## Asset Loader

### `TrainerRegistryLoader.java`

```
com.pocket.rpg.resources.loaders.TrainerRegistryLoader extends JsonAssetLoader<TrainerRegistry>

- extensions(): ".trainers.json"
- fromJson(): deserialize trainers array → Map by trainerId
- toJson(): serialize Map values → trainers array
- copyInto(): existing.copyFrom(fresh) for hot-reload
```

Follows the exact same pattern as `PokedexLoader` and `ItemRegistryLoader`.

---

## Editor Content

### `TrainerRegistryEditorContent.java`

```
com.pocket.rpg.editor.panels.content.TrainerRegistryEditorContent
@EditorContentFor(TrainerRegistry.class) implements AssetEditorContent
```

#### Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  [Search...........................] [+ Add] [- Delete]             │
│  Class: [▾ All Classes        ]                                    │
├──────────────────────┬──────────────────────────────────────────────┤
│                      │                                              │
│  Trainer List        │  ▼ Identity                                  │
│  ─────────────       │  ┌──────────────────────────────────────┐    │
│  ● rival_1           │  │ ID:      [rival_1              ]     │    │
│    Blue (Rival)      │  │ Name:    [Blue                 ]     │    │
│                      │  │ Class:   [Rival                ]     │    │
│  ○ brock             │  │ Sprite:  [Select...]                 │    │
│    Brock (Gym Ldr)   │  └──────────────────────────────────────┘    │
│                      │                                              │
│  ○ bug_catcher_1     │  ▼ Battle Settings                           │
│    Rick (Bug Catcher)│  ┌──────────────────────────────────────┐    │
│                      │  │ Defeat $: [150    ]                  │    │
│  ○ lass_1            │  │ Pre:     [▾ Select Dialogue...]      │    │
│    Amy (Lass)        │  │ Post:    [▾ Select Dialogue...]      │    │
│                      │  └──────────────────────────────────────┘    │
│                      │                                              │
│                      │  ▼ Party (1/6)                               │
│                      │  ┌──────────────────────────────────────┐    │
│                      │  │ ▼ charmander Lv.5              [x]   │    │
│                      │  │   Species: [▾ charmander       ]     │    │
│                      │  │   Level:   [===5===============]     │    │
│                      │  │   Moves:   Auto (from learnset)      │    │
│                      │  │                                      │    │
│                      │  │ [+ Add Pokemon]                      │    │
│                      │  └──────────────────────────────────────┘    │
│                      │                                              │
└──────────────────────┴──────────────────────────────────────────────┘
```

#### Left Panel — Trainer List

- **Search bar**: fuzzy match on trainerId, trainerName, trainerClass
- **Class filter combo**: "All Classes" + unique `trainerClass` values from the registry (dynamic, not a fixed enum)
- **Trainer entries**: two-line display — `trainerId` on top (bold/selectable), `trainerName (trainerClass)` below in grey
- **Add button**: creates new `TrainerDefinition` with auto-generated ID `"trainer_N"`
- **Delete button**: removes selected trainer (with undo)

#### Right Panel — Trainer Detail

Three collapsible sections:

1. **Identity**: trainerId (static label, not editable), trainerName, trainerClass, sprite picker
2. **Battle Settings**: defeatMoney (int drag), preDialogue (asset picker), postDialogue (asset picker)
3. **Party**: reuses the same UI as `TrainerComponentInspector` — species dropdown, level drag, move buttons with `MoveBrowserPopup`. Bordered child, tree nodes per Pokemon, max 6

#### Undo

Snapshot-based undo (same as Pokedex/ItemRegistry):

```java
private record TrainerRegistrySnapshot(String json) {
    static TrainerRegistrySnapshot capture(TrainerRegistry reg) { ... }
    void restore(TrainerRegistry target) { ... }
}
```

#### Custom Save

```java
@Override
public boolean hasCustomSave() { return true; }

@Override
public void customSave(String path) {
    TrainerRegistryLoader loader = new TrainerRegistryLoader();
    loader.save(editingRegistry, Assets.getAssetRoot() + "/" + path);
    Assets.reload(path);
    shell.showStatus("Saved: " + path);
}
```

---

## TrainerComponent Migration

### Before (current)

```java
public class TrainerComponent extends Component {
    private String trainerName;
    private List<TrainerPokemonSpec> partySpecs;  // inline data
    private int defeatMoney;
    private Dialogue preDialogue;
    private Dialogue postDialogue;
}
```

### After

```java
public class TrainerComponent extends Component {
    private String trainerId;   // references TrainerRegistry

    // Runtime — resolved lazily from registry
    public TrainerDefinition getDefinition() {
        TrainerRegistry registry = Assets.load(REGISTRY_PATH, TrainerRegistry.class);
        return registry != null ? registry.getTrainer(trainerId) : null;
    }

    public List<PokemonInstance> getParty() {
        TrainerDefinition def = getDefinition();
        // create instances from def.getParty() specs
    }

    public String getTrainerName() {
        TrainerDefinition def = getDefinition();
        return def != null ? def.getTrainerName() : "";
    }
}
```

The component becomes a lightweight **reference** — all data lives in the registry.

### TrainerComponentInspector (simplified)

```
┌─ TrainerComponent ──────────────────────────────┐
│                                                  │
│  Trainer:  [▾ brock - Brock (Gym Leader)    ]   │
│                                                  │
│  [Open in Trainer Registry]                      │
│                                                  │
│  ── Preview ──────────────────────────────       │
│  Name:    Brock                                  │
│  Class:   Gym Leader                             │
│  Money:   $480                                   │
│  Party:   geodude Lv.12, onix Lv.14             │
│                                                  │
└──────────────────────────────────────────────────┘
```

- **Dropdown** lists all trainer IDs from the registry (with name + class)
- **"Open in Trainer Registry"** button navigates to the registry editor and selects this trainer
- **Read-only preview** shows key info from the resolved definition
- No inline editing — all editing happens in the registry

---

## Scene File Migration

### Before

```json
{
  "type": "TrainerComponent",
  "trainerName": "Brock",
  "defeatMoney": 480,
  "partySpecs": [
    { "speciesId": "onix", "level": 14, "moves": ["tackle"] }
  ]
}
```

### After

```json
{
  "type": "TrainerComponent",
  "trainerId": "brock"
}
```

Old scene files with inline data will need a one-time migration (or the component can fall back to reading inline fields if `trainerId` is null, for backwards compatibility during the transition).

---

## Package Layout

```
com.pocket.rpg/
├── pokemon/
│   ├── TrainerDefinition.java          # NEW — trainer data model
│   ├── TrainerRegistry.java            # NEW — registry (Map<String, TrainerDefinition>)
│   └── TrainerPokemonSpec.java         # MOVED from TrainerComponent inner class
│
├── resources/loaders/
│   └── TrainerRegistryLoader.java      # NEW — .trainers.json loader
│
├── components/pokemon/
│   └── TrainerComponent.java           # MODIFIED — trainerId reference instead of inline
│
├── editor/panels/content/
│   └── TrainerRegistryEditorContent.java  # NEW — @EditorContentFor(TrainerRegistry.class)
│
├── editor/ui/inspectors/
│   └── TrainerComponentInspector.java  # MODIFIED — simplified to dropdown + preview
```

---

## Implementation Phases

| Phase | Description |
|-------|-------------|
| 1 | `TrainerPokemonSpec` → top-level class, `TrainerDefinition`, `TrainerRegistry` |
| 2 | `TrainerRegistryLoader` + `.trainers.json` file creation |
| 3 | `TrainerRegistryEditorContent` — left panel (list, search, class filter, add/delete) |
| 4 | `TrainerRegistryEditorContent` — right panel (identity, battle settings, party editor) |
| 5 | `TrainerComponent` migration — `trainerId` reference + `getDefinition()` |
| 6 | `TrainerComponentInspector` simplification — dropdown + preview + "Open in Registry" |
| 7 | Scene file migration helper (or backwards-compat fallback) |

---

## Open Questions

1. **Registry path**: single `data/pokemon/trainers.trainers.json` next to `pokedex.pokedex.json`? Or allow multiple files (e.g. per-route)?
2. **Backwards compatibility**: support inline fallback during transition, or require migration?
3. **"Open in Registry" navigation**: what's the API to programmatically open an asset editor and select an entry? (Pokedex editor may already support this pattern)
