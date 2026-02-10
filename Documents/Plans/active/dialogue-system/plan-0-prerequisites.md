# Plan 0: Prerequisites

**PR:** `dialogue-prerequisites`
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

These are engine-level features required by the dialogue system but useful beyond it. Each phase compiles and is independently testable.

---

## Phase 1: `Scene.getComponentsImplementing()`

**Design ref:** §3 — Scene Query: `getComponentsImplementing()`

Add a method to `Scene` that queries all components implementing a given interface. Used by the dialogue system for `IPausable` dispatch, but general-purpose.

- [ ] Add `<T> List<T> getComponentsImplementing(Class<T> interfaceClass)` to `Scene`
- [ ] Iterate all GameObjects → all components → `instanceof` check
- [ ] Unit tests:
  - Scene with mixed components, some implementing a test interface → returns correct subset
  - Empty scene → returns empty list
  - No matches → returns empty list
  - Multiple components on same GameObject implementing the interface → all returned

**Files:**

| File | Change |
|------|--------|
| `core/Scene.java` | Add `getComponentsImplementing()` method |
| `SceneTest.java` or `SceneComponentQueryTest.java` | **NEW** — Unit tests |

**Acceptance criteria:**
- `scene.getComponentsImplementing(SomeInterface.class)` returns all matching components across all GameObjects
- Returns empty list (not null) when no matches
- All unit tests pass

---

## Phase 2: `IPausable` Interface

**Design ref:** §3 — IPausable Interface

- [ ] Create `IPausable` interface with `onPause()` and `onResume()` methods
- [ ] Implement `IPausable` on `GridMovement` — `paused` flag, early-out in `update()`
- [ ] Integration test:
  - GridMovement with `IPausable`: pause → update does nothing; resume → update runs again
  - `scene.getComponentsImplementing(IPausable.class)` finds GridMovement

**Files:**

| File | Change |
|------|--------|
| `IPausable.java` | **NEW** — Pause/resume interface |
| `components/pokemon/GridMovement.java` | Implement `IPausable` |
| `IPausableTest.java` | **NEW** — Integration test with GridMovement |

**Acceptance criteria:**
- `GridMovement` implements `IPausable`
- After `onPause()`, `GridMovement.update()` does not process movement (component stays enabled, keeps tile registration)
- After `onResume()`, `GridMovement.update()` processes movement again
- `scene.getComponentsImplementing(IPausable.class)` returns `GridMovement` instances
- All unit + integration tests pass

---

## Phase 3: `PlayerInput` Component

**Design ref:** §2 — PlayerInput Component

New `PlayerInput` component that wraps `Input` with `InputMode` (OVERWORLD, DIALOGUE, BATTLE, MENU). Existing components refactored to read from `PlayerInput` instead of `Input` directly.

### Phase 3a: PlayerInput core

- [ ] Create `InputMode` enum: OVERWORLD, DIALOGUE, BATTLE, MENU
- [ ] Create `PlayerInput` component with:
  - `InputMode currentMode` (default OVERWORLD)
  - `setMode(InputMode)` / `getMode()`
  - `getMovement()` — reads `Input` directional keys every frame
  - `isInteractPressed()` — reads `Input.isActionPressed(INTERACT)`
  - Callback registration: `onInteract(InputMode, Runnable)`
  - Callback dispatch in `update()`: fires matching callbacks when action pressed
- [ ] Unit tests:
  - Mode switching
  - Callbacks only fire for matching mode
  - Multiple callbacks for same mode all fire

### Phase 3b: Refactor existing consumers

- [ ] `GridMovement` / PlayerMovement: read from `playerInput.getMovement()`, gate on `isOverworld()`
- [ ] `InteractionController`: register callback for OVERWORLD interact
- [ ] `PlayerPauseUI`: register callback for OVERWORLD menu
- [ ] Integration tests:
  - OVERWORLD mode: movement works, interaction works
  - DIALOGUE mode: movement ignored by GridMovement, interaction ignored by InteractionController
  - Mode switch: OVERWORLD → DIALOGUE → OVERWORLD, verify behavior at each step

**Files:**

| File | Change |
|------|--------|
| `components/player/PlayerInput.java` | **NEW** — Input mode component |
| `components/player/InputMode.java` | **NEW** — Enum |
| `components/pokemon/GridMovement.java` | Read from PlayerInput instead of Input |
| `components/interaction/InteractionController.java` | Read from PlayerInput instead of Input |
| `components/pokemon/PlayerPauseUI.java` | Read from PlayerInput instead of Input |
| `PlayerInputTest.java` | **NEW** — Unit + integration tests |

**Acceptance criteria:**
- `PlayerInput` is the single source of truth for player input — no other player component reads `Input` directly
- Default mode is OVERWORLD; `setMode(DIALOGUE)` switches mode
- In DIALOGUE mode: `GridMovement` does not move, `InteractionController` does not trigger interactions, `PlayerPauseUI` does not open menu
- In OVERWORLD mode: all three work as before
- Callbacks registered for a specific mode only fire when that mode is active
- All unit + integration tests pass

---

## Plan Acceptance Criteria

- [ ] `mvn compile` passes
- [ ] `mvn test` passes — all new tests + no regressions in existing tests
- [ ] Editor runs: existing scenes load and work with refactored input
- [ ] Manual playthrough: walk around, interact with NPCs, open pause menu — **identical behavior to before the refactor**
- [ ] No component reads `Input` directly for player actions (only `PlayerInput` does)
- [ ] `IPausable` and `getComponentsImplementing()` are available for any future system to use
