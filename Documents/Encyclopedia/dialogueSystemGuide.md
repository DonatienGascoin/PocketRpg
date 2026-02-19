# Dialogue System Guide

> **Summary:** The dialogue system handles NPC conversations with branching choices, variable substitution, conditional dialogue selection, and persistent event tracking. It covers asset authoring, runtime flow, and the dedicated Dialogue Editor panel.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Asset Types](#asset-types)
4. [Creating a Dialogue](#creating-a-dialogue)
5. [Dialogue Editor Panel](#dialogue-editor-panel)
6. [Setting Up an NPC](#setting-up-an-npc)
7. [Variables](#variables)
8. [Events](#events)
9. [Conditional Dialogues](#conditional-dialogues)
10. [DialogueEventListener](#dialogueeventlistener)
11. [Dialogue UI](#dialogue-ui)
12. [Keyboard Shortcuts](#keyboard-shortcuts)
13. [Tips & Best Practices](#tips--best-practices)
14. [Troubleshooting](#troubleshooting)
15. [Code Integration](#code-integration)
16. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create a dialogue file | Right-click in Asset Browser > New > `.dialogue.json` or create manually in `gameData/assets/dialogues/` |
| Open Dialogue Editor | Menu **Window > Dialogue Editor**, or double-click a `.dialogue.json` asset |
| Add a line | Click **+ Line** in the Dialogue Editor toolbar |
| Add choices | Click **+ Choices** (appended as the last entry) |
| Chain dialogues | Set a choice action type to **DIALOGUE** and pick the target asset |
| Use a variable | Type `[VARIABLE_NAME]` in dialogue text |
| Fire an event | Set **onCompleteEvent** on a line, or use a choice action |
| Make dialogue conditional | Add entries to the **Conditional Dialogues** list on `DialogueInteractable` |
| React to events | Add a `DialogueEventListener` component to any entity |
| Set up an NPC | Add `DialogueInteractable` component, assign a default dialogue |
| Set up the player | Ensure the player has `PlayerDialogueManager` and `PlayerInput` components |

---

## Overview

The dialogue system lets NPCs have multi-line conversations with the player, including branching choices that chain to other dialogues or fire events. It is built around three pillars:

- **Data layer** — Dialogue assets (`.dialogue.json`), a global events registry (`.dialogue-events.json`), and a global variables registry (`.dialogue-vars.json`). All are JSON files in `gameData/assets/dialogues/`.
- **Runtime** — `PlayerDialogueManager` orchestrates the conversation flow on the player entity. `DialogueInteractable` marks NPCs as talkable. `DialogueEventListener` reacts to custom events.
- **Editor tooling** — A dedicated Dialogue Editor panel for authoring lines and choices, plus custom inspectors for all dialogue components and assets.

When the player interacts with an NPC, the system pauses movement (via `IPausable`), switches input to `DIALOGUE` mode, shows the dialogue UI, and steps through lines with a typewriter effect. Choices are navigated with arrow keys and confirmed with the interact button.

---

## Asset Types

The dialogue system uses three asset types, all stored under `gameData/assets/dialogues/`:

### Dialogue (`.dialogue.json`)

The main conversation asset. Contains an ordered list of entries:

| Entry Type | Description |
|------------|-------------|
| **LINE** | A single text line. Can have an optional `onCompleteEvent` |
| **CHOICES** | A branching point with up to 4 choices. Must be the last entry |

Example (`npc_greeting.dialogue.json`):
```json
{
  "entries": [
    {
      "type": "LINE",
      "text": "Hello there, traveler!"
    },
    {
      "type": "LINE",
      "text": "Welcome to our little town."
    },
    {
      "type": "CHOICES",
      "hasChoices": true,
      "choices": [
        {
          "text": "Tell me about this place",
          "action": {
            "type": "DIALOGUE",
            "dialogue": "dialogues/npc_lore.dialogue.json"
          }
        },
        {
          "text": "Goodbye",
          "action": {
            "type": "BUILT_IN_EVENT",
            "builtInEvent": "END_CONVERSATION"
          }
        }
      ]
    }
  ]
}
```

### Dialogue Events (`.dialogue-events.json`)

A single global registry of custom event names. Convention path: `dialogues/events.dialogue-events.json`.

```json
{
  "events": [
    "HEARD_CAVE_RUMOR"
  ]
}
```

Custom events are dispatched to `DialogueEventListener` components in the scene when they fire.

### Dialogue Variables (`.dialogue-vars.json`)

A single global registry of variable definitions. Convention path: `dialogues/variables.dialogue-vars.json`.

```json
{
  "variables": [
    { "name": "PLAYER_NAME", "type": "AUTO" },
    { "name": "TRAINER_NAME", "type": "STATIC" },
    { "name": "POKEMON_NAME", "type": "RUNTIME" }
  ]
}
```

See [Variables](#variables) for details on each type.

---

## Creating a Dialogue

### By hand

1. Create a `.dialogue.json` file in `gameData/assets/dialogues/`
2. Add entries as shown in the example above
3. The filename (minus extension) becomes the dialogue name

### In the Dialogue Editor

1. Open the Dialogue Editor (see [Dialogue Editor Panel](#dialogue-editor-panel))
2. Click **+ New** in the left column
3. Name the dialogue and start adding lines

### Rules

- A dialogue must have at least one entry
- A `CHOICES` entry can only be the **last** entry
- Maximum **4 choices** per choice group
- Variable tags use `[VARIABLE_NAME]` syntax (square brackets)
- The `hasChoices` flag on a choice group can be set to `false` to end the dialogue without branching

---

## Dialogue Editor Panel

The Dialogue Editor is a dedicated editor panel for authoring `.dialogue.json` assets.

### Opening

- **Menu:** Window > Dialogue Editor
- **Double-click** a `.dialogue.json` file in the Asset Browser
- **Open button** on the `DialogueInteractable` inspector (opens the assigned dialogue)

### Interface

```
+----------------------------+----------------------------------------------+
| Dialogue List              | Toolbar                                      |
|                            |   [+ Line] [+ Choices] [Save] [Undo] [Redo] |
| [Search...]                |----------------------------------------------|
|                            |                                              |
|  npc_greeting       [x]   | Lines                                        |
|  npc_lore           [x]   |   1. "Hello there, traveler!"           [=]  |
|  npc_job            [x]   |   2. "Welcome to our little town."      [=]  |
|  npc_post_lore      [x]   |                                              |
|                            | Choices                                      |
| [+ New]                    |   [x] Has Choices                            |
|                            |   1. "Tell me about this place"  -> DIALOGUE |
|                            |   2. "Goodbye"                   -> END      |
|                            |                                              |
+----------------------------+----------------------------------------------+
```

| Area | Description |
|------|-------------|
| **Dialogue List** (left) | Shows all loaded dialogues with search filter. Click to select. `[x]` removes. |
| **Toolbar** (top-right) | Add lines/choices, save, undo/redo |
| **Lines** (right) | Ordered list of dialogue lines. Drag handles `[=]` for reordering. Expand a line to edit its `onCompleteEvent`. |
| **Choices** (right, bottom) | Choice group editor. Toggle `hasChoices`, edit choice text and action type. |

### Line editing

- Click a line's text to edit it inline
- Expand a line (click the arrow) to access the **onCompleteEvent** editor
- Use the **Insert Variable** popup to insert `[VAR_NAME]` tags
- Drag lines by their handle to reorder
- Validation warnings appear inline for malformed tags or unknown variables

### Choice editing

Each choice has:

| Field | Description |
|-------|-------------|
| **Text** | The display text shown to the player |
| **Action Type** | `DIALOGUE`, `BUILT_IN_EVENT`, or `CUSTOM_EVENT` |
| **Target** | Dialogue path, built-in event, or custom event name (depending on type) |

### Undo/Redo

The editor tracks up to 50 snapshots. Every edit (text change, reorder, add/remove) captures state. Use `Ctrl+Z` / `Ctrl+Y` or the toolbar buttons.

### Unsaved changes

When switching dialogues with unsaved changes, a confirmation popup appears with Save / Discard / Cancel options.

---

## Setting Up an NPC

### Required components on the player

The player entity needs:
- **PlayerInput** — Manages input modes (OVERWORLD, DIALOGUE, etc.)
- **PlayerDialogueManager** — Orchestrates dialogue flow, typewriter effect, UI management

### Required components on the NPC

Add **DialogueInteractable** to the NPC entity. This extends `InteractableComponent`, so a `TriggerZone` is auto-added.

### DialogueInteractable inspector

```
+--------------------------------------------------+
| v DialogueInteractable                      [X]  |
|                                                  |
|  A. Interaction Settings                         |
|     Directional In...  [x]                       |
|     Interact From (1)                       [+]  |
|       0  [DOWN]                             [x]  |
|                                                  |
|  B. Conditional Dialogues (0)               [+]  |
|     (empty)                                      |
|                                                  |
|  C. Default Dialogue (required)                  |
|     [dialogues/npc_greeting.dialogue.json] [Open]|
|                                                  |
|  D. On Conversation End                          |
|     Category  [CUSTOM]                           |
|     Event     [HEARD_CAVE_RUMOR]                 |
|                                                  |
|  E. Variable Table                               |
|     (shows STATIC vars with editable values)     |
|                                                  |
|  F. Preview (collapsed)                          |
+--------------------------------------------------+
```

| Section | Purpose |
|---------|---------|
| **A. Interaction Settings** | Inherited from `InteractableComponent` — directional constraints |
| **B. Conditional Dialogues** | Optional list of condition-based dialogue overrides (see [Conditional Dialogues](#conditional-dialogues)) |
| **C. Default Dialogue** | The dialogue to play when no conditional matches. **Required field.** Click **Open** to jump to Dialogue Editor. |
| **D. On Conversation End** | Optional event fired when the entire conversation ends |
| **E. Variable Table** | Shows all variables from the global `DialogueVariables` asset. STATIC variables can be edited per-NPC here. |
| **F. Preview** | Collapsible read-only preview of the selected dialogue's content |

### Minimal NPC setup

1. Create a new entity for the NPC
2. Add `SpriteRenderer` (for visuals)
3. Add `DialogueInteractable`
4. Set **Default Dialogue** to a `.dialogue.json` asset path
5. The `TriggerZone` is auto-added — adjust its width/height if the NPC spans multiple tiles

---

## Variables

Variables allow dynamic text in dialogue lines. Write `[VARIABLE_NAME]` in any line's text and it will be replaced at runtime.

### Variable types

| Type | Set by | Example | Description |
|------|--------|---------|-------------|
| **AUTO** | Code (supplier) | `PLAYER_NAME` | Resolved from game state automatically. Registered in `PlayerDialogueManager.onStart()` via `DialogueVariableResolver`. |
| **STATIC** | Editor (per-NPC) | `TRAINER_NAME` | Set in the `DialogueInteractable` inspector's Variable Table. Each NPC can have different values. |
| **RUNTIME** | Code (at dialogue start) | `POKEMON_NAME` | Provided programmatically when starting a dialogue. |

### Resolution order

Variables merge in order: **AUTO -> STATIC -> RUNTIME**. Each layer overrides the previous for the same key. This means a RUNTIME variable with the same name as an AUTO variable will take precedence.

### Setting up variables

1. Open the `variables.dialogue-vars.json` asset in the Inspector
2. Add variable definitions with name and type
3. Use `[VAR_NAME]` in dialogue text
4. For STATIC variables, set values per-NPC in the `DialogueInteractable` inspector
5. For AUTO variables, register suppliers in code (see [Code Integration](#code-integration))

### Validation

The Dialogue Editor shows warnings for:
- `[UNKNOWN_VAR]` — Variable not defined in the global registry
- `[unclosed` — Malformed bracket syntax

---

## Events

Events allow dialogues to trigger game actions. There are two categories:

### Built-in events

Handled directly by `PlayerDialogueManager`:

| Event | Effect |
|-------|--------|
| `END_CONVERSATION` | Ends the dialogue cleanly |

### Custom events

Defined in the `events.dialogue-events.json` asset. When fired, custom events are:
1. Recorded in `DialogueEventStore` (persists across scenes and save/load)
2. Dispatched to all `DialogueEventListener` components in the scene

### Where events can be placed

| Location | Fires when... |
|----------|---------------|
| **Line `onCompleteEvent`** | The player advances past that line |
| **Choice action** (`CUSTOM_EVENT` or `BUILT_IN_EVENT`) | The player selects that choice |
| **`DialogueInteractable.onConversationEnd`** | The entire conversation ends (after the last line or after `END_CONVERSATION`) |

### Event persistence

Custom events are persisted via `DialogueEventStore`, which stores boolean flags in the `dialogue_events` namespace of `SaveManager`'s global state. Once an event fires, `DialogueEventStore.hasFired("EVENT_NAME")` returns true for the rest of the playthrough (survives scene transitions and save/load).

---

## Conditional Dialogues

NPCs can show different dialogues based on which events have fired.

### How it works

1. The `DialogueInteractable` inspector has a **Conditional Dialogues** list
2. Each entry has one or more **conditions** and a **target dialogue**
3. Conditions check whether a custom event has been `FIRED` or `NOT_FIRED`
4. All conditions in an entry use AND logic (all must be true)
5. Entries are evaluated **top to bottom** — first match wins
6. If no conditional matches, the **Default Dialogue** is used

### Example

An NPC that tells a different story after the player has heard a rumor:

| # | Conditions | Dialogue |
|---|-----------|----------|
| 1 | `HEARD_CAVE_RUMOR` = FIRED | `npc_post_lore.dialogue.json` |
| — | *(default)* | `npc_greeting.dialogue.json` |

### Setting up in the inspector

1. Click **+** on the Conditional Dialogues list
2. Add one or more conditions (event name dropdown + FIRED/NOT_FIRED)
3. Pick the target dialogue asset
4. Reorder entries if needed (first match wins)

---

## DialogueEventListener

The `DialogueEventListener` component lets any entity react to a custom dialogue event with a predefined action.

### Inspector fields

| Field | Description |
|-------|-------------|
| **Event Name** | Custom event to listen for (dropdown from global events registry) |
| **Reaction** | What happens when the event fires |

### Available reactions

| Reaction | Effect |
|----------|--------|
| `ENABLE_GAME_OBJECT` | Enables this entity's GameObject |
| `DISABLE_GAME_OBJECT` | Disables this entity's GameObject |
| `DESTROY_GAME_OBJECT` | Destroys this entity's GameObject |
| `RUN_ANIMATION` | Runs the entity's animation |

### Cross-scene support

If the listener's event has already been fired (checked via `DialogueEventStore`) when the scene loads, the reaction executes immediately during `onStart()`. This means world-state changes persist even after leaving and returning to a scene.

### Example usage

Block a cave entrance with a boulder entity:
1. Add `DialogueEventListener` to the boulder
2. Set **Event Name** to `CAVE_OPENED`
3. Set **Reaction** to `DESTROY_GAME_OBJECT`
4. When an NPC dialogue fires the `CAVE_OPENED` event, the boulder is destroyed — even if the player leaves and returns later

---

## Dialogue UI

The dialogue UI is a programmatic prefab built by `DialogueUIBuilder`. It renders at the bottom of the screen during conversations.

### Layout

```
+-----------------------------------------------+
|                                                |
|                    (game world)                |
|                                                |
|                         +-------------------+  |
|                         | > Tell me more    |  |
|                         |   Goodbye         |  |
|                         +-------------------+  |
|                                                |
| +-------------------------------------------+ |
| | Hello there, traveler!              v      | |
| +-------------------------------------------+ |
+-----------------------------------------------+
```

| Element | Description |
|---------|-------------|
| **Dialogue box** | Bottom of screen, SLICED sprite background |
| **Text** | Word-wrapped with typewriter reveal effect |
| **Continue indicator** | Blinking `v` arrow, visible when text is fully revealed |
| **Choice panel** | Top-right, visible only when a choice group is reached |
| **Choice arrow** | `>` indicator next to the selected choice |

### Player controls

| Input | Action |
|-------|--------|
| **Interact** (Z / Space / Enter) | Advance to next line, or confirm choice selection |
| **Up/Down arrows** (or W/S) | Navigate between choices |

### Typewriter effect

Text is revealed character by character. Pressing interact while text is revealing skips to the full text instantly. Pressing interact after text is fully revealed advances to the next line.

---

## Keyboard Shortcuts

### Dialogue Editor panel

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save current dialogue |
| Ctrl+Z | Undo |
| Ctrl+Y | Redo |
| Ctrl+Enter | Add a new line |

### In-game dialogue controls

| Key | Action |
|-----|--------|
| Z / Space / Enter | Advance text / confirm choice |
| W / Up Arrow | Move choice selection up |
| S / Down Arrow | Move choice selection down |

---

## Tips & Best Practices

- **Start simple** — Create a single-line dialogue to test the NPC setup before adding branches
- **Use `END_CONVERSATION`** — Always have at least one choice that ends the conversation (e.g., "Goodbye" -> `BUILT_IN_EVENT` -> `END_CONVERSATION`)
- **Name events clearly** — Use descriptive event names like `HEARD_CAVE_RUMOR` rather than `EVENT_1`
- **Test conditional order** — Conditional dialogues evaluate top-to-bottom; put the most specific conditions first
- **Keep dialogues short** — Each `.dialogue.json` represents one conversation segment. Use choice chaining to link segments together.
- **Use the Preview section** — The collapsible preview in the `DialogueInteractable` inspector lets you verify dialogue content without opening the Dialogue Editor
- **Use the Variable Table** — For NPC-specific data (names, titles), define STATIC variables and set them per-NPC in the inspector
- **Save frequently** — The Dialogue Editor warns about unsaved changes, but it's good practice to save after each edit session

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| NPC doesn't start dialogue | Ensure the player has `PlayerDialogueManager` and `PlayerInput` components. Check that the NPC's `DialogueInteractable` has a Default Dialogue set. |
| Player can still move during dialogue | Ensure movement components implement `IPausable`. `PlayerDialogueManager` calls `onPause()` on all `IPausable` components in the scene. |
| Variable shows as `[VAR_NAME]` literally | The variable is not defined in `variables.dialogue-vars.json`, or it has no value (STATIC not set, AUTO not registered, RUNTIME not provided). |
| Event not firing | Check that the event is listed in `events.dialogue-events.json`. Verify the event ref category (BUILT_IN vs CUSTOM) is correct. |
| DialogueEventListener not reacting | Ensure the listener's Event Name matches the event being fired exactly. Check the Reaction field is set. |
| Conditional dialogue not triggering | Conditions use AND logic — all must be true. Check event names match and expected state (FIRED/NOT_FIRED) is correct. Conditionals evaluate top-to-bottom. |
| Dialogue Editor shows validation warnings | Yellow warnings for unknown variables, missing dialogue paths, or orphaned event references. Fix the referenced assets. |
| Choices not appearing | Ensure the `CHOICES` entry has `hasChoices: true` and at least one choice. The choice group must be the last entry. |
| Typewriter effect too fast/slow | The typewriter speed is configured in `PlayerDialogueManager`. |
| Undo not working in Dialogue Editor | Maximum 50 undo steps. Undo/redo only tracks the currently open dialogue. |
| Stale event warning in inspector | The event name on the component no longer exists in the global events registry. Update or re-add the event. |

---

## Code Integration

### Starting a dialogue programmatically

```java
PlayerDialogueManager manager = player.getComponent(PlayerDialogueManager.class);
Dialogue dialogue = Assets.load("dialogues/my_dialogue.dialogue.json", Dialogue.class);
manager.startDialogue(dialogue);
```

### Starting with runtime variables

```java
Map<String, String> runtimeVars = Map.of("POKEMON_NAME", "Pikachu");
manager.startDialogue(dialogue, runtimeVars);
```

### Registering AUTO variables

In your player setup (e.g., `PlayerDialogueManager.onStart()`):

```java
DialogueVariableResolver resolver = new DialogueVariableResolver();
resolver.register("PLAYER_NAME", () -> playerData.getName());
resolver.register("MONEY", () -> String.valueOf(playerData.getGold()));
```

### Checking event state

```java
// Check if an event has been fired this playthrough
if (DialogueEventStore.hasFired("HEARD_CAVE_RUMOR")) {
    // React to the event
}

// Manually mark an event as fired (e.g., from non-dialogue code)
DialogueEventStore.markFired("QUEST_COMPLETE");
```

### Creating a dialogue asset in code

```java
Dialogue dialogue = new Dialogue("dynamic_dialogue");
dialogue.getEntries().add(new DialogueLine("This was generated in code!"));

DialogueChoiceGroup choices = new DialogueChoiceGroup(true, List.of(
    new Choice("Option A", ChoiceAction.dialogue("dialogues/path_a.dialogue.json")),
    new Choice("Option B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
));
dialogue.getEntries().add(choices);
```

---

## Related

- [Interactable System Guide](interactableSystemGuide.md) — Base interactable framework that `DialogueInteractable` extends
- [Components Guide](componentsGuide.md) — Component lifecycle, annotations, `@ComponentReference`
- [Save System Guide](saveSystemGuide.md) — `DialogueEventStore` uses `SaveManager` global state for persistence
- [Asset Loader Guide](assetLoaderGuide.md) — How `.dialogue.json` files are loaded via the asset pipeline
- [Custom Inspector Guide](customInspectorGuide.md) — How dialogue inspectors are built
- [Animation Editor Guide](animationEditorGuide.md) — `RUN_ANIMATION` reaction on `DialogueEventListener`
