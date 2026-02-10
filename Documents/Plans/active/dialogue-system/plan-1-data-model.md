# Plan 1: Dialogue Data Model & Asset Pipeline

**PR:** `dialogue-data-model`
**Depends on:** None (no runtime or engine dependencies)
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

All dialogue data classes, asset loaders, and supporting utilities. Pure data — no components, no UI, no editor. Everything here is unit testable.

---

## Phase 1: Core Data Model

**Design ref:** §1 — Data Model

Create the dialogue data classes. No loader yet — just the model and its rules.

- [ ] `Dialogue` — name (derived from filename), entries list, onStartEvent, onEndEvent
- [ ] `DialogueEntry` — base type (sealed interface or abstract class)
- [ ] `DialogueLine` — text, optional onCompleteEvent
- [ ] `DialogueChoiceGroup` — hasChoices flag, choices list
- [ ] `Choice` — text, action
- [ ] `ChoiceAction` — type, dialoguePath (String), builtInEvent, customEvent
- [ ] `ChoiceActionType` — enum: DIALOGUE, BUILT_IN_EVENT, CUSTOM_EVENT
- [ ] `DialogueEvent` — enum: START_BATTLE, END_CONVERSATION
- [ ] `DialogueEventRef` — category (BUILT_IN/CUSTOM), builtInEvent, customEvent
- [ ] Unit tests:
  - Dialogue with mixed LINE/CHOICES entries
  - ChoiceAction holds path string for DIALOGUE type
  - DialogueEventRef distinguishes built-in vs custom

**Files:**

| File | Change |
|------|--------|
| `dialogue/Dialogue.java` | **NEW** |
| `dialogue/DialogueEntry.java` | **NEW** |
| `dialogue/DialogueLine.java` | **NEW** |
| `dialogue/DialogueChoiceGroup.java` | **NEW** |
| `dialogue/Choice.java` | **NEW** |
| `dialogue/ChoiceAction.java` | **NEW** — with `getDialogue()` lazy resolution via `Assets.load()` |
| `dialogue/ChoiceActionType.java` | **NEW** |
| `dialogue/DialogueEvent.java` | **NEW** |
| `dialogue/DialogueEventRef.java` | **NEW** |
| `DialogueModelTest.java` | **NEW** |

**Acceptance criteria:**
- All data classes compile with correct field types and relationships
- A `Dialogue` can hold a mix of `DialogueLine` and `DialogueChoiceGroup` entries
- `ChoiceAction` stores dialogue references as path strings (not object references)
- `DialogueEventRef` correctly wraps both BUILT_IN (enum) and CUSTOM (string) events
- All unit tests pass

---

## Phase 2: Dialogue Loader

**Design ref:** §1 — Asset Pipeline Integration, Deserialization Strategy

Manual JSON parsing following the `AnimatorControllerLoader` pattern. Type discrimination via `"type"` field.

- [ ] `DialogueLoader` implements `AssetLoader<Dialogue>`:
  - `load(path)` — manual JSON parsing with type switch (LINE/CHOICES)
  - `save(dialogue, path)` — serialize back to JSON
  - `getSupportedExtensions()` → `[".dialogue.json"]`
  - `getPlaceholder()` → one empty line (not zero entries)
  - `supportsHotReload()` → true
  - `reload(existing, path)` — mutate existing instance
  - `getEditorPanelType()` → `EditorPanelType.DIALOGUE_EDITOR`
  - `getIconCodepoint()` → speech bubble
- [ ] Register in asset pipeline
- [ ] Unit tests (`DialogueLoaderTest`):
  - Parse LINE entries, CHOICES entries
  - Unknown entry type → skipped with warning
  - ChoiceAction types: DIALOGUE (path string), BUILT_IN_EVENT, CUSTOM_EVENT
  - hasChoices true/false, empty choices list
  - onStartEvent / onEndEvent / onCompleteEvent parsing
  - Save → load roundtrip preserves all data
  - Placeholder has exactly one empty line
  - Empty entries JSON → loader handles gracefully

**Files:**

| File | Change |
|------|--------|
| `resources/loaders/DialogueLoader.java` | **NEW** |
| `editor/EditorPanelType.java` | Add `DIALOGUE_EDITOR` |
| `DialogueLoaderTest.java` | **NEW** |

**Acceptance criteria:**
- `.dialogue.json` files are loadable via `Assets.load(path, Dialogue.class)`
- JSON with LINE and CHOICES entries parses correctly into the data model
- Unknown entry types are skipped with a logged warning (no crash)
- `save()` → `load()` roundtrip produces identical data
- `getPlaceholder()` returns a dialogue with exactly one empty line (never zero entries)
- Hot-reload is supported (`supportsHotReload()` returns true)
- All unit tests pass

---

## Phase 3: Variables & Events Assets

**Design ref:** §1 — DialogueVariables Asset, §5 — DialogueEvents

Global assets for variable definitions and custom event names.

- [ ] `DialogueVariable` — name + type (STATIC / RUNTIME / AUTO)
- [ ] `DialogueVariables` — list of DialogueVariable, loaded from convention path
- [ ] `DialogueVariablesLoader` — load/save/hot-reload for `.dialogue-vars.json`
- [ ] `DialogueEvents` — list of String event names
- [ ] `DialogueEventsLoader` — load/save/hot-reload for `.dialogue-events.json`
- [ ] Register both loaders in asset pipeline
- [ ] Unit tests:
  - `DialogueVariablesLoaderTest`: parse all three types (AUTO, STATIC, RUNTIME), empty list, roundtrip
  - `DialogueEventsLoaderTest`: parse event list, empty list, roundtrip

**Files:**

| File | Change |
|------|--------|
| `dialogue/DialogueVariable.java` | **NEW** |
| `dialogue/DialogueVariables.java` | **NEW** |
| `dialogue/DialogueEvents.java` | **NEW** |
| `resources/loaders/DialogueVariablesLoader.java` | **NEW** |
| `resources/loaders/DialogueEventsLoader.java` | **NEW** |
| `DialogueVariablesLoaderTest.java` | **NEW** |
| `DialogueEventsLoaderTest.java` | **NEW** |

**Acceptance criteria:**
- `DialogueVariables` supports all three variable types: AUTO, STATIC, RUNTIME
- Both assets load from their convention paths (`dialogues/variables.dialogue-vars.json`, `dialogues/events.dialogue-events.json`)
- Both loaders support save, load, hot-reload
- Save → load roundtrip preserves all data for both assets
- Empty variable/event lists are handled (no crash, no null)
- All unit tests pass

---

## Phase 4: Supporting Utilities

**Design ref:** §1 — Variable Resolution, DialogueVariableResolver; §5 — DialogueEventStore; §6 — Conditional Dialogue Selection

- [ ] `DialogueEventStore` — thin wrapper over `SaveManager` for event persistence (`markFired`, `hasFired`)
- [ ] `DialogueVariableResolver` — register `Supplier<String>` per auto variable, `resolveAutoVariables()`
- [ ] `ConditionalDialogue` — conditions list + dialogue reference, `allConditionsMet()`
- [ ] `DialogueCondition` — eventName + expectedState (FIRED / NOT_FIRED)
- [ ] Unit tests:
  - `DialogueEventStoreTest`: markFired → hasFired true; not fired → false
  - `DialogueVariableResolverTest`: register → resolve; null supplier value → excluded; multiple suppliers
  - `VariableMergeTest`: AUTO → STATIC → RUNTIME override order
  - `ConditionalDialogueTest`: single FIRED/NOT_FIRED; multiple AND conditions; empty conditions → true; mixed
  - `DialogueSelectionTest`: first match wins; no match → default; empty list → default

**Files:**

| File | Change |
|------|--------|
| `dialogue/DialogueEventStore.java` | **NEW** |
| `dialogue/DialogueVariableResolver.java` | **NEW** |
| `dialogue/ConditionalDialogue.java` | **NEW** |
| `dialogue/DialogueCondition.java` | **NEW** |
| `DialogueEventStoreTest.java` | **NEW** |
| `DialogueVariableResolverTest.java` | **NEW** |
| `VariableMergeTest.java` | **NEW** |
| `ConditionalDialogueTest.java` | **NEW** |
| `DialogueSelectionTest.java` | **NEW** |

**Acceptance criteria:**
- `DialogueEventStore.markFired("X")` → `DialogueEventStore.hasFired("X")` returns true; unfired events return false
- `DialogueVariableResolver`: registered suppliers are evaluated fresh on each `resolveAutoVariables()` call; suppliers returning null are excluded from the result
- Variable merge order is AUTO → STATIC → RUNTIME — later layers override earlier ones with the same key
- `ConditionalDialogue.allConditionsMet()`: returns true only when ALL conditions match (AND logic); empty conditions list returns true
- `selectDialogue()` logic: iterates top-to-bottom, first match wins; no match returns default fallback
- All unit tests pass

---

## Plan Acceptance Criteria

- [ ] `mvn compile` passes
- [ ] `mvn test` passes — all loader roundtrips, model tests, utility tests green
- [ ] No runtime component or editor dependencies — pure data layer
- [ ] A `.dialogue.json` file can be loaded, saved, and round-tripped without data loss
- [ ] Variable and event assets can be loaded, saved, and round-tripped without data loss
- [ ] Conditional dialogue selection logic is fully covered by unit tests
- [ ] Variable merge order (AUTO → STATIC → RUNTIME) is tested and correct
