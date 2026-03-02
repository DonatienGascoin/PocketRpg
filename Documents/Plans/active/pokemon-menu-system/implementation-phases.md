# Pokemon Menu System — Implementation Phases

## Phase Summary

| Phase | Name | Description | Depends On |
|-------|------|-------------|------------|
| **0** | Nested Prefab Support | Runtime + editor nested prefab instantiation | None |
| **1** | Core Infrastructure | SelectableList component, MenuState enum, InputMode.MENU | Phase 0 |
| **2** | Main Menu Screen | MenuManager, main menu panel, PokemonMenuPrefabGenerator | Phase 1 |
| **3** | Confirmation Dialog + Save + Quit | Reusable confirm dialog (nested prefab), save/quit flows | Phase 2 |
| **4** | Pokemon Team Screen | Team display with HP bars (nested prefab slots), TeamScreenController | Phase 2 |
| **5** | Inventory Screen | Category tabs, item list, description panel, InventoryScreenController | Phase 2 |
| **6** | Integration, Polish & Review | Wire all screens, transition animations, edge case testing, sound hooks, code review | Phases 3–5 |

## MVP Definition

**Minimum shippable set:** Phases 0–3 + Phase 6. Delivers: main menu with conditional options, save with overwrite confirmation, quit to title, and reusable SelectableList/SelectableSlot components.

**Fast-follow:** Phases 4 (Team) and 5 (Inventory) are high priority but can ship independently.

**Note:** Phases 3, 4, and 5 are independent of each other (all depend on Phase 2 only). They can be developed in parallel if multiple contributors are available.

---

## Files to Create / Modify

### New Files

| File | Phase | Description |
|------|-------|-------------|
| `scenes/PauseManager.java` | 0 | Reference-counted pause system, owned by Scene |
| `tools/PrefabCodeGenerator.java` | 0 | Reusable utility for building prefab JSON from code |
| `components/ui/SelectableSlot.java` | 1 | Base slot component: owns its arrow + label UI elements |
| `components/ui/SelectableList.java` | 1 | Manages List<SelectableSlot>, scrolling, selection |
| `components/ui/InventorySlot.java` | 5 | Extends SelectableSlot: adds quantity text |
| `components/ui/PokemonSlot.java` | 4 | Extends SelectableSlot: adds HP bar, level, status, icon |
| `components/ui/menu/PokemonMenuUIBuilder.java` | 2 | UI hierarchy builder (development tool) |
| `components/ui/menu/PokemonMenuPrefabGenerator.java` | 2 | Serializes builder output to JSON prefab files (uses `PrefabCodeGenerator` from Phase 0) |
| `components/pokemon/MenuManager.java` | 2 | Central menu controller, input router, state machine |
| `components/pokemon/MenuState.java` | 1 | Menu state enum with animation states |
| `components/pokemon/TeamScreenController.java` | 4 | Pokemon team screen logic + HP bar updates |
| `components/pokemon/InventoryScreenController.java` | 5 | Inventory screen logic + category filtering |
| `components/ui/menu/MenuScreen.java` | 2 | Interface for sub-screen delegation (`show`, `hide`, `onActivated`, `killTweens`) |
| `components/pokemon/PokedexScreenController.java` | Future | Pokedex screen logic (post-MVP, when Pokedex data dependencies are met) |
| `components/pokemon/PlayerCardController.java` | Future | Trainer card screen logic (post-MVP, when badge/trainer ID data exists) |
| `gameData/assets/prefabs/pokemon_menu_ui.prefab.json` | 2 | **Generated** — JSON prefab for the menu UI hierarchy |
| `gameData/assets/prefabs/pokemon_slot.prefab.json` | 4 | **Generated** — Reusable Pokemon slot sub-prefab |
| `gameData/assets/prefabs/confirm_dialog.prefab.json` | 3 | **Generated** — Reusable confirmation dialog sub-prefab |

### Modified Files

| File | Phase | Change |
|------|-------|--------|
| `prefab/Prefab.java` | 0 | Nested prefab support in `instantiateChildren()` |
| `prefab/PrefabHierarchyHelper.java` | 0 | Nested prefab expansion in `expandChildren()` |
| `editor/scene/RuntimeSceneLoader.java` | 0 | Node map traversal for nested prefabs |
| `editor/serialization/EditorSceneSerializer.java` | 0 | Child override handling for nested nodes |
| `editor/scene/EditorGameObject.java` | 0 | `nestedPrefabPath` field |
| `scenes/Scene.java` | 0 | Add `PauseManager` field + `getPauseManager()` accessor |
| `components/dialogue/PlayerDialogueManager.java` | 0 | Replace private `pauseAll()`/`resumeAll()` with `PauseManager` |
| `components/player/InputMode.java` | 1 | Add `MENU` value to enum |
| `components/pokemon/PlayerPauseUI.java` | 2 | **Remove** — replaced by `MenuManager` |
| `overworld_player.prefab.json` | 2 | Add nested prefab node for menu UI, add MenuManager component, remove PlayerPauseUI |

---

## Phase 0: Nested Prefabs, PauseManager & PrefabCodeGenerator

**See `phase-0-nested-prefabs.md` for full design.**

**Done when:** (1) All existing prefabs/scenes work unchanged. A test prefab with a nested prefab node instantiates correctly at runtime and in the editor. (2) PauseManager is integrated and PlayerDialogueManager uses it. (3) PrefabCodeGenerator can produce valid `.prefab.json` files.

**Nested Prefab Support:**
- [ ] Add `nestedPrefabPath` field to `EditorGameObject`
- [ ] Modify `Prefab.instantiateChildren()` to handle nested prefab nodes
- [ ] Modify `PrefabHierarchyHelper.expandChildren()` for nested expansion
- [ ] Modify `RuntimeSceneLoader.buildNodeIdMap()` to skip nested subtrees
- [ ] Modify `EditorSceneSerializer.buildChildOverrides()` for nested nodes
- [ ] Modify `PrefabHierarchyHelper.reconcileInstance()` for nested nodes
- [ ] Unit tests for nested prefab instantiation
- [ ] Regression tests: verify ALL existing prefabs still instantiate correctly (no regression from isPrefabInstance check)
- [ ] Regression tests: verify existing scenes still load and editor can save/load

**PauseManager (Reference-Counted):**
- [ ] Create `PauseManager` class with `requestPause(owner)` / `releasePause(owner)`
- [ ] Add `PauseManager` to `Scene` (field + `getPauseManager()` accessor)
- [ ] Migrate `PlayerDialogueManager` from private `pauseAll()`/`resumeAll()` to `PauseManager`
- [ ] Unit tests: single owner, dual owner overlap, duplicate request, isPaused()/getPauseCount()

**PrefabCodeGenerator Framework:**
- [ ] Create `PrefabCodeGenerator` utility (node builders, component builders, JSON serialization)
- [ ] ID generation, `toJson()`, `writeTo(Path)`
- [ ] Unit test: build hierarchy → verify JSON structure matches engine format

---

## Phase 1: Core Infrastructure

Foundation components that everything else depends on.

**Done when:** SelectableSlot and SelectableList pass all unit tests. Navigation, scrolling, callbacks, wrap-around, and edge cases (empty list, boundaries) all verified.

- [ ] Add `MENU` to `InputMode` enum
- [ ] Create `MenuState` enum (with OPENING/CLOSING animation states)
- [ ] Create `SelectableSlot` component:
  - [ ] `@ComponentReference(source = KEY)` for `arrowText: UIText` and `labelText: UIText`
  - [ ] `setSelected(boolean)` — show/hide arrow text (`<` or `""`)
  - [ ] `setLabel(String)` — update label text
  - [ ] `setVisible(boolean)` — enable/disable the slot's GameObject
  - [ ] No manual resolveUI() — framework resolves KEY references at load time
- [ ] Create `SelectableList` component:
  - [ ] `@ComponentReference(source = KEY)` for `List<SelectableSlot> slots` (serialized as JSON string array)
  - [ ] `@ComponentReference(source = KEY, required = false)` for `scrollUpIndicator` and `scrollDownIndicator`
  - [ ] Push API: `movePrimary(delta)`, `moveUp()`, `moveDown()`, `select()`, `cancel()`
  - [ ] Orientation enum: VERTICAL / HORIZONTAL
  - [ ] Delegates arrow/label updates to `SelectableSlot.setSelected()` / `setLabel()`
  - [ ] Scrolling support (scrollOffset when items > maxVisibleItems)
  - [ ] Scroll indicators (▲/▼ shown when more items exist above/below)
  - [ ] Callbacks: onSelect, onCancel, onSelectionChanged
  - [ ] Wrap-around option
  - [ ] `setItems()` / `setSelectedIndex()` / `setActive()` API
  - [ ] componentKey on the SelectableList component itself for lookup by controller
  - [ ] Empty list guard (no crash on navigation when items is empty)
- [ ] Input auto-repeat support in `MenuManager.update()`:
  - [ ] Track held direction with repeat delay (~0.3s) and repeat rate (~8/s)
  - [ ] Only active for scrollable states (INVENTORY_SCREEN, POKEDEX_LIST)
  - [ ] Short lists (MAIN_MENU, CONFIRM_*) use edge-triggered input only
- [ ] Verify `@ComponentReference(source = KEY)` supports `List<SelectableSlot>` resolution from JSON string arrays. If not supported, implement in `ComponentReferenceResolver`.
- [ ] Unit tests for SelectableSlot and SelectableList navigation logic

---

## Phase 2: Main Menu Screen

The entry point menu panel.

**Done when:** Pressing Escape in overworld opens a sliding menu with conditional options. Arrow navigation works. CLOSE/Escape closes the menu. MenuManager exists on player prefab. PlayerPauseUI removed.

- [ ] Create `PokemonMenuUIBuilder.buildMainMenu()`:
  - [ ] UICanvas root (sortOrder 15)
  - [ ] Main panel (UIImage SLICED, TOP_RIGHT anchor, 30% width)
  - [ ] 7 selectable slots: POKéDEX, POKéMON, BAG, CARD, SAVE, QUIT, CLOSE
  - [ ] SelectableList component with componentKey `"menu_main_list"`
  - [ ] Register all componentKeys
  - [ ] All screens start hidden (off-screen or disabled)
- [ ] Create `MenuManager` component:
  - [ ] `@ComponentReference(source = SELF)` for `PlayerInput`, `PlayerPartyComponent`, `PlayerInventoryComponent`
  - [ ] `@ComponentReference(source = KEY)` for all `SelectableList` refs: mainMenuList, confirmList, categoryList, itemList, teamList
  - [ ] `@ComponentReference(source = KEY)` for all `UITransform` panel refs: mainMenuPanelTransform, teamScreenTransform, inventoryScreenTransform, confirmDialogTransform
  - [ ] No manual `resolveUI()` — all references resolved by framework at load time
  - [ ] Register `onMenu(InputMode.OVERWORLD, this::openMenu)` in `onStart()`
  - [ ] `openMenu()`: guard against scene transitions, call `killAllMenuTweens()`, set OPENING, pauseAll, set MENU mode, slide in, onComplete → MAIN_MENU
  - [ ] `closeMenu()`: `killAllMenuTweens()`, set CLOSING, slide out, onComplete → CLOSED, resumeAll, set OVERWORLD mode
  - [ ] `update()`: skip if CLOSED/OPENING/CLOSING/SAVING, poll PlayerInput, route to activeList
  - [ ] `setActiveList(list)`: deactivate old, activate new
  - [ ] `killAllMenuTweens()`: kill tweens on ALL menu transforms
  - [ ] Main menu SelectableList.onCancel → closeMenu
  - [ ] Main menu SelectableList.onSelect → onMainMenuSelect(index)
  - [ ] `onDestroy()`: force-close menu if open (kill tweens, resumeAll, reset InputMode, state = CLOSED)
  - [ ] Add state guards to ALL tween `onComplete` callbacks (`if (state != expectedState) return;`)
  - [ ] Note: add `removeCallbacks(owner)` to `PlayerInput` to prevent callback accumulation in editor
  - [ ] Menu options built dynamically: POKéDEX only when obtained, POKéMON only when party non-empty
  - [ ] `openMenu()` calls `setItems()` with conditional list each time
- [ ] Create `PokemonMenuPrefabGenerator` utility:
  - [ ] Runs `PokemonMenuUIBuilder.build()` and serializes to `gameData/assets/prefabs/pokemon_menu_ui.prefab.json`
  - [ ] Output is a standard JSON prefab loadable by `JsonPrefab`
- [ ] Add menu UI as a **nested prefab node** in `overworld_player.prefab.json`:
  - [ ] New `GameObjectData` node with `prefab: "gameData/assets/prefabs/pokemon_menu_ui.prefab.json"` and `parentId` set to the player root
  - [ ] Add `MenuManager` component to the player root node
  - [ ] Remove `PlayerPauseUI` component from the player root node
- [ ] Remove `PlayerPauseUI` (functionality moved to MenuManager). Keep class file temporarily as deprecated stub if ComponentRegistry cannot handle unknown _type during deserialization. Verify existing prefab/scene files referencing PlayerPauseUI are updated.
- [ ] Create `MenuScreen` interface: `show()`, `hide()`, `onActivated(MenuManager)`, `killTweens()`
- [ ] Sub-controllers (TeamScreenController, InventoryScreenController, etc.) implement `MenuScreen`
- [ ] `MenuManager` delegates screen transitions via `Map<MenuState, MenuScreen>`

---

## Phase 3: Confirmation Dialog + Save + Quit

Reusable confirmation dialog as a nested sub-prefab.

**Done when:** SAVE shows game info, overwrite check if save exists, saves successfully, shows "Game saved!" and auto-returns. QUIT defaults to NO, returns to title on YES. All confirmation dialogs work.

- [ ] Create `PokemonMenuUIBuilder.buildConfirmDialog()`:
  - [ ] Center-anchored panel (40% width, auto height)
  - [ ] Info text (for save summary: player name, play time)
  - [ ] Message text (UIText, word-wrapped, left-aligned, font size 18)
  - [ ] 2-slot SelectableList (YES / NO or single OK)
  - [ ] Starts hidden
- [ ] Generate `confirm_dialog.prefab.json` as a standalone sub-prefab
- [ ] Reference `confirm_dialog.prefab.json` as a **nested prefab node** inside `pokemon_menu_ui.prefab.json`
- [ ] `showConfirmDialog(message, infoText, defaultIndex, onYes)`:
  - [ ] Set message and info text
  - [ ] Set confirm list items (YES/NO or OK)
  - [ ] Set default selection index (0=first, 1=second)
  - [ ] Show dialog, dim main menu behind (50% alpha)
  - [ ] `setActiveList(confirmList)`
- [ ] Save flow in MenuManager:
  - [ ] Build info text: "PLAYER: " + playerName + "\nBADGES: " + badgeCount + "\nPOKéDEX: " + caughtCount + "\nTIME: " + playTime (show available fields, hide unavailable)
  - [ ] Show dialog with default = YES (index 0)
  - [ ] YES → state = SAVING, show "Saving..." text, 0.5s delay
  - [ ] After delay: call `SaveManager.save(SaveManager.getCurrentSlot())` // Use current save slot, not hardcoded "save1"
  - [ ] If success → state = SAVE_SUCCESS, show "Game saved!" for 1s then auto-return to main menu. INTERACT/MENU also dismisses immediately. No OK button.
  - [ ] If fail → state = SAVE_FAILED, show "Save failed." with OK
  - [ ] OK / MENU/B → returnToMainMenu()
- [ ] Overwrite save check: if `SaveManager.hasSave()`, show "There is already a save file. Is it OK to overwrite?" (default YES) before the save confirmation
- [ ] CONFIRM_OVERWRITE state: YES → CONFIRM_SAVE, NO/MENU → MAIN_MENU
- [ ] Quit flow in MenuManager:
  - [ ] Show dialog with default = NO (index 1)
  - [ ] YES → return to title screen (e.g., `SceneManager.loadScene("title")` — not `System.exit(0)`)
  - [ ] NO / MENU/B → returnToMainMenu()
- [ ] SAVING state has 2s watchdog timer: if main callback fails to fire, transitions to SAVE_FAILED as safety fallback
- [ ] Delay tween assigned to `confirmDialogTransform` so `killAllMenuTweens()` can cancel it
- [ ] `returnToMainMenu()`: hide current screen, show main panel, setActiveList(mainMenuList)

---

## Phase 4: Pokemon Team Screen

Display the player's party with HP bars and status. Pokemon slots are nested sub-prefabs.

**Done when:** POKéMON shows party with names, levels, HP bars (correct colors), status labels, held item indicators. Empty slots show "───". BACK returns to main menu.

- [ ] Create `PokemonMenuUIBuilder.buildTeamScreen()`:
  - [ ] Full-screen background panel
  - [ ] Title: "POKeMON"
  - [ ] 6 Pokemon slots, each a **nested prefab** (`pokemon_slot.prefab.json`) containing:
    - [ ] Arrow text (<)
    - [ ] Icon slot (8% width, placeholder, future: species sprite)
    - [ ] Name text
    - [ ] Status label text (PSN/PAR/etc)
    - [ ] Level text ("Lv.XX")
    - [ ] HP bar: dark track + colored fill (UIImage FILLED)
    - [ ] HP text ("current/max")
    - [ ] Held item indicator text ("♦" if holding)
  - [ ] Empty slots: dimmed single-line "───" at 50% alpha (no full border)
  - [ ] BACK option at bottom (7th selectable item)
  - [ ] SelectableList with componentKey `"menu_team_list"` (7 slots: 6 party + BACK)
- [ ] Generate `pokemon_slot.prefab.json` as a standalone sub-prefab
- [ ] Reference 6x `pokemon_slot.prefab.json` as nested prefab nodes inside team screen
- [ ] Create `PokemonSlot` component (extends `SelectableSlot`):
  - [ ] `@ComponentReference(source = KEY)` for `levelText`, `statusText`, `hpBarTrack`, `hpBarFill`, `hpText`
  - [ ] `@ComponentReference(source = KEY, required = false)` for `iconImage` (future)
  - [ ] `@ComponentReference(source = KEY, required = false)` for `heldItemIndicator: UIText`
  - [ ] Show "♦" when Pokemon holds an item, empty when not
  - [ ] `setPokemon(PokemonInstance)` — populate all sub-elements from Pokemon data
  - [ ] `setEmpty()` — dim slot, show "───", hide HP bar/level/status/icon
  - [ ] `updateHpBar(float hpPercent)` — fill amount + color thresholds (>50% green, 20-50% yellow, <20% red)
  - [ ] `updateStatusLabel(StatusCondition)` — abbreviation + color per condition, hidden if NONE
  - [ ] Fainted state: if `!pokemon.isAlive()`, show "FNT" in red (overrides StatusCondition display)
  - [ ] HP bar clamped to `[0.0, 1.0]`: `Math.min(1f, Math.max(0f, hpPercent))`
  - [ ] No manual resolveUI() — framework resolves all KEY references
- [ ] Create `TeamScreenController`:
  - [ ] `@ComponentReference(source = KEY)` for `List<PokemonSlot> pokemonSlots` (serialized as JSON string array)
  - [ ] `refresh(PlayerPartyComponent)` — delegates to `slot.setPokemon()` or `slot.setEmpty()` per slot
  - [ ] No manual resolveUI() — framework resolves KEY reference
- [ ] Wire to MenuManager:
  - [ ] POKeMON → hide main panel, show team screen, refresh from playerParty, setActiveList(teamList)
  - [ ] teamList.onCancel and BACK selection → hide team, show main, setActiveList(mainMenuList)
  - [ ] Refresh team data every time screen is shown (party HP/status may change)

---

## Phase 5: Inventory Screen

Display the player's bag with category tabs, money, and item descriptions.

**Done when:** BAG shows items by category with LEFT/RIGHT switching. Scroll indicators work. Description updates on selection. Money displays correctly. Empty inventory shows "No items". BACK returns to main menu.

- [ ] Create `PokemonMenuUIBuilder.buildInventoryScreen()`:
  - [ ] Full-screen background panel
  - [ ] Title: "BAG" left-aligned + money text right-aligned
  - [ ] Category tab bar: ◀ label ▶ (HORIZONTAL SelectableList, componentKey)
  - [ ] 8-slot scrollable item list (VERTICAL SelectableList with `InventorySlot` components):
    - [ ] Each `InventorySlot` extends `SelectableSlot` with quantity UIText
    - [ ] Scroll indicators: ▲ top, ▼ bottom (UIText, hidden/shown by SelectableList)
- [ ] Create `InventorySlot` component (extends `SelectableSlot`):
  - [ ] `@ComponentReference(source = KEY)` for `quantityText: UIText`
  - [ ] `setQuantity(int)` — show "xN" or empty if 1
  - [ ] `clearQuantity()` — hide quantity text
  - [ ] Description panel at bottom: 70px fixed height, word wrap, font size 18
  - [ ] BACK option (included as last item in the item list, or as 9th special slot)
- [ ] BACK appended as last item in the item `SelectableList` (not a separate slot)
- [ ] When BACK is highlighted, description panel shows empty text
- [ ] `wrapAround = true`: scrolling past BACK wraps to first item
- [ ] Create `InventoryScreenController`:
  - [ ] `@ComponentReference(source = KEY)` for `moneyText: UIText` and `descriptionText: UIText`
  - [ ] `refresh(PlayerInventoryComponent)` — populate from current inventory
  - [ ] `rebuildCategoryList()` — filter to non-empty categories, update HORIZONTAL list
  - [ ] `onCategoryChanged(index)` — rebuild item list for new category
  - [ ] `onItemSelectionChanged(index)` — update description text
  - [ ] `updateMoneyDisplay()` — show "$" + money from PlayerData
  - [ ] Handle empty inventory: show "No items" message
  - [ ] No manual resolveUI() — framework resolves KEY references
- [ ] Wire to MenuManager:
  - [ ] BAG → hide main panel, show inventory screen, refresh from playerInventory
  - [ ] Set itemList as activeList, wire categoryList.onSelectionChanged
  - [ ] MenuManager routes LEFT/RIGHT to categoryList, UP/DOWN to itemList
  - [ ] itemList.onCancel → hide inventory, show main, setActiveList(mainMenuList)
  - [ ] Refresh inventory data every time screen is shown (items may change)

---

## Phase 6: Integration, Polish & Review

Wire everything together, polish the experience, and perform final code review.

**Done when:** All screens wire together. Transitions are clean. All edge cases tested (rapid open/close, empty data, scene transitions). Code review checklist complete. All unit tests pass.

- [ ] Complete `PokemonMenuUIBuilder.build()` assembling all screens under one UICanvas
- [ ] Screen transition visuals:
  - [ ] Main menu → sub-screen: main panel hides instantly, sub-screen appears instantly (matches classic Pokemon feel)
  - [ ] Sub-screen → main menu: sub-screen hides, main panel shows
  - [ ] Main menu → confirm dialog: main panel dims to 50% alpha, dialog pops in
  - [ ] Confirm dialog → main menu: dialog hides, main panel restores full alpha
- [ ] `killAllMenuTweens()` called at start of every state transition
- [ ] Test team screen with empty party (0 Pokemon) → all slots show "───"
- [ ] Test inventory screen with empty inventory → "No items" shown
- [ ] Verify InputMode switches correctly in all transitions (no leaked MENU mode)
- [ ] Verify IPausable pause/resume in all transitions
- [ ] Edge case: open menu during dialogue → blocked by InputMode.OVERWORLD gate
- [ ] Edge case: rapid open/close menu → animation states + killAllMenuTweens prevent stale callbacks
- [ ] Edge case: open menu during scene transition → guard in openMenu()
- [ ] Edge case: empty items list in SelectableList → no crash, no callbacks
- [ ] Edge case: tween onComplete fires after state change → guard against stale state

### Sound Effect Hooks

Prepare integration points for these sounds (implement when audio system available):
- **Cursor move:** Short blip on every SelectableList.movePrimary() call
- **Confirm:** Higher-pitched tone on SelectableList.select()
- **Cancel:** Lower-pitched tone on SelectableList.cancel() and menu close
- **Menu open:** Slide-in sound
- **Menu close:** Slide-out sound
- **Save complete:** Brief jingle on SAVE_SUCCESS

- [ ] Add optional `Runnable` callbacks to SelectableList: `onMoveSound`, `onSelectSound`, `onCancelSound`
- [ ] Or document integration points for future audio system

### Code Review Checklist

- [ ] Review all new classes for common pitfalls (see `.claude/reference/common-pitfalls.md`)
- [ ] Verify componentKey uniqueness (no collisions with `dialogue_*` keys — menu uses `menu_*` prefix)
- [ ] Verify no resource leaks (sprites loaded via Assets.load are cached)
- [ ] Verify SelectableList is truly reusable (zero coupling to PlayerInput, no menu-specific logic)
- [ ] Verify InputMode transitions have no dead states (every state can reach CLOSED)
- [ ] Verify SAVE_SUCCESS / SAVE_FAILED states can always be dismissed (OK or MENU/B)
- [ ] Verify tween cleanup on scene unload (`onBeforeSceneUnload` or `onDestroy`)
- [ ] Verify quit default is NO (not YES)
- [ ] Verify HP bar thresholds: >50% green, 20-50% yellow, <20% red
- [ ] Verify scroll indicators appear correctly at list boundaries
- [ ] Check thread safety of ComponentKeyRegistry access

---

## Post-MVP: Future Interactions

These features use existing `SelectableList` and `ConfirmDialog` components and require no architectural changes:

**Team sub-menu:** Select a Pokemon → sub-menu (SUMMARY, SWITCH, ITEM, CANCEL + field moves). Opens a new SelectableList overlay. SWITCH triggers a "select target" mode.

**Inventory sub-menu:** Select an item → sub-menu (USE, GIVE, TOSS, CANCEL; REGISTER for Key Items). USE on a healing item → select target Pokemon from party. TOSS → confirmation dialog. REGISTER → marks item for SELECT-button shortcut on overworld.

**Item registration:** Key Items can be registered for quick-use via shortcut button on the overworld (e.g., Bicycle, Fishing Rod).

---

## Testing Strategy

### Unit Tests

**SelectableSlot:**
- `setSelected(true)` shows arrow "<", `setSelected(false)` shows ""
- `setLabel("text")` updates label UIText
- `setVisible(false)` disables the slot's GameObject

**SelectableList:**
- Navigation: movePrimary(-1) moves up, movePrimary(+1) moves down
- Delegates to slots: only the selected slot has `setSelected(true)`
- Boundary: selection doesn't go below 0 or above itemCount-1 (wrapAround=false)
- Wrap-around: when enabled, wraps from last→first and first→last
- Empty list: movePrimary, select, cancel don't crash when items is empty
- Scrolling: scrollOffset adjusts when selection moves past visible range
- Scrolling edge: maxVisibleItems equals items.size() → scrollOffset stays 0
- Scrolling visibility: slots beyond visible range have `setVisible(false)`
- Callbacks: select() fires onSelect with correct index
- Callbacks: cancel() fires onCancel
- Callbacks: movePrimary fires onSelectionChanged only when selection actually changes
- Active flag: no callbacks fire when inactive
- Orientation: HORIZONTAL mode works identically to VERTICAL
- setItems() resets selectedIndex to 0 and scrollOffset to 0
- getItemCount() returns correct value

**PokemonSlot:**
- `setPokemon()` populates name, level, status, HP bar, HP text
- `setEmpty()` shows "───", hides HP bar/level/status/icon
- HP bar color: >50% green, 20-50% yellow, <20% red
- HP bar fill amount: correct ratio of currentHp/maxHp
- Status label: correct abbreviation + color, hidden when NONE
- Fainted Pokemon (isAlive=false) shows "FNT" in red
- HP > maxHP: hpPercent clamped to 1.0 (no bar overflow)
- Negative HP: hpPercent clamped to 0.0
- maxHp = 0: hpPercent is 0 (no division by zero)
- Held item: "♦" shown when holding, empty when not

**InventorySlot:**
- `setQuantity(5)` shows "x5"
- `setQuantity(1)` shows "" (single items don't show quantity)
- `clearQuantity()` hides quantity text

**MenuManager:**
- State transitions: CLOSED → OPENING → MAIN_MENU → TEAM_SCREEN → MAIN_MENU → CLOSING → CLOSED
- InputMode: verify OVERWORLD when CLOSED, MENU when any other state
- IPausable: verify pause called on open, resume on close
- Animation guard: no input processed during OPENING / CLOSING / SAVING
- Active list: only one SelectableList is active at any time
- Rapid open/close: openMenu() then closeMenu() before tween completes → ends in CLOSED
- Quit default: confirm dialog defaults to NO (index 1)
- Save default: confirm dialog defaults to YES (index 0)
- Tween callback after state reset: stale onComplete does not corrupt state
- Save failure: SaveManager.save() returns false → state transitions to SAVE_FAILED, error dialog shown
- SAVE_FAILED: OK/MENU/B dismisses → returns to MAIN_MENU
- SAVING watchdog: if callback doesn't fire within 2s → auto-transition to SAVE_FAILED
- Save overwrite: if save exists → CONFIRM_OVERWRITE shown first → YES → CONFIRM_SAVE
- SAVING → SAVE_SUCCESS transition: input is re-enabled (state no longer in blocked set)
- Conditional menu options: no Pokedex → 5 items, with Pokedex → 6, with Pokedex + Pokemon → 7
- onDestroy while open: kills tweens, resumes pausables, resets InputMode to OVERWORLD
- MenuScreen delegation: showScreen() calls screen.show() + onActivated(); hideScreen() calls screen.hide()

**TeamScreenController:**
- `refresh()` delegates to `PokemonSlot.setPokemon()` for populated slots
- `refresh()` delegates to `PokemonSlot.setEmpty()` for empty slots
- Empty party (0 Pokemon): all 6 slots show "───"

**InventoryScreenController:**
- Category filtering: only non-empty categories appear
- Category cycling: wraps around through available categories
- Empty inventory (all categories empty): shows "No items"
- Description updates on selection change
- Money display: shows correct amount from PlayerInventoryComponent

**PokemonMenuUIBuilder:**
- All componentKeys are unique (no duplicates in built hierarchy)
- All componentKeys start with "menu_" prefix

**Integration Tests:**
- Full menu cycle: open → navigate → save → dismiss → close → verify InputMode is OVERWORLD
- Scene transition while menu open: MenuManager.onDestroy() restores InputMode and resumes pausables

### Manual Tests

- Press ESCAPE in overworld → menu slides in from right
- Navigate UP/DOWN between options → arrow moves correctly
- Select CLOSE → menu slides out, overworld resumes
- Press ESCAPE on main menu → same as CLOSE
- Select POKeMON → team screen with party data (or empty state)
- Press ESCAPE on team screen → returns to main menu
- Select BAG → inventory screen with items (or empty state)
- Press LEFT/RIGHT → category switches (only non-empty categories)
- Scroll through items → ▲/▼ indicators appear at boundaries
- Select SAVE → save info (player name, time) + confirmation dialog
- YES → "Saving..." pause → "Game saved!" message → OK returns to menu
- Select QUIT → confirmation dialog with NO selected by default
- Navigate to YES and confirm → returns to title screen
- Open menu → close → open → close rapidly → no visual glitches
- Open menu during NPC dialogue → menu should NOT open (InputMode blocks it)
- Verify money display updates on inventory screen
- Verify HP bars show correct colors at different HP percentages
- Gamepad navigation: D-pad + A/B buttons work for all menu screens
- Save when save file exists → overwrite confirmation appears
- Fainted Pokemon in party → shows "FNT" in red with 0 HP
- Pokemon holding item → "♦" indicator visible
- Very long player name → text doesn't overflow menu panel
- Maximum money value → text fits in display area
- 150+ Pokedex entries → scrolling is smooth with held direction

---

## Migration Notes

### Replacing PlayerPauseUI

`PlayerPauseUI` currently handles:
1. Listen to MENU button in OVERWORLD mode
2. Toggle a pause panel with tween animation

`MenuManager` subsumes all of this functionality plus adds the full menu system. Migration:
1. Remove `PlayerPauseUI` component from player prefab
2. Add `MenuManager` component to player prefab
3. Add nested prefab node referencing `pokemon_menu_ui.prefab.json` to player prefab
4. The existing pause panel GameObjects (PauseMenuCanvas and children) in `overworld_player.prefab.json` are replaced by the nested menu prefab

### InputMode.MENU

The `MENU` value needs to be added to the `InputMode` enum. Since `PlayerInput` already uses enum-based mode gating, the existing OVERWORLD and DIALOGUE callbacks will automatically be blocked when the mode is set to MENU. No changes needed in `PlayerDialogueManager` — it only listens for DIALOGUE mode.

The menu cannot open during DIALOGUE or BATTLE modes because `onMenu` is registered for `InputMode.OVERWORLD` only. No additional guard needed.
