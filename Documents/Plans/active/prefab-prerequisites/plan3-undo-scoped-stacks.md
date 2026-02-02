# Plan 3: UndoManager Scoped Stacks

## Overview

**Problem**: The prefab design proposes stash/restore with caller-held snapshots (`UndoSnapshot`). The Architect recommends `pushScope()`/`popScope()` -- safer, nestable, no stale snapshot bugs.

**Approach**: Add `pushScope()` and `popScope()` to `UndoManager` that create isolated undo/redo contexts. All state is scoped: `undoStack`, `redoStack`, `lastCommand`, `lastCommandTime`.

**Addresses**: Review findings #5 (undo stash/restore needs scoping), #11 (Architect recommendation).

---

## Phase 1: Implement Scoped Stacks

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/undo/UndoManager.java` | Add `UndoScope` record, `Deque<UndoScope> scopeStack`, `pushScope()`, `popScope()` |

### Tasks

- [ ] Add inner record `UndoScope` to capture all undo state:

```java
/**
 * Snapshot of undo state for scope isolation.
 */
private record UndoScope(
    Deque<EditorCommand> undoStack,
    Deque<EditorCommand> redoStack,
    EditorCommand lastCommand,
    long lastCommandTime
) {}
```

- [ ] Add scope stack field:

```java
private final Deque<UndoScope> scopeStack = new ArrayDeque<>();
```

- [ ] Implement `pushScope()`:

```java
/**
 * Pushes a new isolated undo scope.
 * Current undo/redo state is saved and a fresh scope begins.
 * Use this when entering a sub-editing context (e.g., prefab edit mode).
 */
public void pushScope() {
    // Save current state
    scopeStack.push(new UndoScope(
        new ArrayDeque<>(undoStack),
        new ArrayDeque<>(redoStack),
        lastCommand,
        lastCommandTime
    ));

    // Start fresh
    undoStack.clear();
    redoStack.clear();
    lastCommand = null;
    lastCommandTime = 0;
}
```

- [ ] Implement `popScope()`:

```java
/**
 * Pops the current scope and restores the previous undo state.
 * All commands in the current scope are discarded.
 *
 * @throws IllegalStateException if no scope has been pushed
 */
public void popScope() {
    if (scopeStack.isEmpty()) {
        throw new IllegalStateException("No undo scope to pop");
    }

    UndoScope scope = scopeStack.pop();

    // Restore previous state
    undoStack.clear();
    undoStack.addAll(scope.undoStack());
    redoStack.clear();
    redoStack.addAll(scope.redoStack());
    lastCommand = scope.lastCommand();
    lastCommandTime = scope.lastCommandTime();
}
```

- [ ] Add `getScopeDepth()` for diagnostics:

```java
/**
 * Returns the current scope nesting depth (0 = root scope).
 */
public int getScopeDepth() {
    return scopeStack.size();
}
```

- [ ] Add `isInScope()` convenience method:

```java
/**
 * Returns true if currently inside a pushed scope (not the root scope).
 */
public boolean isInScope() {
    return !scopeStack.isEmpty();
}
```

---

## Phase 2: Tests

### Files

| File | Change |
|------|--------|
| `src/test/java/com/pocket/rpg/editor/undo/UndoManagerScopeTest.java` | **NEW** -- scope lifecycle tests |

### Test Cases

- [ ] **Push/pop lifecycle**: Push scope, execute commands, pop scope -> previous commands restored, scoped commands gone
- [ ] **Nested scopes**: Push scope A, execute commands, push scope B, execute commands, pop B -> scope A commands visible, pop A -> original commands visible
- [ ] **Undo/redo isolation**: Push scope, execute 3 commands, undo 2 -> canUndo is true (1 remaining), canRedo is true (2 available). Pop -> original canUndo/canRedo state restored
- [ ] **canUndo/canRedo in fresh scope**: Push scope -> canUndo is false, canRedo is false
- [ ] **Pop on empty throws**: `popScope()` without `pushScope()` throws `IllegalStateException`
- [ ] **getScopeDepth**: Starts at 0, increments on push, decrements on pop
- [ ] **isInScope**: false at root, true after push, false after pop back to root
- [ ] **Command merging resets**: Push scope -> lastCommand is null, merging with previous scope's commands is impossible
- [ ] **Clear inside scope**: `clear()` only clears current scope's stacks, not the saved ones

### Test Commands

```bash
mvn test -Dtest=UndoManagerScopeTest
```

Regression (existing undo tests):
```bash
mvn test
```

---

## Edge Cases

- **`clear()` behavior**: The existing `clear()` method clears `undoStack`, `redoStack`, and `lastCommand`. This correctly operates on the current scope's stacks only. The saved stacks in `scopeStack` are unaffected. No change needed.
- **`enabled` flag**: Applies regardless of scope depth. Disabling undo tracking works the same in any scope.
- **`maxHistorySize`**: Shared across all scopes (sensible -- each scope respects the same limit).

---

## Size

Small. ~40 lines of new code in UndoManager + one test class.

---

## Code Review

- [ ] Verify `pushScope()`/`popScope()` correctly save and restore ALL state (`undoStack`, `redoStack`, `lastCommand`, `lastCommandTime`)
- [ ] Verify `clear()` doesn't corrupt scope stack
- [ ] Verify nested scopes work correctly
- [ ] Verify existing undo tests pass unchanged (no behavioral change for code that doesn't use scopes)
- [ ] Run full test suite: `mvn test`
