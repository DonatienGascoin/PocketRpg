# Pokemon Menu System — Screen Designs

All screens are part of the `pokemon_menu_ui.prefab.json` nested prefab. ASCII mockups show the final rendered appearance.

---

## Main Menu

Slides in from the right side of the screen. Game continues rendering behind it (not paused visually, but IPausable stops logic).

```
Game World (continues rendering)
                                    ┌──────────────────┐
                                    │   < POKéDEX      │
                                    │     POKéMON      │
                                    │     BAG          │
                                    │     CARD         │
                                    │     SAVE         │
                                    │     QUIT         │
                                    │     CLOSE        │
                                    │                  │
                                    └──────────────────┘
```

**Layout specs:**
- Panel: `UIImage` SLICED, anchored TOP_RIGHT
- Width: 30% of screen, Height: auto (based on item count)
- Offset: starts off-screen right (+width), tweens to visible position
- Sort order: 15 (above dialogue at 10)
- Font: `fonts/Pokemon-Red.ttf`, size 20
- Arrow character: `<` (the font renders this as the correct selection arrow glyph)
- Background: 9-slice panel sprite
- Slide duration: 0.2s (fast, near-instant feel like original games)

**Navigation:**
- UP/DOWN: Move selection arrow
- INTERACT (Z/Enter): Select highlighted option
- MENU (Escape): Close menu (handled by SelectableList.onCancel → closeMenu)
- LEFT/RIGHT: No action on main menu

**Input auto-repeat:** For scrollable lists (inventory items, Pokedex species), holding a direction key triggers auto-repeat after a ~0.3s initial delay at ~8 moves/second. Short lists (main menu, confirmation dialogs) use single edge-triggered input only.

---

## Pokemon Team Screen

Full-screen overlay showing party Pokemon. Populated slots show name, level, HP bar, and status. Empty slots are visually dimmed (no border, single-line "---" at reduced alpha). An optional icon slot is reserved for future Pokemon sprites.

Each populated slot is a **nested prefab instance** of `pokemon_slot.prefab.json`, enabling reuse in battle UI, PC storage, etc.

```
┌────────────────────────────────────────────────────────┐
│                     POKéMON                            │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  < [icon] PIKACHU            Lv.25    45/60     │  │
│  │           HP ████████████████░░░░░░░░           │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │    [icon] BULBASAUR    PSN   Lv.12    22/40     │  │
│  │           HP █████████░░░░░░░░░░░░░░            │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │    [icon] CHARMANDER         Lv.15    35/35     │  │
│  │           HP ████████████████████████            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│     ───                                                │
│     ───                                                │
│     ───                                                │
│                                                        │
│                                          BACK          │
└────────────────────────────────────────────────────────┘
```

**Layout specs:**
- Full-screen background panel (UIImage SLICED, 100% width/height)
- Title text: "POKeMON" centered at top
- 6 Pokemon slots, each a `pokemon_slot.prefab.json` nested instance containing two rows:
  - **Row 1:** Arrow (<) → Icon slot (8%, placeholder Pokeball, future: species icon) → Name (30%) → Status label (8%, "PSN"/"PAR"/etc or "FNT" if fainted) → Held item indicator (4%, "♦" if holding) → Level (12%, "Lv.XX") → HP text (10%, "current/max")
  - **Row 2:** HP bar track (dark background) + HP bar fill (UIImage FILLED horizontal)
- Populated slots: full bordered box with all elements visible
- Empty slots: collapsed to single line, dimmed, no border — "───" text at 50% alpha
- BACK option at bottom (7th selectable item)

**HP Bar Design:**
- Two layers: dark background track (always visible) + colored fill on top
- Fill amount = `currentHp / maxHp`
- Color thresholds (matching original Pokemon games):
  - Green `rgb(0.2, 0.8, 0.2)`: > 50% HP
  - Yellow `rgb(0.9, 0.8, 0.1)`: 20–50% HP
  - Red `rgb(0.9, 0.2, 0.2)`: < 20% HP

**Status label:** Shows status abbreviation next to level. Hidden when NONE. Colors: PSN=purple, PAR=yellow, SLP=gray, BRN=orange, FRZ=blue. Fainted Pokemon (currentHp == 0) show "FNT" in red — checked separately from StatusCondition since FAINT is not a status condition but a derived state.

**Held item indicator:** A small icon (or "♦" text marker) appears next to Pokemon holding items. Located after the status label area. Hidden when no item is held. Future: show actual item icon sprite.

**Navigation:**
- UP/DOWN: Move between Pokemon slots (and BACK)
- MENU/B (Escape): Return to main menu (same as BACK)
- INTERACT: No action for now (future: open Pokemon summary/switch sub-menu)
- LEFT/RIGHT: No action (reserved for future switch/reorder)

**Future: Pokemon sub-menus** — Selecting a Pokemon will open a context sub-menu: SUMMARY, SWITCH, ITEM, CANCEL (plus field moves like CUT, FLY when learned). This reuses `SelectableList`. The current plan leaves INTERACT as no-action; the sub-menu will be added in a post-MVP phase.

---

## Inventory Screen

Full-screen overlay with category tabs and scrollable item list. Shows money in header. Only categories with items are shown in the cycle (empty categories are skipped).

```
┌────────────────────────────────────────────────────────┐
│                       BAG               $3000          │
│                                                        │
│     ◀  ITEMS  ▶   2/4                                 │
│  ┌──────────────────────────────────────────────────┐  │
│  │                                                  │  │
│  │   < POTION                              x5      │  │
│  │     SUPER POTION                        x2      │  │
│  │     ANTIDOTE                            x3      │  │
│  │     REVIVE                              x1      │  │
│  │     FULL RESTORE                        x1      │  │
│  │                                                  │  │
│  │                                                  │  │
│  │                                               ▼  │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Restores 20 HP to one POKéMON.                  │  │
│  └──────────────────────────────────────────────────┘  │
│                                          BACK          │
└────────────────────────────────────────────────────────┘
```

**Layout specs:**
- Full-screen background panel
- Title: "BAG" left-aligned, money display right-aligned ("$" + `PlayerData.money`)
- Category tab bar: Shows current category name with ◀ ▶ arrows and position indicator ("2/4")
  - Uses a HORIZONTAL `SelectableList` for category cycling
  - Only categories with at least 1 item are included (empty categories filtered out)
  - If no categories have items, show "No items" in the list area
- Item list panel: 8 visible slots (scrollable VERTICAL `SelectableList`)
  - Selection arrow (<) — 5% width
  - Item name — 65% width
  - Quantity ("x5") — 15% width, right-aligned
  - Scroll indicators: ▲ at top when `scrollOffset > 0`, ▼ at bottom when more items below (mandatory, not optional)
- Description panel at bottom: Fixed height 70px (~2-3 lines at size 18), word wrap enabled
  - Updates as selection changes via `onSelectionChanged` callback
  - Shows selected item's description from `ItemDefinition`
- BACK option: appended as the last item in the item `SelectableList` (not a separate slot). When BACK is selected, the description panel shows empty text. With `wrapAround = true`, scrolling past BACK wraps to the first item. BACK has no quantity display.

**Navigation:**
- LEFT/RIGHT: Switch category tab (routed to category HORIZONTAL SelectableList)
- UP/DOWN: Navigate item list (routed to item VERTICAL SelectableList)
- INTERACT: No action for now (future: USE/TOSS sub-menu)
- MENU/B: Return to main menu

**Future: Item registration** — Key Items can be "registered" for quick-use via a shortcut button on the overworld (e.g., Bicycle, Fishing Rod). The registration system is out of scope for this plan but the inventory screen should be aware that Key Items may have a REGISTER option in a future item sub-menu.

**Future: Item sub-menus** — Selecting an item will open a context sub-menu: USE, GIVE, TOSS, CANCEL (or REGISTER for Key Items). This reuses `SelectableList` + `ConfirmDialog` for TOSS confirmation. The current plan leaves INTERACT as no-action; the sub-menu will be added in a post-MVP phase.

**Input routing in MenuManager for INVENTORY_SCREEN state:**
```java
Direction dir = playerInput.getMovementDirectionUp();
if (dir == Direction.LEFT) categoryList.movePrimary(-1);
else if (dir == Direction.RIGHT) categoryList.movePrimary(+1);
else if (dir == Direction.UP) itemList.movePrimary(-1);
else if (dir == Direction.DOWN) itemList.movePrimary(+1);
```

---

## Confirmation Dialog — Save

Centered popup over the main menu. Shows game state summary before asking to save. The confirmation dialog is a **nested prefab instance** of `confirm_dialog.prefab.json`.

```
                ┌────────────────────────────┐
                │                            │
                │  PLAYER:  RED              │
                │  BADGES:  3               │
                │  POKéDEX: 23              │
                │  TIME:    12:34            │
                │                            │
                │  Would you like to save    │
                │  the game?                 │
                │                            │
                │       < YES                │
                │         NO                 │
                │                            │
                └────────────────────────────┘
```

Data sources: `PlayerData.playerName`, `SaveManager.getTotalPlayTime()`, badge count (from `PlayerData.badges.size()` when available), Pokedex caught count (from `PlayerData.pokedexCaught.size()` when available), current map/location name (when available). Shows available fields, hides unavailable ones.

**Overwrite Existing Save:**

If a save file already exists, an additional confirmation appears first:

```
                ┌────────────────────────────────┐
                │                                │
                │  There is already a save file. │
                │  Is it OK to overwrite?        │
                │                                │
                │       < YES                    │
                │         NO                     │
                │                                │
                └────────────────────────────────┘
```

Default selection: YES. This is intentionally YES (not NO) to match the original Pokemon games, where overwriting a save was the expected action. If YES, proceeds to the save info + confirmation. If NO or MENU/B, returns to main menu.

**Save Success Message:**

```
                ┌────────────────────────────┐
                │                            │
                │   Saving...                │
                │                            │
                └────────────────────────────┘

                        ↓ (0.5s delay)

                ┌────────────────────────────┐
                │                            │
                │     Game saved!            │
                │                            │
                └────────────────────────────┘
```

Two-stage feedback: "Saving..." for 0.5s, then "Game saved!" displayed briefly (1s), then auto-returns to main menu. INTERACT or MENU/B also dismisses immediately (no need to wait). No OK button — matches classic Pokemon auto-dismiss behavior.

**Save Error (if SaveManager.save() fails):**

```
                ┌────────────────────────────┐
                │                            │
                │  Save failed.              │
                │  Please try again.         │
                │                            │
                │       < OK                 │
                │                            │
                └────────────────────────────┘
```

---

## Confirmation Dialog — Quit

**Default selection is NO** (destructive action — prevents accidental data loss).

```
                ┌────────────────────────────┐
                │                            │
                │  Return to title screen?   │
                │  Unsaved progress will     │
                │  be lost.                  │
                │                            │
                │         YES                │
                │       < NO                 │
                │                            │
                └────────────────────────────┘
```

**Layout specs (all confirmation dialogs — from `confirm_dialog.prefab.json`):**
- Center-anchored panel, 40% width, height auto-sized
- Message text: word-wrapped, left-aligned, font size 18
- SelectableList with 2 slots (reuses same YES/NO or single OK)
- Background: same 9-slice panel sprite as main menu
- Main menu stays visible behind (dimmed at 50% alpha)

**Navigation:**
- UP/DOWN: Switch between options
- INTERACT: Confirm selection
- MENU/B: Cancel (equivalent to NO, or dismiss for OK-only)
