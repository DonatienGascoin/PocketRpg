# Plan: Pokedex Editor Panel

## Context

The Pokemon Data plan created all data models (`PokemonSpecies`, `Move`, `Pokedex`, etc.) and a `PokedexLoader` that loads `.pokedex.json` files via the asset pipeline. However, there is no editor panel to visually edit Pokedex data — currently the only way to modify species and moves is by hand-editing JSON. This plan adds a `PokedexEditorPanel` following the same two-column pattern used by `DialogueEditorPanel` and `AnimatorEditorPanel`.

## Dependencies

- **pokemon-data** (completed) — `Pokedex`, `PokemonSpecies`, `Move`, `PokedexLoader`

## Overview

A single `PokedexEditorPanel` with a tab switch at the top to toggle between **Species** and **Moves** views. Each view has:
- **Left column**: Searchable list of items with add/delete buttons
- **Right column**: Editor for the selected item's fields

Double-clicking a `.pokedex.json` file in the Asset Browser opens the panel and selects that Pokedex.

---

## Data Model Changes

`PokemonSpecies` and `Move` currently have only getters (Lombok `@Getter`). The editor needs to mutate fields in place. Add `@Setter` to both classes.

`Stats` is a record (immutable). The editor will create new `Stats` instances when editing base stats — no change needed to the record itself.

`Pokedex` needs a `removeSpecies(String id)` and `removeMove(String id)` method for delete operations.

---

## Phases

### Phase 1: Data Model Prep
- [ ] Add `@Setter` to `PokemonSpecies`
- [ ] Add `@Setter` to `Move`
- [ ] Add `removeSpecies(String)` and `removeMove(String)` to `Pokedex`
- [ ] Add `POKEDEX_EDITOR("Pokedex Editor")` to `EditorPanelType`
- [ ] Update `PokedexLoader.getEditorPanelType()` to return `EditorPanelType.POKEDEX_EDITOR`

### Phase 2: Panel Skeleton
- [ ] Create `PokedexEditorPanel` extending `EditorPanel`
  - Constructor with `super("pokedexEditor", false)`
  - `initPanel()` for config persistence
  - `render()` with two-column layout
  - Tab bar at top of left column: "Species" / "Moves"
  - Public `selectPokedexByPath(String path)` for Asset Browser double-click
  - Load Pokedex asset list via `Assets.scanByType(Pokedex.class)`
- [ ] Wire into `EditorUIController`:
  - Add `@Getter private PokedexEditorPanel pokedexEditorPanel` field
  - Create in `createPanels()` with `initPanel(config)`
  - Set status callback
  - Register panel handler: `assetBrowserPanel.registerPanelHandler(EditorPanelType.POKEDEX_EDITOR, pokedexEditorPanel::selectPokedexByPath)`
  - Add `pokedexEditorPanel.render()` in `renderPanels()`

### Phase 3: Species Editor
- [ ] Left column: searchable species list with add/delete buttons
  - Filter by name/ID
  - Highlight selected species
  - "New Species" button creates with default values and unique ID
  - "Delete" button with confirmation
- [ ] Right column: species field editors
  - `speciesId` — text input (read-only after creation or editable with rename logic)
  - `name` — text input
  - `type` — combo dropdown (18 PokemonType values)
  - `baseStats` — 6 int inputs (HP/ATK/DEF/SP_ATK/SP_DEF/SPD) with total display
  - `baseExpYield` — int input
  - `catchRate` — int input (0-255)
  - `growthRate` — combo dropdown (4 GrowthRate values)
  - `spriteId` — text input (sprite path)
  - `evolutionMethod` — combo dropdown (3 EvolutionMethod values)
  - `evolutionLevel` — int input (shown only when method = LEVEL)
  - `evolutionItem` — text input (shown only when method = ITEM)
  - `evolvesInto` — combo dropdown from species list (shown when method != NONE)
- [ ] Learnset sub-editor:
  - Table of level → moveId entries
  - Add/remove entries
  - Move combo dropdown references loaded moves
  - Sorted by level

### Phase 4: Move Editor
- [ ] Left column: searchable move list with add/delete buttons
- [ ] Right column: move field editors
  - `moveId` — text input
  - `name` — text input
  - `type` — combo dropdown (18 PokemonType values)
  - `category` — combo dropdown (3 MoveCategory values)
  - `power` — int input
  - `accuracy` — int input (0-100)
  - `pp` — int input
  - `effect` — text input
  - `effectChance` — int input (0-100)
  - `priority` — int input

### Phase 5: Undo/Redo & Save
- [ ] Snapshot-based undo/redo (captures full Pokedex state via PokedexLoader serialization or deep copy)
  - `captureUndoState()` before each mutation
  - Undo/redo stacks with MAX_UNDO_HISTORY = 50
- [ ] Dirty tracking — mark dirty on any edit, prompt on unsaved switch
- [ ] Save: serialize via `PokedexLoader.save()`, then `Assets.reload()` for hot-reload
- [ ] Panel shortcuts: Ctrl+S (save), Ctrl+Z (undo), Ctrl+Shift+Z (redo)

### Phase 6: Polish & Testing
- [ ] Unsaved changes popup when switching Pokedex or closing panel
- [ ] Status bar messages for save/delete/error
- [ ] Manual test: open from Asset Browser, add species, add moves, edit learnset, save, verify JSON, hot-reload
- [ ] Manual test: undo/redo through multiple operations

---

## Files to Change

| File | Change | Phase |
|------|--------|-------|
| `pokemon/PokemonSpecies.java` | Add `@Setter` | 1 |
| `pokemon/Move.java` | Add `@Setter` | 1 |
| `pokemon/Pokedex.java` | Add `removeSpecies()`, `removeMove()` | 1 |
| `editor/EditorPanelType.java` | Add `POKEDEX_EDITOR` | 1 |
| `resources/loaders/PokedexLoader.java` | Return `POKEDEX_EDITOR` from `getEditorPanelType()` | 1 |
| `editor/panels/PokedexEditorPanel.java` | **NEW** — Full editor panel | 2-5 |
| `editor/EditorUIController.java` | Wire panel: field, create, handler, render | 2 |

---

## Key Patterns to Follow

- **Two-column layout**: Same as `DialogueEditorPanel` — `ImGui.beginChild()` for left/right split
- **Tab bar**: `ImGui.beginTabBar()` / `ImGui.beginTabItem()` at top of left column for Species/Moves toggle
- **Undo snapshots**: Capture before mutation, restore on undo (same as DialogueEditorPanel)
- **Asset loading**: `Assets.scanByType(Pokedex.class)` to find all pokedex files, `Assets.load()` to load
- **Save**: `PokedexLoader.save()` to disk, then `Assets.reload()` to hot-reload cached instance
- **Dirty tracking**: Boolean flag, prompt before losing changes

## Acceptance Criteria

- [ ] Double-clicking `.pokedex.json` in Asset Browser opens the Pokedex Editor
- [ ] Species can be added, edited, and deleted with all fields editable
- [ ] Moves can be added, edited, and deleted with all fields editable
- [ ] Learnset entries can be added, removed, and reordered within a species
- [ ] Save writes valid JSON readable by PokedexLoader
- [ ] Undo/redo works across species and move edits
- [ ] Hot-reload updates cached Pokedex after save
- [ ] Unsaved changes prompt prevents accidental data loss
