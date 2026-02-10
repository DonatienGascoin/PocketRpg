# EncounterTable Asset Design

## Overview

Design for a reusable asset that defines which Pokemon can be encountered in a zone, with weights and level ranges. Multiple `EncounterZone` components can reference the same `EncounterTable` asset.

---

## Motivation

Without this asset:
- Each `EncounterZone` has its own inline `List<EncounterEntry>`
- Duplicating encounter data across zones (e.g., multiple grass patches on Route 1)
- Changing Route 1 Pokemon requires editing every zone

With this asset:
- Define "Route 1 Pokemon" once as `route1.encounter.json`
- All Route 1 grass zones reference this single asset
- Change once, applies everywhere

---

## Data Structure

### `EncounterTable`

```java
public class EncounterTable {
    String id;                        // "route1", "viridian_forest"
    String displayName;               // "Route 1 Pokemon" (for editor)
    List<EncounterEntry> entries;     // the Pokemon + weights + levels
}
```

### `EncounterEntry`

```java
public class EncounterEntry {
    String pokemonId;      // reference to SpeciesRegistry ("pikachu")
    int weight;            // relative probability (higher = more common)
    int minLevel;
    int maxLevel;
}
```

---

## Storage

```
gameData/encounters/
    route1.encounter.json
    route2.encounter.json
    viridian_forest.encounter.json
```

### File Format

```json
{
  "id": "route1",
  "displayName": "Route 1 Pokemon",
  "entries": [
    { "pokemonId": "pidgey",   "weight": 40, "minLevel": 2, "maxLevel": 5 },
    { "pokemonId": "rattata",  "weight": 45, "minLevel": 2, "maxLevel": 4 },
    { "pokemonId": "pikachu",  "weight": 5,  "minLevel": 3, "maxLevel": 5 },
    { "pokemonId": "spearow",  "weight": 10, "minLevel": 3, "maxLevel": 5 }
  ]
}
```

---

## Asset Integration

### Loader

```java
public class EncounterTableLoader implements AssetLoader<EncounterTable> {

    @Override
    public String[] getSupportedExtensions() {
        return new String[] { ".encounter.json" };
    }

    @Override
    public EncounterTable load(String path) {
        // Load via Gson
    }

    @Override
    public void save(EncounterTable table, String path) {
        // Save via Gson
    }
}
```

### EncounterZone Reference

```java
public class EncounterZone extends Component {
    private List<TileRect> areas = new ArrayList<>();
    private float encounterRate = 0.1f;

    // Asset reference (path to .encounter.json)
    private String encounterTablePath;

    // Runtime: resolved from path
    private transient EncounterTable encounterTable;

    @Override
    protected void onStart() {
        if (encounterTablePath != null && !encounterTablePath.isEmpty()) {
            encounterTable = Assets.load(encounterTablePath, EncounterTable.class);
        }
    }

    public EncounterEntry rollPokemon(Random rng) {
        if (encounterTable == null || encounterTable.entries.isEmpty()) {
            return null;
        }
        // Weighted random selection from encounterTable.entries
    }
}
```

---

## Editor Panel: `EncounterTableEditorPanel`

Similar to `AnimatorEditorPanel` — manages `.encounter.json` files.

### Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│ Encounter Tables                                            [+] [-] │
├──────────────────┬──────────────────────────────────────────────────┤
│                  │                                                  │
│  route1          │  Route 1 Pokemon                                 │
│  route2          │  ─────────────────────────────────────────────── │
│  viridian_forest │                                                  │
│ >mt_moon         │  ID: mt_moon                                     │
│  rock_tunnel     │  Display Name: [Mt. Moon Pokemon          ]      │
│                  │                                                  │
│                  │  ┌─ Entries ─────────────────────────────────┐   │
│                  │  │                                           │   │
│                  │  │  Pokemon      Weight   Level Range        │   │
│                  │  │  ──────────────────────────────────────── │   │
│                  │  │  [zubat    ▼]  [60]    [7 ] - [10]  [X]   │   │
│                  │  │  [geodude  ▼]  [25]    [8 ] - [11]  [X]   │   │
│                  │  │  [paras    ▼]  [10]    [9 ] - [12]  [X]   │   │
│                  │  │  [clefairy ▼]  [5 ]    [8 ] - [12]  [X]   │   │
│                  │  │                                           │   │
│                  │  │  [+ Add Entry]                            │   │
│                  │  └───────────────────────────────────────────┘   │
│                  │                                                  │
│                  │  Total Weight: 100                               │
│                  │  Probabilities: zubat 60%, geodude 25%, ...      │
│                  │                                                  │
└──────────────────┴──────────────────────────────────────────────────┘
```

### Features

- **Left panel**: List of all `.encounter.json` files (via `Assets.scanByType()`)
- **Right panel**: Edit selected table
- **Pokemon dropdown**: Populated from `SpeciesRegistry.getAll()`
- **Weight display**: Shows calculated percentages
- **Validation**: Warn if pokemonId doesn't exist in registry
- **Undo/redo**: Snapshot-based like AnimatorEditorPanel

### Inspector Integration

When editing an `EncounterZone` in the Inspector, the `encounterTablePath` field shows an asset picker:

```
┌─────────────────────────────────────────┐
│ EncounterZone                       [-] │
├─────────────────────────────────────────┤
│ Encounter Rate  [====●====] 0.10        │
│ Encounter Table [route1.encounter ▼] [↗]│
│                                         │
│ ┌─ Areas ─────────────────────────────┐ │
│ │ ...                                 │ │
└─────────────────────────────────────────┘

[▼] = Dropdown with all .encounter.json files
[↗] = Open in EncounterTableEditorPanel
```

---

## Open Questions

1. **Fallback for missing asset?** If `encounterTablePath` points to a deleted file, should the zone:
   - Log warning and never trigger encounters?
   - Have a hardcoded fallback entry?

2. **Inline override?** Should `EncounterZone` support both asset reference AND inline entries for quick testing? Or keep it simple (asset only)?

3. **Time-of-day variants?** Some Pokemon games have different encounters at night. Support now or defer?
   - Option A: Single table, defer time-of-day
   - Option B: `EncounterTable` has `dayEntries` and `nightEntries`
   - Option C: Separate assets (`route1_day.encounter.json`, `route1_night.encounter.json`)

4. **Fishing/surfing?** Different encounter tables for different interaction types. Handle via separate assets or fields on `EncounterTable`?

---

## Dependencies

- `SpeciesRegistry` must exist for Pokemon dropdown population
- Asset system (`AssetLoader`, `Assets.scanByType()`)
- Editor panel infrastructure (see `AnimatorEditorPanel` pattern)
