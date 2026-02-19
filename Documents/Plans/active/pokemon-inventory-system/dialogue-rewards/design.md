# Dialogue Reward Integration — Plan

## Overview

Extends the existing dialogue event system to support giving items and Pokemon to the player as dialogue rewards. Adds `GIVE_ITEM` and `GIVE_POKEMON` to the `DialogueReaction` enum and adds reward-specific fields to `DialogueEventListener`. The existing exhaustive switch in `onDialogueEvent()` is extended with two new cases — no new component classes needed.

## Dependencies

- **pokemon-data** — `PokemonFactory`, `PokemonInstance`
- **item-inventory** — `PlayerInventoryComponent`
- **pokemon-ecs** — `PlayerPartyComponent`, `PokemonStorageComponent`
- **scene-data-persistence** — `PlayerData` (player name for PokemonFactory)

## Existing System Context

The dialogue system dispatches custom events via `PlayerDialogueManager.dispatchEvent()`. Events fire at three points:
1. **Line-level**: `DialogueLine.onCompleteEvent` — when player advances past a line
2. **Choice-level**: `ChoiceAction` — when a choice is selected
3. **Conversation-level**: `DialogueInteractable.onConversationEnd` — when dialogue ends

Custom events trigger a scene-wide query via `scene.getComponentsImplementing(DialogueEventListener.class)`. Each matching listener's `onDialogueEvent()` is called, which executes a `DialogueReaction` via an exhaustive switch.

**Existing system:**
- `DialogueEventListener` (class, extends Component) — has `eventName` field, `reaction` field (DialogueReaction enum), and `onDialogueEvent()` with exhaustive switch
- `DialogueReaction` (enum) — `ENABLE_GAME_OBJECT`, `DISABLE_GAME_OBJECT`, `DESTROY_GAME_OBJECT`, `RUN_ANIMATION`
- `PlayerDialogueManager` — dispatches events, calls `DialogueEventStore.markFired()`
- `DialogueEventStore` — persists fired event names

**Key files:**
- `com.pocket.rpg.components.dialogue.PlayerDialogueManager` — dispatches events
- `com.pocket.rpg.components.dialogue.DialogueEventListener` — listens + reacts
- `com.pocket.rpg.components.dialogue.DialogueReaction` — reaction enum
- `com.pocket.rpg.dialogue.DialogueEventStore` — persists fired events

## Package Layout

No new files — only modifications to existing classes:

```
com.pocket.rpg/
├── components/dialogue/
│   ├── DialogueReaction.java        # MODIFIED — add GIVE_ITEM, GIVE_POKEMON
│   └── DialogueEventListener.java   # MODIFIED — add reward fields + switch cases
```

---

## Detailed Class Designs

### `DialogueReaction` (enum modification)

Add two new values:

```java
public enum DialogueReaction {
    ENABLE_GAME_OBJECT,
    DISABLE_GAME_OBJECT,
    DESTROY_GAME_OBJECT,
    RUN_ANIMATION,
    GIVE_ITEM,           // NEW — adds items to player inventory
    GIVE_POKEMON         // NEW — gives a Pokemon to the player
}
```

### `DialogueEventListener` (class modification)

Add reward-specific fields and extend the exhaustive switch with two new cases. The new fields are only relevant when `reaction` is `GIVE_ITEM` or `GIVE_POKEMON` — they are ignored for other reactions.

**New fields:**

```java
// GIVE_ITEM fields (only used when reaction == GIVE_ITEM)
private String itemId;           // "potion", "pokeball", etc. (references ItemRegistry)
private int quantity = 1;        // How many to give

// GIVE_POKEMON fields (only used when reaction == GIVE_POKEMON)
private String speciesId;        // "bulbasaur" (references Pokedex)
private int level = 5;           // Level of the Pokemon to create
```

**Extended switch in `onDialogueEvent()`:**

```java
case GIVE_ITEM -> {
    // 1. Find player entity via scene query
    PlayerInventoryComponent inv = findPlayerComponent(PlayerInventoryComponent.class);
    if (inv == null) {
        LOG.warn("GIVE_ITEM on '%s' — no PlayerInventoryComponent in scene", ...);
        yield false;
    }
    // 2. Add item to inventory
    boolean success = inv.addItem(itemId, quantity);
    if (!success) {
        LOG.warn("GIVE_ITEM failed — bag full for item '%s'", itemId);
    }
    // 3. (Future) Show "Received X Potion(s)!" message via UI
    yield success;
}
case GIVE_POKEMON -> {
    // 1. Load Pokedex and create Pokemon
    PlayerData playerData = PlayerData.load();
    PokemonInstance pokemon = PokemonFactory.createStarter(speciesId, level, playerData.playerName);

    // 2. Try adding to party
    PlayerPartyComponent party = findPlayerComponent(PlayerPartyComponent.class);
    if (party == null) {
        LOG.warn("GIVE_POKEMON on '%s' — no PlayerPartyComponent in scene", ...);
        yield false;
    }
    boolean added = party.addToParty(pokemon);

    // 3. If party full, deposit to PC storage
    if (!added) {
        PokemonStorageComponent storage = findPlayerComponent(PokemonStorageComponent.class);
        if (storage != null) {
            added = storage.depositToFirstAvailable(pokemon);
        }
    }
    // 4. (Future) Show "Received Bulbasaur!" message via UI
    yield added;
}
```

**Helper method** (added to DialogueEventListener):

```java
private <T> T findPlayerComponent(Class<T> type) {
    if (gameObject == null || gameObject.getScene() == null) return null;
    List<T> found = gameObject.getScene().getComponentsImplementing(type);
    return found.isEmpty() ? null : found.get(0);
}
```

**Inspector fields** (all on the same DialogueEventListener component):
- `eventName` — dropdown from DialogueEvents asset (existing)
- `reaction` — dropdown from DialogueReaction enum (existing)
- `itemId` — text field, only relevant when reaction = GIVE_ITEM
- `quantity` — integer field, only relevant when reaction = GIVE_ITEM
- `speciesId` — text field, only relevant when reaction = GIVE_POKEMON
- `level` — integer field, only relevant when reaction = GIVE_POKEMON

---

## Scene Setup Examples

### Giving Items via Dialogue

```
NPC "Professor Oak" (GameObject)
├── Transform
├── SpriteRenderer
├── TriggerZone                        ◄── auto-added by DialogueInteractable
└── DialogueInteractable
    └── dialogue: "oak_starter_dialogue"

ItemReward_Pokeballs (GameObject)
└── DialogueEventListener             ◄── existing component, no new class
    ├── eventName: "GIVE_STARTER_POKEBALLS"
    ├── reaction: GIVE_ITEM
    ├── itemId: "pokeball"
    └── quantity: 5
```

In the dialogue file, a line's `onCompleteEvent` is set to the custom event `"GIVE_STARTER_POKEBALLS"`. When the player reads that line and presses continue, the event fires and the listener adds 5 Pokeballs to the player's inventory.

### Giving Pokemon via Dialogue

```
PokemonReward_Starter (GameObject)
└── DialogueEventListener
    ├── eventName: "GIVE_STARTER_POKEMON"
    ├── reaction: GIVE_POKEMON
    ├── speciesId: "bulbasaur"
    └── level: 5
```

### Dialogue Events Asset

Add the new events to `gameData/assets/dialogues/events.dialogue-events.json`:

```json
{
  "events": [
    "OPEN_DOOR",
    "GIVE_STARTER_POKEMON",
    "GIVE_STARTER_POKEBALLS",
    "GIVE_ITEM_REWARD",
    "GIVE_POKEMON_REWARD"
  ]
}
```

---

## Event Flow

```
Player talks to Professor Oak
  → Dialogue plays: "Here, take this Pokemon!"
  → Line completes → fires custom event "GIVE_STARTER_POKEMON"
  → PlayerDialogueManager.dispatchEvent("GIVE_STARTER_POKEMON")
  → Scene query: finds DialogueEventListener with matching eventName
  → DialogueEventListener.onDialogueEvent()
      → switch(reaction) → case GIVE_POKEMON
      → PokemonFactory.createStarter("bulbasaur", 5, playerName)
      → PlayerPartyComponent.addToParty(pokemon)
  → DialogueEventStore.markFired("GIVE_STARTER_POKEMON")  // Persisted
```

### One-Time Rewards & Replay Prevention

**The problem:** `DialogueEventListener.onStart()` checks `DialogueEventStore.hasFired(eventName)` and calls `onDialogueEvent()` immediately if the event was previously fired. For visual reactions (ENABLE/DISABLE/DESTROY), this is correct — the visual state replays on scene load. For rewards (GIVE_ITEM/GIVE_POKEMON), this would duplicate items/Pokemon on every scene load.

**The solution:** Add a guard at the top of the GIVE_ITEM and GIVE_POKEMON switch cases:

```java
case GIVE_ITEM -> {
    // Rewards are persisted by PlayerData, not by event replay.
    // Skip if this is an auto-replay from onStart().
    if (DialogueEventStore.hasFired(getEventName())) {
        yield true;  // Already given — skip silently
    }
    // ... actual reward logic
}
```

This way the base `onStart()` auto-replay behavior is preserved for all reactions, but reward cases early-return when they detect they're being replayed. The actual reward data is persisted via `PlayerData` (inventory/party state).

**Additional safety — dialogue branching:** The dialogue itself can check a flag and branch to different text if the reward was already given. The NPC says something different on subsequent visits, so the event never fires a second time.

---

## Implementation Phases

| Phase | Scope |
|-------|-------|
| **1** | Add `GIVE_ITEM`, `GIVE_POKEMON` to `DialogueReaction` enum |
| **2** | Add reward fields (`itemId`, `quantity`, `speciesId`, `level`) + `findPlayerComponent()` helper to `DialogueEventListener` |
| **3** | Extend the `onDialogueEvent()` switch with GIVE_ITEM and GIVE_POKEMON cases (including replay guard) |
| **4** | Add sample events to `events.dialogue-events.json` |
| **5** | Integration test: dialogue → event → reward → verify inventory/party state |

## Acceptance Criteria

- [ ] `GIVE_ITEM` and `GIVE_POKEMON` added to `DialogueReaction` enum
- [ ] Existing exhaustive switch in `onDialogueEvent()` compiles with both new cases handled
- [ ] `GIVE_ITEM` adds correct item + quantity to player inventory via `PlayerInventoryComponent`
- [ ] `GIVE_POKEMON` creates Pokemon via `PokemonFactory` and adds to party via `PlayerPartyComponent`
- [ ] `GIVE_POKEMON` with full party deposits to PC storage via `PokemonStorageComponent.depositToFirstAvailable()`
- [ ] Replay guard prevents duplicate rewards when scene reloads (checks `DialogueEventStore.hasFired()`)
- [ ] Missing player components handled gracefully (warning log, no crash, returns false)
- [ ] Reward state persisted via write-through (`PlayerInventoryComponent` / `PlayerPartyComponent` flush to `PlayerData`)

## Testing Plan

### Unit Tests

**GIVE_ITEM:**
- Fire event → item present in player inventory with correct itemId and quantity
- Fire event with quantity > 1 → correct quantity added
- Fire event with missing `PlayerInventoryComponent` in scene → returns false, logs warning
- Fire event with full inventory pocket → returns false (item not added)

**GIVE_POKEMON:**
- Fire event → Pokemon in player party with correct species and level
- Fire event → Pokemon's OT = `PlayerData.playerName`
- Fire event with full party (6) → Pokemon deposited to PC storage via `depositToFirstAvailable()`
- Fire event with full party + full PC → returns false, no crash
- Fire event with missing `PlayerPartyComponent` → returns false, logs warning

**Replay guard:**
- `DialogueEventStore.hasFired(eventName)` = true → GIVE_ITEM case yields true without adding item
- `DialogueEventStore.hasFired(eventName)` = true → GIVE_POKEMON case yields true without adding Pokemon
- `DialogueEventStore.hasFired(eventName)` = false → reward given normally

**Exhaustive switch:**
- Compile-time verification — adding `GIVE_ITEM` / `GIVE_POKEMON` to enum without switch cases produces compiler error

### Manual Tests

- Set up NPC with dialogue → line fires event "GIVE_STARTER_POKEBALLS" → `DialogueEventListener` with reaction=GIVE_ITEM, itemId="pokeball", quantity=5 — interact, verify 5 Pokeballs in inventory
- Set up NPC with dialogue → event "GIVE_STARTER_POKEMON" → reaction=GIVE_POKEMON, speciesId="bulbasaur", level=5 — interact, verify Bulbasaur in party
- Give Pokemon with full party (6 members) — verify Pokemon goes to PC storage
- Receive reward, save game, reload — verify reward still present (write-through to PlayerData)
- Receive reward, leave scene, return — verify no duplicate reward on re-entry (replay guard)
- Receive reward, interact with NPC again — verify dialogue branches to post-reward text (no second event fire)
