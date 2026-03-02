# Pokemon Menu System — Pokedex Screen Design

## Overview

The Pokedex screen is a full-screen overlay accessed from the main menu's POKeDEX option. It displays all known Pokemon species with a scrollable list, species detail view, and seen/caught tracking.

## Data Dependencies

### Exists Today

| Data | Source | Available |
|------|--------|-----------|
| All species definitions | `Pokedex.getAllSpecies()` → `Collection<PokemonSpecies>` | YES |
| Species name, type, base stats | `PokemonSpecies` fields | YES |
| Species sprite ID | `PokemonSpecies.spriteId` | YES |
| Owned Pokemon | `PlayerData.team` + `PlayerData.boxes` | YES |

### Needs to Be Created

| Data | Proposed Location | Description |
|------|-------------------|-------------|
| Seen species set | `PlayerData.pokedexSeen: Set<String>` | Species IDs the player has encountered (wild encounters, trainer battles) |
| Caught species set | `PlayerData.pokedexCaught: Set<String>` | Species IDs the player has caught or received |
| Pokedex numbering | `PokemonSpecies.dexNumber: int` | National/regional dex number for ordering |

**Note:** The seen/caught sets are write-through via `PlayerData` persistence. They should be updated by:
- **Seen**: Battle system adds species ID when a wild/trainer Pokemon is encountered
- **Caught**: `PlayerPartyComponent.addPokemon()` or `PokemonStorageComponent.store()` marks species as caught (implies seen)

For the initial implementation, **caught can be derived** from owned Pokemon (`team` + `boxes` unique species), but **seen requires a dedicated set** since Pokemon can be seen in battle without being caught.

---

## Screen Designs

### Pokedex — Species List

Full-screen overlay with a scrollable list of all species. Entries show dex number, name, and seen/caught status. Unknown species show only their dex number with "???".

```
┌────────────────────────────────────────────────────────┐
│                      POKéDEX                           │
│                                                        │
│   SEEN: 45    CAUGHT: 23                               │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │                                               ▲  │  │
│  │   < 001  BULBASAUR                        ●   │  │
│  │     002  IVYSAUR                          ●   │  │
│  │     003  VENUSAUR                         ○   │  │
│  │     004  CHARMANDER                       ●   │  │
│  │     005  CHARMELEON                       ○   │  │
│  │     006  CHARIZARD                        ─   │  │
│  │     007  SQUIRTLE                         ●   │  │
│  │     008  ???                               ─   │  │
│  │     009  ???                               ─   │  │
│  │     010  CATERPIE                         ○   │  │
│  │                                               ▼  │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│                                          BACK          │
└────────────────────────────────────────────────────────┘
```

**Legend:**
- `●` = Caught (Pokeball icon)
- `○` = Seen but not caught (empty circle)
- `─` = Not seen (no icon)
- `???` = Species not yet seen (name hidden)

**Layout specs:**
- Full-screen background panel (UIImage SLICED, 100% width/height)
- Title: "POKeDEX" centered at top
- Seen/Caught counters: below title, left-aligned
- Species list: 10 visible slots (scrollable VERTICAL `SelectableList`)
  - Dex number (10% width, right-aligned, "001" format)
  - Species name (65% width) — shows "???" if not seen
  - Status icon (5% width) — ● caught, ○ seen, ─ unseen
  - Scroll indicators: ▲/▼ mandatory
- BACK option at bottom
- Font: `fonts/Pokemon-Red.ttf`, size 20

**Navigation:**
- UP/DOWN: Scroll through species list
- INTERACT (Z/Enter): Open species detail view (only if seen — guard in onSelect callback checks `isSeen(speciesId)` before transitioning to POKEDEX_DETAIL; unseen entries are silently ignored)
- MENU/B (Escape): Return to main menu
- LEFT/RIGHT: No action (reserved for future area Pokedex switching)

### Pokedex — Species Detail

When the player selects a seen species, a detail overlay appears showing species information. If the species has been caught, full details are shown; if only seen, some info is hidden.

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│     No.001  BULBASAUR                                  │
│                                                        │
│     ┌────────────┐                                     │
│     │            │    Type:  GRASS                      │
│     │  [sprite]  │                                     │
│     │            │    Height: ???                       │
│     │            │    Weight: ???                       │
│     └────────────┘                                     │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  A strange seed was planted on its back at       │  │
│  │  birth. The plant sprouts and grows with         │  │
│  │  this POKéMON.                                   │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│     AREA     CRY                           BACK        │
│                                                        │
└────────────────────────────────────────────────────────┘
```

**Layout specs:**
- Full-screen overlay on top of the species list (list stays visible behind, dimmed)
- Header: Dex number + species name
- Sprite area: 25% width box, displays `front.png` variant of species sprite
  - If not caught: shows silhouette (sprite with black fill) — or just the sprite, implementation-dependent
- Info panel (right of sprite):
  - Type: `PokemonSpecies.type` name
  - Height/Weight: "???" placeholder (not yet in data model — future addition)
- Description panel: word-wrapped text, 3-4 lines
  - Requires `PokemonSpecies.description: String` field (needs to be added)
  - If field doesn't exist yet: show "No data available."
- Bottom options: AREA (future), CRY (future), BACK
  - Only BACK is functional initially

**Navigation:**
- MENU/B: Return to species list
- INTERACT on BACK: Return to species list
- LEFT/RIGHT: Cycle to prev/next species (within seen species only)
- AREA/CRY: No action (future)

---

## Data Model Additions

### `PokemonSpecies` additions

```java
// New fields on PokemonSpecies
private int dexNumber;           // National/regional dex number for ordering
private String description;      // Pokedex flavor text entry
// Future: private float height, weight;
```

### `PlayerData` additions

```java
// New fields on PlayerData
public Set<String> pokedexSeen = new HashSet<>();    // speciesIds
public Set<String> pokedexCaught = new HashSet<>();  // speciesIds (subset of seen)
```

### Helper methods

```java
// On PlayerPartyComponent or a new PokedexHelper utility
public static void markSeen(String speciesId);    // Adds to pokedexSeen
public static void markCaught(String speciesId);  // Adds to both seen + caught
public static int getSeenCount();
public static int getCaughtCount();
public static boolean isSeen(String speciesId);
public static boolean isCaught(String speciesId);
```

---

## Component Design

### `PokedexScreenController` (Component — on PokedexScreen GameObject)

```java
@ComponentMeta(category = "UI")
public class PokedexScreenController extends Component implements MenuScreen {

    // UI references (resolved by framework at load time via @ComponentReference)
    @ComponentReference(source = Source.KEY)
    private UIText seenCountText;

    @ComponentReference(source = Source.KEY)
    private UIText caughtCountText;

    @ComponentReference(source = Source.KEY)
    private List<UIText> dexNumberTexts;    // 10 visible slots

    @ComponentReference(source = Source.KEY)
    private List<UIText> speciesNameTexts;

    @ComponentReference(source = Source.KEY)
    private List<UIText> statusIconTexts;   // ● ○ ─

    // Detail view references
    @ComponentReference(source = Source.KEY)
    private UIText detailNameText;

    @ComponentReference(source = Source.KEY)
    private UIText detailTypeText;

    @ComponentReference(source = Source.KEY)
    private UIText detailDescriptionText;

    @ComponentReference(source = Source.KEY, required = false)
    private UIImage detailSpriteImage;

    @ComponentReference(source = Source.KEY)
    private UITransform detailPanelTransform;

    // State (transient — runtime only)
    private transient List<PokemonSpecies> allSpeciesSorted;
    private transient boolean showingDetail = false;

    public void refresh(PlayerData playerData);
    public void showDetail(int listIndex);
    public void hideDetail();
    public void cycleSpecies(int delta);     // LEFT/RIGHT in detail view
    private void updateListSlot(int slotIndex, PokemonSpecies species, PlayerData data);

    // ── MenuScreen interface ──

    @Override
    public void show(Runnable onComplete) { /* show pokedex panel, call onComplete */ }

    @Override
    public void hide(Runnable onComplete) { /* hide pokedex panel, call onComplete */ }

    @Override
    public void onActivated(MenuManager manager) {
        /* set manager's activeList to pokedex SelectableList, refresh species data */
    }

    @Override
    public void killTweens() { /* kill any active tweens on pokedex transforms */ }
}
```

### Component Keys

```java
public static final String KEY_POKEDEX_SCREEN = "menu_pokedex_screen";
public static final String KEY_POKEDEX_LIST = "menu_pokedex_list";
public static final String KEY_POKEDEX_SEEN_COUNT = "menu_pokedex_seen_count";
public static final String KEY_POKEDEX_CAUGHT_COUNT = "menu_pokedex_caught_count";
public static final String KEY_POKEDEX_DEX_NUM_PREFIX = "menu_pokedex_dex_num_";
public static final String KEY_POKEDEX_NAME_PREFIX = "menu_pokedex_name_";
public static final String KEY_POKEDEX_STATUS_PREFIX = "menu_pokedex_status_";
public static final String KEY_POKEDEX_DETAIL_PANEL = "menu_pokedex_detail_panel";
public static final String KEY_POKEDEX_DETAIL_NAME = "menu_pokedex_detail_name";
public static final String KEY_POKEDEX_DETAIL_TYPE = "menu_pokedex_detail_type";
public static final String KEY_POKEDEX_DETAIL_DESC = "menu_pokedex_detail_desc";
public static final String KEY_POKEDEX_DETAIL_SPRITE = "menu_pokedex_detail_sprite";
```

---

## MenuManager Integration

### State Machine Additions

```java
// New MenuState values
POKEDEX_LIST,        // Species list view
POKEDEX_DETAIL,      // Species detail overlay
```

### State Transitions

| From | Trigger | To | Action |
|------|---------|-----|--------|
| MAIN_MENU | Select POKeDEX | POKEDEX_LIST | Hide main panel, show Pokedex, refresh data, setActiveList(pokedexList) |
| POKEDEX_LIST | Select species (if seen) | POKEDEX_DETAIL | Show detail overlay, dim list, load species data |
| POKEDEX_LIST | BACK / MENU press | MAIN_MENU | Hide Pokedex, show main panel |
| POKEDEX_DETAIL | BACK / MENU press | POKEDEX_LIST | Hide detail, restore list |
| POKEDEX_DETAIL | LEFT/RIGHT | POKEDEX_DETAIL | Cycle to prev/next seen species. `MenuManager.routeDirectionInput()` has explicit POKEDEX_DETAIL case that calls `PokedexScreenController.cycleSpecies(delta)` instead of routing to a SelectableList. |

### Main Menu Update

POKéDEX is the first option in the main menu (conditional — only shown when obtained):

```
┌──────────────────┐
│   < POKéDEX      │
│     POKéMON      │
│     BAG          │
│     CARD         │
│     SAVE         │
│     QUIT         │
│     CLOSE        │
└──────────────────┘
```

The main menu auto-sizes based on item count, so adding one more option requires no layout changes.

---

## Implementation Notes

### Sorting

Species in the list are sorted by `dexNumber`. If `dexNumber` is not set (0), fall back to alphabetical by `speciesId`.

### Performance

`Pokedex.getAllSpecies()` returns all definitions. For large Pokedexes (150+), the list uses the same scrollable `SelectableList` with 10 visible slots. The species data is cached in `allSpeciesSorted` on refresh.

### Sprite Loading

Species sprites are loaded via `Assets.load("sprites/pokemon/" + species.spriteId + "/front.png", Sprite.class)`. The `front.png` variant is used for the detail view. If the sprite path doesn't exist, a placeholder Pokeball sprite is shown.

### List Slot Size Validation

`PokedexScreenController` holds `List<UIText>` references for 10 visible slots (`dexNumberTexts`, `speciesNameTexts`, `statusIconTexts`). The `updateListSlot(int slotIndex, ...)` method must guard against index-out-of-bounds if the prefab slot count doesn't match. Use `slotIndex < dexNumberTexts.size()` checks, or validate list sizes in `onStart()` and log a warning if mismatched.

### Graceful Degradation

If `PokemonSpecies.dexNumber` or `description` fields don't exist yet:
- `dexNumber = 0` → show "---" instead of "001"
- `description = null` → show "No data available."

If `PlayerData.pokedexSeen/pokedexCaught` don't exist yet:
- Derive caught from `team` + `boxes` unique species
- Treat all species as unseen (seen = caught only)
- Show warning in console: "Pokedex tracking not initialized"
