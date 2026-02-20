# Inspector Undo Coverage & Shortcut Applicability Design

**Date:** 2026-02-20
**Status:** Proposal
**Extends:** `Documents/Design/editor/shortcutUndoRefactorDesign.md` (Future Work section)

---

## Overview

The Option A quick fix from the shortcut/undo refactor solved the dispatch conflict — Ctrl+Z now routes correctly when the InspectorPanel is focused. But two gaps remain:

1. **7 inspectors use raw ImGui calls** instead of FieldEditors, so their edits push no undo commands. Ctrl+Z routes correctly but has nothing to revert.
2. **Option A duplicates scene-undo logic** in InspectorPanel that already exists in the global handler. Option D is a cleaner architectural solution that eliminates this duplication.

This design addresses both.

---

## Part 1: Option D — Conditional Shortcut Applicability

### Problem with Option A

Option A routes undo inside the handler:

```java
// InspectorPanel.java (current)
private void handleUndo() {
    if (isShowingAssetInspector()) {
        AssetInspectorRegistry.undo();
    } else {
        // Duplicates EditorShortcutHandlersImpl.onUndo()
        if (UndoManager.getInstance().undo()) {
            markSceneDirtyAfterUndoRedo();
        }
    }
}
```

The `else` branch duplicates `EditorShortcutHandlersImpl.onUndo()` — minus the status bar message. The InspectorPanel shouldn't own scene-undo logic; the global handler already does.

### Solution: `applicableWhen` predicate on ShortcutAction

Make `inspector.undo` declare itself inapplicable when not showing an asset inspector. The dispatch naturally falls through to `editor.edit.undo` (GLOBAL scope), which already handles `UndoManager.undo()` + `markDirty()` + status message.

### Changes to ShortcutAction.java

Add one field, one builder method, and a three-line check in `isApplicable()`:

```java
// New field
private final BooleanSupplier applicableWhen; // null = always applicable

// Updated constructor
private ShortcutAction(Builder builder) {
    // ... existing assignments ...
    this.applicableWhen = builder.applicableWhen;
}

// Updated isApplicable() — add guard at the top
public boolean isApplicable(ShortcutContext context) {
    if (applicableWhen != null && !applicableWhen.getAsBoolean()) {
        return false;
    }

    // ... existing scope/input checks unchanged ...
}

// New builder method
public static class Builder {
    private BooleanSupplier applicableWhen;

    public Builder applicableWhen(BooleanSupplier guard) {
        this.applicableWhen = guard;
        return this;
    }
}
```

### Changes to InspectorPanel.java

Simplify shortcuts to use `applicableWhen` + direct handlers. Remove routing methods.

**Before (Option A):**
```java
panelShortcut()
    .id("inspector.undo")
    .handler(this::handleUndo)  // routing method
    .build()
```

**After (Option D):**
```java
panelShortcut()
    .id("inspector.undo")
    .applicableWhen(this::isShowingAssetInspector)
    .handler(AssetInspectorRegistry::undo)  // single-purpose, no routing
    .build()
```

**Methods removed:** `handleUndo()`, `handleRedo()`, `markSceneDirtyAfterUndoRedo()`
**Fields removed:** `dirtyTracker`
**Methods kept:** `isShowingAssetInspector()` (now used as the predicate)

### Dispatch flow after Option D

When InspectorPanel is focused and showing **entity components**:

```
1. Ctrl+Z pressed
2. findBestMatch() checks inspector.undo (PANEL_FOCUSED, priority 1)
3. applicableWhen → isShowingAssetInspector() → false
4. isApplicable() returns false → skipped
5. findBestMatch() checks editor.edit.undo (GLOBAL, priority 3)
6. isApplicable() → true (global, no popup)
7. EditorShortcutHandlersImpl.onUndo() fires
8. UndoManager.undo() + markDirty() + "Undo" status message
```

When InspectorPanel is focused and showing **asset inspector**:

```
1. Ctrl+Z pressed
2. findBestMatch() checks inspector.undo (PANEL_FOCUSED, priority 1)
3. applicableWhen → isShowingAssetInspector() → true
4. isApplicable() → true (panel focused)
5. AssetInspectorRegistry.undo() fires
```

### Why this is better than Option A

| Aspect | Option A | Option D |
|--------|----------|----------|
| Scene undo path | Duplicated in InspectorPanel (without status message) | Handled by global handler (with status message) |
| InspectorPanel responsibility | Routes between asset and scene undo | Only handles asset undo (its actual concern) |
| DirtyTracker wiring | Needed in InspectorPanel | Not needed (global handler has it) |
| Status message on undo | Missing | Present (global handler shows "Undo") |
| Lines in InspectorPanel | ~40 (4 routing methods + dirtyTracker field) | ~10 (isShowingAssetInspector only) |
| Reusability | None | Any panel can use `applicableWhen` |

---

## Part 2: EnumSetEditor

### Purpose

Render all values of an enum as checkboxes (flags-style) backed by a `List<E>` field. Handles undo automatically via `ListItemCommand`.

### API

```java
// Reflection variant
EnumSetEditor.draw("Interact From", component, "interactFrom", Direction.class, entity);

// Getter/setter variant
EnumSetEditor.draw("Interact From", key, getter, setter, Direction.class);
```

### Rendering

Displays all enum constants as inline checkboxes. Checked = present in the list:

```
Interact From:  [x] UP  [x] DOWN  [ ] LEFT  [x] RIGHT
```

### Undo strategy — reflection variant

Uses `ListItemCommand` (existing class, no new commands):

```java
public static <E extends Enum<E>> boolean draw(String label, Component component,
                                                String fieldName, Class<E> enumClass,
                                                EditorGameObject entity) {
    List<E> list = (List<E>) ComponentReflectionUtils.getFieldValue(component, fieldName);
    E[] constants = enumClass.getEnumConstants();
    boolean changed = false;

    FieldEditorUtils.inspectorRow(label, () -> {
        for (int i = 0; i < constants.length; i++) {
            E value = constants[i];
            boolean active = list.contains(value);

            if (ImGui.checkbox(value.name() + "##" + fieldName, active)) {
                if (active) {
                    // Remove
                    int index = list.indexOf(value);
                    UndoManager.getInstance().execute(
                        new ListItemCommand(component, fieldName,
                            ListItemCommand.Operation.REMOVE, index, value, null, entity)
                    );
                } else {
                    // Add
                    UndoManager.getInstance().execute(
                        new ListItemCommand(component, fieldName,
                            ListItemCommand.Operation.ADD, list.size(), null, value, entity)
                    );
                }
                changed = true;
            }

            if (i < constants.length - 1) ImGui.sameLine();
        }
    });

    return changed;
}
```

### Undo strategy — getter/setter variant

Uses `SetterUndoCommand` with list snapshot (no reflection needed):

```java
public static <E extends Enum<E>> boolean draw(String label, String key,
                                                Supplier<List<E>> getter,
                                                Consumer<List<E>> setter,
                                                Class<E> enumClass) {
    List<E> list = getter.get();
    List<E> snapshot = new ArrayList<>(list);  // capture before state
    // ... render checkboxes, mutate list directly ...
    // On change:
    List<E> after = new ArrayList<>(list);
    UndoManager.getInstance().push(
        new SetterUndoCommand<>(setter, snapshot, after, "Change " + label)
    );
}
```

### File location

`src/main/java/com/pocket/rpg/editor/ui/fields/EnumSetEditor.java` — follows the existing pattern (`EnumEditor.java`, `ListEditor.java`).

### Integration with ReflectionFieldEditor

Not automatic. `List<Direction>` would still be dispatched to `ListEditor` by reflection. The `EnumSetEditor` is for custom inspectors that want the flags-style checkbox layout instead of the generic list UI. An annotation like `@EnumFlags` could enable auto-dispatch in the future, but that's out of scope.

---

## Part 3: MapEditor

### Purpose

Render `Map<K, V>` fields with add/remove/edit support and automatic undo.

### New undo command: MapItemCommand

Follows `ListItemCommand` pattern:

```java
public class MapItemCommand implements EditorCommand {

    public enum Operation {
        PUT,    // Add or update entry
        REMOVE  // Remove entry
    }

    private final Component component;
    private final String fieldName;
    private final Operation operation;
    private final Object key;
    private final Object oldValue;  // null for new entries
    private Object newValue;        // null for removals
    private final EditorGameObject entity;

    @Override
    public void execute() {
        Map<Object, Object> map = (Map<Object, Object>)
            ComponentReflectionUtils.getFieldValue(component, fieldName);
        switch (operation) {
            case PUT -> map.put(key, newValue);
            case REMOVE -> map.remove(key);
        }
    }

    @Override
    public void undo() {
        Map<Object, Object> map = (Map<Object, Object>)
            ComponentReflectionUtils.getFieldValue(component, fieldName);
        switch (operation) {
            case PUT -> {
                if (oldValue == null) map.remove(key);
                else map.put(key, oldValue);
            }
            case REMOVE -> map.put(key, oldValue);
        }
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof MapItemCommand cmd)) return false;
        return operation == Operation.PUT
            && cmd.operation == Operation.PUT
            && cmd.component == this.component
            && cmd.fieldName.equals(this.fieldName)
            && Objects.equals(cmd.key, this.key);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof MapItemCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}
```

### FieldMeta extension

Add `keyType` and `valueType` for Map fields:

```java
public record FieldMeta(
    String name,
    Class<?> type,
    Field field,
    Object defaultValue,
    Class<?> elementType,    // existing — for List<T>
    Class<?> keyType,        // NEW — for Map<K,V>, the key type K
    Class<?> valueType       // NEW — for Map<K,V>, the value type V
) {
    // Existing constructors updated with defaults (null, null)

    public boolean isMap() {
        return Map.class.isAssignableFrom(type) && keyType != null && valueType != null;
    }
}
```

The reflection system that builds `FieldMeta` (likely in `ComponentReflectionUtils` or wherever List element types are discovered) needs to extract `Map<K, V>` generic types the same way it extracts `List<T>` element types.

### MapEditor.java

Renders a map as a collapsible list of key-value rows:

```
Variables (2)                              [+]
  ├─ playerName    [Hero          ]        [x]
  └─ startGold     [100           ]        [x]
```

Structure follows `ListEditor.java`:
- Collapsible header with entry count
- Add button (creates entry with default key/value)
- Per-entry: key editor + value editor + remove button
- Type dispatch for keys and values (String, int, float, enum, etc.)
- Undo via `MapItemCommand`

### Value editing with undo

For value changes (most common operation):

```java
// Capture old value on widget activation
String undoKey = FieldUndoTracker.undoKey(component, fieldName + "[" + key + "]");
// ... render value editor ...
// FieldUndoTracker pattern won't work directly since MapItemCommand is not a simple setter.
// Instead, use explicit tracking:

if (ImGui.isItemActivated()) {
    capturedValues.put(undoKey, currentValue);
}
if (ImGui.isItemDeactivatedAfterEdit() && capturedValues.containsKey(undoKey)) {
    Object oldVal = capturedValues.remove(undoKey);
    if (!Objects.equals(oldVal, currentValue)) {
        UndoManager.getInstance().push(
            new MapItemCommand(component, fieldName, PUT, key, oldVal, currentValue, entity)
        );
    }
}
```

### ReflectionFieldEditor integration

Add a dispatch case in ReflectionFieldEditor:

```java
// After the List check (line ~173)
else if (meta.isMap()) {
    fieldChanged = MapEditor.drawMap(label, component, meta, entity);
}
```

### DialogueInteractableInspector's variable table

The variable table is a special case: keys come from an external asset (`DialogueVariables`), not from the map itself. The generic MapEditor renders both keys and values from the map. Two options:

**Option 1:** Use MapEditor in fixed-keys mode (only render/edit values for keys provided externally). This adds a parameter to `drawMap()`.

**Option 2:** Keep the custom layout but use `MapItemCommand` for undo instead of raw `map.put()`. The variable table already has a custom layout with type-based rendering (auto/runtime/static) that a generic MapEditor can't replicate.

**Recommendation: Option 2.** The variable table's layout is too specialized for a generic editor. Replace the raw `map.put()` with `MapItemCommand` pushes:

```java
// Before (no undo):
if (ImGui.inputText("##staticVar", varBuffer)) {
    component.getVariables().put(name, varBuffer.get());
    markSceneDirty();
}

// After (with undo):
if (ImGui.inputText("##staticVar", varBuffer)) {
    component.getVariables().put(name, varBuffer.get());
}
// Use activation/deactivation tracking for text input undo:
String undoKey = FieldUndoTracker.undoKey(component, "variables[" + name + "]");
if (ImGui.isItemActivated()) {
    capturedVarValues.put(undoKey, currentValue);
}
if (ImGui.isItemDeactivatedAfterEdit() && capturedVarValues.containsKey(undoKey)) {
    String oldVal = capturedVarValues.remove(undoKey);
    String newVal = varBuffer.get();
    if (!Objects.equals(oldVal, newVal)) {
        UndoManager.getInstance().push(
            new MapItemCommand(component, "variables", MapItemCommand.Operation.PUT,
                name, oldVal, newVal, editorEntity())
        );
    }
}
```

---

## Part 4: Inspector Conversions

### 4.1 Simple field conversions (existing FieldEditors)

These inspectors use raw ImGui for fields that have direct FieldEditor equivalents.

#### WarpZoneInspector

| Current raw ImGui | Field | Replacement |
|-------------------|-------|-------------|
| `ImGui.checkbox("##showLabel", ...)` | `showDestinationLabel` | `FieldEditors.drawBoolean()` or `PrimitiveEditors.drawBoolean()` (getter/setter) |
| `ImGui.checkbox("##useFade", ...)` | `useFade` | Same |
| `ImGui.checkbox("##overrideDefaults", ...)` | `overrideTransitionDefaults` | Same |
| `ImGui.dragFloat("##fadeOut", ...)` | `fadeOutDuration` | `FieldEditors.drawFloat()` or `PrimitiveEditors.drawFloat()` (getter/setter) |
| `ImGui.dragFloat("##fadeIn", ...)` | `fadeInDuration` | Same |
| `ImGui.selectable()` → `setTransitionName()` | `transitionName` | String combo with undo (see note below) |
| `ImGui.selectable()` → `setTargetScene()` | `targetScene` | String combo with undo |
| `ImGui.selectable()` → `setTargetSpawnId()` | `targetSpawnId` | String combo with undo |

#### DoorInspector

| Current raw ImGui | Field | Replacement |
|-------------------|-------|-------------|
| `ImGui.checkbox("##open", ...)` | `open` | `PrimitiveEditors.drawBoolean()` (getter/setter) |
| `ImGui.checkbox("##stayOpen", ...)` | `stayOpen` | Same |
| `ImGui.checkbox("##locked", ...)` | `locked` | Same |
| `ImGui.inputText("##keyId", ...)` | `requiredKeyId` | `PrimitiveEditors.drawString()` (getter/setter) |
| `ImGui.checkbox("##consumeKey", ...)` | `consumeKey` | `PrimitiveEditors.drawBoolean()` (getter/setter) |
| `ImGui.dragInt2("##offset", ...)` | `offsetX`, `offsetY` | Two `PrimitiveEditors.drawInt()` calls |
| `ImGui.dragInt2("##size", ...)` | `width`, `height` | Two `PrimitiveEditors.drawInt()` calls |
| `ImGui.selectable()` → elevation | `elevation` | `EnumEditor.drawEnum()` (getter/setter) if enum, or string combo |

#### StaticOccupantInspector

| Current raw ImGui | Field | Replacement |
|-------------------|-------|-------------|
| `ImGui.dragInt2("##offset", ...)` | `offsetX`, `offsetY` | Two `PrimitiveEditors.drawInt()` (getter/setter) |
| `ImGui.dragInt2("##size", ...)` | `width`, `height` | Two `PrimitiveEditors.drawInt()` (getter/setter) |

#### SpawnPointInspector

| Current raw ImGui | Field | Replacement |
|-------------------|-------|-------------|
| `ImGui.inputText("##spawnId", ...)` | `spawnId` | `PrimitiveEditors.drawString()` (getter/setter) |
| `ImGui.selectable()` → direction | `facingDirection` | `EnumEditor.drawEnum()` (getter/setter) |
| `ImGui.selectable()` → cameraBounds | `cameraBoundsId` | String combo with undo |

#### DialogueEventListenerInspector

| Current raw ImGui | Field | Replacement |
|-------------------|-------|-------------|
| `ImGui.selectable()` → eventName | `eventName` | String combo with undo |

### 4.2 String combo pattern (dynamic list selection with undo)

Several inspectors use `ImGui.beginCombo()` + `ImGui.selectable()` to pick from a dynamic list of strings (scene names, spawn IDs, event names, transition names). These aren't enums — the options are loaded at runtime.

No existing FieldEditor handles this. Two approaches:

**Approach A: Inline undo wrapper.** Wrap the existing combo code with `SetterUndoCommand`:

```java
String old = getter.get();
if (ImGui.beginCombo("##field", current)) {
    for (String option : options) {
        if (ImGui.selectable(option, option.equals(current))) {
            setter.accept(option);
            if (editorEntity() != null) {
                UndoManager.getInstance().push(
                    new SetterUndoCommand<>(setter, old, option, "Change " + label)
                );
            }
        }
    }
    ImGui.endCombo();
}
```

**Approach B: New `StringComboEditor`.** A reusable widget:

```java
StringComboEditor.draw("Target Scene", key, getter, setter, sceneNames);
```

**Recommendation: Approach B.** This pattern appears in at least 5 places across 4 inspectors. A reusable widget avoids duplicating the undo boilerplate.

### 4.3 TilemapLayersInspector

This inspector edits `TilemapLayer` and `Scene` objects, not `Component` fields. Reflection-based FieldEditors won't work. Getter/setter variants apply:

| Current raw ImGui | Object | Field | Replacement |
|-------------------|--------|-------|-------------|
| `ImGui.radioButton()` → visibility mode | Scene | `visibilityMode` | `EnumEditor.drawEnum()` (getter/setter) |
| `ImGui.sliderFloat()` → dimmed opacity | Scene | `dimmedOpacity` | `PrimitiveEditors.drawFloat()` (getter/setter) with `FieldUndoTracker.track()` |
| `ImGui.smallButton()` → visible toggle | TilemapLayer | `visible` | Transient editor state — **undo not needed** |
| `ImGui.smallButton()` → locked toggle | TilemapLayer | `locked` | Transient editor state — **undo not needed** |
| `ImGui.inputText()` → layer name | TilemapLayer | `name` | `PrimitiveEditors.drawString()` (getter/setter) |
| `ImGui.inputInt()` → zIndex | TilemapLayer | `zIndex` | `PrimitiveEditors.drawInt()` (getter/setter) |
| `ImGui.smallButton()` → up/down | TilemapLayer | `zIndex` | Button with `SetterUndoCommand` on click |

Note: `visible` and `locked` are transient editor-only state (not saved), so undo is not needed for those.

### 4.4 DialogueInteractableInspector (complex cases)

Beyond the variable table (covered in Part 3), this inspector has:

**Direction checkboxes (`interactFrom`):** → `EnumSetEditor.draw()` (Part 2)

**Conditional dialogues list (`conditionalDialogues`):** This is a `List<ConditionalDialogue>` with a custom layout (collapsing headers, nested conditions, dialogue picker, remove button). The generic `ListEditor` can't replicate this layout. Keep the custom rendering but add undo:

- **Add conditional:** `ListItemCommand(ADD, ...)`
- **Remove conditional:** `ListItemCommand(REMOVE, ...)`
- **Edit condition event name:** `SetterUndoCommand` via `StringComboEditor`
- **Edit condition expected state:** `SetterUndoCommand` via `EnumEditor.drawEnum()` (getter/setter)
- **Edit dialogue reference:** Already uses `FieldEditors.drawAsset()` — already has undo

**Add/remove conditions within a ConditionalDialogue:** The conditions list (`List<DialogueCondition>`) is on a nested object, not directly on the component. `ListItemCommand` requires a component + field name. Two options:

- Store a reference to the parent component and use a path-based field accessor (complex, fragile)
- Use `SetterUndoCommand` with a list snapshot on the ConditionalDialogue

**Recommendation:** Use `SetterUndoCommand` with list snapshots for nested list mutations:

```java
// Remove condition
List<DialogueCondition> snapshot = new ArrayList<>(cd.getConditions());
conditions.remove(index);
List<DialogueCondition> after = new ArrayList<>(conditions);
UndoManager.getInstance().push(
    new SetterUndoCommand<>(cd::setConditions, snapshot, after, "Remove condition")
);
```

This is simple and avoids extending `ListItemCommand` to support nested objects.

---

## Files to Change

### New files

| File | Purpose |
|------|---------|
| `editor/ui/fields/EnumSetEditor.java` | Enum-as-checkboxes widget with undo |
| `editor/ui/fields/MapEditor.java` | Map field editor with undo |
| `editor/ui/fields/StringComboEditor.java` | String-from-list combo with undo |
| `editor/undo/commands/MapItemCommand.java` | Undo command for map PUT/REMOVE |

### Modified files

| File | Change |
|------|--------|
| `editor/shortcut/ShortcutAction.java` | Add `applicableWhen` field + builder method + `isApplicable()` guard |
| `editor/panels/InspectorPanel.java` | Use `applicableWhen`, remove routing methods and `dirtyTracker` |
| `serialization/FieldMeta.java` | Add `keyType`, `valueType` for Map support |
| `editor/ui/fields/ReflectionFieldEditor.java` | Add Map dispatch case |
| `editor/ui/fields/FieldEditors.java` | Add facade methods for EnumSet, Map, StringCombo |
| `editor/ui/inspectors/WarpZoneInspector.java` | Convert raw ImGui → FieldEditors |
| `editor/ui/inspectors/DoorInspector.java` | Convert raw ImGui → FieldEditors |
| `editor/ui/inspectors/StaticOccupantInspector.java` | Convert raw ImGui → FieldEditors |
| `editor/ui/inspectors/SpawnPointInspector.java` | Convert raw ImGui → FieldEditors |
| `editor/ui/inspectors/DialogueEventListenerInspector.java` | Convert raw ImGui → FieldEditors |
| `editor/ui/inspectors/DialogueInteractableInspector.java` | EnumSetEditor for directions, MapItemCommand for variables, undo for conditionals |
| `editor/panels/inspector/TilemapLayersInspector.java` | Convert applicable fields → getter/setter FieldEditors |
| `.claude/reference/field-editors.md` | Document new editors |

### Wiring / reflection changes

| File | Change |
|------|--------|
| Wherever `FieldMeta` is constructed | Extract Map generic types (`keyType`/`valueType`) from `ParameterizedType` |

---

## Phases

### Phase 1: Option D — Shortcut applicability

- [ ] Add `applicableWhen` field + builder method to `ShortcutAction`
- [ ] Add guard check to `ShortcutAction.isApplicable()`
- [ ] Update `InspectorPanel.provideShortcuts()` to use `applicableWhen(this::isShowingAssetInspector)`
- [ ] Revert handlers to `AssetInspectorRegistry::undo` / `::redo`
- [ ] Remove `handleUndo()`, `handleRedo()`, `markSceneDirtyAfterUndoRedo()`
- [ ] Remove `dirtyTracker` field and its wiring
- [ ] Manual test: entity undo, asset undo, status message, dirty tracking

### Phase 2: New field editors

- [ ] Create `EnumSetEditor.java` (reflection + getter/setter variants)
- [ ] Create `StringComboEditor.java` (getter/setter, dynamic options list)
- [ ] Create `MapItemCommand.java`
- [ ] Extend `FieldMeta` with `keyType`, `valueType`, `isMap()`
- [ ] Update FieldMeta construction to extract Map generic types
- [ ] Create `MapEditor.java` (reflection variant)
- [ ] Add Map dispatch to `ReflectionFieldEditor`
- [ ] Add facade methods to `FieldEditors.java`

### Phase 3: Simple inspector conversions

- [ ] Convert `WarpZoneInspector` — booleans, floats, string combos
- [ ] Convert `DoorInspector` — booleans, string, ints, elevation
- [ ] Convert `StaticOccupantInspector` — int pairs
- [ ] Convert `SpawnPointInspector` — string, enum, string combo
- [ ] Convert `DialogueEventListenerInspector` — string combo
- [ ] Convert `TilemapLayersInspector` — enum, float, string, int (getter/setter variants)

### Phase 4: DialogueInteractableInspector conversion

- [ ] Replace direction checkboxes with `EnumSetEditor.draw()`
- [ ] Add undo to conditional dialogue add/remove via `ListItemCommand`
- [ ] Add undo to condition add/remove via `SetterUndoCommand` with list snapshot
- [ ] Replace condition event name combo with `StringComboEditor`
- [ ] Replace condition expected state combo with `EnumEditor.drawEnum()` (getter/setter)
- [ ] Add undo to variable table via `MapItemCommand`

### Phase 5: Documentation & review

- [ ] Update `.claude/reference/field-editors.md` with new editors
- [ ] Update `.claude/reference/common-pitfalls.md` if new patterns apply
- [ ] Code review

---

## Testing Strategy

### Phase 1 tests (Option D)

1. **Entity undo with Inspector focused:** Select entity, edit a field, Ctrl+Z. Verify undo fires via global handler, dirty indicator updates, status bar shows "Undo".
2. **Asset undo with Inspector focused:** Select asset, edit, Ctrl+Z. Verify asset undo fires.
3. **Context switch:** Edit entity, select asset, select entity again, Ctrl+Z. Verify scene undo fires.
4. **Other panels unaffected:** Ctrl+Z in Animation/Animator/Dialogue editors still uses their own undo stacks.

### Phase 2 tests (new editors)

5. **EnumSetEditor:** Toggle checkboxes, Ctrl+Z reverts each toggle individually.
6. **StringComboEditor:** Select from combo, Ctrl+Z reverts to previous selection.
7. **MapEditor:** Add entry, edit value, remove entry — each undoable. Verify merge on consecutive value edits.
8. **Reflection auto-dispatch:** Add a `Map<String, String>` field to a test component, verify MapEditor renders automatically.

### Phase 3-4 tests (inspector conversions)

9. **Per-inspector regression:** For each converted inspector, edit every field and verify Ctrl+Z reverts it.
10. **DialogueInteractableInspector full flow:** Toggle directions, add/remove conditionals, change conditions, edit variables — verify all operations are undoable.
11. **Prefab overrides:** Edit fields in a prefab instance inspector, verify override styling and reset buttons work with the new FieldEditor calls.

### Edge cases

12. **Empty undo stack:** Press Ctrl+Z with nothing to undo. No error, no crash.
13. **Rapid toggles:** Toggle an EnumSetEditor checkbox rapidly 5+ times, then undo multiple times. Verify each toggle is a separate undo step.
14. **Play mode:** Enter play mode, verify no undo operations fire from Inspector.

---

## Open Questions

1. **Should `EnumSetEditor` support horizontal vs vertical layout?** The direction checkboxes are horizontal (4 values, fits on one row). A larger enum (8+ values) might need vertical layout or wrapping. **Proposal:** Default to horizontal with `ImGui.sameLine()`, wrap naturally when the row overflows.

2. **Should `MapEditor` support key editing?** For the `variables` map, keys are fixed by an external asset. For a generic `Map<String, String>` on a component, both keys and values should be editable. **Proposal:** Generic `MapEditor` supports key editing. The variable table keeps its custom layout and uses `MapItemCommand` directly.

3. **Should `StringComboEditor` support a "clear" option?** Some fields allow null/empty. **Proposal:** Yes, include an optional "None" entry at the top when a `nullable` flag is set.