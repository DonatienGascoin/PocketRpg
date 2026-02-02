# Plan: Change inspector entity type from EditorGameObject to HierarchyItem

## Problem

In play mode, `ComponentFieldEditor.renderRuntimeComponentFields` passes `null` for entity to avoid undo/prefab logic. Complex inspectors like `UITransformInspector` use `entity` for scene graph queries (`getParent()`, `getChildren()`, `getComponent()`) and crash with NPE. Current fix is whack-a-mole null guards that keep missing sites.

## Solution

Change `CustomComponentInspector.entity` from `EditorGameObject` to `HierarchyItem`. Pass the real `RuntimeGameObjectAdapter` through the call chain in play mode instead of `null`. Undo/prefab code downcasts via a new `editorEntity()` helper.

The split: `ReflectionFieldEditor.drawComponent` accepts `HierarchyItem` and passes it to custom editors, but extracts `EditorGameObject` (or null) for undo/context/field-editor paths. The undo infrastructure (`FieldEditorContext`, `FieldEditors`, `drawField`, undo commands) keeps its `EditorGameObject` types unchanged.

---

## Phase 1: Interface & adapter (additive, no behavior change)

**`HierarchyItem.java`** — Add `getHierarchyParent()` (follows existing `getHierarchyChildren()` naming, avoids conflict with `EditorGameObject.getParent()` returning `EditorGameObject`):
```java
HierarchyItem getHierarchyParent();
```

**`EditorGameObject.java`** — Implement (delegates to existing `parent` field, which is an `EditorGameObject` and thus a `HierarchyItem`):
```java
@Override
public HierarchyItem getHierarchyParent() { return parent; }
```

**`RuntimeGameObjectAdapter.java`** — Implement (delegates to `GameObject.getParent()`):
```java
@Override
public HierarchyItem getHierarchyParent() {
    GameObject parent = gameObject.getParent();
    return parent != null ? RuntimeGameObjectAdapter.of(parent) : null;
}
```

## Phase 2: Core type change

**`CustomComponentInspector.java`**:
- `protected EditorGameObject entity` → `protected HierarchyItem entity`
- `bind(Component, EditorGameObject)` → `bind(Component, HierarchyItem)`
- Add convenience helper for subclasses (with Javadoc explaining the convention):
```java
/**
 * Returns the entity as {@link EditorGameObject}, or null if in play mode.
 * <p>
 * Use this for operations that only apply in the editor:
 * undo commands, prefab overrides, position access via
 * {@code EditorGameObject.getPosition()}.
 * <p>
 * For scene graph queries (getComponent, parent/children),
 * use {@link #entity} directly — it is always non-null.
 */
protected EditorGameObject editorEntity() {
    return entity instanceof EditorGameObject ego ? ego : null;
}
```
- No other callers of `bind()` besides the registry — confirmed safe.

**`CustomComponentEditorRegistry.java`**:
- `drawCustomEditor(Component, EditorGameObject)` → `drawCustomEditor(Component, HierarchyItem)`
- The `FieldEditorContext.begin(entity, ...)` call: pass `entity instanceof EditorGameObject ego ? ego : null` so undo/override context is null in play mode (existing behavior)

## Phase 3: Call chain plumbing

**`ReflectionFieldEditor.java`** — `drawComponent(Component, EditorGameObject)` → `drawComponent(Component, HierarchyItem)`:
- Extract `EditorGameObject` internally:
  ```java
  EditorGameObject editorEntity = hierarchyEntity instanceof EditorGameObject ego ? ego : null;
  ```
- Pass `hierarchyEntity` to `CustomComponentEditorRegistry.drawCustomEditor` (custom editors get the full HierarchyItem)
- Pass `editorEntity` (may be null) to: `FieldEditorContext.begin()`, `drawField()`, `drawComponentReferences()`
- The `drawField`/`drawFieldInternal` methods keep `EditorGameObject entity` parameter — they use it for undo logic (line 175 `if (entity != null)`) and pass it to `FieldEditors.drawAsset/drawAudioClip/ListEditor.drawList`. All of these already handle null gracefully.
- `drawComponentReferences` keeps `EditorGameObject` param — returns `UNKNOWN` status when null (line 296).

**`ComponentFieldEditor.java`**:
- `renderRuntimeComponentFields(Component)` → `renderRuntimeComponentFields(Component, HierarchyItem)`
- Passes entity through to `ReflectionFieldEditor.drawComponent`
- `renderComponentFields(EditorGameObject, Component, boolean)` — already passes `EditorGameObject`, just update the `drawComponent` call (EditorGameObject IS a HierarchyItem, so it fits the new signature)

**`EntityInspector.java`**:
- `renderRuntime(IGameObject)` → `renderRuntime(HierarchyItem)` (callers always pass `RuntimeGameObjectAdapter` which implements `HierarchyItem`)
- Pass entity through: `fieldEditor.renderRuntimeComponentFields(comp, gameObject)`

## Phase 4: UITransformInspector

**Parent access** — Replace `entity.getParent()` → `entity.getHierarchyParent()`:
- Line 180: `entity != null ? entity.getParent() : null` → `entity.getHierarchyParent()` (entity is now always non-null; parent result can still be null, that null check on the result stays)
- Line 451 (match-parent button): `entity.getParent()` → `entity.getHierarchyParent()` — this block is inside `if (hasParentUITransform)` which is false when parent is null, so it's safe
- `getParentUITransform()`: remove `if (entity == null) return null` guard, change to `entity.getHierarchyParent()`
- `getParentLayoutGroup()`: remove `if (entity == null) return null` guard, change to `entity.getHierarchyParent()`, change `parentEntity.getComponents()` → `parentEntity.getAllComponents()`

**Component lookup** — Replace `getComponentByType(String)` → `getComponent(Class)`:
- `getSpriteTextureDimensions()`: remove `if (entity == null) return null` guard, replace:
  ```java
  entity.getComponentByType("com.pocket.rpg.components.ui.UIImage")  → entity.getComponent(UIImage.class)
  entity.getComponentByType("com.pocket.rpg.components.ui.UIButton") → entity.getComponent(UIButton.class)
  ```
  Bonus: fixes pre-existing bug where fully-qualified name was compared against `getSimpleName()`. Call out separately in PR description.

**Child iteration** — `captureChildStates`:
- Parameter: `EditorGameObject parent` → `HierarchyItem parent`
- Iteration: `parent.getChildren()` → `parent.getHierarchyChildren()`
- `ChildState.entity`: `EditorGameObject` → `HierarchyItem` (add comment: may be a RuntimeGameObjectAdapter in play mode; only used for undo when it is an EditorGameObject)
- `startSizeEdit()`: remove the `if (entity != null)` guard — entity is always non-null now, cascading works in both modes

**Undo guards** — use `editorEntity()`:
- `createAnchorPivotCommand`: `if (entity == null) return` → `if (editorEntity() == null) return`, use `editorEntity()` in command construction
- `commitSizeEdit`: `if (hasChanges && entity != null)` → `if (hasChanges && editorEntity() != null)`, use `editorEntity()` for `UITransformDragCommand.resize(...)`. For child undo states, filter: `if (state.entity instanceof EditorGameObject childEgo)` then build `ChildTransformState` with `childEgo`. Non-EditorGameObject children are skipped for undo but still get visual cascading.
- `drawPresetGrid`: parameter `EditorGameObject entity` → `HierarchyItem entity` (it doesn't use entity directly, only passes to `createAnchorPivotCommand`)

**Local variable types** — Change `EditorGameObject parentEntity` → `HierarchyItem parentEntity` in all locations where it comes from `getHierarchyParent()`

## Phase 5: Other inspectors

**Position access** (`getPosition()`/`setPosition()` are `EditorGameObject`-specific, handle prefab overrides):

- **`GridMovementInspector`**: Change `entity == null` → `editorEntity() == null`, use `editorEntity().getPosition()`/`setPosition()`, `editorEntity().getComponent(...)`
- **`DoorInspector`**: Change `entity == null` → `editorEntity() == null`, use `editorEntity().getPosition()`
- **`SpawnPointInspector`**: Change `entity == null` → `editorEntity() == null`, use `editorEntity().getPosition()`
- **`StaticOccupantInspector`**: Change `entity == null` → `editorEntity() == null`, use `editorEntity().getPosition()`

**Undo guards** (already have `entity != null` checks, change to `editorEntity()`):

- **`UIImageInspector`**: Change `entity != null` → `editorEntity() != null`, use `editorEntity()` for `SetComponentFieldCommand` and `UITransformDragCommand.resize()` in `resetSizeToSprite`. The `entity.getComponent(UITransform.class)` call works on HierarchyItem (via IGameObject), but the `entity == null` guard in `resetSizeToSprite` should become `editorEntity() == null` since it protects undo.
- **`UIButtonInspector`**: Same pattern as UIImageInspector.
- **`UITextInspector`**: Change `entity != null` → `editorEntity() != null` for all undo guards.
- **`UIPanelInspector`**: Change `entity != null` → `editorEntity() != null` for undo guards.

**Pass-through to FieldEditors** (methods that take `EditorGameObject`):

- **`WarpZoneInspector`**: `FieldEditors.drawAudioClip(..., entity)` → `FieldEditors.drawAudioClip(..., editorEntity())`
- **`SpawnPointInspector`**: `FieldEditors.drawAudioClip(..., entity)` → same
- **`TransformInspector`**: `FieldEditors.drawPosition/Scale/Rotation("...", entity)` → pass `editorEntity()`. These methods already handle null (fall back to `FieldEditorContext.getComponent()`).
- **`CameraBoundsZoneInspector`**: `ReflectionFieldEditor.drawField(..., entity)` → pass `editorEntity()`
- **`UIButtonInspector`/`UIImageInspector`**: `FieldEditors.drawAsset(..., entity)` → pass `editorEntity()`

---

## Files modified

| File | Change |
|------|--------|
| `editor/panels/hierarchy/HierarchyItem.java` | Add `getHierarchyParent()` |
| `editor/scene/EditorGameObject.java` | Implement `getHierarchyParent()` |
| `editor/scene/RuntimeGameObjectAdapter.java` | Implement `getHierarchyParent()` |
| `editor/ui/inspectors/CustomComponentInspector.java` | Entity type → `HierarchyItem`, add `editorEntity()` |
| `editor/ui/inspectors/CustomComponentEditorRegistry.java` | Param type → `HierarchyItem` |
| `editor/ui/fields/ReflectionFieldEditor.java` | Param type → `HierarchyItem`, extract EditorGameObject for undo |
| `editor/panels/inspector/ComponentFieldEditor.java` | Add `HierarchyItem` param to runtime method |
| `editor/panels/inspector/EntityInspector.java` | Param → `HierarchyItem`, pass entity through |
| `editor/ui/inspectors/UITransformInspector.java` | Hierarchy API, `editorEntity()`, remove old null guards |
| `editor/ui/inspectors/GridMovementInspector.java` | `editorEntity()` for position |
| `editor/ui/inspectors/DoorInspector.java` | `editorEntity()` for position |
| `editor/ui/inspectors/SpawnPointInspector.java` | `editorEntity()` for position + audio |
| `editor/ui/inspectors/StaticOccupantInspector.java` | `editorEntity()` for position |
| `editor/ui/inspectors/UIImageInspector.java` | `editorEntity()` for undo + assets |
| `editor/ui/inspectors/UIButtonInspector.java` | `editorEntity()` for undo + assets |
| `editor/ui/inspectors/UITextInspector.java` | `editorEntity()` for undo |
| `editor/ui/inspectors/UIPanelInspector.java` | `editorEntity()` for undo |
| `editor/ui/inspectors/WarpZoneInspector.java` | `editorEntity()` for audio |
| `editor/ui/inspectors/TransformInspector.java` | `editorEntity()` for FieldEditors |
| `editor/ui/inspectors/CameraBoundsZoneInspector.java` | `editorEntity()` for drawField |

**NOT modified**: `UITransformDragCommand.java`, `SetComponentFieldCommand.java`, `FieldEditorContext.java`, `FieldEditors.java`, `TransformEditors.java`, any undo command — these keep `EditorGameObject` types.

## Worktree Setup

- Worktree: `.worktrees/inspector-hierarchy-entity`
- Branch: `inspector-hierarchy-entity`

```bash
mkdir -p .worktrees
git worktree add .worktrees/inspector-hierarchy-entity -b inspector-hierarchy-entity
```

Commit per phase, PR after phase 1, wait for user review between phases.

## Verification

```bash
mvn compile    # No type errors
mvn test       # Existing tests pass
```

Manual:
- Editor mode: select UI entity → anchor/pivot grids, size fields, undo all work
- Editor mode: resize with children → cascading resize + undo works
- Play mode: select UI entity → inspector renders, fields editable, no NPE
- Play mode: edit size field → cascading resize works (no undo, changes temporary)
- Play mode: select entity with GridMovement/Door/SpawnPoint → inspector renders, no NPE
- Editor mode after play mode: verify undo stack is clean (no stale runtime objects)
