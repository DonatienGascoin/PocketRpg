# Fix: Reparent Layout Bake, Selection Order, Group Undo

Three related editor fixes for hierarchy drag-drop operations.

---

## Issue 1: Bake layout-driven values when reparenting out of a layout group

### Problem

When children are reparented from a layout group parent to a non-layout parent, the visual
appearance breaks. The layout was driving position/size at runtime via transient overrides
and direct field writes, but the child's own serialized mode fields (e.g. `widthMode=PERCENT`,
`offsetXMode=PERCENT`) may reference the old parent's dimensions. Once the layout stops
driving, stale values take effect and the child jumps to a wrong position/size.

### Root cause

Layout groups call every frame:
- `ct.setAnchor(0, 0)` / `ct.setPivot(0, 0)` — writes serialized fields directly
- `ct.setOffset(x, y)` — writes `localPosition` (only effective for FIXED offset mode)
- `ct.setLayoutOverrideWidth(w)` / `ct.setLayoutOverrideHeight(h)` — **transient**, lost on reparent
- `ct.setWidth(w)` / `ct.setHeight(h)` — only when layout enforces size

When the child leaves the layout, transient overrides vanish and the child falls back to its
serialized fields which may be stale or reference a different parent's coordinate space.

### Fix

In `ReparentEntityCommand.execute()`, **before** calling `scene.insertEntityAtPosition()`:

1. Get the old parent
2. Check if the old parent has a component implementing `UITransformDriver`
3. If yes, get `driver.getChildDriverInfo(entity)` to know what's driven
4. For each driven axis, bake the current effective runtime value into the serialized field as FIXED

#### Bake steps for each child UITransform `ct`:

**Position (if `driverInfo.positionDriven()`):**
```java
float effectiveX = ct.getEffectiveOffsetX();
float effectiveY = ct.getEffectiveOffsetY();
ct.setAnchor(0, 0);
ct.setPivot(0, 0);
ct.setOffsetXMode(SizeMode.FIXED);
ct.setOffsetYMode(SizeMode.FIXED);
ct.setOffset(effectiveX, effectiveY);
```

**Width (if `driverInfo.widthDriven()`):**
```java
float effectiveW = ct.getEffectiveWidth();
ct.setWidthMode(SizeMode.FIXED);
ct.setWidth(effectiveW);
```

**Height (if `driverInfo.heightDriven()`):**
```java
float effectiveH = ct.getEffectiveHeight();
ct.setHeightMode(SizeMode.FIXED);
ct.setHeight(effectiveH);
```

**Clear transient state:**
```java
ct.clearLayoutOverrides();
```

#### Undo

No special undo logic needed. `ReparentEntityCommand.undo()` moves the child back under the
layout parent. On the next frame, the layout's `applyLayout()` takes over and drives the
fields again, restoring the original visual state.

### Files to modify

| File | Change |
|------|--------|
| `ReparentEntityCommand.java` | Add `bakeLayoutValues()` call in `execute()` before `insertEntityAtPosition()` |

### Edge cases

- **Reparenting INTO a layout**: No bake needed — layout takes over on next frame.
- **Reparenting between two layout parents**: Bake from old layout, new layout overrides on next frame. Safe.
- **Entity has no UITransform**: Skip bake (not a UI entity).
- **Old parent has no UITransformDriver**: Skip bake (no layout driving values).

---

## Issue 2: Unit tests for selection order preservation

### Problem

`EditorScene.selectedEntities` was changed from `HashSet` to `LinkedHashSet` to preserve
insertion order. Several intermediate collections were also changed. This needs test coverage.

### Test file

`src/test/java/com/pocket/rpg/editor/scene/EditorSceneSelectionOrderTest.java`

Following existing test patterns (JUnit 5, `@Nested` groups, `EditorScene` + `EditorGameObject`
constructed directly without mocks).

### Test scenarios

**Group: Insertion order preservation**
1. `addToSelection_preservesInsertionOrder` — add A, B, C → iterate → get A, B, C
2. `setSelection_preservesIterationOrderOfInput` — pass LinkedHashSet of C, A, B → iterate → get C, A, B
3. `setSelectedEntity_singleElement` — set one entity → getSelectedEntities returns it

**Group: Toggle preserves remaining order**
4. `toggleSelection_removeMiddle_preservesOrder` — add A, B, C → toggle B off → iterate → A, C
5. `toggleSelection_addNew_appendsAtEnd` — add A, B → toggle C on → iterate → A, B, C

**Group: Clear and re-select**
6. `clearSelection_thenReselect_newOrder` — add A, B → clear → add B, A → iterate → B, A

**Group: getSelectedEntities returns unmodifiable but ordered view**
7. `getSelectedEntities_isUnmodifiable` — verify returned set throws on modification
8. `getSelectedEntities_reflectsInsertionOrder` — add 5 entities → verify iteration order matches insertion

**Group: Interaction with other operations**
9. `removeEntity_removesFromSelection_preservesOrder` — add A, B, C → removeEntity(B) → selection is A, C in order

### Files to create

| File | Content |
|------|---------|
| `src/test/java/com/pocket/rpg/editor/scene/EditorSceneSelectionOrderTest.java` | New test class |

---

## Issue 3: Wrap multi-reparent in a single CompoundCommand

### Problem

`HierarchyDragDropHandler.executeReparent()` creates individual `ReparentEntityCommand`
for each selected entity via separate `UndoManager.execute()` calls. Undoing N reparented
entities requires N undo presses. User expects one undo to revert the entire group.

### Root cause

```java
// Current code in executeReparent():
for (EditorGameObject dragged : selected) {
    UndoManager.getInstance().execute(
        new ReparentEntityCommand(scene, dragged, targetParent, adjustedIndex)
    );
    offset++;
}
```

Each iteration pushes a separate command to the undo stack.

### Fix

Collect all `ReparentEntityCommand`s into a list, wrap in the existing `CompoundCommand`,
and execute once:

```java
private void executeReparent(Set<EditorGameObject> selected, EditorGameObject targetParent,
                             int insertIndex) {
    List<EditorCommand> commands = new ArrayList<>();
    int offset = 0;
    for (EditorGameObject dragged : selected) {
        if (dragged == targetParent || (targetParent != null && dragged.isAncestorOf(targetParent))) {
            continue;
        }

        int adjustedIndex = insertIndex + offset;
        if (dragged.getParent() == targetParent && dragged.getOrder() < insertIndex) {
            adjustedIndex = Math.max(0, adjustedIndex - 1);
        }

        commands.add(new ReparentEntityCommand(scene, dragged, targetParent, adjustedIndex));
        offset++;
    }

    if (commands.size() == 1) {
        UndoManager.getInstance().execute(commands.get(0));
    } else if (commands.size() > 1) {
        String desc = "Reparent " + commands.size() + " entities";
        if (targetParent != null) desc += " to " + targetParent.getName();
        UndoManager.getInstance().execute(new CompoundCommand(desc, commands));
    }
}
```

Single entity → single command (cleaner undo description).
Multiple entities → one `CompoundCommand` → one undo press reverts all.

### Files to modify

| File | Change |
|------|--------|
| `HierarchyDragDropHandler.java` | Rewrite `executeReparent()` to collect commands and wrap in `CompoundCommand` |

### Note on interaction with Issue 1

The bake logic (Issue 1) lives inside `ReparentEntityCommand.execute()`. When wrapped in a
`CompoundCommand`, each sub-command's `execute()` still runs independently in order — the bake
happens naturally per entity. `CompoundCommand.undo()` calls each sub-command's `undo()` in
reverse, which reparents back under the layout. No special interaction needed.

---

## Implementation order

1. **Issue 3** first — simple, isolated change to `executeReparent()`
2. **Issue 1** second — modifies `ReparentEntityCommand.execute()`, benefits from issue 3 being done
3. **Issue 2** last — tests verify the already-applied LinkedHashSet changes, can also add a test for compound reparent undo if desired
