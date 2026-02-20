# Test Plan — PR #9: Inspector HierarchyItem + GameEngine Extraction

## Manual Testing (User)

These require running the editor/game and interacting with the UI.

### GameEngine Extraction

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 1 | Game launches and runs normally | `mvn compile exec:java -Dexec.mainClass="com.pocket.rpg.Main"` | Game boots, scenes load, gameplay works as before |
| 2 | Editor launches normally | `mvn compile exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"` | Editor opens, no crash |
| 3 | Play mode enter/exit cycle | Enter play mode, exit, repeat 3-4 times rapidly | No crash, no resource leak, editor state fully restored each time |
| 4 | Audio in play mode | Trigger sounds during play mode, then exit | Audio plays in play mode; editor audio still works after exit (shared context not destroyed) |
| 5 | Input restoration after play mode | Exit play mode, use editor keyboard/mouse (viewport pan, entity selection, shortcuts) | No stuck keys, no missing callbacks, input works normally |
| 6 | Window resize during play mode | Resize window while in play mode, then exit | Rendering adapts during play mode; editor viewport correct after exit |

### Inspector HierarchyItem Fix

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 7 | Editor mode — all inspectors render | Select entities with each component type: Transform, UITransform, Door, GridMovement, SpawnPoint, StaticOccupant, UIButton, UIImage, UIPanel, UIText, CameraBoundsZone, WarpZone | Inspector renders correctly, fields editable |
| 8 | Editor mode — undo on inspectors | Edit a field on UITransform, Transform, and 2-3 other inspector types, then Ctrl+Z | Each edit undoes correctly |
| 9 | Editor mode — UI cascading resize | Select UI entity with children, resize it, then undo | Children cascade on resize; undo reverts including children |
| 10 | Play mode — UI entity inspector | Enter play mode, select a UI entity | Inspector renders, fields visible, **no NPE** |
| 11 | Play mode — GridMovement inspector | Enter play mode, select entity with GridMovement | Inspector renders, no NPE |
| 12 | Play mode — Door inspector | Enter play mode, select entity with Door | Inspector renders, no NPE |
| 13 | Play mode — SpawnPoint inspector | Enter play mode, select entity with SpawnPoint | Inspector renders, no NPE |
| 14 | Play mode — field editing is temporary | In play mode, edit a size field on UI entity | Cascading resize works; exit play mode → values revert |
| 15 | Play mode — undo is no-op | In play mode, edit a field, then Ctrl+Z | No crash, undo either does nothing or gracefully no-ops |
| 16 | Editor after play mode — clean state | Exit play mode, select entities, check inspector | Inspector works normally, undo stack is clean (no play mode edits leaked) |

---

## Unit Tests (Claude)

To be written in the worktree at `.worktrees/inspector-hierarchy-entity/src/test/`.

### GameEngine

| # | Test | File | What it verifies |
|---|------|------|-----------------|
| 1 | `init()` sets all three context singletons | `GameEngineTest.java` | Time, Audio, Input contexts are active after init |
| 2 | `destroy()` nulls all context singletons | `GameEngineTest.java` | Time, Audio, Input are null/inactive after destroy |
| 3 | `render()` with null scene returns safely | `GameEngineTest.java` | No NPE when sceneManager has no current scene |
| 4 | `destroy()` is idempotent | `GameEngineTest.java` | Calling destroy twice doesn't throw |

### EditorSharedAudioContext

| # | Test | File | What it verifies |
|---|------|------|-----------------|
| 5 | `initialize()` is a no-op | `EditorSharedAudioContextTest.java` | Delegate's initialize is NOT called |
| 6 | `destroy()` is a no-op | `EditorSharedAudioContextTest.java` | Delegate's destroy is NOT called |
| 7 | Audio operations delegate correctly | `EditorSharedAudioContextTest.java` | `playSound()`, `stopAll()`, etc. forward to delegate |

### RuntimeGameObjectAdapter (extend existing)

| # | Test | File | What it verifies |
|---|------|------|-----------------|
| 8 | `getHierarchyParent()` returns wrapped parent adapter | `RuntimeGameObjectAdapterTest.java` | Parent GameObject is wrapped in adapter |
| 9 | `getHierarchyParent()` returns null for root objects | `RuntimeGameObjectAdapterTest.java` | No parent → null |
| 10 | `isEditable()` returns false | `RuntimeGameObjectAdapterTest.java` | Runtime objects are not editable |

### CustomComponentInspector / editorEntity()

| # | Test | File | What it verifies |
|---|------|------|-----------------|
| 11 | `editorEntity()` returns entity when bound to EditorGameObject | `CustomComponentInspectorTest.java` | Downcast works in editor mode |
| 12 | `editorEntity()` returns null when bound to RuntimeGameObjectAdapter | `CustomComponentInspectorTest.java` | Safe null in play mode |

### ReflectionFieldEditor — NOT unit-testable

Tests #13-14 were dropped: `ReflectionFieldEditor.drawComponent()` calls ImGui directly and
cannot run headlessly. The entity extraction logic (`instanceof EditorGameObject ego ? ego : null`)
is the same pattern as `CustomComponentInspector.editorEntity()`, which is covered by tests #11-12.
Undo-skipping behavior in play mode is covered by manual tests #15-16.

---

**Result: 25 new tests written, 829 total tests pass.**
