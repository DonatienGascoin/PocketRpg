# Pokemon Menu System — Player Card Screen Design

## Overview

The Player Card (Trainer Card) is a full-screen overlay accessed from the main menu. It displays the player's trainer profile: name, trainer ID, money, play time, Pokedex progress, and badges.

In Pokemon Gen 3 (FireRed/LeafGreen), the trainer card was a dedicated menu option showing player name, money, play time, Pokedex progress, and badges. This design follows that pattern with the "CARD" label.

## Data Dependencies

### Exists Today

| Data | Source | Available |
|------|--------|-----------|
| Player name | `PlayerData.playerName` | YES |
| Money | `PlayerData.money` | YES |
| Total play time | `SaveManager.getTotalPlayTime()` (float, seconds) | YES |
| Party Pokemon | `PlayerData.team` | YES |

### Needs to Be Created

| Data | Proposed Location | Description |
|------|-------------------|-------------|
| Trainer ID | `PlayerData.trainerId: int` | 5-digit numeric ID, generated once at new game (random 10000–99999) |
| Pokedex seen/caught counts | `PlayerData.pokedexSeen` / `pokedexCaught` | Sets of speciesIds (see Pokedex screen design) |
| Badge list | `PlayerData.badges: List<String>` | Badge IDs earned by defeating gym leaders |
| Badge definitions | `BadgeDefinition` or similar | Name, icon sprite, gym leader name — future system |
| Adventure started date | `PlayerData.adventureStarted: long` | Unix timestamp, set at new game |

---

## Screen Design

### Player Card — Main View

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│                    TRAINER CARD                         │
│                                                        │
│     ┌────────────┐                                     │
│     │            │    NAME:    RED                      │
│     │  [trainer  │    ID No:   34521                    │
│     │   sprite]  │    MONEY:   $3000                    │
│     │            │                                     │
│     └────────────┘    POKéDEX:  23 / 45                │
│                       (caught / seen)                   │
│                                                        │
│     PLAY TIME:  12:34                                  │
│                                                        │
│  ─────────────────────────────────────────────────────  │
│                       BADGES                            │
│                                                        │
│     ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐  ┌───┐  │
│     │ ● │  │ ● │  │ ● │  │   │  │   │  │   │  │   │  │   │  │
│     └───┘  └───┘  └───┘  └───┘  └───┘  └───┘  └───┘  └───┘  │
│      1      2      3      4      5      6      7      8      │
│                                                        │
│                                          BACK          │
└────────────────────────────────────────────────────────┘
```

**Layout specs:**
- Full-screen background panel (UIImage SLICED, 100% width/height)
- Title: "TRAINER CARD" centered at top, font size 24
- Trainer sprite: 20% width box, left side
  - Placeholder: generic trainer silhouette
  - Future: player-chosen sprite (male/female variants)
- Info panel (right of sprite):
  - NAME: `PlayerData.playerName`
  - ID No: `PlayerData.trainerId` (5-digit, zero-padded: "00001"–"99999")
  - MONEY: "$" + formatted number with commas (`String.format("$%,d", money)`)
  - POKeDEX: "caught / seen" — from `pokedexCaught.size()` / `pokedexSeen.size()`
- Play time: `SaveManager.getTotalPlayTime()` formatted as "HH:MM"
  - `int hours = (int)(playTime / 3600); int minutes = (int)((playTime % 3600) / 60);`
  - Format: `String.format("%d:%02d", hours, minutes)`
- Horizontal separator
- Badge section: 8 badge slots in a row
  - Earned badges: show badge icon (●) with color/sprite
  - Unearned badges: empty box
  - Badge number below each slot
- BACK at bottom right
- Font: `fonts/Pokemon-Red.ttf`, size 20

**Navigation:**
- MENU/B (Escape): Return to main menu
- INTERACT on BACK: Return to main menu
- UP/DOWN/LEFT/RIGHT: No action (this is a view-only screen)

---

## Minimal Initial Version

Since badges, trainer ID, and Pokedex tracking don't exist yet, the initial implementation uses available data only:

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│                    TRAINER CARD                         │
│                                                        │
│     ┌────────────┐                                     │
│     │            │    NAME:    RED                      │
│     │  [trainer  │    MONEY:   $3,000                   │
│     │   sprite]  │                                     │
│     │            │    POKéMON: 3                        │
│     └────────────┘    (in party)                        │
│                                                        │
│     PLAY TIME:  12:34                                  │
│                                                        │
│                                          BACK          │
└────────────────────────────────────────────────────────┘
```

**Minimal version shows:**
- Name (from `PlayerData.playerName`)
- Money (from `PlayerData.money`)
- Pokemon count (from `PlayerData.team.size()` — just the party count)
- Play time (from `SaveManager.getTotalPlayTime()`)

**Minimal version hides:**
- Trainer ID (field doesn't exist yet)
- Pokedex counts (tracking doesn't exist yet)
- Badge row (badge system doesn't exist yet)

The screen gracefully degrades — fields are shown only when their data source exists.

---

## Data Model Additions

### `PlayerData` additions

```java
// New fields on PlayerData
public int trainerId;                           // 5-digit, generated at new game
public long adventureStarted;                   // Unix ms, set at new game
public List<String> badges = new ArrayList<>();  // Badge IDs earned
// pokedexSeen and pokedexCaught defined in Pokedex screen design
```

### Trainer ID Generation

```java
// At new game initialization
playerData.trainerId = 10000 + new Random().nextInt(90000);  // 10000–99999
playerData.adventureStarted = System.currentTimeMillis();
```

### Play Time Formatting

```java
public static String formatPlayTime(float totalSeconds) {
    int hours = Math.min(999, (int) (totalSeconds / 3600));
    int minutes = (int) ((totalSeconds % 3600) / 60);
    return String.format("%d:%02d", hours, minutes);
}
```

### Money Formatting

```java
public static String formatMoney(int money) {
    return String.format("$%,d", Math.max(0, money));
}
```

---

## Component Design

### `PlayerCardController` (Component — on PlayerCardScreen GameObject)

```java
@ComponentMeta(category = "UI")
public class PlayerCardController extends Component implements MenuScreen {

    // UI references (resolved by framework at load time via @ComponentReference)
    @ComponentReference(source = Source.KEY)
    private UIText nameText;

    @ComponentReference(source = Source.KEY)
    private UIText moneyText;

    @ComponentReference(source = Source.KEY)
    private UIText playTimeText;

    @ComponentReference(source = Source.KEY)
    private UIText pokemonCountText;

    @ComponentReference(source = Source.KEY, required = false)
    private UIText trainerIdText;          // Hidden if trainerId not set

    @ComponentReference(source = Source.KEY, required = false)
    private UIText pokedexText;            // Hidden if pokedex tracking not set

    @ComponentReference(source = Source.KEY, required = false)
    private UIImage trainerSprite;

    @ComponentReference(source = Source.KEY, required = false)
    private UITransform badgeSectionTransform;  // Hidden if no badge system

    // State (transient — runtime only)
    private transient boolean visible = false;

    public void refresh(PlayerData playerData);
    private void updateBadges(List<String> badges);

    // ── MenuScreen interface ──

    @Override
    public void show(Runnable onComplete) { visible = true; /* show transform, call onComplete */ }

    @Override
    public void hide(Runnable onComplete) { visible = false; /* hide transform, call onComplete */ }

    @Override
    public void onActivated(MenuManager manager) { /* no activeList — view-only screen */ }

    @Override
    public void killTweens() { /* kill any active tweens on this screen's transforms */ }
}
```

No `uiResolved` flag, no `resolveUI()` method. Every reference is declarative via `@ComponentReference(source = KEY)`. Optional fields use `required = false` for graceful degradation when data sources don't exist yet.

### Component Keys

```java
public static final String KEY_CARD_SCREEN = "menu_card_screen";
public static final String KEY_CARD_NAME = "menu_card_name";
public static final String KEY_CARD_TRAINER_ID = "menu_card_trainer_id";
public static final String KEY_CARD_MONEY = "menu_card_money";
public static final String KEY_CARD_POKEDEX = "menu_card_pokedex";
public static final String KEY_CARD_PLAY_TIME = "menu_card_play_time";
public static final String KEY_CARD_POKEMON_COUNT = "menu_card_pokemon_count";
public static final String KEY_CARD_TRAINER_SPRITE = "menu_card_trainer_sprite";
public static final String KEY_CARD_BADGE_PREFIX = "menu_card_badge_";
public static final String KEY_CARD_BADGE_SECTION = "menu_card_badge_section";
```

---

## MenuManager Integration

### State Machine Addition

```java
// New MenuState value
PLAYER_CARD,         // Trainer card view (no sub-navigation needed)
```

### State Transitions

| From | Trigger | To | Action |
|------|---------|-----|--------|
| MAIN_MENU | Select CARD | PLAYER_CARD | Hide main panel, show card, refresh data |
| PLAYER_CARD | BACK / MENU press | MAIN_MENU | Hide card, show main panel |

### Main Menu Update

CARD is included in the main menu options:

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

The main menu auto-sizes based on item count. POKéDEX and POKéMON are conditional (see overview.md — Conditional Menu Options).

---

## Implementation Notes

### View-Only Screen

Unlike the team or inventory screens, the Player Card has **no interactive elements** beyond BACK. There is no `SelectableList` needed on the card itself. The `MenuManager` simply listens for MENU/B press while in `PLAYER_CARD` state and returns to `MAIN_MENU`.

However, for consistency, a minimal `SelectableList` with one item ("BACK") can be used so the `activeList` pattern still works. The BACK list's `onCancel` and `onSelect` both map to returning to the main menu.

### Badge Rendering

Each badge slot is a small `UIImage`:
- **Earned**: Shows badge-specific icon sprite (if badge sprites exist) or a generic colored circle
- **Unearned**: Empty outline / dimmed box at 30% alpha
- **Badge section hidden entirely** if `PlayerData.badges` field doesn't exist (graceful degradation)

### Future Expansion

The Player Card in later Pokemon games shows:
- Back side (flip animation) with signature, adventure started date, Hall of Fame time
- Player avatar sprite that changes based on badges earned
- Link battle record
- Poketch (Diamond/Pearl)

These are all out of scope but the screen layout leaves room for future additions.
