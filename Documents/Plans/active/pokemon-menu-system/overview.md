# Pokemon Menu System — Overview

## Summary

Implement a classic Pokemon-style start menu that appears when the player presses the MENU button (Escape/Start). The menu provides access to the Pokemon team, inventory bag, save game, and quit game. All navigation is keyboard-driven (arrow keys + interact/cancel buttons) — no mouse interaction.

The menu system follows the same architectural patterns as the existing `DialogueUIBuilder` / `PlayerDialogueManager`:
- **UIBuilder** classes create the UI hierarchy programmatically with `componentKey` registration
- **Controller** components manage state, handle input, and update UI via `ComponentKeyRegistry`
- **Tween** animations for transitions (slide in/out)
- **InputMode.MENU** gates all menu input, preventing overworld actions

### Goals

1. **Main Menu** — Slide-in panel with options that appear conditionally based on game progress (POKéDEX when obtained, POKéMON when party non-empty); core options: BAG, CARD, SAVE, QUIT, CLOSE
2. **Pokemon Team Screen** — View party of up to 6 Pokemon with names, levels, HP bars, status
3. **Inventory Screen** — Browse items by category pocket, with item description and money display
4. **Save Game** — Save info summary + confirmation → `SaveManager.save()` → success feedback
5. **Quit Game** — Confirmation dialog → exit application (default selection: NO)
6. **Reusable Components** — `SelectableList` component reusable across menu, shops, battle, etc.
7. **Nested Prefab Architecture** — Menu UI is a nested prefab within the player prefab; reusable sub-elements (HP bar, selectable list, confirmation dialog) are standalone prefabs

### Conditional Menu Options

Menu options appear dynamically based on game progress, matching classic Pokemon behavior:
- **POKéDEX** — Only shown after the player receives the Pokedex (e.g., from professor)
- **POKéMON** — Only shown when the player has at least 1 Pokemon in their party
- **BAG, CARD, SAVE, QUIT, CLOSE** — Always shown

`MenuManager.openMenu()` calls `setItems()` with the current conditional list each time the menu opens. The `SelectableList` auto-sizes to fit the current item count.

### Planned Menu Options (designed, not yet phased)

These screens are fully designed (see `screen-designs-pokedex.md` and `screen-designs-player-card.md`) but require data model additions before implementation:
- **POKeDEX** — Species list with seen/caught tracking, detail view. Requires `PlayerData.pokedexSeen/pokedexCaught` sets and `PokemonSpecies.dexNumber/description` fields.
- **TRAINER CARD** — Player profile: name, trainer ID, money, Pokedex progress, play time, badges. Requires `PlayerData.trainerId`, badge system.

### Future Menu Options (out of scope)

- **OPTIONS** — Settings menu (audio, text speed, etc.)

## Plan Index

| File | Content |
|------|---------|
| `overview.md` | This file — goals, dependencies, architecture, decisions |
| `phase-0-nested-prefabs.md` | Phase 0: Nested prefab runtime support (prerequisite) |
| `screen-designs.md` | ASCII mockups and layout specs for core screens (menu, team, inventory, save, quit) |
| `screen-designs-pokedex.md` | Pokedex screen — species list, detail view, seen/caught tracking |
| `screen-designs-player-card.md` | Player Card screen — trainer profile, badges, Pokedex progress |
| `class-designs.md` | SelectableList, MenuManager, controllers, UIBuilder API designs |
| `implementation-phases.md` | Phases 1–8 task lists, files to create/modify, testing strategy |
| `review-changelog.md` | Review feedback summary from UI expert + senior engineer |

## MVP Definition

**Minimum shippable menu:** Phases 0–3 + Phase 6 (integration).

Delivers: main menu (conditional options), save with overwrite confirmation, quit to title, and the reusable SelectableList/SelectableSlot components. Team and Inventory screens (Phases 4–5) are high-priority fast-follow. Pokedex and Player Card wait until their data dependencies exist.

**MVP acceptance criteria:**
- Escape opens menu with slide-in animation
- Menu options appear conditionally (POKéDEX/POKéMON only when available)
- Arrow navigation + interact/cancel works on all lists
- SAVE shows game info, confirms overwrite if save exists, saves, auto-dismisses success
- QUIT defaults to NO, confirms, returns to title screen
- CLOSE and Escape close the menu
- InputMode.MENU blocks overworld/dialogue input
- IPausable pauses game logic while menu is open
- MenuManager.onDestroy() cleans up state on scene transitions

## Dependencies

| Dependency | Status | Impact |
|-----------|--------|--------|
| **Nested Prefab Support** | **Phase 0 of this plan** | Required for menu UI as nested prefab in player prefab |
| **PauseManager** | **Phase 0 of this plan** | Reference-counted pause system, replaces per-consumer pauseAll/resumeAll |
| **PrefabCodeGenerator** | **Phase 0 of this plan** | Reusable utility for building prefab JSON from code (used by PokemonMenuPrefabGenerator) |
| `InputMode` enum | Exists | Add `MENU` value |
| `PlayerInput` | Exists | Already supports `onMenu()` callbacks |
| `SaveManager` | Exists | `save()`, `listSaves()`, `getTotalPlayTime()` API ready |
| `PlayerData` | Exists | Position, team, inventory, playerName, money |
| `PlayerPartyComponent` | Exists (pokemon-ecs) | Team data source |
| `PlayerInventoryComponent` | Exists (item-inventory) | Bag data source + money |
| `ItemRegistry` | Exists (item-inventory) | Item names/descriptions/categories |
| `PokemonInstance` | Exists (pokemon-data) | Pokemon details (name, level, HP, status) |
| `ItemCategory` | Exists (item-inventory) | Inventory pocket categories |
| `Inventory` | Exists (item-inventory) | Item stacks organized by category |
| `ComponentKeyRegistry` | Exists | Runtime UI element lookup |
| `Tweens` / `TweenManager` | Exists | Slide animations |
| `UICanvas`, `UIImage`, `UIText`, `UITransform` | Exists | All UI components ready |
| `UIVerticalLayoutGroup` | Exists | Layout management |
| `IPausable` | Exists | Pause game systems while menu open |

All data systems (pokemon-data, pokemon-ecs, item-inventory) are implemented. `PlayerPartyComponent` and `PlayerInventoryComponent` are available on the player GameObject. Use `@ComponentReference(source = Source.SELF)` to resolve them directly.

**Phase 0 fallback:** If nested prefab support stalls, the menu UI can be built as a flat prefab using `PokemonMenuUIBuilder` injecting directly (same pattern as `DialogueUISceneInjector`). The nested prefab architecture is preferred for reuse but is not strictly required for the menu MVP. The PauseManager and PrefabCodeGenerator parts of Phase 0 are independent of nested prefabs and should be completed regardless.

## Architecture

### Nested Prefab Integration

The menu UI is a **nested prefab** within the player prefab (`overworld_player.prefab.json`). This is enabled by Phase 0's nested prefab support.

```
overworld_player.prefab.json
├── Player (root) — PlayerInput, PlayerPartyComponent, PlayerInventoryComponent, MenuManager, ...
├── InGame Canvas — HUD elements
├── DialogueUI Canvas — Dialogue system (existing)
└── PokemonMenuUI Canvas — ★ NESTED PREFAB: pokemon_menu_ui.prefab.json
    ├── MainMenuPanel
    │   └── SelectableList (7 options)
    ├── TeamScreen
    │   ├── TeamScreenController
    │   └── 6x PokemonSlot — ★ NESTED PREFAB: pokemon_slot.prefab.json (reusable)
    │       ├── HP bar track + fill
    │       ├── Name, Level, Status, HP text
    │       └── Icon placeholder
    ├── InventoryScreen
    │   ├── InventoryScreenController
    │   ├── CategoryTabBar (HORIZONTAL SelectableList)
    │   └── ItemList (VERTICAL SelectableList, 8 slots)
    └── ConfirmDialog — ★ NESTED PREFAB: confirm_dialog.prefab.json (reusable)
        ├── Info text, Message text
        └── SelectableList (YES/NO or OK)
```

**Reusable nested prefabs:**

| Prefab | File | Reuse Sites |
|--------|------|-------------|
| `pokemon_slot.prefab.json` | HP bar + name/level/status | Team screen, battle UI, PC storage |
| `confirm_dialog.prefab.json` | Message + YES/NO/OK list | Save, quit, item use, overwrite |
| `pokemon_menu_ui.prefab.json` | Complete menu UI hierarchy | Player prefab (nested) |

### Component Relationships

```
PlayerPrefab (GameObject)
├── PlayerInput              (existing — provides onMenu callback + input polling)
├── PlayerPartyComponent     (existing — team data source)
├── PlayerInventoryComponent (existing — bag data source + money)
└── MenuManager              (NEW — central menu controller)
    ├── Listens to PlayerInput.onMenu(OVERWORLD) to open
    ├── Switches InputMode to MENU on open
    ├── Polls input in update() and pushes to active SelectableList
    ├── Manages MenuState machine with animation guards
    ├── Manages single activeList reference (only one list receives input)
    ├── Delegates to sub-controllers per screen
    └── Returns InputMode to OVERWORLD on close

MenuUI (nested prefab instance within player prefab)
├── UICanvas (sortOrder 15, above dialogue's 10)
├── MainMenuPanel        (slide-in from right)
│   └── SelectableList   (arrow navigation, componentKey: "menu_main_list")
├── TeamScreen           (full-screen overlay, starts hidden)
│   └── TeamScreenController
├── InventoryScreen      (full-screen overlay, starts hidden)
│   └── InventoryScreenController
└── ConfirmDialog        (nested prefab: confirm_dialog.prefab.json)
    └── SelectableList   (YES/NO, componentKey: "menu_confirm_list")
```

### Key Architectural Decisions

**Push model for input (not pull):** `SelectableList` does NOT hold a `PlayerInput` reference. It lives in the MenuUI hierarchy (a separate GameObject tree from the Player). Instead, `MenuManager` (on the Player, with access to `PlayerInput`) polls input and calls methods on the active `SelectableList`:
```java
// SelectableList — zero coupling to PlayerInput
public void moveUp();
public void moveDown();
public void select();
public void cancel();

// MenuManager.update() — routes input to the active list
Direction dir = playerInput.getMovementDirectionUp();
if (dir == Direction.UP) activeList.moveUp();
if (dir == Direction.DOWN) activeList.moveDown();
if (playerInput.isInteractPressed()) activeList.select();
if (playerInput.isMenuPressed()) activeList.cancel();
```

This makes `SelectableList` truly reusable — it works in any context (menu, shop, battle) with any input source.

**Single active list:** `MenuManager` holds a single `activeList` reference. When switching screens, the old list is deactivated and the new list is set as active. This prevents multiple lists from processing the same input.

**MenuScreen interface for extensibility:** Each sub-screen (team, inventory, Pokedex, card) implements a `MenuScreen` interface with `show()`, `hide()`, `onActivated(MenuManager)`, and `killTweens()`. `MenuManager` delegates screen transitions to the active `MenuScreen` instead of managing all UITransform/tween references directly. This prevents `MenuManager` from accumulating God object traits as new screens are added.

**Declarative UI resolution:** All component references use `@ComponentReference(source = Source.KEY)` annotations. The framework resolves keys at scene load time — no manual `resolveUI()` or `uiResolved` flags needed. `List<SelectableSlot>` is serialized as a JSON string array of componentKeys. The menu UI prefab is instantiated as a nested prefab within the player prefab, so keys exist when reference resolution runs.

**Animation guard states:** `MenuState` includes `OPENING`, `CLOSING`, and `CONFIRM_OVERWRITE` states. `update()` skips input processing during animations. Tween `onComplete` callbacks transition to the next state.

**IPausable safety via PauseManager (Phase 0):** The current pattern — each consumer (`PlayerDialogueManager`, future `MenuManager`) having its own `pauseAll()`/`resumeAll()` that calls `scene.getComponentsImplementing(IPausable.class)` — is fragile. If two systems pause simultaneously and one resumes first, all pausables resume incorrectly (e.g., player moves during dialogue because menu closed). Phase 0 introduces a **reference-counted `PauseManager`** that replaces this pattern. Consumers call `PauseManager.requestPause(owner)` and `PauseManager.releasePause(owner)`. `IPausable.onResume()` only fires when all pause requests are released. The InputMode gate (`OVERWORLD` → `MENU`) prevents the menu from opening during dialogue, but PauseManager is the architectural fix that makes pause safe for any number of consumers.

**InputMode.MENU blocks everything:** The `onMenu` callback is registered for `OVERWORLD` only, so it won't fire while the menu is already open. Inside the menu, `MenuManager.update()` polls `isMenuPressed()` directly to handle cancel/close. The `SelectableList.onCancel` callback is the sole handler for the MENU/B button — `MenuManager` does not register a parallel `onMenu(InputMode.MENU, ...)` listener.

**Lifecycle cleanup:** `MenuManager.onDestroy()` force-closes the menu if open: kills all tweens, resumes all pausables, resets InputMode to OVERWORLD, and sets state to CLOSED. This prevents leaked state when scenes transition while the menu is open. All tween `onComplete` callbacks include state guards (`if (state != expectedState) return;`) to handle stale callbacks firing after a state change or scene unload.

**Input auto-repeat for long lists:** `MenuManager.update()` implements held-direction auto-repeat for scrollable lists (Pokedex, inventory). After an initial delay (~0.3s), repeated `movePrimary()` calls fire at a moderate rate (~8/s). This is essential for scrolling through 150+ Pokedex entries. Short lists (main menu, confirmation dialogs) do not need auto-repeat — single edge-triggered input per press is sufficient.

### State Machine

```
                    ┌──────────────┐
                    │  OVERWORLD   │
                    └──────┬───────┘
                  press MENU │ ▲ closing anim done
                             │ │
                    ┌────────▼─┴──────┐
             ┌─────│    OPENING      │
             │     └─────────────────┘
             │       anim done │
             │     ┌───────────▼──────┐
             │     │   MAIN_MENU     │◄──────────────────────┐
             │     └──┬──┬──┬──┬──┬──┬─────┘                   │
             │  POKéDEX┘  │  │  │  │  └─ QUIT                │
             │  POKéMON───┘  │  │  └── SAVE                  │
             │     BAG ──────┘  └── CARD                     │
             │      │  │    │      │          press MENU/B   │ or OK
             │ ┌────▼──▼─┐ ┌▼─────▼───┐  ┌──────────┐      │
             │ │ SCREEN   │ │ CONFIRM  │  │ SAVE OK  │      │
             │ │(TEAM/INV/│ │ DIALOG   │  │ MESSAGE  │──────┤
             │ │DEX/CARD) │ └──────────┘  └──────────┘      │
             │ └──────────┘       │                          │
             │      │             │                          │
             │      └─────────────┴──────────────────────────┘
             │                         press MENU or CLOSE
             │     ┌─────────────────┐
             └─────│    CLOSING      │
                   └─────────────────┘
```

### State Transitions Table

| From | Trigger | To | Action |
|------|---------|-----|--------|
| CLOSED | MENU press (OVERWORLD) | OPENING | Pause game, set MENU mode, start slide-in tween |
| OPENING | Tween complete | MAIN_MENU | Enable input on main list |
| MAIN_MENU | Select POKéDEX | POKEDEX_LIST | Hide main panel, show Pokedex, refresh data (see `screen-designs-pokedex.md`) |
| MAIN_MENU | Select POKéMON | TEAM_SCREEN | Hide main panel, show team, refresh data |
| MAIN_MENU | Select BAG | INVENTORY_SCREEN | Hide main panel, show inventory, refresh data |
| MAIN_MENU | Select CARD | PLAYER_CARD | Hide main panel, show trainer card, refresh data (see `screen-designs-player-card.md`) |
| MAIN_MENU | Select SAVE | CONFIRM_SAVE / CONFIRM_OVERWRITE | Show save info. If save exists: show overwrite warning first (default: YES). Else: show save confirmation (default: YES). |
| CONFIRM_OVERWRITE | YES | CONFIRM_SAVE | Show "Would you like to save?" confirmation |
| CONFIRM_OVERWRITE | NO / MENU press | MAIN_MENU | Hide dialog, show main panel |
| MAIN_MENU | Select QUIT | CONFIRM_QUIT | Show quit confirm dialog (default: NO) |
| MAIN_MENU | Select CLOSE / MENU press | CLOSING | Start slide-out tween |
| TEAM_SCREEN | BACK / MENU press | MAIN_MENU | Hide team, show main panel |
| INVENTORY_SCREEN | BACK / MENU press | MAIN_MENU | Hide inventory, show main panel |
| POKEDEX_LIST | BACK / MENU press | MAIN_MENU | Hide Pokedex, show main panel (see `screen-designs-pokedex.md`) |
| PLAYER_CARD | BACK / MENU press | MAIN_MENU | Hide card, show main panel (see `screen-designs-player-card.md`) |
| CONFIRM_SAVE | YES | SAVE_SUCCESS | SaveManager.save(), show success message |
| CONFIRM_SAVE | NO / MENU press | MAIN_MENU | Hide dialog, show main panel |
| CONFIRM_QUIT | YES | — | Return to title screen (SceneManager.loadScene(titleScene)) |
| CONFIRM_QUIT | NO / MENU press | MAIN_MENU | Hide dialog, show main panel |
| SAVE_SUCCESS | Auto-dismiss (1s) or INTERACT / MENU press | MAIN_MENU | Brief "Game saved!" message, auto-returns to main menu |
| CLOSING | Tween complete | CLOSED | Resume game, set OVERWORLD mode |

### Reusable: SelectableList Component

The `SelectableList` is the centerpiece reusable component. It handles:
- Visual arrow indicator on the selected item
- Scrolling when items exceed visible slots
- Scroll indicators (▲ at top when scrollOffset > 0, ▼ at bottom when more items below)
- Callbacks: `onSelect(index)`, `onCancel()`, `onSelectionChanged(index)`
- Optional wrap-around navigation
- Orientation: VERTICAL (UP/DOWN) or HORIZONTAL (LEFT/RIGHT)

**Critically, it does NOT poll input.** Input is pushed to it by the owning controller.

**Reuse targets:**
| System | Usage | Orientation |
|--------|-------|-------------|
| Main Menu | Up to 7 options, conditional on game progress (POKéDEX, POKéMON, BAG, CARD, SAVE, QUIT, CLOSE) | VERTICAL |
| Pokemon Team | 6 party slots + BACK | VERTICAL |
| Inventory Items | Scrollable item list per pocket | VERTICAL |
| Inventory Categories | Category tab switching | HORIZONTAL |
| Confirmation Dialog | YES / NO | VERTICAL |
| Shop System (future) | Buy/sell item lists | VERTICAL |
| Battle System (future) | Move selection | VERTICAL |

**Wrap-around defaults:**
- Main menu: `wrapAround = false` (matches classic Pokemon)
- Team screen: `wrapAround = false`
- Confirmation dialogs: `wrapAround = false`
- Inventory item list: `wrapAround = true` (scrollable lists benefit from it)
- Inventory categories: `wrapAround = true` (cycle through tabs)

### Prefab Strategy

The menu UI is a **JSON-based nested prefab** referenced from within `overworld_player.prefab.json`. This is the recommended approach over the old `PokemonMenuPrefabGenerator` utility.

**Workflow:**
1. `PokemonMenuUIBuilder.build()` creates the hierarchy programmatically (development tool)
2. `PokemonMenuPrefabGenerator` (main method utility) runs the builder and serializes the output to `gameData/assets/prefabs/pokemon_menu_ui.prefab.json`
3. The prefab is referenced as a **nested prefab node** within `overworld_player.prefab.json` — the node has a `prefab` field pointing to the menu prefab
4. At runtime, the nested prefab system (Phase 0) recursively instantiates the menu hierarchy when the player prefab loads
5. `@ComponentReference(source = KEY)` annotations resolve UI elements declaratively at scene load time

**Regeneration:** If the builder is modified (layout tweaks, new elements), re-run the generator to update the `.prefab.json`.

**Generic PrefabCodeGenerator framework (Phase 0):** The existing `DialogueUISceneInjector` builds UI hierarchies as raw Gson `JsonObject` trees and injects them into scene files. The menu system needs the same capability (via `PokemonMenuPrefabGenerator`), and future systems will too. Phase 0 extracts a reusable `PrefabCodeGenerator` base class (or utility) from the `DialogueUISceneInjector` pattern. It provides:
- Helper methods to build `GameObjectData` nodes: `gameObject(id, name, parentId, components...)`
- Helper methods to build common components: `uiTransform(...)`, `uiText(...)`, `uiImage(...)`, `uiCanvas(...)`, etc.
- JSON serialization to `.prefab.json` files (not scene injection — prefab files are standalone)
- ID generation (`UUID.randomUUID()` or sequential)

`PokemonMenuUIBuilder` and `PokemonMenuPrefabGenerator` in Phase 2 use this framework instead of building raw JSON from scratch. The `DialogueUISceneInjector` can also be refactored to use it (optional, not required for MVP).

**Nested prefab sub-elements:**

| Sub-element | Prefab File | Reuse Sites |
|-------------|-------------|-------------|
| Pokemon slot (HP bar + info) | `pokemon_slot.prefab.json` | Team screen ×6, battle UI, PC storage |
| Confirmation dialog (message + YES/NO/OK) | `confirm_dialog.prefab.json` | Save, quit, item use, overwrite |
| Category tab bar (LEFT/RIGHT) | Inline (not extracted yet) | Inventory, PC storage |

### Localization

All UI strings are hardcoded in the builder and controllers for now. A future localization pass would extract them to a resource file. This is documented debt, not a current blocker.
