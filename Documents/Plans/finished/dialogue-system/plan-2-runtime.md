# Plan 2: Runtime Components

**Status:** COMPLETE
**PR:** `dialogue-runtime`
**Depends on:** Plan 0 (PlayerInput, IPausable, getComponentsImplementing), Plan 1 (data model, loaders, utilities)
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

All runtime components that execute dialogue in-game. No editor tooling.

---

## Phase 1: PlayerDialogueManager — Core State Machine ✅

**Design ref:** §4 — PlayerDialogueManager Component

The central orchestrator. Implements the state machine, variable resolution, and typewriter effect. UI references are null-safe (guarded), allowing full integration testing without a UI hierarchy.

- [x] Create `PlayerDialogueManager` component with serialized fields (`charsPerSecond`, `slideDuration`) and transient state (`currentDialogue`, `currentEntryIndex`, `active`, `selectedChoice`, `visibleChars`, etc.)
- [x] `onStart()`: resolve `PlayerInput` sibling, create `DialogueVariableResolver`, register DIALOGUE-mode interact callback on PlayerInput
- [x] `startDialogue(dialogue, staticVars)` — 2-arg convenience overload
- [x] `startDialogue(dialogue, staticVars, runtimeVars)` — full entry point with runtime validation, reentrancy (chain vs fresh), variable merge (AUTO → STATIC → RUNTIME), slide-in animation with `waitingForSlide` blocking
- [x] `endDialogue()`: clear state, slide-out animation, restore OVERWORLD mode and resume IPausable in `onComplete` callback
- [x] `update()`: typewriter text reveal, continue indicator blink, choice navigation (key-up edge-triggered via `PlayerInput.getMovementDirectionUp()`), interact handling via callback flag
- [x] Choice action execution: DIALOGUE → internal chain, BUILT_IN_EVENT → dispatch + end, CUSTOM_EVENT → dispatch + persist + end
- [x] `dispatchEvent(DialogueEventRef)`: built-in → handle directly; custom → scene query for listeners + `DialogueEventStore.markFired()`
- [x] Line-level `onCompleteEvent` dispatch when advancing past a line
- [x] Variable substitution: `[VAR_NAME]` tags replaced from merged variable map
- [x] Choice validation: `hasChoices=true` with empty list → skip + end; `hasChoices=false` → skip
- [x] Auto-advance from last LINE to CHOICES without requiring an extra interact press
- [x] Integration tests (`PlayerDialogueManagerTest` — 33 tests):
  - State machine, typewriter, variable substitution, choices (navigate + select), runtime validation, line events, custom event dispatch, pause/resume

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/PlayerDialogueManager.java` | **NEW** |
| `test/.../dialogue/PlayerDialogueManagerTest.java` | **NEW** — 33 tests |

**Planned refactoring — extract helpers from PlayerDialogueManager:**

The initial implementation keeps all logic in one component for simplicity. Once the feature is stable, three concerns should be extracted:

| Extract | Into | What moves |
|---------|------|------------|
| Typewriter effect | `DialogueTypewriter` (helper class) | `charTimer`, `visibleChars`, `charsPerSecond`, `fullText`, `textFullyRevealed`. Self-contained state machine — the manager calls `typewriter.update(dt)`, `typewriter.skipToEnd()`, `typewriter.setText(text)`. |
| Variable substitution | `DialogueTextResolver` (static utility) | `substituteVariables()` method + `VARIABLE_TAG` pattern. Pure function: takes text + variable map, returns resolved text. |
| Event dispatch | `DialogueEventDispatcher` (utility) | `dispatchEvent()`, `handleBuiltInEvent()`, `dispatchToSceneListeners()`. Routes built-in vs custom, performs scene query, calls `DialogueEventStore.markFired()`. The manager calls `dispatcher.dispatch(eventRef, scene)`. |

What stays in the manager: state machine transitions (start/end/advance/chain), input routing (callback flag), choice navigation, and pause/resume orchestration.

---

## Phase 2: DialogueInteractable ✅

**Design ref:** §6 — DialogueComponent

NPC interactable that starts dialogue, with conditional dialogue selection. Named `DialogueInteractable` (not `DialogueComponent` as originally planned) to better describe its role.

- [x] Create `DialogueInteractable extends InteractableComponent`:
  - `conditionalDialogues: List<ConditionalDialogue>` — ordered, first match wins
  - `@Required dialogue: Dialogue` — default fallback
  - `variables: Map<String, String>` — shared static variable values
  - `onConversationEnd: DialogueEventRef` — optional event fired when the entire conversation ends
- [x] `interact(player)`: get `PlayerDialogueManager`, check `!manager.isActive()`, call `selectDialogue()`, set source component, start dialogue
- [x] `selectDialogue()`: iterate conditionalDialogues, first match → return; none → default
- [x] `getInteractionPrompt()` → "Talk"
- [x] Gizmo: CIRCLE, purple (0.8f, 0.4f, 1.0f, 0.9f)
- [x] Integration tests (`DialogueSelectionTest`, `ConditionalDialogueTest`):
  - No conditions → default dialogue, first match wins, AND logic, FIRED/NOT_FIRED states

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/DialogueInteractable.java` | **NEW** |
| `test/.../dialogue/DialogueSelectionTest.java` | **NEW** |
| `test/.../dialogue/ConditionalDialogueTest.java` | **NEW** |

**Planned improvement — conditional dialogues toggle:**

Add a `hasConditionalDialogues` boolean toggle to `DialogueInteractable`. When disabled, the `conditionalDialogues` list is hidden in the inspector, keeping the UI clean for simple NPCs that only use the default dialogue. This requires a custom inspector (`DialogueInteractableInspector`) that shows/hides the conditional section based on the toggle — similar to how `DialogueChoiceGroup.hasChoices` controls choice visibility.

---

## Phase 3: DialogueEventListener ✅

**Design ref:** §5 — Component-Based Event Listener

Reacts to custom dialogue events with predefined actions.

- [x] Create `DialogueEventListener` component:
  - `@Required eventName: String` — set via dropdown
  - `reaction: DialogueReaction` — enum: ENABLE_GAME_OBJECT, DISABLE_GAME_OBJECT, DESTROY_GAME_OBJECT, RUN_ANIMATION
  - `onStart()`: null/blank eventName guard; check `DialogueEventStore.hasFired()` → react if already fired
  - `onDialogueEvent()`: switch on reaction type
- [x] Integration tests (`DialogueEventListenerTest`, `DialogueEventDispatchTest`, `DialoguePersistenceTest`)

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/DialogueEventListener.java` | **NEW** |
| `components/dialogue/DialogueReaction.java` | **NEW** — Enum |
| `test/.../dialogue/DialogueEventListenerTest.java` | **NEW** |
| `test/.../dialogue/DialogueEventDispatchTest.java` | **NEW** |
| `test/.../dialogue/DialoguePersistenceTest.java` | **NEW** |

---

## Phase 4: Dialogue UI Prefab & Visual Integration ✅

**Design ref:** §8 — Dialogue UI Prefab

Wire the PlayerDialogueManager to actual UI elements. Create a programmatic prefab builder and a scene injector tool. This phase included extensive manual testing and visual polish.

### Final UI Hierarchy

```
UICanvas "DialogueUI" (sortOrder: 10, SCREEN_SPACE_OVERLAY)
└── UIImage "DialogueBox"                       (key: "dialogue_box", SLICED)
    │  battleDialogue.png, anchor: BOTTOM_LEFT, pivot: (0,1)
    │  100% width, 150px height
    │  starts off-screen (offset Y = +150), slides up/down via Tween
    ├── UIText "DialogueText"                   (key: "dialogue_text")
    │     anchor: (0,0), offset: (20,20), 85% width, 75% height
    │     wordWrap=true, Pokemon-Red font 20px, black text
    ├── UIText "ContinueIndicator"              (key: "dialogue_continue")
    │     anchor: BOTTOM_RIGHT, pivot: (1,1), 24x24px, offset: (-8,-8)
    │     "v" character, alpha 0 initially, blinks at 0.5s interval
    └── UIImage "ChoicePanel"                   (key: "dialogue_choice_panel")
        │  dialogueChoiceBg.png, anchor: TOP_RIGHT, pivot: (1,1)
        │  35% width, dynamic height based on choice count
        │  offset: (-10, 0), right-aligned Pokémon-style choice box
        └── "ChoiceContainer"
            │  anchor: (0, 0.10), offset: (16, 0), 75% width, 80% height
            │  UIVerticalLayoutGroup (spacing: 5px, forceExpandWidth: true)
            │  Inset avoids unusable left ~15% of background image
            ├── "Choice0" [40px height]
            │   ├── UIText "Arrow0" [20%w]      (key: "dialogue_choice_arrow_0")
            │   │     "<" character, size 25, centered
            │   └── UIText "Text0"  [80%w]      (key: "dialogue_choice_text_0")
            │         wordWrap=true, white text, vertically centered
            ├── "Choice1" ... (same pattern)
            ├── "Choice2" ... (same pattern)
            └── "Choice3" ... (same pattern)
```

### Implementation

- [x] Create `DialogueUIBuilder` — programmatic prefab builder with static `build()` method
- [x] Create `DialogueUISceneInjector` — tool that generates the UI hierarchy + test NPC as JSON and merges into scene files
- [x] Wire PlayerDialogueManager UI references via `ComponentKeyRegistry` (lazy resolve in `onStart()`)
- [x] Show/hide dialogue box with `Tweens.offsetY()` (slide up: offset 150→0, slide down: 0→150)
- [x] Show/hide choice panel via `GameObject.setEnabled()`
- [x] Dynamic choice panel height based on visible choice count (`choicePanelHeight()`)
- [x] Choice arrow selection: selected arrow alpha=1, others alpha=0
- [x] Continue indicator blink: timer-based alpha toggle every 0.5s
- [x] Variable substitution applied to choice labels
- [x] Unused choice slots hidden via `setEnabled(false)` on slot parent
- [x] UIImage backgrounds: `battleDialogue.png` (SLICED) for dialogue box, `dialogueChoiceBg.png` for choice panel
- [x] Manual testing:
  - [x] Dialogue box appears on interaction, text typewriter reveals
  - [x] INTERACT skips to full text, then advances
  - [x] Choice panel appears (right-aligned, Pokémon-style)
  - [x] UP/DOWN navigates choices with visible arrow
  - [x] Choice selection triggers action (chain or event)
  - [x] Dialogue box hides on end (slides down)
  - [x] NPCs freeze during dialogue, resume after
  - [x] Word wrap works for both dialogue text and choice labels
  - [x] Events & conditional dialogue: HEARD_CAVE_RUMOR changes NPC dialogue on subsequent interactions
  - [x] Input blocked during slide-out animation (no re-triggering dialogue mid-slide)

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/DialogueUIBuilder.java` | **NEW** — Programmatic prefab builder |
| `tools/DialogueUISceneInjector.java` | **NEW** — Scene injection tool |
| `components/dialogue/PlayerDialogueManager.java` | Add UI references, show/hide/tween logic, blink, choice arrows, `slidingOut` guard |

---

## Bugs Fixed During Manual Testing

Issues discovered and resolved during Phase 4 manual testing:

| Issue | Root Cause | Fix |
|-------|-----------|-----|
| Choice arrow not visible | Unicode `▶` not in Pokémon Red font | Changed to ASCII `<` (size 25) |
| Extra click needed to show choices | No auto-advance from last LINE to CHOICES | Added `isNextEntryChoices()` check in `updateTextReveal()` and `handleInteract()` |
| Choice navigation too fast | Per-frame polling (`getKey()`) fires every frame | Added `PlayerInput.getMovementDirectionUp()` for edge-triggered key-up detection |
| SaveManager not initialized in editor play mode | `PlayModeController.play()` didn't call `SaveManager.initialize()` | Added `SaveManager.initialize(engine.getSceneManager())` in `PlayModeController.play()` |
| SaveManager state persisted across play sessions | `SaveManager.initialize()` early-returned if already initialized | Removed the early-return guard — always creates a fresh instance |
| Conditional dialogue not loading | `AssetReferenceTypeAdapterFactory` didn't strip `ClassName:` prefix from typed asset format | Added `ClassName:path` prefix stripping in `AssetReferenceTypeAdapterFactory.read()` |
| Player could move during slide-out | `setMode(OVERWORLD)` and `resumeAll()` called immediately in `endDialogue()` | Deferred to `hideDialogueBox()` `onComplete` callback |
| Dialogue re-triggered during slide-out | `isActive()` returned false immediately, so NPC interaction was accepted | Added `slidingOut` flag; `isActive()` returns `active \|\| slidingOut` |
| Test SaveManager warnings | `DialogueEventStore.markFired()` calls `SaveManager.setGlobal()` which needs initialization | Added reflection-based SaveManager init in `PlayerDialogueManagerTest` setup (same pattern as `DialogueEventStoreTest`) |

---

## Plan Acceptance Criteria

- [x] `mvn compile` passes
- [x] `mvn test` passes — 1276 tests green
- [x] Full manual playthrough: interact with NPC → dialogue plays with typewriter effect → choices appear and are navigable → selecting a choice triggers the correct action (chain or event) → events fire and listeners react → world pauses during dialogue and resumes after
- [x] Conditional dialogue: fire HEARD_CAVE_RUMOR event → NPC says different things on next interaction
- [x] Edge cases: null dialogue, empty entries, hasChoices=false — all handled gracefully (logged, no crash)

---

## Improvements

### Tidy up `DialogueUISceneInjector` for reusability

The `DialogueUISceneInjector` tool proved valuable during development for iterating on the dialogue UI hierarchy and test NPC configuration without manual scene editing. It should be kept and improved:

- **Separate test data from injection logic**: Extract the test NPC configuration (`buildTestNPC()`, dialogue asset references, conditional dialogues) into a configurable section or separate file, so the tool can be reused for injecting dialogue UI into any scene without carrying test-specific data.
- **Make it easier to add/remove objects**: Currently adding a new UI element requires manually constructing `JsonObject` trees with verbose helper calls. A more declarative API (e.g. a builder pattern or a simple DSL) would make it straightforward to add, remove, or reconfigure objects in the hierarchy without touching low-level JSON construction.
- **Keep in sync with `DialogueUIBuilder`**: The injector and the programmatic builder must produce equivalent hierarchies. Consider generating the injector's JSON from `DialogueUIBuilder` constants (already partially done for layout values) to avoid drift.

### Other planned improvements

- **Conditional dialogues toggle**: Add `hasConditionalDialogues` boolean on `DialogueInteractable` to hide the conditional list in the inspector for simple NPCs (see Phase 2 notes).
- **Extract helpers from PlayerDialogueManager**: `DialogueTypewriter`, `DialogueTextResolver`, `DialogueEventDispatcher` (see Phase 1 notes).

---

## Known Issues / Follow-up

### `Scene.registerCachedComponents()` skips disabled GameObjects entirely

**Severity:** Important — affects any system that needs to reference initially-disabled UI elements by component key.

**Current workaround:** The ChoicePanel is kept `active: true` in the scene data and `DialogueUIBuilder`. `PlayerDialogueManager.resolveUI()` hides it after resolving references. This works but is fragile — any new initially-disabled hierarchy with component keys will hit the same issue.

**Proper fix:** `Scene.registerCachedComponents()` should register `ComponentKeyRegistry` keys even for disabled GameObjects. Only renderables and UICanvases should be skipped for disabled GOs (to avoid rendering invisible elements). The recursive child traversal should also continue into disabled GOs for key registration purposes.
