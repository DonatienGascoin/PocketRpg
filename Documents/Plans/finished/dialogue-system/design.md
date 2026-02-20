# Dialogue System — Design Document

## Overview

A GameObject-based dialogue system for PocketRpg. It is **independent from any System feature** — all runtime logic lives in Components, all data in Assets. No singletons, no static managers. The player's `PlayerDialogueManager` component orchestrates dialogue flow, input blocking, and UI updates.

---

## 1. Dialogue Asset

### Data Model

A `Dialogue` is a JSON asset (`.dialogue.json`) containing an ordered list of **entries**. Each entry is either a **Line** or a **Choice Group**.

```
Dialogue
├── (name derived from filename)     // e.g. "professor_greeting" from professor_greeting.dialogue.json
├── entries: List<DialogueEntry>     // Ordered sequence
│   ├── DialogueLine                 // Text shown to the player
│   │   └── text: String            // Supports [VARIABLE] tags
│   └── DialogueChoiceGroup          // Branching point (must be last entry)
│       ├── hasChoices: boolean      // If false, choices list is hidden/ignored
│       └── choices: List<Choice>
│           ├── text: String         // Choice label
│           └── action: ChoiceAction
│               ├── type: DIALOGUE | BUILT_IN_EVENT | CUSTOM_EVENT
│               ├── dialogue: Dialogue           // If DIALOGUE: asset reference
│               ├── builtInEvent: DialogueEvent  // If BUILT_IN_EVENT: enum
│               └── customEvent: String          // If CUSTOM_EVENT: validated against DialogueEvents asset
```

**Rules:**
- A dialogue must have at least one entry. **Enforced at three levels:**
  - **Editor:** New dialogues are created with one empty line. The `[╳]` delete button on the last remaining line is disabled. The dialogue cannot be saved with 0 entries.
  - **Loader:** `DialogueLoader.getPlaceholder()` returns a dialogue with one empty line (not zero).
  - **Runtime:** `startDialogue()` guards against empty entries — logs error, closes dialogue (see Runtime Validation in section 4).
- A `DialogueChoiceGroup` can only appear as the **last** entry.
- **Maximum 4 choices** per `DialogueChoiceGroup`. The UI prefab has 4 fixed choice slots. **Enforced at two levels:**
  - **Editor:** The `[+ Add Choice]` button is disabled when 4 choices exist.
  - **Runtime:** If a dialogue somehow has more than 4 choices (e.g. hand-edited JSON), only the first 4 are shown. A warning is logged.
- `hasChoices` controls whether the choice group is active. When `false`, the choices list is hidden in the editor and ignored at runtime — the dialogue ends after the last line. This avoids needing to add/remove the choice group entirely.
- **`hasChoices=true` with empty choices list:** Treated as `hasChoices=false` at runtime (dialogue ends after last line). **Enforced at two levels:**
  - **Editor:** When `hasChoices` is checked, if choices list is empty, show orange `⚠` warning: "Has choices enabled but no choices defined."
  - **Runtime:** If `hasChoices && choices.isEmpty()`, log a warning and skip the choice group — end the dialogue normally.
- Variables are referenced in lines via `[VAR_NAME]` syntax. The valid variable names come from the global `DialogueVariables` asset (see below).

### DialogueVariables Asset

A **single, global** asset (`.dialogue-vars.json`) that holds the master list of all variable names used across the game. It lives at a **well-known path** and is loaded automatically — no per-dialogue reference needed.

Each variable has a **type** that determines how its value is provided:

```
DialogueVariables
└── variables: List<DialogueVariable>
    ├── name: String                  // e.g. "PLAYER_NAME"
    └── type: STATIC | RUNTIME | AUTO

STATIC  — Value known at edit time. Set in the DialogueComponent inspector.
            Example: TRAINER_NAME, TOWN_NAME, RIVAL_NAME
RUNTIME — Value set programmatically at dialogue start by the caller. Cannot be set in the editor.
            Example: POKEMON_NAME (depends on which pokemon is sent to battle),
                     DAMAGE_AMOUNT (depends on last attack)
AUTO    — Resolved automatically from game state. Always available, no one provides them.
            Example: PLAYER_NAME (from save data), MONEY (from inventory),
                     BADGE_COUNT (from progress), TIME_OF_DAY (from time system)
```

**Convention path:** `gameData/assets/dialogues/variables.dialogue-vars.json`

**Automatic loading:** The `DialogueLoader` loads the `DialogueVariables` asset from the convention path on first use and caches it. All dialogues share this single instance. The editor panels and inspectors access it the same way — `Assets.load("dialogues/variables.dialogue-vars.json")`. If the file doesn't exist, the loader creates it with an empty list on first editor save.

**Why a separate asset instead of a Java enum?**
- The project uses standard OpenJDK 25 (not JBR/DCEVM). The existing hot-reload (Ctrl+Shift+R) re-scans registries but **cannot pick up structural changes** like new enum members — only method body changes.
- A DCEVM setup would allow enum hot-reload, but JBR support for Java 25 is uncertain and hasn't been configured.
- An asset can be edited and hot-reloaded at runtime without restarting the editor.
- A `DialogueVariablesLoader` handles load/save/hot-reload, just like any other asset.

**JSON format:**
```json
{
  "variables": [
    { "name": "PLAYER_NAME",  "type": "AUTO" },
    { "name": "MONEY",        "type": "AUTO" },
    { "name": "BADGE_COUNT",  "type": "AUTO" },
    { "name": "TRAINER_NAME", "type": "STATIC" },
    { "name": "TOWN_NAME",    "type": "STATIC" },
    { "name": "POKEMON_NAME", "type": "RUNTIME" },
    { "name": "DAMAGE_AMOUNT","type": "RUNTIME" }
  ]
}
```

**Editor editing:** Clicking the `variables.dialogue-vars.json` file in the `AssetBrowserPanel` selects it and shows it in the `InspectorPanel` via the standard asset selection flow (`EditorSelectionManager.selectAsset()`). A custom `DialogueVariablesInspectorRenderer` registered in `AssetInspectorRegistry` renders the variable list with add/remove/edit and a save button. This follows the same pattern as `SpriteInspectorRenderer` and `AnimationInspectorRenderer`.

**Usage:** The editor uses this global list for:
- Autocomplete when inserting variables into lines
- Validation warnings when a `[TAG]` in a line doesn't match any known variable
- Populating the variable table in `DialogueComponentInspector`:
  - **STATIC** variables show an editable text field
  - **RUNTIME** variables show as read-only with label "Set at runtime"
  - **AUTO** variables show as read-only with label "Auto" — always available, resolved from game state

### Variable Resolution at Runtime

When a dialogue starts, variables are merged from three sources in order (each layer overrides the previous):

```java
// In PlayerDialogueManager.startDialogue(dialogue, staticVars, runtimeVars)
Map<String, String> merged = new HashMap<>();

// 1. Auto variables — resolved from game state, always available
merged.putAll(variableResolver.resolveAutoVariables());

// 2. Static variables (set in editor via DialogueComponent inspector)
if (staticVars != null) merged.putAll(staticVars);

// 3. Runtime variables from the caller (set by code) — overrides static if same key
if (runtimeVars != null) merged.putAll(runtimeVars);
```

### DialogueVariableResolver

Resolves AUTO variables from game state. Each auto variable has a registered `Supplier<String>` that evaluates the current value on demand.

```java
public class DialogueVariableResolver {
    private final Map<String, Supplier<String>> resolvers = new LinkedHashMap<>();

    public void register(String variableName, Supplier<String> supplier) {
        resolvers.put(variableName, supplier);
    }

    /** Evaluates all registered auto variables and returns their current values. */
    public Map<String, String> resolveAutoVariables() {
        Map<String, String> result = new HashMap<>();
        for (var entry : resolvers.entrySet()) {
            String value = entry.getValue().get();
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
}
```

**Registration** happens once at game startup (e.g. in a setup component or initialization routine):

```java
variableResolver.register("PLAYER_NAME", () -> SaveManager.getPlayerName());
variableResolver.register("MONEY",       () -> String.valueOf(inventory.getMoney()));
variableResolver.register("BADGE_COUNT", () -> String.valueOf(progress.getBadgeCount()));
```

**Key point:** AUTO variables are always available in every dialogue — no NPC or system needs to provide them. They are evaluated fresh each time a dialogue starts, so they always reflect current game state.

**Who calls which overload?**

| Caller | Overload | Variables |
|--------|----------|-----------|
| `DialogueComponent.interact()` | `startDialogue(dialogue, staticVars)` | Only editor-set static vars. No runtime context. |
| Encounter system | `startDialogue(dialogue, staticVars, runtimeVars)` | Static + `POKEMON_NAME` from encounter |
| Battle system | `startDialogue(dialogue, staticVars, runtimeVars)` | Static + `DAMAGE_AMOUNT` from battle |
| Quest system | `startDialogue(dialogue, staticVars, runtimeVars)` | Static + `QUEST_TARGET` from quest |

```java
// Example: Simple NPC — DialogueComponent.interact() calls two-arg overload
manager.startDialogue(dialogue, variables);  // variables = editor-set static vars only

// Example: Encounter system — needs runtime context
Map<String, String> runtimeVars = Map.of("POKEMON_NAME", encounter.getPokemon().getName());
manager.startDialogue(dialogue, npcStaticVars, runtimeVars);
```

### JSON Format

The dialogue name is **not stored in JSON** — it is derived from the filename at load time by the `DialogueLoader`. For example, `professor_greeting.dialogue.json` → display name `"professor_greeting"`. The editor toolbar shows this derived name (read-only). Renaming is done via the asset browser (file rename).

```json
{
  "entries": [
    { "type": "LINE", "text": "Hello [PLAYER_NAME]!" },
    { "type": "LINE", "text": "Your [POKEMON_NAME] looks healthy." },
    { "type": "LINE", "text": "What would you like to do?" },
    {
      "type": "CHOICES",
      "hasChoices": true,
      "choices": [
        {
          "text": "Battle",
          "action": { "type": "BUILT_IN_EVENT", "builtInEvent": "START_BATTLE" }
        },
        {
          "text": "Tell me more",
          "action": { "type": "DIALOGUE", "dialogue": "dialogues/professor_lore.dialogue.json" }
        },
        {
          "text": "Goodbye",
          "action": { "type": "BUILT_IN_EVENT", "builtInEvent": "END_CONVERSATION" }
        }
      ]
    }
  ]
}
```

### Variable Substitution

When a line is displayed, `[VAR_NAME]` tokens are replaced with values from a `Map<String, String>` provided by the `DialogueComponent` at interaction time.

**If a variable is not set:**
- The tag is rendered literally: `[POKEMON_NAME]` stays as `[POKEMON_NAME]`.
- A warning is logged: `WARN: Dialogue 'Professor Greeting' — variable 'POKEMON_NAME' not set`.
- **No crash, no error popup.** This is a data issue, not a code error. Rendering the raw tag makes it visually obvious to the developer during playtesting.

### Asset Pipeline Integration

```
DialogueLoader implements AssetLoader<Dialogue>
├── load(path)              → JSON → Dialogue (manual parsing)
├── save(dialogue, path)    → Dialogue → JSON
├── getSupportedExtensions()→ [".dialogue.json"]
├── getPlaceholder()        → Empty Dialogue (one empty line, name derived from path)
├── supportsHotReload()     → true (editor live editing)
├── reload(existing, path)  → Mutate existing instance fields
├── getEditorPanelType()    → EditorPanelType.DIALOGUE_EDITOR
└── getIconCodepoint()      → speech bubble icon
```

Files stored in: `gameData/assets/dialogues/`

### Deserialization Strategy

Follows the `AnimatorControllerLoader` pattern: **manual JSON parsing with type discrimination**, not Gson's polymorphic deserialization (no RuntimeTypeAdapterFactory, no custom TypeAdapter).

**Why manual parsing?**
- The project's `Serializer` is used for Component serialization only.
- All existing `AssetLoader` implementations (AnimatorControllerLoader, AnimationLoader, etc.) parse JSON manually using Gson's low-level API (`JsonObject`, `JsonArray`).
- Manual parsing keeps control over validation and error messages.

**Type discrimination for entries:**
```java
// In DialogueLoader.load()
JsonArray entriesJson = root.getAsJsonArray("entries");
for (JsonElement elem : entriesJson) {
    JsonObject entryObj = elem.getAsJsonObject();
    String type = entryObj.get("type").getAsString();
    switch (type) {
        case "LINE"    -> entries.add(parseLine(entryObj));
        case "CHOICES" -> entries.add(parseChoiceGroup(entryObj));
        default -> LOG.warn("Unknown entry type: " + type);
    }
}
```

**Dialogue references in ChoiceAction (asset path strings):**

Following the Animator pattern, dialogue references are stored as **path strings** in JSON and loaded lazily via `Assets.load()`:

```json
{ "type": "DIALOGUE", "dialogue": "dialogues/professor_lore.dialogue.json" }
```

```java
// In DialogueLoader.parseChoiceAction()
case DIALOGUE -> {
    String dialoguePath = actionObj.get("dialogue").getAsString();
    action.setDialoguePath(dialoguePath);  // Stored as string
}

// In ChoiceAction — runtime resolution:
public Dialogue getDialogue() {
    return Assets.load(dialoguePath, Dialogue.class);  // Lazy load from cache
}
```

The `ChoiceAction` stores a `String dialoguePath` (serialized) and resolves it to a `Dialogue` asset on demand via `Assets.load()`. This avoids circular loading issues and matches how `AnimatorState` handles `Animation` references.

---

## 2. Input System — PlayerInput Component (Prerequisite)

**This is a prerequisite that should be implemented before the dialogue system.** It should have its own plan and PR.

### Problem

Currently, components read `Input` directly (e.g. `GridMovement` calls `Input.getKeyDown()`, `InteractionController` calls `Input.isActionPressed()`). There is no centralized concept of input modes, so blocking overworld input during dialogue requires disabling components — which has side effects (de-registration, lost state).

### Design: PlayerInput Component

A new `PlayerInput` component on the Player GameObject becomes the **single source of truth** for game input. The `Input` class itself is untouched — `PlayerInput` wraps it.

```
@ComponentMeta(category = "Player")
PlayerInput extends Component

enum InputMode:
  OVERWORLD       // Normal movement, interaction, menu
  DIALOGUE        // Dialogue advance, choice navigation
  BATTLE          // Future: battle input
  MENU            // Future: menu navigation
```

**Two patterns for consuming input:**

**1. Polled values (every-frame data like movement direction):**

`PlayerInput` reads `Input` every frame and exposes values. Consumers check the mode themselves before using the value:

```java
// PlayerInput publishes every frame:
public Vector2f getMovement() { ... }    // Reads Input directional keys
public boolean isInteractPressed() { ... }  // Reads Input.isActionPressed(INTERACT)

// PlayerMovement consumes:
void update(float dt) {
    if (playerInput.getMode() == InputMode.OVERWORLD) {
        Vector2f movement = playerInput.getMovement();
        // move...
    }
}

// PlayerDialogueManager consumes the same movement for choice navigation:
void update(float dt) {
    if (playerInput.getMode() == InputMode.DIALOGUE) {
        Vector2f movement = playerInput.getMovement();
        // navigate choices with movement.y...
    }
}
```

**2. Callbacks (discrete actions like interact press, menu open):**

Components register for specific actions. `PlayerInput` only fires callbacks when the current mode matches:

```java
// Registration (in onStart):
playerInput.onInteract(InputMode.OVERWORLD, this::handleOverworldInteract);
playerInput.onInteract(InputMode.DIALOGUE, this::handleDialogueAdvance);

// PlayerInput dispatches:
void update(float dt) {
    if (Input.isActionPressed(InputAction.INTERACT)) {
        for (var callback : interactCallbacks) {
            if (callback.mode == currentMode) {
                callback.handler.run();
            }
        }
    }
}
```

**Mode switching:**

```java
// PlayerDialogueManager calls:
playerInput.setMode(InputMode.DIALOGUE);  // On dialogue start
playerInput.setMode(InputMode.OVERWORLD); // On dialogue end
```

**Key principle:** Movement is a shared concept. UP/DOWN means "walk" in overworld and "navigate choices" in dialogue. The `PlayerInput` always provides the movement vector — consumers decide if their mode is active before acting on it. No callback needed for per-frame data.

### What Changes in Existing Code

| Component | Before | After |
|-----------|--------|-------|
| `GridMovement` / PlayerMovement | Reads `Input` directly | Reads `playerInput.getMovement()`, gates on `isOverworld()` |
| `InteractionController` | Reads `Input.isActionPressed(INTERACT)` | Registers callback for `OVERWORLD` interact |
| `PlayerPauseUI` | Reads `Input.isActionPressed(MENU)` | Registers callback for `OVERWORLD` menu |
| `PlayerDialogueManager` | (new) | Registers callbacks for `DIALOGUE` interact, reads movement for choice nav |

### Why This Approach

- **No component disabling** — `GridMovement` stays enabled, keeps its tile registration. It simply doesn't receive movement input because the mode is wrong.
- **Single source of truth** — Only `PlayerInput` talks to `Input`. Other player components go through it.
- **Extensible** — Adding `BATTLE` or `MENU` mode later is just a new enum value + new callbacks.
- **Movement reuse** — The same directional input drives walking, choice selection, and future menu navigation.

---

## 3. Game Pause During Dialogue

### Problem

While the player is in dialogue, the world should freeze:
- NPCs stop moving (pattern movement, random walks)
- No random encounters trigger
- No timers advance
- But dialogue UI still animates (text reveal, choice cursor)

### Design: IPausable Interface

Disabling `GridMovement` directly is problematic: it may de-register from the occupancy map, and every new pausable system would require updating the `PlayerDialogueManager` — violating the Open/Closed principle.

Instead, any component that should freeze during dialogue (or other pause scenarios) implements `IPausable`:

```java
public interface IPausable {
    void onPause();
    void onResume();
}
```

**Components implement it themselves** — they know best how to freeze their own state:

```java
// GridMovement implements IPausable
@Override
public void onPause() {
    paused = true;
    // Mid-tile transition is acceptable — the NPC simply freezes in place.
    // No new moves are accepted while paused.
}

@Override
public void onResume() {
    paused = false;
}

@Override
public void update(float deltaTime) {
    if (paused) return;  // Early-out — component stays enabled, keeps registrations
    // normal movement logic...
}
```

**Key difference from `setEnabled(false)`:** The component stays enabled. It keeps its tile registrations, its `update()` still runs (for the early-out check), and no lifecycle side effects occur. Pausing mid-transition is fine — the NPC freezes visually between tiles and resumes smoothly on `onResume()`.

### Pause Dispatch

The `PlayerDialogueManager` (or any system that needs to pause the world) queries the scene for all `IPausable` components:

```java
// On dialogue start
for (IPausable pausable : scene.getComponentsImplementing(IPausable.class)) {
    pausable.onPause();
}

// On dialogue end (only when conversation is truly over, not between chained dialogues)
for (IPausable pausable : scene.getComponentsImplementing(IPausable.class)) {
    pausable.onResume();
}
```

**Chaining behavior:** When a DIALOGUE choice action chains to another dialogue, `IPausable` components **stay paused** throughout. `onPause()` is called once when the first dialogue opens, `onResume()` is called once when the last dialogue closes. No resume+re-pause between chains — this avoids visual glitches (NPCs twitching for one frame).

**SOLID compliance:** Adding a new pausable system (e.g. `WanderingNPC`, `WeatherSystem`) only requires implementing `IPausable` on that component. The `PlayerDialogueManager` never changes.

### Scene Query: `getComponentsImplementing()`

If the scene doesn't already support querying by interface, this needs to be added. The query iterates all components in the scene and checks `instanceof IPausable`. For the handful of pausable components in a typical scene, this is negligible cost.

---

## 4. PlayerDialogueManager Component

Component on the overworld Player GameObject. Orchestrates the full dialogue lifecycle.

```
@ComponentMeta(category = "Dialogue")
PlayerDialogueManager extends Component

Fields (serialized):
  @ComponentReference(source = KEY) UIText    dialogueText      // Text display
  @ComponentReference(source = KEY) UIPanel   dialogueBox       // Background panel
  @ComponentReference(source = KEY) UIImage   continueIndicator // Blinking ▼ arrow
  @ComponentReference(source = KEY) UIPanel   choicePanel       // Choice container
  @ComponentReference(source = KEY) UIText    choice1Text       // Choice labels (up to 4)
  @ComponentReference(source = KEY) UIText    choice2Text
  @ComponentReference(source = KEY) UIText    choice3Text
  @ComponentReference(source = KEY) UIText    choice4Text
  float              charsPerSecond = 30f                       // Typewriter speed

Fields (transient / runtime):
  DialogueVariableResolver variableResolver // Auto variable resolver (initialized in onStart)
  Dialogue           currentDialogue
  int                currentEntryIndex
  Map<String,String> currentVariables       // Merged auto + static + runtime
  boolean            isActive
  int                selectedChoice         // For choice navigation
  int                visibleChars           // Typewriter progress
  String             fullText               // Current line after variable substitution

References resolved at start:
  @ComponentReference(source = SELF) PlayerInput playerInput
```

### Auto Variable Registration

The `PlayerDialogueManager` creates and populates the `DialogueVariableResolver` in `onStart()`. It queries sibling/scene components for the data sources:

```java
@Override
protected void onStart() {
    variableResolver = new DialogueVariableResolver();

    // Player identity — from save data
    variableResolver.register("PLAYER_NAME", () -> SaveManager.getPlayerName());

    // Economy — from Inventory component (on same GameObject or scene)
    Inventory inventory = getComponent(Inventory.class);
    if (inventory != null) {
        variableResolver.register("MONEY", () -> String.valueOf(inventory.getMoney()));
    }

    // Progress — from ProgressTracker component
    ProgressTracker progress = getComponent(ProgressTracker.class);
    if (progress != null) {
        variableResolver.register("BADGE_COUNT", () -> String.valueOf(progress.getBadgeCount()));
    }

    // ... other auto variables registered here as systems are built
}
```

**Key points:**
- Registration happens once. The `Supplier<String>` lambdas capture component references and evaluate fresh values each time a dialogue starts.
- If a component is not found (e.g. `Inventory` doesn't exist yet), the auto variable is simply not registered — the `[TAG]` renders literally and logs a warning, same as any unset variable.
- New auto variables are added here as new systems are built (party, time, quest progress, etc.).

### State Machine

```
                    ┌────────────┐
                    │   IDLE     │ ← Component waits
                    └─────┬──────┘
                          │ startDialogue(dialogue, variables)
                          ▼
                    ┌────────────┐
                    │  SHOWING   │ ← Displaying a line (typewriter effect)
                    │   LINE     │   INTERACT → skip to full text
                    └─────┬──────┘
                          │ INTERACT (line fully shown)
                          ▼
                   ┌──────┴──────┐
                   │ More lines? │
                   └──┬───────┬──┘
                  Yes │       │ No
                      ▼       ▼
               ┌──────────┐ ┌─────────────┐
               │ SHOWING  │ │ Last entry  │
               │  LINE    │ │ is choices? │
               └──────────┘ └──┬───────┬──┘
                           Yes │       │ No
                               ▼       ▼
                     ┌────────────┐ ┌──────┐
                     │  SHOWING   │ │ END  │ → Re-enable, hide UI
                     │  CHOICES   │ └──────┘
                     │ ↑↓ nav    │
                     │ INTERACT  │
                     └─────┬─────┘
                           │ Choice selected
                           ▼
                    ┌──────┴──────┐
                    │ action type │
                    └──┬───────┬──┘
                DIALOGUE│    │EVENT
                        ▼    ▼
                 Start new  Dispatch event
                 dialogue   → END state
```

### Reentrancy: Internal Chaining vs External Calls

`startDialogue()` can be called from two contexts:

| Context | Caller | `isActive` | Behavior |
|---------|--------|------------|----------|
| **External** | `DialogueComponent.interact()` | `false` | Normal entry — pause IPausable, switch to DIALOGUE mode |
| **Internal chain** | Choice action handler (inside the manager) | `true` | Reset entry index, load new dialogue, continue — no pause/resume, no mode switch |

The external guard (`!manager.isActive()`) lives in `DialogueComponent.interact()`, not in `startDialogue()` itself. The manager's internal chaining calls `startDialogue()` directly, bypassing that guard. Inside `startDialogue()`, the method checks `isActive` to decide whether this is a fresh conversation (pause + mode switch) or a chain (just reset state):

```java
public void startDialogue(Dialogue dialogue, Map<String, String> staticVars, Map<String, String> runtimeVars) {
    // ... null/empty guards (see Runtime Validation) ...

    boolean isChain = isActive;  // Already in a conversation?

    if (!isChain) {
        // Fresh conversation — set up environment
        isActive = true;
        playerInput.setMode(InputMode.DIALOGUE);
        pauseAll();
    }

    // Reset dialogue state (both fresh and chain)
    currentDialogue = dialogue;
    currentEntryIndex = 0;
    mergeVariables(staticVars, runtimeVars);
    showEntry(currentDialogue.getEntries().get(0));
}
```

### API

```java
/**
 * Convenience overload for simple NPC conversations (DialogueComponent.interact()).
 * Only static variables — no runtime context needed.
 */
public void startDialogue(Dialogue dialogue, Map<String, String> staticVars) {
    startDialogue(dialogue, staticVars, Map.of());
}

/**
 * Full entry point for systems that provide runtime variables (encounter, battle, quest).
 * Merges static + runtime variables. Runtime overrides static if same key.
 *
 * Guards: if dialogue is null or has 0 entries, logs an error and calls endDialogue()
 * (or stays in IDLE if not yet active). No exception thrown.
 */
public void startDialogue(Dialogue dialogue, Map<String, String> staticVars, Map<String, String> runtimeVars) { ... }

/** Called by choice action or end of lines. Dispatches DialogueComponent.onConversationEnd if set, resumes IPausable, restores input mode. */
public void endDialogue() { ... }

/** Dispatches built-in or custom events (line-level onCompleteEvent, choice actions). */
private void dispatchEvent(DialogueEventRef eventRef) { ... }
```

**Two overloads, two use cases:**
- `startDialogue(dialogue, staticVars)` — Called by `DialogueComponent.interact()`. Simple NPCs that only have editor-set variables.
- `startDialogue(dialogue, staticVars, runtimeVars)` — Called by game systems (encounter, battle, quest) that have programmatic context to inject. Example: encounter system provides `POKEMON_NAME`.

### Runtime Validation

All error paths **log and recover gracefully** — no exceptions thrown to the caller.

```java
// In startDialogue():
if (dialogue == null) {
    LOG.error("startDialogue() called with null dialogue");
    if (isActive) endDialogue();  // Clean up if chaining from a previous dialogue
    return;
}
if (dialogue.getEntries().isEmpty()) {
    LOG.error("Dialogue '" + dialogue.getName() + "' has no entries");
    if (isActive) endDialogue();
    return;
}

// In choice action handling (DIALOGUE type):
Dialogue target = choiceAction.getDialogue();  // Assets.load() — may return null/placeholder
if (target == null || target.getEntries().isEmpty()) {
    LOG.error("Choice action references missing/empty dialogue: " + choiceAction.getDialoguePath());
    endDialogue();  // Close dialogue UI cleanly
    return;
}
startDialogue(target, currentVariables);  // Internal chain — bypasses isActive() guard
```

**Principle:** Invalid data should never crash the game. Log the error, close the dialogue box, return the player to normal gameplay.

### Typewriter Effect

Text is revealed character-by-character with configurable speed.

```java
private float charsPerSecond = 30f;
private float charTimer = 0f;
private int visibleChars = 0;
private String fullText;

void updateTextReveal(float deltaTime) {
    if (visibleChars < fullText.length()) {
        charTimer += deltaTime;
        int newChars = (int)(charTimer * charsPerSecond);
        if (newChars > 0) {
            visibleChars = Math.min(visibleChars + newChars, fullText.length());
            charTimer -= newChars / charsPerSecond;
            dialogueText.setText(fullText.substring(0, visibleChars));
        }
    }
}
```

When the player presses INTERACT during typewriter:
- If text is still revealing → instantly show full text (`visibleChars = fullText.length()`)
- If text is fully shown → advance to next entry

---

## 5. Dialogue Events

### Problem

A dialogue choice fires an event like `"OPEN_DOOR"`. Multiple objects might need to react:
- A door elsewhere opens
- An NPC changes behavior
- A quest flag is set

Events shouldn't be limited to choices — they can fire at multiple points in the dialogue lifecycle.

### Built-In vs Custom Events

**Built-in events** — Hard-coded in the `DialogueEvent` enum. Engine-level actions that always behave the same way regardless of source:

```java
public enum DialogueEvent {
    START_BATTLE,       // Triggers battle transition (handled by PlayerDialogueManager or future BattleManager)
    END_CONVERSATION;   // Ends dialogue cleanly (no further action)
}
```

**Custom events** — Defined in a global `DialogueEvents` asset (same convention-path pattern as `DialogueVariables`). Game-specific, handled by `DialogueEventListener` components in the scene.

```
DialogueEvents asset (gameData/assets/dialogues/events.dialogue-events.json)
└── events: List<String>     // e.g. ["OPEN_DOOR", "GIVE_ITEM", "UNLOCK_PATH"]
```

**Editor editing:** Same as `DialogueVariables` — clicking `events.dialogue-events.json` in `AssetBrowserPanel` shows it in `InspectorPanel` via a custom `DialogueEventsInspectorRenderer` registered in `AssetInspectorRegistry`. Renders the event list with add/remove/rename and a save button.

**Why this split?**
- Built-in events have engine-level meaning — the `PlayerDialogueManager` reacts to them directly (e.g. `START_BATTLE` triggers a battle transition). Enum = typo-proof, discoverable.
- Custom events are game-content-specific. They live in an asset so they can be added without restarting the editor, and validated against a known list to prevent spelling errors.
- The editor shows built-in events as an enum dropdown, custom events as a validated dropdown from the asset.

### Event Hook Points

Events exist at two levels:

**1. Per-line hooks (on the Dialogue asset):**

```
Dialogue
├── entries:
│   ├── DialogueLine
│   │   ├── text: String
│   │   └── onCompleteEvent: DialogueEventRef?  // Fires when player advances past this line
│   └── DialogueChoiceGroup
│       └── choices:
│           └── Choice
│               ├── text: String
│               └── action: ChoiceAction         // Contains its own event ref
```

**2. Per-NPC conversation hook (on the DialogueComponent):**

```
DialogueComponent
└── onConversationEnd: DialogueEventRef?   // Fires when the entire conversation ends (after all chaining)
```

Dialogue start/end are **global lifecycle signals** — the `PlayerDialogueManager` switches input mode and pauses `IPausable` components automatically for every dialogue. No per-dialogue configuration needed. NPC-specific post-dialogue triggers (e.g. trainer → `START_BATTLE`) live on the `DialogueComponent` via `onConversationEnd`.

`DialogueEventRef` wraps the built-in/custom distinction:

```
DialogueEventRef
├── category: BUILT_IN | CUSTOM
├── builtInEvent: DialogueEvent     // If BUILT_IN (enum)
└── customEvent: String             // If CUSTOM (validated against DialogueEvents asset)
```

| Hook Point | When It Fires | Example Use |
|---|---|---|
| `DialogueLine.onCompleteEvent` | Player advances past this line (fires in every dialogue, including chained) | Sound effect, NPC animation, camera shake |
| `Choice.action` | Player selects a choice | `OPEN_DOOR`, branch to another dialogue |
| `DialogueComponent.onConversationEnd` | `endDialogue()` is called — the conversation is truly over (not during chaining) | `START_BATTLE` after trainer speech, cleanup |

### Dialogue Chaining Semantics

When a DIALOGUE choice action chains from dialogue A to dialogue B:

```
Dialogue A opens       → PlayerDialogueManager pauses IPausable, switches to DIALOGUE mode
A's lines play         → A's line-level onCompleteEvents fire normally
Player picks choice    → chains to B
                       → A ends silently (no endDialogue, no resume, no mode switch)
                       → B starts as internal chain (isActive already true)
B's lines play         → B's line-level onCompleteEvents fire normally
B ends                 → endDialogue() called → DialogueComponent.onConversationEnd fires
                       → IPausable resumed, input mode restored to OVERWORLD
```

**Principle:** Line-level `onCompleteEvent` fires normally in every dialogue. Conversation-level lifecycle (pause/resume, input mode switch, `onConversationEnd`) only fires at the boundaries of the **entire conversation**, not between chained dialogues.

**Choice action execution:**
- **DIALOGUE** → call `startDialogue()` internally (chain). Do NOT call `endDialogue()`. The current dialogue ends silently, the new one starts.
- **BUILT_IN_EVENT** → call `dispatchEvent()`, then `endDialogue()`.
- **CUSTOM_EVENT** → call `dispatchEvent()` to scene listeners + `DialogueEventStore.markFired()`, then `endDialogue()`.

**Why?** If a trainer's `onConversationEnd` is `START_BATTLE` and a choice chains to dialogue B, the battle should not start until B finishes and `endDialogue()` is actually called.

**Example — Trainer encounter (battle after dialogue, configured on DialogueComponent):**

The trainer NPC's `DialogueComponent` has `onConversationEnd = { category: BUILT_IN, builtInEvent: START_BATTLE }`. The dialogue asset itself is pure data:
```json
{
  "entries": [
    { "type": "LINE", "text": "I've been waiting for you!" },
    { "type": "LINE", "text": "Let's battle!" }
  ]
}
```

**Example — Line-level event (sound on specific line):**
```json
{
  "entries": [
    { "type": "LINE", "text": "Did you hear that?",
      "onCompleteEvent": { "category": "CUSTOM", "customEvent": "PLAY_RUMBLE" } },
    { "type": "LINE", "text": "Something is coming..." }
  ]
}
```

### DialogueEventStore — Persistence Helper

A thin wrapper over `SaveManager` that encapsulates the namespace constant. All event persistence goes through this class — no raw strings passed to `SaveManager` anywhere else.

```java
public class DialogueEventStore {
    private static final String NAMESPACE = "dialogue_events";

    /** Record that a custom event has been fired. Persists across scenes and save/load. */
    public static void markFired(String eventName) {
        SaveManager.setGlobal(NAMESPACE, eventName, true);
    }

    /** Check if a custom event has ever been fired this playthrough. */
    public static boolean hasFired(String eventName) {
        return SaveManager.getGlobal(NAMESPACE, eventName, false);
    }
}
```

The `eventName` parameter always comes from a validated source — either the `DialogueEvents` asset dropdown (in the editor) or the `DialogueEventListener.eventName` field (which was itself set via that dropdown). Nobody types event name strings by hand.

### Design: Component-Based Event Listener (Custom Events)

**Scene example — Door reacts to dialogue event:**

```
Door (GameObject)
├── SpriteRenderer: door_closed.png
├── TriggerZone: solid=true
└── DialogueEventListener
    ├── eventName: "OPEN_DOOR"
    └── reaction: DISABLE_GAME_OBJECT
```

When `"OPEN_DOOR"` fires, the listener disables the door GameObject (making it invisible and non-blocking). Multiple objects can react to the same event — each has its own `DialogueEventListener`.

```
@ComponentMeta(category = "Dialogue")
DialogueEventListener extends Component

Fields:
  String                eventName        // Dropdown populated from DialogueEvents asset (not free-text)
  DialogueReaction      reaction         // What to do when event fires

enum DialogueReaction:
  ENABLE_GAME_OBJECT     // Enable target GameObject
  DISABLE_GAME_OBJECT    // Disable target GameObject
  DESTROY_GAME_OBJECT    // Remove target GameObject
  RUN_ANIMATION          // Play animation on target
```

**Full component example:**

```java
@ComponentMeta(category = "Dialogue")
public class DialogueEventListener extends Component {

    @Required String eventName;  // Set via dropdown in custom inspector, never typed
    DialogueReaction reaction;

    @Override
    protected void onStart() {
        if (eventName == null || eventName.isBlank()) {
            LOG.warn("DialogueEventListener on '" + getGameObject().getName() + "' has no event name — skipping");
            return;
        }
        // On scene load, check if this event was already fired (cross-scene support)
        if (DialogueEventStore.hasFired(eventName)) {
            onDialogueEvent();
        }
    }

    public void onDialogueEvent() {
        switch (reaction) {
            case ENABLE_GAME_OBJECT  -> getGameObject().setEnabled(true);
            case DISABLE_GAME_OBJECT -> getGameObject().setEnabled(false);
            case DESTROY_GAME_OBJECT -> getGameObject().getScene().removeGameObject(getGameObject());
            case RUN_ANIMATION -> {
                AnimationComponent anim = getComponent(AnimationComponent.class);
                if (anim != null) {
                    anim.play();
                } else {
                    LOG.warn("RUN_ANIMATION reaction on '" + getGameObject().getName()
                        + "' but no AnimationComponent found — skipping");
                }
            }
        }
    }
}
```

**Custom inspector — dropdown from asset, not free-text:**

```java
@InspectorFor(DialogueEventListener.class)
public class DialogueEventListenerInspector extends CustomComponentInspector<DialogueEventListener> {

    @Override
    public boolean draw() {
        DialogueEvents events = Assets.load("dialogues/events.dialogue-events.json");
        List<String> eventNames = events.getEvents();

        int currentIndex = eventNames.indexOf(component.eventName);
        if (component.eventName == null || component.eventName.isBlank()) {
            // @Required — red highlight, no event selected
            ImGui.textColored(1, 0.3f, 0.3f, 1, "⚠ No event selected");
        } else if (currentIndex == -1) {
            // Stale event name — show warning
            ImGui.textColored(1, 0.4f, 0.4f, 1, "⚠ Unknown event: " + component.eventName);
        }
        // Dropdown populated from the DialogueEvents asset
        if (FieldEditors.dropdown("Event", eventNames, currentIndex)) {
            component.eventName = eventNames.get(selectedIndex);
        }

        FieldEditors.enumField("Reaction", component.reaction);
        return changed;
    }
}
```

**Validation:** The `eventName` is never typed by hand — the inspector renders it as a dropdown from the `DialogueEvents` asset. If the asset is updated (event renamed or removed), listeners with stale names show a warning. No `Dialogue` reference needed — the `DialogueEvents` asset is the single source of truth.

### Event Dispatch

The `PlayerDialogueManager` handles both built-in and custom events:

```java
private void dispatchEvent(DialogueEventRef eventRef) {
    if (eventRef == null) return;

    if (eventRef.category == BUILT_IN) {
        // Handled directly by the manager
        switch (eventRef.builtInEvent) {
            case START_BATTLE -> startBattleTransition();
            case END_CONVERSATION -> endDialogue();
        }
    } else {
        // Dispatch to scene listeners + persist for cross-scene
        String eventName = eventRef.customEvent;
        for (DialogueEventListener listener : scene.getComponentsOfType(DialogueEventListener.class)) {
            if (eventName.equals(listener.getEventName())) {
                listener.onDialogueEvent();
            }
        }
        DialogueEventStore.markFired(eventName);
    }
}
```

**Why not a static event bus?**
- A static bus creates hidden dependencies and makes scene serialization harder.
- Component-based listeners are visible in the editor, inspectable, and serialized with the scene.
- Finding listeners via scene query is O(n) but n is tiny (handful of listeners per scene).
- Later, if many events are needed, we can add an index. But not now.

### Persistence & Cross-Scene Events

**Same-scene events** work immediately — the `DialogueEventListener` is in the loaded scene and receives the dispatch directly. `DialogueEventStore.markFired()` also records it for future scenes.

**Cross-scene events** (e.g. carpenter agrees to fix a bridge in another scene) — When the target scene loads, `DialogueEventListener.onStart()` calls `DialogueEventStore.hasFired()` and reacts immediately if the event was already fired.

#### Save Data Format

Events are stored as flags in the `"dialogue_events"` namespace of `SaveData.globalState`:

```json
{
  "globalState": {
    "dialogue_events": {
      "FIX_BRIDGE": true,
      "OPEN_DOOR": true,
      "GIVE_ITEM": true
    }
  }
}
```

Each fired event is a key with value `true`. Events are only added, never removed (reset on new game when global state clears).

#### Lifecycle

| Moment | What Happens |
|--------|--------------|
| Custom event fires | `DialogueEventStore.markFired(eventName)` — recorded in SaveManager global state |
| Scene loads | Each `DialogueEventListener.onStart()` calls `DialogueEventStore.hasFired()` |
| Game save | Global state serialized automatically as part of `SaveData.globalState` |
| Game load | Global state restored — cross-scene events survive save/load |
| New game | Global state starts empty — no events have been fired |

---

## 6. DialogueComponent

Interactable component placed on NPCs and objects that start dialogue. Supports **conditional dialogue selection** — an NPC can say different things based on which dialogue events have been fired.

```
@ComponentMeta(category = "Dialogue")
@RequiredComponent(TriggerZone.class)
DialogueComponent extends InteractableComponent

Fields (serialized):
  List<ConditionalDialogue>     conditionalDialogues  // Ordered, first match wins
  @Required Dialogue            dialogue               // Default fallback dialogue
  Map<String, String>           variables              // Variable values (shared across all dialogues)
  DialogueEventRef              onConversationEnd      // Optional — fires when entire conversation ends (after all chaining)

Gizmo:
  GizmoShape.CIRCLE, color = purple (0.8f, 0.4f, 1.0f, 0.9f)
  // DIAMOND+cyan = Sign, SQUARE+gold = Chest — CIRCLE+purple is unique to dialogue
```

### Conditional Dialogue Selection

A `ConditionalDialogue` pairs a list of conditions with a dialogue asset. Conditions are evaluated top-to-bottom at interaction time — **first match wins**. If none match, the default `dialogue` is used.

```
ConditionalDialogue
├── conditions: List<DialogueCondition>   // ALL must be true (AND logic)
│   └── DialogueCondition
│       ├── eventName: String             // Dropdown from DialogueEvents asset
│       └── expectedState: FIRED | NOT_FIRED
└── dialogue: Dialogue                    // Asset to use if conditions match
```

**Condition evaluation:**

```java
// ConditionalDialogue
boolean allConditionsMet() {
    for (DialogueCondition cond : conditions) {
        boolean fired = DialogueEventStore.hasFired(cond.eventName);
        if (cond.expectedState == FIRED && !fired) return false;
        if (cond.expectedState == NOT_FIRED && fired) return false;
    }
    return true;  // All conditions passed
}
```

**Multiple conditions per entry** use AND logic. For OR logic, create separate entries pointing to the same dialogue.

**Example — NPC with 3 states:**

```
Entry 1: If GOT_BADGE_1 is FIRED AND TALKED_TO_RIVAL is FIRED → professor_congrats
Entry 2: If GOT_BADGE_1 is FIRED                              → professor_badge_only
Default:                                                       → professor_greeting
```

Order matters — more specific conditions go first.

**Persistence:** Conditions read from `DialogueEventStore.hasFired()`, which reads from `SaveManager.globalState`. No new persistence mechanism. The flow:

| Step | What happens |
|------|--------------|
| Player beats gym | Gym dialogue fires `GOT_BADGE_1` event → `DialogueEventStore.markFired()` |
| Player saves | `globalState` serialized (`"GOT_BADGE_1": true`) |
| Player loads | `globalState` restored |
| Player talks to NPC | `selectDialogue()` checks `hasFired("GOT_BADGE_1")` → returns `professor_congrats` |

### Behavior

```java
@Override
public void interact(GameObject player) {
    PlayerDialogueManager manager = player.getComponent(PlayerDialogueManager.class);
    if (manager != null && !manager.isActive()) {
        Dialogue selected = selectDialogue();
        manager.startDialogue(selected, variables, this);  // Pass self so manager can read onConversationEnd
    }
}

private Dialogue selectDialogue() {
    for (ConditionalDialogue cd : conditionalDialogues) {
        if (cd.allConditionsMet()) {
            return cd.getDialogue();
        }
    }
    return dialogue;  // Default fallback
}

@Override
public String getInteractionPrompt() {
    return "Talk";
}
```

### Custom Inspector

The `DialogueComponentInspector` shows:

```
┌─ DialogueComponent ──────────────────────────────────────┐
│                                                           │
│  Conditional Dialogues (first match wins):                │
│  ┌─ 1 ────────────────────────────────────────────────┐  │
│  │ If: [GOT_BADGE_1 ▾] is [FIRED ▾]                  │  │
│  │ AND: [TALKED_TO_RIVAL ▾] is [FIRED ▾]             │  │
│  │     [+ Add Condition]                               │  │
│  │ Then: [professor_congrats.dialogue ▾] [Open]        │  │
│  │                                                [╳]  │  │
│  └─────────────────────────────────────────────────────┘  │
│  ┌─ 2 ────────────────────────────────────────────────┐  │
│  │ If: [GOT_BADGE_1 ▾] is [FIRED ▾]                  │  │
│  │     [+ Add Condition]                               │  │
│  │ Then: [professor_badge_only.dialogue ▾] [Open]      │  │
│  │                                                [╳]  │  │
│  └─────────────────────────────────────────────────────┘  │
│  [+ Add Conditional Dialogue]                             │
│                                                           │
│  Default Dialogue: [professor_greeting.dialogue ▾] [Open] │
│                                                           │
│  On Conversation End: [BUILT_IN ▾] [START_BATTLE ▾] [╳]  │
│  (optional — fires when entire conversation ends)         │
│                                                           │
│  ▸ Preview (read-only, collapsed by default)              │
│  ┌────────────────────────────────────────────────────┐   │
│  │ 1. "Hello [PLAYER_NAME]!"                          │   │
│  │ 2. "What would you like to do?"                    │   │
│  │ ── Choices ──                                      │   │
│  │  ● Battle → EVENT: START_BATTLE                    │   │
│  │  ● Goodbye → EVENT: END_CONVERSATION               │   │
│  └────────────────────────────────────────────────────┘   │
│                                                           │
│  Variables ────────────────────────────────────────────   │
│  ┌──────────────┬────────────────────────┐                │
│  │ Variable     │ Value                  │                │
│  ├──────────────┼────────────────────────┤                │
│  │ PLAYER_NAME  │  auto                  │  ← disabled    │
│  │ MONEY        │  auto                  │  ← disabled    │
│  │ TRAINER_NAME │ [Prof. Oak___________] │  ← editable    │
│  │ POKEMON_NAME │  runtime               │  ← disabled    │
│  └──────────────┴────────────────────────┘                │
│  ⚠ Variables without values will show raw [TAG]           │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

**Key details:**
- Conditional dialogues are shown **above** the default dialogue. Order is top-to-bottom priority.
- Each condition's `eventName` is a **dropdown from the `DialogueEvents` asset** — no free-text.
- `expectedState` is a dropdown: `FIRED` or `NOT_FIRED`.
- The variable table is **derived from the global `DialogueVariables` asset**, filtered to variables that appear across **all** dialogues (default + conditional). Variables are shared — they apply to whichever dialogue is selected.
- **AUTO** variables show a disabled field with "auto" text — always available, resolved from game state by `DialogueVariableResolver`.
- **STATIC** variables show an editable text field. If the value is empty → show warning icon.
- **RUNTIME** variables show a disabled field with "runtime" text — they're set by code at dialogue start, not in the editor.
- The `DialogueComponent` only stores static variable values in its `Map<String, String>`.
- The "Open" button opens the `DialogueEditorPanel` focused on that dialogue.
- Preview section shows the **default** dialogue (collapsible, read-only).

---

## 7. DialogueEditorPanel

### Layout

Two-column layout following existing panel patterns (AnimationEditor, AnimatorEditor).

```
┌─ Dialogue Editor ───────────────────────────────────────────────────────────────────┐
│ ┌── LEFT COLUMN ──────────────┐ ┌── RIGHT COLUMN ─────────────────────────────────┐ │
│ │                              │ │                                                  │ │
│ │  [Search___________________ ]│ │  Toolbar: [Name: professor_greeting]  (read-only)│ │
│ │                              │ │    [Undo] [Redo] [Save] [Delete]                 │ │
│ │                              │ │    [Variables ⇗] [Events ⇗]  ← quick links      │ │
│ │  Dialogues:                  │ │                                                  │ │
│ │  ┌────────────────────────┐  │ │  ─── Lines ──────────────────────────────────    │ │
│ │  │▸ professor_greeting    │  │ │                                                  │ │
│ │  │  shop_welcome          │  │ │  ┌─ 1 ────────────────────────────────────────┐  │ │
│ │  │  guard_warning         │  │ │  │ Hello [PLAYER_NAME]!                       │  │ │
│ │  │⚠ quest_accept          │  │ │  │                            [+ Var ▾]  [╳]  │  │ │
│ │  │  quest_complete        │  │ │  └────────────────────────────────────────────┘  │ │
│ │  │  pokemon_center        │  │ │  ┌─ 2 ────────────────────────────────────────┐  │ │
│ │  │  battle_challenge      │  │ │  │ Your [POKEMAN_NAME] looks healthy.         │  │ │
│ │  └────────────────────────┘  │ │  │ ⚠ Unknown variable: POKEMAN_NAME           │  │ │
│ │                              │ │  │                            [+ Var ▾]  [╳]  │  │ │
│ │  [+ Add] [- Remove]         │ │  └────────────────────────────────────────────┘  │ │
│ │                              │ │  ┌─ 3 ────────────────────────────────────────┐  │ │
│ │                              │ │  │ What would you like to do?                 │  │ │
│ │                              │ │  │                            [+ Var ▾]  [╳]  │  │ │
│ │                              │ │  └────────────────────────────────────────────┘  │ │
│ │                              │ │                                                  │ │
│ │                              │ │  [+ Add Line]                                    │ │
│ │                              │ │                                                  │ │
│ │                              │ │  ─── Choices (optional) ─────────────────────    │ │
│ │                              │ │  ☑ Has choices                                   │ │
│ │                              │ │                                                  │ │
│ │                              │ │  ┌─ Choice 1 ─────────────────────────────────┐  │ │
│ │                              │ │  │ Text: [Battle_______________]              │  │ │
│ │                              │ │  │ Action: [BUILT_IN_EVENT ▾]                 │  │ │
│ │                              │ │  │ Event:  [START_BATTLE ▾]              [╳]  │  │ │
│ │                              │ │  └────────────────────────────────────────────┘  │ │
│ │                              │ │  ┌─ Choice 2 ─────────────────────────────────┐  │ │
│ │                              │ │  │ Text: [Tell me more________]               │  │ │
│ │                              │ │  │ Action: [DIALOGUE ▾]                        │  │ │
│ │                              │ │  │ Target: [professor_lore.dialogue ▾]   [╳]  │  │ │
│ │                              │ │  └────────────────────────────────────────────┘  │ │
│ │                              │ │  ┌─ Choice 3 ─────────────────────────────────┐  │ │
│ │                              │ │  │ Text: [Goodbye______________]              │  │ │
│ │                              │ │  │ Action: [BUILT_IN_EVENT ▾]                 │  │ │
│ │                              │ │  │ Event:  [END_CONVERSATION ▾]          [╳]  │  │ │
│ │                              │ │  └────────────────────────────────────────────┘  │ │
│ │                              │ │                                                  │ │
│ │                              │ │  [+ Add Choice]                                  │ │
│ │                              │ │                                                  │ │
│ └──────────────────────────────┘ └──────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Line Editor Details

**[+ Var] button:** Each line has a `[+ Var ▾]` dropdown button. Clicking it opens a dropdown populated from the `DialogueVariables` asset. Selecting a variable appends `[VAR_NAME]` at the end of the current line text. This ensures correct formatting — the user never types brackets manually.

**Variable validation warnings:** After each line edit, the editor scans for `[...]` tags and validates them against the `DialogueVariables` asset:
- **Unknown variable** (e.g. `[POKEMAN_NAME]` — typo): orange `⚠` warning shown below the line text.
- **Malformed tag** (e.g. `[PLAYER_NAME` — missing closing bracket): orange `⚠` warning shown below the line.
- **Valid variable** (e.g. `[PLAYER_NAME]`): no warning.

**Choice validation warnings:**
- **DIALOGUE action with no target dialogue:** orange `⚠` on the choice card and `⚠` in the dialogue list.
- **CUSTOM_EVENT action with no event selected:** same.
- **Empty choice text:** orange `⚠` on the choice card.

**Warnings in the dialogue list (left column):** If any line or choice in a dialogue has a validation warning, the dialogue name in the left column shows a `⚠` icon. This makes it easy to spot dialogues with issues at a glance without opening each one.

### Panel Features

| Feature | Implementation |
|---------|---------------|
| Undo/Redo | Own `Deque<DialogueState>` stack, independent from scene undo |
| Save | `Ctrl+S` saves current dialogue via `DialogueLoader.save()` |
| Shortcuts | Registered via `provideShortcuts()` using the `ShortcutRegistry` (see below) |
| Dirty tracking | `hasUnsavedChanges` flag, title shows `*` when dirty |
| Search | Filter dialogue list by name in left column |
| Drag reorder | Lines can be reordered by drag handle |
| Delete confirm | Delete dialogue shows confirmation popup |
| Quick links | Toolbar buttons to select `DialogueVariables` / `DialogueEvents` assets in the InspectorPanel (calls `selectionManager.selectAsset()`) |

### Undo/Redo

Follows the `AnimationEditorPanel` pattern:

```java
private final Deque<DialogueSnapshot> undoStack = new ArrayDeque<>();
private final Deque<DialogueSnapshot> redoStack = new ArrayDeque<>();
private static final int MAX_UNDO = 50;

// DialogueSnapshot: immutable deep copy of all editable dialogue state
private record DialogueSnapshot(String name, List<DialogueEntry> entries, List<String> variables) {
    static DialogueSnapshot capture(Dialogue d) { /* deep copy */ }
    void restore(Dialogue d) { /* write back */ }
}
```

**Undo stacks are cleared when switching dialogues.** When the user selects a different dialogue in the left column, `undoStack` and `redoStack` are both cleared. This prevents undoing into a previous dialogue's state. Matches the `AnimationEditorPanel` pattern where switching animations resets the undo history. If the current dialogue has unsaved changes, a confirmation popup appears before switching (same as `AssetInspector`'s unsaved changes flow).

### Shortcuts

All shortcuts are registered via the existing `ShortcutRegistry` system — **no direct `ImGui.isKeyPressed()` calls**. The panel overrides `provideShortcuts(KeyboardLayout layout)` following the `AnimationEditorPanel` pattern:

```java
@Override
public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
    return List.of(
        panelShortcut()
            .id("editor.dialogue.save")
            .displayName("Save Dialogue")
            .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
            .allowInInput(true)  // Works while typing in text fields
            .handler(this::saveCurrentDialogue)
            .build(),

        panelShortcut()
            .id("editor.dialogue.undo")
            .displayName("Undo")
            .defaultBinding(ShortcutBinding.ctrl(
                layout == KeyboardLayout.AZERTY ? ImGuiKey.W : ImGuiKey.Z))
            .allowInInput(true)
            .handler(this::undo)
            .build(),

        panelShortcut()
            .id("editor.dialogue.redo")
            .displayName("Redo")
            .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
            .allowInInput(true)
            .handler(this::redo)
            .build(),

        panelShortcut()
            .id("editor.dialogue.addLine")
            .displayName("Add Line")
            .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Enter))
            .handler(this::addLine)
            .build()
    );
}
```

**Key details:**
- `panelShortcut()` helper pre-configures `PANEL_FOCUSED` scope — shortcuts only fire when the Dialogue Editor panel is focused.
- `allowInInput(true)` on save/undo/redo ensures they work even while the cursor is in a text field (important since most of this panel is text editing).
- AZERTY support: undo binding adapts to keyboard layout (W instead of Z).
- All shortcuts are automatically included in `gameData/config/shortcuts.json` and rebindable by the user.
- Auto-registered during `EditorApplication` initialization via `provideShortcuts()`.

### Opening the Panel

- **Double-click** `.dialogue.json` in Asset Browser → opens panel, selects that dialogue
- **"Open" button** in `DialogueComponentInspector` → same
- **Window menu** → Dialogue Editor → opens panel with last-selected dialogue

---

## 8. Dialogue UI Prefab

A prefab placed in the scene as a child of the player's UICanvas.

### Structure

```
DialogueUI (GameObject)
├── UICanvas                          // If not already under one
├── UITransform: anchor=BOTTOM_CENTER, width=100% (PERCENT), height=150px, offset=(0, 20)
│
├── ChoicePanel (GameObject)                    // Hidden by default, ABOVE dialogue box
│   ├── UITransform: anchor=TOP_RIGHT, size=(250, 200), offset=(-20, 160)
│   ├── UIPanel: color=(0, 0, 0, 0.9)
│   │
│   ├── Choice1 (GameObject)
│   │   ├── UITransform: anchor=TOP_LEFT, size=(230, 35), offset=(10, -10)
│   │   ├── UIText: font="zelda.ttf", size=14
│   │   └── UIPanel: color=transparent (highlighted when selected)
│   │
│   ├── Choice2 (GameObject)
│   │   ├── UITransform: ...offset=(10, -45)
│   │   ├── UIText
│   │   └── UIPanel
│   │
│   ├── Choice3 (GameObject)
│   │   ├── UITransform: ...offset=(10, -80)
│   │   ├── UIText
│   │   └── UIPanel
│   │
│   └── Choice4 (GameObject)                   // Max 4 choices
│       └── ...
│
└── DialogueBox (GameObject)
    ├── UITransform: anchor=STRETCH, offset=(0,0)
    ├── UIPanel: color=(0, 0, 0, 0.85)       // Dark semi-transparent box
    │
    ├── DialogueText (GameObject)
    │   ├── UITransform: anchor=TOP_LEFT, size=(95% width, 110), offset=(15, -15)
    │   └── UIText: font="zelda.ttf", size=16, align=LEFT/TOP, wordWrap=true
    │
    └── ContinueIndicator (GameObject)         // ▼ blinking arrow
        ├── UITransform: anchor=BOTTOM_RIGHT, size=(16, 16), offset=(-10, 10)
        └── UIImage: sprite="ui/arrow_down.png"
```

**Notes:**
- **Width is 100% (PERCENT sizing)** so the dialogue box stretches across the full screen width. Height stays fixed at 150px.
- **Text overflow:** `UIText` uses `wordWrap=true`. The 110px text area fits approximately 3-4 lines of size-16 text. If a single dialogue line is longer than this after variable substitution, the text is clipped at the bottom of the text area. This is acceptable for now — dialogue lines should be kept short by content design (Pokémon-style: 2-3 sentences max per line). A future improvement could add per-line pagination (press INTERACT to scroll within a single entry), but that is out of scope for the first implementation.
- **No speaker/portrait** — removed for now. Speaker concept can be added later when the data model supports it (speaker reference on dialogue lines, portrait asset, etc.).
- **ChoicePanel is above DialogueBox** — anchored above the dialogue text so choices don't overlap or appear below the conversation.

### Visual Mockup (In-Game)

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│                        OVERWORLD SCENE                           │
│                                                                  │
│                           NPC                                    │
│                                                                  │
│                         Player                                   │
│                                                                  │
│                                         ┌──────────────────────┐ │
│                                         │ > Battle             │ │
│                                         │   Tell me more       │ │
│                                         │   Goodbye            │ │
│                                         └──────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │                                                              │ │
│ │  Hello Red! Your Pikachu looks healthy.                      │ │
│ │  What would you like to do?                              ▼   │ │
│ │                                                              │ │
│ └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### User Flow

```
1. Player walks to NPC, "Talk" prompt appears (InteractionController)
2. Player presses INTERACT
3. InteractionController calls DialogueComponent.interact(player)
4. DialogueComponent calls player.PlayerDialogueManager.startDialogue()
5. PlayerDialogueManager:
   a. Switches PlayerInput to DIALOGUE mode
   b. Pauses all IPausable components in the scene
   c. Shows DialogueBox UI (tween slide up)
   d. Sets first line text with typewriter effect
6. Player presses INTERACT → advance to next line
7. (Repeat for each line, dispatching onCompleteEvent if present)
8. If last entry is ChoiceGroup (and hasChoices == true):
   a. Show ChoicePanel (above dialogue box)
   b. UP/DOWN navigates, highlighted choice shown
   c. Player presses INTERACT on selected choice
   d. Execute action:
      - DIALOGUE → startDialogue() with new dialogue
      - BUILT_IN_EVENT → dispatchEvent() (e.g. START_BATTLE)
      - CUSTOM_EVENT → dispatchEvent() to listeners + DialogueEventStore
9. If last entry is a Line (no choices or hasChoices == false):
   a. Player presses INTERACT → endDialogue()
10. endDialogue():
    a. Dispatch DialogueComponent.onConversationEnd if set (e.g. START_BATTLE)
    b. Hide dialogue UI (tween slide down)
    c. Resume all IPausable components
    d. Switch PlayerInput back to OVERWORLD mode
```

---

## 9. Overworld vs. Battle Dialogue

### Question: "How to differentiate overworld and battle dialogue?"

**Answer: They are the same asset, different managers.**

The `Dialogue` asset is data — it doesn't know where it's displayed. The **manager** decides how to render and react:

| Aspect | Overworld | Battle |
|--------|-----------|--------|
| Manager | `PlayerDialogueManager` | `BattleDialogueManager` (future) |
| UI | DialogueUI prefab (bottom box) | Battle UI text area |
| Input blocking | Freezes GridMovement | Already in battle input mode |
| Choice actions | DIALOGUE, EVENT | MOVE_SELECT, ITEM_SELECT, etc. |
| Variables | NPC names, quest data | Pokemon stats, move names |

The `ChoiceAction.type` enum can be extended for battle-specific actions when the battle system is built. For now, `DIALOGUE` and `EVENT` cover all overworld needs.

**Key insight:** Don't build battle dialogue now. Design the asset format to be extensible (action types can be added), and keep the manager component-based so a different manager can interpret the same dialogue differently.

---

## 10. Class Summary & File Layout

### New Files

| File | Type | Description |
|------|------|-------------|
| `dialogue/Dialogue.java` | Asset | Dialogue data model (name derived from filename) |
| `dialogue/DialogueEntry.java` | Model | Base type for entries |
| `dialogue/DialogueLine.java` | Model | Text line entry with optional `onCompleteEvent` |
| `dialogue/DialogueChoiceGroup.java` | Model | Choice branching with `hasChoices` flag |
| `dialogue/Choice.java` | Model | Single choice with action |
| `dialogue/ChoiceAction.java` | Model | Action (DIALOGUE, BUILT_IN_EVENT, CUSTOM_EVENT) |
| `dialogue/ChoiceActionType.java` | Enum | DIALOGUE, BUILT_IN_EVENT, CUSTOM_EVENT |
| `dialogue/DialogueEvent.java` | Enum | Built-in events: START_BATTLE, END_CONVERSATION |
| `dialogue/DialogueEventRef.java` | Model | Wraps built-in or custom event reference |
| `dialogue/DialogueVariables.java` | Asset | Global variable definitions (STATIC / RUNTIME) |
| `dialogue/DialogueVariable.java` | Model | Variable name + type |
| `dialogue/DialogueEvents.java` | Asset | Global custom event name list |
| `dialogue/DialogueEventStore.java` | Utility | Persistence wrapper over SaveManager |
| `dialogue/DialogueVariableResolver.java` | Utility | Auto variable resolution via registered suppliers |
| `dialogue/ConditionalDialogue.java` | Model | Conditions + dialogue pair for conditional selection |
| `dialogue/DialogueCondition.java` | Model | Event name + expected state (FIRED / NOT_FIRED) |
| `resources/loaders/DialogueLoader.java` | Loader | Dialogue asset pipeline |
| `resources/loaders/DialogueVariablesLoader.java` | Loader | Variables asset pipeline |
| `resources/loaders/DialogueEventsLoader.java` | Loader | Events asset pipeline |
| `components/dialogue/DialogueComponent.java` | Component | NPC interactable |
| `components/dialogue/PlayerDialogueManager.java` | Component | Player-side orchestrator |
| `components/dialogue/DialogueEventListener.java` | Component | Event reaction |
| `components/player/PlayerInput.java` | Component | Input modes (prerequisite) |
| `IPausable.java` | Interface | Pause/resume contract |
| `editor/panels/DialogueEditorPanel.java` | Panel | Dialogue asset editor |
| `editor/ui/inspectors/DialogueComponentInspector.java` | Inspector | DialogueComponent inspector |
| `editor/ui/inspectors/DialogueEventListenerInspector.java` | Inspector | Event listener inspector |
| `editor/panels/inspector/DialogueVariablesInspectorRenderer.java` | Asset Inspector | Variables asset editing in InspectorPanel |
| `editor/panels/inspector/DialogueEventsInspectorRenderer.java` | Asset Inspector | Events asset editing in InspectorPanel |

### Modified Files

| File | Change |
|------|--------|
| `editor/EditorPanelType.java` | Add `DIALOGUE_EDITOR` |
| `editor/EditorUIController.java` | Register panel handler + menu item |
| `components/pokemon/GridMovement.java` | Implement `IPausable` |
| `components/interaction/InteractionController.java` | Read input from `PlayerInput` |
| `editor/panels/inspector/AssetInspectorRegistry.java` | Register `DialogueVariables` and `DialogueEvents` renderers |

### Package Structure

```
com.pocket.rpg
├── dialogue/                          // Asset models
│   ├── Dialogue.java
│   ├── DialogueEntry.java
│   ├── DialogueLine.java
│   ├── DialogueChoiceGroup.java
│   ├── Choice.java
│   ├── ChoiceAction.java
│   ├── ChoiceActionType.java
│   ├── DialogueEvent.java            // Built-in event enum
│   ├── DialogueEventRef.java         // Built-in or custom event wrapper
│   ├── DialogueVariables.java        // Global variables asset
│   ├── DialogueVariable.java         // Variable name + STATIC/RUNTIME type
│   ├── DialogueEvents.java           // Global custom events asset
│   ├── DialogueEventStore.java       // SaveManager persistence wrapper
│   ├── DialogueVariableResolver.java // Auto variable resolution
│   ├── ConditionalDialogue.java      // Conditions + dialogue pair
│   └── DialogueCondition.java        // Event name + FIRED/NOT_FIRED
├── components/dialogue/               // Runtime components
│   ├── DialogueComponent.java
│   ├── PlayerDialogueManager.java
│   └── DialogueEventListener.java
├── components/player/                 // Prerequisite: input refactor
│   └── PlayerInput.java
├── IPausable.java                     // Pause interface
├── resources/loaders/
│   ├── DialogueLoader.java
│   ├── DialogueVariablesLoader.java
│   └── DialogueEventsLoader.java
└── editor/
    ├── panels/
    │   ├── DialogueEditorPanel.java
    │   └── inspector/
    │       ├── DialogueVariablesInspectorRenderer.java
    │       └── DialogueEventsInspectorRenderer.java
    └── ui/inspectors/
        ├── DialogueComponentInspector.java
        └── DialogueEventListenerInspector.java
```

---

## 11. Testing Strategy

Follows existing project patterns: JUnit 5, inline test objects, purpose-built mocks (`MockInputTesting`, `MockTimeContext`), no Mockito.

### Unit Tests

Pure logic with no engine or UI dependencies. Each area maps to a test class.

| Test Class | What It Covers | Key Cases |
|------------|----------------|-----------|
| `DialogueVariableSubstitutionTest` | `[VAR_NAME]` tag replacement in line text | Valid tag → replaced; unknown tag → stays literal + warning; malformed `[BROKEN` → stays literal; multiple tags in one line; empty variable map; null variable map |
| `DialogueVariableResolverTest` | AUTO variable resolution | Register supplier → resolveAutoVariables() returns value; supplier returns null → excluded; multiple suppliers; fresh evaluation each call |
| `VariableMergeTest` | AUTO → STATIC → RUNTIME merge order | Each layer overrides previous; null/empty maps at each layer; runtime overrides auto with same key |
| `ConditionalDialogueTest` | `allConditionsMet()` evaluation | Single FIRED condition; single NOT_FIRED; multiple conditions (AND); empty conditions list → true; mixed FIRED + NOT_FIRED |
| `DialogueSelectionTest` | `DialogueComponent.selectDialogue()` | First match wins; no match → default fallback; empty conditionalDialogues → default; ordering matters (more specific first) |
| `DialogueLoaderTest` | JSON parse + save roundtrip | LINE entries; CHOICES entries; unknown type → skipped with warning; ChoiceAction types (DIALOGUE path, BUILT_IN_EVENT, CUSTOM_EVENT); hasChoices true/false; empty choices list; DialogueLine.onCompleteEvent parsing (optional DialogueEventRef); save → load preserves all data |
| `DialogueVariablesLoaderTest` | Variables asset parse + save | Parse all three types (AUTO, STATIC, RUNTIME); empty list; roundtrip |
| `DialogueEventsLoaderTest` | Events asset parse + save | Parse event list; empty list; roundtrip |
| `DialogueValidationTest` | Runtime validation guards | Null dialogue → error path; empty entries → error path; >4 choices → first 4 only; hasChoices=true with empty choices → treated as false |

### Integration Tests

Multiple systems interacting. Use `TestScene`, `MockInputTesting`, `MockTimeContext` — no real window or renderer.

| Test Class | What It Covers | Key Cases |
|------------|----------------|-----------|
| `PlayerDialogueManagerTest` | Full dialogue state machine | startDialogue → SHOWING_LINE → INTERACT advance → next line → end; typewriter skip on INTERACT; choice navigation (UP/DOWN via MockInput) → INTERACT selects; endDialogue restores input mode |
| `DialogueChainingTest` | DIALOGUE choice action chaining | Choice chains to new dialogue — no pause/resume between; reentrancy: `isActive` true → internal chain path; external call while active → rejected by DialogueComponent guard |
| `DialogueEventDispatchTest` | Event firing + chaining semantics | Line-level onCompleteEvent fires in all chained dialogues; BUILT_IN_EVENT choice action handled by manager; CUSTOM_EVENT choice action dispatched to listeners + persisted; DialogueComponent.onConversationEnd fires once when endDialogue() is called (not during chaining) |
| `IPausableIntegrationTest` | Pause/resume across scene | startDialogue → all IPausable.onPause() called; endDialogue → onResume() called; chaining → no resume between dialogues; component stays enabled while paused |
| `DialogueEventListenerTest` | Listener lifecycle + persistence | Event fires → listener reacts (ENABLE/DISABLE/DESTROY/RUN_ANIMATION); scene loads with already-fired event → reacts in onStart(); null/blank eventName → skipped; RUN_ANIMATION without AnimationComponent → warning |
| `DialoguePersistenceTest` | Cross-scene event persistence | markFired → hasFired true; save → load → hasFired still true; conditional dialogue selection changes after events fired |

### Manual Testing

Requires editor UI, visual feedback, or full playthrough. Checklist for each implementation phase.

**Editor — DialogueEditorPanel:**
- [ ] Create new dialogue, edit lines, save
- [ ] Add/remove/drag-reorder lines
- [ ] Add choices (verify max 4 enforced), remove choices
- [ ] `[+ Var]` dropdown inserts tag correctly
- [ ] Validation warnings: unknown variable, empty choice text, missing DIALOGUE target
- [ ] Warning icon appears in dialogue list for invalid dialogues
- [ ] Undo/redo works, stacks cleared on dialogue switch
- [ ] Unsaved changes confirmation on dialogue switch
- [ ] Dirty tracking (`*` in title), Ctrl+S save
- [ ] Quick links open Variables/Events in InspectorPanel

**Editor — Inspectors:**
- [ ] DialogueComponentInspector: conditional dialogues add/remove/reorder, condition dropdowns, default dialogue picker
- [ ] Variable table: AUTO=disabled "auto", STATIC=editable, RUNTIME=disabled "runtime"
- [ ] Preview section collapses/expands, shows default dialogue
- [ ] DialogueEventListenerInspector: event dropdown from asset, stale event warning, @Required red highlight
- [ ] Asset inspector renderers: Variables and Events editable with save button

**In-Game UI:**
- [ ] Dialogue box tween in/out
- [ ] Typewriter effect at configured speed, skip on INTERACT, advance on second INTERACT
- [ ] Choice panel appears above dialogue box, UP/DOWN navigates, highlight visible
- [ ] Continue indicator (▼) blinks when text fully shown
- [ ] Word wrap works, long text clips at bottom (no crash)
- [ ] NPC movement freezes during dialogue, resumes after

**Cross-Scene Persistence (full playthrough):**
- [ ] Fire event in scene A → travel to scene B → listener reacted on load
- [ ] Save → quit → load → fired events preserved
- [ ] Conditional dialogue changes after events fired
- [ ] New game → all events reset

---

## 12. Open Questions Resolved

| Question | Answer |
|----------|--------|
| Overworld vs battle dialogue? | Same asset, different manager components. Battle manager is future work. |
| How to pause the game? | `IPausable` interface. Components implement `onPause()`/`onResume()` themselves. `PlayerDialogueManager` queries scene for all `IPausable`. |
| How NPCs react to events? | `DialogueEventListener` component, found by scene query. Event name validated via dropdown from `DialogueEvents` asset. |
| Multiple objects same event? | Each has its own listener. All matching listeners fire. |
| Persistence / cross-scene events? | `DialogueEventStore` writes to `SaveManager.globalState`. Listeners check on `onStart()`. Survives save/load. |
| Unset variables? | Render raw `[TAG]`, log warning. No crash. |
| List of strings vs node editor? | List of strings. Nodes are overkill for linear + one choice point. |
| `PlayerInput` refactored for modes? | Yes. `PlayerInput` component with `InputMode` enum (OVERWORLD, DIALOGUE, BATTLE, MENU). Prerequisite plan. |
| Runtime vs static variables? | Three types: AUTO (resolved from game state, always available), STATIC (set per-NPC in editor), RUNTIME (provided by caller code). Merge order: AUTO → STATIC → RUNTIME. |
| Player-related variables? | AUTO type — `PLAYER_NAME`, `MONEY`, `BADGE_COUNT`, etc. Registered in `PlayerDialogueManager.onStart()` via `DialogueVariableResolver` with `Supplier<String>` lambdas. |
| Variable spelling errors? | `DialogueVariables` asset is single source of truth. Editor uses dropdowns + inline validation warnings. |
| Event spelling errors? | `DialogueEvents` asset is single source of truth. Inspector renders dropdown, not free-text. |
| Conditional NPC dialogue? | `DialogueComponent` has ordered `conditionalDialogues` list. Conditions check `DialogueEventStore.hasFired()` (AND logic). First match wins, default fallback. No new persistence — piggybacks on existing event system via `SaveManager.globalState`. |
