# Undoable Selection System - Implementation Plan

## Overview

Make entity selection changes undoable (Unity-like behavior), including:
- Clicking to select/deselect entities creates undo entries
- Undo after delete restores the entity AND its selection state
- Rapid selection changes merge into single undo entries

## Current State

### Selection System (`EditorScene.java`)
- Selection stored in `Set<EditorGameObject> selectedEntities`
- Direct methods: `setSelection()`, `addToSelection()`, `clearSelection()`, `toggleSelection()`
- **Not undoable** - changes bypass undo system entirely

### Undo System (`editor/undo/`)
- `EditorCommand` interface: `execute()`, `undo()`, `getDescription()`
- `UndoManager` singleton with undo/redo stacks
- Supports command merging within 500ms window for rapid changes

### Deletion Commands
- `RemoveEntityCommand` - doesn't track selection
- `BulkDeleteCommand` - calls `clearSelection()` but doesn't restore on undo

---

## Implementation

### Phase 1: Create SelectionCommand

**New file:** `editor/undo/commands/SelectionCommand.java`

```java
public class SelectionCommand implements EditorCommand {
    private final EditorScene scene;
    private final Set<EditorGameObject> oldSelection;
    private final Set<EditorGameObject> newSelection;
    private final String description;

    public SelectionCommand(EditorScene scene,
                           Set<EditorGameObject> oldSelection,
                           Set<EditorGameObject> newSelection,
                           String description) {
        this.scene = scene;
        this.oldSelection = new HashSet<>(oldSelection);
        this.newSelection = new HashSet<>(newSelection);
        this.description = description;
    }

    @Override
    public void execute() {
        scene.setSelectionInternal(newSelection);
    }

    @Override
    public void undo() {
        scene.setSelectionInternal(oldSelection);
    }

    @Override
    public String getDescription() {
        return description;
    }

    // Merge rapid selection changes into one undo entry
    @Override
    public boolean canMergeWith(EditorCommand other) {
        return other instanceof SelectionCommand;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        SelectionCommand otherCmd = (SelectionCommand) other;
        // Keep our oldSelection, take their newSelection
        this.newSelection.clear();
        this.newSelection.addAll(otherCmd.newSelection);
    }

    // Don't create command if selection didn't change
    public boolean hasChanges() {
        return !oldSelection.equals(newSelection);
    }
}
```

### Phase 2: Modify EditorScene

**File:** `editor/scene/EditorScene.java`

Add internal method that bypasses undo (for use by commands):

```java
/**
 * Internal method for undo system - sets selection without creating command.
 */
void setSelectionInternal(Set<EditorGameObject> entities) {
    selectedEntities.clear();
    selectedEntities.addAll(entities);
    // Fire selection changed event if you have one
}
```

Modify public selection methods to create commands:

```java
public void setSelection(Set<EditorGameObject> entities) {
    Set<EditorGameObject> oldSelection = new HashSet<>(selectedEntities);
    SelectionCommand cmd = new SelectionCommand(this, oldSelection, entities, "Change Selection");
    if (cmd.hasChanges()) {
        UndoManager.getInstance().execute(cmd);
    }
}

public void setSelectedEntity(EditorGameObject entity) {
    Set<EditorGameObject> newSelection = entity != null
        ? Set.of(entity)
        : Set.of();
    setSelection(newSelection);
}

public void addToSelection(EditorGameObject entity) {
    Set<EditorGameObject> newSelection = new HashSet<>(selectedEntities);
    newSelection.add(entity);
    setSelection(newSelection);
}

public void clearSelection() {
    setSelection(Set.of());
}

public void toggleSelection(EditorGameObject entity) {
    Set<EditorGameObject> newSelection = new HashSet<>(selectedEntities);
    if (newSelection.contains(entity)) {
        newSelection.remove(entity);
    } else {
        newSelection.add(entity);
    }
    setSelection(newSelection);
}
```

### Phase 3: Modify Deletion Commands

**File:** `editor/undo/commands/RemoveEntityCommand.java`

```java
public class RemoveEntityCommand implements EditorCommand {
    private final EditorScene scene;
    private final EditorGameObject entity;
    private final Set<EditorGameObject> selectedBeforeDelete;  // ADD THIS

    public RemoveEntityCommand(EditorScene scene, EditorGameObject entity) {
        this.scene = scene;
        this.entity = entity;
        this.selectedBeforeDelete = new HashSet<>(scene.getSelectedEntities());  // ADD THIS
    }

    @Override
    public void execute() {
        scene.removeEntity(entity);
        // Selection is automatically cleared by removeEntity
    }

    @Override
    public void undo() {
        scene.addEntity(entity);
        // Restore selection - only include entities that still exist
        Set<EditorGameObject> toSelect = new HashSet<>();
        for (EditorGameObject e : selectedBeforeDelete) {
            if (scene.getEntities().contains(e) || e == entity) {
                toSelect.add(e);
            }
        }
        scene.setSelectionInternal(toSelect);  // Use internal to avoid extra undo entry
    }
}
```

**File:** `editor/undo/commands/BulkDeleteCommand.java`

Same pattern - capture `selectedBeforeDelete` in constructor, restore in `undo()`.

### Phase 4: Update Selection Entry Points

These files call selection methods and should continue to work (they'll now create undo entries):

| File | Method | Notes |
|------|--------|-------|
| `SelectionTool.java` | `onMouseDown()` | Main click-to-select |
| `HierarchyPanel.java` | Tree node clicks | Hierarchy selection |
| `UIDesignerInputHandler.java` | `handleClick()` | UI Designer selection |
| `EditorShortcutHandlersImpl.java` | Various | Ctrl+A, etc. |

**No changes needed** - they already call `setSelectedEntity()`, `addToSelection()`, etc.

### Phase 5: Batch Operations

For operations that make multiple selection changes (e.g., "Select All Children"), wrap in undo group:

```java
UndoManager.getInstance().setEnabled(false);
// ... make multiple selection changes ...
UndoManager.getInstance().setEnabled(true);
// Push single combined command
```

Or use internal methods directly and push one `SelectionCommand` at the end.

---

## Edge Cases

### 1. Entity Deleted While Selected
- `removeEntity()` removes from `selectedEntities`
- On undo, entity is restored AND re-selected

### 2. Undo Delete After New Selection
- User deletes Entity A (was selected)
- User selects Entity B
- User undoes delete
- **Result:** Entity A restored, selection = {A} (original selection before delete)
- This may feel wrong - consider restoring selection as {A, B} instead?

### 3. Parent-Child Deletion
- Deleting parent also deletes children
- Selection should restore all previously-selected entities in the hierarchy

### 4. Selection During Play Mode
- Selection changes in play mode probably shouldn't be undoable
- Check `PlayModeController.isPlaying()` before creating commands

---

## Testing Checklist

- [ ] Click entity → undo → entity deselected
- [ ] Click to deselect → undo → entity reselected
- [ ] Rapid clicking (within 500ms) creates single undo entry
- [ ] Ctrl+click multi-select → undo → previous selection restored
- [ ] Delete selected entity → undo → entity restored AND selected
- [ ] Delete multiple entities → undo → all restored with original selection
- [ ] Shift+click range select → undo → previous selection
- [ ] Clear selection → undo → selection restored
- [ ] Select All → undo → previous selection
- [ ] Redo works correctly for all above

---

## Files to Modify

| File | Change |
|------|--------|
| `editor/undo/commands/SelectionCommand.java` | **NEW** - selection command with merge support |
| `editor/scene/EditorScene.java` | Add `setSelectionInternal()`, modify public methods |
| `editor/undo/commands/RemoveEntityCommand.java` | Track and restore selection |
| `editor/undo/commands/BulkDeleteCommand.java` | Track and restore selection |

## Files That Should Work Without Changes

- `SelectionTool.java` - already calls `setSelectedEntity()`
- `HierarchyPanel.java` - already calls selection methods
- `UIDesignerInputHandler.java` - already calls selection methods
- `EditorShortcutHandlersImpl.java` - already calls selection methods

---

## Code Review Step

After implementation, create `Documents/Plans/undoable-selection/review.md` with a code review of all changed files.
