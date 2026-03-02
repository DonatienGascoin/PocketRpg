# Pokemon Menu System — Visual Design (from YELLOW Spritesheets)

## Art Style Summary

The YELLOW spritesheet set defines a **PokéGear-inspired** visual language with a distinctive palette and shapes:

- **Primary color**: Orange/yellow (#F0A000-ish and bright yellow)
- **Secondary color**: Light mint green (#C8E8B0-ish) for backgrounds
- **Accent**: Black for text panels and screen areas, dark olive (#808000-ish) for item slot backgrounds
- **Borders**: Angular with decorative diagonal cuts at corners — NOT round. Panels have a beveled/notched feel.
- **Font**: Circular pixel letters (from `LETTERS UI.png`) in orange-on-dark and dark-on-orange variants
- **Icons**: Pokeball-based navigation dots, weather/time icons (sun, moon, rain, cloud, snowflake), category icons (pokeball, TM disc, key, medicine bottle)

---

## Sprite Extraction Guide

### From `MENU UI.png`

This is the main spritesheet. It's **irregularly packed** — sprites overlap and vary in size. Approximate extraction regions:

| Sprite | Approximate Region | Size (est.) | Description |
|--------|-------------------|-------------|-------------|
| Main menu panel (light) | Top-left | ~200×220 | Light green panel with orange angular borders, notched top-left corner |
| Main menu panel (dark) | Top-center-right | ~200×220 | Same shape, black/dark background, orange accents |
| Pokedex panel (orange) | Bottom-center-left | ~180×200 | Orange body with small black screen, decorative borders |
| D-pad icon | Center area | ~40×40 | Orange cross directional pad |
| Pokeball dots (pagination) | Bottom of panels | ~8×8 each | Small orange filled circles, used as page/scroll indicators |
| Weather icons | Scattered mid-area | ~20×20 each | Sun, crescent moon, raindrop, cloud, snowflake — all orange |
| Action icons (right side) | Far-right column | ~24×24 each | Save (floppy), load, settings (gear), close (X) icons in orange/dark variants |
| Navigation triangles | Near Pokedex panel | ~12×12 | Left/right arrows (◁ ▷) and up/down (△ ▽) |
| PAGE / AREA / CRY buttons | Center-bottom | ~48×16 each | Small labeled button sprites for Pokedex detail |
| Day-of-week slots (MON-SUN) | Bottom-left | ~48×16 each | Team list slot labels with colored status dots |
| Status dots | Next to day labels | ~6×6 | Green (+), red (P), orange (F) status indicators per slot |
| Scroll indicators | Left edges | ~8×8 | Small up/down scroll markers |

### From `POKEMON_MENU-Sheet-Sheet.png`

Four panel background variants for Pokemon team slots:

| Sprite | Position | Description |
|--------|----------|-------------|
| Panel variant A | Top-left (~200×170) | Gray fill, orange tab on top-LEFT edge |
| Panel variant B | Top-right (~200×170) | Gray fill, orange tab on top-RIGHT edge |
| Panel variant C | Bottom-left (~200×170) | Gray fill, black triangle accent on bottom-left |
| Panel variant D | Bottom-right (~200×170) | Gray fill, orange bar on top + black triangle on bottom-left |
| Thin separator (dark) | Bottom, left | ~200×3 | Dark green thin horizontal line |
| Thin separator (orange) | Bottom, right | ~200×3 | Orange thin line with pokeball end cap |

**Usage:** Panel D (orange bar + triangle) is likely the selected/active slot. Panel A or C for unselected slots. The tab position (left vs right) may alternate for visual rhythm in stacked slots.

### From `BAG UI.png`

Seven frames of the backpack sprite (animation or pocket variants):

| Frame | Position (left to right) | Size (est.) | Description |
|-------|-------------------------|-------------|-------------|
| 1 (detailed) | Far left | ~90×90 | Full detail: goggles, clasp, pockets, black outline |
| 2–4 | Center-left | ~80×80 each | Bright yellow variants, slight pose differences (no outline) |
| 5–7 | Center-right to far right | ~80×80 each | Darker orange variants, visible clasp and stitching |

**Usage:** Frame 1 (detailed, with black outline) as the static bag icon. Frames 2–7 may be pocket-specific bag variants (the bag changes color/detail per inventory category — a Gen 3 FRLG convention). Or an idle animation.

### From `BAG EXE.png` (Mockup — Bag Screen with Items)

Full layout mockup showing a populated inventory screen. Key regions:

| Element | Position | Description |
|---------|----------|-------------|
| Category tab bar | Top of left panel, ~4 icons | Icons: Pokeball, TM disc, Key, Medicine bottle — horizontal row |
| Item grid | Left panel body, 4×4 | Dark olive squares, ~40×40 each, with spacing |
| Selected item highlight | Grid position (0,2) | White/light border around selected cell |
| Item icon | Inside selected cell | Small orange capsule/berry icon |
| Quantity "99" | Bottom-right of item cell | Orange pixel digits |
| Scrollbar | Far left edge | Vertical track with square handle at top/bottom, dotted line between |
| Bag sprite | Right side, large | ~120×120 detailed backpack with goggles |
| Name plate area | Top-right | Parallelogram-shaped orange/striped bar — item name display |
| Description area | Bottom-right | Two horizontal striped orange bars — item description text |

### From `BAG1.png` (Mockup — Empty Bag Screen)

Same layout as BAG EXE, but empty. Confirms:
- Category icons clearly visible as separate icon sprites
- Orange solid fill when no items (no grid lines visible on empty)
- Scroll handle positions: top = small square, bottom = small square, dotted line connecting them

### From `INVENTORY.png`

Individual inventory UI elements:

| Sprite | Position | Size (est.) | Description |
|--------|----------|-------------|-------------|
| Item slot tile (×20) | 5×4 grid, main area | ~40×40 each | Dark olive/brown square — alternating slight shade for visual rhythm |
| Selected slot highlight | Top-right area | ~44×44 | Light green/white bordered square (selection indicator) |
| Empty slot | Right, below highlight | ~40×40 | Same olive but slightly lighter |
| Scroll handle | Right side, middle | ~8×24 | Small orange vertical capsule |
| Item capsule icon | Right side, bottom | ~24×24 | Orange capsule item sprite |
| Quantity background | Bottom-right | ~30×14 | Dark square with "99" in orange digits |
| Digit sprites (0–9) | Bottom two rows | ~12×14 each | Individual pixel-art digits in orange |

### From `BATTLE SYSTEM UI.png`

HP bar and status elements:

| Sprite | Position (top to bottom) | Description |
|--------|------------------------|-------------|
| HP bar (green, full) | Row 1 | "HP" label + green fill bar + black track |
| HP bar (yellow, mid) | Row 2 | "HP" label + yellow fill bar + black track |
| HP bar (red, low) | Row 3 | "HP" label + red fill bar + black track |
| HP bar (empty) | Row 4 | "HP" label + black track only |
| Standalone green fill | Row 5 | Just the green bar (no label, for overlaying) |
| Standalone yellow fill | Row 6 | Just the yellow bar |
| Standalone red fill | Row 7 | Just the red bar |
| EXP bar (blue) | Row 8 | Blue fill bar + black track (thinner than HP) |
| Lightning bolt icon | Right of EXP | Small orange/yellow bolt |
| Ring indicators (3×3 grid) | Bottom rows | Green ring, orange ring, dark ring — 3 size variants each. Likely catch rate / type effectiveness indicators |

### From `hpBar.png`

Standalone HP bar fills (simpler, larger variants):

| Sprite | Description |
|--------|-------------|
| "HP" text label | Red/orange "HP" text |
| Green bar | Full HP fill |
| Yellow bar | Medium HP fill |
| Red bar | Low HP fill |

### From `LETTERS UI.png`

Complete character set in two color schemes (orange-on-dark, dark-on-orange):

- **Alphabet**: A–Z (each ~20×20 in a circular badge)
- **Punctuation/Special**: Period, comma, question mark, exclamation, colon, parentheses, plus, minus, hash
- **Digits**: 0–9 (same circular badge style)
- **Function keys**: F1–F7, FD, FA, FI, FR, FL, FR (likely button prompts)
- **Modifier keys**: DEL, ALT, TAB, special icons

**Note:** These circular letter badges are the UI font, NOT the game text font. They may be used for button prompts, labels, or decorative headers. Regular game text uses `fonts/Pokemon-Red.ttf`.

### From `MAP UI.png`

Panel frame styles and map elements (less relevant to menu, but useful):

| Sprite | Description |
|--------|-------------|
| Yellow-green frame | Dashed inner border, solid outer — used for selection/focus states? |
| Blue cross-hatch frame | Striped fill pattern — used for inactive/locked areas? |
| Dark olive frame | Solid border, yellow dashed inner — alternative panel style |
| Building sprites | Various small orange building/house icons for town map |
| Connection nodes | Orange circles connecting buildings — route indicators |

---

## Screen Designs Based on Spritesheets

### Main Menu

Based on `MENU UI.png` — the right column (POKéDEX, BAG, GEAR, ID, SAVE, OPTIONS):

```
                        ┌─────────────────────────┐
                        │╲                        │
                        │  ┌───────────────────┐  │
                        │  │ ■■  (mini screen) │  │
                        │  └───────────────────┘  │
                        │                         │
                        │    < POKéDEX            │
                        │      POKéMON            │
                        │      BAG                │
                        │      CARD               │
                        │      SAVE               │
                        │      QUIT               │
                        │      CLOSE              │
                        │                         │
                        │    ● ● ○                │
                        └─────────────────────────┘
```

**Sprite composition:**
- **Background panel**: Light green panel from `MENU UI.png` (top-left sprite). Notched diagonal cut at top-left corner. Orange beveled border.
- **Mini screen**: Small black rectangle near the top — shows a tiny preview (party lead Pokemon sprite, or decorative). Purely cosmetic for now.
- **Menu text**: Rendered via `fonts/Pokemon-Red.ttf` (NOT the circular letter sprites — those are for button prompts).
- **Selection arrow**: `<` character rendered in font (the font converts it to a proper arrow glyph).
- **Pagination dots**: Pokeball dot sprites from bottom of `MENU UI.png` — filled (●) for current page, empty (○) for other. Cosmetic — single page, always "page 1 of 1".
- **Panel anchoring**: Right side of screen. Slides in from right on open.

**Layout (UITransform):**
- Panel: anchored right (anchorMin 0.6, 0), sized to content
- Mini screen: 70% width, near top, ~30px height
- Menu items: left-aligned with ~20px padding, 24px line height
- Dots: centered at bottom, 8px spacing

---

### Team Screen (POKéMON)

Based on `MENU UI.png` bottom-left (MON–SUN slots) + `POKEMON_MENU-Sheet-Sheet.png` panels + `BATTLE SYSTEM UI.png` HP bars:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔  │    │
│  │  < PIKACHU          ♂   Lv.25    HP ████████░░  52/60  │    │
│  │▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▲ │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔  │    │
│  │    CHARMANDER              Lv.18    HP ██████░░░  34/48 │    │
│  │  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁  │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔  │    │
│  │    BULBASAUR         PSN   Lv.22    HP ██░░░░░░  11/55 │    │
│  │  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁  │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │                                                         │    │
│  │    ─── ─── ─── ───                                      │    │
│  │                                                         │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │                                                         │    │
│  │    ─── ─── ─── ───                                      │    │
│  │                                                         │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │                                                         │    │
│  │    ─── ─── ─── ───                                      │    │
│  │                                                         │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │    < BACK                                               │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ══════════════════════════════════════════════════●             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Sprite composition:**
- **Outer frame**: Full-screen light green background (same mint green as main menu panel background).
- **Slot backgrounds**: From `POKEMON_MENU-Sheet-Sheet.png`. Each Pokemon slot uses one of the 4 panel variants:
  - **Selected slot** (slot 1): Panel variant D (orange bar on top + triangle at bottom). The orange bar acts as a visual "active tab" indicator.
  - **Unselected populated slots**: Panel variant A or C (gray fill, subtle corner accent). Alternating left/right tab position for visual rhythm.
  - **Empty slots**: Same panel but dimmed (lower alpha or darker variant).
  - **BACK slot**: Plain — no panel background, just text.
- **HP bars**: From `BATTLE SYSTEM UI.png`. Two-layer:
  - Black track (from "HP bar empty" sprite)
  - Color fill overlay (green/yellow/red sprites, scaled to HP ratio)
  - "HP" text label sprite from `hpBar.png`
- **Status text**: "PSN", "FNT", etc. rendered in font with status color.
- **Bottom separator**: The orange line with pokeball end cap from `POKEMON_MENU-Sheet-Sheet.png` — decorative footer.

**Layout (UITransform):**
- Full-screen panel
- 6 Pokemon slots: each ~14% screen height, stacked vertically
- BACK slot: ~8% screen height at bottom
- Separator line: anchored bottom, 3px height
- Each slot internally: Name (40% width), Status (10%), Level (15%), HP bar (25%), HP text (10%)

---

### Bag/Inventory Screen

Based on `BAG EXE.png`, `BAG1.png`, `INVENTORY.png`:

```
┌─────────────────────────────────────────────────────────────────┐
│                                    ╱═══════════════════╲        │
│  ┌──────────────────────────────┐ ╱  POTION             ╲       │
│  │  ⊕    ⊘    🔑    🧴         │ ═══════════════════════════    │
│  │╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌│                                │
│  │                              │                                │
│  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │      ┌──────────────┐         │
│  │ │    │ │    │ │    │ │    │ │      │              │         │
│  │ └────┘ └────┘ └────┘ └────┘ │      │  [backpack   │         │
│□ │                              │      │   sprite]    │         │
│| │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │      │              │         │
│| │ │    │ │    │ │    │ │    │ │      └──────────────┘         │
│| │ └────┘ └────┘ └────┘ └────┘ │                                │
│| │                              │ ═══════════════════════════    │
│| │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │ ╲  Restores 20 HP to one  ╱  │
│| │ │◖99 │ │    │ │    │ │    │ │  ╲  POKéMON.              ╱   │
│| │ └────┘ └────┘ └────┘ └────┘ │   ═══════════════════════      │
│□ │                              │                                │
│  │ ┌────┐ ┌────┐ ┌────┐ ┌────┐ │                                │
│  │ │    │ │    │ │    │ │    │ │                                │
│  │ └────┘ └────┘ └────┘ └────┘ │                                │
│  │                              │                                │
│  └──────────────────────────────┘                                │
│                                                                  │
│   ● ○ ○ ○                                              BACK     │
└──────────────────────────────────────────────────────────────────┘
```

**Sprite composition:**
- **Left panel (item grid area)**: Orange background. Category tab bar at top with 4 icon buttons. 4×4 grid of item slots below.
  - **Category icons** (from `BAG EXE.png` top-left of orange panel):
    - Pokeball icon = BALLS category
    - Disc/TM icon = TMs & HMs category
    - Key icon = KEY ITEMS category
    - Medicine bottle icon = ITEMS category
  - **Item slots**: Dark olive squares from `INVENTORY.png`. Selected slot has a light green highlight border (also from `INVENTORY.png`).
  - **Item icon**: Inside slot cell (e.g., the orange capsule from `INVENTORY.png`).
  - **Quantity digits**: Bottom-right corner of cell, using digit sprites from `INVENTORY.png` (e.g., "99").
- **Scrollbar** (left edge of item panel): Vertical track with two small square handles (top and bottom) connected by a dotted line. From `BAG1.png` left edge.
- **Right side**:
  - **Background**: Light mint green (same as main menu background color).
  - **Item name plate** (top): Parallelogram-shaped bar with diagonal striped orange pattern (from `BAG EXE.png` top-right). Text rendered over it.
  - **Bag sprite** (center-right): Large backpack from `BAG UI.png` frame 1 (detailed with outline). May change appearance per category if using frames 5–7 (color shift for different pockets).
  - **Description plate** (bottom): Two horizontal bars with diagonal striped orange pattern (from `BAG EXE.png` bottom-right). 2–3 lines of wrapped description text.
- **Bottom bar**: Category pagination dots (pokeball dots, one per category — filled = current). BACK option at far right.

**Layout (UITransform):**
- Left panel: anchorMin (0, 0.08) to anchorMax (0.48, 0.95)
- Category tabs: top of left panel, ~30px height, 4 icons equally spaced
- Item grid: below tabs, 4 columns × 4 rows, ~40px cells with 4px spacing
- Scrollbar: left edge of item grid, 8px wide
- Name plate: top-right, parallelogram, ~50px height
- Bag sprite: center-right, ~120×120
- Description plate: bottom-right, ~60px height, 2 lines
- Pagination dots + BACK: anchored bottom, full width

**Grid navigation:**
- UP/DOWN: Move through rows (4 items per row)
- LEFT/RIGHT at category tabs: Switch category
- The grid is the `SelectableList` with `maxVisibleItems = 16` (4×4). If more items, scroll the grid and update scrollbar handle position.

**Note — Grid vs List deviation:** The spritesheets show a **4×4 grid** layout for items, NOT a vertical list. This is a significant visual deviation from the current `screen-designs.md` (which describes a vertical list). The grid matches the actual spritesheet mockup and should be the canonical layout. Grid navigation: LEFT/RIGHT moves between columns within a row, UP/DOWN moves between rows.

---

### Save Confirmation

Based on `MENU UI.png` panel elements — uses the same panel style as the main menu but smaller, centered:

```
                ┌─────────────────────────────────┐
                │╲                                │
                │                                  │
                │   RED            BADGES: 3       │
                │   PLAY TIME:     12:34           │
                │   POKéDEX:       23              │
                │                                  │
                │   Would you like to save         │
                │   the game?                      │
                │                                  │
                │         < YES                    │
                │           NO                     │
                │                                  │
                └─────────────────────────────────┘
```

**Sprite composition:**
- **Panel**: Light green panel with orange border (same as main menu panel sprite, but used at a smaller centered size). The diagonal notch at top-left is the signature visual element.
- **Text**: `fonts/Pokemon-Red.ttf` for all text.
- **Selection arrow**: Same `<` as main menu.

---

### Quit Confirmation

Same panel style, smaller:

```
                ┌─────────────────────────────────┐
                │╲                                │
                │                                  │
                │   Return to title screen?        │
                │                                  │
                │         < NO                     │
                │           YES                    │
                │                                  │
                └─────────────────────────────────┘
```

---

### Trainer Card (CARD)

Based on `MENU UI.png` dark variant panel (top-right area — orange-accented panel with black screen):

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                       TRAINER CARD                              │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │╲                           ╱│                             │  │
│  │  ┌─────────────┐          │ │    NAME:    RED             │  │
│  │  │             │          │ │    MONEY:   $3,000          │  │
│  │  │  [trainer   │          │ │                             │  │
│  │  │   sprite]   │          │ │    POKéMON: 3               │  │
│  │  │             │          │ │    (in party)               │  │
│  │  └─────────────┘          │ │                             │  │
│  │                           │ │    PLAY TIME:  12:34        │  │
│  │                           │ │                             │  │
│  │  ──────────────────────── │ │                             │  │
│  │         BADGES            │ │                             │  │
│  │                           │ │                             │  │
│  │  ┌──┐ ┌──┐ ┌──┐ ┌──┐    │ │                             │  │
│  │  │● │ │● │ │● │ │  │    │ │                             │  │
│  │  └──┘ └──┘ └──┘ └──┘    │ │                             │  │
│  │  ┌──┐ ┌──┐ ┌──┐ ┌──┐    │ │                             │  │
│  │  │  │ │  │ │  │ │  │    │ │                             │  │
│  │  └──┘ └──┘ └──┘ └──┘    │ │                             │  │
│  │▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁│ │                             │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│                                                        BACK     │
└─────────────────────────────────────────────────────────────────┘
```

**Sprite composition:**
- **Outer background**: Full-screen light mint green.
- **Card panel**: Uses the **dark variant panel** from `MENU UI.png` (top-center-right sprite — black background with orange accents). This gives the card a "device screen" look, as if displayed on a PokéGear-like device.
- **Left half of card**: Trainer sprite area (placeholder) + badge grid (2 rows × 4 badges).
  - Badge slots use the **ring indicator sprites** from `BATTLE SYSTEM UI.png` (bottom rows) — orange filled ring for earned, dark/empty ring for unearned.
- **Right half of card**: Text info in orange font on dark background.
- **Separator**: The orange thin line from `POKEMON_MENU-Sheet-Sheet.png` between info and badges.
- **Diagonal notches**: Both top-left and top-right of the card panel have the signature angular cuts.

---

### Pokedex — Species List

Based on `MENU UI.png` Pokedex panel (bottom-center) + navigation elements:

```
┌─────────────────────────────────────────────────────────────────┐
│                        POKéDEX                                  │
│                                                                 │
│     SEEN: 45    CAUGHT: 23                                      │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │╲                                                       ▲  │  │
│  │   < 001  BULBASAUR                                  ●  │  │  │
│  │     002  IVYSAUR                                    ●  │  │  │
│  │     003  VENUSAUR                                   ○  │  │  │
│  │     004  CHARMANDER                                 ●  │  │  │
│  │     005  CHARMELEON                                 ○  │  │  │
│  │     006  CHARIZARD                                  ─  │  │  │
│  │     007  SQUIRTLE                                   ●  │  │  │
│  │     008  ???                                        ─  │  │  │
│  │     009  ???                                        ─  │  │  │
│  │     010  CATERPIE                                   ○  ▼  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│   ◁  ●●○  ▷                                       BACK         │
└─────────────────────────────────────────────────────────────────┘
```

**Sprite composition:**
- **Main list panel**: Orange body panel from `MENU UI.png` Pokedex area. Distinctive orange fill with angular borders.
- **Status icons**: Pokeball dots from `MENU UI.png`:
  - ● (filled pokeball) = Caught
  - ○ (empty circle) = Seen
  - ─ (dash) = Unseen
- **Navigation arrows**: Triangle sprites from `MENU UI.png` (◁ ▷ for area switching, ▲ ▼ for scroll).
- **Pagination dots**: Pokeball dots at bottom.

---

### Pokedex — Species Detail

Based on `MENU UI.png` — uses dark panel variant as overlay + PAGE/AREA/CRY buttons:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│     No.001  BULBASAUR                                           │
│                                                                 │
│     ┌──────────────────┐                                        │
│     │╲                 │      Type:  GRASS                      │
│     │                  │                                        │
│     │   [sprite]       │      Height: ???                       │
│     │                  │      Weight: ???                        │
│     │                ▲ │                                        │
│     └──────────────────┘                                        │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  A strange seed was planted on its back at                │  │
│  │  birth. The plant sprouts and grows with                  │  │
│  │  this POKéMON.                                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│     [PAGE]   [AREA]   [CRY]                       BACK          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Sprite composition:**
- **Sprite box**: Dark variant panel (black background, orange border). Shows the Pokemon front sprite.
- **Description box**: Orange panel, same as Pokedex list panel style.
- **Button sprites**: PAGE, AREA, CRY buttons from `MENU UI.png` (small labeled rectangles). Only BACK is functional initially.
- **Background**: Light mint green full-screen.

---

## Palette Reference

Extracted from spritesheets:

| Name | Hex (approx.) | Usage |
|------|---------------|-------|
| Orange primary | `#F0A000` | Panel borders, icons, accents |
| Bright yellow | `#F8D030` | Bag sprite, highlights |
| Mint green (BG) | `#C8E8B0` | Background panels, light areas |
| Black | `#000000` | Text, screen areas, dark panels |
| Dark olive (slots) | `#808000` | Item grid slot backgrounds |
| Green (HP high) | `#40C040` | HP bar >50% |
| Yellow (HP mid) | `#F8C030` | HP bar 20-50% |
| Red (HP low) | `#F03030` | HP bar <20% |
| Blue (EXP) | `#4080F0` | EXP bar fill |
| White/Light | `#F0F0E8` | Selected highlights, text on dark |

---

## Key Visual Differences from `screen-designs.md`

| Aspect | screen-designs.md | YELLOW Spritesheets |
|--------|-------------------|---------------------|
| **Inventory layout** | Vertical scrollable list | **4×4 item grid** with icons + quantities |
| **Inventory navigation** | UP/DOWN only | **2D grid**: UP/DOWN/LEFT/RIGHT within cells |
| **Item display** | Text name + quantity | **Icon tile** with small quantity digit overlay |
| **Bag visual** | No bag sprite | **Large backpack sprite** on right side, may change per category |
| **Category tabs** | Text labels cycling with LEFT/RIGHT | **Icon buttons** in a horizontal row at top of grid |
| **Category indicator** | Position text "2/4" | **Filled/empty pokeball dots** at bottom |
| **Item name/description** | Below item list | On **right side** in parallelogram-shaped accent bars |
| **Scrollbar** | ▲/▼ arrows | **Track with handles** + dotted line (vertical left edge) |
| **Team slot style** | Flat text rows | **Paneled cards** with orange accent tabs per slot |
| **Trainer card** | Light panel | **Dark panel** (screen-on-device aesthetic) |
| **Confirmation dialogs** | Generic panels | Same panel as main menu (with signature diagonal notch) |
| **Mini screen on menu** | Not present | Small **black preview rectangle** at top of menu panel |

These differences should be reconciled when updating `screen-designs.md`. The grid-based inventory in particular is a significant UX change that affects `SelectableList` (which currently assumes 1D lists) — may need a `SelectableGrid` component or a 2D mode for `SelectableList`.

---

## Sprite Slicing Notes

Several sprites need 9-slice configuration for UI scaling:

| Sprite | Slice Strategy |
|--------|---------------|
| Main menu panel (light) | 9-slice: keep corners + diagonal notch fixed, stretch center fill |
| Main menu panel (dark) | Same as light variant |
| Orange Pokedex panel | 9-slice: keep top/bottom decorative borders fixed |
| Team slot panels (×4) | 9-slice: keep corner accents fixed, stretch center |
| Item slot tile | Simple stretch (uniform fill, no borders) |
| HP bar track | Horizontal stretch only (fixed height) |
| HP bar fills | Horizontal stretch only |
| Parallelogram name bar | **Cannot 9-slice** — use as-is or split into left cap + stretch middle + right cap |
| Diagonal striped bars | **Cannot 9-slice** — tile horizontally or use as fixed-width decorative elements |

**Irregularity warning:** The diagonal notch on panels means standard 9-slice won't preserve the notch correctly if the top-left corner region is too small. The slicing must include the full notch geometry in the top-left corner patch (approximately 20×20px).
