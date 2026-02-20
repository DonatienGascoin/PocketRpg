# Nested Hierarchy Prefabs

## Overview

Prefabs currently store a flat `List<Component>` representing a single entity. This plan adds hierarchy support so prefabs can define a tree of entities (root + children), enabling compound objects like "Player with Shadow" or "NPC with DialogueZone".

### Key Design Decision

Reuse `GameObjectData` for the prefab's internal hierarchy -- the same format used by scene files. No new data classes. The prefab stores a flat list of `GameObjectData` entries with `parentId` references, exactly like a scene's `gameObjects` array.

---

## Prefab Format

### Current (single entity)
```json
{
  "id": "test_barrel",
  "displayName": "Test Barrel",
  "components": [
    {"type": "...Transform", "properties": {...}},
    {"type": "...SpriteRenderer", "properties": {...}}
  ]
}
```

### New (hierarchy via `gameObjects`)
```json
{
  "id": "player_with_shadow",
  "displayName": "Player With Shadow",
  "category": "Characters",
  "gameObjects": [
    {
      "id": "root01",
      "name": "Player With Shadow",
      "components": [
        {"type": "...Transform", "properties": {...}},
        {"type": "...SpriteRenderer", "properties": {...}}
      ]
    },
    {
      "id": "shadow01",
      "name": "Shadow",
      "parentId": "root01",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {...}},
        {"type": "...SpriteRenderer", "properties": {...}}
      ]
    }
  ]
}
```

### Backward Compatibility

- If `gameObjects` is present, it takes precedence. The `components` field is ignored.
- If only `components` is present (old format), treated as single root entity.
- `Prefab.getComponents()` returns root entity's components in either case.
- When a prefab is saved from the editor, `components` is set to `null` and `gameObjects` is written. This prevents dual-format ambiguity.
- Entries in `gameObjects` must be scratch (no `prefabId` set). This is validated on load.

### Node ID Stability

Each `GameObjectData` in the prefab's `gameObjects` list has an `id` field (8-char UUID). This serves as the **stable node identifier** that scene instances reference via `prefabNodeId`.

**Rules:**
- Node IDs are generated when a child is first created (via `UUID.randomUUID().substring(0,8)`).
- Node IDs are preserved when saving/reloading the prefab (the `PrefabEditController` maintains a `workingEntityNodeIds` map).
- Node IDs are never reused across different nodes.
- When the prefab is saved, existing node IDs are preserved for unchanged children. New children get fresh IDs.
- `findNode(nodeId)` uses a `Map<String, GameObjectData>` lookup (built lazily, cached) for O(1) access.

---

## Instance Model

When a prefab instance is placed in a scene, **every node in the prefab tree becomes a real `EditorGameObject`**, parented via the existing hierarchy system.

### Why `prefabNodeId` is needed

A prefab instance's child entity needs to know **which node in the prefab definition** it corresponds to. This is essential for:
1. **Component resolution**: `getMergedComponents()` must clone components from the correct prefab node (child's components, not root's).
2. **Default values**: `getFieldDefault()` must return the correct node's defaults for the "bold field" and "reset to default" features.
3. **Override detection**: `isFieldOverridden()` compares the instance value against the correct node's defaults.
4. **Instance sync**: When the prefab changes, the engine can match each scene entity to its corresponding prefab node.

Without `prefabNodeId`, there is no way to link a scene child entity back to its definition in the prefab.

### Root entity
- `prefabId = "player_with_shadow"` (existing)
- `prefabNodeId = null` (root)
- `componentOverrides = {Transform: {localPosition: [5, 3, 0]}}` (existing)

### Child entity
- `prefabId = "player_with_shadow"` (same prefab)
- `prefabNodeId = "shadow01"` (NEW -- references `GameObjectData.id` from the prefab)
- `parentId = root.id` (existing hierarchy)
- `componentOverrides = {}` (per-child overrides, same mechanism)

### Scene file
```json
[
  {"id": "abc123", "prefabId": "player_with_shadow", "componentOverrides": {...}},
  {"id": "def456", "prefabId": "player_with_shadow", "prefabNodeId": "shadow01",
   "parentId": "abc123", "componentOverrides": {}}
]
```

---

## Grandchild Example (Depth > 1)

This example validates that the flat-list + `parentId` approach supports arbitrary nesting depth.

### Prefab definition
```json
{
  "id": "guard_tower",
  "displayName": "Guard Tower",
  "category": "Structures",
  "gameObjects": [
    {
      "id": "root0001",
      "name": "Guard Tower",
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 0.0, "z": 0.0},
          "localScale": {"x": 1.0, "y": 1.0, "z": 1.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 5}}
      ]
    },
    {
      "id": "guard001",
      "name": "Guard",
      "parentId": "root0001",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 1.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 10}}
      ]
    },
    {
      "id": "helm0001",
      "name": "Helmet",
      "parentId": "guard001",
      "order": 0,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.0, "y": 0.3, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 12}}
      ]
    },
    {
      "id": "weap0001",
      "name": "Spear",
      "parentId": "guard001",
      "order": 1,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.3, "y": 0.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 11}}
      ]
    },
    {
      "id": "flag0001",
      "name": "Flag",
      "parentId": "root0001",
      "order": 1,
      "components": [
        {"type": "...Transform", "properties": {
          "localPosition": {"x": 0.5, "y": 2.0, "z": 0.0}
        }},
        {"type": "...SpriteRenderer", "properties": {"zIndex": 15}}
      ]
    }
  ]
}
```

### Hierarchy
```
Guard Tower (root0001)           ← root, no parentId
├── Guard (guard001)             ← child, parentId = root0001
│   ├── Helmet (helm0001)        ← grandchild, parentId = guard001
│   └── Spear (weap0001)        ← grandchild, parentId = guard001
└── Flag (flag0001)              ← child, parentId = root0001
```

### Scene file with overrides at every level
```json
[
  {
    "id": "abc1",
    "prefabId": "guard_tower",
    "componentOverrides": {
      "com.pocket.rpg.components.core.Transform": {
        "localPosition": {"x": 5.0, "y": 3.0, "z": 0.0}
      }
    }
  },
  {
    "id": "abc2",
    "prefabId": "guard_tower",
    "prefabNodeId": "guard001",
    "parentId": "abc1",
    "componentOverrides": {
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "zIndex": 15
      }
    }
  },
  {
    "id": "abc3",
    "prefabId": "guard_tower",
    "prefabNodeId": "helm0001",
    "parentId": "abc2",
    "componentOverrides": {
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "sprite": "com.pocket.rpg.rendering.resources.Sprite:equipment.png#gold_helmet",
        "zIndex": 17
      }
    }
  },
  {
    "id": "abc4",
    "prefabId": "guard_tower",
    "prefabNodeId": "weap0001",
    "parentId": "abc2",
    "componentOverrides": {}
  },
  {
    "id": "abc5",
    "prefabId": "guard_tower",
    "prefabNodeId": "flag0001",
    "parentId": "abc1",
    "componentOverrides": {
      "com.pocket.rpg.components.core.Transform": {
        "localPosition": {"x": -0.5, "y": 2.0, "z": 0.0}
      },
      "com.pocket.rpg.components.rendering.SpriteRenderer": {
        "zIndex": 20
      }
    }
  }
]
```

### Override resolution

| Entity | Field | Prefab Default | Override | Resolved |
|--------|-------|---------------|----------|----------|
| **Guard Tower** (root) | Transform.localPosition | 0, 0, 0 | **5, 3, 0** | **5, 3, 0** |
| **Guard** (child) | SpriteRenderer.zIndex | 10 | **15** | **15** |
| **Guard** (child) | Transform.localPosition | 0, 1, 0 | — | 0, 1, 0 |
| **Helmet** (grandchild) | SpriteRenderer.sprite | *(none)* | **equipment.png#gold_helmet** | **gold_helmet** |
| **Helmet** (grandchild) | SpriteRenderer.zIndex | 12 | **17** | **17** |
| **Helmet** (grandchild) | Transform.localPosition | 0, 0.3, 0 | — | 0, 0.3, 0 |
| **Spear** (grandchild) | *(all fields)* | *(from prefab)* | — | *(all defaults)* |
| **Flag** (child) | Transform.localPosition | 0.5, 2, 0 | **-0.5, 2, 0** | **-0.5, 2, 0** |
| **Flag** (child) | SpriteRenderer.zIndex | 15 | **20** | **20** |

Each entity's `getFieldDefault()` resolves against **its own node** via `prefab.findNode(prefabNodeId)`. Resetting the Helmet's zIndex returns 12 (from `helm0001`), not 5 (from `root0001`) or 10 (from `guard001`). The override system is uniform regardless of depth.

Note: `parentId` values in the scene file reference **scene entity IDs** (abc1, abc2...), not prefab node IDs. The `prefabNodeId` values reference back to the prefab definition for component resolution. This separation is what makes arbitrary depth work.

---

## Override System for Children

Overrides work **identically** for root and child entities because each is its own `EditorGameObject` with its own `componentOverrides` map. A parent and child can both have `SpriteRenderer` with different overrides -- there is no conflict because overrides are per-entity, not per-prefab.

### Complete override lifecycle for a child entity

1. **Component resolution** (`getMergedComponents()`):
   - Calls `prefab.findNode(prefabNodeId)` to get the child's `GameObjectData`
   - Clones the node's components
   - Applies `componentOverrides` on top
   - Caches result in `cachedMergedComponents`

2. **Reading a field value** (`getFieldValue(componentType, fieldName)`):
   - Checks `componentOverrides[componentType][fieldName]` first
   - Falls back to `prefab.getChildFieldDefault(prefabNodeId, componentType, fieldName)` for defaults from the correct child node

3. **Writing a field value** (`setFieldValue(componentType, fieldName, value)`):
   - Stores in `componentOverrides[componentType][fieldName]`
   - Must call `invalidateComponentCache()` to refresh merged components

4. **Override detection** (`isFieldOverridden(componentType, fieldName)`):
   - Checks if `componentOverrides` has the field
   - Compares against `getFieldDefault()` which resolves to the child node's default (not root's)
   - Returns true only if the value differs from the child node's default

5. **Reset to default** (`resetFieldToDefault(componentType, fieldName)`):
   - Removes the field from `componentOverrides`
   - Invalidates component cache
   - Value reverts to the child node's default from the prefab

6. **Reset all overrides** (`resetAllOverrides()`):
   - Clears all `componentOverrides`
   - Only affects this entity (root or child independently)

### Stale node handling

If `prefab.findNode(prefabNodeId)` returns null (node was removed from prefab):
- `getMergedComponents()` returns an empty list
- A broken-node indicator is shown (similar to broken prefab link)
- Console warning logged: "Prefab node 'shadow01' not found in 'player_with_shadow'"

---

## Instance Synchronization

When a prefab definition changes (children added/removed/reordered), existing scene instances may be out of sync.

### Strategy: Lazy reconciliation on scene load

When a scene is loaded and hierarchy is resolved:
1. For each prefab root instance, compare the scene's child entities against the prefab's `gameObjects` list.
2. **Missing children** (node exists in prefab but no scene entity has matching `prefabNodeId`): auto-create a new `EditorGameObject` with `prefabNodeId` set, parented to the root, with no overrides. Log: "Added missing prefab child 'shadow01' to instance 'abc123'."
3. **Orphaned children** (scene entity has `prefabNodeId` that no longer exists in prefab): mark as broken (same as stale node handling above). Do NOT auto-delete -- the user may have overrides they want to preserve. Show warning icon.
4. **Order changes**: Reorder children to match prefab's order field if the user hasn't manually reordered.

This reconciliation runs in `EditorScene.resolveHierarchy()` (or a new `reconcilePrefabInstances()` called after it).

### For runtime

`RuntimeSceneLoader` performs the same reconciliation during `loadGameObjectsWithHierarchy()` -- if a prefab node is missing from the scene data, it creates a default `GameObject` from the prefab node's components.

---

## Constraints & Scope

- Children in a prefab are always inline scratch (no nested prefab-in-prefab refs -- deferred).
- Prefab children in scenes cannot be reparented away from their root or deleted individually.
- Scratch entities CAN be parented under a prefab root (they coexist as "loose children" without `prefabNodeId`). Only entities with `prefabNodeId` are locked.
- Structure changes (add/remove children) happen in prefab edit mode only.
- "Unpack Prefab" (converting a prefab instance + children to independent scratch entities) is deferred to a future plan.

---

## Phase 1: Prefab Data Model

Add `gameObjects` field to `JsonPrefab`.

### Modified Files

**`src/main/java/com/pocket/rpg/prefab/JsonPrefab.java`**
- [ ] Add `private List<GameObjectData> gameObjects`
- [ ] Add `private transient Map<String, GameObjectData> nodeIdIndex` -- lazy cached lookup map
- [ ] `getGameObjects()` -- returns `gameObjects` if non-null; else wraps `components` in a single root `GameObjectData`
- [ ] `setGameObjects(List<GameObjectData>)` -- sets `gameObjects`, clears `components = null`, clears `nodeIdIndex`
- [ ] `hasChildren()` -- true if `gameObjects` has entries with non-null `parentId`
- [ ] `findNode(String nodeId)` -- uses `nodeIdIndex` for O(1) lookup. Builds index lazily on first call.
- [ ] `getRootNode()` -- returns the `GameObjectData` with null `parentId`
- [ ] Update `getComponents()` -- if `gameObjects` exists, delegate to `getRootNode().getComponents()`
- [ ] On save: set `components = null` when `gameObjects` is non-null (prevents dual-format files)

**`src/main/java/com/pocket/rpg/prefab/Prefab.java`**
- [ ] Add default `getGameObjects()` → wraps `getComponents()` in single `GameObjectData`
- [ ] Add default `hasChildren()` → `false`
- [ ] Add default `findNode(String nodeId)` → `null`
- [ ] Add default `getChildFieldDefault(nodeId, componentType, fieldName)` -- finds node, gets field via `ComponentReflectionUtils.getFieldValue()`
- [ ] Add default `getNodeComponentsCopy(nodeId)` -- deep-clones a node's components
- [ ] Update `instantiate()` to also create child GameObjects when `hasChildren()`, parent them to root

### Verify
- [ ] `mvn compile`
- [ ] Load `poke_player.prefab.json` -- `hasChildren() == false`, `getComponents()` unchanged
- [ ] Create test prefab JSON with `gameObjects` (root + 2 children, one nested under other)
- [ ] `findNode()` returns correct node for each ID, returns null for invalid ID
- [ ] `getRootNode()` returns entry with null parentId
- [ ] Empty `gameObjects` list: `getRootNode()` returns null without crash
- [ ] `getGameObjects()` with old format: wraps `components` in single-entry list

---

## Phase 2: Instance Model -- `prefabNodeId`

Add `prefabNodeId` to `EditorGameObject` and `GameObjectData`. Update all override-related methods.

### Modified Files

**`src/main/java/com/pocket/rpg/serialization/GameObjectData.java`**
- [ ] Add `private String prefabNodeId` with getter/setter
- [ ] Ensure `children` field is null in prefab `gameObjects` entries (add note / validation)

**`src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`**
- [ ] Add `private final String prefabNodeId` (null in existing constructors)
- [ ] New constructor: `EditorGameObject(String prefabId, String prefabNodeId, String name, Vector3f position)`
- [ ] Update private deserialization constructor to accept `prefabNodeId`
- [ ] Add `isPrefabChildNode()` → `prefabNodeId != null && !prefabNodeId.isEmpty()`
- [ ] **Update `getMergedComponents()`**: if `prefabNodeId != null`:
  - Call `prefab.findNode(prefabNodeId)` → if null, log warning, return empty list
  - Otherwise clone node's components and apply overrides (same logic as root)
- [ ] **Update `getFieldValue()`**: for prefab instances with `prefabNodeId != null`, fallback calls `prefab.getChildFieldDefault(prefabNodeId, ...)` instead of `prefab.getFieldDefault(...)`
- [ ] **Update `getFieldDefault()`**: same delegation based on `prefabNodeId`
- [ ] **Update `isFieldOverridden()`**: compares against correct node defaults (via updated `getFieldDefault()`)
- [ ] **Update `resetFieldToDefault()`**: resets to correct node defaults (already works because it removes the override and cache refresh pulls from correct node)
- [ ] **Update `toData()`**: set `data.setPrefabNodeId(prefabNodeId)`
- [ ] **Update `fromData()`**: read `data.getPrefabNodeId()`, pass to constructor
- [ ] **`setFieldValue()`**: call `invalidateComponentCache()` after storing override (currently missing for prefab instances)

### Verify
- [ ] Create `EditorGameObject` with `prefabNodeId` pointing to child node. `getComponents()` resolves child's components.
- [ ] `getFieldDefault()` returns child node's values, not root's.
- [ ] `isFieldOverridden()` compares against child node defaults.
- [ ] `resetFieldToDefault()` reverts to child node default.
- [ ] `toData()` / `fromData()` round-trip preserves `prefabNodeId`.
- [ ] Stale `prefabNodeId` (node removed): `getMergedComponents()` returns empty, no NPE, warning logged.
- [ ] Regression: existing prefab instances (`prefabNodeId == null`) behave identically.

---

## Phase 3: Editor Instantiation

When placing a prefab with children, create all child entities automatically.

### New File

**`src/main/java/com/pocket/rpg/prefab/PrefabHierarchyHelper.java`**
- [ ] `expandChildren(root, prefab)` -- creates child `EditorGameObject` instances from `prefab.getGameObjects()`. Sets parent relationships. Returns flat list of descendants (parent-first order).
- [ ] `captureHierarchy(root)` -- reverse: walks root + children, calls `toData()` for each. Returns `List<GameObjectData>` for `prefab.setGameObjects()`.
- [ ] `collectAll(root)` -- collects root + all descendants in **parent-first order** (critical for `AddEntitiesCommand` undo correctness).
- [ ] `reconcileInstance(root, prefab, scene)` -- compares scene children against prefab nodes. Creates missing children, flags orphans.

### Modified Files

**`src/main/java/com/pocket/rpg/prefab/JsonPrefabLoader.java`** (`instantiate()`)
- [ ] After creating root entity, call `PrefabHierarchyHelper.expandChildren(entity, asset)` to create children
- [ ] Children are parented to root via existing hierarchy system

**`src/main/java/com/pocket/rpg/editor/assets/SceneViewportDropTarget.java`**
- [ ] After `handleDrop()`, check `entity.hasChildren()`
- [ ] If children: `PrefabHierarchyHelper.collectAll(entity)` → `AddEntitiesCommand`
- [ ] If no children: existing `AddEntityCommand`

**`src/main/java/com/pocket/rpg/editor/assets/HierarchyDropTarget.java`**
- [ ] Same pattern in all 4 static drop methods

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/EntityCreationService.java`** (`cloneEntity()`)
- [ ] Add `isPrefabChildNode()` case: `new EditorGameObject(prefabId, prefabNodeId, name, position)` + copy overrides
- [ ] `duplicateChildrenRecursive()` already handles children -- no change

**`src/main/java/com/pocket/rpg/editor/scene/EditorScene.java`** (or new reconciliation step)
- [ ] After `resolveHierarchy()`, call `reconcilePrefabInstances()` to auto-create missing children and flag orphans

**`src/main/java/com/pocket/rpg/editor/PrefabEditController.java`** (`invalidateInstanceCaches()`, `getInstanceCount()`)
- [ ] `invalidateInstanceCaches()`: already iterates by `prefabId` -- children share the same `prefabId` so they're naturally included
- [ ] `getInstanceCount()`: filter to `!isPrefabChildNode()` to avoid double-counting children as separate instances

### Verify
- [ ] Drop hierarchical prefab onto viewport. Root + children in hierarchy. Parent-first ordering verified.
- [ ] Undo removes all. Redo restores all (parent-child links intact).
- [ ] Duplicate hierarchical prefab: fresh IDs, same `prefabNodeId`, parent points to cloned root.
- [ ] Drop flat prefab: single entity (regression).
- [ ] Multiple rapid drops of same prefab: no ID collisions.
- [ ] Scene with missing children (prefab gained a child since save): auto-created on load.
- [ ] Scene with orphaned children (prefab lost a child since save): flagged, no crash.

---

## Phase 4: Runtime Scene Loading

Handle `prefabNodeId` during runtime instantiation.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/scene/RuntimeSceneLoader.java`** (`createPrefabInstance()`)
- [ ] If `goData.getPrefabNodeId() != null`:
  - `prefab.findNode(nodeId)` → if null, log error, return null
  - Use node's components as template, clone + apply overrides
  - Extract helper: `instantiateFromNode(name, position, nodeComponents, overrides, active)`
- [ ] If `goData.getPrefabNodeId() == null`: existing behavior
- [ ] During hierarchy resolution: if a prefab root has missing children (present in prefab def but not scene data), auto-create them from prefab defaults

### Verify
- [ ] Play mode with hierarchical prefab: hierarchy correct, child components resolved
- [ ] Play mode with child overrides: overrides applied
- [ ] Play mode with stale `prefabNodeId`: warning logged, no crash
- [ ] Play mode with flat prefabs: unchanged (regression)
- [ ] @ComponentReference from root component to child component resolves correctly (Phase 4 of `loadGameObjectsWithHierarchy` handles this)

---

## Phase 5: Prefab Edit Mode

Update `PrefabEditController` to edit the full hierarchy.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/PrefabEditController.java`**
- [ ] **`buildWorkingEntity()`**: use `prefab.getGameObjects()` to create scratch entities via `EditorGameObject.fromData()`. All added to `workingScene`. Call `resolveHierarchy()`.
- [ ] Add `Map<EditorGameObject, String> workingEntityNodeIds` -- maps working entities to their original node IDs.
- [ ] **`save()`**: call `PrefabHierarchyHelper.captureHierarchy(workingEntity)`, using `workingEntityNodeIds` to preserve existing IDs. New children get fresh IDs. Set `targetPrefab.setGameObjects()`.
- [ ] Add `List<GameObjectData> savedGameObjects` field. Set in `enterEditMode()` and after save. Used by `resetToSaved()`.
- [ ] **`resetToSaved()`**: rebuild working entities from `savedGameObjects`.
- [ ] Prefab edit mode's working scene supports: creating children (via `AddEntityCommand`), deleting children (via `RemoveEntityCommand`), reordering children (via drag-drop).

**`src/main/java/com/pocket/rpg/editor/panels/inspector/PrefabInspector.java`**
- [ ] Render whichever entity is selected in the working scene (not always root).
- [ ] Metadata section (display name, category) only when root is selected.
- [ ] Component list for any selected entity (root or child).

### Verify
- [ ] Open hierarchical prefab in edit mode. Children in hierarchy.
- [ ] Select child. Inspector shows child's components.
- [ ] Add component to child, save, verify JSON.
- [ ] Add new child entity, save, verify new entry in `gameObjects` with fresh nodeId.
- [ ] Delete child in edit mode, save, verify node removed from `gameObjects`.
- [ ] Reorder children (drag-drop), save, verify order fields in JSON.
- [ ] Revert restores original hierarchy.
- [ ] Undo/redo within prefab edit mode works for add/remove/modify on children.
- [ ] Flat prefab editing unchanged (regression).
- [ ] Edit prefab, close without saving (discard): no changes persisted.
- [ ] Two consecutive saves produce identical JSON (idempotency).

---

## Phase 6: Hierarchy Panel & Inspector UX

Visual distinction, interaction restrictions, and Save As Prefab updates.

### Modified Files

**`src/main/java/com/pocket/rpg/editor/utils/IconUtils.java`**
- [ ] Add icon for prefab child nodes (e.g., `MaterialIcons.AccountTree`)

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyTreeRenderer.java`**
- [ ] Prefab child icon for `isPrefabChildNode()` entities
- [ ] Context menu: hide "Unparent", "Delete", "Duplicate" for prefab children. Show "Edit Prefab".
- [ ] Delete key handler: if selection includes prefab children, ignore them (only delete roots/scratch entities).

**`src/main/java/com/pocket/rpg/editor/panels/hierarchy/HierarchyDragDropHandler.java`**
- [ ] Skip drag initiation for `isPrefabChildNode()` entities
- [ ] Multi-select drag: exclude `isPrefabChildNode()` entities from the reparent action

**`src/main/java/com/pocket/rpg/editor/panels/inspector/EntityInspector.java`**
- [ ] For `isPrefabChildNode()`: show prefab name, node name ("Shadow in Player With Shadow"), override count
- [ ] "Edit Prefab" button → `RequestPrefabEditEvent`
- [ ] "Reset All" on a child resets only that child's overrides (existing behavior, but verify)

**`src/main/java/com/pocket/rpg/editor/panels/SavePrefabPopup.java`**
- [ ] Capture children: `PrefabHierarchyHelper.captureHierarchy(sourceEntity)` → `prefab.setGameObjects()`
- [ ] Component summary: show collapsible tree (root + each child with their component lists)
- [ ] If source entity has a mix of prefab children and scratch children: bake prefab children into scratch (deep-clone merged components, strip `prefabId`)

### Verify
- [ ] Prefab children show with distinct icon
- [ ] Drag prefab child: blocked
- [ ] Delete key on prefab child: no-op
- [ ] No Delete/Unparent in context menu for prefab children
- [ ] Delete root: all children removed. Confirmation mentions child count. Undo restores all.
- [ ] Override a child field: bold display. "Reset to default" reverts to child node's default (not root's).
- [ ] "Reset All" on child: resets only that child.
- [ ] Save entity with children as new prefab: `gameObjects` in JSON, tree summary in popup.
- [ ] Multi-select root + child + external entity → delete: root deleted (with children), external entity deleted, child-only selection ignored.
- [ ] Selecting a prefab child in the **scene viewport** (click on sprite) selects the child entity.

---

## Files Summary

| Phase | File | Action |
|-------|------|--------|
| 1 | `src/.../prefab/JsonPrefab.java` | MODIFY -- `gameObjects` field, node index, hierarchy queries |
| 1 | `src/.../prefab/Prefab.java` | MODIFY -- default methods, update `instantiate()` |
| 2 | `src/.../serialization/GameObjectData.java` | MODIFY -- add `prefabNodeId` |
| 2 | `src/.../editor/scene/EditorGameObject.java` | MODIFY -- `prefabNodeId`, merged components, override methods |
| 3 | `src/.../prefab/PrefabHierarchyHelper.java` | **NEW** -- expand, capture, collect, reconcile |
| 3 | `src/.../prefab/JsonPrefabLoader.java` | MODIFY -- expand children on instantiate |
| 3 | `src/.../editor/assets/SceneViewportDropTarget.java` | MODIFY -- multi-entity add |
| 3 | `src/.../editor/assets/HierarchyDropTarget.java` | MODIFY -- multi-entity add |
| 3 | `src/.../editor/panels/hierarchy/EntityCreationService.java` | MODIFY -- clone prefab children |
| 3 | `src/.../editor/scene/EditorScene.java` | MODIFY -- reconcile prefab instances after resolveHierarchy |
| 3 | `src/.../editor/PrefabEditController.java` | MODIFY -- instance count filter |
| 4 | `src/.../editor/scene/RuntimeSceneLoader.java` | MODIFY -- handle `prefabNodeId`, auto-create missing children |
| 5 | `src/.../editor/PrefabEditController.java` | MODIFY -- hierarchy edit/save/revert |
| 5 | `src/.../editor/panels/inspector/PrefabInspector.java` | MODIFY -- render selected entity |
| 6 | `src/.../editor/utils/IconUtils.java` | MODIFY -- prefab child icon |
| 6 | `src/.../editor/panels/hierarchy/HierarchyTreeRenderer.java` | MODIFY -- visual distinction, restrictions |
| 6 | `src/.../editor/panels/hierarchy/HierarchyDragDropHandler.java` | MODIFY -- block child drag |
| 6 | `src/.../editor/panels/inspector/EntityInspector.java` | MODIFY -- child node info |
| 6 | `src/.../editor/panels/SavePrefabPopup.java` | MODIFY -- capture children, tree summary |

## Existing Utilities to Reuse

| Utility | Location | Reuse For |
|---------|----------|-----------|
| `GameObjectData` | `serialization/GameObjectData.java` | Prefab hierarchy representation |
| `EditorGameObject.toData()/fromData()` | `editor/scene/EditorGameObject.java` | Working entities in prefab edit |
| `EditorScene.resolveHierarchy()` | `editor/scene/EditorScene.java` | Rebuild parent-child from parentId |
| `ComponentReflectionUtils.cloneComponent()` | `serialization/ComponentReflectionUtils.java` | Deep-cloning components |
| `AddEntitiesCommand` | `editor/undo/commands/AddEntitiesCommand.java` | Atomic add of root + children |
| `ComponentTypeAdapterFactory` | `serialization/custom/ComponentTypeAdapterFactory.java` | GameObjectData.components serialization |
| `RemoveEntityCommand` | `editor/undo/commands/RemoveEntityCommand.java` | Already handles children recursively |

## End-to-End Verification

1. `mvn compile` after each phase
2. Create test prefab JSON with `gameObjects` (root + children, including grandchild for depth > 1)
3. Drop in editor -- full hierarchy appears
4. Override a child's field -- bold display, reset works (uses child defaults, not root's)
5. Save scene, reload -- children persist with correct overrides and `prefabNodeId`
6. Enter play mode -- runtime hierarchy + overrides correct
7. Open prefab in edit mode -- children editable, can add/remove/reorder
8. Edit prefab to add a child, save. Load scene with existing instances. New child auto-created.
9. Edit prefab to remove a child, save. Load scene. Orphaned child flagged with warning.
10. Save As Prefab on entity with children -- `gameObjects` in JSON, tree summary in popup
11. Regression: all existing flat prefabs (old `components` format) work unchanged
12. Multiple instances of same hierarchical prefab with different child overrides in same scene

## Deferred / Future Work

- **Nested prefab refs** (child is itself a prefab instance): requires extension of `gameObjects` entries to support `prefabId`
- **Unpack Prefab**: convert instance + children to independent scratch entities
- **Prefab variants**: prefab inheriting from another with some defaults changed
- **Asset browser preview**: show hierarchy badge/tooltip for prefabs with children
- **Side-by-side override comparison**: diff view for current values vs prefab defaults

## Code Review

- [ ] Review all modified files for consistency with existing patterns
- [ ] Verify undo/redo for: place, delete, duplicate, override, reset, revert
- [ ] Serialization round-trip: save → load → save produces identical JSON
- [ ] `AddEntitiesCommand` entity ordering: root always before children (parent-first)
- [ ] No NPEs from stale `prefabNodeId` or missing prefab
- [ ] `getInstanceCount()` excludes child nodes
- [ ] `invalidateComponentCache()` called on all override mutations
- [ ] `GameObjectData.children` field is null in prefab entries (no conflict with PersistentEntitySnapshot)
- [ ] Update `architecture.md` and `common-pitfalls.md` with prefab hierarchy info
- [ ] Ask user about `Documents/Encyclopedia/` update after implementation
