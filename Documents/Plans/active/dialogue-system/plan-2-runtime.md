# Plan 2: Runtime Components

**PR:** `dialogue-runtime`
**Depends on:** Plan 0 (PlayerInput, IPausable, getComponentsImplementing), Plan 1 (data model, loaders, utilities)
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

All runtime components that execute dialogue in-game. No editor tooling.

---

## Phase 1: PlayerDialogueManager — Core State Machine

**Design ref:** §4 — PlayerDialogueManager Component

The central orchestrator. This phase implements the state machine, variable resolution, and typewriter effect — but **without UI wiring**. UI references are null-safe (guarded). This allows full integration testing of dialogue logic via MockInputTesting.

- [ ] Create `PlayerDialogueManager` component:
  - Serialized fields: UI references (all `@ComponentReference(source = KEY)`), `charsPerSecond`
  - Transient fields: `DialogueVariableResolver`, `currentDialogue`, `currentEntryIndex`, `currentVariables`, `isActive`, `selectedChoice`, `visibleChars`, `fullText`
  - `@ComponentReference(source = SELF) PlayerInput playerInput`
- [ ] `onStart()`: create `DialogueVariableResolver`, register auto variables (see design §4 — Auto Variable Registration)
- [ ] `startDialogue(dialogue, staticVars)` — 2-arg convenience overload
- [ ] `startDialogue(dialogue, staticVars, runtimeVars)` — full entry point:
  - Runtime validation: null dialogue, empty entries → log error, endDialogue, return (design §4 — Runtime Validation)
  - Reentrancy: `isChain = isActive` check (design §4 — Reentrancy)
  - Fresh conversation: set active, switch PlayerInput to DIALOGUE, pause all IPausable
  - Chain: skip pause/mode switch (already in DIALOGUE mode, already paused)
  - Variable merge: AUTO → STATIC → RUNTIME
  - Show first entry
- [ ] `endDialogue()`: resume IPausable, restore OVERWORLD mode, hide UI, dispatch `DialogueComponent.onConversationEnd` if set (see Phase 2)
- [ ] `update()`: typewriter text reveal, INTERACT handling (skip/advance), choice navigation (UP/DOWN + INTERACT)
- [ ] Choice action execution (explicit flow):
  - **DIALOGUE** → call `startDialogue()` internally (chain) — do NOT call `endDialogue()`. The current dialogue ends silently, the new one starts. IPausable stays paused, input stays in DIALOGUE mode.
  - **BUILT_IN_EVENT** → call `dispatchEvent()`, then `endDialogue()`. Example: `END_CONVERSATION` just calls `endDialogue()`; `START_BATTLE` triggers battle transition then `endDialogue()`.
  - **CUSTOM_EVENT** → call `dispatchEvent()` to scene listeners + `DialogueEventStore.markFired()`, then `endDialogue()`.
- [ ] `dispatchEvent(DialogueEventRef)`: built-in → handle directly; custom → scene query for listeners + markFired
- [ ] Line-level `onCompleteEvent` dispatch: when the player advances past a line, if that line has an `onCompleteEvent`, dispatch it (fires in every dialogue, including chained ones)
- [ ] Variable substitution: replace `[VAR_NAME]` tags in line text
- [ ] Choice validation: >4 choices → first 4 + warning; hasChoices=true with empty → skip
- [ ] Integration tests (`PlayerDialogueManagerTest`):
  - startDialogue → SHOWING_LINE → advance → next line → end
  - Typewriter: partial reveal → INTERACT → full text → INTERACT → next line
  - Choices: navigate UP/DOWN, select with INTERACT
  - Runtime validation: null dialogue, empty entries → error path
  - Variable substitution: `[TAG]` replaced, unknown tag stays literal
  - Line-level onCompleteEvent: fires when player advances past the line
- [ ] Integration tests (`DialogueChainingTest`):
  - Choice DIALOGUE action → chains to new dialogue, no resume between, no `endDialogue()` on intermediate dialogue
  - isActive true → internal chain path (no re-pause)
  - External call while active → rejected by DialogueComponent guard (tested in Phase 2)
  - Line-level onCompleteEvent fires normally in both the first and chained dialogue

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/PlayerDialogueManager.java` | **NEW** |
| `PlayerDialogueManagerTest.java` | **NEW** |
| `DialogueChainingTest.java` | **NEW** |

**Acceptance criteria:**
- `startDialogue()` transitions through IDLE → SHOWING_LINE → (advance through lines) → END correctly
- Typewriter effect reveals text character-by-character at `charsPerSecond` rate
- INTERACT during typewriter → shows full text; INTERACT when full → advances to next entry
- Choice navigation: UP/DOWN changes `selectedChoice`, INTERACT on choice executes its action
- Null dialogue or empty entries → logs error, calls `endDialogue()`, no exception
- Chaining: DIALOGUE choice action calls `startDialogue()` internally — no `endDialogue()`, no IPausable resume, no mode switch between chains
- BUILT_IN_EVENT / CUSTOM_EVENT choice actions dispatch the event, then call `endDialogue()`
- Line-level `onCompleteEvent` fires when the player advances past a line (in every dialogue, including chained)
- Variable substitution replaces `[TAG]` with values; unknown tags stay literal with warning logged
- `>4` choices → only first 4 shown + warning; `hasChoices=true` with empty list → skipped
- All integration tests pass

---

## Phase 2: DialogueComponent

**Design ref:** §6 — DialogueComponent

NPC interactable that starts dialogue, with conditional dialogue selection.

- [ ] Create `DialogueComponent extends InteractableComponent`:
  - `@RequiredComponent(TriggerZone.class)`
  - `conditionalDialogues: List<ConditionalDialogue>` — ordered, first match wins
  - `@Required dialogue: Dialogue` — default fallback
  - `variables: Map<String, String>` — shared static variable values
  - `onConversationEnd: DialogueEventRef` — optional event fired when the entire conversation with this NPC ends (after all chaining). This is where NPC-specific triggers live (e.g. trainer sets `onConversationEnd = START_BATTLE`). The `PlayerDialogueManager.endDialogue()` reads this from the originating `DialogueComponent` and dispatches it.
- [ ] `interact(player)`: get PlayerDialogueManager, check `!manager.isActive()`, call `selectDialogue()`, start dialogue. Pass `this` (the DialogueComponent) so the manager can read `onConversationEnd` later.
- [ ] `selectDialogue()`: iterate conditionalDialogues, first match → return; none → default
- [ ] `getInteractionPrompt()` → "Talk"
- [ ] Gizmo: CIRCLE, purple (0.8f, 0.4f, 1.0f, 0.9f)
- [ ] Integration tests (`DialogueSelectionIntegrationTest`):
  - No conditions → default dialogue used
  - First condition matches → that dialogue returned
  - Multiple conditions, first match wins
  - Events fired → condition evaluation changes result

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/DialogueComponent.java` | **NEW** |
| `DialogueSelectionIntegrationTest.java` | **NEW** |

**Acceptance criteria:**
- `DialogueComponent` extends `InteractableComponent` and requires `TriggerZone`
- `interact()` calls `PlayerDialogueManager.startDialogue()` with the selected dialogue, static variables, and a reference to itself (so the manager can read `onConversationEnd`)
- `interact()` is a no-op when `PlayerDialogueManager.isActive()` is true (no double-trigger)
- `onConversationEnd` is dispatched by the manager when `endDialogue()` is called (after all chaining completes)
- `selectDialogue()` evaluates conditional dialogues top-to-bottom, returns first match; falls back to default
- Gizmo renders as purple circle in editor
- All integration tests pass

---

## Phase 3: DialogueEventListener

**Design ref:** §5 — Component-Based Event Listener

Reacts to custom dialogue events with predefined actions.

- [ ] Create `DialogueEventListener` component:
  - `@Required eventName: String` — set via dropdown (no free-text)
  - `reaction: DialogueReaction` — enum: ENABLE_GAME_OBJECT, DISABLE_GAME_OBJECT, DESTROY_GAME_OBJECT, RUN_ANIMATION
  - `onStart()`: null/blank eventName guard → warn + return; check `DialogueEventStore.hasFired()` → react if already fired
  - `onDialogueEvent()`: switch on reaction type
  - RUN_ANIMATION: null-check AnimationComponent → warn if missing
- [ ] Integration tests (`DialogueEventListenerTest`):
  - Event fires → DISABLE_GAME_OBJECT reaction
  - Event fires → ENABLE_GAME_OBJECT reaction
  - Event fires → DESTROY_GAME_OBJECT reaction
  - Event fires → RUN_ANIMATION with AnimationComponent → plays
  - Event fires → RUN_ANIMATION without AnimationComponent → warning, no crash
  - Scene loads with already-fired event → listener reacts in onStart()
  - Null/blank eventName → skipped with warning
- [ ] Integration tests (`DialogueEventDispatchTest`):
  - Full flow: dialogue fires custom event via choice action → listener in scene reacts
  - Line-level onCompleteEvent fires in all chained dialogues (every line, not just first/last dialogue)
  - BUILT_IN_EVENT choice action handled by manager (END_CONVERSATION → endDialogue)
  - CUSTOM_EVENT choice action dispatched to listeners + persisted, then endDialogue
  - `DialogueComponent.onConversationEnd` fires once when `endDialogue()` is called (not during chaining)
  - `onConversationEnd` with START_BATTLE triggers battle transition after dialogue closes
- [ ] Integration test (`DialoguePersistenceTest`):
  - markFired → hasFired true across dialogue components
  - Conditional dialogue selection changes after events fired

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/DialogueEventListener.java` | **NEW** |
| `components/dialogue/DialogueReaction.java` | **NEW** — Enum |
| `DialogueEventListenerTest.java` | **NEW** |
| `DialogueEventDispatchTest.java` | **NEW** |
| `DialoguePersistenceTest.java` | **NEW** |

**Acceptance criteria:**
- Each `DialogueReaction` type works: ENABLE, DISABLE, DESTROY, RUN_ANIMATION
- RUN_ANIMATION without AnimationComponent → logs warning, no crash
- Null/blank `eventName` → logged warning on `onStart()`, listener inactive
- On scene load, if event was already fired (`DialogueEventStore.hasFired()`), listener reacts immediately in `onStart()`
- Event dispatch from `PlayerDialogueManager`: custom events reach all matching listeners in the scene and are persisted via `DialogueEventStore`
- Chaining semantics: line-level `onCompleteEvent` fires on every line in every dialogue; `DialogueComponent.onConversationEnd` fires only once when the conversation truly ends (not during chaining)
- Conditional dialogue selection changes after events are fired (e.g. NPC says different things after event)
- All integration tests pass

---

## Phase 4: Dialogue UI Prefab & Visual Integration

**Design ref:** §8 — Dialogue UI Prefab

Wire the PlayerDialogueManager to actual UI elements. Create the prefab structure. This phase is primarily manual testing.

- [ ] Create dialogue UI prefab (scene template or documented structure):
  - DialogueUI root with UITransform (BOTTOM_CENTER, 100% width, 150px height)
  - ChoicePanel (hidden by default, above dialogue box) with 4 choice slots
  - DialogueBox with DialogueText (UIText, wordWrap=true) and ContinueIndicator (UIImage)
  - All UI elements registered with ComponentKeyRegistry keys
- [ ] Wire PlayerDialogueManager UI references to prefab elements
- [ ] Show/hide dialogue box with tween (slide up/down)
- [ ] Show/hide choice panel
- [ ] Choice highlight visual (selected choice panel color)
- [ ] Continue indicator blinking when text fully shown
- [ ] Manual testing:
  - [ ] Dialogue box appears on interaction, text typewriter reveals
  - [ ] INTERACT skips to full text, then advances
  - [ ] Choice panel appears above dialogue box
  - [ ] UP/DOWN navigates choices with visible highlight
  - [ ] Choice selection triggers action (chain or event)
  - [ ] Dialogue box hides on end
  - [ ] NPCs freeze during dialogue, resume after
  - [ ] Word wrap works, long text clips at bottom

**Files:**

| File | Change |
|------|--------|
| `components/dialogue/PlayerDialogueManager.java` | Add UI show/hide/tween logic |
| Prefab scene file(s) | **NEW** — Dialogue UI prefab |

**Acceptance criteria:**
- Dialogue UI prefab exists with all required GameObjects (DialogueBox, DialogueText, ContinueIndicator, ChoicePanel, Choice1-4)
- All UI elements are wired to PlayerDialogueManager via ComponentKeyRegistry keys
- Dialogue box slides in from bottom on dialogue start, slides out on end
- Text displays with typewriter effect, INTERACT skips/advances
- Choice panel appears above the dialogue box when a ChoiceGroup is reached
- Selected choice is visually highlighted; UP/DOWN changes selection
- Continue indicator (▼) blinks when text is fully revealed, hidden during typewriter
- NPCs freeze during dialogue (IPausable) and resume after
- Word wrap works; text that exceeds the box clips at the bottom (no crash, no overflow)

---

## Plan Acceptance Criteria

- [ ] `mvn compile` passes
- [ ] `mvn test` passes — all integration tests green
- [ ] Full manual playthrough: interact with NPC → dialogue plays with typewriter effect → choices appear and are navigable → selecting a choice triggers the correct action (chain or event) → events fire and listeners react → world pauses during dialogue and resumes after
- [ ] Cross-scene persistence: fire event in scene A → load scene B → listener reacts on load
- [ ] Conditional dialogue: fire events → NPC says different things based on event state
- [ ] Edge cases: null dialogue, empty entries, >4 choices, missing AnimationComponent — all handled gracefully (logged, no crash)
