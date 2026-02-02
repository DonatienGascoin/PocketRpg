# Plan 2: Decouple Dirty Tracking + Extract ComponentListRenderer

## Overview

**Problem**: 46 occurrences of `scene.markDirty()` throughout the editor. `ComponentFieldEditor` holds a `@Setter EditorScene scene`. `EntityInspector.renderComponentList()` is private with inline `markDirty()` calls. None of this works without a scene, which blocks prefab edit mode where there is no scene to mark dirty.

**Approach**:
1. Introduce a `DirtyTracker` functional interface
2. Have `EditorScene` implement it
3. Extract `ComponentListRenderer` from `EntityInspector.renderComponentList()` -- takes `DirtyTracker` instead of `EditorScene`
4. Update `ComponentFieldEditor` to use `DirtyTracker` for dirty tracking

**Addresses**: Review findings #1 (scene.markDirty scattered), #4 (EntityInspector code not extractable), #10 (PrefabInspector reuse ambiguous).

**Not changed**: Undo commands (BulkMoveCommand, RemoveEntityCommand, etc.) and tools that call `scene.markDirty()` -- these are scene-structural operations, legitimately coupled to `EditorScene`, and disabled during prefab edit mode via Plan 4.

---

## Phase 1: DirtyTracker Interface

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/scene/DirtyTracker.java` | **NEW** -- functional interface |
| `src/main/java/com/pocket/rpg/editor/scene/EditorScene.java` | Implement `DirtyTracker` |

### Tasks

- [ ] Create `DirtyTracker` interface:

```java
package com.pocket.rpg.editor.scene;

/**
 * Abstraction for dirty-state tracking.
 * EditorScene implements this for scene editing.
 * Prefab edit mode provides its own implementation.
 */
@FunctionalInterface
public interface DirtyTracker {
    void markDirty();
}
```

- [ ] Add `implements DirtyTracker` to `EditorScene` class declaration. The existing `markDirty()` method already satisfies the interface -- no code change needed in the method body.

---

## Phase 2: Extract ComponentListRenderer

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/inspector/ComponentListRenderer.java` | **NEW** -- extracted from EntityInspector |
| `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java` | Delegate to ComponentListRenderer |

### Tasks

- [ ] Create `ComponentListRenderer` class extracted from `EntityInspector.renderComponentList()` (lines 212-281)

The new class encapsulates component list rendering with these dependencies:
- `DirtyTracker` -- for marking dirty on field changes and component add/remove
- `ComponentFieldEditor` -- for rendering individual component fields
- `ComponentBrowserPopup` -- for the "Add Component" popup
- `UndoManager` -- for executing add/remove commands

```java
package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.editor.scene.DirtyTracker;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddComponentCommand;
import com.pocket.rpg.editor.undo.commands.RemoveComponentCommand;
import com.pocket.rpg.serialization.ComponentRegistry;
// ... imgui imports

/**
 * Renders a component list with add/remove functionality.
 * Used by both EntityInspector (scene editing) and PrefabInspector (prefab editing).
 */
public class ComponentListRenderer {

    private final ComponentFieldEditor fieldEditor;
    private final ComponentBrowserPopup componentBrowserPopup;

    public ComponentListRenderer(ComponentFieldEditor fieldEditor,
                                  ComponentBrowserPopup componentBrowserPopup) {
        this.fieldEditor = fieldEditor;
        this.componentBrowserPopup = componentBrowserPopup;
    }

    /**
     * Renders the component list for an entity.
     *
     * @param entity         The entity whose components to render
     * @param isPrefabInstance  Whether the entity is a prefab instance (affects override display)
     * @param allowStructuralChanges  Whether add/remove component buttons are shown
     * @param dirtyTracker   Called when any change is made
     */
    public void render(EditorGameObject entity, boolean isPrefabInstance,
                       boolean allowStructuralChanges, DirtyTracker dirtyTracker) {
        // ... extracted logic from EntityInspector.renderComponentList()
        // Replace scene.markDirty() with dirtyTracker.markDirty()
    }

    /** Renders the ComponentBrowserPopup (must be called each frame). */
    public void renderPopup() {
        componentBrowserPopup.render();
    }
}
```

Key differences from the original `renderComponentList()`:
- `scene.markDirty()` calls replaced with `dirtyTracker.markDirty()`
- `boolean allowStructuralChanges` parameter controls whether add/remove buttons appear (replaces the `!isPrefab` check which prevented structural changes on prefab instances -- in prefab edit mode we DO want structural changes)
- `findDependentComponent()` helper moved into this class (it's self-contained)

- [ ] Update `EntityInspector` to delegate to `ComponentListRenderer`:

```java
public class EntityInspector {
    @Setter private EditorScene scene;
    private final ComponentFieldEditor fieldEditor = new ComponentFieldEditor();
    private final ComponentBrowserPopup componentBrowserPopup = new ComponentBrowserPopup();
    private final ComponentListRenderer componentListRenderer;
    // ...

    public EntityInspector() {
        componentListRenderer = new ComponentListRenderer(fieldEditor, componentBrowserPopup);
    }

    // In render():
    componentListRenderer.render(entity, entity.isPrefabInstance(),
        !entity.isPrefabInstance(), scene);  // EditorScene IS a DirtyTracker
    componentListRenderer.renderPopup();
```

---

## Phase 3: Update ComponentFieldEditor

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/inspector/ComponentFieldEditor.java` | Replace `@Setter EditorScene scene` with `@Setter DirtyTracker dirtyTracker` |
| `src/main/java/com/pocket/rpg/editor/ui/fields/FieldEditorContext.java` | Add `DirtyTracker` alongside scene reference |

### Tasks

- [ ] In `ComponentFieldEditor`: Replace `@Setter EditorScene scene` with `@Setter DirtyTracker dirtyTracker`. The only use of `scene` in this class is `FieldEditorContext.setCurrentScene(scene)` -- this still needs a scene reference for override logic. Solution: `ComponentFieldEditor` gets two setters:
  - `@Setter DirtyTracker dirtyTracker` -- for dirty tracking
  - Keep `FieldEditorContext.setCurrentScene()` call, but have the caller set it separately (EntityInspector already calls `fieldEditor.setScene(scene)` which can set both)

  Concretely, rename the existing `setScene` to set both:
  ```java
  public void setContext(DirtyTracker dirtyTracker, EditorScene scene) {
      this.dirtyTracker = dirtyTracker;
      FieldEditorContext.setCurrentScene(scene);
  }
  ```

  For prefab edit mode, the caller will pass `(prefabDirtyTracker, null)` since there's no scene.

- [ ] In `FieldEditorContext`: Add `@Setter DirtyTracker dirtyTracker` static field alongside the existing `currentScene`. Field editors that need to mark dirty can use `FieldEditorContext.getDirtyTracker().markDirty()`. However, for now field editors return a boolean and the caller (ComponentListRenderer) calls markDirty -- so this is optional and can be deferred.

---

## Phase 4: Update EntityInspector markDirty Call Sites

### Files

| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java` | Use DirtyTracker for remaining markDirty calls |

### Tasks

- [ ] Entity name rename (line 55): `scene.markDirty()` -> keep as `scene.markDirty()` since this is entity-level and EntityInspector always has a scene
- [ ] Delete entity confirmation (line 162): keep as `scene.markDirty()` -- RemoveEntityCommand needs the scene anyway
- [ ] Reset all overrides (line 206): keep as `scene.markDirty()` -- only shown for prefab instances in scene context
- [ ] Component list rendering: now delegated to `ComponentListRenderer` which uses `DirtyTracker`

**Summary**: Only the component list rendering (the reusable part) moves to `DirtyTracker`. Entity-level operations in `EntityInspector` keep their direct `scene.markDirty()` calls since they are inherently scene-bound.

---

## Phase 5: Tests

### Files

| File | Change |
|------|--------|
| `src/test/java/com/pocket/rpg/editor/panels/inspector/ComponentListRendererTest.java` | **NEW** -- test with mock DirtyTracker |

### Test Cases

- [ ] Rendering component fields marks DirtyTracker dirty when fields change
- [ ] Adding a component via ComponentBrowserPopup marks DirtyTracker dirty
- [ ] Removing a component marks DirtyTracker dirty
- [ ] `allowStructuralChanges=false` hides add/remove buttons
- [ ] `allowStructuralChanges=true` shows add/remove buttons
- [ ] `isPrefabInstance=true` shows override asterisks
- [ ] `findDependentComponent()` blocks removal of required components

### Test Commands

```bash
mvn test -Dtest=ComponentListRendererTest
```

Regression:
```bash
mvn test -Dtest=EntityCommandsTest
```

Manual smoke test: Open editor, select entities, verify inspector renders correctly, add/remove components, undo/redo.

---

## Size

Medium. One new interface, one extracted class, two modified classes, one test class.

---

## Code Review

- [ ] Verify `ComponentListRenderer` is a clean extraction with no behavioral changes
- [ ] Verify `EntityInspector` delegates correctly and all markDirty calls are preserved
- [ ] Verify `ComponentFieldEditor.setContext()` is called correctly by both EntityInspector and (future) PrefabInspector
- [ ] Verify no NPEs when `DirtyTracker` is a lambda (not an EditorScene)
- [ ] Run full test suite: `mvn test`
